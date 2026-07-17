package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

final class PlayerCompat {
    private PlayerCompat() { }
    static MinecraftServer server(ServerPlayerEntity player) { return player.getServer(); }
}
