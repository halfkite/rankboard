package cn.bamgdam.rankboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Loads editable JSON language packs for the Mojang-mapped 26.x build. */
final class RankBoardLanguage {
    private static final Map<String, String> ZH = Map.ofEntries(
            Map.entry("language.name", "中文"), Map.entry("language.prompt", "请选择语言"),
            Map.entry("language.selected", "已选择 {0}。"),
            Map.entry("language.default_prompt", "是否将 {0} 设为全服默认聊天语言？"),
            Map.entry("language.default_button", "设为全服默认"),
            Map.entry("language.default_tooltip", "将此语言应用到全服玩家并写入配置"),
            Map.entry("language.default_set", "已将 {0} 设为全服默认聊天语言，更新了 {1} 位玩家。"),
            Map.entry("help.click_to_fill", "点击填入指令栏"), Map.entry("menu.help", "帮助"),
            Map.entry("menu.website", "打开网站"), Map.entry("menu.scores", "查询分数"),
            Map.entry("menu.close_board", "关闭榜单"), Map.entry("menu.open_board", "开启榜单"),
            Map.entry("menu.close_look", "关闭抬头蹲起"), Map.entry("menu.open_look", "开启抬头蹲起"),
            Map.entry("menu.carousel", "轮播"), Map.entry("help.player", "玩家指令"),
            Map.entry("help.scoreboard", "计分板"), Map.entry("help.web", "网页与配置"),
            Map.entry("help.admin", "OP 管理"));
    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("language.name", "English"), Map.entry("language.prompt", "Select language"),
            Map.entry("language.selected", "Language set to {0}."),
            Map.entry("language.default_prompt", "Set {0} as the server-wide default chat language?"),
            Map.entry("language.default_button", "Set server default"),
            Map.entry("language.default_tooltip", "Apply this language to all players and save it to the config"),
            Map.entry("language.default_set", "Set {0} as the server-wide default chat language for {1} players."),
            Map.entry("help.click_to_fill", "Click to fill the chat command"), Map.entry("menu.help", "Help"),
            Map.entry("menu.website", "Open website"), Map.entry("menu.scores", "My scores"),
            Map.entry("menu.close_board", "Close board"), Map.entry("menu.open_board", "Open board"),
            Map.entry("menu.close_look", "Disable look menu"), Map.entry("menu.open_look", "Enable look menu"),
            Map.entry("menu.carousel", "Carousel"), Map.entry("help.player", "Player"),
            Map.entry("help.scoreboard", "Scoreboard"), Map.entry("help.web", "Web & config"),
            Map.entry("help.admin", "OP admin"));
    private static final Map<String, String> HELP_EN = Map.ofEntries(
            Map.entry("打开可点击菜单", "Open the clickable menu"),
            Map.entry("查询所有个人统计并显示总览", "Show all personal statistics and the overview"),
            Map.entry("查看排行榜", "View a leaderboard"), Map.entry("控制榜单轮播", "Control leaderboard carousel"),
            Map.entry("关闭个人计分板", "Close the personal scoreboard"),
            Map.entry("设置网站按钮地址，默认 127.0.0.1:8765", "Set the website button address; default 127.0.0.1:8765"),
            Map.entry("管理统计缓存", "Manage the statistics cache"),
            Map.entry("管理模组白名单", "Manage the RankBoard whitelist"),
            Map.entry("控制白名单筛选", "Control whitelist filtering"),
            Map.entry("设置全服名字颜色模式", "Set the server-wide name color mode"),
            Map.entry("自定义榜单显示名称", "Customize a leaderboard display name"),
            Map.entry("恢复默认颜色", "Restore default leaderboard colors"),
            Map.entry("导出当前服务器筛选后的排行榜为 Excel 可打开的 CSV 表格", "Export the filtered leaderboard as an Excel-compatible CSV"),
            Map.entry("查看或修改配置", "View or edit configuration"),
            Map.entry("选择图标自动取色、默认蓝色或 RGB 网页主题", "Choose icon, blue, or RGB web colors"),
            Map.entry("首次安装时选择服务器白名单、模组白名单或无白名单；随后可选择是否读取榜单数据", "Choose a whitelist mode on first install, then choose whether to read statistics"));
    private static final Map<String, Map<String, String>> PACKS = new ConcurrentHashMap<>();

    private RankBoardLanguage() { }

    static void load(MinecraftServer server) {
        Path directory = RankBoardConfig.configDirectory(server).resolve("lang");
        try {
            Files.createDirectories(directory);
            mergeDefaults(directory.resolve("zh_cn.json"), defaults(ZH, false));
            mergeDefaults(directory.resolve("en_us.json"), defaults(EN, true));
            PACKS.clear();
            try (var files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(RankBoardLanguage::readPack);
            }
        } catch (IOException exception) {
            RankBoardMod.LOGGER.warn("Could not prepare RankBoard language directory {}", directory, exception);
        }
    }

    private static void readPack(Path path) {
        String file = path.getFileName().toString();
        String code = file.substring(0, file.length() - 5).toLowerCase(Locale.ROOT);
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet())
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString())
                    values.put(entry.getKey(), entry.getValue().getAsString());
            PACKS.put(code, values);
        } catch (Exception exception) {
            RankBoardMod.LOGGER.warn("Could not read language pack {}", path, exception);
        }
    }

    static TreeSet<String> codes() { return new TreeSet<>(PACKS.keySet()); }
    static boolean exists(String code) { return PACKS.containsKey(code); }

    static String text(ServerPlayer player, String key, Object... arguments) {
        String code = LeaderboardState.get(PlayerCompat.server(player)).language(player.getUUID());
        Map<String, String> pack = PACKS.getOrDefault(code, PACKS.get("zh_cn"));
        String value = pack == null ? ZH.getOrDefault(key, key) : pack.getOrDefault(key, ZH.getOrDefault(key, key));
        for (int i = 0; i < arguments.length; i++) value = value.replace("{" + i + "}", String.valueOf(arguments[i]));
        return value;
    }

    static String help(ServerPlayer player, String fallback) {
        String code = LeaderboardState.get(PlayerCompat.server(player)).language(player.getUUID());
        Map<String, String> pack = PACKS.getOrDefault(code, PACKS.get("zh_cn"));
        return pack == null ? fallback : pack.getOrDefault("help." + fallback, fallback);
    }

    private static Map<String, String> defaults(Map<String, String> base, boolean english) {
        Map<String, String> result = new HashMap<>(base);
        HELP_EN.forEach((zh, en) -> result.put("help." + zh, english ? en : zh));
        return result;
    }

    private static void mergeDefaults(Path path, Map<String, String> entries) throws IOException {
        JsonObject root = new JsonObject();
        if (Files.isRegularFile(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (RuntimeException ignored) { return; }
        } else {
            try (InputStream stream = RankBoardLanguage.class.getClassLoader().getResourceAsStream("assets/rankboard/lang/" + path.getFileName())) {
                if (stream != null) root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        boolean changed = !Files.exists(path);
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!root.has(entry.getKey())) { root.addProperty(entry.getKey(), entry.getValue()); changed = true; }
        }
        if (changed) Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
