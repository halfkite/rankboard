package cn.bamgdam.rankboard;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class PlayerNameColors {
    private static final String TEAM_PREFIX = "rbc_";

    private PlayerNameColors() { }

    public static Text decorate(ServerPlayerEntity player, Text fallback) {
        RankBoardMod.Metric metric = BoardService.selectedMetric(player.getUuid());
        if (metric == null || RankBoardConfig.get().nameColorMode != RankBoardConfig.NameColorMode.ENABLED) {
            return fallback;
        }
        MutableText name = RankBoardColors.text(player.getName().getString(), metric);
        Team team = PlayerCompat.server(player).getScoreboard().getScoreHolderTeam(player.getName().getString());
        if (team == null || isRankBoardTeam(team)) return name;
        return team.getPrefix().copy().append(name).append(team.getSuffix());
    }

    static void refresh(ServerPlayerEntity player) {
        MinecraftServer server = PlayerCompat.server(player);
        ServerScoreboard scoreboard = server.getScoreboard();
        String holder = player.getName().getString();
        Team current = scoreboard.getScoreHolderTeam(holder);
        RankBoardMod.Metric metric = BoardService.selectedMetric(player.getUuid());
        boolean active = metric != null && RankBoardConfig.get().nameColorMode == RankBoardConfig.NameColorMode.ENABLED;

        if (!active) {
            if (isRankBoardTeam(current)) scoreboard.removeScoreHolderFromTeam(holder, current);
        } else if (current == null || isRankBoardTeam(current)) {
            Formatting color = RankBoardColors.legacy(metric);
            String teamName = TEAM_PREFIX + color.getColorIndex();
            Team target = scoreboard.getTeam(teamName);
            if (target == null) target = scoreboard.addTeam(teamName);
            if (target.getColor() != color) target.setColor(color);
            if (current != target) scoreboard.addScoreHolderToTeam(holder, target);
        }

        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player);
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(packet);
        }
    }

    static void refreshAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) refresh(player);
    }

    static void disconnect(ServerPlayerEntity player) {
        ServerScoreboard scoreboard = PlayerCompat.server(player).getScoreboard();
        Team team = scoreboard.getScoreHolderTeam(player.getName().getString());
        if (isRankBoardTeam(team)) scoreboard.removeScoreHolderFromTeam(player.getName().getString(), team);
    }

    static void clear(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        for (Team team : java.util.List.copyOf(scoreboard.getTeams())) {
            if (isRankBoardTeam(team)) scoreboard.removeTeam(team);
        }
    }

    private static boolean isRankBoardTeam(Team team) {
        return team != null && team.getName().startsWith(TEAM_PREFIX);
    }
}
