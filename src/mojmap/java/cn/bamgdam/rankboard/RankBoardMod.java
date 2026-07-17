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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
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
            StatReader.reloadPlayer(server, handler.getPlayer().getUUID());
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

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
                                  CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("leaderboard")
                .requires(source -> CommandPermissionCompat.has(source, 0)).executes(context -> menu(context.getSource()));
        root.then(Commands.literal("help").executes(context -> help(context.getSource())));
        root.then(Commands.literal("display")
                .then(Commands.literal("off").executes(context -> BoardService.disable(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(source -> CommandPermissionCompat.has(source, 2))
                                .executes(context -> BoardService.disable(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(buildSelectionCommands(false)));
        root.then(Commands.literal("namecolor")
                .then(Commands.literal("on").executes(context -> setNameColor(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setNameColor(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> nameColorStatus(context.getSource()))));
        LiteralArgumentBuilder<CommandSourceStack> displayFilter = Commands.literal("displayfilter")
                .requires(source -> CommandPermissionCompat.has(source, 2));
        for (Metric metric : Metric.values()) {
            displayFilter.then(Commands.literal(metric.command)
                    .then(Commands.literal("enable").executes(context -> setMetricDisplay(context.getSource(), metric, true)))
                    .then(Commands.literal("disable").executes(context -> setMetricDisplay(context.getSource(), metric, false)))
                    .then(Commands.literal("status").executes(context -> metricDisplayStatus(context.getSource(), metric))));
        }
        root.then(displayFilter);
        root.then(Commands.literal("scoreboard").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("clear").executes(context -> BoardService.clearVanilla(context.getSource())))
                .then(buildSelectionCommands(true)));
        root.then(Commands.literal("whitelist").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("on").executes(context -> setWhitelistOnly(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setWhitelistOnly(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> whitelistStatus(context.getSource()))));
        root.then(Commands.literal("botfilter").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("on").executes(context -> setBotFilter(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setBotFilter(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> botFilterStatus(context.getSource()))));
        root.then(Commands.literal("customfilter").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("on").executes(context -> setCustomFilter(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setCustomFilter(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> customFilterStatus(context.getSource()))));
        root.then(Commands.literal("onlinefilter").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("on").executes(context -> setOnlineFilter(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setOnlineFilter(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> onlineFilterStatus(context.getSource()))));
        root.then(Commands.literal("lookup").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("whitelist").executes(context -> MojangNameLookup.lookupWhitelist(context.getSource())))
                .then(Commands.argument("uuid", StringArgumentType.word())
                        .executes(context -> MojangNameLookup.lookupOne(context.getSource(),
                                StringArgumentType.getString(context, "uuid")))));
        root.then(Commands.literal("cache").requires(source -> CommandPermissionCompat.has(source, 2))
                .then(Commands.literal("status").executes(context -> cacheStatus(context.getSource())))
                .then(Commands.literal("reload").executes(context -> reloadCache(context.getSource()))));
        for (Period period : Period.values()) {
            LiteralArgumentBuilder<CommandSourceStack> periodNode = Commands.literal(period.command);
            for (Metric metric : Metric.values()) {
                periodNode.then(Commands.literal(metric.command)
                        .executes(context -> show(context.getSource(), period, metric, 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(context -> show(context.getSource(), period, metric, IntegerArgumentType.getInteger(context, "limit")))));
            }
            root.then(periodNode);
        }
        dispatcher.register(root);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSelectionCommands(boolean vanilla) {
        LiteralArgumentBuilder<CommandSourceStack> periods = Commands.literal("show");
        for (Period period : Period.values()) {
            LiteralArgumentBuilder<CommandSourceStack> periodNode = Commands.literal(period.command);
            for (Metric metric : Metric.values()) {
                LiteralArgumentBuilder<CommandSourceStack> metricNode = Commands.literal(metric.command)
                        .executes(context -> vanilla
                                ? BoardService.writeVanilla(context.getSource(), period, metric)
                                : BoardService.enable(context.getSource(), period, metric));
                if (!vanilla) {
                    metricNode.then(Commands.argument("player", EntityArgument.player())
                            .requires(source -> CommandPermissionCompat.has(source, 2))
                            .executes(context -> BoardService.enable(context.getSource(),
                                    EntityArgument.getPlayer(context, "player"), period, metric)));
                }
                periodNode.then(metricNode);
            }
            periods.then(periodNode);
        }
        return periods;
    }

    private int help(CommandSourceStack source) {
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
        source.sendSuccess(() -> Component.literal(help).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private int menu(CommandSourceStack source) {
        Component line = Component.literal("[查询我的分数] ").withStyle(ChatFormatting.GOLD);
        int visible = 0;
        for (Metric metric : Metric.values()) {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) continue;
            Component button = Component.literal("[" + metric.label + "]")
                    .setStyle(TextCompat.interactive(Style.EMPTY.withColor(metric.nameColor),
                            "/leaderboard display show all " + metric.command,
                            Component.literal("点击显示总计 " + metric.label + " 侧边栏")));
            if (visible > 0 && visible % 4 == 0) {
                Component completed = line;
                source.sendSuccess(() -> completed, false);
                line = Component.empty();
            }
            line = line.copy().append(button).append(Component.literal(" "));
            visible++;
        }
        if (visible > 0) {
            Component finalLine = line;
            source.sendSuccess(() -> finalLine, false);
        }
        else source.sendSuccess(() -> Component.literal("所有榜单显示均已被 OP 禁用。\n").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("点击榜单即可切换自己的原版侧边栏；输入 /leaderboard help 查看完整命令。")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private int setNameColor(CommandSourceStack source, boolean enabled) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            LeaderboardState.get(source.getServer()).setNameColorEnabled(player.getUUID(), enabled);
            BoardService.refreshAll(source.getServer());
            source.sendSuccess(() -> Component.literal(enabled ? "已开启自己的榜单名字颜色。" : "已关闭自己的榜单名字颜色。"), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private int nameColorStatus(CommandSourceStack source) {
        try {
            boolean enabled = LeaderboardState.get(source.getServer()).isNameColorEnabled(source.getPlayerOrException().getUUID());
            source.sendSuccess(() -> Component.literal("自己的榜单名字颜色：" + (enabled ? "已开启" : "已关闭")), false);
            return enabled ? 1 : 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private int setMetricDisplay(CommandSourceStack source, Metric metric, boolean enabled) {
        LeaderboardState.get(source.getServer()).setMetricDisplayEnabled(metric, enabled);
        BoardService.refreshAll(source.getServer());
        source.sendSuccess(() -> Component.literal(enabled ? metric.label + " 已恢复显示。" : metric.label + " 已禁止显示。"), true);
        return 1;
    }

    private int metricDisplayStatus(CommandSourceStack source, Metric metric) {
        boolean enabled = LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric);
        source.sendSuccess(() -> Component.literal(metric.label + " 显示：" + (enabled ? "已开启" : "已禁用")), false);
        return enabled ? 1 : 0;
    }

    private int setWhitelistOnly(CommandSourceStack source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setWhitelistOnly(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendSuccess(() -> Component.literal(enabled
                ? "排行榜已仅显示服务器白名单玩家。" : "排行榜已显示所有有统计数据的玩家。"), true);
        return 1;
    }

    private int whitelistStatus(CommandSourceStack source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isWhitelistOnly();
        source.sendSuccess(() -> Component.literal("排行榜白名单过滤：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setBotFilter(CommandSourceStack source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setBotFilterEnabled(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendSuccess(() -> Component.literal(enabled
                ? "排行榜已屏蔽 bot_ 前缀玩家。" : "排行榜已允许显示 bot_ 前缀玩家。"), true);
        return 1;
    }

    private int botFilterStatus(CommandSourceStack source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isBotFilterEnabled();
        source.sendSuccess(() -> Component.literal("bot_ 前缀屏蔽：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setCustomFilter(CommandSourceStack source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setCustomPlayerFilterEnabled(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendSuccess(() -> Component.literal(enabled
                ? "排行榜已隐藏无法解析身份的历史玩家。" : "排行榜已允许显示 unknown_ 历史玩家。"), true);
        return 1;
    }

    private int customFilterStatus(CommandSourceStack source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isCustomPlayerFilterEnabled();
        source.sendSuccess(() -> Component.literal("未知历史玩家屏蔽：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int setOnlineFilter(CommandSourceStack source, boolean enabled) {
        LeaderboardState.get(source.getServer()).setOnlineOnly(enabled);
        BoardService.refreshAll(source.getServer());
        source.sendSuccess(() -> Component.literal(enabled
                ? "排行榜已仅显示当前在线玩家。" : "排行榜已恢复显示符合其他筛选条件的玩家。"), true);
        return 1;
    }

    private int onlineFilterStatus(CommandSourceStack source) {
        boolean enabled = LeaderboardState.get(source.getServer()).isOnlineOnly();
        source.sendSuccess(() -> Component.literal("仅显示在线玩家：" + (enabled ? "已开启" : "已关闭")), false);
        return enabled ? 1 : 0;
    }

    private int cacheStatus(CommandSourceStack source) {
        String status = StatReader.isReady() ? "已完成" : "加载中";
        source.sendSuccess(() -> Component.literal("历史统计缓存：" + status + "（" + StatReader.progress() + "）")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private int reloadCache(CommandSourceStack source) {
        StatReader.startWarmup(source.getServer());
        source.sendSuccess(() -> Component.literal("已重新开始加载历史统计缓存，当前进度 " + StatReader.progress() + "。")
                .withStyle(ChatFormatting.GRAY), true);
        return 1;
    }

    private int show(CommandSourceStack source, Period period, Metric metric, int limit) {
        try {
            if (!StatReader.isReady()) {
                source.sendSuccess(() -> Component.literal("历史统计仍在加载（" + StatReader.progress()
                        + "），当前榜单可能不完整。").withStyle(ChatFormatting.GRAY), false);
            }
            List<Entry> entries = entries(source.getServer(), period, metric);
            source.sendSuccess(() -> Component.literal("=== " + period.label + " " + metric.label + " ===").withStyle(ChatFormatting.GOLD), false);
            if (entries.isEmpty()) {
                source.sendSuccess(() -> Component.literal("没有可用于排行的玩家统计。 ").withStyle(ChatFormatting.GRAY), false);
                return 0;
            }
            long total = total(entries);
            source.sendSuccess(() -> Component.literal("总和 ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(format(metric, total)).withStyle(ChatFormatting.AQUA)), false);
            for (int i = 0; i < Math.min(limit, entries.size()); i++) {
                Entry entry = entries.get(i);
                int rank = i + 1;
                source.sendSuccess(() -> Component.literal(rank + " ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(entry.name())).append(Component.literal("  " + format(metric, entry.value())).withStyle(ChatFormatting.AQUA)), false);
            }
            return entries.size();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to execute leaderboard command: period={}, metric={}", period.command, metric.command, exception);
            source.sendFailure(Component.literal("排行榜读取失败：" + exception.getClass().getSimpleName()
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
                && (!state.isOnlineOnly() || server.getPlayerList().getPlayer(uuid) != null);
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
        FOOD("food", "大胃王榜", ChatFormatting.GOLD, RankBoardMod::foodUsed),
        JUMPS("jumps", "跳跃榜", ChatFormatting.LIGHT_PURPLE, p -> custom(p, Stats.JUMP)),
        MINED("mined", "挖掘榜", ChatFormatting.GRAY, RankBoardMod::mined),
        PLACED("placed", "放置榜", ChatFormatting.DARK_GREEN, RankBoardMod::placed),
        KILLS("kills", "击杀榜", ChatFormatting.RED, p -> custom(p, Stats.MOB_KILLS) + custom(p, Stats.PLAYER_KILLS)),
        DEATHS("deaths", "死亡榜", ChatFormatting.DARK_RED, p -> custom(p, Stats.DEATHS)),
        TRADES("trades", "交易榜", ChatFormatting.AQUA, p -> custom(p, Stats.TRADED_WITH_VILLAGER)),
        PLAY_TIME("playtime", "在线时间榜", ChatFormatting.DARK_AQUA, p -> custom(p, Stats.PLAY_TIME)),
        ELYTRA_DISTANCE("elytra", "鞘翅飞行榜", ChatFormatting.LIGHT_PURPLE, p -> custom(p, Stats.AVIATE_ONE_CM)),
        FISHING("fishing", "钓鱼榜", ChatFormatting.BLUE, p -> custom(p, Stats.FISH_CAUGHT)),
        DAMAGE_TAKEN("damage", "受伤害榜", ChatFormatting.YELLOW, p -> custom(p, Stats.DAMAGE_TAKEN));

        final String command;
        final String label;
        final ChatFormatting nameColor;
        final Counter counter;
        Metric(String command, String label, ChatFormatting nameColor, Counter counter) {
            this.command = command; this.label = label; this.nameColor = nameColor; this.counter = counter;
        }
        long read(ServerPlayer player) { return counter.read(player); }
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

    private static long custom(ServerPlayer player, net.minecraft.resources.Identifier stat) { return player.getStats().getValue(Stats.CUSTOM.get(stat)); }
    private static long foodUsed(ServerPlayer player) { return BuiltInRegistries.ITEM.stream().filter(item -> item.components().get(DataComponents.FOOD) != null).mapToLong(item -> player.getStats().getValue(Stats.ITEM_USED.get(item))).sum(); }
    private static long mined(ServerPlayer player) { return BuiltInRegistries.BLOCK.stream().mapToLong(block -> player.getStats().getValue(Stats.BLOCK_MINED.get(block))).sum(); }
    private static long placed(ServerPlayer player) { return BuiltInRegistries.ITEM.stream().filter(BlockItem.class::isInstance).mapToLong(item -> player.getStats().getValue(Stats.ITEM_USED.get(item))).sum(); }

    @FunctionalInterface interface Counter { long read(ServerPlayer player); }
    record Entry(String name, long value) { }
}
