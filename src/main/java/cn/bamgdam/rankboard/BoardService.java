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
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Maintains real vanilla objectives and controls each player's vanilla sidebar with packets. */
final class BoardService {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, String> CLIENT_OBJECTIVES = new HashMap<>();
    private static Selection globalSelection;
    private BoardService() { }

    static void disconnect(ServerPlayerEntity player) {
        SELECTIONS.remove(player.getUuid());
        CLIENT_OBJECTIVES.remove(player.getUuid());
    }

    static void clearSessions() {
        SELECTIONS.clear();
        CLIENT_OBJECTIVES.clear();
        globalSelection = null;
    }

    static int enable(ServerCommandSource source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
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
        if (!LeaderboardState.get(player.getServer()).isMetricDisplayEnabled(metric)) {
            source.sendError(Text.literal(metric.label + " 当前已被 OP 禁止显示。"));
            return 0;
        }
        try {
            SELECTIONS.put(player.getUuid(), new Selection(period, metric));
            sendPrivate(player, period, metric);
            source.sendFeedback(() -> Text.literal(player == source.getEntity()
                    ? "已显示个人原版计分板；输入 /leaderboard display off 可关闭。"
                    : "已为 " + player.getName().getString() + " 显示个人原版计分板。"), false);
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
        removePrivateObjective(player);
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, null));
        source.sendFeedback(() -> Text.literal(player == source.getEntity()
                ? "已关闭个人计分板。" : "已关闭 " + player.getName().getString() + " 的个人计分板。"), false);
        return 1;
    }

    static void refreshAll(MinecraftServer server) {
        Map<Selection, ScoreboardObjective> objectives = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
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
                return;
            }
            ScoreboardObjective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
            server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        }
    }

    private static void sendPrivate(ServerPlayerEntity player, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        ScoreboardObjective objective = syncObjective(player.getServer(), period, metric, true);
        sendPackets(player, objective, RankBoardMod.entries(player.getServer(), period, metric), metric);
    }

    private static void sendPackets(ServerPlayerEntity player, ScoreboardObjective objective,
                                    List<RankBoardMod.Entry> entries, RankBoardMod.Metric metric) {
        removePrivateObjective(player);
        Map<String, ServerPlayerEntity> onlinePlayers = new HashMap<>();
        for (ServerPlayerEntity onlinePlayer : player.getServer().getPlayerManager().getPlayerList()) {
            onlinePlayers.put(onlinePlayer.getName().getString(), onlinePlayer);
        }
        player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
        CLIENT_OBJECTIVES.put(player.getUuid(), objective.getName());
        int totalValue = scoreboardValue(metric, RankBoardMod.total(entries));
        player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                "总和", objective.getName(), totalValue, Optional.empty(), scoreboardFormat(metric, totalValue)));
        for (int i = 0; i < Math.min(14, entries.size()); i++) {
            RankBoardMod.Entry entry = entries.get(i);
            int value = scoreboardValue(metric, entry.value());
            Optional<Text> displayName = Optional.empty();
            ServerPlayerEntity entryPlayer = onlinePlayers.get(entry.name());
            Selection entrySelection = entryPlayer == null ? null : SELECTIONS.get(entryPlayer.getUuid());
            if (entryPlayer != null && entrySelection != null
                    && LeaderboardState.get(player.getServer()).isNameColorEnabled(entryPlayer.getUuid())) {
                displayName = Optional.of(Text.literal(entry.name()).formatted(entrySelection.metric.nameColor));
            }
            player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                    entry.name(), objective.getName(), value, displayName, scoreboardFormat(metric, value)));
        }
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, objective));
    }

    static int writeVanilla(ServerCommandSource source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) {
                source.sendError(Text.literal(metric.label + " 当前已被 OP 禁止显示。"));
                return 0;
            }
            Scoreboard scoreboard = source.getServer().getScoreboard();
            ScoreboardObjective objective = syncObjective(source.getServer(), period, metric, false);
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
            globalSelection = new Selection(period, metric);
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
        source.sendFeedback(() -> Text.literal("已关闭全服原版计分板。"), true);
        return 1;
    }

    private static ScoreboardObjective syncObjective(MinecraftServer server, RankBoardMod.Period period,
                                                     RankBoardMod.Metric metric, boolean personal) {
        String name = objectiveName(period, metric, personal);
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(name);
        String unit = metric == RankBoardMod.Metric.PLAY_TIME ? "（h）" : "";
        Text title = Text.literal(period.label + " " + metric.label + unit);
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
        ScoreboardObjective oldObjective = player.getServer().getScoreboard().getNullableObjective(oldName);
        if (oldObjective != null) {
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(
                    oldObjective, ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE));
        }
    }
    private static void disableSilently(ServerPlayerEntity player) {
        SELECTIONS.remove(player.getUuid());
        removePrivateObjective(player);
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, null));
        player.sendMessage(Text.literal("当前榜单显示已被 OP 禁用，个人计分板已关闭。").formatted(Formatting.GRAY), false);
    }
    private static String objectiveName(RankBoardMod.Period period, RankBoardMod.Metric metric, boolean personal) {
        String prefix = personal ? "rbp_" : "rbg_";
        return prefix + period.command.charAt(0) + "_" + metric.command.substring(0, Math.min(7, metric.command.length()));
    }
    private record Selection(RankBoardMod.Period period, RankBoardMod.Metric metric) { }
}
