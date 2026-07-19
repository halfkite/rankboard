
package cn.bamgdam.rankboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Runtime settings shared by the in-game UI, history loader, and web dashboard. */
final class RankBoardConfig {
    private static final String MAIN_FILE = "rankboard.properties";
    private static final String WEB_FILE = "rankboard-web.properties";
    private static final String READ_LEGACY_CONFIG = "read-legacy-config";
    private static final List<Option> OPTIONS = List.of(
            option("history-files-per-second", "50", FileKind.MAIN, "历史统计", "每秒检查的玩家统计文件数；默认 50，范围 1-1000，修改后下次缓存重载生效。"),
            option("welcome-enabled", "true", FileKind.MAIN, "进服提示", "是否发送“欢迎来到”提示；默认 true。"),
            option("welcome-name", "auto", FileKind.MAIN, "进服提示", "欢迎语名称；默认 auto，自动读取服务器 MOTD 或单人存档名。"),
            option("join-menu-enabled", "true", FileKind.MAIN, "进服提示", "玩家进服时是否显示 /leaderboard 菜单；默认 true。"),
            option("join-web-hint-enabled", "false", FileKind.MAIN, "进服提示", "玩家进服时是否提示网页排行榜地址；默认 false。"),
            option("web-public-address", "", FileKind.MAIN, "进服提示", "对玩家展示的网页地址；默认留空，根据网页 host 和 port 生成。"),
            option("restore-scoreboard-on-join", "true", FileKind.MAIN, "客户端计分板", "玩家进服时是否恢复上一次选择的计分板；默认 true。"),
            option("look-up-sneak-menu-enabled", "true", FileKind.MAIN, "客户端计分板", "是否允许抬头并按住 Shift 打开排行榜菜单；默认 true。"),
            option("carousel-enabled", "true", FileKind.MAIN, "客户端计分板", "是否允许玩家使用榜单轮播；默认 true。"),
            option("carousel-interval-seconds", "30", FileKind.MAIN, "客户端计分板", "榜单轮播间隔秒数；默认 30，范围 3-3600。"),
            option("client-scoreboard-show-zero", "false", FileKind.MAIN, "客户端计分板", "个人侧边栏是否显示当前榜单数值为 0 的玩家；默认 false。"),
            option("scoreboard-switch-message-enabled", "true", FileKind.MAIN, "客户端计分板", "切换个人榜单后是否发送“已显示……”提示；默认 true。"),
            option("scoreboard-name-color-enabled", "true", FileKind.MAIN, "客户端计分板", "是否根据玩家当前榜单给名字着色；默认 true。"),
            option("player-name-color-render-mode", "legacy", FileKind.MAIN, "客户端计分板", "玩家名字颜色渲染模式；legacy 全部映射为原版 16 色，rgb 允许聊天、TAB 与排行榜使用精确 RGB。"),
            option("rankboard-defaults-version", "2", FileKind.MAIN, "配置兼容", "RankBoard 默认预设迁移版本；由模组自动维护，请勿手动降低。"),
            option("metric-label-food", "大胃王榜", FileKind.MAIN, "榜单名称", "大胃王榜在游戏和网页结果中显示的名称。"),
            option("metric-label-jumps", "跳跃榜", FileKind.MAIN, "榜单名称", "跳跃榜在游戏和网页结果中显示的名称。"),
            option("metric-label-mined", "挖掘榜", FileKind.MAIN, "榜单名称", "挖掘榜在游戏和网页结果中显示的名称。"),
            option("metric-label-placed", "放置榜", FileKind.MAIN, "榜单名称", "放置榜在游戏和网页结果中显示的名称。"),
            option("metric-label-kills", "击杀榜", FileKind.MAIN, "榜单名称", "击杀榜在游戏和网页结果中显示的名称。"),
            option("metric-label-deaths", "死亡榜", FileKind.MAIN, "榜单名称", "死亡榜在游戏和网页结果中显示的名称。"),
            option("metric-label-trades", "交易榜", FileKind.MAIN, "榜单名称", "交易榜在游戏和网页结果中显示的名称。"),
            option("metric-label-playtime", "在线榜", FileKind.MAIN, "榜单名称", "在线榜在游戏和网页结果中显示的名称。"),
            option("metric-label-elytra", "飞行榜", FileKind.MAIN, "榜单名称", "飞行榜在游戏和网页结果中显示的名称。"),
            option("metric-label-fishing", "钓鱼榜", FileKind.MAIN, "榜单名称", "钓鱼榜在游戏和网页结果中显示的名称。"),
            option("metric-label-damage", "受伤榜", FileKind.MAIN, "榜单名称", "受伤榜在游戏和网页结果中显示的名称。"),
            option("metric-label-dealt", "伤害输出榜", FileKind.MAIN, "榜单名称", "伤害输出榜在游戏和网页结果中显示的名称。"),
            option("metric-label-dropped", "丢垃圾榜", FileKind.MAIN, "榜单名称", "丢弃物品数量榜在游戏和网页结果中显示的名称。"),
            option("metric-label-picked", "拾荒榜", FileKind.MAIN, "榜单名称", "捡起物品数量榜在游戏和网页结果中显示的名称。"),
            option("metric-label-crafted", "合成榜", FileKind.MAIN, "榜单名称", "合成物品数量榜在游戏和网页结果中显示的名称。"),
            option("metric-label-redstone", "红石大蛇榜", FileKind.MAIN, "榜单名称", "放置电源、传输和机械类红石元件数量榜在游戏和网页结果中显示的名称。"),
            option("metric-color-food", "#FFAA00", FileKind.MAIN, "榜单颜色", "大胃王榜颜色；格式 #RRGGBB。"),
            option("metric-color-jumps", "#FF55FF", FileKind.MAIN, "榜单颜色", "跳跃榜颜色；格式 #RRGGBB。"),
            option("metric-color-mined", "#5555FF", FileKind.MAIN, "榜单颜色", "挖掘榜颜色；默认蓝色，格式 #RRGGBB。"),
            option("metric-color-placed", "#00AAAA", FileKind.MAIN, "榜单颜色", "放置榜颜色；默认深青色，格式 #RRGGBB。"),
            option("metric-color-kills", "#FF5555", FileKind.MAIN, "榜单颜色", "击杀榜颜色；格式 #RRGGBB。"),
            option("metric-color-deaths", "#AA0000", FileKind.MAIN, "榜单颜色", "死亡榜颜色；格式 #RRGGBB。"),
            option("metric-color-trades", "#55FF55", FileKind.MAIN, "榜单颜色", "交易榜颜色；默认绿色，格式 #RRGGBB。"),
            option("metric-color-playtime", "#55FFFF", FileKind.MAIN, "榜单颜色", "在线榜颜色；默认青色，格式 #RRGGBB。"),
            option("metric-color-elytra", "#FF55FF", FileKind.MAIN, "榜单颜色", "飞行榜颜色；格式 #RRGGBB。"),
            option("metric-color-fishing", "#0000AA", FileKind.MAIN, "榜单颜色", "钓鱼榜颜色；默认深蓝色，格式 #RRGGBB。"),
            option("metric-color-damage", "#FF5555", FileKind.MAIN, "榜单颜色", "受伤榜颜色；默认红色，格式 #RRGGBB。"),
            option("metric-color-dealt", "#FFAA00", FileKind.MAIN, "榜单颜色", "伤害输出榜颜色；默认金色，格式 #RRGGBB。"),
            option("metric-color-dropped", "#555555", FileKind.MAIN, "榜单颜色", "丢垃圾榜颜色；格式 #RRGGBB。"),
            option("metric-color-picked", "#55FF55", FileKind.MAIN, "榜单颜色", "拾荒榜颜色；格式 #RRGGBB。"),
            option("metric-color-crafted", "#FFAA00", FileKind.MAIN, "榜单颜色", "合成榜颜色；格式 #RRGGBB。"),
            option("metric-color-redstone", "#FF5555", FileKind.MAIN, "榜单颜色", "红石大蛇榜颜色；格式 #RRGGBB。"),
            option("scoreboard-title-color-enabled", "true", FileKind.MAIN, "客户端计分板", "是否让计分板标题跟随当前榜单颜色；默认 true。"),
            option("scoreboard-live-update-enabled", "true", FileKind.MAIN, "客户端计分板", "玩家行为改变统计时是否即时刷新对应榜单；默认 true。"),
            option("scoreboard-live-update-window-seconds", "30", FileKind.MAIN, "客户端计分板", "高频行为统计窗口秒数；默认 30，范围 1-300。"),
            option("scoreboard-live-update-threshold", "100", FileKind.MAIN, "客户端计分板", "窗口内超过此次数后进入降频；默认 100，范围 1-100000。"),
            option("scoreboard-live-update-throttle-seconds", "30", FileKind.MAIN, "客户端计分板", "高频榜单的最短刷新间隔秒数；默认 30，范围 1-3600。"),
            option("foreign-scoreboard-blocking-mode", "ask", FileKind.MAIN, "客户端计分板", "其他模组计分板屏蔽模式；默认 ask 不自动屏蔽并提示 OP 选择，可选 ask、enabled、disabled。"),
            option("mod-whitelist-enabled", "false", FileKind.MAIN, "玩家筛选", "是否只读取 config/rankboard/rankboard-whitelist.json 中的玩家；默认 false，保留原有服务器白名单逻辑。"),
            option("help-visibility", "all", FileKind.MAIN, "权限与帮助", "帮助可见范围；默认 all，可选 all、op、hidden。"),
            option("avatar-cache-enabled", "true", FileKind.MAIN, "玩家头像缓存", "是否缓存进服玩家的皮肤头像；默认 true。"),
            option("avatar-cache-days", "7", FileKind.MAIN, "玩家头像缓存", "头像缓存有效天数；默认 7，范围 1-365。"),
            option("host", "0.0.0.0", FileKind.WEB, "网页监听", "网页监听地址；默认 0.0.0.0，表示监听所有 IPv4 地址。"),
            option("port", "8765", FileKind.WEB, "网页监听", "网页监听端口；默认 8765，范围 1-65535。"),
            option("web-data-requests-per-second", "1", FileKind.WEB, "请求限流", "数据接口基础请求间隔；值 1 表示每秒最多 1 次。30 秒内超过 30 次后，固定 30 分钟改为每 5 秒 1 次。"),
            option("web-icon-request-interval-seconds", "3", FileKind.WEB, "请求限流", "图片基础请求间隔秒数；默认 3。30 秒内超过 6 次后，固定 30 分钟改为每 15 秒 1 次。"),
            option("web-ranking-refresh-interval-seconds", "30", FileKind.WEB, "网页数据", "网页排行榜数据快照刷新间隔秒数；默认 30，范围 1-3600。"),
            option("server-name", "auto", FileKind.WEB, "网页显示", "网页显示的服务器名称；默认 auto，自动读取服务器 MOTD。"),
            option("website-icon", "server-icon.png", FileKind.WEB, "网页显示", "网页图标文件名；只能使用 config/rankboard/ 目录内的文件，默认 server-icon.png。")
    );
    private static volatile RankBoardConfig current = defaults();
    private static volatile Properties mainProperties = defaultsFor(FileKind.MAIN);
    private static volatile Properties webProperties = defaultsFor(FileKind.WEB);

    final int historyFilesPerSecond;
    final boolean welcomeEnabled;
    final boolean joinMenuEnabled;
    final boolean restoreBoardOnJoin;
    final boolean lookUpSneakMenuEnabled;
    final boolean carouselEnabled;
    final int carouselIntervalSeconds;
    final boolean clientScoreboardShowZero;
    final boolean scoreboardSwitchMessageEnabled;
    final NameColorMode nameColorMode;
    final NameColorRenderMode nameColorRenderMode;
    final boolean scoreboardTitleColorEnabled;
    final boolean scoreboardLiveUpdateEnabled;
    final int scoreboardLiveUpdateWindowSeconds;
    final int scoreboardLiveUpdateThreshold;
    final int scoreboardLiveUpdateThrottleSeconds;
    final ForeignScoreboardPolicy foreignScoreboardPolicy;
    final boolean modWhitelistEnabled;
    final boolean joinWebHintEnabled;
    final boolean avatarCacheEnabled;
    final int avatarCacheDays;
    final String welcomeName;
    final String webPublicAddress;
    final HelpVisibility helpVisibility;

    private RankBoardConfig(Properties properties) {
        historyFilesPerSecond = integer(properties, "history-files-per-second", 50, 1, 1000);
        welcomeEnabled = bool(properties, "welcome-enabled", true);
        joinMenuEnabled = bool(properties, "join-menu-enabled", true);
        restoreBoardOnJoin = bool(properties, "restore-scoreboard-on-join", true);
        lookUpSneakMenuEnabled = bool(properties, "look-up-sneak-menu-enabled", true);
        carouselEnabled = bool(properties, "carousel-enabled", true);
        carouselIntervalSeconds = integer(properties, "carousel-interval-seconds", 30, 3, 3600);
        clientScoreboardShowZero = bool(properties, "client-scoreboard-show-zero", false);
        scoreboardSwitchMessageEnabled = bool(properties, "scoreboard-switch-message-enabled", true);
        nameColorMode = NameColorMode.parse(properties.getProperty("scoreboard-name-color-enabled", "true"));
        nameColorRenderMode = NameColorRenderMode.parse(properties.getProperty("player-name-color-render-mode", "legacy"));
        scoreboardTitleColorEnabled = bool(properties, "scoreboard-title-color-enabled", true);
        scoreboardLiveUpdateEnabled = bool(properties, "scoreboard-live-update-enabled", true);
        scoreboardLiveUpdateWindowSeconds = integer(properties, "scoreboard-live-update-window-seconds", 30, 1, 300);
        scoreboardLiveUpdateThreshold = integer(properties, "scoreboard-live-update-threshold", 100, 1, 100000);
        scoreboardLiveUpdateThrottleSeconds = integer(properties, "scoreboard-live-update-throttle-seconds", 30, 1, 3600);
        foreignScoreboardPolicy = ForeignScoreboardPolicy.parse(properties.getProperty("foreign-scoreboard-blocking-mode", "ask"));
        modWhitelistEnabled = bool(properties, "mod-whitelist-enabled", false);
        joinWebHintEnabled = bool(properties, "join-web-hint-enabled", false);
        avatarCacheEnabled = bool(properties, "avatar-cache-enabled", true);
        avatarCacheDays = integer(properties, "avatar-cache-days", 7, 1, 365);
        welcomeName = properties.getProperty("welcome-name", "auto").strip();
        webPublicAddress = properties.getProperty("web-public-address", "").strip();
        helpVisibility = HelpVisibility.parse(properties.getProperty("help-visibility", "all"));
    }

    static synchronized RankBoardConfig load(MinecraftServer server) {
        try {
            mainProperties = loadMigratedProperties(server, MAIN_FILE, FileKind.MAIN);
            current = new RankBoardConfig(mainProperties);
        } catch (IOException exception) {
            RankBoardMod.LOGGER.warn("Could not load {}; using defaults", configDirectory(server).resolve(MAIN_FILE), exception);
            mainProperties = defaultsFor(FileKind.MAIN);
            current = new RankBoardConfig(mainProperties);
        }
        return current;
    }

    static synchronized Properties loadWeb(MinecraftServer server) throws IOException {
        webProperties = loadMigratedProperties(server, WEB_FILE, FileKind.WEB);
        return copyOf(webProperties);
    }

    static RankBoardConfig get() { return current; }

    static Path configDirectory(MinecraftServer server) {
        return server.getServerDirectory().resolve("config").resolve("rankboard");
    }

    static List<String> optionKeys() {
        return OPTIONS.stream().map(Option::key).toList();
    }

    static boolean isKnownOption(String key) {
        return findOption(key) != null;
    }

    static String value(String key) {
        Option option = requireOption(key);
        Properties properties = option.fileKind == FileKind.MAIN ? mainProperties : webProperties;
        return properties.getProperty(option.key, option.defaultValue);
    }

    static String description(String key) {
        return requireOption(key).comment;
    }

    static String defaultValue(String key) { return requireOption(key).defaultValue; }

    int metricColor(RankBoardMod.Metric metric) {
        return Integer.parseInt(mainProperties.getProperty("metric-color-" + metric.command,
                defaultValue("metric-color-" + metric.command)).substring(1), 16);
    }

    String metricLabel(RankBoardMod.Metric metric) {
        return mainProperties.getProperty("metric-label-" + metric.command,
                defaultValue("metric-label-" + metric.command));
    }

    static boolean isWebOption(String key) {
        return requireOption(key).fileKind == FileKind.WEB;
    }

    static synchronized String set(MinecraftServer server, String key, String rawValue) throws IOException {
        Option option = requireOption(key);
        String value = normalize(option, rawValue);
        Properties updated = copyOf(option.fileKind == FileKind.MAIN ? mainProperties : webProperties);
        updated.setProperty(option.key, value);
        writeConfig(configDirectory(server).resolve(option.fileKind.fileName), updated, option.fileKind);
        if (option.fileKind == FileKind.MAIN) {
            mainProperties = updated;
            current = new RankBoardConfig(updated);
        } else {
            webProperties = updated;
        }
        return value;
    }

    boolean helpVisible(CommandSourceStack source) {
        return switch (helpVisibility) {
            case ALL -> true;
            case OP -> CommandPermissionCompat.has(source, 2);
            case HIDDEN -> false;
        };
    }

    String displayName(MinecraftServer server) {
        if (!welcomeName.isEmpty() && !welcomeName.equalsIgnoreCase("auto")) return welcomeName;
        if (!server.isDedicatedServer()) return server.getWorldData().getLevelName();
        Path propertiesPath = server.getServerDirectory().resolve("server.properties");
        Properties serverProperties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
            serverProperties.load(reader);
            String motd = serverProperties.getProperty("motd", "Minecraft Server")
                    .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                    .replace("\\n", " ").strip();
            return motd.isEmpty() ? "Minecraft Server" : motd;
        } catch (IOException exception) {
            return server.getWorldData().getLevelName();
        }
    }

    String webAddress(MinecraftServer server) {
        if (!webPublicAddress.isEmpty()) return webPublicAddress;
        Properties web = webProperties;
        String host = web.getProperty("host", "").strip();
        if (host.isEmpty() || host.equals("0.0.0.0") || host.equals("::")) host = "127.0.0.1";
        if (host == null || host.isBlank()) host = "127.0.0.1";
        return host + ":" + web.getProperty("port", "8765").strip();
    }

    private static Properties loadMigratedProperties(MinecraftServer server, String fileName,
                                                       FileKind fileKind) throws IOException {
        Path directory = configDirectory(server);
        Path target = directory.resolve(fileName);
        Path legacy = server.getServerDirectory().resolve(fileName);
        Properties properties = new Properties();
        boolean targetExists = Files.isRegularFile(target);
        if (targetExists) loadInto(target, properties, false);
        boolean readLegacy = !targetExists
                || !"false".equalsIgnoreCase(properties.getProperty(READ_LEGACY_CONFIG, "true").strip());
        boolean legacyLoaded = false;
        if (readLegacy && Files.isRegularFile(legacy)) {
            loadInto(legacy, properties, true);
            legacyLoaded = true;
        }
        // Replaces the former per-minute image limit with an exact per-image interval.
        if (properties.remove("web-icon-requests-per-minute") != null
                && !properties.containsKey("web-icon-request-interval-seconds")) {
            properties.setProperty("web-icon-request-interval-seconds", "3");
        }
        if (fileKind == FileKind.MAIN) migrateDefaultPreset(properties);
        for (Option option : OPTIONS) {
            if (option.fileKind == fileKind) properties.putIfAbsent(option.key, option.defaultValue);
        }
        if (readLegacy) properties.setProperty(READ_LEGACY_CONFIG, "false");
        writeConfig(target, properties, fileKind);
        if (legacyLoaded) {
            try {
                Files.delete(legacy);
            } catch (IOException exception) {
                RankBoardMod.LOGGER.warn("Could not delete migrated legacy RankBoard config {}", legacy, exception);
            }
            RankBoardMod.LOGGER.info("Migrated legacy RankBoard config {} to {}", legacy, target);
        }
        return properties;
    }

    private static void migrateDefaultPreset(Properties properties) {
        int version;
        try { version = Integer.parseInt(properties.getProperty("rankboard-defaults-version", "1")); }
        catch (NumberFormatException ignored) { version = 1; }
        if (version >= 2) return;
        replaceOldDefault(properties, "metric-label-playtime", "在线时间榜", "在线榜");
        replaceOldDefault(properties, "metric-label-elytra", "鞘翅飞行榜", "飞行榜");
        replaceOldDefault(properties, "metric-label-damage", "受伤害榜", "受伤榜");
        replaceOldDefault(properties, "metric-color-trades", "#55FFFF", "#55FF55");
        replaceOldDefault(properties, "metric-color-playtime", "#00AAAA", "#55FFFF");
        replaceOldDefault(properties, "metric-color-damage", "#FFFF55", "#FF5555");
        replaceOldDefault(properties, "metric-color-placed", "#00AA00", "#00AAAA");
        replaceOldDefault(properties, "metric-color-mined", "#AAAAAA", "#5555FF");
        replaceOldDefault(properties, "metric-color-fishing", "#5555FF", "#0000AA");
        properties.setProperty("rankboard-defaults-version", "2");
    }

    private static void replaceOldDefault(Properties properties, String key, String oldValue, String newValue) {
        if (oldValue.equalsIgnoreCase(properties.getProperty(key, oldValue))) properties.setProperty(key, newValue);
    }

    private static void loadInto(Path path, Properties target, boolean onlyMissing) throws IOException {
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        }
        for (Map.Entry<Object, Object> entry : loaded.entrySet()) {
            if (!onlyMissing || !target.containsKey(entry.getKey())) target.put(entry.getKey(), entry.getValue());
        }
    }

    private static void writeConfig(Path path, Properties properties, FileKind fileKind) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(READ_LEGACY_CONFIG + "="
                        + properties.getProperty(READ_LEGACY_CONFIG, "true") + "\n");
                writer.write(fileKind == FileKind.MAIN
                        ? "# RankBoard 主配置（Minecraft 26.x）\n"
                        : "# RankBoard 网页配置（Minecraft 26.x）\n");
                writer.write("# 可手动编辑，也可由 OP 使用 /leaderboard config set <配置项> <值> 修改。\n");
                writer.write("# 修改前请阅读每项说明；布尔值使用 true 或 false。\n");
                String previousSection = null;
                for (Option option : OPTIONS) {
                    if (option.fileKind != fileKind) continue;
                    if (!option.section.equals(previousSection)) {
                        writer.write("\n# --- " + option.section + " ---\n");
                        previousSection = option.section;
                    }
                    writer.write("# " + option.comment + "\n");
                    writer.write(escape(option.key, true) + "="
                            + escape(properties.getProperty(option.key, option.defaultValue), false) + "\n");
                }
                List<String> unknown = new ArrayList<>();
                for (String key : properties.stringPropertyNames()) {
                    if (READ_LEGACY_CONFIG.equals(key)) continue;
                    Option option = findOption(key);
                    if (option == null || option.fileKind != fileKind) unknown.add(key);
                }
                if (!unknown.isEmpty()) {
                    unknown.sort(String::compareTo);
                    writer.write("\n# --- 从旧配置保留的未知配置项 ---\n");
                    writer.write("# RankBoard 当前版本不会主动使用这些配置项。\n");
                    for (String key : unknown) {
                        writer.write(escape(key, true) + "=" + escape(properties.getProperty(key, ""), false) + "\n");
                    }
                }
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String normalize(Option option, String rawValue) {
        String value = rawValue.strip();
        if (option.key.startsWith("metric-color-")) return normalizedColor(value);
        if (option.key.startsWith("metric-label-")) return normalizedLabel(value);
        return switch (option.key) {
            case "history-files-per-second" -> normalizedInteger(value, 1, 1000);
            case "carousel-interval-seconds" -> normalizedInteger(value, 3, 3600);
            case "avatar-cache-days" -> normalizedInteger(value, 1, 365);
            case "port" -> normalizedInteger(value, 1, 65535);
            case "web-data-requests-per-second" -> normalizedInteger(value, 1, 100);
            case "web-icon-request-interval-seconds" -> normalizedInteger(value, 1, 3600);
            case "scoreboard-live-update-window-seconds" -> normalizedInteger(value, 1, 300);
            case "scoreboard-live-update-threshold" -> normalizedInteger(value, 1, 100000);
            case "scoreboard-live-update-throttle-seconds", "web-ranking-refresh-interval-seconds" -> normalizedInteger(value, 1, 3600);
            case "welcome-enabled", "join-menu-enabled", "join-web-hint-enabled",
                    "restore-scoreboard-on-join", "look-up-sneak-menu-enabled", "carousel-enabled",
                    "client-scoreboard-show-zero", "scoreboard-switch-message-enabled",
                    "scoreboard-title-color-enabled",
                    "scoreboard-live-update-enabled", "avatar-cache-enabled", "mod-whitelist-enabled" -> normalizedBoolean(value);
            case "help-visibility" -> switch (value.toLowerCase(Locale.ROOT)) {
                case "all" -> "all";
                case "op", "ops" -> "op";
                case "hidden", "off", "none" -> "hidden";
                default -> throw new IllegalArgumentException("可用值：all、op、hidden");
            };
            case "foreign-scoreboard-blocking-mode" -> switch (value.toLowerCase(Locale.ROOT)) {
                case "ask" -> "ask";
                case "enabled", "enable", "on", "true" -> "enabled";
                case "disabled", "disable", "off", "false" -> "disabled";
                default -> throw new IllegalArgumentException("可用值：ask、enabled、disabled");
            };
            case "scoreboard-name-color-enabled" -> NameColorMode.parse(value).serialized;
            case "player-name-color-render-mode" -> NameColorRenderMode.parse(value).serialized;
            case "host", "website-icon" -> {
                if (value.isEmpty()) throw new IllegalArgumentException(option.key + " 不能为空");
                yield value;
            }
            case "welcome-name", "server-name" -> value.isEmpty() ? "auto" : value;
            case "web-public-address" -> value.equalsIgnoreCase("auto") ? "" : value;
            default -> value;
        };
    }

    private static String normalizedBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> "true";
            case "false", "off", "no", "0" -> "false";
            default -> throw new IllegalArgumentException("布尔配置仅支持 true/false（也可使用 on/off）");
        };
    }

    private static String normalizedColor(String value) {
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("颜色格式必须为 #RRGGBB");
        return value.toUpperCase(Locale.ROOT);
    }

    private static String normalizedLabel(String value) {
        if (value.isEmpty() || value.length() > 32) throw new IllegalArgumentException("榜单名称长度必须为 1-32 个字符");
        if (value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("榜单名称不能包含控制字符");
        return value;
    }

    private static String normalizedInteger(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return Integer.toString(parsed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数值范围必须为 " + minimum + "-" + maximum);
        }
    }

    private static String escape(String value, boolean key) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '=', ':' -> {
                    if (key) escaped.append('\\');
                    escaped.append(character);
                }
                case '#', '!' -> {
                    if (key || index == 0) escaped.append('\\');
                    escaped.append(character);
                }
                case ' ' -> {
                    if (index == 0 || key) escaped.append('\\');
                    escaped.append(character);
                }
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static Properties defaultsFor(FileKind fileKind) {
        Properties defaults = new Properties();
        for (Option option : OPTIONS) {
            if (option.fileKind == fileKind) defaults.setProperty(option.key, option.defaultValue);
        }
        return defaults;
    }

    private static Properties copyOf(Properties original) {
        Properties copy = new Properties();
        copy.putAll(original);
        return copy;
    }

    private static RankBoardConfig defaults() { return new RankBoardConfig(defaultsFor(FileKind.MAIN)); }

    private static Option option(String key, String defaultValue, FileKind fileKind, String section, String comment) {
        return new Option(key, defaultValue, fileKind, section, comment);
    }

    private static Option requireOption(String key) {
        Option option = findOption(key);
        if (option == null) throw new IllegalArgumentException("未知配置项：" + key);
        return option;
    }

    private static Option findOption(String key) {
        for (Option option : OPTIONS) if (option.key.equals(key)) return option;
        return null;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties properties, String key, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum,
                    Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum FileKind {
        MAIN(MAIN_FILE), WEB(WEB_FILE);
        final String fileName;
        FileKind(String fileName) { this.fileName = fileName; }
    }

    private record Option(String key, String defaultValue, FileKind fileKind, String section, String comment) { }

    enum HelpVisibility {
        ALL, OP, HIDDEN;

        static HelpVisibility parse(String value) {
            return switch (value.strip().toLowerCase(Locale.ROOT)) {
                case "op", "ops" -> OP;
                case "hidden", "off", "none" -> HIDDEN;
                default -> ALL;
            };
        }
    }

    enum NameColorMode {
        ENABLED("true"), DISABLED("false"), SCOREBOARD_ONLY("scoreboard-only");

        final String serialized;
        NameColorMode(String serialized) { this.serialized = serialized; }

        static NameColorMode parse(String value) {
            return switch (value.strip().toLowerCase(Locale.ROOT)) {
                case "true", "on", "enabled", "enable" -> ENABLED;
                case "false", "off", "disabled", "disable" -> DISABLED;
                case "scoreboard-only", "only-scoreboard" -> SCOREBOARD_ONLY;
                default -> throw new IllegalArgumentException("可用值：true、false、scoreboard-only");
            };
        }
    }

    enum NameColorRenderMode {
        LEGACY("legacy"), RGB("rgb");

        final String serialized;
        NameColorRenderMode(String serialized) { this.serialized = serialized; }

        static NameColorRenderMode parse(String value) {
            return switch (value.strip().toLowerCase(Locale.ROOT)) {
                case "legacy" -> LEGACY;
                case "rgb" -> RGB;
                default -> throw new IllegalArgumentException("可用值：legacy、rgb");
            };
        }
    }

    enum ForeignScoreboardPolicy {
        ASK, ENABLED, DISABLED;

        static ForeignScoreboardPolicy parse(String value) {
            return switch (value.strip().toLowerCase(Locale.ROOT)) {
                case "enabled", "enable", "on", "true" -> ENABLED;
                case "disabled", "disable", "off", "false" -> DISABLED;
                default -> ASK;
            };
        }
    }
}
