package cn.bamgdam.rankboard;

import com.mojang.authlib.GameProfile;
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
import java.util.List;

public final class RankBoardMod implements ModInitializer {
    public static final String MOD_ID = "rankboard";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int REFRESH_INTERVAL_TICKS = 600;
    private int ticks;

    @Override
    public void onInitialize() {
        LOGGER.info("RankBoard initialized for Minecraft 1.21.1");
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StatReader.startWarmup(server);
            WebDashboard.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BoardService.clearSessions();
            WebDashboard.stop();
            StatReader.stopWarmup();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LeaderboardState.get(server).ensurePlayer(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StatReader.reloadPlayer(server, handler.getPlayer().getUuid());
            BoardService.disconnect(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks >= REFRESH_INTERVAL_TICKS) {
                ticks = 0;
                LeaderboardState.get(server).rollPeriods(server);
                BoardService.refreshAll(server);
            }
        });
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
                                  CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("leaderboard")
                .requires(source -> source.hasPermissionLevel(0)).executes(context -> menu(context.getSource()));
        root.then(CommandManager.literal("help").executes(context -> help(context.getSource())));
        root.then(CommandManager.literal("display")
                .then(CommandManager.literal("off").executes(context -> BoardService.disable(context.getSource()))
                        .then(CommandManager.argument("player", EntityArgumentType.player()).requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> BoardService.disable(context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(buildSelectionCommands(false)));
        root.then(CommandManager.literal("namecolor")
                .then(CommandManager.literal("on").executes(context -> setNameColor(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setNameColor(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> nameColorStatus(context.getSource()))));
        LiteralArgumentBuilder<ServerCommandSource> displayFilter = CommandManager.literal("displayfilter")
                .requires(source -> source.hasPermissionLevel(2));
        for (Metric metric : Metric.values()) {
            displayFilter.then(CommandManager.literal(metric.command)
                    .then(CommandManager.literal("enable").executes(context -> setMetricDisplay(context.getSource(), metric, true)))
                    .then(CommandManager.literal("disable").executes(context -> setMetricDisplay(context.getSource(), metric, false)))
                    .then(CommandManager.literal("status").executes(context -> metricDisplayStatus(context.getSource(), metric))));
        }
        root.then(displayFilter);
        root.then(CommandManager.literal("scoreboard").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("clear").executes(context -> BoardService.clearVanilla(context.getSource())))
                .then(buildSelectionCommands(true)));
        root.then(CommandManager.literal("whitelist").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("on").executes(context -> setWhitelistOnly(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setWhitelistOnly(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> whitelistStatus(context.getSource()))));
        root.then(CommandManager.literal("botfilter").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("on").executes(context -> setBotFilter(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setBotFilter(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> botFilterStatus(context.getSource()))));
        root.then(CommandManager.literal("customfilter").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("on").executes(context -> setCustomFilter(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setCustomFilter(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> customFilterStatus(context.getSource()))));
        root.then(CommandManager.literal("onlinefilter").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("on").executes(context -> setOnlineFilter(context.getSource(), true)))
                .then(CommandManager.literal("off").executes(context -> setOnlineFilter(context.getSource(), false)))
                .then(CommandManager.literal("status").executes(context -> onlineFilterStatus(context.getSource()))));
        root.then(CommandManager.literal("lookup").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("whitelist").executes(context -> MojangNameLookup.lookupWhitelist(context.getSource())))
                .then(CommandManager.argument("uuid", StringArgumentType.word())
                        .executes(context -> MojangNameLookup.lookupOne(context.getSource(),
                                StringArgumentType.getString(context, "uuid")))));
        root.then(CommandManager.literal("cache").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("status").executes(context -> cacheStatus(context.getSource())))
                .then(CommandManager.literal("reload").executes(context -> reloadCache(context.getSource()))));
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
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> BoardService.enable(context.getSource(),
                                    EntityArgumentType.getPlayer(context, "player"), period, metric)));
                }
                periodNode.then(metricNode);
            }
            periods.then(periodNode);
        }
        return periods;
    }

    private int help(ServerCommandSource source) {
        String help = "RankBoard 排行榜帮助\n"
                + "/leaderboard <周期> <榜单> [数量]：在聊天栏查看排名\n"
                + "/leaderboard：打开可点击的个人榜单选择菜单\n"
                + "/leaderboard display show <周期> <榜单> [玩家]：显示个人原版侧边栏；OP 可指定在线玩家\n"
                + "/leaderboard display off [玩家]：关闭个人侧边栏；OP 可指定在线玩家\n"
                + "/leaderboard namecolor on|off|status：开关自己的榜单名字颜色\n"
                + "/leaderboard displayfilter <榜单> enable|disable|status：OP 禁用或恢复某榜单显示\n"
                + "/leaderboard scoreboard show <周期> <榜单>：显示全服侧边栏（需要 OP）\n"
                + "/leaderboard scoreboard clear：关闭全服侧边栏（需要 OP）\n"
                + "/leaderboard whitelist on|off|status：白名单玩家过滤开关（需要 OP）\n"
                + "/leaderboard botfilter on|off|status：bot_ 前缀屏蔽开关（需要 OP）\n"
                + "/leaderboard customfilter on|off|status：未知历史玩家屏蔽开关（需要 OP）\n"
                + "/leaderboard onlinefilter on|off|status：仅显示在线玩家开关（需要 OP）\n"
                + "/leaderboard lookup <UUID>|whitelist：查询当前游戏名并更新缓存（需要 OP）\n"
                + "/leaderboard cache status|reload：查看进度或重新加载历史统计（需要 OP）\n"
                + "周期：daily 每日，weekly 每周，monthly 每月，yearly 每年，all 总计\n"
                + "榜单：food 食物，jumps 跳跃，mined 挖掘，placed 放置，kills 击杀\n"
                + "deaths 死亡，trades 交易，playtime 在线，elytra 鞘翅，fishing 钓鱼，damage 受伤";
        source.sendFeedback(() -> Text.literal(help).formatted(Formatting.GRAY), false);
        return 1;
    }

    private int menu(ServerCommandSource source) {
        Text line = Text.literal("[查询我的分数] ").formatted(Formatting.GOLD);
        int visible = 0;
        for (Metric metric : Metric.values()) {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) continue;
            Text button = Text.literal("[" + metric.label + "]")
                    .setStyle(Style.EMPTY.withColor(metric.nameColor)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/leaderboard display show all " + metric.command))
                            .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("点击显示总计 " + metric.label + " 侧边栏"))));
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
        source.sendFeedback(() -> Text.literal("点击榜单即可切换自己的原版侧边栏；输入 /leaderboard help 查看完整命令。")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    private int setNameColor(ServerCommandSource source, boolean enabled) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            LeaderboardState.get(source.getServer()).setNameColorEnabled(player.getUuid(), enabled);
            BoardService.refreshAll(source.getServer());
            source.sendFeedback(() -> Text.literal(enabled ? "已开启自己的榜单名字颜色。" : "已关闭自己的榜单名字颜色。"), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private int nameColorStatus(ServerCommandSource source) {
        try {
            boolean enabled = LeaderboardState.get(source.getServer()).isNameColorEnabled(source.getPlayerOrThrow().getUuid());
            source.sendFeedback(() -> Text.literal("自己的榜单名字颜色：" + (enabled ? "已开启" : "已关闭")), false);
            return enabled ? 1 : 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private int setMetricDisplay(ServerCommandSource source, Metric metric, boolean enabled) {
        LeaderboardState.get(source.getServer()).setMetricDisplayEnabled(metric, enabled);
        BoardService.refreshAll(source.getServer());
        source.sendFeedback(() -> Text.literal(enabled ? metric.label + " 已恢复显示。" : metric.label + " 已禁止显示。"), true);
        return 1;
    }

    private int metricDisplayStatus(ServerCommandSource source, Metric metric) {
        boolean enabled = LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric);
        source.sendFeedback(() -> Text.literal(metric.label + " 显示：" + (enabled ? "已开启" : "已禁用")), false);
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
            source.sendFeedback(() -> Text.literal("=== " + period.label + " " + metric.label + " ===").formatted(Formatting.GOLD), false);
            if (entries.isEmpty()) {
                source.sendFeedback(() -> Text.literal("没有可用于排行的玩家统计。 ").formatted(Formatting.GRAY), false);
                return 0;
            }
            long total = total(entries);
            source.sendFeedback(() -> Text.literal("总和 ").formatted(Formatting.GRAY)
                    .append(Text.literal(format(metric, total)).formatted(Formatting.AQUA)), false);
            for (int i = 0; i < Math.min(limit, entries.size()); i++) {
                Entry entry = entries.get(i);
                int rank = i + 1;
                source.sendFeedback(() -> Text.literal(rank + " ").formatted(Formatting.YELLOW)
                        .append(Text.literal(entry.name())).append(Text.literal("  " + format(metric, entry.value())).formatted(Formatting.AQUA)), false);
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
        return (!state.isWhitelistOnly() || server.getPlayerManager().getWhitelist()
                .isAllowed(new GameProfile(uuid, name)))
                && (!state.isBotFilterEnabled() || !normalized.startsWith("bot_"))
                && (!state.isCustomPlayerFilterEnabled() || !normalized.startsWith("unknown_"))
                && (!state.isOnlineOnly() || server.getPlayerManager().getPlayer(uuid) != null);
    }

    static String format(Metric metric, long value) {
        if (metric == Metric.PLAY_TIME) return (value / 72000) + "h " + ((value / 1200) % 60) + "m";
        if (metric == Metric.ELYTRA_DISTANCE) return String.format(java.util.Locale.ROOT, "%.1f km", value / 100000.0);
        if (metric == Metric.DAMAGE_TAKEN) return String.format(java.util.Locale.ROOT, "%.1f", value / 10.0);
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
        MINED("mined", "挖掘榜", Formatting.GRAY, RankBoardMod::mined),
        PLACED("placed", "放置榜", Formatting.DARK_GREEN, RankBoardMod::placed),
        KILLS("kills", "击杀榜", Formatting.RED, p -> custom(p, Stats.MOB_KILLS) + custom(p, Stats.PLAYER_KILLS)),
        DEATHS("deaths", "死亡榜", Formatting.DARK_RED, p -> custom(p, Stats.DEATHS)),
        TRADES("trades", "交易榜", Formatting.AQUA, p -> custom(p, Stats.TRADED_WITH_VILLAGER)),
        PLAY_TIME("playtime", "在线时间榜", Formatting.DARK_AQUA, p -> custom(p, Stats.PLAY_TIME)),
        ELYTRA_DISTANCE("elytra", "鞘翅飞行榜", Formatting.LIGHT_PURPLE, p -> custom(p, Stats.AVIATE_ONE_CM)),
        FISHING("fishing", "钓鱼榜", Formatting.BLUE, p -> custom(p, Stats.FISH_CAUGHT)),
        DAMAGE_TAKEN("damage", "受伤害榜", Formatting.YELLOW, p -> custom(p, Stats.DAMAGE_TAKEN));

        final String command;
        final String label;
        final Formatting nameColor;
        final Counter counter;
        Metric(String command, String label, Formatting nameColor, Counter counter) {
            this.command = command; this.label = label; this.nameColor = nameColor; this.counter = counter;
        }
        long read(ServerPlayerEntity player) { return counter.read(player); }
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

    @FunctionalInterface interface Counter { long read(ServerPlayerEntity player); }
    record Entry(String name, long value) { }
}
