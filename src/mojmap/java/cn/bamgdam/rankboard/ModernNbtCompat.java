package cn.bamgdam.rankboard;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class NbtCompat {
    private NbtCompat() { }

    static boolean getBoolean(CompoundTag nbt, String key) { return nbt.getBooleanOr(key, false); }
    static long getLong(CompoundTag nbt, String key) { return nbt.getLongOr(key, 0L); }
    static String getString(CompoundTag nbt, String key) { return nbt.getStringOr(key, ""); }
    static String asString(Tag element) { return element.asString().orElse(""); }
    static ListTag getList(CompoundTag nbt, String key, byte type) { return nbt.getListOrEmpty(key); }
    static void putUuid(CompoundTag nbt, String key, UUID uuid) { nbt.putString(key, uuid.toString()); }

    static UUID getUuid(CompoundTag nbt, String key) {
        String value = nbt.getStringOr(key, "");
        if (!value.isEmpty()) return UUID.fromString(value);
        return nbt.getIntArray(key).map(UUIDUtil::uuidFromIntArray).orElseThrow();
    }
}
