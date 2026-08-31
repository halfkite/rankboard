package cn.bamgdam.rankboard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
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
public final class LeaderboardState extends PersistentState {
    private static final String LEGACY_STATE_ID = "rankboard_leaderboard";
    private static final String STATE_ID = "rankboard/" + LEGACY_STATE_ID;
    private static final String HISTORY_SCHEMA = "4";
    private static final LocalTime COMPLETE_BOUNDARY_LIMIT = LocalTime.of(0, 5);
    private final Map<RankBoardMod.Period, PeriodData> periods = new EnumMap<>(RankBoardMod.Period.class);
    private boolean whitelistOnly;
    // New worlds choose a whitelist mode from the RankBoard menu. Worlds saved
    // before this flag existed are treated as already configured on upgrade.
    private boolean whitelistModeConfigured;
    private boolean botFilterEnabled = true;
    private boolean customPlayerFilterEnabled = true;
    private boolean onlineOnly;
    private final Set<RankBoardMod.Metric> disabledDisplayMetrics = new HashSet<>();
    private final Set<UUID> nameColorDisabledPlayers = new HashSet<>();
    private final Set<UUID> lookMenuDisabledPlayers = new HashSet<>();
    private final Set<UUID> joinMenuDisabledPlayers = new HashSet<>();
    private final Map<UUID, String> playerLanguages = new HashMap<>();
    private final Map<UUID, BoardPreference> boardPreferences = new HashMap<>();
    private BoardPreference globalBoardPreference;
    private final NavigableMap<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> dailySnapshots = new TreeMap<>();
    private final Set<LocalDate> partialSnapshotDates = new HashSet<>();
    LeaderboardState() { }

    public static LeaderboardState get(MinecraftServer server) {
        prepareStorage(server);
        return PersistentStateCompat.get(server, STATE_ID);
    }

    private static synchronized void prepareStorage(MinecraftServer server) {
        Path dataDirectory = server.getSavePath(WorldSavePath.ROOT).resolve("data");
        Path rankBoardDirectory = dataDirectory.resolve("rankboard");
        Path legacy = dataDirectory.resolve(LEGACY_STATE_ID + ".dat");
        Path target = rankBoardDirectory.resolve(LEGACY_STATE_ID + ".dat");
        try {
            Files.createDirectories(rankBoardDirectory);
            if (Files.isRegularFile(target) || !Files.isRegularFile(legacy)) return;
            Path temporary = target.resolveSibling(target.getFileName() + ".migrating");
            Files.copy(legacy, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.delete(legacy);
            RankBoardMod.LOGGER.info("Migrated RankBoard world data from {} to {}", legacy, target);
        } catch (IOException exception) {
            RankBoardMod.LOGGER.warn("Could not migrate RankBoard world data from {} to {}; the legacy file was kept",
                    legacy, target, exception);
        }
    }
    static LeaderboardState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        LeaderboardState state = new LeaderboardState();
        String historySchema = NbtCompat.getString(nbt, "historySchema");
        boolean legacyHistory = historySchema.isEmpty();
        if (nbt.contains("whitelistOnly")) state.whitelistOnly = NbtCompat.getBoolean(nbt, "whitelistOnly");
        state.whitelistModeConfigured = nbt.contains("whitelistModeConfigured")
                ? NbtCompat.getBoolean(nbt, "whitelistModeConfigured") : true;
        if (nbt.contains("botFilterEnabled")) state.botFilterEnabled = NbtCompat.getBoolean(nbt, "botFilterEnabled");
        if (nbt.contains("customPlayerFilterEnabled")) state.customPlayerFilterEnabled = NbtCompat.getBoolean(nbt, "customPlayerFilterEnabled");
        if (nbt.contains("onlineOnly")) state.onlineOnly = NbtCompat.getBoolean(nbt, "onlineOnly");
        for (NbtElement element : NbtCompat.getList(nbt, "disabledDisplayMetrics", NbtElement.STRING_TYPE)) {
            try { state.disabledDisplayMetrics.add(RankBoardMod.Metric.valueOf(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (NbtElement element : NbtCompat.getList(nbt, "nameColorDisabledPlayers", NbtElement.STRING_TYPE)) {
            try { state.nameColorDisabledPlayers.add(UUID.fromString(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (NbtElement element : NbtCompat.getList(nbt, "lookMenuDisabledPlayers", NbtElement.STRING_TYPE)) {
            try { state.lookMenuDisabledPlayers.add(UUID.fromString(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (NbtElement element : NbtCompat.getList(nbt, "joinMenuDisabledPlayers", NbtElement.STRING_TYPE)) {
            try { state.joinMenuDisabledPlayers.add(UUID.fromString(NbtCompat.asString(element))); }
            catch (IllegalArgumentException ignored) { }
        }
        for (NbtElement element : NbtCompat.getList(nbt, "playerLanguages", NbtElement.COMPOUND_TYPE)) {
            try {
                NbtCompound entry = (NbtCompound) element;
                String language = NbtCompat.getString(entry, "language");
                if (language.matches("[a-z0-9_-]{2,32}")) {
                    state.playerLanguages.put(NbtCompat.getUuid(entry, "uuid"), language);
                }
            } catch (RuntimeException ignored) { }
        }
        for (NbtElement element : NbtCompat.getList(nbt, "periods", NbtElement.COMPOUND_TYPE)) {
            PeriodData data = PeriodData.fromNbt((NbtCompound) element, legacyHistory);
            state.periods.put(data.period, data);
        }
        for (NbtElement element : NbtCompat.getList(nbt, "dailySnapshots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound snapshot = (NbtCompound) element;
            LocalDate date = LocalDate.parse(NbtCompat.getString(snapshot, "date"));
            state.dailySnapshots.put(date, readPlayers(snapshot));
            if (legacyHistory) state.partialSnapshotDates.add(date);
        }
        for (NbtElement element : NbtCompat.getList(nbt, "partialSnapshotDates", NbtElement.STRING_TYPE)) {
            try { state.partialSnapshotDates.add(LocalDate.parse(NbtCompat.asString(element))); }
            catch (RuntimeException ignored) { }
        }
        for (NbtElement element : NbtCompat.getList(nbt, "boardPreferences", NbtElement.COMPOUND_TYPE)) {
            try {
                NbtCompound entry = (NbtCompound) element;
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
                NbtCompound entry = NbtCompat.getCompound(nbt, "globalBoardPreference");
                RankBoardMod.Period period = RankBoardMod.Period.valueOf(NbtCompat.getString(entry, "period"));
                RankBoardMod.Metric metric = RankBoardMod.Metric.valueOf(NbtCompat.getString(entry, "metric"));
                state.globalBoardPreference = new BoardPreference(period, metric, true, false, false);
            } catch (IllegalArgumentException ignored) { }
        }
        return state;
    }
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putString("historySchema", HISTORY_SCHEMA);
        NbtList list = new NbtList();
        periods.values().forEach(data -> list.add(data.toNbt()));
        nbt.put("periods", list);
        nbt.putBoolean("whitelistOnly", whitelistOnly);
        nbt.putBoolean("whitelistModeConfigured", whitelistModeConfigured);
        nbt.putBoolean("botFilterEnabled", botFilterEnabled);
        nbt.putBoolean("customPlayerFilterEnabled", customPlayerFilterEnabled);
        nbt.putBoolean("onlineOnly", onlineOnly);
        NbtList disabledMetrics = new NbtList();
        disabledDisplayMetrics.forEach(metric -> disabledMetrics.add(NbtString.of(metric.name())));
        nbt.put("disabledDisplayMetrics", disabledMetrics);
        NbtList disabledColors = new NbtList();
        nameColorDisabledPlayers.forEach(uuid -> disabledColors.add(NbtString.of(uuid.toString())));
        nbt.put("nameColorDisabledPlayers", disabledColors);
        NbtList disabledLookMenus = new NbtList();
        lookMenuDisabledPlayers.forEach(uuid -> disabledLookMenus.add(NbtString.of(uuid.toString())));
        nbt.put("lookMenuDisabledPlayers", disabledLookMenus);
        NbtList disabledJoinMenus = new NbtList();
        joinMenuDisabledPlayers.forEach(uuid -> disabledJoinMenus.add(NbtString.of(uuid.toString())));
        nbt.put("joinMenuDisabledPlayers", disabledJoinMenus);
        NbtList languages = new NbtList();
        playerLanguages.forEach((uuid, language) -> {
            NbtCompound entry = new NbtCompound();
            NbtCompat.putUuid(entry, "uuid", uuid);
            entry.putString("language", language);
            languages.add(entry);
        });
        nbt.put("playerLanguages", languages);
        NbtList snapshots = new NbtList();
        dailySnapshots.forEach((date, players) -> {
            NbtCompound snapshot = new NbtCompound();
            snapshot.putString("date", date.toString());
            snapshot.put("players", writePlayers(players));
            snapshots.add(snapshot);
        });
        nbt.put("dailySnapshots", snapshots);
        NbtList partialDates = new NbtList();
        partialSnapshotDates.forEach(date -> partialDates.add(NbtString.of(date.toString())));
        nbt.put("partialSnapshotDates", partialDates);
        NbtList preferences = new NbtList();
        boardPreferences.forEach((uuid, preference) -> {
            NbtCompound entry = new NbtCompound();
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
            NbtCompound entry = new NbtCompound();
            entry.putString("period", globalBoardPreference.period().name());
            entry.putString("metric", globalBoardPreference.metric().name());
            nbt.put("globalBoardPreference", entry);
        }
        return nbt;
    }
    public void rollPeriods(MinecraftServer server) {
        if (!StatReader.isReady()) return;
        LocalDate now = LocalDate.now();
        boolean nearMidnight = !LocalTime.now().isAfter(COMPLETE_BOUNDARY_LIMIT);
        boolean changed = false;
        List<StatSnapshot> snapshots = StatReader.readAll(server);
        for (RankBoardMod.Period period : RankBoardMod.Period.values()) {
            if (period == RankBoardMod.Period.ALL) continue;
            PeriodData old = periods.get(period);
            if (old == null || !old.key.equals(period.key(now))) {
                PeriodData replacement = new PeriodData(period, period.key(now),
                        completePeriodBoundary(period, now, nearMidnight));
                snapshots.forEach(replacement::capture);
                periods.put(period, replacement);
                changed = true;
            } else if (old.initializeMissingMetrics(snapshots)) {
                changed = true;
            }
        }
        if (!dailySnapshots.containsKey(now)) {
            Map<UUID, Map<RankBoardMod.Metric, Long>> values = new HashMap<>();
            snapshots.forEach(snapshot -> values.put(snapshot.uuid(), new EnumMap<>(snapshot.values())));
            dailySnapshots.put(now, values);
            if (nearMidnight) partialSnapshotDates.remove(now); else partialSnapshotDates.add(now);
            changed = true;
        } else if (initializeMissingSnapshotMetrics(dailySnapshots.get(now), snapshots)) {
            partialSnapshotDates.add(now);
            changed = true;
        }
        if (changed) markDirty();
    }
    public void ensurePlayer(ServerPlayerEntity player) {
        rollPeriods(PlayerCompat.server(player));
        StatSnapshot snapshot = StatSnapshot.fromPlayer(player);
        boolean changed = false;
        for (PeriodData data : periods.values()) if (data.players.putIfAbsent(snapshot.uuid(), snapshot.values()) == null) changed = true;
        if (changed) markDirty();
    }
    public boolean needsLanguageChoice(UUID uuid) { return !playerLanguages.containsKey(uuid); }
    public String language(UUID uuid) {
        return playerLanguages.getOrDefault(uuid, RankBoardConfig.get().defaultLanguage);
    }
    public void setLanguage(UUID uuid, String language) {
        if (!language.matches("[a-z0-9_-]{2,32}")) throw new IllegalArgumentException("unsupported language");
        if (!language.equals(playerLanguages.put(uuid, language))) markDirty();
    }
    /** Applies a server-wide language choice to all known and currently online players. */
    public int setLanguageForAll(MinecraftServer server, String language) {
        Set<UUID> players = new HashSet<>(playerLanguages.keySet());
        server.getPlayerManager().getPlayerList().forEach(player -> players.add(player.getUuid()));
        boolean changed = false;
        for (UUID uuid : players) {
            if (!language.equals(playerLanguages.put(uuid, language))) changed = true;
        }
        if (changed) markDirty();
        return players.size();
    }
    public long getBaseline(RankBoardMod.Period period, UUID uuid, RankBoardMod.Metric metric) {
        PeriodData data = periods.get(period);
        return data == null ? 0 : data.players.getOrDefault(uuid, Map.of()).getOrDefault(metric, 0L);
    }
    public boolean hasBaseline(RankBoardMod.Period period, UUID uuid, RankBoardMod.Metric metric) {
        if (period == RankBoardMod.Period.ALL) return true;
        PeriodData data = periods.get(period);
        Map<RankBoardMod.Metric, Long> values = data == null ? null : data.players.get(uuid);
        return values != null && values.containsKey(metric);
    }
    public boolean isPeriodComplete(RankBoardMod.Period period) {
        if (period == RankBoardMod.Period.ALL) return true;
        PeriodData data = periods.get(period);
        return data != null && data.complete && data.partialMetrics.isEmpty();
    }
    public boolean isPeriodComplete(RankBoardMod.Period period, RankBoardMod.Metric metric) {
        if (period == RankBoardMod.Period.ALL) return true;
        PeriodData data = periods.get(period);
        return data != null && data.complete && !data.partialMetrics.contains(metric);
    }
    public boolean isWhitelistOnly() { return whitelistOnly; }
    public void setWhitelistOnly(boolean whitelistOnly) {
        if (this.whitelistOnly != whitelistOnly) {
            this.whitelistOnly = whitelistOnly;
            markDirty();
        }
    }
    public boolean needsWhitelistModeSetup() { return !whitelistModeConfigured; }
    public void setWhitelistModeConfigured() {
        if (!whitelistModeConfigured) {
            whitelistModeConfigured = true;
            markDirty();
        }
    }
    public boolean isBotFilterEnabled() { return botFilterEnabled; }
    public void setBotFilterEnabled(boolean enabled) {
        if (botFilterEnabled != enabled) {
            botFilterEnabled = enabled;
            markDirty();
        }
    }
    public boolean isCustomPlayerFilterEnabled() { return customPlayerFilterEnabled; }
    public void setCustomPlayerFilterEnabled(boolean enabled) {
        if (customPlayerFilterEnabled != enabled) {
            customPlayerFilterEnabled = enabled;
            markDirty();
        }
    }
    public boolean isOnlineOnly() { return onlineOnly; }
    public void setOnlineOnly(boolean enabled) {
        if (onlineOnly != enabled) {
            onlineOnly = enabled;
            markDirty();
        }
    }
    public boolean isMetricDisplayEnabled(RankBoardMod.Metric metric) { return !disabledDisplayMetrics.contains(metric); }
    public void setMetricDisplayEnabled(RankBoardMod.Metric metric, boolean enabled) {
        boolean changed = enabled ? disabledDisplayMetrics.remove(metric) : disabledDisplayMetrics.add(metric);
        if (changed) markDirty();
    }
    public boolean isNameColorEnabled(UUID uuid) { return !nameColorDisabledPlayers.contains(uuid); }
    public void setNameColorEnabled(UUID uuid, boolean enabled) {
        boolean changed = enabled ? nameColorDisabledPlayers.remove(uuid) : nameColorDisabledPlayers.add(uuid);
        if (changed) markDirty();
    }

    public boolean isLookMenuEnabled(UUID uuid) { return !lookMenuDisabledPlayers.contains(uuid); }
    public void setLookMenuEnabled(UUID uuid, boolean enabled) {
        boolean changed = enabled ? lookMenuDisabledPlayers.remove(uuid) : lookMenuDisabledPlayers.add(uuid);
        if (changed) markDirty();
    }

    public boolean isJoinMenuEnabled(UUID uuid) { return !joinMenuDisabledPlayers.contains(uuid); }
    public void setJoinMenuEnabled(UUID uuid, boolean enabled) {
        boolean changed = enabled ? joinMenuDisabledPlayers.remove(uuid) : joinMenuDisabledPlayers.add(uuid);
        if (changed) markDirty();
    }

    public BoardPreference boardPreference(UUID uuid) { return boardPreferences.get(uuid); }

    public void setBoardPreference(UUID uuid, RankBoardMod.Period period, RankBoardMod.Metric metric,
                                   boolean enabled, boolean carousel) {
        BoardPreference replacement = new BoardPreference(period, metric, enabled, carousel, false);
        if (!replacement.equals(boardPreferences.put(uuid, replacement))) markDirty();
    }

    public void setOverviewPreference(UUID uuid, RankBoardMod.Period period, boolean enabled) {
        BoardPreference replacement = new BoardPreference(
                period, RankBoardMod.Metric.PLAY_TIME, enabled, false, enabled);
        if (!replacement.equals(boardPreferences.put(uuid, replacement))) markDirty();
    }

    public void disableBoard(UUID uuid) {
        BoardPreference current = boardPreferences.get(uuid);
        if (current != null && current.enabled()) {
            boardPreferences.put(uuid, new BoardPreference(
                    current.period(), current.metric(), false, current.carousel(), current.overview()));
            markDirty();
        }
    }

    public BoardPreference globalBoardPreference() { return globalBoardPreference; }

    public void setGlobalBoardPreference(RankBoardMod.Period period, RankBoardMod.Metric metric) {
        BoardPreference replacement = new BoardPreference(period, metric, true, false, false);
        if (!replacement.equals(globalBoardPreference)) {
            globalBoardPreference = replacement;
            markDirty();
        }
    }

    public void clearGlobalBoardPreference() {
        if (globalBoardPreference != null) {
            globalBoardPreference = null;
            markDirty();
        }
    }

    public RangeData range(MinecraftServer server, LocalDate from, LocalDate to, RankBoardMod.Metric metric) {
        return range(server, from, to, metric, true);
    }

    public RangeData range(MinecraftServer server, LocalDate from, LocalDate to,
                           RankBoardMod.Metric metric, boolean allowPartial) {
        boolean scanning = !StatReader.isReady();
        if (to.isBefore(from)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        LocalDate today = LocalDate.now();
        if (to.isAfter(today)) throw new IllegalArgumentException("结束日期不能晚于今天：" + today);

        Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> startEntry =
                firstSnapshotWithMetric(from, to, metric);
        if (startEntry == null) {
            throw new IllegalArgumentException("所选范围内没有可用的 " + metric.label()
                    + " 快照；最早快照为 " + earliestSnapshotDate(metric));
        }
        LocalDate actualStart = startEntry.getKey();
        Map<UUID, Map<RankBoardMod.Metric, Long>> start = startEntry.getValue();
        List<String> warnings = new java.util.ArrayList<>();
        if (scanning) warnings.add("权威扫描进行中（" + StatReader.progress() + "），结果可能变化");
        if (!actualStart.equals(from) || partialSnapshotDates.contains(actualStart)) {
            if (!allowPartial) throw new IllegalArgumentException("开始日期缺少完整零点快照：" + from);
            warnings.add("实际从 " + actualStart + " 的部分快照开始");
        }

        Map<UUID, Long> endValues = new HashMap<>();
        if (!to.isBefore(today)) {
            StatReader.readAll(server, metric).forEach(snapshot -> endValues.put(snapshot.uuid(), snapshot.value(metric)));
        } else {
            LocalDate requiredEnd = to.plusDays(1);
            Map<UUID, Map<RankBoardMod.Metric, Long>> end = dailySnapshots.get(requiredEnd);
            if (end == null || !snapshotHasMetric(end, metric)) {
                throw new IllegalArgumentException("结束日期缺少次日边界快照：" + requiredEnd);
            }
            if (partialSnapshotDates.contains(requiredEnd)) {
                if (!allowPartial) throw new IllegalArgumentException("结束边界不是完整零点快照：" + requiredEnd);
                warnings.add("结束边界 " + requiredEnd + " 为部分快照");
            }
            end.forEach((uuid, values) -> {
                Long value = values.get(metric);
                if (value != null) endValues.put(uuid, value);
            });
        }

        Map<UUID, Long> result = new HashMap<>();
        int missingStart = 0;
        int missingEnd = 0;
        int reset = 0;
        for (UUID uuid : start.keySet()) if (!endValues.containsKey(uuid)) missingEnd++;
        for (Map.Entry<UUID, Long> entry : endValues.entrySet()) {
            Map<RankBoardMod.Metric, Long> baseValues = start.get(entry.getKey());
            if (baseValues == null || !baseValues.containsKey(metric)) { missingStart++; continue; }
            long base = baseValues.get(metric);
            if (entry.getValue() < base) { reset++; continue; }
            result.put(entry.getKey(), entry.getValue() - base);
        }
        if (missingStart > 0) warnings.add(missingStart + " 名玩家缺少开始边界，已排除");
        if (missingEnd > 0) warnings.add(missingEnd + " 名玩家缺少结束边界，已排除");
        if (reset > 0) warnings.add(reset + " 名玩家累计统计发生回退，已排除");
        return new RangeData(actualStart, to, result, warnings.isEmpty(), List.copyOf(warnings));
    }

    public String earliestSnapshotDate() {
        return dailySnapshots.isEmpty() ? "暂无历史快照" : dailySnapshots.firstKey().toString();
    }

    public String earliestSnapshotDate(RankBoardMod.Metric metric) {
        for (Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> entry : dailySnapshots.entrySet()) {
            if (snapshotHasMetric(entry.getValue(), metric)) return entry.getKey().toString();
        }
        return "暂无 " + metric.label() + " 快照";
    }

    private Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> firstSnapshotWithMetric(
            LocalDate from, LocalDate to, RankBoardMod.Metric metric) {
        Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> entry = dailySnapshots.ceilingEntry(from);
        while (entry != null && !entry.getKey().isAfter(to)) {
            if (snapshotHasMetric(entry.getValue(), metric)) return entry;
            entry = dailySnapshots.higherEntry(entry.getKey());
        }
        return null;
    }

    private static boolean snapshotHasMetric(Map<UUID, Map<RankBoardMod.Metric, Long>> snapshot,
                                             RankBoardMod.Metric metric) {
        return snapshot.values().stream().anyMatch(values -> values.containsKey(metric));
    }

    public record RangeData(LocalDate actualStart, LocalDate actualEnd, Map<UUID, Long> values,
                            boolean complete, List<String> warnings) { }
    public record BoardPreference(RankBoardMod.Period period, RankBoardMod.Metric metric,
                                  boolean enabled, boolean carousel, boolean overview) { }

    private static NbtList writePlayers(Map<UUID, Map<RankBoardMod.Metric, Long>> players) {
        NbtList list = new NbtList();
        players.forEach((uuid, values) -> {
            NbtCompound entry = new NbtCompound();
            NbtCompat.putUuid(entry, "uuid", uuid);
            values.forEach((metric, value) -> entry.putLong(metric.command, value));
            list.add(entry);
        });
        return list;
    }

    private static Map<UUID, Map<RankBoardMod.Metric, Long>> readPlayers(NbtCompound owner) {
        Map<UUID, Map<RankBoardMod.Metric, Long>> players = new HashMap<>();
        for (NbtElement element : NbtCompat.getList(owner, "players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            Map<RankBoardMod.Metric, Long> values = new EnumMap<>(RankBoardMod.Metric.class);
            for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                if (entry.contains(metric.command)) values.put(metric, NbtCompat.getLong(entry, metric.command));
            }
            players.put(NbtCompat.getUuid(entry, "uuid"), values);
        }
        return players;
    }
    private static final class PeriodData {
        final RankBoardMod.Period period; final String key; final boolean complete;
        final Map<UUID, Map<RankBoardMod.Metric, Long>> players = new HashMap<>();
        final Set<RankBoardMod.Metric> partialMetrics = new HashSet<>();
        PeriodData(RankBoardMod.Period period, String key, boolean complete) {
            this.period = period; this.key = key; this.complete = complete;
        }
        void capture(StatSnapshot snapshot) { players.put(snapshot.uuid(), new EnumMap<>(snapshot.values())); }
        boolean initializeMissingMetrics(List<StatSnapshot> snapshots) {
            boolean changed = false;
            for (StatSnapshot snapshot : snapshots) {
                Map<RankBoardMod.Metric, Long> values = players.get(snapshot.uuid());
                if (values == null) continue;
                for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                    if (!values.containsKey(metric)) {
                        values.put(metric, snapshot.value(metric));
                        partialMetrics.add(metric);
                        changed = true;
                    }
                }
            }
            return changed;
        }
        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound(); nbt.putString("period", period.name()); nbt.putString("key", key);
            nbt.putBoolean("complete", complete); nbt.put("players", writePlayers(players));
            NbtList partial = new NbtList();
            partialMetrics.forEach(metric -> partial.add(NbtString.of(metric.name())));
            nbt.put("partialMetrics", partial);
            return nbt;
        }
        static PeriodData fromNbt(NbtCompound nbt, boolean legacyHistory) {
            PeriodData data = new PeriodData(RankBoardMod.Period.valueOf(NbtCompat.getString(nbt, "period")),
                    NbtCompat.getString(nbt, "key"), !legacyHistory && NbtCompat.getBoolean(nbt, "complete"));
            data.players.putAll(readPlayers(nbt));
            for (NbtElement element : NbtCompat.getList(nbt, "partialMetrics", NbtElement.STRING_TYPE)) {
                try { data.partialMetrics.add(RankBoardMod.Metric.valueOf(NbtCompat.asString(element))); }
                catch (IllegalArgumentException ignored) { }
            }
            return data;
        }
    }

    private static boolean completePeriodBoundary(RankBoardMod.Period period, LocalDate date, boolean nearMidnight) {
        if (!nearMidnight) return false;
        return switch (period) {
            case DAILY -> true;
            case WEEKLY -> date.getDayOfWeek() == java.time.DayOfWeek.MONDAY;
            case MONTHLY -> date.getDayOfMonth() == 1;
            case YEARLY -> date.getDayOfYear() == 1;
            case ALL -> true;
        };
    }

    private static boolean initializeMissingSnapshotMetrics(
            Map<UUID, Map<RankBoardMod.Metric, Long>> values, List<StatSnapshot> snapshots) {
        boolean changed = false;
        for (StatSnapshot snapshot : snapshots) {
            Map<RankBoardMod.Metric, Long> playerValues = values.get(snapshot.uuid());
            if (playerValues == null) continue;
            for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                if (!playerValues.containsKey(metric)) {
                    playerValues.put(metric, snapshot.value(metric));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
