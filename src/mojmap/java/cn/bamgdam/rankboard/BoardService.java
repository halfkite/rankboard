
package cn.bamgdam.rankboard;

import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Maintains real vanilla objectives and controls each player's vanilla sidebar with packets. */
final class BoardService {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, RankBoardMod.Period> OVERVIEW_SELECTIONS = new HashMap<>();
    private static final Map<UUID, String> CLIENT_OBJECTIVES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CAROUSEL_AT = new HashMap<>();
    private static final Map<ActivityKey, Long> LAST_ACTIVITY_VALUES = new HashMap<>();
    private static final Map<RankBoardMod.Metric, ActivityRate> ACTIVITY_RATES = new EnumMap<>(RankBoardMod.Metric.class);
    private static Selection globalSelection;
    private BoardService() { }

    static boolean canReceive(ServerPlayer player) {
        RankBoardConfig.RecipientFilter filter = RankBoardConfig.get().recipientFilter;
        if (filter == RankBoardConfig.RecipientFilter.DISABLED) return true;
        if (filter == RankBoardConfig.RecipientFilter.FAKE_ONLY) return !PlayerCompat.isFake(player);
        boolean listed = RankBoardWhitelist.matches(PlayerCompat.server(player), player.getUUID(),
                player.getName().getString());
        return filter == RankBoardConfig.RecipientFilter.WHITELIST ? listed : !listed;
    }

    static void disconnect(ServerPlayer player) {
        PlayerNameColors.disconnect(player);
        SELECTIONS.remove(player.getUUID());
        OVERVIEW_SELECTIONS.remove(player.getUUID());
        CLIENT_OBJECTIVES.remove(player.getUUID());
        NEXT_CAROUSEL_AT.remove(player.getUUID());
        LAST_ACTIVITY_VALUES.keySet().removeIf(key -> key.uuid.equals(player.getUUID()));
    }

    static void clearSessions() {
        SELECTIONS.clear();
        OVERVIEW_SELECTIONS.clear();
        CLIENT_OBJECTIVES.clear();
        NEXT_CAROUSEL_AT.clear();
        LAST_ACTIVITY_VALUES.clear();
        ACTIVITY_RATES.clear();
        globalSelection = null;
    }

    static int enable(CommandSourceStack source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            OVERVIEW_SELECTIONS.remove(player.getUUID());
            return enable(source, player, period, metric);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        } catch (RuntimeException exception) {
            RankBoardMod.LOGGER.error("Failed to display personal scoreboard: period={}, metric={}",
                    period.command, metric.command, exception);
            source.sendFailure(Component.literal("个人计分板显示失败：" + describe(exception)));
            return 0;
        }
    }

    static int enable(CommandSourceStack source, ServerPlayer player, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        if (!LeaderboardState.get(PlayerCompat.server(player)).isMetricDisplayEnabled(metric)) {
            source.sendFailure(Component.literal(metric.label() + " 当前已被 OP 禁止显示。"));
            return 0;
        }
        try {
            SELECTIONS.put(player.getUUID(), new Selection(period, metric));
            NEXT_CAROUSEL_AT.remove(player.getUUID());
            LeaderboardState.get(PlayerCompat.server(player)).setBoardPreference(
                    player.getUUID(), period, metric, true, false);
            sendPrivate(player, period, metric);
            PlayerNameColors.refresh(player);
            if (RankBoardConfig.get().scoreboardSwitchMessageEnabled) {
                player.sendSystemMessage(Component.literal(
                        "已显示个人原版计分板；输入 /leaderboard display off 可关闭。"), false);
            }
            return 1;
        } catch (RuntimeException exception) {
            RankBoardMod.LOGGER.error("Failed to display personal scoreboard: player={}, period={}, metric={}",
                    player.getName().getString(), period.command, metric.command, exception);
            source.sendFailure(Component.literal("个人计分板显示失败：" + describe(exception)));
            return 0;
        }
    }

    static int disable(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return disable(source, player);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    static int disable(CommandSourceStack source, ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
        OVERVIEW_SELECTIONS.remove(player.getUUID());
        NEXT_CAROUSEL_AT.remove(player.getUUID());
        LeaderboardState.get(PlayerCompat.server(player)).disableBoard(player.getUUID());
        removePrivateObjective(player);
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        PlayerNameColors.refresh(player);
        source.sendSuccess(() -> Component.literal(player == source.getEntity()
                ? "已关闭个人计分板。" : "已关闭 " + player.getName().getString() + " 的个人计分板。"), false);
        return 1;
    }

    static int enable(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            LeaderboardState state = LeaderboardState.get(PlayerCompat.server(player));
            LeaderboardState.BoardPreference preference = state.boardPreference(player.getUUID());
            if (preference == null) {
                preference = new LeaderboardState.BoardPreference(
                        RankBoardMod.Period.ALL, RankBoardMod.Metric.PLAY_TIME, false, false, false);
            }
            if (preference.overview()) {
                SELECTIONS.remove(player.getUUID());
                NEXT_CAROUSEL_AT.remove(player.getUUID());
                OVERVIEW_SELECTIONS.put(player.getUUID(), preference.period());
                state.setOverviewPreference(player.getUUID(), preference.period(), true);
                sendOverview(player, preference.period());
            } else {
                RankBoardMod.Metric metric = preference.metric();
                if (!state.isMetricDisplayEnabled(metric)) {
                    metric = firstEnabledMetric(state);
                    if (metric == null) {
                        source.sendFailure(Component.literal("所有榜单均已被 OP 禁用。"));
                        return 0;
                    }
                }
                OVERVIEW_SELECTIONS.remove(player.getUUID());
                Selection selection = new Selection(preference.period(), metric);
                SELECTIONS.put(player.getUUID(), selection);
                boolean carousel = preference.carousel() && RankBoardConfig.get().carouselEnabled;
                state.setBoardPreference(player.getUUID(), selection.period, selection.metric, true, carousel);
                if (carousel) scheduleCarousel(player.getUUID()); else NEXT_CAROUSEL_AT.remove(player.getUUID());
                sendPrivate(player, selection.period, selection.metric);
            }
            PlayerNameColors.refresh(player);
            source.sendSuccess(() -> Component.literal("已恢复个人计分板。"), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("个人计分板开启失败：" + describe(exception)));
            return 0;
        }
    }

    static void restore(ServerPlayer player) {
        if (!canReceive(player) || !RankBoardConfig.get().restoreBoardOnJoin || !StatReader.isReady()) return;
        LeaderboardState state = LeaderboardState.get(PlayerCompat.server(player));
        LeaderboardState.BoardPreference preference = state.boardPreference(player.getUUID());
        if (preference == null || !preference.enabled()) return;
        if (preference.overview()) {
            OVERVIEW_SELECTIONS.put(player.getUUID(), preference.period());
            try { sendOverview(player, preference.period()); }
            catch (RuntimeException exception) { RankBoardMod.LOGGER.warn("Could not restore overview for {}", player.getName().getString(), exception); }
            PlayerNameColors.refresh(player);
            return;
        }
        if (!state.isMetricDisplayEnabled(preference.metric())) return;
        Selection selection = new Selection(preference.period(), preference.metric());
        SELECTIONS.put(player.getUUID(), selection);
        if (preference.carousel() && RankBoardConfig.get().carouselEnabled) scheduleCarousel(player.getUUID());
        try { sendPrivate(player, selection.period, selection.metric); }
        catch (RuntimeException exception) {
            RankBoardMod.LOGGER.warn("Could not restore scoreboard for {}", player.getName().getString(), exception);
        }
        PlayerNameColors.refresh(player);
    }

    static void restoreGlobal(MinecraftServer server) {
        LeaderboardState state = LeaderboardState.get(server);
        LeaderboardState.BoardPreference preference = state.globalBoardPreference();
        if (preference == null || !preference.enabled() || !state.isMetricDisplayEnabled(preference.metric())) return;
        Selection restored = new Selection(preference.period(), preference.metric());
        Objective objective = syncObjective(server, restored.period, restored.metric, false);
        globalSelection = restored;
        server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, objective);
    }

    static int setCarousel(CommandSourceStack source, boolean enabled) {
        if (!RankBoardConfig.get().carouselEnabled) {
            source.sendFailure(Component.literal("榜单轮播功能已在配置中关闭。"));
            return 0;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            OVERVIEW_SELECTIONS.remove(player.getUUID());
            LeaderboardState state = LeaderboardState.get(source.getServer());
            LeaderboardState.BoardPreference stored = state.boardPreference(player.getUUID());
            Selection selection = SELECTIONS.get(player.getUUID());
            if (selection == null && stored != null) selection = new Selection(stored.period(), stored.metric());
            if (selection == null) selection = new Selection(RankBoardMod.Period.ALL, RankBoardMod.Metric.PLAY_TIME);
            SELECTIONS.put(player.getUUID(), selection);
            state.setBoardPreference(player.getUUID(), selection.period, selection.metric, true, enabled);
            if (enabled) scheduleCarousel(player.getUUID()); else NEXT_CAROUSEL_AT.remove(player.getUUID());
            sendPrivate(player, selection.period, selection.metric);
            PlayerNameColors.refresh(player);
            int seconds = RankBoardConfig.get().carouselIntervalSeconds;
            source.sendSuccess(() -> Component.literal(enabled
                    ? "已开启榜单轮播，每 " + seconds + " 秒自动切换。"
                    : "已关闭榜单轮播，保留当前计分板。"), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    static int carouselStatus(CommandSourceStack source) {
        try {
            UUID uuid = source.getPlayerOrException().getUUID();
            LeaderboardState.BoardPreference preference = LeaderboardState.get(source.getServer()).boardPreference(uuid);
            boolean enabled = preference != null && preference.carousel();
            source.sendSuccess(() -> Component.literal("榜单轮播：" + (enabled ? "已开启" : "已关闭")
                    + "；切换间隔 " + RankBoardConfig.get().carouselIntervalSeconds + " 秒。"), false);
            return enabled ? 1 : 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    static void tickCarousel(MinecraftServer server) {
        if (!StatReader.isReady() || !RankBoardConfig.get().carouselEnabled) return;
        long now = System.currentTimeMillis();
        LeaderboardState state = LeaderboardState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!canReceive(player)) continue;
            LeaderboardState.BoardPreference preference = state.boardPreference(player.getUUID());
            if (preference == null || !preference.enabled() || !preference.carousel()) continue;
            long due = NEXT_CAROUSEL_AT.computeIfAbsent(player.getUUID(), ignored -> carouselDeadline());
            if (now < due) continue;
            RankBoardMod.Metric next = nextMetric(state, preference.metric());
            Selection selection = new Selection(preference.period(), next);
            SELECTIONS.put(player.getUUID(), selection);
            state.setBoardPreference(player.getUUID(), selection.period, selection.metric, true, true);
            NEXT_CAROUSEL_AT.put(player.getUUID(), carouselDeadline());
            try { sendPrivate(player, selection.period, selection.metric); }
            catch (RuntimeException exception) {
                RankBoardMod.LOGGER.warn("Could not rotate scoreboard for {}", player.getName().getString(), exception);
            }
            PlayerNameColors.refresh(player);
        }
    }

    /** Polls live vanilla statistics and refreshes only scoreboards watching a changed metric. */
    static void tickActivity(MinecraftServer server) {
        RankBoardConfig config = RankBoardConfig.get();
        if (!StatReader.isReady() || !config.scoreboardLiveUpdateEnabled
                || (SELECTIONS.isEmpty() && OVERVIEW_SELECTIONS.isEmpty() && globalSelection == null)) return;
        Set<RankBoardMod.Metric> activeMetrics = new HashSet<>();
        for (Selection selection : SELECTIONS.values()) activeMetrics.add(selection.metric);
        if (!OVERVIEW_SELECTIONS.isEmpty()) for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) activeMetrics.add(metric);
        if (globalSelection != null) activeMetrics.add(globalSelection.metric);
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (RankBoardMod.Metric metric : activeMetrics) {
                long value;
                try { value = metric.read(player); }
                catch (RuntimeException exception) { continue; }
                ActivityKey key = new ActivityKey(player.getUUID(), metric);
                Long previous = LAST_ACTIVITY_VALUES.put(key, value);
                if (previous == null || previous == value) continue;
                ActivityRate rate = ACTIVITY_RATES.computeIfAbsent(metric, ignored -> new ActivityRate());
                if (rate.shouldSend(now, config.scoreboardLiveUpdateWindowSeconds * 1000L,
                        config.scoreboardLiveUpdateThreshold, config.scoreboardLiveUpdateThrottleSeconds * 1000L)) {
                    refreshMetric(server, metric);
                }
            }
        }
    }

    private static void refreshMetric(MinecraftServer server, RankBoardMod.Metric metric) {
        if (!StatReader.isReady()) return;
        Map<Selection, Objective> objectives = new HashMap<>();
        LeaderboardState state = LeaderboardState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!canReceive(player)) continue;
            RankBoardMod.Period overviewPeriod = OVERVIEW_SELECTIONS.get(player.getUUID());
            if (overviewPeriod != null) {
                try { sendOverview(player, overviewPeriod); } catch (RuntimeException exception) { RankBoardMod.LOGGER.warn("Could not refresh overview", exception); }
            }
            Selection selection = SELECTIONS.get(player.getUUID());
            if (selection == null || selection.metric != metric) continue;
            if (!state.isMetricDisplayEnabled(metric)) {
                disableSilently(player);
                continue;
            }
            Objective objective = objectives.computeIfAbsent(selection,
                    value -> syncObjective(server, value.period, value.metric, true));
            sendPackets(player, objective, RankBoardMod.entries(server, selection.period, metric), metric);
        }
        if (globalSelection != null && globalSelection.metric == metric) {
            if (!state.isMetricDisplayEnabled(metric)) {
                server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, null);
                globalSelection = null;
                state.clearGlobalBoardPreference();
                return;
            }
            Objective objective = syncObjective(server, globalSelection.period, metric, false);
            server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }
    }

    static void refreshAll(MinecraftServer server) {
        if (!StatReader.isReady()) return;
        Map<Selection, Objective> objectives = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!canReceive(player)) {
                removePrivateObjective(player);
                player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
                continue;
            }
            RankBoardMod.Period overviewPeriod = OVERVIEW_SELECTIONS.get(player.getUUID());
            if (overviewPeriod != null) sendOverview(player, overviewPeriod);
            Selection selection = SELECTIONS.get(player.getUUID());
            if (selection == null) continue;
            if (!LeaderboardState.get(server).isMetricDisplayEnabled(selection.metric)) {
                disableSilently(player);
                continue;
            }
            Objective objective = objectives.computeIfAbsent(selection,
                    value -> syncObjective(server, value.period, value.metric, true));
            sendPackets(player, objective, RankBoardMod.entries(server, selection.period, selection.metric), selection.metric);
        }
        if (globalSelection != null) {
            if (!LeaderboardState.get(server).isMetricDisplayEnabled(globalSelection.metric)) {
                server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, null);
                globalSelection = null;
                LeaderboardState.get(server).clearGlobalBoardPreference();
                return;
            }
            Objective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
            server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }
    }

    private static void sendPrivate(ServerPlayer player, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        if (!canReceive(player)) return;
        LeaderboardState.BoardPreference preference = LeaderboardState.get(PlayerCompat.server(player))
                .boardPreference(player.getUUID());
        boolean carousel = preference != null && preference.carousel();
        Objective objective = syncObjective(PlayerCompat.server(player), period, metric, true, carousel);
        sendPackets(player, objective, RankBoardMod.entries(PlayerCompat.server(player), period, metric), metric);
    }

    static int enableOverview(CommandSourceStack source, RankBoardMod.Period period) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            SELECTIONS.remove(player.getUUID());
            NEXT_CAROUSEL_AT.remove(player.getUUID());
            OVERVIEW_SELECTIONS.put(player.getUUID(), period);
            LeaderboardState.get(PlayerCompat.server(player)).setOverviewPreference(player.getUUID(), period, true);
            sendOverview(player, period);
            PlayerNameColors.refresh(player);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private static void sendOverview(ServerPlayer player, RankBoardMod.Period period) {
        if (!canReceive(player)) return;
        MinecraftServer server = PlayerCompat.server(player);
        LeaderboardState state = LeaderboardState.get(server);
        Scoreboard scoreboard = server.getScoreboard();
        String name = "rbo_" + period.command;
        Objective objective = scoreboard.getObjective(name);
        boolean partialPeriod = period != RankBoardMod.Period.ALL && !state.isPeriodComplete(period);
        Component title = Component.literal(period.label + (partialPeriod ? "（部分）" : "") + " 我的总览");
        if (objective == null) objective = scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        else { objective.setDisplayName(title); scoreboard.onObjectiveChanged(objective); }
        removePrivateObjective(player);
        player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        CLIENT_OBJECTIVES.put(player.getUUID(), name);
        for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
            if (!state.isMetricDisplayEnabled(metric)) continue;
            long raw = metric.read(player);
            long value = period == RankBoardMod.Period.ALL ? raw : Math.max(0L, raw - state.getBaseline(period, player.getUUID(), metric));
            if (!RankBoardConfig.get().clientScoreboardShowZero && value == 0L) continue;
            int score = scoreboardValue(metric, value);
            player.connection.send(new ClientboundSetScorePacket(metric.label(), name, score, Optional.empty(), scoreboardFormat(metric, score)));
        }
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
    }

    private static void sendPackets(ServerPlayer player, Objective objective,
                                    List<RankBoardMod.Entry> entries, RankBoardMod.Metric metric) {
        removePrivateObjective(player);
        List<RankBoardMod.Entry> visibleEntries = RankBoardConfig.get().clientScoreboardShowZero
                ? entries : entries.stream().filter(entry -> entry.value() != 0L).toList();
        Map<String, ServerPlayer> onlinePlayers = new HashMap<>();
        for (ServerPlayer onlinePlayer : PlayerCompat.server(player).getPlayerList().getPlayers()) {
            onlinePlayers.put(onlinePlayer.getName().getString(), onlinePlayer);
        }
        player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        CLIENT_OBJECTIVES.put(player.getUUID(), objective.getName());
        int totalValue = scoreboardValue(metric, RankBoardMod.total(entries));
        player.connection.send(new ClientboundSetScorePacket(
                "总和", objective.getName(), totalValue, Optional.empty(), scoreboardFormat(metric, totalValue)));
        for (int i = 0; i < Math.min(14, visibleEntries.size()); i++) {
            RankBoardMod.Entry entry = visibleEntries.get(i);
            int value = scoreboardValue(metric, entry.value());
            Optional<Component> displayName = Optional.empty();
            ServerPlayer entryPlayer = onlinePlayers.get(entry.name());
            Selection entrySelection = entryPlayer == null ? null : SELECTIONS.get(entryPlayer.getUUID());
            if (RankBoardConfig.get().nameColorMode != RankBoardConfig.NameColorMode.DISABLED
                    && entryPlayer != null && entrySelection != null) {
                LeaderboardState.BoardPreference entryPreference = LeaderboardState.get(PlayerCompat.server(player))
                        .boardPreference(entryPlayer.getUUID());
                boolean entryCarousel = entryPreference != null && entryPreference.carousel();
                displayName = Optional.of(RankBoardColors.text(entry.name(), entrySelection.metric, entryCarousel));
            }
            player.connection.send(new ClientboundSetScorePacket(
                    entry.name(), objective.getName(), value, displayName, scoreboardFormat(metric, value)));
        }
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
    }

    static int writeVanilla(CommandSourceStack source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) {
                source.sendFailure(Component.literal(metric.label() + " 当前已被 OP 禁止显示。"));
                return 0;
            }
            Scoreboard scoreboard = source.getServer().getScoreboard();
            Objective objective = syncObjective(source.getServer(), period, metric, false);
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
            globalSelection = new Selection(period, metric);
            LeaderboardState.get(source.getServer()).setGlobalBoardPreference(period, metric);
            source.sendSuccess(() -> Component.literal("已更新全服原版计分板：" + objective.getName()), true);
            return 1;
        } catch (RuntimeException exception) {
            RankBoardMod.LOGGER.error("Failed to display global scoreboard: period={}, metric={}",
                    period.command, metric.command, exception);
            source.sendFailure(Component.literal("全服计分板显示失败：" + describe(exception)));
            return 0;
        }
    }

    static int clearVanilla(CommandSourceStack source) {
        source.getServer().getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, null);
        globalSelection = null;
        LeaderboardState.get(source.getServer()).clearGlobalBoardPreference();
        source.sendSuccess(() -> Component.literal("已关闭全服原版计分板。"), true);
        return 1;
    }

    static int clearForeignScoreboards(CommandSourceStack source) {
        List<String> closed = clearForeignScoreboardDisplays(source.getServer());
        restoreGlobalDisplay(source.getServer());
        if (closed.isEmpty()) {
            source.sendSuccess(() -> Component.literal("未检测到正在显示的其他模组计分板。"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已关闭 " + closed.size() + " 个其他计分板显示槽："
                + String.join("，", closed)), true);
        return closed.size();
    }

    static int setForeignScoreboardBlocking(CommandSourceStack source, boolean enabled) {
        try {
            RankBoardConfig.set(source.getServer(), "foreign-scoreboard-blocking-mode",
                    enabled ? "enabled" : "disabled");
            int closed = enabled ? clearForeignScoreboardDisplays(source.getServer()).size() : 0;
            if (enabled) restoreGlobalDisplay(source.getServer());
            source.sendSuccess(() -> Component.literal(enabled
                    ? "已开启其他模组计分板自动屏蔽；本次关闭 " + closed + " 个显示槽。"
                    : "已禁用其他模组计分板自动屏蔽；仍可手动使用 cleanup。"), true);
            return 1;
        } catch (java.io.IOException exception) {
            source.sendFailure(Component.literal("计分板屏蔽设置保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    static int foreignScoreboardBlockingStatus(CommandSourceStack source) {
        String status = switch (RankBoardConfig.get().foreignScoreboardPolicy) {
            case ASK -> "未选择（默认不屏蔽，并继续显示选择提示）";
            case ENABLED -> "已开启";
            case DISABLED -> "已禁用";
        };
        source.sendSuccess(() -> Component.literal("其他模组计分板自动屏蔽：" + status), false);
        return 1;
    }

    static void sendForeignScoreboardPrompt(CommandSourceStack source) {
        if (!CommandPermissionCompat.has(source, 2)
                || RankBoardConfig.get().foreignScoreboardPolicy != RankBoardConfig.ForeignScoreboardPolicy.ASK) return;
        Component prompt = Component.literal("其他模组计分板屏蔽尚未选择：").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[开启]").setStyle(TextCompat.suggest(
                        Style.EMPTY.withColor(ChatFormatting.GREEN),
                        "/leaderboard scoreboard blocking true",
                        Component.literal("填入开启自动屏蔽指令"))))
                .append(Component.literal(" "))
                .append(Component.literal("[禁用]").setStyle(TextCompat.suggest(
                        Style.EMPTY.withColor(ChatFormatting.RED),
                        "/leaderboard scoreboard blocking false",
                        Component.literal("填入禁用自动屏蔽指令"))));
        source.sendSuccess(() -> prompt, false);
    }

    static void enforceForeignScoreboardPolicy(MinecraftServer server) {
        if (RankBoardConfig.get().foreignScoreboardPolicy != RankBoardConfig.ForeignScoreboardPolicy.ENABLED) return;
        List<String> closed = clearForeignScoreboardDisplays(server);
        restoreGlobalDisplay(server);
        if (!closed.isEmpty()) RankBoardMod.LOGGER.info("Closed foreign scoreboard display slots: {}", closed);
    }

    private static List<String> clearForeignScoreboardDisplays(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        List<String> closed = new java.util.ArrayList<>();
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective objective = scoreboard.getDisplayObjective(slot);
            if (objective == null || isRankBoardObjective(objective)) continue;
            closed.add(slot.getSerializedName() + "=" + objective.getName());
            scoreboard.setDisplayObjective(slot, null);
        }
        return closed;
    }

    private static void restoreGlobalDisplay(MinecraftServer server) {
        if (globalSelection != null) {
            Objective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
            server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }
    }

    private static Objective syncObjective(MinecraftServer server, RankBoardMod.Period period,
                                                     RankBoardMod.Metric metric, boolean personal) {
        return syncObjective(server, period, metric, personal, false);
    }

    private static Objective syncObjective(MinecraftServer server, RankBoardMod.Period period,
                                                     RankBoardMod.Metric metric, boolean personal, boolean carousel) {
        String name = objectiveName(period, metric, personal);
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(name);
        String unit = metric == RankBoardMod.Metric.PLAY_TIME ? "（h）" : "";
        boolean partialPeriod = period != RankBoardMod.Period.ALL
                && !LeaderboardState.get(server).isPeriodComplete(period, metric);
        Component title = Component.literal(period.label + (partialPeriod ? "（部分）" : "")
                + " " + metric.label() + unit);
        if (RankBoardConfig.get().scoreboardTitleColorEnabled) {
            int titleColor = carousel && !RankBoardConfig.get().carouselColorFollowMetric
                    ? RankBoardColors.colorValue(ChatFormatting.AQUA)
                    : RankBoardColors.renderedRgb(metric);
            title = title.copy().withStyle(style -> style.withColor(titleColor));
        }
        if (objective == null) {
            objective = scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, title,
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        } else {
            objective.setDisplayName(title);
            scoreboard.onObjectiveChanged(objective);
        }

        for (ScoreHolder holder : List.copyOf(scoreboard.getTrackedPlayers())) {
            scoreboard.resetSinglePlayerScore(holder, objective);
        }
        List<RankBoardMod.Entry> entries = RankBoardMod.entries(server, period, metric);
        int totalValue = scoreboardValue(metric, RankBoardMod.total(entries));
        ScoreAccess totalScore = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("总和"), objective, true);
        totalScore.set(totalValue);
        totalScore.numberFormatOverride(scoreboardFormat(metric, totalValue).orElse(null));
        for (RankBoardMod.Entry entry : entries) {
            int value = scoreboardValue(metric, entry.value());
            ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.name()), objective, true);
            score.set(value);
            score.numberFormatOverride(scoreboardFormat(metric, value).orElse(null));
        }
        return objective;
    }

    private static int clamp(long value) { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)); }
    private static int scoreboardValue(RankBoardMod.Metric metric, long value) {
        return clamp(metric == RankBoardMod.Metric.PLAY_TIME ? value / 72_000L : value);
    }
    private static Optional<NumberFormat> scoreboardFormat(RankBoardMod.Metric metric, int value) {
        if (metric != RankBoardMod.Metric.PLAY_TIME) return Optional.empty();
        return Optional.of(new FixedFormat(Component.literal(value + "h").withStyle(ChatFormatting.RED)));
    }
    private static String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : " - " + exception.getMessage());
    }
    private static void removePrivateObjective(ServerPlayer player) {
        String oldName = CLIENT_OBJECTIVES.remove(player.getUUID());
        if (oldName == null) return;
        Objective oldObjective = PlayerCompat.server(player).getScoreboard().getObjective(oldName);
        if (oldObjective != null) {
            player.connection.send(new ClientboundSetObjectivePacket(
                    oldObjective, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }
    private static void disableSilently(ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
        OVERVIEW_SELECTIONS.remove(player.getUUID());
        NEXT_CAROUSEL_AT.remove(player.getUUID());
        LeaderboardState.get(PlayerCompat.server(player)).disableBoard(player.getUUID());
        removePrivateObjective(player);
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        PlayerNameColors.refresh(player);
        player.sendSystemMessage(Component.literal("当前榜单显示已被 OP 禁用，个人计分板已关闭。").withStyle(ChatFormatting.GRAY), false);
    }
    private static String objectiveName(RankBoardMod.Period period, RankBoardMod.Metric metric, boolean personal) {
        String prefix = personal ? "rbp_" : "rbg_";
        return prefix + period.command.charAt(0) + "_" + metric.command.substring(0, Math.min(7, metric.command.length()));
    }
    private static boolean isRankBoardObjective(Objective objective) {
        String name = objective.getName();
        return name.startsWith("rbp_") || name.startsWith("rbg_") || name.startsWith("rbo_");
    }
    static RankBoardMod.Metric selectedMetric(UUID uuid) {
        Selection selection = SELECTIONS.get(uuid);
        return selection == null ? null : selection.metric;
    }
    private static void scheduleCarousel(UUID uuid) { NEXT_CAROUSEL_AT.put(uuid, carouselDeadline()); }
    private static long carouselDeadline() {
        return System.currentTimeMillis() + RankBoardConfig.get().carouselIntervalSeconds * 1000L;
    }
    private static RankBoardMod.Metric nextMetric(LeaderboardState state, RankBoardMod.Metric current) {
        RankBoardMod.Metric[] metrics = RankBoardMod.Metric.values();
        for (int offset = 1; offset <= metrics.length; offset++) {
            RankBoardMod.Metric candidate = metrics[(current.ordinal() + offset) % metrics.length];
            if (state.isMetricDisplayEnabled(candidate)) return candidate;
        }
        return current;
    }

    private static RankBoardMod.Metric firstEnabledMetric(LeaderboardState state) {
        for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
            if (state.isMetricDisplayEnabled(metric)) return metric;
        }
        return null;
    }
    private record Selection(RankBoardMod.Period period, RankBoardMod.Metric metric) { }

    private record ActivityKey(UUID uuid, RankBoardMod.Metric metric) { }

    private static final class ActivityRate {
        private final ArrayDeque<Long> changes = new ArrayDeque<>();
        private long lastSentAt;

        boolean shouldSend(long now, long windowMillis, int threshold, long throttleMillis) {
            while (!changes.isEmpty() && now - changes.peekFirst() >= windowMillis) changes.removeFirst();
            while (changes.size() > threshold) changes.removeFirst();
            changes.addLast(now);
            if (changes.size() <= threshold) {
                lastSentAt = now;
                return true;
            }
            if (now - lastSentAt >= throttleMillis) {
                lastSentAt = now;
                return true;
            }
            return false;
        }
    }
}
