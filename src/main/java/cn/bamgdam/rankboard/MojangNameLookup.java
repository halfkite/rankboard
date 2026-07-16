package cn.bamgdam.rankboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves current profile names through Mojang's public session profile endpoint. */
final class MojangNameLookup {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "RankBoard-NameLookup");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).executor(EXECUTOR).build();

    private MojangNameLookup() { }

    static int lookupOne(ServerCommandSource source, String input) {
        UUID uuid;
        try {
            uuid = parseUuid(input);
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("UUID 格式无效：" + input));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("正在向 Mojang 查询 " + uuid + " ...").formatted(Formatting.GRAY), false);
        MinecraftServer server = source.getServer();
        query(uuid).thenAccept(result -> server.execute(() -> {
            if (result.name != null) {
                cache(server, result);
                source.sendFeedback(() -> Text.literal(result.uuid + " -> " + result.name)
                        .formatted(Formatting.GRAY), false);
            } else {
                source.sendError(Text.literal(result.uuid + " 查询失败：" + result.error));
            }
        }));
        return 1;
    }

    static int lookupWhitelist(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        Map<UUID, String> whitelist = StatReader.readWhitelistNames(server);
        if (whitelist.isEmpty()) {
            source.sendError(Text.literal("服务器白名单为空或无法读取 whitelist.json。"));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("正在查询白名单中的 " + whitelist.size() + " 个 UUID ...")
                .formatted(Formatting.GRAY), false);
        List<CompletableFuture<Result>> futures = whitelist.keySet().stream().map(MojangNameLookup::query).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            List<Result> results = futures.stream().map(CompletableFuture::join).toList();
            server.execute(() -> finishWhitelistLookup(source, server, whitelist, results));
        });
        return whitelist.size();
    }

    private static void finishWhitelistLookup(ServerCommandSource source, MinecraftServer server,
                                              Map<UUID, String> whitelist, List<Result> results) {
        List<String> lines = new ArrayList<>();
        int success = 0;
        for (Result result : results) {
            if (result.name != null) {
                cache(server, result);
                String oldName = whitelist.get(result.uuid);
                lines.add(result.uuid + "：" + oldName + " -> " + result.name
                        + (result.name.equals(oldName) ? "" : "（名称已变化）"));
                success++;
            } else {
                lines.add(result.uuid + "：查询失败（" + result.error + "）");
            }
        }
        server.getUserCache().save();
        String message = "白名单名称查询完成：成功 " + success + "/" + results.size() + "\n" + String.join("\n", lines);
        source.sendFeedback(() -> Text.literal(message).formatted(Formatting.GRAY), false);
    }

    private static void cache(MinecraftServer server, Result result) {
        server.getUserCache().add(new GameProfile(result.uuid, result.name));
        server.getUserCache().save();
        StatReader.updateName(result.uuid, result.name);
    }

    private static CompletableFuture<Result> query(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String compact = uuid.toString().replace("-", "");
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compact))
                        .timeout(Duration.ofSeconds(15)).header("User-Agent", "RankBoard/1.0").GET().build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return new Result(uuid, null, "HTTP " + response.statusCode());
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                return new Result(uuid, json.get("name").getAsString(), null);
            } catch (Exception exception) {
                return new Result(uuid, null, exception.getClass().getSimpleName()
                        + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
            }
        }, EXECUTOR);
    }

    private static UUID parseUuid(String value) {
        String normalized = value.trim();
        if (normalized.matches("[0-9a-fA-F]{32}")) {
            normalized = normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-"
                    + normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-" + normalized.substring(20);
        }
        return UUID.fromString(normalized);
    }

    private record Result(UUID uuid, String name, String error) { }
}
