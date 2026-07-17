package cn.bamgdam.rankboard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.UUID;

final class NbtCompat {
    private NbtCompat() { }

    static boolean getBoolean(NbtCompound nbt, String key) { return nbt.getBoolean(key); }
    static long getLong(NbtCompound nbt, String key) { return nbt.getLong(key); }
    static String getString(NbtCompound nbt, String key) { return nbt.getString(key); }
    static String asString(NbtElement element) { return element.asString(); }
    static NbtList getList(NbtCompound nbt, String key, byte type) { return nbt.getList(key, type); }
    static void putUuid(NbtCompound nbt, String key, UUID uuid) { nbt.putUuid(key, uuid); }
    static UUID getUuid(NbtCompound nbt, String key) { return nbt.getUuid(key); }
}
