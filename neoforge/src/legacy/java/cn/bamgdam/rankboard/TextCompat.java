package cn.bamgdam.rankboard;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

final class TextCompat {
    private TextCompat() { }

    static Style interactive(Style style, String command, Component hoverText) {
        return style.withClickEvent(clickEvent("RUN_COMMAND", command)).withHoverEvent(hoverEvent(hoverText));
    }

    static Style suggest(Style style, String command, Component hoverText) {
        return style.withClickEvent(clickEvent("SUGGEST_COMMAND", command)).withHoverEvent(hoverEvent(hoverText));
    }

    static Style openUrl(Style style, String url, Component hoverText) {
        return style.withClickEvent(clickEvent("OPEN_URL", url)).withHoverEvent(hoverEvent(hoverText));
    }

    private static ClickEvent clickEvent(String action, String value) {
        try {
            if (ClickEvent.class.isInterface()) {
                String implementationName = ClickEvent.class.getName() + switch (action) {
                    case "OPEN_URL" -> "$OpenUrl";
                    case "RUN_COMMAND" -> "$RunCommand";
                    case "SUGGEST_COMMAND" -> "$SuggestCommand";
                    default -> throw new IllegalArgumentException("Unsupported click action: " + action);
                };
                Class<?> implementation = Class.forName(implementationName, true, ClickEvent.class.getClassLoader());
                Constructor<?> constructor = action.equals("OPEN_URL")
                        ? implementation.getConstructor(URI.class) : implementation.getConstructor(String.class);
                return (ClickEvent) constructor.newInstance(action.equals("OPEN_URL") ? URI.create(value) : value);
            }
            Class<?> actionType = java.util.Arrays.stream(ClickEvent.class.getDeclaredClasses())
                    .filter(Class::isEnum).findFirst().orElseThrow(NoSuchMethodException::new);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object actionValue = Enum.valueOf((Class<? extends Enum>) actionType, action);
            return (ClickEvent) ClickEvent.class.getConstructor(actionType, String.class)
                    .newInstance(actionValue, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Minecraft click event " + action, unwrap(exception));
        }
    }

    private static HoverEvent hoverEvent(Component text) {
        try {
            if (HoverEvent.class.isInterface()) {
                Class<?> implementation = Class.forName(HoverEvent.class.getName() + "$ShowText", true,
                        HoverEvent.class.getClassLoader());
                return (HoverEvent) implementation.getConstructor(Component.class).newInstance(text);
            }
            for (Class<?> actionType : HoverEvent.class.getDeclaredClasses()) {
                Object showText = null;
                if (actionType.isEnum()) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object candidate = Enum.valueOf((Class<? extends Enum>) actionType, "SHOW_TEXT");
                    showText = candidate;
                } else {
                    for (Field field : actionType.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                                && actionType.isAssignableFrom(field.getType())
                                && field.getName().toUpperCase(java.util.Locale.ROOT).contains("SHOW_TEXT")) {
                            if (!field.canAccess(null)) field.setAccessible(true);
                            showText = field.get(null);
                            break;
                        }
                    }
                }
                if (showText != null) {
                    return (HoverEvent) HoverEvent.class.getConstructor(actionType, Object.class)
                            .newInstance(showText, text);
                }
            }
            throw new NoSuchMethodException("HoverEvent.Action.SHOW_TEXT");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Minecraft hover event", unwrap(exception));
        }
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }
}
