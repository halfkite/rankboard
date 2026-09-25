package cn.bamgdam.rankboard;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

final class TextCompat {
    private TextCompat() { }

    static Style interactive(Style style, String command, Text hoverText) {
        return style.withClickEvent(clickEvent("RUN_COMMAND", command)).withHoverEvent(hoverEvent(hoverText));
    }

    static Style suggest(Style style, String command, Text hoverText) {
        return style.withClickEvent(clickEvent("SUGGEST_COMMAND", command)).withHoverEvent(hoverEvent(hoverText));
    }

    static Style openUrl(Style style, String url, Text hoverText) {
        return style.withClickEvent(clickEvent("OPEN_URL", url)).withHoverEvent(hoverEvent(hoverText));
    }

    private static ClickEvent clickEvent(String action, String value) {
        try {
            if (ClickEvent.class.isInterface()) {
                String classId = switch (action) {
                    case "OPEN_URL" -> "$class_10608";
                    case "RUN_COMMAND" -> "$class_10609";
                    case "SUGGEST_COMMAND" -> "$class_10610";
                    default -> throw new IllegalArgumentException("Unsupported click action: " + action);
                };
                Class<?> implementation = Class.forName(ClickEvent.class.getName() + classId, true,
                        ClickEvent.class.getClassLoader());
                Constructor<?> constructor = action.equals("OPEN_URL")
                        ? implementation.getConstructor(URI.class) : implementation.getConstructor(String.class);
                Object payload = action.equals("OPEN_URL") ? URI.create(value) : value;
                return (ClickEvent) constructor.newInstance(payload);
            }

            Class<?> actionClass = enumClass(ClickEvent.class);
            Object actionValue = enumValue(actionClass, action);
            return (ClickEvent) ClickEvent.class.getConstructor(actionClass, String.class)
                    .newInstance(actionValue, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Minecraft click event " + action, unwrap(exception));
        }
    }

    private static HoverEvent hoverEvent(Text text) {
        try {
            if (HoverEvent.class.isInterface()) {
                Class<?> implementation = Class.forName(HoverEvent.class.getName() + "$class_10613", true,
                        HoverEvent.class.getClassLoader());
                return (HoverEvent) implementation.getConstructor(Text.class).newInstance(text);
            }
            for (Class<?> nested : HoverEvent.class.getDeclaredClasses()) {
                try {
                    Field showText = nested.getDeclaredField("field_24342");
                    showText.setAccessible(true);
                    Object action = showText.get(null);
                    return (HoverEvent) HoverEvent.class.getConstructor(showText.getType(), Object.class)
                            .newInstance(action, text);
                } catch (NoSuchFieldException ignored) {
                    // Continue until the legacy SHOW_TEXT action is found.
                }
            }
            throw new NoSuchMethodException("HoverEvent.Action.SHOW_TEXT");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Minecraft hover event", unwrap(exception));
        }
    }

    private static Class<?> enumClass(Class<?> eventClass) throws NoSuchMethodException {
        for (Class<?> nested : eventClass.getDeclaredClasses()) if (nested.isEnum()) return nested;
        throw new NoSuchMethodException("No action enum on " + eventClass.getName());
    }

    private static Object enumValue(Class<?> enumClass, String fieldName) throws NoSuchMethodException {
        for (Object value : enumClass.getEnumConstants()) {
            if (((Enum<?>) value).name().equals(fieldName)) return value;
        }
        throw new NoSuchMethodException("No enum value " + fieldName + " on " + enumClass.getName());
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }
}
