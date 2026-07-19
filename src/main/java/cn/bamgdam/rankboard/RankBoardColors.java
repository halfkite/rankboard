package cn.bamgdam.rankboard;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

final class RankBoardColors {
    private static final List<Formatting> COLORS = List.of(
            Formatting.BLACK, Formatting.DARK_BLUE, Formatting.DARK_GREEN, Formatting.DARK_AQUA,
            Formatting.DARK_RED, Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY,
            Formatting.DARK_GRAY, Formatting.BLUE, Formatting.GREEN, Formatting.AQUA,
            Formatting.RED, Formatting.LIGHT_PURPLE, Formatting.YELLOW, Formatting.WHITE);

    private RankBoardColors() { }

    static int rgb(RankBoardMod.Metric metric) {
        return RankBoardConfig.get().metricColor(metric);
    }

    static Formatting legacy(RankBoardMod.Metric metric) {
        return nearestLegacy(rgb(metric));
    }

    static int renderedRgb(RankBoardMod.Metric metric) {
        if (RankBoardConfig.get().nameColorRenderMode == RankBoardConfig.NameColorRenderMode.RGB) return rgb(metric);
        Integer value = legacy(metric).getColorValue();
        return value == null ? 0xFFFFFF : value;
    }

    static MutableText text(String value, RankBoardMod.Metric metric) {
        return Text.literal(value).styled(style -> style.withColor(renderedRgb(metric)));
    }

    static Formatting nearestLegacy(int rgb) {
        Formatting best = Formatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        for (Formatting formatting : COLORS) {
            Integer candidate = formatting.getColorValue();
            if (candidate == null) continue;
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
}
