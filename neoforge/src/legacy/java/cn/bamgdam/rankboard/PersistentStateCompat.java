package cn.bamgdam.rankboard;

import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

final class PersistentStateCompat {
    private PersistentStateCompat() { }
    static LeaderboardState get(MinecraftServer server, String id) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LeaderboardState::new, LeaderboardState::fromNbt,
                        DataFixTypes.SAVED_DATA_SCOREBOARD), id);
    }
}
