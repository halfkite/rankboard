package cn.bamgdam.rankboard;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.lang.reflect.Method;
import java.util.Collection;

/** Reads profile data across Authlib's JavaBean and record-style APIs. */
final class ProfileCompat {
    private ProfileCompat() { }

    @SuppressWarnings("unchecked")
    static Collection<Property> textures(GameProfile profile) {
        Object properties = invoke(profile, "properties", "getProperties");
        for (Method method : properties.getClass().getMethods()) {
            if (!method.getName().equals("get") || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isAssignableFrom(String.class)
                    || !Collection.class.isAssignableFrom(method.getReturnType())) continue;
            try {
                return (Collection<Property>) method.invoke(properties, "textures");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not read player profile textures", exception);
            }
        }
        throw new IllegalStateException("Unsupported Authlib profile property collection");
    }

    private static Object invoke(GameProfile profile, String modernName, String legacyName) {
        for (String name : new String[]{modernName, legacyName}) {
            try {
                return profile.getClass().getMethod(name).invoke(profile);
            } catch (NoSuchMethodException ignored) {
                // Try the other Authlib generation.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not read player profile " + name, exception);
            }
        }
        throw new IllegalStateException("Unsupported Authlib GameProfile API");
    }
}
