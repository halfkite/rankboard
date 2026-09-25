package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class PlayerCompat {
    private PlayerCompat() { }
    static MinecraftServer server(ServerPlayerEntity player) {
        try {
            return invokeByReturnType(player, MinecraftServer.class);
        } catch (IllegalStateException absentOnPlayer) {
            return invokeByReturnType(world(player), MinecraftServer.class);
        }
    }
    static World world(ServerPlayerEntity player) { return invokeByReturnType(player, World.class); }
    static ServerCommandSource source(ServerPlayerEntity player) {
        return invokeByReturnType(server(player), ServerCommandSource.class).withEntity(player);
    }

    private static <T> T invokeByReturnType(Object target, Class<T> expectedType) {
        Method fallback = null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && expectedType.isAssignableFrom(method.getReturnType())) {
                if (method.getName().equals("getServer") || method.getName().equals("getWorld")
                        || method.getName().equals("getCommandSource")) {
                    fallback = method;
                    break;
                }
                if (fallback == null) fallback = method;
            }
        }
        if (fallback == null) throw new IllegalStateException("No " + expectedType.getName()
                + " accessor on " + target.getClass().getName());
        try {
            return expectedType.cast(fallback.invoke(target));
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : exception;
            throw new IllegalStateException("Could not read " + expectedType.getName(), cause);
        }
    }

    static boolean isFake(ServerPlayerEntity player) {
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().equals("EntityPlayerMPFake")) return true;
        }
        return false;
    }
}
