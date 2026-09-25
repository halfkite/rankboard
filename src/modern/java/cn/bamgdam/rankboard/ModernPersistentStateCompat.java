package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;

final class PersistentStateCompat {
    private PersistentStateCompat() { }

    static LeaderboardState get(MinecraftServer server, String id) {
        return PersistentStateCompatSupport.get(server, id);
    }
}
