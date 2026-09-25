package cn.bamgdam.rankboard;

import net.minecraft.server.command.ServerCommandSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;

final class CommandPermissionCompat {
    private CommandPermissionCompat() { }

    static boolean has(ServerCommandSource source, int level) {
        return hasNewPermissionApi() ? hasPermissionApi(source, level) : hasLegacyApi(source, level);
    }

    private static boolean hasNewPermissionApi() {
        try {
            Class.forName("net.minecraft.class_12087", false, CommandPermissionCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean hasLegacyApi(ServerCommandSource source, int level) {
        try {
            Method method = null;
            for (Method candidate : source.getClass().getMethods()) {
                if (candidate.getParameterCount() == 1 && candidate.getParameterTypes()[0] == int.class
                        && candidate.getReturnType() == boolean.class) {
                    if (candidate.getName().equals("method_9259")
                            || candidate.getName().equals("hasPermissionLevel")) {
                        method = candidate;
                        break;
                    }
                    if (method == null) method = candidate;
                }
            }
            if (method == null) throw new NoSuchMethodException("boolean permission check(int)");
            return (boolean) method.invoke(source, level);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not check legacy Minecraft command permissions", exception);
        }
    }

    private static boolean hasPermissionApi(ServerCommandSource source, int level) {
        try {
        ClassLoader loader = source.getClass().getClassLoader();
        Class<?> levelClass = Class.forName("net.minecraft.class_12094", true, loader);
        Class<?> levelPermissionClass = Class.forName("net.minecraft.class_12087$class_12089", true, loader);
        Class<?> predicateClass = Class.forName("net.minecraft.class_12096", true, loader);
        Class<?> permissionClass = Class.forName("net.minecraft.class_12087", true, loader);

        Method fromLevel = null;
        for (Method candidate : levelClass.getMethods()) {
            if (Modifier.isStatic(candidate.getModifiers()) && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0] == int.class && candidate.getReturnType() == levelClass) {
                fromLevel = candidate;
                if (candidate.getName().equals("method_75027") || candidate.getName().equals("fromLevel")) break;
            }
        }
        if (fromLevel == null) throw new NoSuchMethodException("PermissionLevel.fromLevel(int)");
        Object permissionLevel = fromLevel.invoke(null, level);
        Constructor<?> constructor = levelPermissionClass.getConstructor(levelClass);
        Object permission = constructor.newInstance(permissionLevel);

        Method getPermissions = null;
        for (Method candidate : source.getClass().getMethods()) {
            if (candidate.getParameterCount() == 0 && candidate.getReturnType() == predicateClass) {
                getPermissions = candidate;
                if (candidate.getName().equals("method_75037") || candidate.getName().equals("getPermissions")) break;
            }
        }
        if (getPermissions == null) throw new NoSuchMethodException("ServerCommandSource.getPermissions()");
        Object predicate = getPermissions.invoke(source);
        Method hasPermission = null;
        for (Method candidate : predicateClass.getMethods()) {
            if (candidate.getParameterCount() == 1 && candidate.getParameterTypes()[0] == permissionClass
                    && candidate.getReturnType() == boolean.class) {
                hasPermission = candidate;
                if (candidate.getName().equals("hasPermission")) break;
            }
        }
        if (hasPermission == null) throw new NoSuchMethodException("PermissionPredicate.hasPermission(permission)");
        return (boolean) hasPermission.invoke(predicate, permission);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not check Minecraft command permissions", exception);
        }
    }
}
