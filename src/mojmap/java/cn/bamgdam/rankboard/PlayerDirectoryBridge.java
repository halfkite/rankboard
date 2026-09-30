package cn.bamgdam.rankboard;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

/** Resolves the GameProfile -> NameAndId transition from the running server,
 * rather than linking a family JAR to a single Minecraft point release. */
final class PlayerDirectoryBridge {
    private PlayerDirectoryBridge() { }

    private static final ClassValue<Method> WHITELIST = new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            return entryMethod(type, boolean.class, "isAllowed", "isWhiteListed", "method_14653");
        }
    };
    private static final ClassValue<Method> ADD = new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            return entryMethod(type, void.class, "add", "method_14508");
        }
    };
    private static final ClassValue<Method> SAVE = new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            return zeroArg(type, "save", "method_14518");
        }
    };

    static boolean isAllowed(Object whitelist, UUID uuid, String name) {
        Method method = WHITELIST.get(whitelist.getClass());
        return (boolean) invoke(method, whitelist, entry(method, uuid, name));
    }

    static void cache(Object server, UUID uuid, String name) {
        Object cache = cacheOf(server);
        Method method = ADD.get(cache.getClass());
        invoke(method, cache, entry(method, uuid, name));
        save(cache);
    }

    static void saveCache(Object server) { save(cacheOf(server)); }

    private static void save(Object cache) { invoke(SAVE.get(cache.getClass()), cache); }

    private static Object cacheOf(Object server) {
        // Older servers expose GameProfileCache directly; later servers expose
        // NameAndIdCache through Services. Do not reference the new classes.
        Method legacy = findZeroArg(server.getClass(), "getUserCache", "getProfileCache", "method_3793");
        if (legacy != null) return invoke(legacy, server);
        Object services = invoke(zeroArg(server.getClass(), "getApiServices", "services", "method_73550"), server);
        return invoke(zeroArg(services.getClass(), "nameToIdCache", "comp_4407"), services);
    }

    private static Object entry(Method method, UUID uuid, String name) {
        try {
            var constructor = method.getParameterTypes()[0].getConstructor(UUID.class, String.class);
            constructor.trySetAccessible();
            return constructor.newInstance(uuid, name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Minecraft player directory entry", exception);
        }
    }

    private static Method entryMethod(Class<?> type, Class<?> result, String... names) {
        for (Method method : type.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && !method.isBridge()
                    && method.getParameterCount() == 1 && method.getReturnType() == result
                    && Arrays.asList(names).contains(method.getName())) {
                try {
                    method.getParameterTypes()[0].getConstructor(UUID.class, String.class);
                    method.trySetAccessible();
                    return method;
                } catch (NoSuchMethodException ignored) { }
            }
        }
        throw new IllegalStateException("Unsupported Minecraft player directory API: " + type.getName());
    }

    private static Method zeroArg(Class<?> type, String... names) {
        Method method = findZeroArg(type, names);
        if (method == null) throw new IllegalStateException("Missing Minecraft directory method on " + type.getName());
        return method;
    }

    private static Method findZeroArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                if (Modifier.isStatic(method.getModifiers())) continue;
                method.trySetAccessible();
                return method;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke Minecraft player directory API: " + method, exception);
        }
    }
}
