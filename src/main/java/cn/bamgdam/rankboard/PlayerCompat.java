package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

final class PlayerCompat {
    private PlayerCompat() { }
    static MinecraftServer server(ServerPlayerEntity player) { return player.getServer(); }
    static World world(ServerPlayerEntity player) { return player.getWorld(); }
    static boolean isFake(ServerPlayerEntity player) {
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().equals("EntityPlayerMPFake")) return true;
        }
        return false;
    }
}
