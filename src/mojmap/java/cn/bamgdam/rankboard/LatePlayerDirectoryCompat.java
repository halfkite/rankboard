package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import java.util.UUID;

final class PlayerDirectoryCompat {
    private PlayerDirectoryCompat() { }

    static boolean isAllowed(MinecraftServer server, UUID uuid, String name) {
        return server.getPlayerList().getWhiteList().isWhiteListed(new NameAndId(uuid, name));
    }

    static void cache(MinecraftServer server, UUID uuid, String name) {
        server.services().nameToIdCache().add(new NameAndId(uuid, name));
        server.services().nameToIdCache().save();
    }

    static void saveCache(MinecraftServer server) { server.services().nameToIdCache().save(); }
}
