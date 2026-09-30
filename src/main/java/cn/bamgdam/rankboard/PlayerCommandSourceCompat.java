package cn.bamgdam.rankboard;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

/** Preserve player permissions and chat output across renamed accessors.
 * Using server.getCommandSource().withEntity(player) retains console privileges. */
final class PlayerCommandSourceCompat {
    private PlayerCommandSourceCompat() { }

    private static final ClassValue<Method> ACCESSOR = new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == ServerCommandSource.class) {
                    return method;
                }
            }
            throw new IllegalStateException("No player command source accessor on " + type.getName());
        }
    };

    static ServerCommandSource source(ServerPlayerEntity player) {
        try {
            return (ServerCommandSource) ACCESSOR.get(player.getClass()).invoke(player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read player's command source", exception);
        }
    }
}
