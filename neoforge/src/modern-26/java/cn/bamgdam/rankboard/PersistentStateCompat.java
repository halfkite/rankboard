package cn.bamgdam.rankboard;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;

final class PersistentStateCompat {
    private PersistentStateCompat() { }

    static LeaderboardState get(MinecraftServer server, String id) {
        SavedDataType<LeaderboardState> type = new SavedDataType<>(Identifier.withDefaultNamespace(id),
                LeaderboardState::new, codec(server.registryAccess()), DataFixTypes.SAVED_DATA_SCOREBOARD);
        return server.overworld().getDataStorage().computeIfAbsent(type);
    }

    private static Codec<LeaderboardState> codec(net.minecraft.core.HolderLookup.Provider lookup) {
        return CompoundTag.CODEC.xmap(
                nbt -> LeaderboardState.fromNbt(nbt, lookup),
                state -> state.writeNbt(new CompoundTag(), lookup));
    }
}
