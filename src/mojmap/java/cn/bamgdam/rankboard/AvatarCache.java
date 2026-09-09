
package cn.bamgdam.rankboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates web-ready player heads from the skin texture carried by joining profiles. */
final class AvatarCache {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RankBoard-AvatarCache");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private AvatarCache() { }

    static void cacheOnJoin(MinecraftServer server, ServerPlayer player) {
        if (!RankBoardConfig.get().avatarCacheEnabled) return;
        UUID uuid = player.getUUID();
        String resolvedSkinUrl = skinUrl(player);
        boolean avatarService = resolvedSkinUrl == null;
        if (avatarService) resolvedSkinUrl = "https://mc-heads.net/avatar/" + uuid + "/64";
        final String downloadUrl = resolvedSkinUrl;
        final boolean directAvatar = avatarService;
        Path target = path(server, uuid);
        if (isFresh(target)) return;
        WORKER.submit(() -> downloadHead(downloadUrl, target, uuid, directAvatar));
    }

    static Path path(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("rankboard").resolve("avatars").resolve(uuid + ".png");
    }

    private static boolean isFresh(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try {
            long maximumAge = RankBoardConfig.get().avatarCacheDays * 86_400_000L;
            return System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis() < maximumAge;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String skinUrl(ServerPlayer player) {
        Collection<Property> textures = player.getGameProfile().properties().get("textures");
        for (Property property : textures) {
            try {
                String decoded = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
                JsonObject textureSet = root.getAsJsonObject("textures");
                if (textureSet == null || !textureSet.has("SKIN")) continue;
                String url = textureSet.getAsJsonObject("SKIN").get("url").getAsString();
                URI uri = URI.create(url);
                if (uri.getScheme().equals("https") || uri.getScheme().equals("http")) return url;
            } catch (RuntimeException ignored) { }
        }
        return null;
    }

    private static void downloadHead(String url, Path target, UUID uuid, boolean directAvatar) {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "RankBoard/1.1").GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            BufferedImage skin;
            try (InputStream input = response.body()) { skin = ImageIO.read(input); }
            if (skin == null || skin.getWidth() < 16 || skin.getHeight() < 16) throw new IOException("Invalid skin image");
            BufferedImage head = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = head.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (directAvatar && skin.getWidth() >= 16 && skin.getHeight() >= 16) {
                graphics.drawImage(skin, 0, 0, 64, 64, null);
            } else if (skin.getWidth() >= 48 && skin.getHeight() >= 16) {
                graphics.drawImage(skin, 0, 0, 64, 64, 8, 8, 16, 16, null);
                graphics.drawImage(skin, 0, 0, 64, 64, 40, 8, 48, 16, null);
            } else {
                throw new IOException("Invalid skin image dimensions");
            }
            graphics.dispose();
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(head, "png", temporary.toFile())) throw new IOException("PNG writer unavailable");
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            RankBoardMod.LOGGER.debug("Could not cache avatar for {}", uuid, exception);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
