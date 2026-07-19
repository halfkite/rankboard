package cn.bamgdam.rankboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Small read-only HTTP dashboard backed by server-thread snapshots. */
final class WebDashboard {
    private static HttpServer http;
    private static MinecraftServer minecraft;
    private static String serverName = "Minecraft Server";
    private static Path websiteIcon;
    private static int dataRequestsPerSecond = 1;
    private static int iconRequestIntervalSeconds = 3;
    private static int rankingRefreshIntervalSeconds = 30;
    private static final Map<String, RequestWindow> REQUEST_WINDOWS = new ConcurrentHashMap<>();
    private static final Map<String, CachedRanking> RANKING_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BurstPenaltyWindow> BURST_PENALTIES = new ConcurrentHashMap<>();
    private static final long DATA_WINDOW_MILLIS = 1000;
    private static final long BURST_WINDOW_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long PENALTY_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long DATA_PENALTY_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final long ICON_PENALTY_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final int DATA_BURST_THRESHOLD = 30;
    private static final int ICON_BURST_THRESHOLD = 6;
    private static final int MAX_TRACKED_WINDOWS = 20_000;
    private static final long CLEANUP_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final AtomicLong LAST_CLEANUP = new AtomicLong();
    private WebDashboard() { }

    static synchronized void start(MinecraftServer server) {
        if (http != null) return;
        minecraft = server;
        try {
            Properties config = RankBoardConfig.loadWeb(server);
            String host = config.getProperty("host", "0.0.0.0");
            int port = Integer.parseInt(config.getProperty("port", "8765"));
            dataRequestsPerSecond = Integer.parseInt(config.getProperty("web-data-requests-per-second", "1"));
            iconRequestIntervalSeconds = Integer.parseInt(config.getProperty("web-icon-request-interval-seconds", "3"));
            rankingRefreshIntervalSeconds = Integer.parseInt(config.getProperty("web-ranking-refresh-interval-seconds", "30"));
            serverName = resolveServerName(server, config.getProperty("server-name", "auto"));
            websiteIcon = resolveIcon(server, config.getProperty("website-icon", "server-icon.png"));
            http = HttpServer.create(new InetSocketAddress(host, port), 0);
            http.createContext("/api/rankings", WebDashboard::rankings);
            http.createContext("/api/site", WebDashboard::site);
            http.createContext("/site-icon", WebDashboard::siteIcon);
            http.createContext("/avatar/", WebDashboard::avatar);
            http.createContext("/", WebDashboard::webAsset);
            http.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "RankBoard-Web");
                thread.setDaemon(true);
                return thread;
            }));
            http.start();
            RankBoardMod.LOGGER.info("RankBoard web dashboard listening on http://{}:{}/", host, port);
        } catch (Exception exception) {
            http = null;
            RankBoardMod.LOGGER.error("Failed to start RankBoard web dashboard", exception);
        }
    }

    static synchronized void stop() {
        if (http != null) http.stop(1);
        http = null;
        minecraft = null;
        websiteIcon = null;
        REQUEST_WINDOWS.clear();
        BURST_PENALTIES.clear();
        RANKING_CACHE.clear();
        LAST_CLEANUP.set(0);
    }

    static synchronized boolean restart(MinecraftServer server) {
        stop();
        start(server);
        return http != null;
    }

    static void invalidateRankings() { RANKING_CACHE.clear(); }

    static int clearRateLimits() {
        int cleared = REQUEST_WINDOWS.size() + BURST_PENALTIES.size();
        REQUEST_WINDOWS.clear();
        BURST_PENALTIES.clear();
        LAST_CLEANUP.set(0);
        return cleared;
    }

    private static Path resolveIcon(MinecraftServer server, String configuredPath) {
        Path iconDirectory = RankBoardConfig.configDirectory(server).toAbsolutePath().normalize();
        Path fallback = iconDirectory.resolve("server-icon.png").normalize();
        String raw = configuredPath == null ? "" : configuredPath.strip();
        try {
            Path requested = Path.of(raw);
            if (requested.isAbsolute()) throw new IllegalArgumentException("absolute path");
            Path candidate = iconDirectory.resolve(requested).normalize();
            if (!candidate.startsWith(iconDirectory)) throw new IllegalArgumentException("path escapes config directory");
            if (Files.isRegularFile(candidate)) {
                Path realDirectory = iconDirectory.toRealPath();
                Path realCandidate = candidate.toRealPath();
                if (realCandidate.startsWith(realDirectory)) return realCandidate;
                throw new IllegalArgumentException("symbolic link escapes config directory");
            }
            RankBoardMod.LOGGER.warn("Website icon {} was not found in {}; falling back to {}", raw, iconDirectory, fallback);
        } catch (IOException | RuntimeException exception) {
            RankBoardMod.LOGGER.warn("Website icon {} is invalid; only files inside {} are allowed", raw, iconDirectory);
        }
        return fallback;
    }

    private static String resolveServerName(MinecraftServer server, String configuredName) {
        String value = configuredName.strip();
        if (!value.equalsIgnoreCase("auto") && !value.isEmpty()) return value;
        Properties serverProperties = new Properties();
        Path path = server.getRunDirectory().resolve("server.properties");
        try (var reader = Files.newBufferedReader(path)) {
            serverProperties.load(reader);
            String motd = serverProperties.getProperty("motd", "Minecraft Server")
                    .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                    .replace("\\n", " ").strip();
            return motd.isEmpty() ? "Minecraft Server" : motd;
        } catch (IOException exception) {
            RankBoardMod.LOGGER.warn("Could not read server name from {}", path, exception);
            return "Minecraft Server";
        }
    }

    private static void rankings(HttpExchange exchange) throws IOException {
        if (!enforceRateLimit(exchange, RequestKind.API_DATA)) return;
        String cacheKey = exchange.getRequestURI().getRawQuery();
        if (cacheKey == null) cacheKey = "";
        CachedRanking cached = RANKING_CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAt < rankingRefreshIntervalSeconds * 1000L) {
            respond(exchange, 200, "application/json; charset=utf-8", cached.body);
            return;
        }
        try {
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            String period = query.getOrDefault("period", "day");
            LocalDate today = LocalDate.now();
            LocalDate from;
            LocalDate to;
            switch (period) {
                case "day" -> { from = today; to = today; }
                case "week" -> { from = today.minusDays(6); to = today; }
                case "month" -> { from = today.minusDays(29); to = today; }
                case "all" -> { from = today; to = today; }
                case "custom" -> {
                    from = LocalDate.parse(query.getOrDefault("from", today.toString()));
                    to = LocalDate.parse(query.getOrDefault("to", from.toString()));
                }
                default -> throw new IllegalArgumentException("未知时间范围：" + period);
            }
            RankBoardMod.Metric metric = metric(query.getOrDefault("metric", "playtime"));
            boolean onlineOnly = Boolean.parseBoolean(query.getOrDefault("online", "false"));
            CompletableFuture<String> result = new CompletableFuture<>();
            MinecraftServer server = minecraft;
            if (server == null) throw new IllegalStateException("服务器尚未启动");
            server.execute(() -> {
                try { result.complete(buildRanking(server, period, from, to, metric, onlineOnly)); }
                catch (Exception exception) { result.completeExceptionally(exception); }
            });
            String body = result.get(15, TimeUnit.SECONDS);
            RANKING_CACHE.put(cacheKey, new CachedRanking(System.currentTimeMillis(), body));
            respond(exchange, 200, "application/json; charset=utf-8", body);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            JsonObject error = new JsonObject();
            error.addProperty("error", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            respond(exchange, 400, "application/json; charset=utf-8", error.toString());
        }
    }

    private static String buildRanking(MinecraftServer server, String period, LocalDate from, LocalDate to,
                                       RankBoardMod.Metric metric, boolean requestOnlineOnly) {
        LeaderboardState state = LeaderboardState.get(server);
        boolean effectiveOnlineOnly = requestOnlineOnly || state.isOnlineOnly();
        if (!period.equals("all") && !StatReader.isReady()) {
            throw new IllegalStateException("历史统计缓存仍在加载（" + StatReader.progress() + "），日期范围榜将在加载完成后可用。");
        }
        List<WebEntry> entries = new ArrayList<>();
        String actualStart;
        String actualEnd;
        if (period.equals("all")) {
            StatReader.readAll(server, metric).forEach(snapshot -> {
                if (isIncluded(server, state, snapshot.uuid(), snapshot.name(), effectiveOnlineOnly)) {
                    entries.add(new WebEntry(snapshot.uuid(), snapshot.name(), snapshot.value(metric)));
                }
            });
            actualStart = "原版统计起始";
            actualEnd = LocalDate.now().toString();
        } else {
            LeaderboardState.RangeData range = state.range(server, from, to, metric);
            Map<UUID, String> names = new HashMap<>();
            StatReader.readAll(server, metric).forEach(snapshot -> names.put(snapshot.uuid(), snapshot.name()));
            range.values().forEach((uuid, value) -> {
                String name = names.getOrDefault(uuid, "unknown_" + uuid.toString().substring(0, 8));
                if (isIncluded(server, state, uuid, name, effectiveOnlineOnly)) entries.add(new WebEntry(uuid, name, value));
            });
            actualStart = range.actualStart().toString();
            actualEnd = range.actualEnd().toString();
        }
        entries.sort(Comparator.comparingLong(WebEntry::value).reversed().thenComparing(WebEntry::name));
        long total = entries.stream().map(entry -> new RankBoardMod.Entry(entry.name, entry.value)).collect(
                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), RankBoardMod::total));

        JsonObject root = new JsonObject();
        root.addProperty("cacheReady", StatReader.isReady());
        root.addProperty("cacheChecking", StatReader.isChecking());
        root.addProperty("cacheProcessed", StatReader.processed());
        root.addProperty("cacheTotal", StatReader.totalFiles());
        root.addProperty("onlineOnly", effectiveOnlineOnly);
        root.addProperty("period", period);
        root.addProperty("from", from.toString()); root.addProperty("to", to.toString());
        root.addProperty("actualStart", actualStart); root.addProperty("actualEnd", actualEnd);
        root.addProperty("earliest", state.earliestSnapshotDate());
        root.addProperty("metric", metric.command); root.addProperty("label", metric.label());
        root.addProperty("total", total); root.addProperty("formattedTotal", formatWeb(metric, total));
        JsonArray players = new JsonArray();
        for (int i = 0; i < entries.size(); i++) {
            WebEntry entry = entries.get(i); JsonObject player = new JsonObject();
            player.addProperty("rank", i + 1); player.addProperty("uuid", entry.uuid.toString());
            player.addProperty("name", entry.name); player.addProperty("value", entry.value);
            player.addProperty("formatted", formatWeb(metric, entry.value));
            player.addProperty("lastOnline", StatReader.lastOnline(server, entry.uuid));
            player.addProperty("online", server.getPlayerManager().getPlayer(entry.uuid) != null);
            players.add(player);
        }
        root.add("players", players);
        return root.toString();
    }

    private static boolean isIncluded(MinecraftServer server, LeaderboardState state, UUID uuid, String name,
                                      boolean onlineOnly) {
        return RankBoardMod.isIncluded(server, state, uuid, name)
                && (!onlineOnly || server.getPlayerManager().getPlayer(uuid) != null);
    }

    private static void site(HttpExchange exchange) throws IOException {
        if (!enforceRateLimit(exchange, RequestKind.API_DATA)) return;
        JsonObject root = new JsonObject();
        root.addProperty("name", serverName);
        root.addProperty("rankingRefreshIntervalSeconds", rankingRefreshIntervalSeconds);
        respond(exchange, 200, "application/json; charset=utf-8", root.toString());
    }

    private static RankBoardMod.Metric metric(String command) {
        for (RankBoardMod.Metric metric : RankBoardMod.Metric.values()) if (metric.command.equals(command)) return metric;
        throw new IllegalArgumentException("未知榜单：" + command);
    }

    private static String formatWeb(RankBoardMod.Metric metric, long value) {
        return switch (metric) {
            case PLAY_TIME -> String.format(java.util.Locale.ROOT, "%,dh %dm", value / 72000, (value / 1200) % 60);
            case ELYTRA_DISTANCE -> String.format(java.util.Locale.ROOT, "%,.1f km", value / 100000.0);
            case DAMAGE_TAKEN -> String.format(java.util.Locale.ROOT, "%,.1f", value / 10.0);
            default -> {
                String exact = String.format(java.util.Locale.ROOT, "%,d", value);
                yield value > 100_000 ? (value / 10_000) + "w · " + exact : exact;
            }
        };
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isBlank()) return values;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static void siteIcon(HttpExchange exchange) throws IOException {
        if (!enforceRateLimit(exchange, RequestKind.ICON)) return;
        Path icon = websiteIcon;
        if (icon == null || !Files.isRegularFile(icon)) { respond(exchange, 404, "text/plain", "Not found"); return; }
        byte[] bytes = Files.readAllBytes(icon);
        String contentType = Files.probeContentType(icon);
        exchange.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static void avatar(HttpExchange exchange) throws IOException {
        if (!enforceRateLimit(exchange, RequestKind.ICON)) return;
        MinecraftServer server = minecraft;
        if (server == null) { respond(exchange, 503, "text/plain; charset=utf-8", "Server unavailable"); return; }
        String value = exchange.getRequestURI().getPath().substring("/avatar/".length());
        try {
            Path path = AvatarCache.path(server, UUID.fromString(value));
            if (!Files.isRegularFile(path)) { respond(exchange, 404, "text/plain; charset=utf-8", "Not found"); return; }
            byte[] bytes = Files.readAllBytes(path);
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Bad UUID");
        }
    }

    private static void webAsset(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        RequestKind requestKind = isImagePath(requestPath) ? RequestKind.ICON : RequestKind.WEB_ASSET;
        if (!enforceRateLimit(exchange, requestKind)) return;
        String assetPath = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
        if (assetPath.contains("..") || assetPath.contains("\\")) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Bad request");
            return;
        }
        try (InputStream input = WebDashboard.class.getResourceAsStream("/rankboard-web/" + assetPath)) {
            if (input == null) {
                respond(exchange, 404, "text/plain; charset=utf-8", "Not found");
                return;
            }
            String contentType = switch (assetPath.substring(assetPath.lastIndexOf('.') + 1).toLowerCase()) {
                case "html" -> "text/html; charset=utf-8";
                case "css" -> "text/css; charset=utf-8";
                case "js" -> "text/javascript; charset=utf-8";
                case "json" -> "application/json; charset=utf-8";
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "svg" -> "image/svg+xml";
                case "ico" -> "image/x-icon";
                case "woff2" -> "font/woff2";
                default -> "application/octet-stream";
            };
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control",
                    assetPath.equals("index.html") ? "no-cache" : "public, max-age=31536000, immutable");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        }
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static boolean enforceRateLimit(HttpExchange exchange, RequestKind requestKind) throws IOException {
        long now = System.currentTimeMillis();
        maybeCleanupBurst(now);
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        BurstPenalty penalty = recordBurst(address, requestKind, now);
        long windowMillis = requestIntervalMillis(requestKind, penalty.active);
        int limit = requestKind == RequestKind.API_DATA && !penalty.active ? dataRequestsPerSecond : 1;
        String resource = normalizedRateResource(exchange.getRequestURI().getPath());
        String key = "burst\n" + address + '\n' + requestKind + '\n' + resource;
        if (REQUEST_WINDOWS.size() >= MAX_TRACKED_WINDOWS) cleanupRequestWindows(now);
        if (REQUEST_WINDOWS.size() >= MAX_TRACKED_WINDOWS && !REQUEST_WINDOWS.containsKey(key)) {
            return rateLimited(exchange, requestKind, limit, Math.max(1, windowMillis / 1000));
        }
        RequestWindow window = REQUEST_WINDOWS.computeIfAbsent(key, ignored -> new RequestWindow());
        RateDecision decision = window.acquire(now, limit, windowMillis);
        exchange.getResponseHeaders().set("X-RateLimit-Limit", Integer.toString(limit));
        exchange.getResponseHeaders().set("X-RateLimit-Remaining", Integer.toString(decision.remaining));
        exchange.getResponseHeaders().set("X-RateLimit-Window", Long.toString(windowMillis / 1000));
        if (requestKind == RequestKind.API_DATA || requestKind == RequestKind.ICON) {
            exchange.getResponseHeaders().set("X-RateLimit-Burst-Requests", Integer.toString(penalty.requests));
            exchange.getResponseHeaders().set("X-RateLimit-Penalty-Active", Boolean.toString(penalty.active));
            if (penalty.active) {
                exchange.getResponseHeaders().set("X-RateLimit-Penalty-Until", Long.toString(penalty.until));
            }
        }
        if (decision.allowed) return true;
        return rateLimited(exchange, requestKind, limit, decision.retryAfterSeconds);
    }

    private static boolean rateLimited(HttpExchange exchange, RequestKind requestKind, int limit,
                                       long retryAfterSeconds) throws IOException {
        exchange.getResponseHeaders().set("Retry-After", Long.toString(retryAfterSeconds));
        String message = switch (requestKind) {
            case API_DATA -> "请求过于频繁：数据请求基础每秒最多 " + dataRequestsPerSecond
                    + " 次；30 秒内超过 30 次后，30 分钟内每 5 秒最多 1 次。";
            case ICON -> "请求过于频繁：同一图标基础每 " + iconRequestIntervalSeconds
                    + " 秒最多 1 次；30 秒内图片请求超过 6 次后，30 分钟内每 15 秒最多 1 次。";
            case WEB_ASSET -> "请求过于频繁，请在 " + retryAfterSeconds + " 秒后重试。";
        };
        if (exchange.getRequestURI().getPath().startsWith("/api/")) {
            JsonObject error = new JsonObject();
            error.addProperty("error", message);
            respond(exchange, 429, "application/json; charset=utf-8", error.toString());
        } else {
            respond(exchange, 429, "text/plain; charset=utf-8", message);
        }
        return false;
    }

    private static String normalizedRateResource(String path) {
        if (path.startsWith("/avatar/")) {
            String value = path.substring("/avatar/".length());
            try { return "/avatar/" + UUID.fromString(value); }
            catch (IllegalArgumentException ignored) { return "/avatar/invalid"; }
        }
        return path.length() <= 256 ? path : "/invalid-path";
    }

    private static boolean isImagePath(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")
                || lower.endsWith(".ico");
    }

    private static void cleanupRequestWindows(long now) {
        REQUEST_WINDOWS.entrySet().removeIf(
                entry -> now - entry.getValue().lastAccess() > PENALTY_DURATION_MILLIS + BURST_WINDOW_MILLIS);
    }

    private static BurstPenalty recordBurst(String address, RequestKind requestKind, long now) {
        if (requestKind == RequestKind.WEB_ASSET) return BurstPenalty.NONE;
        String key = address + '\n' + requestKind;
        BurstPenaltyWindow penalty = BURST_PENALTIES.computeIfAbsent(key, ignored -> new BurstPenaltyWindow());
        return penalty.record(now,
                requestKind == RequestKind.API_DATA ? DATA_BURST_THRESHOLD : ICON_BURST_THRESHOLD);
    }

    private static void maybeCleanupBurst(long now) {
        maybeCleanup(now);
        BURST_PENALTIES.entrySet().removeIf(
                entry -> now - entry.getValue().lastAccess() > PENALTY_DURATION_MILLIS + BURST_WINDOW_MILLIS);
    }

    private static long requestIntervalMillis(RequestKind requestKind, boolean penaltyActive) {
        if (requestKind == RequestKind.API_DATA) {
            return penaltyActive ? DATA_PENALTY_INTERVAL_MILLIS : DATA_WINDOW_MILLIS;
        }
        if (requestKind == RequestKind.ICON) {
            return penaltyActive ? ICON_PENALTY_INTERVAL_MILLIS
                    : TimeUnit.SECONDS.toMillis(iconRequestIntervalSeconds);
        }
        return DATA_WINDOW_MILLIS;
    }

    private static void maybeCleanup(long now) {
        long previous = LAST_CLEANUP.get();
        if (now - previous < CLEANUP_INTERVAL_MILLIS || !LAST_CLEANUP.compareAndSet(previous, now)) return;
        cleanupRequestWindows(now);
    }

    private enum RequestKind { API_DATA, WEB_ASSET, ICON }

    private static final class RequestWindow {
        private final ArrayDeque<Long> requests = new ArrayDeque<>();
        private long lastAccess;

        synchronized RateDecision acquire(long now, int limit, long windowMillis) {
            lastAccess = now;
            while (!requests.isEmpty() && now - requests.peekFirst() >= windowMillis) requests.removeFirst();
            if (requests.size() < limit) {
                requests.addLast(now);
                return new RateDecision(true, limit - requests.size(), 0);
            }
            long retryAfter = Math.max(1, (requests.peekFirst() + windowMillis - now + 999) / 1000);
            return new RateDecision(false, 0, retryAfter);
        }

        synchronized long lastAccess() { return lastAccess; }
    }

    private static final class BurstPenaltyWindow {
        private final ArrayDeque<Long> requests = new ArrayDeque<>();
        private long lastAccess;
        private long penaltyUntil;

        synchronized BurstPenalty record(long now, int threshold) {
            lastAccess = now;
            while (!requests.isEmpty() && now - requests.peekFirst() >= BURST_WINDOW_MILLIS) {
                requests.removeFirst();
            }
            requests.addLast(now);
            if (penaltyUntil <= now && requests.size() > threshold) {
                penaltyUntil = now + PENALTY_DURATION_MILLIS;
            }
            return new BurstPenalty(requests.size(), penaltyUntil > now, penaltyUntil);
        }

        synchronized long lastAccess() { return lastAccess; }
    }

    private record RateDecision(boolean allowed, int remaining, long retryAfterSeconds) { }

    private record CachedRanking(long createdAt, String body) { }

    private record BurstPenalty(int requests, boolean active, long until) {
        private static final BurstPenalty NONE = new BurstPenalty(0, false, 0);
    }

    private record WebEntry(UUID uuid, String name, long value) { }

    private static String page(String name) {
        return PAGE.replace("{{SERVER_NAME}}", escapeHtml(name));
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String PAGE = """
            <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>RankBoard · {{SERVER_NAME}}</title><link rel="icon" href="/site-icon"><script>try{document.documentElement.dataset.theme=localStorage.getItem('rankboard-theme')||'ocean'}catch(e){}</script><style>
            :root{color-scheme:dark;--page:#06163a;--header:#08295d;--surface:#0a2e63;--surface-strong:#0b3c79;--line:#1d71ad;--blue:#138ee8;--cyan:#39d9f3;--lavender:#b79bd7;--pink:#efb5d8;--text:#f4fbff;--muted:#9fc3dc;--danger:#ff9db9}
            :root[data-theme="violet"]{--page:#160d32;--header:#28134e;--surface:#34205e;--surface-strong:#492a76;--line:#7652a4;--blue:#8f61d5;--cyan:#d69cf2;--lavender:#b9a7ff;--pink:#ff9dcc;--text:#fff7ff;--muted:#c9b8dc;--danger:#ff91aa}
            :root[data-theme="emerald"]{--page:#062725;--header:#083a38;--surface:#0d4945;--surface-strong:#12605a;--line:#288c7d;--blue:#159982;--cyan:#55e3c2;--lavender:#a8cfae;--pink:#f2c879;--text:#f2fff9;--muted:#a9d3c6;--danger:#ff9c9c}
            :root[data-theme="contrast"]{--page:#090b11;--header:#141925;--surface:#202735;--surface-strong:#303a4c;--line:#697a92;--blue:#f0b429;--cyan:#75e6ff;--lavender:#c8b6ff;--pink:#ffb3c7;--text:#ffffff;--muted:#c5ceda;--danger:#ff8c8c}
            *{box-sizing:border-box}body{margin:0;padding-bottom:44px;background:var(--page);color:var(--text);font:14px system-ui,"Microsoft YaHei",sans-serif;letter-spacing:0;min-height:100vh}
            header{height:64px;border-bottom:1px solid var(--line);background:var(--header);display:flex;align-items:center;padding:0 28px;gap:12px;box-shadow:0 6px 24px rgba(10,155,224,.2)}header img{width:36px;height:36px;border:1px solid var(--cyan);border-radius:4px;object-fit:cover;image-rendering:pixelated}.identity{display:flex;align-items:baseline;gap:10px;min-width:0}h1{font-size:20px;margin:0;color:#fff;text-shadow:0 0 14px color-mix(in srgb,var(--cyan) 70%,transparent);white-space:nowrap}.server-name{color:var(--pink);font:italic 700 17px/1.1 "Segoe Script","KaiTi","STKaiti",cursive;letter-spacing:0;transform:rotate(-2deg);text-shadow:0 0 10px color-mix(in srgb,var(--pink) 75%,transparent),1px 1px 0 color-mix(in srgb,var(--lavender) 70%,transparent);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.palette{margin-left:auto;display:flex;gap:7px;flex:none}.palette button{width:24px;height:24px;padding:3px;border:1px solid var(--line);border-radius:4px;background:var(--surface);display:grid;grid-template-columns:1fr 1fr;overflow:hidden}.palette button span{height:16px}.palette button.active{outline:2px solid var(--text);outline-offset:2px}.palette button:hover{background:var(--surface-strong)}
            .palette [data-theme="ocean"] span:first-child{background:#138ee8}.palette [data-theme="ocean"] span:last-child{background:#efb5d8}.palette [data-theme="violet"] span:first-child{background:#8f61d5}.palette [data-theme="violet"] span:last-child{background:#ff9dcc}.palette [data-theme="emerald"] span:first-child{background:#159982}.palette [data-theme="emerald"] span:last-child{background:#f2c879}.palette [data-theme="contrast"] span:first-child{background:#f0b429}.palette [data-theme="contrast"] span:last-child{background:#75e6ff}
            main{max-width:1050px;margin:0 auto;padding:24px}.periods{display:flex;margin-bottom:18px}.periods button{background:var(--surface);color:var(--muted);border-color:var(--line);border-radius:0;border-right-width:0}.periods button:first-child{border-radius:4px 0 0 4px}.periods button:last-child{border-radius:0 4px 4px 0;border-right-width:1px}.periods button.active{background:var(--blue);color:#fff;border-color:var(--cyan);box-shadow:inset 0 -2px 0 var(--cyan)}.controls{display:grid;grid-template-columns:2fr 1fr 1fr auto;gap:12px;align-items:end;margin-bottom:22px}.date-field.hidden{display:none}
            label{display:grid;gap:6px;color:var(--muted);font-size:12px}select,input,button{height:38px;border:1px solid var(--line);border-radius:4px;background:var(--surface);color:var(--text);padding:0 10px;font:inherit}select:focus,input:focus,button:focus-visible{outline:2px solid var(--cyan);outline-offset:2px}button{background:var(--blue);color:#fff;border-color:#42c8ef;font-weight:650;cursor:pointer;padding:0 20px}button:hover{background:#20a3ee}
            .summary{display:flex;justify-content:space-between;align-items:baseline;border-block:1px solid var(--line);padding:14px 4px;margin-bottom:8px}.summary b{color:var(--pink)}.summary strong{font-size:22px;color:var(--cyan);font-variant-numeric:tabular-nums}.muted{color:var(--muted)}#progress{padding:10px 12px;margin-bottom:12px;border-left:3px solid var(--lavender);background:rgba(65,89,150,.28)}
            table{width:100%;border-collapse:collapse;background:rgba(9,41,88,.45)}th,td{text-align:left;padding:8px 10px;border-bottom:1px solid rgba(50,132,183,.42)}th{color:#aed4e9;background:rgba(12,59,116,.6);font-size:12px;font-weight:600}td.rank{width:70px;color:var(--lavender);font-weight:650}.player{display:flex;align-items:center;gap:9px;min-height:28px}.player-avatar{width:28px;height:28px;flex:none;border-radius:3px;image-rendering:pixelated;background:var(--surface-strong)}td.value{text-align:right;color:var(--cyan);font-weight:650;font-variant-numeric:tabular-nums;white-space:nowrap}tbody tr:hover{background:var(--surface-strong)}tbody tr:nth-child(-n+3) td.rank{color:var(--pink)}.error{color:var(--danger);padding:16px 0}
            .mod-links{position:fixed;z-index:2;left:16px;bottom:12px;display:flex;align-items:center;gap:8px;color:var(--muted);font-size:12px}.mod-links span{color:var(--pink);font-weight:650}.mod-links a{color:var(--cyan);text-decoration:none}.mod-links a:hover,.mod-links a:focus-visible{text-decoration:underline;text-underline-offset:3px}
            @media(max-width:700px){body{padding-bottom:40px}header{padding:0 16px;gap:9px}.identity{display:grid;gap:1px}.server-name{max-width:150px;font-size:14px}.palette{gap:5px}.palette button{width:22px;height:22px;padding:3px}.palette button span{height:14px}main{padding:16px}.periods{display:grid;grid-template-columns:repeat(3,1fr)}.periods button,.periods button:first-child,.periods button:last-child{border:1px solid var(--line);border-radius:0;padding:0 6px}.controls{grid-template-columns:1fr 1fr}.controls label:first-child{grid-column:1/-1}button{width:100%}.summary{align-items:flex-start;gap:12px}.summary strong{font-size:18px;text-align:right}th:nth-child(3),td:nth-child(3){display:none}td.value{font-size:13px}.mod-links{left:12px;bottom:10px;font-size:11px;gap:6px}}
            </style></head><body><header><img src="/site-icon" onerror="this.style.display='none'" alt=""><div class="identity"><h1>RankBoard</h1><span class="server-name">{{SERVER_NAME}}</span></div><div class="palette" aria-label="网站配色"><button type="button" data-theme="ocean" title="蓝青"><span></span><span></span></button><button type="button" data-theme="violet" title="紫粉"><span></span><span></span></button><button type="button" data-theme="emerald" title="青绿"><span></span><span></span></button><button type="button" data-theme="contrast" title="高对比"><span></span><span></span></button></div></header><main>
            <div class="periods" id="periods" role="group" aria-label="时间范围"><button type="button" data-period="day">最近一日</button><button type="button" data-period="week">最近一周</button><button type="button" data-period="month">最近一月</button><button type="button" data-period="all" class="active">总榜</button><button type="button" data-period="custom">自定义</button></div>
            <form class="controls" id="form"><label>榜单<select id="metric"><option value="playtime">在线时间</option><option value="food">食物</option><option value="jumps">跳跃</option><option value="mined">挖掘</option><option value="placed">放置</option><option value="kills">击杀</option><option value="deaths">死亡</option><option value="trades">交易</option><option value="elytra">鞘翅飞行</option><option value="fishing">钓鱼</option><option value="damage">受到伤害</option><option value="dropped">丢垃圾</option><option value="picked">拾荒</option><option value="crafted">合成</option><option value="redstone">红石大蛇</option></select></label><label class="date-field hidden">开始日期<input id="from" type="date" required></label><label class="date-field hidden">结束日期<input id="to" type="date" required></label><button>查询</button></form>
            <div id="progress" class="muted" hidden></div><div id="error" class="error" hidden></div><section id="result" hidden><div class="summary"><span><b id="title"></b><br><small id="range" class="muted"></small></span><strong id="total"></strong></div><table><thead><tr><th>排名</th><th>玩家</th><th>UUID</th><th style="text-align:right">数值</th></tr></thead><tbody id="rows"></tbody></table></section>
            </main><footer class="mod-links"><span>RankBoard Mod</span><a href="https://modrinth.com/project/rankboard" target="_blank" rel="noopener noreferrer">Modrinth</a><a href="https://github.com/halfkite/rankboard" target="_blank" rel="noopener noreferrer">GitHub</a></footer><script>
            const theme=document.documentElement.dataset.theme||'ocean';document.querySelectorAll('.palette button').forEach(b=>{b.classList.toggle('active',b.dataset.theme===theme);b.addEventListener('click',()=>{document.documentElement.dataset.theme=b.dataset.theme;document.querySelectorAll('.palette button').forEach(x=>x.classList.toggle('active',x===b));try{localStorage.setItem('rankboard-theme',b.dataset.theme)}catch(e){}})});
            const today=new Date().toISOString().slice(0,10);from.value=today;to.value=today;let selectedPeriod='all',lastLoadAt=0,pendingLoad;
            function requestLoad(){const wait=1000-(Date.now()-lastLoadAt);clearTimeout(pendingLoad);if(wait>0){pendingLoad=setTimeout(load,wait);return}load()}
            async function load(){lastLoadAt=Date.now();error.hidden=true;result.hidden=true;progress.hidden=true;let url=`/api/rankings?metric=${encodeURIComponent(metric.value)}&period=${selectedPeriod}`;if(selectedPeriod==='custom')url+=`&from=${from.value}&to=${to.value}`;try{const r=await fetch(url);const d=await r.json();if(r.status===429){pendingLoad=setTimeout(load,1000);return}if(!r.ok)throw new Error(d.error||'查询失败');if(!d.cacheReady){progress.textContent=`历史统计加载中：${d.cacheProcessed}/${d.cacheTotal}，当前总榜为已加载部分`;progress.hidden=false;pendingLoad=setTimeout(load,2000)}else if(d.cacheChecking){progress.textContent=`历史统计正在后台校验：${d.cacheProcessed}/${d.cacheTotal}`;progress.hidden=false;pendingLoad=setTimeout(load,2000)}title.textContent=d.label;const filterNote=d.onlineOnly?' · 仅在线玩家':'';range.textContent=(d.period==='all'?`总榜 · 原版累计统计`: `${d.from} 至 ${d.to} · 实际可用 ${d.actualStart} 至 ${d.actualEnd}`)+filterNote;total.textContent=`总和 ${d.formattedTotal}`;rows.innerHTML=d.players.map(p=>`<tr><td class="rank">${p.rank}</td><td><span class="player"><img class="player-avatar" src="https://mc-heads.net/avatar/${encodeURIComponent(p.uuid)}/28" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.style.display='none'"><span>${escapeHtml(p.name)}</span></span></td><td class="muted">${p.uuid}</td><td class="value">${p.formatted}</td></tr>`).join('');result.hidden=false}catch(e){error.textContent=e.message;error.hidden=false}}
            function escapeHtml(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}periods.addEventListener('click',e=>{const b=e.target.closest('button[data-period]');if(!b)return;selectedPeriod=b.dataset.period;periods.querySelectorAll('button').forEach(x=>x.classList.toggle('active',x===b));document.querySelectorAll('.date-field').forEach(x=>x.classList.toggle('hidden',selectedPeriod!=='custom'));if(selectedPeriod!=='custom')requestLoad()});metric.addEventListener('change',requestLoad);form.addEventListener('submit',e=>{e.preventDefault();requestLoad()});requestLoad();
            </script></body></html>
            """;
}
