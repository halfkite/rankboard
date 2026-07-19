package cn.bamgdam.rankboard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

public final class PlayerNameColors {
    private static final String TEAM_PREFIX = "rbc_";

    private PlayerNameColors() { }

    public static Component decorate(ServerPlayer player, Component fallback) {
        RankBoardMod.Metric metric = BoardService.selectedMetric(player.getUUID());
        if (metric == null || RankBoardConfig.get().nameColorMode != RankBoardConfig.NameColorMode.ENABLED) return fallback;
        MutableComponent name = RankBoardColors.text(player.getName().getString(), metric);
        PlayerTeam team = PlayerCompat.server(player).getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null || isRankBoardTeam(team)) return name;
        return team.getPlayerPrefix().copy().append(name).append(team.getPlayerSuffix());
    }

    static void refresh(ServerPlayer player) {
        MinecraftServer server = PlayerCompat.server(player);
        ServerScoreboard scoreboard = server.getScoreboard();
        String holder = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(holder);
        RankBoardMod.Metric metric = BoardService.selectedMetric(player.getUUID());
        boolean active = metric != null && RankBoardConfig.get().nameColorMode == RankBoardConfig.NameColorMode.ENABLED;
        if (!active) {
            if (isRankBoardTeam(current)) scoreboard.removePlayerFromTeam(holder, current);
        } else if (current == null || isRankBoardTeam(current)) {
            ChatFormatting color = RankBoardColors.legacy(metric);
            String teamName = TEAM_PREFIX + color.ordinal();
            PlayerTeam target = scoreboard.getPlayerTeam(teamName);
            if (target == null) target = scoreboard.addPlayerTeam(teamName);
            setTeamColor(target, color);
            if (current != target) scoreboard.addPlayerToTeam(holder, target);
        }
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) viewer.connection.send(packet);
    }

    static void refreshAll(MinecraftServer server) { server.getPlayerList().getPlayers().forEach(PlayerNameColors::refresh); }

    static void disconnect(ServerPlayer player) {
        ServerScoreboard scoreboard = PlayerCompat.server(player).getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (isRankBoardTeam(team)) scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
    }

    static void clear(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        for (PlayerTeam team : java.util.List.copyOf(scoreboard.getPlayerTeams())) {
            if (isRankBoardTeam(team)) scoreboard.removePlayerTeam(team);
        }
    }

    private static boolean isRankBoardTeam(PlayerTeam team) { return team != null && team.getName().startsWith(TEAM_PREFIX); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setTeamColor(PlayerTeam team, ChatFormatting color) {
        try {
            for (java.lang.reflect.Method method : PlayerTeam.class.getMethods()) {
                if (!method.getName().equals("setColor") || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter == ChatFormatting.class) method.invoke(team, color);
                else {
                    Class<? extends Enum> teamColorClass = (Class<? extends Enum>) Class.forName("net.minecraft.world.scores.TeamColor");
                    Object teamColor = Enum.valueOf(teamColorClass, color.name());
                    method.invoke(team, java.util.Optional.of(teamColor));
                }
                return;
            }
        } catch (ReflectiveOperationException exception) {
            RankBoardMod.LOGGER.warn("Could not apply RankBoard overhead name color", exception);
        }
    }
}
