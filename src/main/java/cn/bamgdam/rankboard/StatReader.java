package cn.bamgdam.rankboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/** Rate-limited asynchronous cache for vanilla JSON statistic files. */
final class StatReader {
    private static final Map<UUID, StatSnapshot> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SOURCE_MODIFIED = new ConcurrentHashMap<>();
    private static final Set<String> FOOD_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Set<String> BLOCK_ITEMS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger PROCESSED = new AtomicInteger();
    private static final AtomicInteger TOTAL = new AtomicInteger();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final int PERSISTENT_CACHE_SCHEMA = 1;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RankBoard-HistoryLoader");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Future<?> warmupTask;
    private static volatile boolean ready;
    private static volatile boolean persistentCacheLoaded;

    private StatReader() { }

    static void startWarmup(MinecraftServer server) {
        long generation = GENERATION.incrementAndGet();
        Future<?> oldTask = warmupTask;
        if (oldTask != null) oldTask.cancel(true);
        ready = false;
        PROCESSED.set(0);
        TOTAL.set(0);
        CACHE.clear();
        SOURCE_MODIFIED.clear();
        prepareItemSets();
        persistentCacheLoaded = loadPersistentCache(server);
        ready = persistentCacheLoaded;
        if (persistentCacheLoaded) {
            server.execute(() -> {
                LeaderboardState.get(server).rollPeriods(server);
                BoardService.refreshAll(server);
            });
        }
        int filesPerSecond = loadRate(server.getRunDirectory().resolve("rankboard.properties"));
        warmupTask = LOADER.submit(() -> warmup(server, generation, filesPerSecond));
    }

    static void stopWarmup() {
        GENERATION.incrementAndGet();
        Future<?> task = warmupTask;
        if (task != null) task.cancel(true);
        warmupTask = null;
        ready = false;
        persistentCacheLoaded = false;
    }

    static void reloadPlayer(MinecraftServer server, UUID uuid) {
        long generation = GENERATION.get();
        LOADER.submit(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
            if (generation != GENERATION.get()) return;
            Map<UUID, String> names = readKnownNames(server);
            Path path = server.getSavePath(WorldSavePath.STATS).resolve(uuid + ".json");
            readSnapshot(path, names).ifPresent(snapshot -> {
                CACHE.put(uuid, snapshot);
                SOURCE_MODIFIED.put(uuid, modifiedTime(path));
                savePersistentCache(server);
            });
        });
    }

    static void updateName(UUID uuid, String name) {
        CACHE.computeIfPresent(uuid, (ignored, snapshot) -> new StatSnapshot(uuid, name, snapshot.values()));
    }

    static boolean isReady() { return ready; }
    static boolean isPersistentCacheLoaded() { return persistentCacheLoaded; }
    static boolean isChecking() { return warmupTask != null && !warmupTask.isDone(); }
    static int processed() { return PROCESSED.get(); }
    static int totalFiles() { return TOTAL.get(); }
    static String progress() { return processed() + "/" + totalFiles(); }

    static List<StatSnapshot> readAll(MinecraftServer server) { return readAll(server, null); }

    static List<StatSnapshot> readAll(MinecraftServer server, RankBoardMod.Metric onlyMetric) {
        Map<UUID, StatSnapshot> snapshots = new HashMap<>(CACHE);
        // Live handlers are already in memory and are always newer than files on disk.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            snapshots.put(player.getUuid(), fromPlayer(player, onlyMetric));
        }
        return new ArrayList<>(snapshots.values());
    }

    private static void warmup(MinecraftServer server, long generation, int filesPerSecond) {
        try {
            Map<UUID, String> names = readKnownNames(server);
            Set<UUID> whitelist = readWhitelistNames(server).keySet();
            Path directory = server.getSavePath(WorldSavePath.STATS);
            List<Path> files;
            try (Stream<Path> stream = Files.isDirectory(directory) ? Files.list(directory) : Stream.empty()) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> uuidFromPathOrNull(path) != null).toList();
            }
            files = new ArrayList<>(files);
            files.sort(Comparator.comparing((Path path) -> !whitelist.contains(uuidFromPath(path))).thenComparing(Path::toString));
            TOTAL.set(files.size());
            long delayMillis = Math.max(1, 1000L / filesPerSecond);
            RankBoardMod.LOGGER.info("Checking history cache: {} files at {} files/second (persistent cache: {})",
                    files.size(), filesPerSecond, persistentCacheLoaded ? "loaded" : "not found");
            Set<UUID> present = new HashSet<>();
            for (Path file : files) {
                if (generation != GENERATION.get() || Thread.currentThread().isInterrupted()) return;
                UUID uuid = uuidFromPath(file);
                present.add(uuid);
                long modified = modifiedTime(file);
                if (!CACHE.containsKey(uuid) || SOURCE_MODIFIED.getOrDefault(uuid, -1L) != modified) {
                    readSnapshot(file, names).ifPresent(snapshot -> {
                        CACHE.put(snapshot.uuid(), snapshot);
                        SOURCE_MODIFIED.put(snapshot.uuid(), modified);
                    });
                }
                PROCESSED.incrementAndGet();
                if (delayMillis > 0) Thread.sleep(delayMillis);
            }
            if (generation != GENERATION.get()) return;
            CACHE.keySet().removeIf(uuid -> !present.contains(uuid));
            SOURCE_MODIFIED.keySet().removeIf(uuid -> !present.contains(uuid));
            ready = true;
            persistentCacheLoaded = true;
            savePersistentCache(server);
            RankBoardMod.LOGGER.info("History cache ready: {} player files loaded", CACHE.size());
            server.execute(() -> {
                LeaderboardState.get(server).rollPeriods(server);
                BoardService.refreshAll(server);
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            RankBoardMod.LOGGER.error("History cache warmup failed at {}", progress(), exception);
        }
    }

    private static boolean loadPersistentCache(MinecraftServer server) {
        Path path = persistentCachePath(server);
        if (!Files.isRegularFile(path)) return false;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("schema") || root.get("schema").getAsInt() != PERSISTENT_CACHE_SCHEMA
                    || !root.has("complete") || !root.get("complete").getAsBoolean()) return false;
            Map<UUID, String> knownNames = readKnownNames(server);
            for (JsonElement element : root.getAsJsonArray("players")) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
                String name = knownNames.getOrDefault(uuid, entry.get("name").getAsString());
                Map<RankBoardMod.Metric, Long> values = new EnumMap<>(RankBoardMod.Metric.class);
                JsonObject storedValues = entry.getAsJsonObject("values");
                for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                    JsonElement value = storedValues.get(metric.command);
                    values.put(metric, value == null ? 0L : value.getAsLong());
                }
                CACHE.put(uuid, new StatSnapshot(uuid, name, values));
                SOURCE_MODIFIED.put(uuid, entry.get("modified").getAsLong());
            }
            if (CACHE.isEmpty() && root.getAsJsonArray("players").size() > 0) return false;
            RankBoardMod.LOGGER.info("Loaded persistent history cache: {} player files", CACHE.size());
            return true;
        } catch (Exception exception) {
            CACHE.clear();
            SOURCE_MODIFIED.clear();
            RankBoardMod.LOGGER.warn("Could not load persistent history cache {}; rebuilding it", path, exception);
            return false;
        }
    }

    private static void savePersistentCache(MinecraftServer server) {
        Path path = persistentCachePath(server);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("schema", PERSISTENT_CACHE_SCHEMA);
            root.addProperty("complete", true);
            com.google.gson.JsonArray players = new com.google.gson.JsonArray();
            CACHE.values().stream().sorted(Comparator.comparing(snapshot -> snapshot.uuid().toString())).forEach(snapshot -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", snapshot.uuid().toString());
                entry.addProperty("name", snapshot.name());
                entry.addProperty("modified", SOURCE_MODIFIED.getOrDefault(snapshot.uuid(), -1L));
                JsonObject values = new JsonObject();
                for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                    values.addProperty(metric.command, snapshot.value(metric));
                }
                entry.add("values", values);
                players.add(entry);
            });
            root.add("players", players);
            try (var writer = Files.newBufferedWriter(temporary)) { writer.write(root.toString()); }
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            RankBoardMod.LOGGER.warn("Could not save persistent history cache {}", path, exception);
        }
    }

    private static Path persistentCachePath(MinecraftServer server) {
        return server.getRunDirectory().resolve("rankboard-history-cache.json");
    }

    private static long modifiedTime(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException exception) { return -1L; }
    }

    private static java.util.Optional<StatSnapshot> readSnapshot(Path path, Map<UUID, String> names) {
        if (!Files.isRegularFile(path)) return java.util.Optional.empty();
        try {
            UUID uuid = uuidFromPath(path);
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject stats = root.has("stats") ? root.getAsJsonObject("stats") : new JsonObject();
                String name = names.getOrDefault(uuid, "unknown_" + uuid.toString().substring(0, 8));
                return java.util.Optional.of(new StatSnapshot(uuid, name, readValues(stats)));
            }
        } catch (IllegalArgumentException | IOException | IllegalStateException exception) {
            RankBoardMod.LOGGER.debug("Skipping unreadable statistic file {}", path, exception);
            return java.util.Optional.empty();
        }
    }

    static Map<UUID, String> readWhitelistNames(MinecraftServer server) {
        return readProfileNames(server.getRunDirectory().resolve("whitelist.json"));
    }

    private static Map<UUID, String> readKnownNames(MinecraftServer server) {
        Map<UUID, String> names = readProfileNames(server.getRunDirectory().resolve("usercache.json"));
        names.putAll(readWhitelistNames(server));
        return names;
    }

    private static Map<UUID, String> readProfileNames(Path path) {
        Map<UUID, String> names = new HashMap<>();
        if (!Files.isRegularFile(path)) return names;
        try (Reader reader = Files.newBufferedReader(path)) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                names.put(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString());
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException | NullPointerException ignored) { }
        return names;
    }

    private static UUID uuidFromPath(Path path) {
        String fileName = path.getFileName().toString();
        return UUID.fromString(fileName.substring(0, fileName.length() - 5));
    }

    private static UUID uuidFromPathOrNull(Path path) {
        try { return uuidFromPath(path); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static int loadRate(Path path) {
        Properties properties = new Properties();
        int defaultRate = 50;
        try {
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path)) { properties.load(reader); }
            } else {
                properties.setProperty("history-files-per-second", Integer.toString(defaultRate));
                try (var writer = Files.newBufferedWriter(path)) {
                    properties.store(writer, "RankBoard settings");
                }
            }
            return Math.max(1, Math.min(1000,
                    Integer.parseInt(properties.getProperty("history-files-per-second", Integer.toString(defaultRate)))));
        } catch (Exception exception) {
            RankBoardMod.LOGGER.warn("Could not read {}, using {} files/second", path, defaultRate, exception);
            return defaultRate;
        }
    }

    private static void prepareItemSets() {
        FOOD_ITEMS.clear();
        BLOCK_ITEMS.clear();
        for (Item item : Registries.ITEM) {
            String id = Registries.ITEM.getId(item).toString();
            if (item.getComponents().get(DataComponentTypes.FOOD) != null) FOOD_ITEMS.add(id);
            if (item instanceof BlockItem) BLOCK_ITEMS.add(id);
        }
    }

    static StatSnapshot fromPlayer(ServerPlayerEntity player) { return fromPlayer(player, null); }

    private static StatSnapshot fromPlayer(ServerPlayerEntity player, RankBoardMod.Metric onlyMetric) {
        Map<RankBoardMod.Metric, Long> values = new EnumMap<>(RankBoardMod.Metric.class);
        if (onlyMetric != null) {
            values.put(onlyMetric, onlyMetric.read(player));
        } else {
            for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) {
                try { values.put(metric, metric.read(player)); }
                catch (RuntimeException exception) {
                    RankBoardMod.LOGGER.warn("Failed to read live {} statistic for {}", metric.command,
                            player.getName().getString(), exception);
                    values.put(metric, 0L);
                }
            }
        }
        return new StatSnapshot(player.getUuid(), player.getName().getString(), values);
    }

    private static Map<RankBoardMod.Metric, Long> readValues(JsonObject stats) {
        Map<RankBoardMod.Metric, Long> values = new EnumMap<>(RankBoardMod.Metric.class);
        for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) values.put(metric, readValue(stats, metric));
        return values;
    }

    private static long readValue(JsonObject stats, RankBoardMod.Metric metric) {
        return switch (metric) {
            case FOOD -> sumMatching(stats, "minecraft:used", FOOD_ITEMS);
            case PLACED -> sumMatching(stats, "minecraft:used", BLOCK_ITEMS);
            case MINED -> sum(stats, "minecraft:mined");
            case JUMPS -> stat(stats, "minecraft:custom", "minecraft:jump");
            case KILLS -> stat(stats, "minecraft:custom", "minecraft:mob_kills") + stat(stats, "minecraft:custom", "minecraft:player_kills");
            case DEATHS -> stat(stats, "minecraft:custom", "minecraft:deaths");
            case TRADES -> stat(stats, "minecraft:custom", "minecraft:traded_with_villager");
            case PLAY_TIME -> stat(stats, "minecraft:custom", "minecraft:play_time");
            case ELYTRA_DISTANCE -> stat(stats, "minecraft:custom", "minecraft:aviate_one_cm");
            case FISHING -> stat(stats, "minecraft:custom", "minecraft:fish_caught");
            case DAMAGE_TAKEN -> stat(stats, "minecraft:custom", "minecraft:damage_taken");
        };
    }

    private static long sum(JsonObject stats, String group) {
        long result = 0;
        for (Map.Entry<String, JsonElement> entry : object(stats, group).entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) result += value.getAsLong();
        }
        return result;
    }

    private static long sumMatching(JsonObject stats, String group, Set<String> acceptedIds) {
        long result = 0;
        for (Map.Entry<String, JsonElement> entry : object(stats, group).entrySet()) {
            if (acceptedIds.contains(entry.getKey())) result += entry.getValue().getAsLong();
        }
        return result;
    }

    private static long stat(JsonObject stats, String group, String key) {
        JsonElement value = object(stats, group).get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : 0;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
}

record StatSnapshot(UUID uuid, String name, Map<RankBoardMod.Metric, Long> values) {
    long value(RankBoardMod.Metric metric) { return values.getOrDefault(metric, 0L); }
    static StatSnapshot fromPlayer(ServerPlayerEntity player) { return StatReader.fromPlayer(player); }
}
