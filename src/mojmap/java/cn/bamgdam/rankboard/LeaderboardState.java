package cn.bamgdam.rankboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;

/** Stores raw-stat baselines, allowing period ranks without modifying vanilla statistics. */
public final class LeaderboardState extends SavedData {
    private static final String STATE_ID = "rankboard_leaderboard";
    private final Map<RankBoardMod.Period, PeriodData> periods = new EnumMap<>(RankBoardMod.Period.class);
    private boolean whitelistOnly = true;
    private boolean botFilterEnabled = true;
    private boolean customPlayerFilterEnabled = true;
    private boolean onlineOnly;
    private final Set<RankBoardMod.Metric> disabledDisplayMetrics = new HashSet<>();
    private final Set<UUID> nameColorDisabledPlayers = new HashSet<>();
    private final Set<UUID> lookMenuDisabledPlayers = new HashSet<>();
    private final Map<UUID, BoardPreference> boardPreferences = new HashMap<>();
    private BoardPreference globalBoardPreference;
    private final NavigableMap<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> dailySnapshots = new TreeMap<>();
    LeaderboardState() { }

    public static LeaderboardState get(MinecraftServer server) {
        return PersistentStateCompat.get(server, STATE_ID);
    }
    static LeaderboardState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        LeaderboardState state = new LeaderboardState();
        if (nbt.contains("whitelistOnly")) state.whitelistOnly = NbtCompat.getBoolean(nbt, "whitelistOnly");
        if (nbt.contains("botFilterEnabled")) state.botFilterEnabled = NbtCompat.getBoolean(nbt, "botFilterEnabled");
        if (nbt.contains("customPlayerFilterEnabled")) state.customPlayerFilterEnabled = NbtCompat.getBoolean(nbt, "customPlayerFilterEnabled");
        if (nbt.contains("onlineOnly")) state.onlineOnly = NbtCompat.getBoolean(nbt, "onlineOnly");
        for (Tag element : NbtCompat.getList(nbt, "disabledDisplayMetrics", Tag.TAG_STRING)) {
            try { state.disabledDisplayMetrics.add(RankBoardMod.Metric.valueOf(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (Tag element : NbtCompat.getList(nbt, "nameColorDisabledPlayers", Tag.TAG_STRING)) {
            try { state.nameColorDisabledPlayers.add(UUID.fromString(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (Tag element : NbtCompat.getList(nbt, "lookMenuDisabledPlayers", Tag.TAG_STRING)) {
            try { state.lookMenuDisabledPlayers.add(UUID.fromString(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (Tag element : NbtCompat.getList(nbt, "periods", Tag.TAG_COMPOUND)) {
            PeriodData data = PeriodData.fromNbt((CompoundTag) element);
            state.periods.put(data.period, data);
        }
        for (Tag element : NbtCompat.getList(nbt, "dailySnapshots", Tag.TAG_COMPOUND)) {
            CompoundTag snapshot = (CompoundTag) element;
            state.dailySnapshots.put(LocalDate.parse(NbtCompat.getString(snapshot, "date")), readPlayers(snapshot));
        }
        for (Tag element : NbtCompat.getList(nbt, "boardPreferences", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag entry = (CompoundTag) element;
                UUID uuid = NbtCompat.getUuid(entry, "uuid");
                RankBoardMod.Period period = RankBoardMod.Period.valueOf(NbtCompat.getString(entry, "period"));
                RankBoardMod.Metric metric = RankBoardMod.Metric.valueOf(NbtCompat.getString(entry, "metric"));
                state.boardPreferences.put(uuid, new BoardPreference(period, metric,
                        NbtCompat.getBoolean(entry, "enabled"), NbtCompat.getBoolean(entry, "carousel"),
                        NbtCompat.getBoolean(entry, "overview")));
            } catch (IllegalArgumentException ignored) { }
        }
        if (nbt.contains("globalBoardPreference")) {
            try {
                CompoundTag entry = NbtCompat.getCompound(nbt, "globalBoardPreference");
                RankBoardMod.Period period = RankBoardMod.Period.valueOf(NbtCompat.getString(entry, "period"));
                RankBoardMod.Metric metric = RankBoardMod.Metric.valueOf(NbtCompat.getString(entry, "metric"));
                state.globalBoardPreference = new BoardPreference(period, metric, true, false, false);
            } catch (IllegalArgumentException ignored) { }
        }
        return state;
    }
    public CompoundTag writeNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        periods.values().forEach(data -> list.add(data.toNbt()));
        nbt.put("periods", list);
        nbt.putBoolean("whitelistOnly", whitelistOnly);
        nbt.putBoolean("botFilterEnabled", botFilterEnabled);
        nbt.putBoolean("customPlayerFilterEnabled", customPlayerFilterEnabled);
        nbt.putBoolean("onlineOnly", onlineOnly);
        ListTag disabledMetrics = new ListTag();
        disabledDisplayMetrics.forEach(metric -> disabledMetrics.add(StringTag.valueOf(metric.name())));
        nbt.put("disabledDisplayMetrics", disabledMetrics);
        ListTag disabledColors = new ListTag();
        nameColorDisabledPlayers.forEach(uuid -> disabledColors.add(StringTag.valueOf(uuid.toString())));
        nbt.put("nameColorDisabledPlayers", disabledColors);
        ListTag disabledLookMenus = new ListTag();
        lookMenuDisabledPlayers.forEach(uuid -> disabledLookMenus.add(StringTag.valueOf(uuid.toString())));
        nbt.put("lookMenuDisabledPlayers", disabledLookMenus);
        ListTag snapshots = new ListTag();
        dailySnapshots.forEach((date, players) -> {
            CompoundTag snapshot = new CompoundTag();
            snapshot.putString("date", date.toString());
            snapshot.put("players", writePlayers(players));
            snapshots.add(snapshot);
        });
        nbt.put("dailySnapshots", snapshots);
        ListTag preferences = new ListTag();
        boardPreferences.forEach((uuid, preference) -> {
            CompoundTag entry = new CompoundTag();
            NbtCompat.putUuid(entry, "uuid", uuid);
            entry.putString("period", preference.period().name());
            entry.putString("metric", preference.metric().name());
            entry.putBoolean("enabled", preference.enabled());
            entry.putBoolean("carousel", preference.carousel());
            entry.putBoolean("overview", preference.overview());
            preferences.add(entry);
        });
        nbt.put("boardPreferences", preferences);
        if (globalBoardPreference != null && globalBoardPreference.enabled()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("period", globalBoardPreference.period().name());
            entry.putString("metric", globalBoardPreference.metric().name());
            nbt.put("globalBoardPreference", entry);
        }
        return nbt;
    }
    public void rollPeriods(MinecraftServer server) {
        if (!StatReader.isReady()) return;
        LocalDate now = LocalDate.now();
        boolean changed = false;
        List<StatSnapshot> snapshots = StatReader.readAll(server);
        for (RankBoardMod.Period period : RankBoardMod.Period.values()) {
            if (period == RankBoardMod.Period.ALL) continue;
            PeriodData old = periods.get(period);
            if (old == null || !old.key.equals(period.key(now))) {
                PeriodData replacement = new PeriodData(period, period.key(now));
                snapshots.forEach(replacement::capture);
                periods.put(period, replacement);
                changed = true;
            }
        }
        if (!dailySnapshots.containsKey(now)) {
            Map<UUID, Map<RankBoardMod.Metric, Long>> values = new HashMap<>();
            snapshots.forEach(snapshot -> values.put(snapshot.uuid(), new EnumMap<>(snapshot.values())));
            dailySnapshots.put(now, values);
            changed = true;
        }
        if (backfillMissingMetrics(snapshots)) changed = true;
        if (changed) setDirty();
    }

    private boolean backfillMissingMetrics(List<StatSnapshot> snapshots) {
        boolean changed = false;
        for (PeriodData data : periods.values()) {
            for (StatSnapshot snapshot : snapshots) {
                Map<RankBoardMod.Metric, Long> values = data.players.computeIfAbsent(
                        snapshot.uuid(), ignored -> new EnumMap<>(RankBoardMod.Metric.class));
                for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                    if (!values.containsKey(metric)) {
                        values.put(metric, snapshot.value(metric));
                        changed = true;
                    }
                }
            }
        }
        for (Map<UUID, Map<RankBoardMod.Metric, Long>> players : dailySnapshots.values()) {
            for (StatSnapshot snapshot : snapshots) {
                Map<RankBoardMod.Metric, Long> values = players.computeIfAbsent(
                        snapshot.uuid(), ignored -> new EnumMap<>(RankBoardMod.Metric.class));
                for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                    if (!values.containsKey(metric)) {
                        values.put(metric, snapshot.value(metric));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
    public void ensurePlayer(ServerPlayer player) {
        rollPeriods(PlayerCompat.server(player));
        StatSnapshot snapshot = StatSnapshot.fromPlayer(player);
        boolean changed = false;
        for (PeriodData data : periods.values()) if (data.players.putIfAbsent(snapshot.uuid(), snapshot.values()) == null) changed = true;
        if (changed) setDirty();
    }
    public long getBaseline(RankBoardMod.Period period, UUID uuid, RankBoardMod.Metric metric) {
        PeriodData data = periods.get(period);
        return data == null ? 0 : data.players.getOrDefault(uuid, Map.of()).getOrDefault(metric, 0L);
    }
    public boolean isWhitelistOnly() { return whitelistOnly; }
    public void setWhitelistOnly(boolean whitelistOnly) {
        if (this.whitelistOnly != whitelistOnly) {
            this.whitelistOnly = whitelistOnly;
            setDirty();
        }
    }
    public boolean isBotFilterEnabled() { return botFilterEnabled; }
    public void setBotFilterEnabled(boolean enabled) {
        if (botFilterEnabled != enabled) {
            botFilterEnabled = enabled;
            setDirty();
        }
    }
    public boolean isCustomPlayerFilterEnabled() { return customPlayerFilterEnabled; }
    public void setCustomPlayerFilterEnabled(boolean enabled) {
        if (customPlayerFilterEnabled != enabled) {
            customPlayerFilterEnabled = enabled;
            setDirty();
        }
    }
    public boolean isOnlineOnly() { return onlineOnly; }
    public void setOnlineOnly(boolean enabled) {
        if (onlineOnly != enabled) {
            onlineOnly = enabled;
            setDirty();
        }
    }
    public boolean isMetricDisplayEnabled(RankBoardMod.Metric metric) { return !disabledDisplayMetrics.contains(metric); }
    public void setMetricDisplayEnabled(RankBoardMod.Metric metric, boolean enabled) {
        boolean changed = enabled ? disabledDisplayMetrics.remove(metric) : disabledDisplayMetrics.add(metric);
        if (changed) setDirty();
    }
    public boolean isNameColorEnabled(UUID uuid) { return !nameColorDisabledPlayers.contains(uuid); }
    public void setNameColorEnabled(UUID uuid, boolean enabled) {
        boolean changed = enabled ? nameColorDisabledPlayers.remove(uuid) : nameColorDisabledPlayers.add(uuid);
        if (changed) setDirty();
    }

    public boolean isLookMenuEnabled(UUID uuid) { return !lookMenuDisabledPlayers.contains(uuid); }
    public void setLookMenuEnabled(UUID uuid, boolean enabled) {
        boolean changed = enabled ? lookMenuDisabledPlayers.remove(uuid) : lookMenuDisabledPlayers.add(uuid);
        if (changed) setDirty();
    }

    public BoardPreference boardPreference(UUID uuid) { return boardPreferences.get(uuid); }

    public void setBoardPreference(UUID uuid, RankBoardMod.Period period, RankBoardMod.Metric metric,
                                   boolean enabled, boolean carousel) {
        BoardPreference replacement = new BoardPreference(period, metric, enabled, carousel, false);
        if (!replacement.equals(boardPreferences.put(uuid, replacement))) setDirty();
    }

    public void setOverviewPreference(UUID uuid, RankBoardMod.Period period, boolean enabled) {
        BoardPreference replacement = new BoardPreference(period, RankBoardMod.Metric.PLAY_TIME, enabled, false, enabled);
        if (!replacement.equals(boardPreferences.put(uuid, replacement))) setDirty();
    }

    public void disableBoard(UUID uuid) {
        BoardPreference current = boardPreferences.get(uuid);
        if (current != null && current.enabled()) {
            boardPreferences.put(uuid, new BoardPreference(current.period(), current.metric(), false, false, false));
            setDirty();
        }
    }

    public BoardPreference globalBoardPreference() { return globalBoardPreference; }

    public void setGlobalBoardPreference(RankBoardMod.Period period, RankBoardMod.Metric metric) {
        BoardPreference replacement = new BoardPreference(period, metric, true, false, false);
        if (!replacement.equals(globalBoardPreference)) {
            globalBoardPreference = replacement;
            setDirty();
        }
    }

    public void clearGlobalBoardPreference() {
        if (globalBoardPreference != null) {
            globalBoardPreference = null;
            setDirty();
        }
    }

    public RangeData range(MinecraftServer server, LocalDate from, LocalDate to, RankBoardMod.Metric metric) {
        if (!StatReader.isReady()) {
            throw new IllegalStateException("历史统计缓存仍在加载（" + StatReader.progress() + "），请稍后再查询日期范围。");
        }
        if (to.isBefore(from)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> start = dailySnapshots.ceilingEntry(from);
        if (start == null || start.getKey().isAfter(to)) {
            throw new IllegalArgumentException("该日期早于可用快照；最早可查询日期为 " + earliestSnapshotDate());
        }
        Map<UUID, Long> endValues = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate endBoundary;
        if (!to.isBefore(today)) {
            endBoundary = today;
            StatReader.readAll(server, metric).forEach(snapshot -> endValues.put(snapshot.uuid(), snapshot.value(metric)));
        } else {
            Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> end = dailySnapshots.ceilingEntry(to.plusDays(1));
            if (end == null) throw new IllegalArgumentException("结束日期尚无完整快照");
            endBoundary = end.getKey();
            end.getValue().forEach((uuid, values) -> endValues.put(uuid, values.getOrDefault(metric, 0L)));
        }
        Map<UUID, Long> result = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : endValues.entrySet()) {
            long base = start.getValue().getOrDefault(entry.getKey(), Map.of()).getOrDefault(metric, 0L);
            result.put(entry.getKey(), Math.max(0, entry.getValue() - base));
        }
        return new RangeData(start.getKey(), endBoundary, result);
    }

    public String earliestSnapshotDate() {
        return dailySnapshots.isEmpty() ? "暂无" : dailySnapshots.firstKey().toString();
    }

    public record RangeData(LocalDate actualStart, LocalDate actualEnd, Map<UUID, Long> values) { }
    public record BoardPreference(RankBoardMod.Period period, RankBoardMod.Metric metric,
                                  boolean enabled, boolean carousel, boolean overview) { }

    private static ListTag writePlayers(Map<UUID, Map<RankBoardMod.Metric, Long>> players) {
        ListTag list = new ListTag();
        players.forEach((uuid, values) -> {
            CompoundTag entry = new CompoundTag();
            NbtCompat.putUuid(entry, "uuid", uuid);
            values.forEach((metric, value) -> entry.putLong(metric.command, value));
            list.add(entry);
        });
        return list;
    }

    private static Map<UUID, Map<RankBoardMod.Metric, Long>> readPlayers(CompoundTag owner) {
        Map<UUID, Map<RankBoardMod.Metric, Long>> players = new HashMap<>();
        for (Tag element : NbtCompat.getList(owner, "players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            Map<RankBoardMod.Metric, Long> values = new EnumMap<>(RankBoardMod.Metric.class);
            for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                if (entry.contains(metric.command)) values.put(metric, NbtCompat.getLong(entry, metric.command));
            }
            players.put(NbtCompat.getUuid(entry, "uuid"), values);
        }
        return players;
    }
    private static final class PeriodData {
        final RankBoardMod.Period period; final String key;
        final Map<UUID, Map<RankBoardMod.Metric, Long>> players = new HashMap<>();
        PeriodData(RankBoardMod.Period period, String key) { this.period = period; this.key = key; }
        void capture(StatSnapshot snapshot) { players.put(snapshot.uuid(), snapshot.values()); }
        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag(); nbt.putString("period", period.name()); nbt.putString("key", key);
            nbt.put("players", writePlayers(players)); return nbt;
        }
        static PeriodData fromNbt(CompoundTag nbt) {
            PeriodData data = new PeriodData(RankBoardMod.Period.valueOf(NbtCompat.getString(nbt, "period")), NbtCompat.getString(nbt, "key"));
            data.players.putAll(readPlayers(nbt));
            return data;
        }
    }
}
