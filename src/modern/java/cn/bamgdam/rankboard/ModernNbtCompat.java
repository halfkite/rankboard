package cn.bamgdam.rankboard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Uuids;

import java.util.UUID;

final class NbtCompat {
    private NbtCompat() { }

    static boolean getBoolean(NbtCompound nbt, String key) { return nbt.getBoolean(key, false); }
    static long getLong(NbtCompound nbt, String key) { return nbt.getLong(key, 0L); }
    static String getString(NbtCompound nbt, String key) { return nbt.getString(key, ""); }
    static String asString(NbtElement element) { return element.asString().orElse(""); }
    static NbtList getList(NbtCompound nbt, String key, byte type) { return nbt.getListOrEmpty(key); }
    static void putUuid(NbtCompound nbt, String key, UUID uuid) { nbt.putString(key, uuid.toString()); }

    static UUID getUuid(NbtCompound nbt, String key) {
        String value = nbt.getString(key, "");
        if (!value.isEmpty()) return UUID.fromString(value);
        return nbt.getIntArray(key).map(Uuids::toUuid).orElseThrow();
    }
}
