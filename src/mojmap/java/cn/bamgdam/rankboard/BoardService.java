package cn.bamgdam.rankboard;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
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

    static void disconnect(ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
        CLIENT_OBJECTIVES.remove(player.getUUID());
    }

    static void clearSessions() {
        SELECTIONS.clear();
        CLIENT_OBJECTIVES.clear();
        globalSelection = null;
    }

    static int enable(CommandSourceStack source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            ServerPlayer player = source.getPlayerOrException();
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
            source.sendFailure(Component.literal(metric.label + " 当前已被 OP 禁止显示。"));
            return 0;
        }
        try {
            SELECTIONS.put(player.getUUID(), new Selection(period, metric));
            sendPrivate(player, period, metric);
            source.sendSuccess(() -> Component.literal(player == source.getEntity()
                    ? "已显示个人原版计分板；输入 /leaderboard display off 可关闭。"
                    : "已为 " + player.getName().getString() + " 显示个人原版计分板。"), false);
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
        removePrivateObjective(player);
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        source.sendSuccess(() -> Component.literal(player == source.getEntity()
                ? "已关闭个人计分板。" : "已关闭 " + player.getName().getString() + " 的个人计分板。"), false);
        return 1;
    }

    static void refreshAll(MinecraftServer server) {
        Map<Selection, Objective> objectives = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
                return;
            }
            Objective objective = syncObjective(server, globalSelection.period, globalSelection.metric, false);
            server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }
    }

    private static void sendPrivate(ServerPlayer player, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        Objective objective = syncObjective(PlayerCompat.server(player), period, metric, true);
        sendPackets(player, objective, RankBoardMod.entries(PlayerCompat.server(player), period, metric), metric);
    }

    private static void sendPackets(ServerPlayer player, Objective objective,
                                    List<RankBoardMod.Entry> entries, RankBoardMod.Metric metric) {
        removePrivateObjective(player);
        Map<String, ServerPlayer> onlinePlayers = new HashMap<>();
        for (ServerPlayer onlinePlayer : PlayerCompat.server(player).getPlayerList().getPlayers()) {
            onlinePlayers.put(onlinePlayer.getName().getString(), onlinePlayer);
        }
        player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        CLIENT_OBJECTIVES.put(player.getUUID(), objective.getName());
        int totalValue = scoreboardValue(metric, RankBoardMod.total(entries));
        player.connection.send(new ClientboundSetScorePacket(
                "总和", objective.getName(), totalValue, Optional.empty(), scoreboardFormat(metric, totalValue)));
        for (int i = 0; i < Math.min(14, entries.size()); i++) {
            RankBoardMod.Entry entry = entries.get(i);
            int value = scoreboardValue(metric, entry.value());
            Optional<Component> displayName = Optional.empty();
            ServerPlayer entryPlayer = onlinePlayers.get(entry.name());
            Selection entrySelection = entryPlayer == null ? null : SELECTIONS.get(entryPlayer.getUUID());
            if (entryPlayer != null && entrySelection != null
                    && LeaderboardState.get(PlayerCompat.server(player)).isNameColorEnabled(entryPlayer.getUUID())) {
                displayName = Optional.of(Component.literal(entry.name()).withStyle(entrySelection.metric.nameColor));
            }
            player.connection.send(new ClientboundSetScorePacket(
                    entry.name(), objective.getName(), value, displayName, scoreboardFormat(metric, value)));
        }
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
    }

    static int writeVanilla(CommandSourceStack source, RankBoardMod.Period period, RankBoardMod.Metric metric) {
        try {
            if (!LeaderboardState.get(source.getServer()).isMetricDisplayEnabled(metric)) {
                source.sendFailure(Component.literal(metric.label + " 当前已被 OP 禁止显示。"));
                return 0;
            }
            Scoreboard scoreboard = source.getServer().getScoreboard();
            Objective objective = syncObjective(source.getServer(), period, metric, false);
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
            globalSelection = new Selection(period, metric);
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
        source.sendSuccess(() -> Component.literal("已关闭全服原版计分板。"), true);
        return 1;
    }

    private static Objective syncObjective(MinecraftServer server, RankBoardMod.Period period,
                                                     RankBoardMod.Metric metric, boolean personal) {
        String name = objectiveName(period, metric, personal);
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(name);
        String unit = metric == RankBoardMod.Metric.PLAY_TIME ? "（h）" : "";
        Component title = Component.literal(period.label + " " + metric.label + unit);
        if (objective == null) {
            objective = scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, title,
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        } else {
            objective.setDisplayName(title);
            scoreboard.onObjectiveAdded(objective);
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
        removePrivateObjective(player);
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        player.sendSystemMessage(Component.literal("当前榜单显示已被 OP 禁用，个人计分板已关闭。").withStyle(ChatFormatting.GRAY), false);
    }
    private static String objectiveName(RankBoardMod.Period period, RankBoardMod.Metric metric, boolean personal) {
        String prefix = personal ? "rbp_" : "rbg_";
        return prefix + period.command.charAt(0) + "_" + metric.command.substring(0, Math.min(7, metric.command.length()));
    }
    private record Selection(RankBoardMod.Period period, RankBoardMod.Metric metric) { }
}
