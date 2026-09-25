package cn.bamgdam.rankboard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

final class NbtCompat {
    private NbtCompat() { }

    static boolean getBoolean(CompoundTag nbt, String key) {
        Object modern = invokeOptional(nbt, "getBooleanOr", new Class<?>[]{String.class, boolean.class}, key, false);
        return modern != Missing.VALUE ? (Boolean) modern
                : (Boolean) invokeRequired(nbt, "getBoolean", new Class<?>[]{String.class}, key);
    }

    static long getLong(CompoundTag nbt, String key) {
        Object modern = invokeOptional(nbt, "getLongOr", new Class<?>[]{String.class, long.class}, key, 0L);
        return modern != Missing.VALUE ? (Long) modern
                : (Long) invokeRequired(nbt, "getLong", new Class<?>[]{String.class}, key);
    }

    static String getString(CompoundTag nbt, String key) {
        Object modern = invokeOptional(nbt, "getStringOr", new Class<?>[]{String.class, String.class}, key, "");
        Object value = modern != Missing.VALUE ? modern
                : invokeRequired(nbt, "getString", new Class<?>[]{String.class}, key);
        return unwrap(value, "");
    }

    static String asString(Tag element) {
        Object modern = invokeOptional(element, "asString", new Class<?>[0]);
        Object value = modern != Missing.VALUE ? modern : invokeRequired(element, "getAsString", new Class<?>[0]);
        return unwrap(value, "");
    }

    static ListTag getList(CompoundTag nbt, String key, byte type) {
        Object modern = invokeOptional(nbt, "getListOrEmpty", new Class<?>[]{String.class}, key);
        return modern != Missing.VALUE ? (ListTag) modern
                : (ListTag) invokeRequired(nbt, "getList", new Class<?>[]{String.class, byte.class}, key, type);
    }

    static CompoundTag getCompound(CompoundTag nbt, String key) {
        Object value = invokeRequired(nbt, "getCompound", new Class<?>[]{String.class}, key);
        if (value instanceof Optional<?> optional) return (CompoundTag) optional.orElseThrow();
        return (CompoundTag) value;
    }

    static void putUuid(CompoundTag nbt, String key, UUID uuid) {
        Object old = invokeOptional(nbt, "putUUID", new Class<?>[]{String.class, UUID.class}, key, uuid);
        if (old == Missing.VALUE) invokeRequired(nbt, "putString", new Class<?>[]{String.class, String.class}, key, uuid.toString());
    }

    static UUID getUuid(CompoundTag nbt, String key) {
        Object old = invokeOptional(nbt, "getUUID", new Class<?>[]{String.class}, key);
        if (old instanceof UUID uuid) return uuid;
        String value = getString(nbt, key);
        if (!value.isEmpty()) return UUID.fromString(value);
        Object ints = invokeOptional(nbt, "getIntArray", new Class<?>[]{String.class}, key);
        if (ints instanceof int[] parts && parts.length == 4) {
            long most = ((long) parts[0] << 32) | (parts[1] & 0xffffffffL);
            long least = ((long) parts[2] << 32) | (parts[3] & 0xffffffffL);
            return new UUID(most, least);
        }
        throw new IllegalStateException("No UUID data for NBT key " + key);
    }

    private static Object invokeRequired(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not call " + target.getClass().getName() + "." + name, unwrap(exception));
        }
    }

    private static Object invokeOptional(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            return method.invoke(target, arguments);
        } catch (NoSuchMethodException ignored) {
            return Missing.VALUE;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not call " + target.getClass().getName() + "." + name, unwrap(exception));
        }
    }

    private static String unwrap(Object value, String fallback) {
        if (value instanceof Optional<?> optional) return optional.map(Object::toString).orElse(fallback);
        return value instanceof String text ? text : fallback;
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }

    private enum Missing { VALUE }
}
