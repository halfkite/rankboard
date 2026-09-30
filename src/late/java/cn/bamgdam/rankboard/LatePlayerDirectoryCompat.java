package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

final class PlayerDirectoryCompat {
    private PlayerDirectoryCompat() { }

    static boolean isAllowed(MinecraftServer server, UUID uuid, String name) {
        return PlayerDirectoryBridge.isAllowed(server.getPlayerManager().getWhitelist(), uuid, name);
    }

    static void cache(MinecraftServer server, UUID uuid, String name) {
        PlayerDirectoryBridge.cache(server, uuid, name);
    }

    static void saveCache(MinecraftServer server) { PlayerDirectoryBridge.saveCache(server); }
}
