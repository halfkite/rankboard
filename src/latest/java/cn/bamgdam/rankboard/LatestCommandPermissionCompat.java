package cn.bamgdam.rankboard;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;

final class CommandPermissionCompat {
    private CommandPermissionCompat() { }

    static boolean has(ServerCommandSource source, int level) {
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(level)));
    }
}
