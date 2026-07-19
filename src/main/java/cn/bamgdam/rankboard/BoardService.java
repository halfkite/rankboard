package cn.bamgdam.rankboard;

import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

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

    static void disconnect(ServerPlayerEntity player) {
        PlayerNameColors.disconnect(player);
        SELECTIONS.remove(player.getUuid());
        OVERVIEW_SELECTIONS.remove(player.getUuid());
        CLIENT_OBJECTIVES.remove(player.getUuid());
        NEXT_CAROUSEL_AT.remove(player.getUuid());
        LAST_ACTIVITY_VALUES.keySet().removeIf(key -> key.uuid.equals(player.getUuid()));
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

    static int enable(ServerCommandSource source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            OVERVIEW_SELECTIONS.remove(player.getUuid());
            return enable(source, player, period, metric);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        } catch (RuntimeException exception) {
            RankBoardMod.LOGGER.error("Failed to display personal scoreboard: period={}, metric={}",
                    period.command, metric.command, exception);
            source.sendError(Text.literal("个人计分板显示失败：" + describe(exception)));
            return 0;
        }
    }

    static int enable(ServerCommandSource source, ServerPlayerEntity player, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        if (!LeaderboardState.get(PlayerCompat.server(player)).isMetricDisplayEnabled(metric)) {
            source.sendError(Text.literal(metric.label() + " 当前已被 OP 禁止显示。"));
            return 0;
        }
        try {
            OVERVIEW_SELECTIONS.remove(player.getUuid());
            SELECTIONS.put(player.getUuid(), new Selection(period, metric));
            NEXT_CAROUSEL_AT.remove(player.getUuid());
            LeaderboardState.get(PlayerCompat.server(player)).setBoardPreference(
                    player.getUuid(), period, metric, true, false);
            sendPrivate(player, period, metric);
            PlayerNameColors.refresh(player);
            if (RankBoardConfig.get().scoreboardSwitchMessageEnabled) {
                player.sendMessage(Text.literal(
                        "已显示个人原版计分板；输入 /leaderboard display off 可关闭。"), false);
            }
            return 1;
        } catch (RuntimeException exception) {
            RankBoardMod.LOGGER.error("Failed to display personal scoreboard: player={}, period={}, metric={}",
                    player.getName().getString(), period.command, metric.command, exception);
            source.sendError(Text.literal("个人计分板显示失败：" + describe(exception)));
            return 0;
        }
    }

    static int disable(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            return disable(source, player);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    static int disable(ServerCommandSource source, ServerPlayerEntity player) {
        SELECTIONS.remove(player.getUuid());
        OVERVIEW_SELECTIONS.remove(player.getUuid());
        NEXT_CAROUSEL_AT.remove(player.getUuid());
        LeaderboardState.get(PlayerCompat.server(player)).disableBoard(player.getUuid());
        removePrivateObjective(player);
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, null));
        PlayerNameColors.refresh(player);
        source.sendFeedback(() -> Text.literal(player == source.getEntity()
                ? "已关闭个人计分板。" : "已关闭 " + player.getName().getString() + " 的个人计分板。"), false);
        return 1;
    }

    static void restore(ServerPlayerEntity player) {
        if (!RankBoardConfig.get().restoreBoardOnJoin) return;
        LeaderboardState state = LeaderboardState.get(PlayerCompat.server(player));
        LeaderboardState.BoardPreference preference = state.boardPreference(player.getUuid());
        if (preference == null || !preference.enabled()) return;
        if (preference.overview()) {
            OVERVIEW_SELECTIONS.put(player.getUuid(), preference.period());
            try { sendOverview(player, preference.period()); }
            catch (RuntimeException exception) {
                RankBoardMod.LOGGER.warn("Could not restore overview for {}", player.getName().getString(), exception);
            }
            PlayerNameColors.refresh(player);
            return;
        }
        if (!state.isMetricDisplayEnabled(preference.metric())) return;
        Selection selection = new Selection(preference.period(), preference.metric());
        SELECTIONS.put(player.getUuid(), selection);
        if (preference.carousel() && RankBoardConfig.get().carouselEnabled) scheduleCarousel(player.getUuid());
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
        globalSelection = new Selection(preference.period(), preference.metric());
        ScoreboardObjective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
        server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
    }

    static int setCarousel(ServerCommandSource source, boolean enabled) {
        if (!RankBoardConfig.get().carouselEnabled) {
            source.sendError(Text.literal("榜单轮播功能已在配置中关闭。"));
            return 0;
        }
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            OVERVIEW_SELECTIONS.remove(player.getUuid());
            LeaderboardState state = LeaderboardState.get(source.getServer());
            LeaderboardState.BoardPreference stored = state.boardPreference(player.getUuid());
            Selection selection = SELECTIONS.get(player.getUuid());
            if (selection == null && stored != null) selection = new Selection(stored.period(), stored.metric());
            if (selection == null) selection = new Selection(RankBoardMod.Period.ALL, RankBoardMod.Metric.PLAY_TIME);
            SELECTIONS.put(player.getUuid(), selection);
            state.setBoardPreference(player.getUuid(), selection.period, selection.metric, true, enabled);
            if (enabled) scheduleCarousel(player.getUuid()); else NEXT_CAROUSEL_AT.remove(player.getUuid());
            sendPrivate(player, selection.period, selection.metric);
            PlayerNameColors.refresh(player);
            int seconds = RankBoardConfig.get().carouselIntervalSeconds;
            source.sendFeedback(() -> Text.literal(enabled
                    ? "已开启榜单轮播，每 " + seconds + " 秒自动切换。"
                    : "已关闭榜单轮播，保留当前计分板。"), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    static int carouselStatus(ServerCommandSource source) {
        try {
            UUID uuid = source.getPlayerOrThrow().getUuid();
            LeaderboardState.BoardPreference preference = LeaderboardState.get(source.getServer()).boardPreference(uuid);
            boolean enabled = preference != null && preference.carousel();
            source.sendFeedback(() -> Text.literal("榜单轮播：" + (enabled ? "已开启" : "已关闭")
                    + "；切换间隔 " + RankBoardConfig.get().carouselIntervalSeconds + " 秒。"), false);
            return enabled ? 1 : 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    static void tickCarousel(MinecraftServer server) {
        if (!RankBoardConfig.get().carouselEnabled) return;
        long now = System.currentTimeMillis();
        LeaderboardState state = LeaderboardState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            LeaderboardState.BoardPreference preference = state.boardPreference(player.getUuid());
            if (preference == null || !preference.enabled() || !preference.carousel()) continue;
            long due = NEXT_CAROUSEL_AT.computeIfAbsent(player.getUuid(), ignored -> carouselDeadline());
            if (now < due) continue;
            RankBoardMod.Metric next = nextMetric(state, preference.metric());
            Selection selection = new Selection(preference.period(), next);
            SELECTIONS.put(player.getUuid(), selection);
            state.setBoardPreference(player.getUuid(), selection.period, selection.metric, true, true);
            NEXT_CAROUSEL_AT.put(player.getUuid(), carouselDeadline());
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
        if (!config.scoreboardLiveUpdateEnabled || (SELECTIONS.isEmpty() && OVERVIEW_SELECTIONS.isEmpty())) return;
        Set<RankBoardMod.Metric> activeMetrics = new HashSet<>();
        for (Selection selection : SELECTIONS.values()) activeMetrics.add(selection.metric);
        if (!OVERVIEW_SELECTIONS.isEmpty()) {
            for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) activeMetrics.add(metric);
        }
        long now = System.currentTimeMillis();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (RankBoardMod.Metric metric : activeMetrics) {
                long value;
                try { value = metric.read(player); }
                catch (RuntimeException exception) { continue; }
                ActivityKey key = new ActivityKey(player.getUuid(), metric);
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
        Map<Selection, ScoreboardObjective> objectives = new HashMap<>();
        LeaderboardState state = LeaderboardState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            RankBoardMod.Period overviewPeriod = OVERVIEW_SELECTIONS.get(player.getUuid());
            if (overviewPeriod != null) {
                try { sendOverview(player, overviewPeriod); }
                catch (RuntimeException exception) { RankBoardMod.LOGGER.warn("Could not refresh overview", exception); }
            }
            Selection selection = SELECTIONS.get(player.getUuid());
            if (selection == null || selection.metric != metric) continue;
            if (!state.isMetricDisplayEnabled(metric)) {
                disableSilently(player);
                continue;
            }
            ScoreboardObjective objective = objectives.computeIfAbsent(selection,
                    value -> syncObjective(server, value.period, value.metric, true));
            sendPackets(player, objective, RankBoardMod.entries(server, selection.period, metric), metric);
        }
    }

    static void refreshAll(MinecraftServer server) {
        Map<Selection, ScoreboardObjective> objectives = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            RankBoardMod.Period overviewPeriod = OVERVIEW_SELECTIONS.get(player.getUuid());
            if (overviewPeriod != null) sendOverview(player, overviewPeriod);
            Selection selection = SELECTIONS.get(player.getUuid());
            if (selection == null) continue;
            if (!LeaderboardState.get(server).isMetricDisplayEnabled(selection.metric)) {
                disableSilently(player);
                continue;
            }
            ScoreboardObjective objective = objectives.computeIfAbsent(selection,
                    value -> syncObjective(server, value.period, value.metric, true));
            sendPackets(player, objective, RankBoardMod.entries(server, selection.period, selection.metric), selection.metric);
        }
        if (globalSelection != null) {
            if (!LeaderboardState.get(server).isMetricDisplayEnabled(globalSelection.metric)) {
                server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
                globalSelection = null;
                LeaderboardState.get(server).clearGlobalBoardPreference();
                return;
            }
            ScoreboardObjective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
            server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        }
    }

    private static void sendPrivate(ServerPlayerEntity player, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        ScoreboardObjective objective = syncObjective(PlayerCompat.server(player), period, metric, true);
        sendPackets(player, objective, RankBoardMod.entries(PlayerCompat.server(player), period, metric), metric);
    }

    static int enableOverview(ServerCommandSource source, RankBoardMod.Period period) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            SELECTIONS.remove(player.getUuid());
            NEXT_CAROUSEL_AT.remove(player.getUuid());
            OVERVIEW_SELECTIONS.put(player.getUuid(), period);
            LeaderboardState.get(PlayerCompat.server(player)).setOverviewPreference(player.getUuid(), period, true);
            sendOverview(player, period);
            PlayerNameColors.refresh(player);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendError(Text.literal("该命令只能由玩家执行。"));
            return 0;
        }
    }

    private static void sendOverview(ServerPlayerEntity player, RankBoardMod.Period period) {
        MinecraftServer server = PlayerCompat.server(player);
        Scoreboard scoreboard = server.getScoreboard();
        String name = "rbo_" + period.command;
        ScoreboardObjective objective = scoreboard.getNullableObjective(name);
        Text title = Text.literal(period.label + " 我的总览");
        if (objective == null) {
            objective = scoreboard.addObjective(name, ScoreboardCriterion.DUMMY, title,
                    ScoreboardCriterion.RenderType.INTEGER, false, null);
        } else {
            objective.setDisplayName(title);
            scoreboard.updateObjective(objective);
        }
        removePrivateObjective(player);
        player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(
                objective, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
        CLIENT_OBJECTIVES.put(player.getUuid(), name);
        LeaderboardState state = LeaderboardState.get(server);
        for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
            if (!state.isMetricDisplayEnabled(metric)) continue;
            long raw = metric.read(player);
            long value = period == RankBoardMod.Period.ALL
                    ? raw : Math.max(0L, raw - state.getBaseline(period, player.getUuid(), metric));
            if (!RankBoardConfig.get().clientScoreboardShowZero && value == 0L) continue;
            int score = scoreboardValue(metric, value);
            player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                    metric.label(), name, score, Optional.empty(), scoreboardFormat(metric, score)));
        }
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, objective));
    }

    private static void sendPackets(ServerPlayerEntity player, ScoreboardObjective objective,
                                    List<RankBoardMod.Entry> entries, RankBoardMod.Metric metric) {
        removePrivateObjective(player);
        List<RankBoardMod.Entry> visibleEntries = RankBoardConfig.get().clientScoreboardShowZero
                ? entries : entries.stream().filter(entry -> entry.value() != 0L).toList();
        Map<String, ServerPlayerEntity> onlinePlayers = new HashMap<>();
        for (ServerPlayerEntity onlinePlayer : PlayerCompat.server(player).getPlayerManager().getPlayerList()) {
            onlinePlayers.put(onlinePlayer.getName().getString(), onlinePlayer);
        }
        player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
        CLIENT_OBJECTIVES.put(player.getUuid(), objective.getName());
        int totalValue = scoreboardValue(metric, RankBoardMod.total(entries));
        player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                "总和", objective.getName(), totalValue, Optional.empty(), scoreboardFormat(metric, totalValue)));
        for (int i = 0; i < Math.min(14, visibleEntries.size()); i++) {
            RankBoardMod.Entry entry = visibleEntries.get(i);
            int value = scoreboardValue(metric, entry.value());
            Optional<Text> displayName = Optional.empty();
            ServerPlayerEntity entryPlayer = onlinePlayers.get(entry.name());
            Selection entrySelection = entryPlayer == null ? null : SELECTIONS.get(entryPlayer.getUuid());
            if (RankBoardConfig.get().nameColorMode != RankBoardConfig.NameColorMode.DISABLED
                    && entryPlayer != null && entrySelection != null) {
                displayName = Optional.of(RankBoardColors.text(entry.name(), entrySelection.metric));
            }
            player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                    entry.name(), objective.getName(), value, displayName, scoreboardFormat(metric, value)));
        }
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, objective));
    }

    static int writeVanilla(ServerCommandSource source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) {
                source.sendError(Text.literal(metric.label() + " 当前已被 OP 禁止显示。"));
                return 0;
            }
            Scoreboard scoreboard = source.getServer().getScoreboard();
            ScoreboardObjective objective = syncObjective(source.getServer(), period, metric, false);
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
            globalSelection = new Selection(period, metric);
            LeaderboardState.get(source.getServer()).setGlobalBoardPreference(period, metric);
            source.sendFeedback(() -> Text.literal("已更新全服原版计分板：" + objective.getName()), true);
            return 1;
        } catch (RuntimeException exception) {
            RankBoardMod.LOGGER.error("Failed to display global scoreboard: period={}, metric={}",
                    period.command, metric.command, exception);
            source.sendError(Text.literal("全服计分板显示失败：" + describe(exception)));
            return 0;
        }
    }

    static int clearVanilla(ServerCommandSource source) {
        source.getServer().getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        globalSelection = null;
        LeaderboardState.get(source.getServer()).clearGlobalBoardPreference();
        source.sendFeedback(() -> Text.literal("已关闭全服原版计分板。"), true);
        return 1;
    }

    static int clearForeignScoreboards(ServerCommandSource source) {
        List<String> closed = clearForeignScoreboardDisplays(source.getServer());
        restoreGlobalDisplay(source.getServer());
        if (closed.isEmpty()) {
            source.sendFeedback(() -> Text.literal("未检测到正在显示的其他模组计分板。"), false);
            return 0;
        }
        source.sendFeedback(() -> Text.literal("已关闭 " + closed.size() + " 个其他计分板显示槽："
                + String.join("，", closed)), true);
        return closed.size();
    }

    static int setForeignScoreboardBlocking(ServerCommandSource source, boolean enabled) {
        try {
            RankBoardConfig.set(source.getServer(), "foreign-scoreboard-blocking-mode",
                    enabled ? "enabled" : "disabled");
            int closed = enabled ? clearForeignScoreboardDisplays(source.getServer()).size() : 0;
            if (enabled) restoreGlobalDisplay(source.getServer());
            source.sendFeedback(() -> Text.literal(enabled
                    ? "已开启其他模组计分板自动屏蔽；本次关闭 " + closed + " 个显示槽。"
                    : "已禁用其他模组计分板自动屏蔽；仍可手动使用 cleanup。"), true);
            return 1;
        } catch (java.io.IOException exception) {
            source.sendError(Text.literal("计分板屏蔽设置保存失败：" + exception.getMessage()));
            return 0;
        }
    }

    static int foreignScoreboardBlockingStatus(ServerCommandSource source) {
        String status = switch (RankBoardConfig.get().foreignScoreboardPolicy) {
            case ASK -> "未选择（默认不屏蔽，并继续显示选择提示）";
            case ENABLED -> "已开启";
            case DISABLED -> "已禁用";
        };
        source.sendFeedback(() -> Text.literal("其他模组计分板自动屏蔽：" + status), false);
        return 1;
    }

    static void sendForeignScoreboardPrompt(ServerCommandSource source) {
        if (!CommandPermissionCompat.has(source, 2)
                || RankBoardConfig.get().foreignScoreboardPolicy != RankBoardConfig.ForeignScoreboardPolicy.ASK) return;
        Text prompt = Text.literal("其他模组计分板屏蔽尚未选择：").formatted(Formatting.GRAY)
                .copy().append(Text.literal("[开启]").setStyle(TextCompat.suggest(
                        Style.EMPTY.withColor(Formatting.GREEN),
                        "/leaderboard scoreboard blocking true", Text.literal("填入开启自动屏蔽指令"))))
                .append(Text.literal(" "))
                .append(Text.literal("[禁用]").setStyle(TextCompat.suggest(
                        Style.EMPTY.withColor(Formatting.RED),
                        "/leaderboard scoreboard blocking false", Text.literal("填入禁用自动屏蔽指令"))));
        source.sendFeedback(() -> prompt, false);
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
        for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
            ScoreboardObjective objective = scoreboard.getObjectiveForSlot(slot);
            if (objective == null || isRankBoardObjective(objective)) continue;
            closed.add(slot.asString() + "=" + objective.getName());
            scoreboard.setObjectiveSlot(slot, null);
        }
        return closed;
    }

    private static void restoreGlobalDisplay(MinecraftServer server) {
        if (globalSelection != null) {
            ScoreboardObjective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
            server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        }
    }

    private static ScoreboardObjective syncObjective(MinecraftServer server, RankBoardMod.Period period,
                                                     RankBoardMod.Metric metric, boolean personal) {
        String name = objectiveName(period, metric, personal);
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(name);
        String unit = metric == RankBoardMod.Metric.PLAY_TIME ? "（h）" : "";
        Text title = Text.literal(period.label + " " + metric.label() + unit);
        if (RankBoardConfig.get().scoreboardTitleColorEnabled) {
            title = title.copy().styled(style -> style.withColor(RankBoardColors.renderedRgb(metric)));
        }
        if (objective == null) {
            objective = scoreboard.addObjective(name, ScoreboardCriterion.DUMMY, title,
                    ScoreboardCriterion.RenderType.INTEGER, false, null);
        } else {
            objective.setDisplayName(title);
            scoreboard.updateObjective(objective);
        }

        for (ScoreHolder holder : List.copyOf(scoreboard.getKnownScoreHolders())) {
            scoreboard.removeScore(holder, objective);
        }
        List<RankBoardMod.Entry> entries = RankBoardMod.entries(server, period, metric);
        int totalValue = scoreboardValue(metric, RankBoardMod.total(entries));
        ScoreAccess totalScore = scoreboard.getOrCreateScore(ScoreHolder.fromName("总和"), objective, true);
        totalScore.setScore(totalValue);
        totalScore.setNumberFormat(scoreboardFormat(metric, totalValue).orElse(null));
        for (RankBoardMod.Entry entry : entries) {
            int value = scoreboardValue(metric, entry.value());
            ScoreAccess score = scoreboard.getOrCreateScore(ScoreHolder.fromName(entry.name()), objective, true);
            score.setScore(value);
            score.setNumberFormat(scoreboardFormat(metric, value).orElse(null));
        }
        return objective;
    }

    private static int clamp(long value) { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)); }
    private static int scoreboardValue(RankBoardMod.Metric metric, long value) {
        return clamp(metric == RankBoardMod.Metric.PLAY_TIME ? value / 72_000L : value);
    }
    private static Optional<NumberFormat> scoreboardFormat(RankBoardMod.Metric metric, int value) {
        if (metric != RankBoardMod.Metric.PLAY_TIME) return Optional.empty();
        return Optional.of(new FixedNumberFormat(Text.literal(value + "h").formatted(Formatting.RED)));
    }
    private static String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : " - " + exception.getMessage());
    }
    private static void removePrivateObjective(ServerPlayerEntity player) {
        String oldName = CLIENT_OBJECTIVES.remove(player.getUuid());
        if (oldName == null) return;
        ScoreboardObjective oldObjective = PlayerCompat.server(player).getScoreboard().getNullableObjective(oldName);
        if (oldObjective != null) {
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(
                    oldObjective, ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE));
        }
    }
    private static void disableSilently(ServerPlayerEntity player) {
        SELECTIONS.remove(player.getUuid());
        OVERVIEW_SELECTIONS.remove(player.getUuid());
        NEXT_CAROUSEL_AT.remove(player.getUuid());
        LeaderboardState.get(PlayerCompat.server(player)).disableBoard(player.getUuid());
        removePrivateObjective(player);
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, null));
        PlayerNameColors.refresh(player);
        player.sendMessage(Text.literal("当前榜单显示已被 OP 禁用，个人计分板已关闭。").formatted(Formatting.GRAY), false);
    }
    private static String objectiveName(RankBoardMod.Period period, RankBoardMod.Metric metric, boolean personal) {
        String prefix = personal ? "rbp_" : "rbg_";
        return prefix + period.command.charAt(0) + "_" + metric.command.substring(0, Math.min(7, metric.command.length()));
    }
    private static boolean isRankBoardObjective(ScoreboardObjective objective) {
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
