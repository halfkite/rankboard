package cn.bamgdam.rankboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

/** User-editable server-side language packs. UTF-8 JSON files live under config/rankboard/lang. */
final class RankBoardLanguage {
    private static final Map<String, String> ZH_CN = Map.ofEntries(
            Map.entry("language.name", "中文"), Map.entry("language.prompt", "请选择语言"),
            Map.entry("language.help", "语言"), Map.entry("language.selected", "已选择 {0}。"),
            Map.entry("welcome", "欢迎来到 {0}"), Map.entry("web_hint", "可在 {0} 查看网页排行榜。"),
            Map.entry("menu.scores", "查询分数"), Map.entry("menu.close_board", "关闭榜单"),
            Map.entry("menu.open_board", "开启榜单"), Map.entry("menu.close_look", "关闭抬头蹲起"),
            Map.entry("menu.open_look", "开启抬头蹲起"), Map.entry("menu.carousel", "轮播"),
            Map.entry("menu.website", "打开网站"), Map.entry("menu.help", "帮助"),
            Map.entry("help.player", "玩家指令"), Map.entry("help.scoreboard", "计分板"),
            Map.entry("help.web", "网页与配置"), Map.entry("help.admin", "OP 管理"),
            Map.entry("period.all", "总计"), Map.entry("period.day", "最近一日"), Map.entry("period.week", "最近一周"), Map.entry("period.month", "最近一月"),
            Map.entry("metric.food", "大胃王榜"), Map.entry("metric.jumps", "跳跃榜"), Map.entry("metric.mined", "挖掘榜"), Map.entry("metric.placed", "放置榜"),
            Map.entry("metric.kills", "击杀榜"), Map.entry("metric.pvp", "PvP榜"), Map.entry("metric.deaths", "死亡榜"), Map.entry("metric.trades", "交易榜"),
            Map.entry("metric.playtime", "在线榜"), Map.entry("metric.elytra", "飞行榜"), Map.entry("metric.fishing", "钓鱼榜"), Map.entry("metric.damage", "受伤榜"),
            Map.entry("metric.dealt", "输出榜"), Map.entry("metric.dropped", "丢垃圾榜"), Map.entry("metric.picked", "拾荒榜"), Map.entry("metric.crafted", "合成榜"), Map.entry("metric.redstone", "红石大蛇榜"));
    private static final Map<String, String> EN_US = Map.ofEntries(
            Map.entry("language.name", "English"), Map.entry("language.prompt", "Select language"),
            Map.entry("language.help", "Language"), Map.entry("language.selected", "Language set to {0}."),
            Map.entry("welcome", "Welcome to {0}"), Map.entry("web_hint", "View web rankings at {0}."),
            Map.entry("menu.scores", "My scores"), Map.entry("menu.close_board", "Close board"),
            Map.entry("menu.open_board", "Open board"), Map.entry("menu.close_look", "Disable look menu"),
            Map.entry("menu.open_look", "Enable look menu"), Map.entry("menu.carousel", "Carousel"),
            Map.entry("menu.website", "Open website"), Map.entry("menu.help", "Help"),
            Map.entry("help.player", "Player"), Map.entry("help.scoreboard", "Scoreboard"),
            Map.entry("help.web", "Web & config"), Map.entry("help.admin", "OP admin"),
            Map.entry("period.all", "All time"), Map.entry("period.day", "Last day"), Map.entry("period.week", "Last week"), Map.entry("period.month", "Last month"),
            Map.entry("metric.food", "Food eater"), Map.entry("metric.jumps", "Jumps"), Map.entry("metric.mined", "Mining"), Map.entry("metric.placed", "Placing"),
            Map.entry("metric.kills", "Kills"), Map.entry("metric.pvp", "PvP"), Map.entry("metric.deaths", "Deaths"), Map.entry("metric.trades", "Trades"),
            Map.entry("metric.playtime", "Playtime"), Map.entry("metric.elytra", "Flight"), Map.entry("metric.fishing", "Fishing"), Map.entry("metric.damage", "Damage taken"),
            Map.entry("metric.dealt", "Damage dealt"), Map.entry("metric.dropped", "Dropped items"), Map.entry("metric.picked", "Picked items"), Map.entry("metric.crafted", "Crafted items"), Map.entry("metric.redstone", "Redstone builder"));
    /** Chinese Help descriptions and their built-in English translation. Keys written to JSON are stable hashes. */
    private static final Map<String, String> HELP_EN = Map.ofEntries(
            Map.entry("打开排行榜菜单", "Open the leaderboard menu"), Map.entry("查询所有个人统计并显示总览", "Show all of your statistics and the overview"),
            Map.entry("查询指定周期的个人统计", "Show your statistics for the selected period"), Map.entry("查看排行榜", "View a leaderboard"),
            Map.entry("控制榜单轮播", "Control leaderboard carousel"), Map.entry("关闭或开启自己的抬头蹲起菜单", "Enable or disable your look-up-and-sneak menu"),
            Map.entry("显示个人单榜计分板", "Show a personal single-metric scoreboard"), Map.entry("恢复关闭前的个人计分板", "Restore your previously closed personal scoreboard"),
            Map.entry("关闭个人计分板", "Close your personal scoreboard"), Map.entry("显示个人所有榜单总览", "Show your personal all-metrics overview"),
            Map.entry("清理其他模组计分板", "Clear scoreboards from other mods"), Map.entry("设置其他模组计分板自动屏蔽", "Configure automatic blocking of other-mod scoreboards"),
            Map.entry("OP 设置轮播标题是否跟随榜单颜色", "OP: choose whether carousel titles follow metric colors"),
            Map.entry("设置网站按钮地址，默认 127.0.0.1:8765", "Set the website button address; default is 127.0.0.1:8765"),
            Map.entry("显示或隐藏菜单和帮助中的网站按钮", "Show or hide the website button in the menu and Help"),
            Map.entry("选择图标自动取色或默认蓝色网页主题", "Use icon-derived colors or the default blue web theme"),
            Map.entry("设置左侧服务器切换按钮名称、排序权重和其他网页地址", "Set server switcher name, sort weight, and peer web addresses"),
            Map.entry("查看或修改配置", "View or edit configuration"), Map.entry("清除网页限流", "Clear web rate limits"),
            Map.entry("控制服务器白名单筛选", "Control vanilla server whitelist filtering"),
            Map.entry("管理模组自带白名单", "Manage the RankBoard whitelist"), Map.entry("筛选 bot_ 前缀玩家；立即生效", "Filter bot_ prefixed players; takes effect immediately"),
            Map.entry("筛选无法识别身份的历史玩家；立即生效", "Filter unrecognised historical players; takes effect immediately"),
            Map.entry("只显示在线玩家；立即生效", "Show only online players; takes effect immediately"),
            Map.entry("查询 Mojang 玩家名或批量补全白名单名称", "Look up Mojang names or complete whitelist names"),
            Map.entry("管理榜单显示", "Manage metric visibility"), Map.entry("关闭 RankBoard 全服共享侧边栏", "Close the RankBoard global sidebar"),
            Map.entry("屏蔽其他模组计分板", "Block scoreboards from other mods"), Map.entry("列出全部榜单的中文名称、英文标识和当前颜色", "List metric names, IDs, and current colors"),
            Map.entry("恢复单个或全部榜单默认颜色", "Restore default color for one or all metrics"),
            Map.entry("查看或恢复榜单显示名称", "View or reset metric display names"), Map.entry("OP 控制全服抬头蹲起菜单", "OP: control the global look-up-and-sneak menu"),
            Map.entry("立即清除全部网页限流记录", "Immediately clear all web rate-limit records"),
            Map.entry("列出全部配置、当前值和所属文件", "List all configuration values and their files"), Map.entry("重新读取主配置和网页配置并立即应用", "Reload and apply main and web configuration"),
            Map.entry("重新读取配置并重启网页服务", "Reload configuration and restart the web service"),
            Map.entry("重新扫描历史统计并应用缓存相关修改", "Rescan historical statistics and apply cache settings"),
            Map.entry("首次安装时选择服务器白名单、模组白名单或无白名单；随后可选择是否读取榜单数据", "On first install, choose vanilla whitelist, RankBoard whitelist, or no whitelist; then choose whether to read statistics"),
            Map.entry("控制哪些在线玩家接收个人榜单数据；白名单和黑名单复用模组名单", "Choose which online players receive personal leaderboard data; whitelist and blacklist use the RankBoard list"),
            Map.entry("显示全服共享原版侧边栏；不会改变玩家名字颜色", "Show a server-wide vanilla sidebar; does not change player name colors"),
            Map.entry("检测并关闭当前其他模组计分板显示槽", "Detect and close currently displayed other-mod scoreboard slots"),
            Map.entry("设置全服名字颜色：全部位置、全部关闭或仅排行榜；立即生效", "Set global name colors: everywhere, disabled, or scoreboard only; takes effect immediately"),
            Map.entry("不填颜色时打开英中双语 16 色预选；颜色名支持 Tab 补全；立即生效", "Without a color, open bilingual 16-color presets; names support Tab completion; takes effect immediately"),
            Map.entry("自定义榜单显示名称；支持中文、英文和空格；立即生效", "Set a custom metric display name; Chinese, English, and spaces are supported; takes effect immediately"),
            Map.entry("设置网站按钮地址；重启网页服务后仍保留", "Set the website button address; it persists after web service restart"),
            Map.entry("管理网页服务器切换列表；权重越小越靠前，1 最先显示", "Manage web server switcher entries; lower weight appears first and 1 is highest priority"),
            Map.entry("查看缓存状态；仅首次安装或缓存无效时自动扫描，reload 手动重新读取统计文件", "View cache status; scans automatically only on first install or invalid cache, while reload manually rereads statistics"),
            Map.entry("导出当前服务器筛选后的排行榜为 Excel 可打开的 CSV 表格", "Export the currently filtered leaderboard as an Excel-compatible CSV table"),
            Map.entry("设置或查看历史扫描线程；0 自动，最多使用 50% 逻辑处理器；下次 cache reload 时生效", "Set or view history scan threads; 0 is automatic and uses up to 50% of CPUs; applies on the next cache reload"),
            Map.entry("开关玩家头像缓存；重新进服时生效", "Enable or disable player avatar caching; takes effect on the next join"),
            Map.entry("查看配置当前值、用途与生效方式", "View a configuration value, its purpose, and when it takes effect"),
            Map.entry("修改并保存配置；网页项会重启网页服务", "Change and save configuration; web options restart the web service"),
            Map.entry("列出所有配置当前值", "List all current configuration values"), Map.entry("查看单项当前值和用途", "View one configuration value and its purpose"));
    private static final Map<String, Map<String, String>> PACKS = new java.util.concurrent.ConcurrentHashMap<>();

    private RankBoardLanguage() { }

    static void load(MinecraftServer server) {
        Path directory = RankBoardConfig.configDirectory(server).resolve("lang");
        try {
            Files.createDirectories(directory);
            writeDefault(directory.resolve("zh_cn.json"), defaults(ZH_CN, false));
            writeDefault(directory.resolve("en_us.json"), defaults(EN_US, true));
            PACKS.clear();
            try (var files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String code = fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(java.util.Locale.ROOT);
                    if (!code.matches("[a-z0-9_-]{2,32}")) return;
                    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        Map<String, String> values = new java.util.HashMap<>();
                        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                                values.put(entry.getKey(), entry.getValue().getAsString());
                            }
                        }
                        PACKS.put(code, values);
                    } catch (IOException exception) {
                        RankBoardMod.LOGGER.warn("Could not read language pack {}", path, exception);
                    }
                });
            }
        } catch (IOException exception) {
            RankBoardMod.LOGGER.warn("Could not prepare RankBoard language directory {}", directory, exception);
        }
    }

    static TreeSet<String> codes() { return new TreeSet<>(PACKS.keySet()); }
    static boolean exists(String code) { return PACKS.containsKey(code); }

    static String text(ServerPlayerEntity player, String key, Object... arguments) {
        String code = LeaderboardState.get(PlayerCompat.server(player)).language(player.getUuid());
        Map<String, String> pack = PACKS.getOrDefault(code, PACKS.get("zh_cn"));
        String value = pack == null ? ZH_CN.getOrDefault(key, key) : pack.getOrDefault(key, ZH_CN.getOrDefault(key, key));
        for (int index = 0; index < arguments.length; index++) {
            value = value.replace("{" + index + "}", String.valueOf(arguments[index]));
        }
        return value;
    }
    static String help(ServerPlayerEntity player, String fallback) {
        String code = LeaderboardState.get(PlayerCompat.server(player)).language(player.getUuid());
        Map<String, String> pack = PACKS.getOrDefault(code, PACKS.get("zh_cn"));
        return pack == null ? fallback : pack.getOrDefault(helpKey(fallback), fallback);
    }
    // Keep Help keys readable in user-maintained JSON packs instead of exposing opaque hashes.
    private static String helpKey(String fallback) { return "help." + fallback; }
    private static Map<String, String> defaults(Map<String, String> base, boolean english) {
        Map<String, String> values = new java.util.HashMap<>(base);
        HELP_EN.forEach((zh, en) -> values.put(helpKey(zh), english ? en : zh));
        return values;
    }

    private static void writeDefault(Path path, Map<String, String> entries) throws IOException {
        JsonObject root = new JsonObject();
        if (Files.isRegularFile(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (RuntimeException exception) {
                RankBoardMod.LOGGER.warn("Could not merge default values into invalid language pack {}", path);
                return;
            }
        } else {
            try (InputStream stream = RankBoardLanguage.class.getClassLoader()
                    .getResourceAsStream("assets/rankboard/lang/" + path.getFileName())) {
                if (stream != null) {
                    root = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
        }
        JsonObject existing = root;
        boolean changed = !Files.exists(path) || entries.keySet().stream().anyMatch(key -> !existing.has(key));
        if (!changed) return;
        JsonObject target = root;
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> { if (!target.has(entry.getKey())) target.addProperty(entry.getKey(), entry.getValue()); });
        Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root)
                + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
