package cn.bamgdam.rankboard;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Reads an item component across Minecraft 1.21 ComponentMap API revisions. */
final class ComponentLookupCompat {
    private ComponentLookupCompat() { }

    static boolean has(Object components, Object componentType) {
        Method fallback = null;
        for (Method method : components.getClass().getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(componentType)
                    && !method.getReturnType().isPrimitive()
                    && method.getReturnType() != void.class) {
                if (method.getDeclaringClass().isInterface()
                        && Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                    fallback = method;
                    break;
                }
                if (fallback == null) fallback = method;
            }
        }
        if (fallback == null) throw new IllegalStateException("Could not find a component getter on "
                + components.getClass().getName());
        try {
            if (!fallback.canAccess(components)) fallback.setAccessible(true);
            return fallback.invoke(components, componentType) != null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read item component " + componentType, exception);
        }
    }
}
