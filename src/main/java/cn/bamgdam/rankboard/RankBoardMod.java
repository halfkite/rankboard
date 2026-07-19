package cn.bamgdam.rankboard;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.net.URI;

public final class RankBoardMod implements ModInitializer {
    public static final String MOD_ID = "rankboard";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int REFRESH_INTERVAL_TICKS = 600;
    private static final Set<UUID> LOOK_MENU_HELD = new HashSet<>();
    private static final Set<String> REDSTONE_COMPONENTS = Set.of(
            "redstone", "redstone_torch", "repeater", "comparator", "observer", "piston", "sticky_piston",
            "dispenser", "dropper", "hopper", "lever", "tripwire_hook", "target", "daylight_detector",
            "note_block", "redstone_block", "sculk_sensor", "calibrated_sculk_sensor", "lightning_rod",
            "trapped_chest", "powered_rail", "detector_rail", "activator_rail", "rail", "lectern", "jukebox", "bell",
            "redstone_lamp", "tnt", "big_dripleaf", "crafter", "command_block", "chain_command_block",
            "repeating_command_block");
    private static final List<ColorPreset> COLOR_PRESETS = List.of(
            new ColorPreset("black", "黑色", Formatting.BLACK), new ColorPreset("dark_blue", "深蓝色", Formatting.DARK_BLUE),
            new ColorPreset("dark_green", "深绿色", Formatting.DARK_GREEN), new ColorPreset("dark_aqua", "深青色", Formatting.DARK_AQUA),
            new ColorPreset("dark_red", "深红色", Formatting.DARK_RED), new ColorPreset("dark_purple", "深紫色", Formatting.DARK_PURPLE),
            new ColorPreset("gold", "金色", Formatting.GOLD), new ColorPreset("gray", "灰色", Formatting.GRAY),
            new ColorPreset("dark_gray", "深灰色", Formatting.DARK_GRAY), new ColorPreset("blue", "蓝色", Formatting.BLUE),
            new ColorPreset("green", "绿色", Formatting.GREEN), new ColorPreset("aqua", "青色", Formatting.AQUA),
            new ColorPreset("red", "红色", Formatting.RED), new ColorPreset("light_purple", "粉紫色", Formatting.LIGHT_PURPLE),
            new ColorPreset("yellow", "黄色", Formatting.YELLOW), new ColorPreset("white", "白色", Formatting.WHITE));
    private int ticks;

    @Override
    public void onInitialize() {
        LOGGER.info("RankBoard initialized for Minecraft 1.21.x");
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RankBoardConfig.load(server);
            RankBoardWhitelist.load(server);
            BoardService.restoreGlobal(server);
            BoardService.enforceForeignScoreboardPolicy(server);
            StatReader.startWarmup(server);
            WebDashboard.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PlayerNameColors.clear(server);
            BoardService.clearSessions();
            WebDashboard.stop();
            StatReader.stopWarmup();
            LOOK_MENU_HELD.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            LeaderboardState.get(server).ensurePlayer(player);
            AvatarCache.cacheOnJoin(server, player);
            BoardService.restore(player);
            sendJoinExperience(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StatReader.reloadPlayer(server, handler.getPlayer().getUuid());
            LOOK_MENU_HELD.remove(handler.getPlayer().getUuid());
            BoardService.disconnect(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BoardService.tickCarousel(server);
            BoardService.tickActivity(server);
            handleLookUpSneakMenu(server);
            if (++ticks >= REFRESH_INTERVAL_TICKS) {
                ticks = 0;
                LeaderboardState.get(server).rollPeriods(server);
                BoardService.refreshAll(server);
                BoardService.enforceForeignScoreboardPolicy(server);
            }
        });
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
                                  CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("leaderboard")
                .requires(source -> CommandPermissionCompat.has(source, 0)).executes(context -> menu(context.getSource()));
        root.then(CommandManager.literal("help").requires(source -> RankBoardConfig.get().helpVisible(source))
                .executes(context -> helpGrouped(context.getSource(), "menu"))
                .then(CommandManager.literal("player").executes(context -> helpGrouped(context.getSource(), "player")))
                .then(CommandManager.literal("scoreboard").executes(context -> helpGrouped(context.getSource(), "scoreboard")))
                .then(CommandManager.literal("web").executes(context -> helpGrouped(context.getSource(), "web")))
                .then(CommandManager.literal("config").requires(source -> CommandPermissionCompat.has(source, 2))
                        .executes(context -> helpGrouped(context.getSource(), "config"))
                        .then(CommandManager.literal("general").executes(context -> helpGrouped(context.getSource(), "config-general")))
                        .then(CommandManager.literal("scoreboard").executes(context -> helpGrouped(context.getSource(), "config-scoreboard")))
                        .then(CommandManager.literal("web").executes(context -> helpGrouped(context.getSource(), "config-web"))))
                .then(CommandManager.literal("admin").requires(source -> CommandPermissionCompat.has(source, 2))
                        .executes(context -> helpGrouped(context.getSource(), "admin"))
                        .then(CommandManager.literal("players").executes(context -> helpGrouped(context.getSource(), "admin-players")))
                        .then(CommandManager.literal("scoreboard").executes(context -> helpGrouped(context.getSource(), "admin-scoreboard")))
                        .then(CommandManager.literal("web").executes(context -> helpGrouped(context.getSource(), "admin-web")))
                        .then(CommandManager.literal("config").executes(context -> helpGrouped(context.getSource(), "admin-config")))));
        root.then(CommandManager.literal("mine")
                .executes(context -> showMyScores(context.getSource(), -1, "总计"))
                .then(CommandManager.literal("all").executes(context -> showMyScores(context.getSource(), -1, "总计")))
                .then(CommandManager.literal("day").executes(context -> showMyScores(context.getSource(), 1, "最近一日")))
                .then(CommandManager.literal("week").executes(context -> showMyScores(context.getSource(), 7, "最近一周")))
                .then(CommandManager.literal("month").executes(context -> showMyScores(context.getSource(), 30, "最近一月"))));
        root.then(CommandManager.literal("carousel")
                .then(CommandManager.literal("true").executes(context -> BoardService.setCarousel(context.getSource(), true)))
                .then(CommandManager.literal("false").executes(context -> BoardService.setCarousel(context.getSource(), false)))
                .then(CommandManager.literal("on").executes(context -> BoardService.setCarousel(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> BoardService.setCarousel(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> BoardService.carouselStatus(context.getSource()))));
        root.then(CommandManager.literal("display")
                .then(CommandManager.literal("off").executes(context -> BoardService.disable(context.getSource()))
                        .then(CommandManager.argument("player", EntityArgumentType.player()).requires(source -> CommandPermissionCompat.has(source, 2))
                                .executes(context -> BoardService.disable(context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(buildSelectionCommands(false)));
        root.then(CommandManager.literal("namecolor").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("true").executes(context -> setNameColor(context.getSource(), "true")))
                .then(CommandManager.literal("false").executes(context -> setNameColor(context.getSource(), "false")))
                .then(CommandManager.literal("scoreboard-only").executes(context -> setNameColor(context.getSource(), "scoreboard-only")))
                .then(CommandManager.literal("on").executes(context -> setNameColor(context.getSource(), "true")))
                .then(CommandManager.literal("off").executes(context -> setNameColor(context.getSource(), "false")))
                .then(CommandManager.literal("status").executes(context -> nameColorStatus(context.getSource()))));
        root.then(buildColorCommands());
        root.then(buildLabelCommands());
        root.then(CommandManager.literal("lookmenu")
                .then(CommandManager.literal("true").executes(context -> setLookMenu(context.getSource(), true)))
                .then(CommandManager.literal("false").executes(context -> setLookMenu(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> lookMenuStatus(context.getSource())))
                .then(CommandManager.literal("global").requires(source -> CommandPermissionCompat.has(source, 2))
                        .then(CommandManager.literal("true").executes(context -> setGlobalLookMenu(context.getSource(), true)))
                        .then(CommandManager.literal("false").executes(context -> setGlobalLookMenu(context.getSource(), false)))
                        .then(CommandManager.literal("status").executes(context -> globalLookMenuStatus(context.getSource())))));
        LiteralArgumentBuilder<ServerCommandSource> displayFilter = CommandManager.literal("displayfilter")
                .requires(source -> CommandPermissionCompat.has(source, 2));
        for (Metric metric : Metric.values()) {
            displayFilter.then(CommandManager.literal(metric.command)
                    .then(CommandManager.literal("true").executes(context -> setMetricDisplay(context.getSource(), metric, true)))
                    .then(CommandManager.literal("false").executes(context -> setMetricDisplay(context.getSource(), metric, false)))
                    .then(CommandManager.literal("enable").executes(context -> setMetricDisplay(context.getSource(), metric, true)))
                    .then(CommandManager.literal("disable").executes(context -> setMetricDisplay(context.getSource(), metric, false)))
                    .then(CommandManager.literal("status").executes(context -> metricDisplayStatus(context.getSource(), metric))));
        }
        root.then(displayFilter);
        root.then(CommandManager.literal("scoreboard").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("clear").executes(context -> BoardService.clearVanilla(context.getSource())))
                .then(CommandManager.literal("cleanup").executes(context -> BoardService.clearForeignScoreboards(context.getSource())))
                .then(CommandManager.literal("blocking")
                        .then(CommandManager.literal("true").executes(context -> BoardService.setForeignScoreboardBlocking(context.getSource(), true)))
                        .then(CommandManager.literal("false").executes(context -> BoardService.setForeignScoreboardBlocking(context.getSource(), false)))
                        .then(CommandManager.literal("enable").executes(context -> BoardService.setForeignScoreboardBlocking(context.getSource(), true)))
                        .then(CommandManager.literal("disable").executes(context -> BoardService.setForeignScoreboardBlocking(context.getSource(), false)))
                        .then(CommandManager.literal("status").executes(context -> BoardService.foreignScoreboardBlockingStatus(context.getSource()))))
                .then(buildSelectionCommands(true)));
        root.then(CommandManager.literal("whitelist").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("true").executes(context -> setWhitelistOnly(context.getSource(), true)))
                .then(CommandManager.literal("false").executes(context -> setWhitelistOnly(context.getSource(), false)))
                .then(CommandManager.literal("on").executes(context -> setWhitelistOnly(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setWhitelistOnly(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> whitelistStatus(context.getSource()))));
        root.then(CommandManager.literal("botfilter").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("true").executes(context -> setBotFilter(context.getSource(), true)))
                .then(CommandManager.literal("false").executes(context -> setBotFilter(context.getSource(), false)))
                .then(CommandManager.literal("on").executes(context -> setBotFilter(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setBotFilter(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> botFilterStatus(context.getSource()))));
        root.then(CommandManager.literal("customfilter").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("true").executes(context -> setCustomFilter(context.getSource(), true)))
                .then(CommandManager.literal("false").executes(context -> setCustomFilter(context.getSource(), false)))
                .then(CommandManager.literal("on").executes(context -> setCustomFilter(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setCustomFilter(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> customFilterStatus(context.getSource()))));
        root.then(CommandManager.literal("onlinefilter").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("true").executes(context -> setOnlineFilter(context.getSource(), true)))
                .then(CommandManager.literal("false").executes(context -> setOnlineFilter(context.getSource(), false)))
                .then(CommandManager.literal("on").executes(context -> setOnlineFilter(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setOnlineFilter(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> onlineFilterStatus(context.getSource()))));
        root.then(CommandManager.literal("modwhitelist").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(context -> modifyModWhitelist(context.getSource(), true,
                                        StringArgumentType.getString(context, "player")))))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(context -> modifyModWhitelist(context.getSource(), false,
                                        StringArgumentType.getString(context, "player")))))
                .then(CommandManager.literal("list").executes(context -> listModWhitelist(context.getSource())))
                .then(CommandManager.literal("reload").executes(context -> reloadModWhitelist(context.getSource()))));
        root.then(CommandManager.literal("lookup").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("whitelist").executes(context -> MojangNameLookup.lookupWhitelist(context.getSource())))
                .then(CommandManager.argument("uuid", StringArgumentType.word())
                        .executes(context -> MojangNameLookup.lookupOne(context.getSource(),
                                StringArgumentType.getString(context, "uuid")))));
        root.then(CommandManager.literal("cache").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("status").executes(context -> cacheStatus(context.getSource())))
                .then(CommandManager.literal("reload").executes(context -> reloadCache(context.getSource()))));
        root.then(CommandManager.literal("ratelimit").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("clear").executes(context -> clearRateLimits(context.getSource()))));
        root.then(CommandManager.literal("config").requires(source -> CommandPermissionCompat.has(source, 2))
                .executes(context -> listConfig(context.getSource()))
                .then(CommandManager.literal("list").executes(context -> listConfig(context.getSource())))
                .then(CommandManager.literal("reload").executes(context -> reloadConfig(context.getSource())))
                .then(CommandManager.literal("get")
                        .then(CommandManager.argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        RankBoardConfig.optionKeys(), builder))
                                .executes(context -> getConfig(context.getSource(),
                                        StringArgumentType.getString(context, "key")))))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        RankBoardConfig.optionKeys(), builder))
                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> setConfig(context.getSource(),
                                                StringArgumentType.getString(context, "key"),
                                                StringArgumentType.getString(context, "value")))))));
        for (Period period : Period.values()) {
            LiteralArgumentBuilder<ServerCommandSource> periodNode = CommandManager.literal(period.command);
            for (Metric metric : Metric.values()) {
                periodNode.then(CommandManager.literal(metric.command)
                        .executes(context -> show(context.getSource(), period, metric, 10))
                        .then(CommandManager.argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(context -> show(context.getSource(), period, metric, IntegerArgumentType.getInteger(context, "limit")))));
            }
            root.then(periodNode);
        }
        dispatcher.register(root);
    }

    private LiteralArgumentBuilder<ServerCommandSource> buildSelectionCommands(boolean vanilla) {
        LiteralArgumentBuilder<ServerCommandSource> periods = CommandManager.literal("show");
        for (Period period : Period.values()) {
            LiteralArgumentBuilder<ServerCommandSource> periodNode = CommandManager.literal(period.command);
            for (Metric metric : Metric.values()) {
                LiteralArgumentBuilder<ServerCommandSource> metricNode = CommandManager.literal(metric.command)
                        .executes(context -> vanilla
                                ? BoardService.writeVanilla(context.getSource(), period, metric)
                                : BoardService.enable(context.getSource(), period, metric));
                if (!vanilla) {
                    metricNode.then(CommandManager.argument("player", EntityArgumentType.player())
                            .requires(source -> CommandPermissionCompat.has(source, 2))
                            .executes(context -> BoardService.enable(context.getSource(),
                                    EntityArgumentType.getPlayer(context, "player"), period, metric)));
                }
                periodNode.then(metricNode);
            }
            periods.then(periodNode);
        }
        return periods;
    }

    private LiteralArgumentBuilder<ServerCommandSource> buildColorCommands() {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("color")
                .requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("list").executes(context -> listMetricColors(context.getSource())));
        LiteralArgumentBuilder<ServerCommandSource> reset = CommandManager.literal("reset")
                .then(CommandManager.literal("all").executes(context -> resetAllMetricColors(context.getSource())));
        for (Metric metric : Metric.values()) {
            root.then(CommandManager.literal(metric.command)
                    .executes(context -> showColorPresets(context.getSource(), metric))
                    .then(CommandManager.argument("value", StringArgumentType.word())
                            .suggests((context, builder) -> suggestColorPresets(builder))
                            .executes(context -> setMetricColor(context.getSource(), metric,
                                    StringArgumentType.getString(context, "value")))));
            reset.then(CommandManager.literal(metric.command)
                    .executes(context -> resetMetricColor(context.getSource(), metric)));
        }
        return root.then(reset);
    }

    private LiteralArgumentBuilder<ServerCommandSource> buildLabelCommands() {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("label")
                .requires(source -> CommandPermissionCompat.has(source, 2))
                .then(CommandManager.literal("list").executes(context -> listMetricLabels(context.getSource())));
        LiteralArgumentBuilder<ServerCommandSource> reset = CommandManager.literal("reset")
                .then(CommandManager.literal("all").executes(context -> resetAllMetricLabels(context.getSource())));
        for (Metric metric : Metric.values()) {
            root.then(CommandManager.literal(metric.command)
                    .executes(context -> showMetricLabel(context.getSource(), metric))
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(context -> setMetricLabel(context.getSource(), metric,
                                    StringArgumentType.getString(context, "name")))));
            reset.then(CommandManager.literal(metric.command)
                    .executes(context -> resetMetricLabel(context.getSource(), metric)));
        }
        return root.then(reset);
    }

    private int helpGrouped(ServerCommandSource source, String group) {
        boolean op = CommandPermissionCompat.has(source, 2);
        if (group.equals("menu")) {
            Text line = clickable("[玩家指令]", Formatting.AQUA, "/leaderboard help player", "玩家常用指令")
                    .copy().append(Text.literal(" "))
                    .append(clickable("[计分板]", Formatting.YELLOW, "/leaderboard help scoreboard", "个人计分板指令"))
                    .append(Text.literal(" "))
                    .append(clickable("[网页与配置]", Formatting.GREEN, "/leaderboard help web", "网页地址和配置说明"));
            if (op) line = line.copy().append(Text.literal(" "))
                    .append(clickable("[OP 管理]", Formatting.RED, "/leaderboard help admin", "仅 OP 可用的管理指令"));
            Text menuLine = line;
            source.sendFeedback(() -> menuLine, false);
            return 1;
        }
        source.sendFeedback(() -> clickable("[返回 Help]", Formatting.GRAY,
                "/leaderboard help", "返回帮助分组"), false);
        switch (group) {
            case "player" -> {
                helpCommand(source, "/leaderboard", "/leaderboard", "打开排行榜菜单");
                helpCommand(source, "/leaderboard mine", "/leaderboard mine", "查询所有个人统计并显示总览");
                helpCommand(source, "/leaderboard mine <all|day|week|month>", "/leaderboard mine ", "查询指定周期的个人统计");
                helpCommand(source, "/leaderboard <周期> <榜单> [数量]", "/leaderboard all playtime ", "查看排行榜");
                helpCommand(source, "/leaderboard carousel true|false|status", "/leaderboard carousel ", "控制榜单轮播");
                helpCommand(source, "/leaderboard lookmenu true|false|status", "/leaderboard lookmenu ", "关闭或开启自己的抬头蹲起菜单");
            }
            case "scoreboard" -> {
                helpCommand(source, "/leaderboard display show <周期> <榜单>", "/leaderboard display show ", "显示个人单榜计分板");
                helpCommand(source, "/leaderboard display off", "/leaderboard display off", "关闭个人计分板");
                helpCommand(source, "/leaderboard mine", "/leaderboard mine", "显示个人所有榜单总览");
                if (op) {
                    helpCommand(source, "/leaderboard scoreboard cleanup", "/leaderboard scoreboard cleanup", "清理其他模组计分板");
                    helpCommand(source, "/leaderboard scoreboard blocking <true|false|status>",
                            "/leaderboard scoreboard blocking ", "设置其他模组计分板自动屏蔽");
                }
            }
            case "web" -> {
                source.sendFeedback(() -> websiteButton(source), false);
                helpCommand(source, "/leaderboard config set web-public-address <地址|auto>",
                        "/leaderboard config set web-public-address ", "设置网站按钮地址，默认 127.0.0.1:8765");
                helpCommand(source, "/leaderboard config list|get|set|reload", "/leaderboard config ", "查看或修改配置");
                helpCommand(source, "/leaderboard ratelimit clear", "/leaderboard ratelimit clear", "清除网页限流");
                source.sendFeedback(() -> Text.literal(
                        "配置文件：config/rankboard/rankboard-web.properties"), false);
            }
            case "admin" -> {
                if (!op) return 0;
                Text modules = clickable("[玩家与筛选]", Formatting.AQUA,
                                "/leaderboard help admin players", "白名单与玩家筛选")
                        .copy().append(Text.literal(" "))
                        .append(clickable("[计分板与颜色]", Formatting.YELLOW,
                                "/leaderboard help admin scoreboard", "计分板、榜单显示与名字颜色"))
                        .append(Text.literal(" "))
                        .append(clickable("[网页与缓存]", Formatting.GREEN,
                                "/leaderboard help admin web", "网页、限流与缓存"))
                        .append(Text.literal(" "))
                        .append(clickable("[配置管理]", Formatting.LIGHT_PURPLE,
                                "/leaderboard help admin config", "配置命令与完整配置说明"));
                source.sendFeedback(() -> modules, false);
            }
            case "admin-players" -> {
                if (!op) return 0;
                helpCommand(source, "/leaderboard whitelist <true|false|status>", "/leaderboard whitelist ", "控制服务器白名单筛选");
                helpCommand(source, "/leaderboard modwhitelist <add|remove|list|reload>", "/leaderboard modwhitelist ", "管理模组自带白名单");
                helpCommand(source, "/leaderboard botfilter <true|false|status>", "/leaderboard botfilter ", "筛选 bot_ 前缀玩家；立即生效");
                helpCommand(source, "/leaderboard customfilter <true|false|status>", "/leaderboard customfilter ", "筛选无法识别身份的历史玩家；立即生效");
                helpCommand(source, "/leaderboard onlinefilter <true|false|status>", "/leaderboard onlinefilter ", "只显示在线玩家；立即生效");
                helpCommand(source, "/leaderboard lookup <uuid|whitelist>", "/leaderboard lookup ", "查询 Mojang 玩家名或批量补全白名单名称");
            }
            case "admin-scoreboard" -> {
                if (!op) return 0;
                helpCommand(source, "/leaderboard displayfilter <榜单> <true|false|status>", "/leaderboard displayfilter ", "管理榜单显示");
                helpCommand(source, "/leaderboard scoreboard show <周期> <榜单>", "/leaderboard scoreboard show ", "显示全服共享原版侧边栏；不会改变玩家名字颜色");
                helpCommand(source, "/leaderboard scoreboard clear", "/leaderboard scoreboard clear", "关闭 RankBoard 全服共享侧边栏");
                helpCommand(source, "/leaderboard scoreboard cleanup", "/leaderboard scoreboard cleanup", "检测并关闭当前其他模组计分板显示槽");
                helpCommand(source, "/leaderboard scoreboard blocking <true|false|status>",
                        "/leaderboard scoreboard blocking ", "屏蔽其他模组计分板");
                helpCommand(source, "/leaderboard namecolor <true|false|scoreboard-only|status>", "/leaderboard namecolor ",
                        "设置全服名字颜色：全部位置、全部关闭或仅排行榜；立即生效");
                helpCommand(source, "/leaderboard color list", "/leaderboard color list", "列出 11 个榜单的当前颜色");
                helpCommand(source, "/leaderboard color <榜单> [颜色名|#RRGGBB]", "/leaderboard color ", "不填颜色时打开英中双语 16 色预选；颜色名支持 Tab 补全；立即生效");
                helpCommand(source, "/leaderboard color reset <榜单|all>", "/leaderboard color reset ", "恢复单个或全部榜单默认颜色");
                helpCommand(source, "/leaderboard label <榜单> <名称>", "/leaderboard label ", "自定义榜单显示名称；支持中文、英文和空格；立即生效");
                helpCommand(source, "/leaderboard label list|reset <榜单|all>", "/leaderboard label ", "查看或恢复榜单显示名称");
                helpCommand(source, "/leaderboard lookmenu global <true|false|status>", "/leaderboard lookmenu global ", "OP 控制全服抬头蹲起菜单");
            }
            case "admin-web" -> {
                if (!op) return 0;
                source.sendFeedback(() -> websiteButton(source), false);
                helpCommand(source, "/leaderboard config set web-public-address <地址|auto>",
                        "/leaderboard config set web-public-address ", "设置网站按钮地址；重启网页服务后仍保留");
                helpCommand(source, "/leaderboard ratelimit clear", "/leaderboard ratelimit clear", "立即清除全部网页限流记录");
                helpCommand(source, "/leaderboard cache <status|reload>", "/leaderboard cache ", "查看或重载历史统计缓存");
                helpCommand(source, "/leaderboard config set avatar-cache-enabled <true|false>",
                        "/leaderboard config set avatar-cache-enabled ", "开关玩家头像缓存；重新进服时生效");
            }
            case "admin-config" -> {
                if (!op) return 0;
                helpCommand(source, "/leaderboard config list", "/leaderboard config list", "列出全部配置、当前值和所属文件");
                helpCommand(source, "/leaderboard config get <配置项>", "/leaderboard config get ", "查看配置当前值、用途与生效方式");
                helpCommand(source, "/leaderboard config set <配置项> <值>", "/leaderboard config set ", "修改并保存配置；网页项会重启网页服务");
                helpCommand(source, "/leaderboard config reload", "/leaderboard config reload", "重新读取主配置和网页配置并立即应用");
                source.sendFeedback(() -> clickable("[打开完整配置说明]", Formatting.LIGHT_PURPLE,
                        "/leaderboard help config", "进入通用、计分板、网页三个配置模块"), false);
            }
            case "config" -> {
                if (!op) return 0;
                Text modules = clickable("[通用与进服]", Formatting.GOLD,
                                "/leaderboard help config general", "欢迎语、菜单、筛选、头像与帮助配置")
                        .copy().append(Text.literal(" "))
                        .append(clickable("[计分板与缓存]", Formatting.YELLOW,
                                "/leaderboard help config scoreboard", "计分板、轮播、刷新与缓存配置"))
                        .append(Text.literal(" "))
                        .append(clickable("[网页与限流]", Formatting.AQUA,
                                "/leaderboard help config web", "网页监听、显示、刷新与限流配置"));
                source.sendFeedback(() -> modules, false);
                helpCommand(source, "/leaderboard config list", "/leaderboard config list", "列出所有配置当前值");
                helpCommand(source, "/leaderboard config get <配置项>", "/leaderboard config get ", "查看单项当前值和用途");
                helpCommand(source, "/leaderboard config reload", "/leaderboard config reload", "重新读取配置并重启网页服务");
                helpCommand(source, "/leaderboard cache reload", "/leaderboard cache reload", "重新扫描历史统计并应用缓存相关修改");
            }
            case "config-general" -> {
                if (!op) return 0;
                configHelpHeader(source);
                configHelp(source, "welcome-enabled");
                configHelp(source, "welcome-name");
                configHelp(source, "join-menu-enabled");
                configHelp(source, "join-web-hint-enabled");
                configHelp(source, "web-public-address");
                configHelp(source, "help-visibility");
                configHelp(source, "mod-whitelist-enabled");
                configHelp(source, "avatar-cache-enabled");
                configHelp(source, "avatar-cache-days");
            }
            case "config-scoreboard" -> {
                if (!op) return 0;
                configHelpHeader(source);
                configHelp(source, "foreign-scoreboard-blocking-mode");
                configHelp(source, "restore-scoreboard-on-join");
                configHelp(source, "look-up-sneak-menu-enabled");
                configHelp(source, "carousel-enabled");
                configHelp(source, "carousel-interval-seconds");
                configHelp(source, "client-scoreboard-show-zero");
                configHelp(source, "scoreboard-switch-message-enabled");
                configHelp(source, "scoreboard-name-color-enabled");
                configHelp(source, "player-name-color-render-mode");
                for (Metric metric : Metric.values()) configHelp(source, "metric-label-" + metric.command);
                for (Metric metric : Metric.values()) configHelp(source, "metric-color-" + metric.command);
                configHelp(source, "scoreboard-title-color-enabled");
                configHelp(source, "scoreboard-live-update-enabled");
                configHelp(source, "scoreboard-live-update-window-seconds");
                configHelp(source, "scoreboard-live-update-threshold");
                configHelp(source, "scoreboard-live-update-throttle-seconds");
                configHelp(source, "history-files-per-second");
            }
            case "config-web" -> {
                if (!op) return 0;
                configHelpHeader(source);
                configHelp(source, "host");
                configHelp(source, "port");
                configHelp(source, "server-name");
                configHelp(source, "website-icon");
                configHelp(source, "web-data-requests-per-second");
                configHelp(source, "web-icon-request-interval-seconds");
                configHelp(source, "web-ranking-refresh-interval-seconds");
            }
        }
        return 1;
    }

    private static void helpCommand(ServerCommandSource source, String label, String suggestion, String description) {
        Text line = Text.literal(label + "：" + description).setStyle(TextCompat.suggest(
                Style.EMPTY.withColor(Formatting.AQUA), suggestion, Text.literal("点击填入指令栏")));
        source.sendFeedback(() -> line, false);
    }

    private static void configHelpHeader(ServerCommandSource source) {
        source.sendFeedback(() -> clickable("[返回配置模块]", Formatting.GRAY,
                "/leaderboard help config", "返回配置说明分组"), false);
    }

    private static void configHelp(ServerCommandSource source, String key) {
        String effect = switch (key) {
            case "history-files-per-second", "mod-whitelist-enabled" -> "；修改后执行 /leaderboard cache reload";
            case "host", "port", "server-name", "website-icon", "web-data-requests-per-second",
                    "web-icon-request-interval-seconds", "web-ranking-refresh-interval-seconds" ->
                    "；修改后执行 /leaderboard config reload";
            default -> "；写入后立即生效";
        };
        helpCommand(source, "/leaderboard config set " + key + " <值>",
                "/leaderboard config set " + key + " ", RankBoardConfig.description(key) + effect);
    }

    private int menu(ServerCommandSource source) {
        Text header = clickable("[查询我的分数]", Formatting.GOLD, "/leaderboard mine all", "查看自己的全部统计分数")
                .copy().append(Text.literal(" "))
                .append(clickable("[关闭]", Formatting.RED, "/leaderboard display off", "关闭自己的客户端计分板"));
        try {
            boolean enabled = LeaderboardState.get(source.getServer()).isLookMenuEnabled(source.getEntity() == null
                    ? null : source.getEntity().getUuid());
            header = header.copy().append(Text.literal(" ")).append(clickable(
                    enabled ? "[关闭抬头蹲起]" : "[开启抬头蹲起]",
                    enabled ? Formatting.RED : Formatting.GREEN,
                    "/leaderboard lookmenu " + !enabled,
                    enabled ? "关闭自己的抬头+蹲起打开菜单" : "开启自己的抬头+蹲起打开菜单"));
        } catch (RuntimeException ignored) { }
        if (RankBoardConfig.get().carouselEnabled) {
            header = header.copy().append(Text.literal(" "))
                    .append(clickable("[轮播]", Formatting.AQUA, "/leaderboard carousel on", "自动轮播当前周期的榜单"));
        }
        if (RankBoardConfig.get().helpVisible(source)) {
            header = header.copy().append(Text.literal(" "))
                    .append(clickable("[Help]", Formatting.GREEN, "/leaderboard help", "查看 RankBoard 帮助"));
        }
        header = header.copy().append(Text.literal(" ")).append(websiteButton(source));
        Text finalHeader = header;
        source.sendFeedback(() -> finalHeader, false);
        Text line = Text.empty();
        int visible = 0;
        for (Metric metric : Metric.values()) {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) continue;
            Text button = clickable("[" + metric.label() + "]", metric,
                    "/leaderboard display show all " + metric.command,
                    "点击显示总计 " + metric.label() + " 侧边栏");
            if (visible > 0 && visible % 4 == 0) {
                Text completed = line;
                source.sendFeedback(() -> completed, false);
                line = Text.empty();
            }
            line = line.copy().append(button).append(Text.literal(" "));
            visible++;
        }
        if (visible > 0) {
            Text finalLine = line;
            source.sendFeedback(() -> finalLine, false);
        }
        else source.sendFeedback(() -> Text.literal("所有榜单显示均已被 OP 禁用。\n").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("点击榜单即可切换自己的原版侧边栏。")
                .formatted(Formatting.GRAY), false);
        BoardService.sendForeignScoreboardPrompt(source);
        return 1;
    }

    private int showMyScores(ServerCommandSource source, int days, String label) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            BoardService.enableOverview(source, days < 0 ? Period.ALL
                    : (days <= 1 ? Period.DAILY : (days <= 7 ? Period.WEEKLY : Period.MONTHLY)));
            LeaderboardState state = LeaderboardState.get(source.getServer());
            source.sendFeedback(() -> Text.literal("=== 我的分数 · " + label + " ===").formatted(Formatting.GOLD), false);
            LocalDate today = LocalDate.now();
            for (Metric metric : Metric.values()) {
                long value;
                if (days < 0) value = metric.read(player);
                else value = state.range(source.getServer(), today.minusDays(days - 1L), today, metric)
                        .values().getOrDefault(player.getUuid(), 0L);
                long score = value;
                source.sendFeedback(() -> RankBoardColors.text(metric.label() + "  ", metric)
                        .append(Text.literal(format(metric, score)).formatted(Formatting.AQUA)), false);
            }
            Text periods = clickable("[总计]", Formatting.GOLD, "/leaderboard mine all", "查看累计分数")
                    .copy().append(Text.literal(" "))
                    .append(clickable("[最近一日]", Formatting.YELLOW, "/leaderboard mine day", "查看最近一日分数"))
                    .append(Text.literal(" "))
                    .append(clickable("[最近一周]", Formatting.AQUA, "/leaderboard mine week", "查看最近一周分数"))
                    .append(Text.literal(" "))
                    .append(clickable("[最近一月]", Formatting.LIGHT_PURPLE, "/leaderboard mine month", "查看最近一月分数"));
            source.sendFeedback(() -> periods, false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        } catch (RuntimeException exception) {
            source.sendError(Text.literal("个人分数读取失败：" + exception.getMessage()));
            return 0;
        }
    }

    private int listConfig(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("=== RankBoard 配置 ===").formatted(Formatting.GOLD), false);
        for (String key : RankBoardConfig.optionKeys()) {
            String value = RankBoardConfig.value(key);
            source.sendFeedback(() -> Text.literal(key + " = " + (value.isEmpty() ? "(空/自动)" : value))
                    .formatted(RankBoardConfig.isWebOption(key) ? Formatting.AQUA : Formatting.GRAY), false);
        }
        source.sendFeedback(() -> Text.literal("使用 /leaderboard config get <配置项> 查看说明；"
                + "使用 /leaderboard config set <配置项> <值> 修改。")
                .formatted(Formatting.DARK_GRAY), false);
        return RankBoardConfig.optionKeys().size();
    }

    private int getConfig(ServerCommandSource source, String key) {
        if (!RankBoardConfig.isKnownOption(key)) {
            source.sendError(Text.literal("未知配置项：" + key));
            return 0;
        }
        String value = RankBoardConfig.value(key);
        source.sendFeedback(() -> Text.literal(key + " = " + (value.isEmpty() ? "(空/自动)" : value))
                .formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> Text.literal(RankBoardConfig.description(key)).formatted(Formatting.GRAY), false);
        return 1;
    }

    private int setConfig(ServerCommandSource source, String key, String value) {
        if (!RankBoardConfig.isKnownOption(key)) {
            source.sendError(Text.literal("未知配置项：" + key));
            return 0;
        }
        try {
            boolean webOption = RankBoardConfig.isWebOption(key);
            String normalized = RankBoardConfig.set(source.getServer(), key, value);
            if (key.equals("history-files-per-second")) StatReader.startWarmup(source.getServer());
            if (key.equals("scoreboard-name-color-enabled") || key.equals("player-name-color-render-mode")
                    || key.startsWith("metric-color-")) refreshColors(source.getServer());
            if (key.startsWith("metric-label-")) refreshMetricLabels(source.getServer());
            boolean webRunning = !webOption || WebDashboard.restart(source.getServer());
            source.sendFeedback(() -> Text.literal("已保存配置：" + key + " = "
                    + (normalized.isEmpty() ? "(空/自动)" : normalized)).formatted(Formatting.GREEN), true);
            if (!webRunning) {
                source.sendError(Text.literal("配置已保存，但网页服务重启失败；请检查服务器日志和监听地址。"));
            }
            return webRunning ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("配置值无效：" + exception.getMessage()));
        } catch (java.io.IOException exception) {
            source.sendError(Text.literal("配置保存失败：" + exception.getMessage()));
            LOGGER.error("Could not save RankBoard config {}", key, exception);
        }
        return 0;
    }

    private int reloadConfig(ServerCommandSource source) {
        RankBoardConfig.load(source.getServer());
        StatReader.startWarmup(source.getServer());
        refreshColors(source.getServer());
        boolean webRunning = WebDashboard.restart(source.getServer());
        if (!webRunning) {
            source.sendError(Text.literal("配置已重载，但网页服务启动失败；请检查服务器日志和网页配置。"));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("RankBoard 主配置与网页配置已重载。")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private int clearRateLimits(ServerCommandSource source) {
        int cleared = WebDashboard.clearRateLimits();
        source.sendFeedback(() -> Text.literal("已清除 " + cleared + " 个网页限流与 API 累计冷却记录。")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private void sendJoinExperience(ServerPlayerEntity player) {
        RankBoardConfig config = RankBoardConfig.get();
        if (config.welcomeEnabled) {
            player.sendMessage(Text.literal("欢迎来到 ").formatted(Formatting.GRAY)
                    .append(Text.literal(config.displayName(PlayerCompat.server(player))).formatted(Formatting.GOLD)), false);
        }
        if (config.joinWebHintEnabled) {
            player.sendMessage(Text.literal("可在 " + config.webAddress(PlayerCompat.server(player)) + " 查看网页排行榜。")
                    .formatted(Formatting.AQUA), false);
        }
        if (config.joinMenuEnabled) menu(player.getCommandSource());
    }

    private void handleLookUpSneakMenu(net.minecraft.server.MinecraftServer server) {
        if (!RankBoardConfig.get().lookUpSneakMenuEnabled) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!LeaderboardState.get(server).isLookMenuEnabled(player.getUuid())) {
                LOOK_MENU_HELD.remove(player.getUuid());
                continue;
            }
            boolean active = player.isSneaking() && player.getPitch() <= -60.0F;
            if (active && LOOK_MENU_HELD.add(player.getUuid())) menu(player.getCommandSource());
            else if (!active) LOOK_MENU_HELD.remove(player.getUuid());
        }
    }

    private static Text clickable(String label, Formatting color, String command, String hover) {
        return Text.literal(label).setStyle(TextCompat.interactive(Style.EMPTY.withColor(color), command, Text.literal(hover)));
    }

    private static Text clickable(String label, Metric metric, String command, String hover) {
        return Text.literal(label).setStyle(TextCompat.interactive(
                Style.EMPTY.withColor(RankBoardColors.renderedRgb(metric)), command, Text.literal(hover)));
    }

    private static Text websiteButton(ServerCommandSource source) {
        String address = RankBoardConfig.get().webAddress(source.getServer());
        if (!address.startsWith("http://") && !address.startsWith("https://")) address = "http://" + address;
        try {
            URI uri = URI.create(address);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) throw new IllegalArgumentException();
            return Text.literal("[打开网站]").setStyle(TextCompat.openUrl(
                    Style.EMPTY.withColor(Formatting.AQUA), address, Text.literal("打开 RankBoard 网页排行榜")));
        } catch (RuntimeException exception) {
            return Text.literal("[网站地址无效]").formatted(Formatting.DARK_GRAY);
        }
    }

    private int setLookMenu(ServerCommandSource source, boolean enabled) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            LeaderboardState.get(source.getServer()).setLookMenuEnabled(player.getUuid(), enabled);
            LOOK_MENU_HELD.remove(player.getUuid());
            source.sendFeedback(() -> Text.literal(enabled
                    ? "已开启自己的抬头+蹲起菜单。"
                    : "已关闭自己的抬头+蹲起菜单；可输入 /leaderboard lookmenu true 重新开启。"), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private int lookMenuStatus(ServerCommandSource source) {
        try {
            boolean personal = LeaderboardState.get(source.getServer())
                    .isLookMenuEnabled(source.getPlayerOrThrow().getUuid());
            boolean global = RankBoardConfig.get().lookUpSneakMenuEnabled;
            source.sendFeedback(() -> Text.literal("自己的抬头+蹲起菜单："
                    + (personal ? "已开启" : "已关闭") + "；全服功能：" + (global ? "已开启" : "已关闭")), false);
            return personal && global ? 1 : 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private int setGlobalLookMenu(ServerCommandSource source, boolean enabled) {
        try {
            RankBoardConfig.set(source.getServer(), "look-up-sneak-menu-enabled", Boolean.toString(enabled));
            LOOK_MENU_HELD.clear();
            source.sendFeedback(() -> Text.literal(enabled
                    ? "已开启全服抬头+蹲起菜单；玩家个人关闭状态保持不变。"
                    : "已关闭全服所有玩家的抬头+蹲起菜单。"), true);
            return 1;
        } catch (java.io.IOException exception) {
            source.sendError(Text.literal("全服抬头蹲起菜单设置保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    private int globalLookMenuStatus(ServerCommandSource source) {
        boolean enabled = RankBoardConfig.get().lookUpSneakMenuEnabled;
        source.sendFeedback(() -> Text.literal("全服抬头+蹲起菜单：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setNameColor(ServerCommandSource source, String mode) {
        try {
            String normalized = RankBoardConfig.set(source.getServer(), "scoreboard-name-color-enabled", mode);
            BoardService.refreshAll(source.getServer());
            PlayerNameColors.refreshAll(source.getServer());
            source.sendFeedback(() -> Text.literal("全服玩家名字颜色模式：" + normalized), true);
            return 1;
        } catch (IllegalArgumentException | java.io.IOException exception) {
            source.sendError(Text.literal("名字颜色模式保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    private int nameColorStatus(ServerCommandSource source) {
        String mode = RankBoardConfig.get().nameColorMode.serialized;
        String render = RankBoardConfig.get().nameColorRenderMode.serialized;
        source.sendFeedback(() -> Text.literal("全服玩家名字颜色模式：" + mode + "；渲染：" + render), false);
        return RankBoardConfig.get().nameColorMode == RankBoardConfig.NameColorMode.DISABLED ? 0 : 1;
    }

    private int showMetricLabel(ServerCommandSource source, Metric metric) {
        source.sendFeedback(() -> Text.literal(metric.command + " = " + metric.label()), false);
        return 1;
    }

    private int setMetricLabel(ServerCommandSource source, Metric metric, String value) {
        return saveMetricLabel(source, metric, value, "已设置");
    }

    private int resetMetricLabel(ServerCommandSource source, Metric metric) {
        String key = "metric-label-" + metric.command;
        return saveMetricLabel(source, metric, RankBoardConfig.defaultValue(key), "已恢复默认");
    }

    private int resetAllMetricLabels(ServerCommandSource source) {
        try {
            for (Metric metric : Metric.values()) {
                String key = "metric-label-" + metric.command;
                RankBoardConfig.set(source.getServer(), key, RankBoardConfig.defaultValue(key));
            }
            refreshMetricLabels(source.getServer());
            source.sendFeedback(() -> Text.literal("已恢复全部榜单默认名称。"), true);
            return Metric.values().length;
        } catch (java.io.IOException exception) {
            source.sendError(Text.literal("榜单名称保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    private int listMetricLabels(ServerCommandSource source) {
        for (Metric metric : Metric.values()) {
            source.sendFeedback(() -> Text.literal(metric.command + " = " + metric.label()), false);
        }
        return Metric.values().length;
    }

    private int saveMetricLabel(ServerCommandSource source, Metric metric, String value, String action) {
        try {
            String normalized = RankBoardConfig.set(source.getServer(), "metric-label-" + metric.command, value);
            refreshMetricLabels(source.getServer());
            source.sendFeedback(() -> Text.literal(action + " " + metric.command + "：" + normalized), true);
            return 1;
        } catch (IllegalArgumentException | java.io.IOException exception) {
            source.sendError(Text.literal("榜单名称保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    private static void refreshMetricLabels(net.minecraft.server.MinecraftServer server) {
        BoardService.refreshAll(server);
        WebDashboard.invalidateRankings();
    }

    private int setMetricColor(ServerCommandSource source, Metric metric, String value) {
        return saveMetricColor(source, metric, presetHex(value), "已设置");
    }

    private int showColorPresets(ServerCommandSource source, Metric metric) {
        String current = RankBoardConfig.value("metric-color-" + metric.command);
        source.sendFeedback(() -> Text.literal(metric.label() + "当前颜色：")
                .append(RankBoardColors.text(current, metric))
                .append(Text.literal("；点击选择预设色：").formatted(Formatting.GRAY)), false);
        for (int row = 0; row < 4; row++) {
            Text line = Text.empty();
            for (int column = 0; column < 4; column++) {
                ColorPreset preset = COLOR_PRESETS.get(row * 4 + column);
                if (column > 0) line = line.copy().append(Text.literal(" "));
                line = line.copy().append(clickable("[" + preset.key + " " + preset.label + "]", preset.formatting,
                        "/leaderboard color " + metric.command + " " + preset.key,
                        "点击设置 " + metric.label() + " 为 " + preset.key + " / " + preset.label));
            }
            Text completed = line;
            source.sendFeedback(() -> completed, false);
        }
        return COLOR_PRESETS.size();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestColorPresets(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (ColorPreset preset : COLOR_PRESETS) {
            if (preset.key.startsWith(remaining)) {
                builder.suggest(preset.key, new LiteralMessage(preset.label));
            }
        }
        return builder.buildFuture();
    }

    private static String presetHex(String value) {
        for (ColorPreset preset : COLOR_PRESETS) {
            if (preset.key.equalsIgnoreCase(value)) return preset.hex();
        }
        return value;
    }

    private int resetMetricColor(ServerCommandSource source, Metric metric) {
        String key = "metric-color-" + metric.command;
        return saveMetricColor(source, metric, RankBoardConfig.defaultValue(key), "已恢复默认");
    }

    private int resetAllMetricColors(ServerCommandSource source) {
        try {
            for (Metric metric : Metric.values()) {
                String key = "metric-color-" + metric.command;
                RankBoardConfig.set(source.getServer(), key, RankBoardConfig.defaultValue(key));
            }
            refreshColors(source.getServer());
            source.sendFeedback(() -> Text.literal("已恢复全部榜单默认颜色。"), true);
            return Metric.values().length;
        } catch (java.io.IOException exception) {
            source.sendError(Text.literal("颜色配置保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    private int listMetricColors(ServerCommandSource source) {
        for (Metric metric : Metric.values()) {
            String value = RankBoardConfig.value("metric-color-" + metric.command);
            source.sendFeedback(() -> RankBoardColors.text(metric.command + " = " + value, metric), false);
        }
        return Metric.values().length;
    }

    private int saveMetricColor(ServerCommandSource source, Metric metric, String value, String action) {
        String key = "metric-color-" + metric.command;
        try {
            String normalized = RankBoardConfig.set(source.getServer(), key, value);
            refreshColors(source.getServer());
            source.sendFeedback(() -> RankBoardColors.text(action + " " + metric.label() + "：" + normalized, metric), true);
            return 1;
        } catch (IllegalArgumentException | java.io.IOException exception) {
            source.sendError(Text.literal("颜色配置保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    private static void refreshColors(net.minecraft.server.MinecraftServer server) {
        BoardService.refreshAll(server);
        PlayerNameColors.refreshAll(server);
    }

    private int setMetricDisplay(ServerCommandSource source, Metric metric, boolean enabled) {
        LeaderboardState.get(source.getServer()).setMetricDisplayEnabled(metric, enabled);
        BoardService.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(enabled ? metric.label() + " 已恢复显示。" : metric.label() + " 已禁止显示。"), true);
        return 1;
    }

    private int metricDisplayStatus(ServerCommandSource source, Metric metric) {
        boolean enabled = LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric);
        source.sendFeedback(() -> Text.literal(metric.label() + " 显示：" + (enabled ? "已开启" : "已禁用")), false);
        return enabled ? 1 : 0;
    }

    private int setWhitelistOnly(ServerCommandSource source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setWhitelistOnly(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(enabled
                ? "排行榜已仅显示服务器白名单玩家。" : "排行榜已显示所有有统计数据的玩家。"), true);
        return 1;
    }

    private int whitelistStatus(ServerCommandSource source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isWhitelistOnly();
        source.sendFeedback(() -> Text.literal("排行榜白名单过滤：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setBotFilter(ServerCommandSource source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setBotFilterEnabled(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(enabled
                ? "排行榜已屏蔽 bot_ 前缀玩家。" : "排行榜已允许显示 bot_ 前缀玩家。"), true);
        return 1;
    }

    private int botFilterStatus(ServerCommandSource source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isBotFilterEnabled();
        source.sendFeedback(() -> Text.literal("bot_ 前缀屏蔽：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setCustomFilter(ServerCommandSource source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setCustomPlayerFilterEnabled(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(enabled
                ? "排行榜已隐藏无法解析身份的历史玩家。" : "排行榜已允许显示 unknown_ 历史玩家。"), true);
        return 1;
    }

    private int customFilterStatus(ServerCommandSource source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isCustomPlayerFilterEnabled();
        source.sendFeedback(() -> Text.literal("未知历史玩家屏蔽：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setOnlineFilter(ServerCommandSource source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setOnlineOnly(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(enabled
                ? "排行榜已仅显示当前在线玩家。" : "排行榜已恢复显示符合其他筛选条件的玩家。"), true);
        return 1;
    }

    private int onlineFilterStatus(ServerCommandSource source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isOnlineOnly();
        source.sendFeedback(() -> Text.literal("仅显示在线玩家：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int modifyModWhitelist(ServerCommandSource source, boolean add, String player) {
        try {
            boolean changed = add ? RankBoardWhitelist.add(source.getServer(), player)
                    : RankBoardWhitelist.remove(source.getServer(), player);
            if (changed && RankBoardConfig.get().modWhitelistEnabled) StatReader.startWarmup(source.getServer());
            source.sendFeedback(() -> Text.literal(changed
                    ? (add ? "已添加到模组白名单：" : "已从模组白名单移除：") + player
                    : (add ? "模组白名单中已存在：" : "模组白名单中未找到：") + player), true);
            return changed ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("模组白名单参数无效：" + exception.getMessage()));
        } catch (java.io.IOException exception) {
            source.sendError(Text.literal("模组白名单保存失败：" + exception.getMessage()));
        }
        return 0;
    }

    private int listModWhitelist(ServerCommandSource source) {
        List<String> entries = RankBoardWhitelist.entries();
        source.sendFeedback(() -> Text.literal("模组白名单（" + entries.size() + "）："
                + (entries.isEmpty() ? "空" : String.join("，", entries))), false);
        return entries.size();
    }

    private int reloadModWhitelist(ServerCommandSource source) {
        RankBoardWhitelist.reload(source.getServer());
        source.sendFeedback(() -> Text.literal("模组白名单已重新加载，共 "
                + RankBoardWhitelist.entries().size() + " 项。"), true);
        if (RankBoardConfig.get().modWhitelistEnabled) StatReader.startWarmup(source.getServer());
        return 1;
    }

    private int cacheStatus(ServerCommandSource source) {
        String status = StatReader.isReady() ? "已完成" : "加载中";
        source.sendFeedback(() -> Text.literal("历史统计缓存：" + status + "（" + StatReader.progress() + "）")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    private int reloadCache(ServerCommandSource source) {
        StatReader.startWarmup(source.getServer());
        source.sendFeedback(() -> Text.literal("已重新开始加载历史统计缓存，当前进度 " + StatReader.progress() + "。")
                .formatted(Formatting.GRAY), true);
        return 1;
    }

    private int show(ServerCommandSource source, Period period, Metric metric, int limit) {
        try {
            if (!StatReader.isReady()) {
                source.sendFeedback(() -> Text.literal("历史统计仍在加载（" + StatReader.progress()
                        + "），当前榜单可能不完整。").formatted(Formatting.GRAY), false);
            }
            List<Entry> entries = entries(source.getServer(), period, metric);
            source.sendFeedback(() -> RankBoardColors.text("=== " + period.label + " " + metric.label() + " ===", metric), false);
            if (entries.isEmpty()) {
                source.sendFeedback(() -> Text.literal("没有可用于排行的玩家统计。 ").formatted(Formatting.GRAY), false);
                return 0;
            }
            long total = total(entries);
            source.sendFeedback(() -> Text.literal("总和 ").formatted(Formatting.GRAY)
                    .append(RankBoardColors.text(format(metric, total), metric)), false);
            for (int i = 0; i < Math.min(limit, entries.size()); i++) {
                Entry entry = entries.get(i);
                int rank = i + 1;
                source.sendFeedback(() -> Text.literal(rank + " ").formatted(Formatting.YELLOW)
                        .append(RankBoardColors.text(entry.name(), metric))
                        .append(Text.literal("  ").formatted(Formatting.GRAY))
                        .append(RankBoardColors.text(format(metric, entry.value()), metric)), false);
            }
            return entries.size();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to execute leaderboard command: period={}, metric={}", period.command, metric.command, exception);
            source.sendError(Text.literal("排行榜读取失败：" + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage())));
            return 0;
        }
    }

    static List<Entry> entries(net.minecraft.server.MinecraftServer server, Period period, Metric metric) {
        LeaderboardState state = LeaderboardState.get(server);
        state.rollPeriods(server);
        return StatReader.readAll(server, metric).stream()
                .filter(snapshot -> isIncluded(server, state, snapshot.uuid(), snapshot.name()))
                .map(snapshot -> new Entry(snapshot.name(), Math.max(0, snapshot.value(metric) - (period == Period.ALL ? 0 : state.getBaseline(period, snapshot.uuid(), metric)))))
                .sorted(Comparator.comparingLong(Entry::value).reversed().thenComparing(Entry::name))
                .toList();
    }

    static boolean isIncluded(net.minecraft.server.MinecraftServer server, LeaderboardState state,
                              java.util.UUID uuid, String name) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        return (!state.isWhitelistOnly() || PlayerDirectoryCompat.isAllowed(server, uuid, name))
                && (!state.isBotFilterEnabled() || !normalized.startsWith("bot_"))
                && (!state.isCustomPlayerFilterEnabled() || !normalized.startsWith("unknown_"))
                && (!state.isOnlineOnly() || server.getPlayerManager().getPlayer(uuid) != null);
    }

    static String format(Metric metric, long value) {
        if (metric == Metric.PLAY_TIME) return (value / 72000) + "h " + ((value / 1200) % 60) + "m";
        if (metric == Metric.ELYTRA_DISTANCE) return String.format(java.util.Locale.ROOT, "%.1f km", value / 100000.0);
        if (metric == Metric.DAMAGE_TAKEN || metric == Metric.DAMAGE_DEALT) return String.format(java.util.Locale.ROOT, "%.1f", value / 10.0);
        return Long.toString(value);
    }

    static long total(List<Entry> entries) {
        long total = 0;
        for (Entry entry : entries) {
            try {
                total = Math.addExact(total, entry.value());
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    public enum Metric {
        FOOD("food", "大胃王榜", Formatting.GOLD, RankBoardMod::foodUsed),
        JUMPS("jumps", "跳跃榜", Formatting.LIGHT_PURPLE, p -> custom(p, Stats.JUMP)),
        MINED("mined", "挖掘榜", Formatting.BLUE, RankBoardMod::mined),
        PLACED("placed", "放置榜", Formatting.DARK_AQUA, RankBoardMod::placed),
        KILLS("kills", "击杀榜", Formatting.RED, p -> custom(p, Stats.MOB_KILLS) + custom(p, Stats.PLAYER_KILLS)),
        DEATHS("deaths", "死亡榜", Formatting.DARK_RED, p -> custom(p, Stats.DEATHS)),
        TRADES("trades", "交易榜", Formatting.GREEN, p -> custom(p, Stats.TRADED_WITH_VILLAGER)),
        PLAY_TIME("playtime", "在线榜", Formatting.AQUA, p -> custom(p, Stats.PLAY_TIME)),
        ELYTRA_DISTANCE("elytra", "飞行榜", Formatting.LIGHT_PURPLE, p -> custom(p, Stats.AVIATE_ONE_CM)),
        FISHING("fishing", "钓鱼榜", Formatting.DARK_BLUE, p -> custom(p, Stats.FISH_CAUGHT)),
        DAMAGE_TAKEN("damage", "受伤榜", Formatting.RED, p -> custom(p, Stats.DAMAGE_TAKEN)),
        DAMAGE_DEALT("dealt", "伤害输出榜", Formatting.GOLD, p -> custom(p, Stats.DAMAGE_DEALT)),
        DROPPED("dropped", "丢垃圾榜", Formatting.DARK_GRAY, RankBoardMod::dropped),
        PICKED_UP("picked", "拾荒榜", Formatting.GREEN, RankBoardMod::pickedUp),
        CRAFTED("crafted", "合成榜", Formatting.GOLD, RankBoardMod::crafted),
        REDSTONE_PLACED("redstone", "红石大蛇榜", Formatting.RED, RankBoardMod::redstonePlaced);

        final String command;
        final String label;
        final Formatting nameColor;
        final Counter counter;
        Metric(String command, String label, Formatting nameColor, Counter counter) {
            this.command = command; this.label = label; this.nameColor = nameColor; this.counter = counter;
        }
        long read(ServerPlayerEntity player) { return counter.read(player); }
        String label() { return RankBoardConfig.get().metricLabel(this); }
    }

    public enum Period {
        DAILY("daily", "每日"), WEEKLY("weekly", "每周"), MONTHLY("monthly", "每月"), YEARLY("yearly", "每年"), ALL("all", "总计");
        final String command;
        final String label;
        Period(String command, String label) { this.command = command; this.label = label; }
        String key(LocalDate date) {
            return switch (this) {
                case DAILY -> date.toString();
                case WEEKLY -> date.getYear() + "-W" + date.get(WeekFields.ISO.weekOfWeekBasedYear());
                case MONTHLY -> date.getYear() + "-" + date.getMonthValue();
                case YEARLY -> Integer.toString(date.getYear());
                case ALL -> "all";
            };
        }
    }

    private static long custom(ServerPlayerEntity player, net.minecraft.util.Identifier stat) { return player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(stat)); }
    private static long foodUsed(ServerPlayerEntity player) { return Registries.ITEM.stream().filter(item -> item.getComponents().get(DataComponentTypes.FOOD) != null).mapToLong(item -> player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item))).sum(); }
    private static long mined(ServerPlayerEntity player) { return Registries.BLOCK.stream().mapToLong(block -> player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(block))).sum(); }
    private static long placed(ServerPlayerEntity player) { return Registries.ITEM.stream().filter(BlockItem.class::isInstance).mapToLong(item -> player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item))).sum(); }
    private static long dropped(ServerPlayerEntity player) { return Registries.ITEM.stream().mapToLong(item -> player.getStatHandler().getStat(Stats.DROPPED.getOrCreateStat(item))).sum(); }
    private static long pickedUp(ServerPlayerEntity player) { return Registries.ITEM.stream().mapToLong(item -> player.getStatHandler().getStat(Stats.PICKED_UP.getOrCreateStat(item))).sum(); }
    private static long crafted(ServerPlayerEntity player) { return Registries.ITEM.stream().mapToLong(item -> player.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item))).sum(); }
    private static long redstonePlaced(ServerPlayerEntity player) { return Registries.ITEM.stream().filter(RankBoardMod::isRedstoneComponent).mapToLong(item -> player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item))).sum(); }
    static boolean isRedstoneComponent(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        return REDSTONE_COMPONENTS.contains(path)
                || path.endsWith("_button")
                || path.endsWith("_pressure_plate")
                || path.endsWith("_door")
                || path.endsWith("_trapdoor")
                || path.endsWith("_fence_gate")
                || path.endsWith("_bulb");
    }

    @FunctionalInterface interface Counter { long read(ServerPlayerEntity player); }
    record Entry(String name, long value) { }
    private record ColorPreset(String key, String label, Formatting formatting) {
        String hex() { return String.format(java.util.Locale.ROOT, "#%06X", formatting.getColorValue()); }
    }
}
