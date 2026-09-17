package cn.bamgdam.rankboard;

import net.minecraft.SharedConstants;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Bootstrap used by the NeoForge 1.21.x universal distribution.
 *
 * <p>NeoForge does not negotiate multiple same-id mod jars the way Fabric's
 * nested-jar loader does.  The universal artifact therefore keeps the
 * version-specific implementation jars as private resources and loads exactly
 * one implementation after the Minecraft version is known.  A regular
 * per-version build simply has no embedded variant and remains unchanged.</p>
 */
@Mod("rankboard")
public final class UniversalNeoForgeEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("rankboard-universal");
    private static final String VARIANT_PREFIX = "META-INF/rankboard-variants/";
    private static final String IMPLEMENTATION_CLASS = "cn.bamgdam.rankboard.RankBoardMod";
    private static final Object INSTANCE_LOCK = new Object();
    private static volatile Object implementation;
    private static volatile ClassLoader implementationLoader;

    public UniversalNeoForgeEntrypoint() {
        loadImplementation();
    }

    private static void loadImplementation() {
        if (implementation != null) return;
        synchronized (INSTANCE_LOCK) {
            if (implementation != null) return;
            String version = currentVersionName();
            String variant = selectVariant(version);
            String resource = VARIANT_PREFIX + variant + ".jar";
            ClassLoader parent = UniversalNeoForgeEntrypoint.class.getClassLoader();
            try (InputStream input = parent.getResourceAsStream(resource)) {
                if (input == null) {
                    // Per-version jars do not carry private variants.
                    return;
                }
                Path extracted = Files.createTempFile("rankboard-", "-" + variant + ".jar");
                Files.copy(input, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                extracted.toFile().deleteOnExit();
                URLClassLoader loader = new URLClassLoader(
                        new URL[]{extracted.toUri().toURL()}, parent);
                Class<?> implementationClass = Class.forName(IMPLEMENTATION_CLASS, true, loader);
                Constructor<?> constructor = implementationClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                implementationLoader = loader;
                UniversalBridge.setImplementationLoader(loader);
                implementation = constructor.newInstance();
                LOGGER.info("Loaded RankBoard NeoForge implementation for Minecraft {}", version);
            } catch (ReflectiveOperationException | IOException exception) {
                LOGGER.error("Could not load the RankBoard implementation for Minecraft {}", version, exception);
            }
        }
    }

    private static String currentVersionName() {
        Object worldVersion = SharedConstants.getCurrentVersion();
        for (String methodName : new String[]{"getName", "name", "getId", "id"}) {
            try {
                Method method = worldVersion.getClass().getMethod(methodName);
                Object value = method.invoke(worldVersion);
                if (value != null) return value.toString();
            } catch (ReflectiveOperationException ignored) {
                // The WorldVersion accessor changed from getName() to name() in 1.21.x.
            }
        }
        return worldVersion.toString();
    }

    private static String selectVariant(String version) {
        if (version == null) return "1.21.1";
        String normalized = version.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("26.1")) {
            int patch = numericPatch(normalized, 2);
            if (patch >= 2) return "26.1.2";
            if (patch >= 1) return "26.1.1";
            return "26.1";
        }
        if (!normalized.startsWith("1.21")) return "1.21.1";
        int patch = numericPatch(normalized, 2);
        if (patch >= 11) return "1.21.11";
        if (patch >= 8) return "1.21.8";
        if (patch >= 4) return "1.21.4";
        return "1.21.1";
    }

    private static int numericPatch(String version, int index) {
        String[] parts = version.split("\\.");
        if (parts.length > index) {
            try {
                return Integer.parseInt(parts[index].replaceAll("[^0-9].*", ""));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
