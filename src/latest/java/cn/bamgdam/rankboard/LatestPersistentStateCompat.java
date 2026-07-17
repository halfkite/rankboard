package cn.bamgdam.rankboard;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentStateType;

final class PersistentStateCompat {
    private PersistentStateCompat() { }

    static LeaderboardState get(MinecraftServer server, String id) {
        PersistentStateType<LeaderboardState> type = new PersistentStateType<>(id,
                LeaderboardState::new, codec(server.getRegistryManager()), DataFixTypes.SAVED_DATA_SCOREBOARD);
        return server.getOverworld().getPersistentStateManager().getOrCreate(type);
    }

    private static Codec<LeaderboardState> codec(net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        return NbtCompound.CODEC.xmap(
                nbt -> LeaderboardState.fromNbt(nbt, lookup),
                state -> state.writeNbt(new NbtCompound(), lookup));
    }
}
