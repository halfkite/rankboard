package cn.bamgdam.rankboard;

import net.minecraft.server.command.ServerCommandSource;

final class CommandPermissionCompat {
    private CommandPermissionCompat() { }
    static boolean has(ServerCommandSource source, int level) { return source.hasPermissionLevel(level); }
}
