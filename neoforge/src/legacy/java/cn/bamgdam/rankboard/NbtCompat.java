package cn.bamgdam.rankboard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

final class NbtCompat {
    private NbtCompat() { }
    static boolean getBoolean(CompoundTag nbt, String key) { return nbt.getBoolean(key); }
    static long getLong(CompoundTag nbt, String key) { return nbt.getLong(key); }
    static String getString(CompoundTag nbt, String key) { return nbt.getString(key); }
    static String asString(Tag element) { return element.getAsString(); }
    static ListTag getList(CompoundTag nbt, String key, byte type) { return nbt.getList(key, type); }
    static CompoundTag getCompound(CompoundTag nbt, String key) { return nbt.getCompound(key); }
    static void putUuid(CompoundTag nbt, String key, UUID uuid) { nbt.putUUID(key, uuid); }
    static UUID getUuid(CompoundTag nbt, String key) { return nbt.getUUID(key); }
}
