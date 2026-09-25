package cn.bamgdam.rankboard;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Selects the matching persistent-state API without linking version-specific Minecraft classes. */
final class PersistentStateCompatSupport {
    private PersistentStateCompatSupport() { }

    static LeaderboardState get(MinecraftServer server, String id) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        Method getOrCreate = findGetOrCreate(manager.getClass());
        Class<?> typeClass = getOrCreate.getParameterTypes()[0];
        Object stateType = createStateType(typeClass, server, id, getOrCreate.getParameterCount() == 2);
        try {
            Object state = getOrCreate.getParameterCount() == 1
                    ? getOrCreate.invoke(manager, stateType)
                    : getOrCreate.invoke(manager, stateType, id);
            return (LeaderboardState) state;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not load RankBoard persistent state", unwrap(exception));
        }
    }

    private static Method findGetOrCreate(Class<?> managerClass) {
        for (Method method : managerClass.getMethods()) {
            if ((method.getName().equals("method_17924") || method.getName().equals("getOrCreate"))
                    && method.getReturnType().isAssignableFrom(LeaderboardState.class)
                    && (method.getParameterCount() == 1
                    || (method.getParameterCount() == 2 && method.getParameterTypes()[1] == String.class))) {
                return method;
            }
        }
        throw new IllegalStateException("Could not find Minecraft PersistentStateManager.getOrCreate");
    }

    private static Object createStateType(Class<?> typeClass, MinecraftServer server, String id, boolean legacyType) {
        for (Constructor<?> constructor : typeClass.getDeclaredConstructors()) {
            Object[] arguments = constructorArguments(constructor, server, id, legacyType);
            if (arguments == null) continue;
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not create Minecraft persistent-state type "
                        + typeClass.getName(), unwrap(exception));
            }
        }
        throw new IllegalStateException("No compatible constructor found for Minecraft persistent-state type "
                + typeClass.getName());
    }

    private static Object[] constructorArguments(Constructor<?> constructor, MinecraftServer server, String id,
                                                 boolean legacyType) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Type[] genericTypes = constructor.getGenericParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        boolean hasId = false;
        boolean hasDataFixType = false;
        boolean hasFactory = false;
        boolean hasCodec = false;
        boolean hasLoader = false;

        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];
            if (parameterType == String.class) {
                arguments[index] = id;
                hasId = true;
            } else if (parameterType == DataFixTypes.class) {
                arguments[index] = DataFixTypes.SAVED_DATA_SCOREBOARD;
                hasDataFixType = true;
            } else if (Supplier.class.isAssignableFrom(parameterType)) {
                arguments[index] = (Supplier<LeaderboardState>) LeaderboardState::new;
                hasFactory = true;
            } else if (BiFunction.class.isAssignableFrom(parameterType)) {
                arguments[index] = (BiFunction<NbtCompound, RegistryWrapper.WrapperLookup, LeaderboardState>)
                        LeaderboardState::fromNbt;
                hasLoader = true;
            } else if (parameterType == Codec.class) {
                arguments[index] = codec(server.getRegistryManager());
                hasCodec = true;
            } else if (Function.class.isAssignableFrom(parameterType)) {
                String genericType = genericTypes[index].getTypeName();
                if (genericType.contains("Codec")) {
                    arguments[index] = (Function<Object, Codec<LeaderboardState>>) context ->
                            codec(registryLookup(context, server));
                    hasCodec = true;
                } else {
                    arguments[index] = (Function<Object, LeaderboardState>) context -> new LeaderboardState();
                    hasFactory = true;
                }
            } else {
                return null;
            }
        }
        return (hasId || legacyType) && hasDataFixType && hasFactory && (hasCodec || hasLoader) ? arguments : null;
    }

    private static RegistryWrapper.WrapperLookup registryLookup(Object context, MinecraftServer server) {
        if (context != null) {
            for (Method method : context.getClass().getMethods()) {
                if (method.getParameterCount() == 0
                        && RegistryWrapper.WrapperLookup.class.isAssignableFrom(method.getReturnType())) {
                    try {
                        return (RegistryWrapper.WrapperLookup) method.invoke(context);
                    } catch (ReflectiveOperationException ignored) {
                        // Try the context's world accessor below.
                    }
                }
            }
            for (Method method : context.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType().isPrimitive()) continue;
                try {
                    Object world = method.invoke(context);
                    if (world == null) continue;
                    for (Method worldMethod : world.getClass().getMethods()) {
                        if (worldMethod.getParameterCount() == 0
                                && RegistryWrapper.WrapperLookup.class.isAssignableFrom(worldMethod.getReturnType())) {
                            return (RegistryWrapper.WrapperLookup) worldMethod.invoke(world);
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Context accessors vary between Minecraft patch releases.
                }
            }
        }
        return server.getRegistryManager();
    }

    private static Codec<LeaderboardState> codec(RegistryWrapper.WrapperLookup lookup) {
        return NbtCompound.CODEC.xmap(
                nbt -> LeaderboardState.fromNbt(nbt, lookup),
                state -> state.writeNbt(new NbtCompound(), lookup));
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }
}
