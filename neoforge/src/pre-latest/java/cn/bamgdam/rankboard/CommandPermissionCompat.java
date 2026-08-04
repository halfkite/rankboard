package cn.bamgdam.rankboard;

import net.minecraft.commands.CommandSourceStack;

final class CommandPermissionCompat {
    private CommandPermissionCompat() { }
    static boolean has(CommandSourceStack source, int level) { return source.hasPermission(level); }
}
