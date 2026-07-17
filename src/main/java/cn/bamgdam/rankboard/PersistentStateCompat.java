package cn.bamgdam.rankboard;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

final class PersistentStateCompat {
    private PersistentStateCompat() { }

    static LeaderboardState get(MinecraftServer server, String id) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(LeaderboardState::new, LeaderboardState::fromNbt,
                        DataFixTypes.SAVED_DATA_SCOREBOARD), id);
    }
}
