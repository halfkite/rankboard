package cn.bamgdam.rankboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Checks operator level without linking to the legacy or permission-set API. */
final class CommandPermissionSupport {
    private CommandPermissionSupport() {}

    static boolean has(Object source, int level) {
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != int.class
                    || method.getReturnType() != boolean.class
                    || !(method.getName().equals("hasPermission") || method.getName().equals("hasPermissionLevel"))) continue;
            try {
                return (boolean) method.invoke(source, level);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not check Minecraft command permission level", exception);
            }
        }

        Object permissionSet = permissionSet(source);
        Method hasPermission = null;
        for (Method method : permissionSet.getClass().getMethods()) {
            if (!method.getName().equals("hasPermission") || method.getParameterCount() != 1
                    || method.getReturnType() != boolean.class) continue;
            if (method.getDeclaringClass().isInterface() && Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                hasPermission = method;
                break;
            }
            if (hasPermission == null) hasPermission = method;
        }
        if (hasPermission == null) throw new IllegalStateException("Could not find Minecraft permission-set check");
        Object permission = commandLevelPermission(hasPermission.getParameterTypes()[0], level, source.getClass().getClassLoader());
        if (permission == null) throw new IllegalStateException("Could not create a command-level permission");
        try {
            return (boolean) hasPermission.invoke(permissionSet, permission);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not check Minecraft permission set", exception);
        }
    }

    private static Object permissionSet(Object source) {
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class
                    || !(method.getName().equals("permissions") || method.getName().equals("getPermissions"))) continue;
            try {
                Object permissions = method.invoke(source);
                if (permissions != null) return permissions;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not read Minecraft permission set", exception);
            }
        }
        throw new IllegalStateException("Could not find Minecraft command permissions");
    }

    private static Object commandLevelPermission(Class<?> permissionType, int level, ClassLoader loader) {
        try {
            for (Class<?> implementation : permissionType.getDeclaredClasses()) {
                for (Constructor<?> constructor : implementation.getConstructors()) {
                    if (constructor.getParameterCount() != 1 || !constructor.getParameterTypes()[0].isEnum()) continue;
                    Class<?> levelType = constructor.getParameterTypes()[0];
                    for (Method factory : levelType.getMethods()) {
                        if (!Modifier.isStatic(factory.getModifiers()) || factory.getParameterCount() != 1
                                || factory.getParameterTypes()[0] != int.class || factory.getReturnType() != levelType) continue;
                        return constructor.newInstance(factory.invoke(null, level));
                    }
                }
            }
            Class<?> implementation = Class.forName(permissionType.getName() + "$HasCommandLevel", true, loader);
            for (Constructor<?> constructor : implementation.getConstructors()) {
                if (constructor.getParameterCount() != 1 || !constructor.getParameterTypes()[0].isEnum()) continue;
                Class<?> levelType = constructor.getParameterTypes()[0];
                for (Method factory : levelType.getMethods()) {
                    if (Modifier.isStatic(factory.getModifiers()) && factory.getParameterCount() == 1
                            && factory.getParameterTypes()[0] == int.class && factory.getReturnType() == levelType) {
                        return constructor.newInstance(factory.invoke(null, level));
                    }
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create a command-level permission", exception);
        }
        return null;
    }
}
