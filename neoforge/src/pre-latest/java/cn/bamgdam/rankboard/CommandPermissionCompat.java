package cn.bamgdam.rankboard;

final class CommandPermissionCompat {
    private CommandPermissionCompat() { }
    static boolean has(Object source, int level) { return CommandPermissionSupport.has(source, level); }
}
