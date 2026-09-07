package cn.bamgdam.rankboard;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks RankBoard metrics that vanilla does not expose in player statistics files. */
final class RuntimeMetricTracker {
    private static final long AFK_THRESHOLD_TICKS = 3L * 60L * 20L;
    private static final Map<UUID, Activity> ACTIVITIES = new HashMap<>();
    private static final Map<UUID, Long> PENDING_AFK = new HashMap<>();
    private static final Map<UUID, Long> PENDING_BEDROCK = new HashMap<>();
    private static final Map<BlockPos, UUID> BEDROCK_WATCHERS = new HashMap<>();
    private static MinecraftServer server;
    private static long tick;

    private RuntimeMetricTracker() { }

    static void start(MinecraftServer value) {
        server = value;
        tick = 0;
        ACTIVITIES.clear();
        PENDING_AFK.clear();
        PENDING_BEDROCK.clear();
        BEDROCK_WATCHERS.clear();
    }

    static void stop(MinecraftServer value) {
        if (server != value) return;
        flush(value);
        ACTIVITIES.clear();
        PENDING_AFK.clear();
        PENDING_BEDROCK.clear();
        BEDROCK_WATCHERS.clear();
        server = null;
    }

    static void join(MinecraftServer value, ServerPlayerEntity player) {
        if (server != value) start(value);
        ACTIVITIES.put(player.getUuid(), new Activity(player.getX(), player.getY(), player.getZ(), tick, tick));
    }

    static void disconnect(MinecraftServer value, ServerPlayerEntity player) {
        if (server != value) return;
        Activity activity = ACTIVITIES.get(player.getUuid());
        if (activity != null) process(value, player, activity, tick);
        flush(value);
        ACTIVITIES.remove(player.getUuid());
    }

    static void tick(MinecraftServer value) {
        if (server != value) start(value);
        tick++;
        for (ServerPlayerEntity player : value.getPlayerManager().getPlayerList()) {
            Activity activity = ACTIVITIES.computeIfAbsent(player.getUuid(), ignored ->
                    new Activity(player.getX(), player.getY(), player.getZ(), tick, tick));
            process(value, player, activity, tick);
        }
        if (RankBoardConfig.get().bedrockBreakLeaderboardEnabled && tick % 20L == 0L) scanBedrock(value);
        else if (!RankBoardConfig.get().bedrockBreakLeaderboardEnabled && !BEDROCK_WATCHERS.isEmpty()) BEDROCK_WATCHERS.clear();
        if (tick % 20L == 0L) flush(value);
    }

    private static void process(MinecraftServer value, ServerPlayerEntity player, Activity activity, long now) {
        boolean moved = player.getX() != activity.x || player.getY() != activity.y || player.getZ() != activity.z;
        if (moved) {
            if (activity.afk) addPending(PENDING_AFK, player.getUuid(), Math.max(0L, now - activity.countedTick));
            activity.afk = false;
            activity.lastMovementTick = now;
            activity.countedTick = now;
            activity.x = player.getX();
            activity.y = player.getY();
            activity.z = player.getZ();
            return;
        }
        if (now - activity.lastMovementTick >= AFK_THRESHOLD_TICKS) {
            if (!activity.afk) {
                activity.afk = true;
                // Count the complete three-minute threshold, not only time after it.
                activity.countedTick = activity.lastMovementTick;
            }
            addPending(PENDING_AFK, player.getUuid(), Math.max(0L, now - activity.countedTick));
            activity.countedTick = now;
        }
    }

    private static void scanBedrock(MinecraftServer value) {
        Map<BlockPos, UUID> seenThisPass = new HashMap<>();
        for (ServerPlayerEntity player : value.getPlayerManager().getPlayerList()) {
            BlockPos center = player.getBlockPos();
            for (int dx = -5; dx <= 5; dx++) for (int dy = -5; dy <= 5; dy++) for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dy * dy + dz * dz > 25) continue;
                BlockPos pos = center.add(dx, dy, dz);
                if (PlayerCompat.world(player).getBlockState(pos).isOf(Blocks.BEDROCK)) {
                    UUID previous = seenThisPass.get(pos);
                    if (previous == null || distanceSquared(value, previous, pos) > distanceSquared(value, player.getUuid(), pos)) {
                        seenThisPass.put(pos, player.getUuid());
                    }
                }
            }
        }
        // A watched bedrock block which is no longer bedrock was removed while it was near a player.
        for (Map.Entry<BlockPos, UUID> entry : new HashMap<>(BEDROCK_WATCHERS).entrySet()) {
            BlockPos pos = entry.getKey();
            if (seenThisPass.containsKey(pos)) continue;
            ServerPlayerEntity watcher = value.getPlayerManager().getPlayer(entry.getValue());
            if (watcher != null && !PlayerCompat.world(watcher).getBlockState(pos).isOf(Blocks.BEDROCK)
                    && watcher.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 36.0) {
                addPending(PENDING_BEDROCK, watcher.getUuid(), 1L);
            }
            BEDROCK_WATCHERS.remove(pos);
        }
        BEDROCK_WATCHERS.putAll(seenThisPass);
    }

    private static double distanceSquared(MinecraftServer value, UUID uuid, BlockPos pos) {
        ServerPlayerEntity player = value.getPlayerManager().getPlayer(uuid);
        return player == null ? Double.MAX_VALUE : player.squaredDistanceTo(Vec3d.ofCenter(pos));
    }

    private static void addPending(Map<UUID, Long> pending, UUID uuid, long amount) {
        if (amount <= 0) return;
        pending.merge(uuid, amount, (oldValue, newValue) -> {
            try { return Math.addExact(oldValue, newValue); }
            catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
        });
    }

    private static void flush(MinecraftServer value) {
        LeaderboardState state = LeaderboardState.get(value);
        PENDING_AFK.forEach((uuid, amount) -> state.addCustomMetric(uuid, RankBoardMod.Metric.AFK_TIME, amount));
        PENDING_BEDROCK.forEach((uuid, amount) -> state.addCustomMetric(uuid, RankBoardMod.Metric.BEDROCK_BROKEN, amount));
        PENDING_AFK.clear();
        PENDING_BEDROCK.clear();
    }

    private static final class Activity {
        double x;
        double y;
        double z;
        long lastMovementTick;
        long countedTick;
        boolean afk;

        Activity(double x, double y, double z, long lastMovementTick, long countedTick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastMovementTick = lastMovementTick;
            this.countedTick = countedTick;
        }
    }
}
