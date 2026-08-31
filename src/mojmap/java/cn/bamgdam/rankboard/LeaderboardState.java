package cn.bamgdam.rankboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
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
public final class LeaderboardState extends SavedData {
    private static final String LEGACY_STATE_ID = "rankboard_leaderboard";
    private static final String STATE_ID = "rankboard/" + LEGACY_STATE_ID;
    private static final String HISTORY_SCHEMA = "4";
    private static final LocalTime COMPLETE_BOUNDARY_LIMIT = LocalTime.of(0, 5);
    private final Map<RankBoardMod.Period, PeriodData> periods = new EnumMap<>(RankBoardMod.Period.class);
    // New worlds start without a vanilla whitelist filter; existing worlds keep
    // the persisted value loaded from NBT.
    private boolean whitelistOnly;
    private boolean whitelistModeConfigured;
    private boolean botFilterEnabled = true;
    private boolean customPlayerFilterEnabled = true;
    private boolean onlineOnly;
    private final Set<RankBoardMod.Metric> disabledDisplayMetrics = new HashSet<>();
    private final Set<UUID> nameColorDisabledPlayers = new HashSet<>();
    private final Set<UUID> lookMenuDisabledPlayers = new HashSet<>();
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
        Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("data");
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

    static LeaderboardState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        LeaderboardState state = new LeaderboardState();
        String historySchema = NbtCompat.getString(nbt, "historySchema");
        boolean legacyHistory = historySchema.isEmpty();
        if (nbt.contains("whitelistOnly")) state.whitelistOnly = NbtCompat.getBoolean(nbt, "whitelistOnly");
        if (nbt.contains("botFilterEnabled")) state.botFilterEnabled = NbtCompat.getBoolean(nbt, "botFilterEnabled");
        if (nbt.contains("customPlayerFilterEnabled")) state.customPlayerFilterEnabled = NbtCompat.getBoolean(nbt, "customPlayerFilterEnabled");
        if (nbt.contains("onlineOnly")) state.onlineOnly = NbtCompat.getBoolean(nbt, "onlineOnly");
        if (nbt.contains("whitelistModeConfigured")) state.whitelistModeConfigured = NbtCompat.getBoolean(nbt, "whitelistModeConfigured");
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
            PeriodData data = PeriodData.fromNbt((CompoundTag) element, legacyHistory);
            state.periods.put(data.period, data);
        }
        for (Tag element : NbtCompat.getList(nbt, "playerLanguages", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            try {
                UUID uuid = NbtCompat.getUuid(entry, "uuid");
                String language = NbtCompat.getString(entry, "language");
                if (language.matches("[a-z0-9_-]{2,32}")) state.playerLanguages.put(uuid, language);
            } catch (RuntimeException ignored) { }
        }
        for (Tag element : NbtCompat.getList(nbt, "dailySnapshots", Tag.TAG_COMPOUND)) {
            CompoundTag snapshot = (CompoundTag) element;
            LocalDate date = LocalDate.parse(NbtCompat.getString(snapshot, "date"));
            state.dailySnapshots.put(date, readPlayers(snapshot));
            if (legacyHistory) state.partialSnapshotDates.add(date);
        }
        for (Tag element : NbtCompat.getList(nbt, "partialSnapshotDates", Tag.TAG_STRING)) {
            try { state.partialSnapshotDates.add(LocalDate.parse(NbtCompat.asString(element))); }
            catch (RuntimeException ignored) { }
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
        nbt.putString("historySchema", HISTORY_SCHEMA);
        ListTag list = new ListTag();
        periods.values().forEach(data -> list.add(data.toNbt()));
        nbt.put("periods", list);
        nbt.putBoolean("whitelistOnly", whitelistOnly);
        nbt.putBoolean("whitelistModeConfigured", whitelistModeConfigured);
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
        ListTag languages = new ListTag();
        playerLanguages.forEach((uuid, language) -> {
            CompoundTag entry = new CompoundTag();
            NbtCompat.putUuid(entry, "uuid", uuid);
            entry.putString("language", language);
            languages.add(entry);
        });
        nbt.put("playerLanguages", languages);
        ListTag snapshots = new ListTag();
        dailySnapshots.forEach((date, players) -> {
            CompoundTag snapshot = new CompoundTag();
            snapshot.putString("date", date.toString());
            snapshot.put("players", writePlayers(players));
            snapshots.add(snapshot);
        });
        nbt.put("dailySnapshots", snapshots);
        ListTag partialDates = new ListTag();
        partialSnapshotDates.forEach(date -> partialDates.add(StringTag.valueOf(date.toString())));
        nbt.put("partialSnapshotDates", partialDates);
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
        LocalTime boundaryTime = LocalTime.now();
        boolean nearMidnight = !boundaryTime.isAfter(COMPLETE_BOUNDARY_LIMIT);
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
            if (LocalTime.now().isAfter(COMPLETE_BOUNDARY_LIMIT)) partialSnapshotDates.add(now);
            else partialSnapshotDates.remove(now);
            changed = true;
        } else {
            Map<UUID, Map<RankBoardMod.Metric, Long>> values = dailySnapshots.get(now);
            boolean activatedMetric = false;
            for (StatSnapshot snapshot : snapshots) {
                Map<RankBoardMod.Metric, Long> playerValues = values.get(snapshot.uuid());
                if (playerValues == null) continue;
                for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                    if (!playerValues.containsKey(metric)) {
                        playerValues.put(metric, snapshot.value(metric));
                        activatedMetric = true;
                        changed = true;
                    }
                }
            }
            if (activatedMetric) partialSnapshotDates.add(now);
        }
        if (changed) setDirty();
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
            boardPreferences.put(uuid, new BoardPreference(
                    current.period(), current.metric(), false, current.carousel(), current.overview()));
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
        return range(server, from, to, metric, true);
    }
    public boolean needsWhitelistModeSetup() { return !whitelistModeConfigured; }
    public void setWhitelistModeConfigured() {
        if (!whitelistModeConfigured) { whitelistModeConfigured = true; setDirty(); }
    }
    public boolean needsLanguageChoice(UUID uuid) { return !playerLanguages.containsKey(uuid); }
    public String language(UUID uuid) {
        return playerLanguages.getOrDefault(uuid, RankBoardConfig.get().defaultLanguage);
    }
    public void setLanguage(UUID uuid, String language) {
        if (!language.matches("[a-z0-9_-]{2,32}")) throw new IllegalArgumentException("unsupported language");
        if (!language.equals(playerLanguages.put(uuid, language))) setDirty();
    }
    /** Applies a server-wide language choice to all known and currently online players. */
    public int setLanguageForAll(MinecraftServer server, String language) {
        Set<UUID> players = new HashSet<>(playerLanguages.keySet());
        server.getPlayerList().getPlayers().forEach(player -> players.add(player.getUUID()));
        boolean changed = false;
        for (UUID uuid : players) {
            if (!language.equals(playerLanguages.put(uuid, language))) changed = true;
        }
        if (changed) setDirty();
        return players.size();
    }

    public RangeData range(MinecraftServer server, LocalDate from, LocalDate to,
                           RankBoardMod.Metric metric, boolean allowPartialStart) {
        boolean scanning = !StatReader.isReady();
        if (to.isBefore(from)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        LocalDate today = LocalDate.now();
        if (to.isAfter(today)) throw new IllegalArgumentException("结束日期不能晚于今天：" + today);

        Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> startEntry;
        if (allowPartialStart) {
            startEntry = firstSnapshotWithMetric(from, to, metric);
            if (startEntry == null) {
                throw new IllegalArgumentException("所选范围内还没有可用的 " + metric.label()
                        + " 快照；最早快照为 " + earliestSnapshotDate(metric));
            }
        } else {
            Map<UUID, Map<RankBoardMod.Metric, Long>> exact = dailySnapshots.get(from);
            if (exact == null) {
                throw new IllegalArgumentException("开始日期没有真实边界快照；最早快照为 " + earliestSnapshotDate(metric));
            }
            if (!snapshotHasMetric(exact, metric)) {
                throw new IllegalArgumentException("开始日期 " + from + " 尚未记录 " + metric.label()
                        + "；最早快照为 " + earliestSnapshotDate(metric));
            }
            if (partialSnapshotDates.contains(from)) {
                throw new IllegalArgumentException("开始日期 " + from + " 不是零点建立的完整快照");
            }
            startEntry = new java.util.AbstractMap.SimpleImmutableEntry<>(from, exact);
        }

        LocalDate actualStart = startEntry.getKey();
        Map<UUID, Map<RankBoardMod.Metric, Long>> start = startEntry.getValue();
        List<String> warnings = new java.util.ArrayList<>();
        if (scanning) warnings.add("权威扫描进行中（" + StatReader.progress() + "），结果可能变化");
        if (!actualStart.equals(from) || partialSnapshotDates.contains(actualStart)) {
            warnings.add("请求周期缺少完整零点起点，实际从 " + actualStart + " 当日首次可信快照开始");
        }

        Map<UUID, Long> endValues = new HashMap<>();
        if (!to.isBefore(today)) {
            StatReader.readAll(server, metric).forEach(snapshot -> endValues.put(snapshot.uuid(), snapshot.value(metric)));
        } else {
            LocalDate requiredEnd = to.plusDays(1);
            Map<UUID, Map<RankBoardMod.Metric, Long>> end = dailySnapshots.get(requiredEnd);
            if (end == null) throw new IllegalArgumentException("结束日期缺少次日零点快照：" + requiredEnd);
            if (!snapshotHasMetric(end, metric)) {
                throw new IllegalArgumentException("结束边界 " + requiredEnd + " 尚未记录 " + metric.label());
            }
            if (partialSnapshotDates.contains(requiredEnd)) throw new IllegalArgumentException("结束边界 " + requiredEnd + " 不是完整零点快照");
            end.forEach((uuid, values) -> {
                Long value = values.get(metric);
                if (value != null) endValues.put(uuid, value);
            });
        }

        Map<UUID, Long> result = new HashMap<>();
        int missing = 0;
        int missingEnd = 0;
        int reset = 0;
        for (UUID uuid : start.keySet()) {
            if (!endValues.containsKey(uuid)) missingEnd++;
        }
        for (Map.Entry<UUID, Long> entry : endValues.entrySet()) {
            Map<RankBoardMod.Metric, Long> baseValues = start.get(entry.getKey());
            if (baseValues == null || !baseValues.containsKey(metric)) { missing++; continue; }
            long base = baseValues.get(metric);
            if (entry.getValue() < base) { reset++; continue; }
            result.put(entry.getKey(), entry.getValue() - base);
        }
        if (missing > 0) warnings.add(missing + " 名玩家缺少开始边界，已排除");
        if (missingEnd > 0) warnings.add(missingEnd + " 名玩家缺少结束边界，已排除");
        if (reset > 0) warnings.add(reset + " 名玩家累计统计发生回退，已排除");
        return new RangeData(actualStart, to, result, warnings.isEmpty(), List.copyOf(warnings));
    }

    public String earliestSnapshotDate() {
        if (dailySnapshots.isEmpty()) return "暂无历史快照";
        LocalDate first = dailySnapshots.firstKey();
        return first + (partialSnapshotDates.contains(first) ? "（部分）" : "");
    }

    public String earliestSnapshotDate(RankBoardMod.Metric metric) {
        for (Map.Entry<LocalDate, Map<UUID, Map<RankBoardMod.Metric, Long>>> entry : dailySnapshots.entrySet()) {
            if (snapshotHasMetric(entry.getValue(), metric)) {
                return entry.getKey() + (partialSnapshotDates.contains(entry.getKey()) ? "（部分）" : "");
            }
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
        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag(); nbt.putString("period", period.name()); nbt.putString("key", key);
            nbt.putBoolean("complete", complete); nbt.put("players", writePlayers(players));
            ListTag partial = new ListTag();
            partialMetrics.forEach(metric -> partial.add(StringTag.valueOf(metric.name())));
            nbt.put("partialMetrics", partial);
            return nbt;
        }
        static PeriodData fromNbt(CompoundTag nbt, boolean legacyHistory) {
            PeriodData data = new PeriodData(RankBoardMod.Period.valueOf(NbtCompat.getString(nbt, "period")),
                    NbtCompat.getString(nbt, "key"), !legacyHistory && NbtCompat.getBoolean(nbt, "complete"));
            data.players.putAll(readPlayers(nbt));
            for (Tag element : NbtCompat.getList(nbt, "partialMetrics", Tag.TAG_STRING)) {
                try { data.partialMetrics.add(RankBoardMod.Metric.valueOf(NbtCompat.asString(element))); }
                catch (IllegalArgumentException ignored) { }
            }
            return data;
        }
    }
}
