package cn.bamgdam.rankboard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

final class NbtCompat {
    private static final Object NO_METHOD = new Object();

    private NbtCompat() { }

    static boolean getBoolean(NbtCompound nbt, String key) {
        Object value = invoke(nbt, new String[]{"method_10577", "getBoolean"},
                new Class<?>[]{String.class}, key);
        if (value == NO_METHOD) {
            value = invoke(nbt, new String[]{"method_68566", "getBoolean"},
                    new Class<?>[]{String.class, boolean.class}, key, false);
        }
        value = unwrap(value);
        return value instanceof Boolean result && result;
    }

    static long getLong(NbtCompound nbt, String key) {
        Object value = invoke(nbt, new String[]{"method_10537", "getLong"},
                new Class<?>[]{String.class}, key);
        if (value == NO_METHOD) {
            value = invoke(nbt, new String[]{"method_68080", "getLong"},
                    new Class<?>[]{String.class, long.class}, key, 0L);
        }
        value = unwrap(value);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    static String getString(NbtCompound nbt, String key) {
        Object value = invoke(nbt, new String[]{"method_10558", "getString"},
                new Class<?>[]{String.class}, key);
        if (value == NO_METHOD) {
            value = invoke(nbt, new String[]{"method_68564", "getString"},
                    new Class<?>[]{String.class, String.class}, key, "");
        }
        value = unwrap(value);
        return value instanceof String result ? result : "";
    }

    static String asString(NbtElement element) {
        Object value = invoke(element, new String[]{"method_10714", "asString"}, new Class<?>[0]);
        if (value == NO_METHOD) {
            value = invoke(element, new String[]{"method_68658", "asString"}, new Class<?>[0]);
        }
        value = unwrap(value);
        return value instanceof String result ? result : "";
    }

    static NbtList getList(NbtCompound nbt, String key, byte type) {
        Object value = invoke(nbt, new String[]{"method_10554", "getList"},
                new Class<?>[]{String.class, int.class}, key, (int) type);
        if (value == NO_METHOD) {
            value = invoke(nbt, new String[]{"method_68569", "getListOrEmpty"},
                    new Class<?>[]{String.class}, key);
        }
        value = unwrap(value);
        return value instanceof NbtList list ? list : new NbtList();
    }

    static NbtCompound getCompound(NbtCompound nbt, String key) {
        Object value = invoke(nbt, new String[]{"method_10562", "getCompound"},
                new Class<?>[]{String.class}, key);
        if (value == NO_METHOD) {
            value = invoke(nbt, new String[]{"method_68568", "getCompoundOrEmpty"},
                    new Class<?>[]{String.class}, key);
        }
        value = unwrap(value);
        return value instanceof NbtCompound compound ? compound : new NbtCompound();
    }

    static void putUuid(NbtCompound nbt, String key, UUID uuid) {
        Method method = null;
        for (Method candidate : nbt.getClass().getMethods()) {
            if (candidate.getParameterCount() == 2 && candidate.getParameterTypes()[0] == String.class
                    && candidate.getParameterTypes()[1] == UUID.class) {
                method = candidate;
                break;
            }
        }
        if (method == null) { nbt.putString(key, uuid.toString()); return; }
        try { method.invoke(nbt, key, uuid); }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not store a RankBoard UUID", unwrap(exception));
        }
    }

    static UUID getUuid(NbtCompound nbt, String key) {
        for (Method method : nbt.getClass().getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class
                    && method.getReturnType() == UUID.class) {
                try { return (UUID) method.invoke(nbt, key); }
                catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Could not read a RankBoard UUID", unwrap(exception));
                }
            }
        }
        try { return UUID.fromString(getString(nbt, key)); }
        catch (RuntimeException exception) {
            throw new IllegalStateException("Could not read a RankBoard UUID", exception);
        }
    }

    private static Object invoke(Object target, String[] names, Class<?>[] parameterTypes, Object... arguments) {
        Method method = null;
        for (String name : names) {
            try {
                method = target.getClass().getMethod(name, parameterTypes);
                break;
            } catch (NoSuchMethodException ignored) { }
        }
        if (method == null) return NO_METHOD;
        try { return method.invoke(target, arguments); }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read a RankBoard NBT value", unwrap(exception));
        }
    }

    private static Object unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }
}
