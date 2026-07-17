package cn.bamgdam.rankboard;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

final class TextCompat {
    private TextCompat() { }

    static Style interactive(Style style, String command, Component hoverText) {
        return style.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverText));
    }
}
