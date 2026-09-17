package cn.bamgdam.rankboard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Small parent-loader bridge used by mixins in the NeoForge universal jar. */
public final class UniversalBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("rankboard-universal");
    private static volatile ClassLoader implementationLoader;
    private static volatile ClassLoader resolvedLoader;
    private static volatile Method decorateMethod;

    private UniversalBridge() { }

    static void setImplementationLoader(ClassLoader loader) {
        implementationLoader = loader;
        resolvedLoader = null;
        decorateMethod = null;
    }

    public static Component decorate(ServerPlayer player, Component fallback) {
        try {
            Method method = resolveDecorateMethod();
            if (method != null) return (Component) method.invoke(null, player, fallback);
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Could not delegate player-name decoration", exception);
        }
        return fallback;
    }

    private static Method resolveDecorateMethod() throws ClassNotFoundException, NoSuchMethodException {
        ClassLoader target = implementationLoader;
        if (target == null) target = UniversalBridge.class.getClassLoader();
        Method cached = decorateMethod;
        if (cached != null && resolvedLoader == target) return cached;
        synchronized (UniversalBridge.class) {
            cached = decorateMethod;
            if (cached != null && resolvedLoader == target) return cached;
            Class<?> colors = Class.forName("cn.bamgdam.rankboard.PlayerNameColors", true, target);
            Method method = colors.getMethod("decorate", ServerPlayer.class, Component.class);
            method.setAccessible(true);
            resolvedLoader = target;
            decorateMethod = method;
            return method;
        }
    }
}
