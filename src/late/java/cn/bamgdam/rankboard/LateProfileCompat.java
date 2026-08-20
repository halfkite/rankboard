package cn.bamgdam.rankboard;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.util.Collection;

final class ProfileCompat {
    private ProfileCompat() { }

    static Collection<Property> textures(GameProfile profile) {
        return profile.properties().get("textures");
    }

    static String name(GameProfile profile) {
        return profile.name();
    }
}
