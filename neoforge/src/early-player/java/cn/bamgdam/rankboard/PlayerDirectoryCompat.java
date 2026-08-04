package cn.bamgdam.rankboard;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

final class PlayerDirectoryCompat {
    private PlayerDirectoryCompat() { }
    static boolean isAllowed(MinecraftServer server, UUID uuid, String name) {
        return server.getPlayerList().getWhiteList().isWhiteListed(new GameProfile(uuid, name));
    }
    static void cache(MinecraftServer server, UUID uuid, String name) {
        server.getProfileCache().add(new GameProfile(uuid, name));
        server.getProfileCache().save();
    }
    static void saveCache(MinecraftServer server) { server.getProfileCache().save(); }
}
