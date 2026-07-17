package cn.bamgdam.rankboard;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

final class TextCompat {
    private TextCompat() { }

    static Style interactive(Style style, String command, Text hoverText) {
        return style.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverText));
    }
}
