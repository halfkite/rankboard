package cn.bamgdam.rankboard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

final class RankBoardColors {
    private static final List<ChatFormatting> COLORS = List.of(
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE);

    private RankBoardColors() { }

    static int rgb(RankBoardMod.Metric metric) { return RankBoardConfig.get().metricColor(metric); }

    static ChatFormatting legacy(RankBoardMod.Metric metric) { return nearestLegacy(rgb(metric)); }

    static int renderedRgb(RankBoardMod.Metric metric) {
        if (RankBoardConfig.get().nameColorRenderMode == RankBoardConfig.NameColorRenderMode.RGB) return rgb(metric);
        return colorValue(legacy(metric));
    }

    static MutableComponent text(String value, RankBoardMod.Metric metric) {
        return Component.literal(value).withStyle(style -> style.withColor(renderedRgb(metric)));
    }

    static ChatFormatting nearestLegacy(int rgb) {
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        for (ChatFormatting formatting : COLORS) {
            int candidate = colorValue(formatting);
            int dr = red - ((candidate >>> 16) & 0xFF);
            int dg = green - ((candidate >>> 8) & 0xFF);
            int db = blue - (candidate & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                best = formatting;
                bestDistance = distance;
            }
        }
        return best;
    }

    static int colorValue(ChatFormatting formatting) {
        return switch (formatting) {
            case BLACK -> 0x000000; case DARK_BLUE -> 0x0000AA; case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA; case DARK_RED -> 0xAA0000; case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00; case GRAY -> 0xAAAAAA; case DARK_GRAY -> 0x555555;
            case BLUE -> 0x5555FF; case GREEN -> 0x55FF55; case AQUA -> 0x55FFFF;
            case RED -> 0xFF5555; case LIGHT_PURPLE -> 0xFF55FF; case YELLOW -> 0xFFFF55;
            default -> 0xFFFFFF;
        };
    }
}
