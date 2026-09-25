package cn.bamgdam.rankboard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

final class NbtCompat {
    private NbtCompat() { }

    static boolean getBoolean(NbtCompound nbt, String key) { return nbt.getBoolean(key); }
    static long getLong(NbtCompound nbt, String key) { return nbt.getLong(key); }
    static String getString(NbtCompound nbt, String key) { return nbt.getString(key); }
    static String asString(NbtElement element) { return element.asString(); }
    static NbtList getList(NbtCompound nbt, String key, byte type) { return nbt.getList(key, type); }
    static NbtCompound getCompound(NbtCompound nbt, String key) { return nbt.getCompound(key); }
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
        try { return UUID.fromString(nbt.getString(key)); }
        catch (RuntimeException exception) {
            throw new IllegalStateException("Could not read a RankBoard UUID", exception);
        }
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }
}
