package cn.bamgdam.rankboard;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Bridges the 1.21.4 SavedData.Factory API and the 1.21.5+ SavedDataType API. */
final class PersistentStateCompat {
    private static final String MODERN_TYPE = "net.minecraft.world.level.saveddata.SavedDataType";

    private PersistentStateCompat() { }

    static LeaderboardState get(MinecraftServer server, String id) {
        Object storage = server.overworld().getDataStorage();
        Method compute = findComputeIfAbsent(storage.getClass());
        Class<?> parameterType = compute.getParameterTypes()[0];
        Object stateType = createType(parameterType, server, id, compute.getParameterCount() == 2);
        Object state;
        try {
            state = compute.getParameterCount() == 2
                    ? compute.invoke(storage, stateType, id)
                    : compute.invoke(storage, stateType);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not load RankBoard persistent state", unwrap(exception));
        }
        return LeaderboardState.class.cast(state);
    }

    private static Method findComputeIfAbsent(Class<?> storageClass) {
        for (Method method : storageClass.getMethods()) {
            if (method.getName().equals("computeIfAbsent")
                    && (method.getParameterCount() == 1
                    || (method.getParameterCount() == 2 && method.getParameterTypes()[1] == String.class))
                    && method.getReturnType().isAssignableFrom(LeaderboardState.class)) {
                return method;
            }
        }
        throw new IllegalStateException("Could not find DimensionDataStorage.computeIfAbsent");
    }

    private static Object createType(Class<?> parameterType, MinecraftServer server, String id, boolean legacy) {
        if (legacy) return createLegacyFactory(parameterType);
        try {
            Class<?> modernType = Class.forName(MODERN_TYPE, true, PersistentStateCompat.class.getClassLoader());
            if (!parameterType.isAssignableFrom(modernType)) {
                throw new IllegalStateException("Unexpected modern saved-data type " + parameterType.getName());
            }
            for (Constructor<?> constructor : modernType.getDeclaredConstructors()) {
                Object[] arguments = constructorArguments(constructor, server, id, true);
                if (arguments == null) continue;
                if (!constructor.canAccess(null)) constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            }
            throw new NoSuchMethodException("No compatible SavedDataType constructor");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Minecraft SavedDataType", unwrap(exception));
        }
    }

    private static Object createLegacyFactory(Class<?> factoryClass) {
        if (!factoryClass.getName().equals("net.minecraft.world.level.saveddata.SavedData$Factory")) {
            throw new IllegalStateException("Unexpected legacy saved-data type " + factoryClass.getName());
        }
        for (Constructor<?> constructor : factoryClass.getDeclaredConstructors()) {
            Object[] arguments = constructorArguments(constructor, null, null, false);
            if (arguments == null) continue;
            try {
                if (!constructor.canAccess(null)) constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not create legacy SavedData.Factory", unwrap(exception));
            }
        }
        throw new IllegalStateException("No compatible SavedData.Factory constructor");
    }

    private static Object[] constructorArguments(Constructor<?> constructor, MinecraftServer server, String id,
                                                 boolean modern) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        boolean hasId = false;
        boolean hasFactory = false;
        boolean hasLoader = false;
        boolean hasCodec = false;
        boolean hasFixType = false;
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> type = parameterTypes[index];
            if (type == String.class && modern) {
                arguments[index] = id;
                hasId = true;
            } else if (type == DataFixTypes.class) {
                arguments[index] = DataFixTypes.SAVED_DATA_SCOREBOARD;
                hasFixType = true;
            } else if (Supplier.class.isAssignableFrom(type)) {
                arguments[index] = (Supplier<LeaderboardState>) LeaderboardState::new;
                hasFactory = true;
            } else if (BiFunction.class.isAssignableFrom(type)) {
                arguments[index] = (BiFunction<CompoundTag, HolderLookup.Provider, LeaderboardState>) LeaderboardState::fromNbt;
                hasLoader = true;
            } else if (type == Codec.class && modern) {
                arguments[index] = codec(server.registryAccess());
                hasCodec = true;
            } else {
                return null;
            }
        }
        if (!hasFactory || !hasFixType) return null;
        if (modern) return hasId && hasCodec ? arguments : null;
        return hasLoader ? arguments : null;
    }

    private static Codec<LeaderboardState> codec(HolderLookup.Provider lookup) {
        return CompoundTag.CODEC.xmap(
                nbt -> LeaderboardState.fromNbt(nbt, lookup),
                state -> writeState(state, lookup));
    }

    private static CompoundTag writeState(LeaderboardState state, HolderLookup.Provider lookup) {
        for (String name : new String[]{"writeNbt", "save"}) {
            try {
                Method method = state.getClass().getMethod(name, CompoundTag.class, HolderLookup.Provider.class);
                return (CompoundTag) method.invoke(state, new CompoundTag(), lookup);
            } catch (NoSuchMethodException ignored) {
                // The saved-data override changed name between 1.21.4 and 1.21.5.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not serialize RankBoard persistent state", unwrap(exception));
            }
        }
        throw new IllegalStateException("No compatible LeaderboardState serializer");
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }
}
