package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;

import java.util.UUID;

final class PlayerDirectoryCompat {
    private PlayerDirectoryCompat() { }

    static boolean isAllowed(MinecraftServer server, UUID uuid, String name) {
        return server.getPlayerManager().getWhitelist().isAllowed(new PlayerConfigEntry(uuid, name));
    }

    static void cache(MinecraftServer server, UUID uuid, String name) {
        server.getApiServices().nameToIdCache().add(new PlayerConfigEntry(uuid, name));
        server.getApiServices().nameToIdCache().save();
    }

    static void saveCache(MinecraftServer server) { server.getApiServices().nameToIdCache().save(); }
}
