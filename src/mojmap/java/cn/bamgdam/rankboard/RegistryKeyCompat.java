package cn.bamgdam.rankboard;

import java.lang.reflect.Method;

/** Resolves registry identifiers without linking to a version-specific return type. */
final class RegistryKeyCompat {
    private RegistryKeyCompat() {}

    static String id(Object registry, Object value) {
        Object key = invokeRegistryKey(registry, value);
        return key.toString();
    }

    static String path(Object registry, Object value) {
        Object key = invokeRegistryKey(registry, value);
        try {
            Method getPath = key.getClass().getMethod("getPath");
            return String.valueOf(getPath.invoke(key));
        } catch (ReflectiveOperationException ignored) {
            String id = key.toString();
            int separator = id.indexOf(':');
            return separator >= 0 ? id.substring(separator + 1) : id;
        }
    }

    private static Object invokeRegistryKey(Object registry, Object value) {
        for (Method method : registry.getClass().getMethods()) {
            if (!method.getName().equals("getKey") || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isInstance(value)) continue;
            try {
                Object key = method.invoke(registry, value);
                if (key != null) return key;
            } catch (ReflectiveOperationException ignored) {
                // Try another public overload/bridge method before reporting an incompatibility.
            }
        }
        throw new IllegalStateException("Could not resolve registry key for " + value.getClass().getName());
    }
}
