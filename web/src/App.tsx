import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Download, ExternalLink, Github, LayoutPanelTop, PackageOpen, Rows3, Server, Table2 } from "lucide-react";
import BlurText from "@/components/BlurText/BlurText";

type Metric = {
  id: string;
  label: string;
  detail: string;
};

type Player = {
  rank: number;
  uuid: string;
  name: string;
  value: number;
  formatted: string;
  lastOnline: number;
  online: boolean;
  metrics?: Record<string, string>;
};

type RankingResponse = {
  metric?: string;
  label: string;
  formattedTotal: string;
  players: Player[];
  cacheReady: boolean;
  cacheChecking?: boolean;
  cacheProcessed?: number;
  cacheTotal?: number;
  onlineOnly?: boolean;
  from?: string;
  to?: string;
  actualStart?: string;
  actualEnd?: string;
  earliest?: string;
  complete?: boolean;
  warnings?: string[];
};

type ThemeColors = {
  background: string;
  surface: string;
  primary: string;
  secondary: string;
  text: string;
  muted: string;
  border: string;
  success: string;
  danger: string;
};

type SiteTheme = {
  followIcon: boolean;
  base: string;
  colors: ThemeColors;
};

type SiteLink = {
  name: string;
  url: string;
  weight: number;
  current: boolean;
  online: boolean;
};

type Language = "zh" | "en";

const UI_TEXT: Record<Language, Record<string, string>> = {
  zh: {
    language: "中文", languageLabel: "语言", currentOnline: "当前在线", lastOnlineUnknown: "最后在线：未知", lastOnline: "最后在线",
    total: "总和", query: "查询", all: "总榜", day: "最近一日", week: "最近一周", month: "最近一月", custom: "自定义日期",
    compact: "紧凑视图", tile: "平铺视图", detail: "详细视图", normal: "普通视图", layoutMode: "布局", switchServer: "服务器切换", current: "当前", switch: "切换", offline: "离线",
    statisticsPeriod: "统计周期", onlineOnly: "仅显示当前在线玩家", metricCategory: "榜单分类", downloadMod: "下载模组", sourceCode: "查看源码",
    searchPlayer: "搜索玩家", enterPlayerName: "输入玩家名称", export: "导出表格", exportTitle: "导出当前筛选结果为 CSV 表格", copyIdentity: "点击复制玩家名称和 UUID", copied: "已复制玩家名称和 UUID",
    startDate: "开始日期", endDate: "结束日期", earliest: "最早可查", backgroundCheck: "后台校验", loading: "正在读取服务器排行榜...",
    noPlayers: "当前筛选下没有可显示的玩家。", partial: "部分统计", incompleteRange: "统计范围缺少完整边界",
    historySync: "历史统计同步", onlyOnline: "仅在线玩家", rank: "排名", playerName: "玩家名称", uuid: "UUID", value: "数值",
    serverIcon: "服务器图标", websiteAria: "RankBoard 模组链接", allMetrics: "的全部榜单数值", noMetric: "无可用榜单",
    playtimeDetail: "活跃度", afkDetail: "摸鱼", breedingDetail: "繁殖", bedrockDetail: "基岩", foodDetail: "食物", jumpsDetail: "移动", minedDetail: "资源", placedDetail: "建造",
    killsDetail: "战斗", pvpDetail: "玩家对战", deathsDetail: "生存", tradesDetail: "经济", elytraDetail: "探索",
    fishingDetail: "休闲", damageDetail: "生存", dealtDetail: "战斗", droppedDetail: "物品", pickedDetail: "物品",
    craftedDetail: "制造", redstoneDetail: "红石", modTitle: "RankBoard排行榜模组"
  },
  en: {
    language: "English", languageLabel: "Language", currentOnline: "Online now", lastOnlineUnknown: "Last online: unknown", lastOnline: "Last online",
    total: "Total", query: "Query", all: "All time", day: "Last day", week: "Last week", month: "Last month", custom: "Custom dates",
    compact: "Compact view", tile: "Tile view", detail: "Detailed view", normal: "Normal view", layoutMode: "Layout", switchServer: "Server switcher", current: "Current", switch: "Switch", offline: "Offline",
    statisticsPeriod: "Statistics period", onlineOnly: "Show online players only", metricCategory: "Leaderboards", downloadMod: "Download mod", sourceCode: "View source",
    searchPlayer: "Search players", enterPlayerName: "Enter a player name", export: "Export table", exportTitle: "Export the filtered result as a CSV table", copyIdentity: "Click to copy player name and UUID", copied: "Player name and UUID copied",
    startDate: "Start date", endDate: "End date", earliest: "Earliest available", backgroundCheck: "Background verification", loading: "Loading server leaderboard...",
    noPlayers: "No players are available for the current filters.", partial: "Partial statistics", incompleteRange: "The selected range has incomplete boundaries",
    historySync: "History synchronized", onlyOnline: "Online players only", rank: "Rank", playerName: "Player name", uuid: "UUID", value: "Value",
    serverIcon: "Server icon", websiteAria: "RankBoard mod links", allMetrics: "'s leaderboard values", noMetric: "No available leaderboard",
    playtimeDetail: "Activity", afkDetail: "AFK", breedingDetail: "Breeding", bedrockDetail: "Bedrock", foodDetail: "Food", jumpsDetail: "Movement", minedDetail: "Resources", placedDetail: "Building",
    killsDetail: "Combat", pvpDetail: "Player combat", deathsDetail: "Survival", tradesDetail: "Economy", elytraDetail: "Exploration",
    fishingDetail: "Leisure", damageDetail: "Survival", dealtDetail: "Combat", droppedDetail: "Items", pickedDetail: "Items",
    craftedDetail: "Crafting", redstoneDetail: "Redstone", modTitle: "RankBoard Leaderboard Mod"
  }
};

const METRIC_TEXT: Record<string, { zh: string; en: string }> = {
  playtime: { zh: "在线时间", en: "Playtime" }, food: { zh: "大胃王", en: "Food eater" }, jumps: { zh: "跳跃榜", en: "Jumps" },
  mined: { zh: "挖掘榜", en: "Mining" }, placed: { zh: "放置榜", en: "Placing" }, breeding: { zh: "繁殖榜", en: "Breeding" }, bedrock: { zh: "破基岩榜", en: "Bedrock breaking" }, kills: { zh: "击杀榜", en: "Kills" },
  pvp: { zh: "PvP榜", en: "PvP" }, deaths: { zh: "死亡榜", en: "Deaths" }, trades: { zh: "交易榜", en: "Trades" },
  afk: { zh: "摸鱼榜", en: "AFK time" }, elytra: { zh: "鞘翅飞行榜", en: "Flight" }, fishing: { zh: "钓鱼榜", en: "Fishing" }, damage: { zh: "受伤榜", en: "Damage taken" },
  dealt: { zh: "输出榜", en: "Damage dealt" }, dropped: { zh: "丢垃圾榜", en: "Dropped items" }, picked: { zh: "拾荒榜", en: "Picked items" },
  crafted: { zh: "合成榜", en: "Crafted items" }, redstone: { zh: "红石大蛇榜", en: "Redstone builder" }
};

const defaultTheme: SiteTheme = {
  followIcon: true,
  base: "auto",
  colors: {
    background: "auto", surface: "auto", primary: "auto", secondary: "auto", text: "auto",
    muted: "auto", border: "auto", success: "auto", danger: "auto"
  }
};

function parseHex(value: string) {
  const match = /^#([0-9a-f]{6})$/i.exec(value);
  if (!match) return null;
  const number = Number.parseInt(match[1], 16);
  return [(number >> 16) & 255, (number >> 8) & 255, number & 255] as const;
}

function hex(red: number, green: number, blue: number) {
  return `#${[red, green, blue].map((value) => Math.round(value).toString(16).padStart(2, "0")).join("")}`;
}

function mix(left: string, right: string, rightWeight: number) {
  const a = parseHex(left) ?? [21, 156, 229];
  const b = parseHex(right) ?? [255, 255, 255];
  return hex(a[0] * (1 - rightWeight) + b[0] * rightWeight,
    a[1] * (1 - rightWeight) + b[1] * rightWeight,
    a[2] * (1 - rightWeight) + b[2] * rightWeight);
}

function iconAverage(image: HTMLImageElement) {
  try {
    const canvas = document.createElement("canvas");
    canvas.width = 32;
    canvas.height = 32;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) return null;
    context.drawImage(image, 0, 0, 32, 32);
    const pixels = context.getImageData(0, 0, 32, 32).data;
    let red = 0, green = 0, blue = 0, count = 0;
    for (let index = 0; index < pixels.length; index += 4) {
      if (pixels[index + 3] < 48) continue;
      red += pixels[index]; green += pixels[index + 1]; blue += pixels[index + 2]; count++;
    }
    return count ? hex(red / count, green / count, blue / count) : null;
  } catch {
    return null;
  }
}

function applyTheme(theme: SiteTheme, iconColor: string | null) {
  const base = theme.followIcon && iconColor ? iconColor : (parseHex(theme.base) ? theme.base : "#159CE5");
  const generated: ThemeColors = {
    background: mix(base, "#FFFFFF", 0.78),
    surface: mix(base, "#FFFFFF", 0.91),
    primary: mix(base, "#159CE5", 0.22),
    secondary: mix(base, "#8B63A7", 0.42),
    text: mix(base, "#102A50", 0.68),
    muted: mix(base, "#5575A2", 0.62),
    border: mix(base, "#FFFFFF", 0.56),
    success: mix(base, "#18A96D", 0.68),
    danger: mix(base, "#C94E73", 0.72)
  };
  (Object.keys(generated) as Array<keyof ThemeColors>).forEach((key) => {
    const configured = theme.colors[key];
    document.documentElement.style.setProperty(`--theme-${key}`,
      parseHex(configured) ? configured : generated[key]);
  });
}

function PlayerAvatar({ player, language, onCopy, copyTitle }: { player: Player; language: Language; onCopy?: () => void; copyTitle?: string }) {
  const [sourceIndex, setSourceIndex] = useState(0);
  const uuid = player.uuid.replaceAll("-", "");
  const sources = [
    `/avatar/${player.uuid}`,
    `https://crafthead.net/avatar/${uuid}/64`,
    `https://minotar.net/avatar/${encodeURIComponent(player.name)}/64`
  ];

  if (sourceIndex >= sources.length) {
    return <span className={`avatar avatar-fallback${onCopy ? " avatar-copyable" : ""}`} role={onCopy ? "button" : undefined}
      tabIndex={onCopy ? 0 : undefined} title={copyTitle} aria-label={copyTitle}
      onClick={onCopy} onKeyDown={(event) => { if (onCopy && (event.key === "Enter" || event.key === " ")) onCopy(); }}>
      {player.name.slice(0, 1).toUpperCase()}
    </span>;
  }

  return (
    <img
      className="avatar"
      src={sources[sourceIndex]}
      alt={language === "zh" ? `${player.name} 的头像` : `${player.name}'s avatar`}
      title={copyTitle}
      role={onCopy ? "button" : undefined}
      tabIndex={onCopy ? 0 : undefined}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setSourceIndex((current) => current + 1)}
      onClick={onCopy}
      onKeyDown={(event) => { if (onCopy && (event.key === "Enter" || event.key === " ")) onCopy(); }}
    />
  );
}

function formatLastOnline(player: Player, language: Language) {
  const text = UI_TEXT[language];
  if (player.online) return text.currentOnline;
  if (player.lastOnline <= 0) return text.lastOnlineUnknown;
  return `${text.lastOnline}: ${new Date(player.lastOnline).toLocaleString(language === "zh" ? "zh-CN" : "en-US", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
  })}`;
}

function lastOnlineParts(player: Player, language: Language) {
  const text = UI_TEXT[language];
  if (player.online) return { time: text.currentOnline, date: "" };
  if (player.lastOnline <= 0) return { time: text.lastOnlineUnknown, date: "" };
  const value = new Date(player.lastOnline);
  return {
    time: value.toLocaleTimeString(language === "zh" ? "zh-CN" : "en-US", {
      hour: "2-digit", minute: "2-digit", hour12: false
    }),
    date: value.toLocaleDateString(language === "zh" ? "zh-CN" : "en-US", {
      year: "numeric", month: "numeric", day: "numeric"
    })
  };
}

function LastOnline({ player, language }: { player: Player; language: Language }) {
  const parts = lastOnlineParts(player, language);
  return (
    <span className={player.online ? "last-online online" : "last-online"}>
      <span className="last-online-time">{parts.time}</span>
      {parts.date && <span className="last-online-date">{parts.date}</span>}
    </span>
  );
}

/** Places the precise total first and the abbreviated web value below it. */
function compactValue(value: string) {
  const separator = " · ";
  const index = value.lastIndexOf(separator);
  if (index < 0) return { exact: value, short: "" };
  return { exact: value.slice(index + separator.length), short: value.slice(0, index) };
}

function csvCell(value: string | number) {
  return `"${String(value).replaceAll('"', '""')}"`;
}

const periods = [
  { id: "all", label: "all" },
  { id: "day", label: "day" },
  { id: "week", label: "week" },
  { id: "month", label: "month" },
  { id: "custom", label: "custom" }
];

const defaultMetrics: Metric[] = [
  { id: "playtime", label: "在线榜", detail: "活跃度" },
  { id: "placed", label: "放置榜", detail: "建造" },
  { id: "mined", label: "挖掘榜", detail: "资源" },
  { id: "deaths", label: "死亡榜", detail: "生存" },
  { id: "elytra", label: "飞行榜", detail: "探索" },
  { id: "food", label: "大胃王榜", detail: "食物" },
  { id: "jumps", label: "跳跃榜", detail: "移动" },
  { id: "kills", label: "击杀榜", detail: "战斗" },
  { id: "damage", label: "受伤榜", detail: "生存" },
  { id: "dealt", label: "输出榜", detail: "战斗" },
  { id: "pvp", label: "PvP榜", detail: "玩家对战" },
  { id: "breeding", label: "繁殖榜", detail: "繁殖" },
  { id: "trades", label: "交易榜", detail: "经济" },
  { id: "fishing", label: "钓鱼榜", detail: "休闲" },
  { id: "dropped", label: "丢垃圾榜", detail: "物品" },
  { id: "picked", label: "拾荒榜", detail: "物品" },
  { id: "crafted", label: "合成榜", detail: "制造" },
  { id: "redstone", label: "红石大蛇榜", detail: "红石" },
  { id: "afk", label: "摸鱼榜", detail: "摸鱼" },
  { id: "bedrock", label: "破基岩榜", detail: "基岩" }
];

const today = new Date().toISOString().slice(0, 10);

function periodLabel(id: string, language: Language) {
  return UI_TEXT[language][id] ?? id;
}

function metricLabel(item: Metric, language: Language) {
  const translated = METRIC_TEXT[item.id];
  const defaultItem = defaultMetrics.find((candidate) => candidate.id === item.id);
  // The API returns the configured label (normally the Chinese default).  Keep
  // administrator custom labels intact, while translating known built-in
  // labels whenever the user switches languages.
  const isBuiltInLabel = translated && (defaultItem?.label === item.label
    || translated.zh === item.label || translated.en === item.label);
  return isBuiltInLabel ? translated[language] : item.label;
}

function metricDetail(item: Metric, language: Language) {
  return UI_TEXT[language][`${item.id}Detail`] ?? item.detail;
}

function languageFromCode(value: string | undefined): Language | null {
  if (!value) return null;
  const normalized = value.toLowerCase();
  if (normalized === "zh" || normalized === "zh_cn" || normalized === "zh-cn") return "zh";
  if (normalized === "en" || normalized === "en_us" || normalized === "en-us") return "en";
  return null;
}

/** Localises the game-mode suffix generated for automatic server switcher names. */
function localizedServerName(name: string, language: Language) {
  if (name === "Minecraft Server") return language === "zh" ? "Minecraft 服务器" : name;
  const modes: Record<string, { zh: string; en: string }> = {
    survival: { zh: "生存", en: "Survival" },
    creative: { zh: "创造", en: "Creative" },
    adventure: { zh: "冒险", en: "Adventure" },
    spectator: { zh: "旁观", en: "Spectator" }
  };
  return name.replace(/(\s*[·•]\s*)(survival|creative|adventure|spectator)\s*$/i,
    (_match, separator: string, mode: string) => `${separator}${modes[mode.toLowerCase()][language]}`);
}

function explicitLanguage(): Language | null {
  const queryLanguage = languageFromCode(new URLSearchParams(window.location.search).get("lang") ?? undefined);
  if (queryLanguage) return queryLanguage;
  return languageFromCode(localStorage.getItem("rankboard-language") ?? undefined);
}

/** Keeps same-port local server selections on the shared dashboard listener. */
function serverScoped(path: string) {
  const server = new URLSearchParams(window.location.search).get("server");
  if (!server) return path;
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}server=${encodeURIComponent(server)}`;
}

export default function App() {
  const [language, setLanguage] = useState<Language>(() =>
    explicitLanguage() ?? (navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en"));
  const [period, setPeriod] = useState("all");
  const [metric, setMetric] = useState("playtime");
  const [query, setQuery] = useState("");
  const [onlineOnly, setOnlineOnly] = useState(false);
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [serverName, setServerName] = useState("Minecraft Server");
  const [metrics, setMetrics] = useState<Metric[]>(defaultMetrics);
  const [siteTheme, setSiteTheme] = useState<SiteTheme>(defaultTheme);
  const [iconColor, setIconColor] = useState<string | null>(null);
  const [iconVersion, setIconVersion] = useState("");
  const [iconSource, setIconSource] = useState<string | null>(null);
  const [rankingRefreshIntervalSeconds, setRankingRefreshIntervalSeconds] = useState(30);
  const [ranking, setRanking] = useState<RankingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sites, setSites] = useState<SiteLink[]>([]);
  const [serverMenuOpen, setServerMenuOpen] = useState(false);
  const [layoutMode, setLayoutMode] = useState<"normal" | "compact" | "tile">(() => {
    const saved = localStorage.getItem("rankboard-layout-mode");
    if (saved === "normal" || saved === "compact" || saved === "tile") return saved;
    if (localStorage.getItem("rankboard-compact-mode") === "true") return "compact";
    return window.matchMedia?.("(max-width: 800px)").matches ? "compact" : "tile";
  });
  const [tileSortMetric, setTileSortMetric] = useState("playtime");
  const tileTableWrapRef = useRef<HTMLDivElement | null>(null);
  const tileTableTopScrollRef = useRef<HTMLDivElement | null>(null);
  const [tileTableScrollWidth, setTileTableScrollWidth] = useState(1500);
  const [copiedIdentity, setCopiedIdentity] = useState<string | null>(null);
  const compactMode = layoutMode === "compact";
  const tileMode = layoutMode === "tile";
  const text = UI_TEXT[language];

  useEffect(() => {
    localStorage.setItem("rankboard-language", language);
    document.documentElement.lang = language === "zh" ? "zh-CN" : "en";
  }, [language]);

  useEffect(() => {
    let cancelled = false;
    const loadRanking = async () => {
      setLoading(true);
      setError(null);
      try {
        const params = new URLSearchParams({ period, metric, online: String(onlineOnly), compact: String(compactMode || tileMode) });
        if (period === "custom") {
          params.set("from", from);
          params.set("to", to);
        }
        const response = await fetch(serverScoped(`/api/rankings?${params}`));
        const body = await response.text();
        let payload: (RankingResponse & { error?: string }) | null = null;
        try {
          payload = body ? (JSON.parse(body) as RankingResponse & { error?: string }) : null;
        } catch {
          throw new Error(language === "zh" ? "服务器排行榜服务返回了无效数据" : "The leaderboard service returned invalid data");
        }
        if (!response.ok || !payload) throw new Error(payload?.error ?? (language === "zh" ? "服务器排行榜服务未启动或不可访问" : "The leaderboard service is unavailable"));
        if (!cancelled) {
          setRanking(payload);
        }
      } catch (requestError) {
        if (!cancelled) {
          setRanking(null);
          setError(requestError instanceof Error ? requestError.message : (language === "zh" ? "无法连接服务器" : "Unable to connect to the server"));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    const timer = window.setTimeout(loadRanking, 1050);
    const interval = window.setInterval(loadRanking, Math.max(1, rankingRefreshIntervalSeconds) * 1000);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      window.clearInterval(interval);
    };
  }, [period, metric, onlineOnly, from, to, layoutMode, compactMode, tileMode, rankingRefreshIntervalSeconds, language]);

  useEffect(() => {
    localStorage.setItem("rankboard-layout-mode", layoutMode);
    // Keep the old key for clients upgraded from the two-layout version.
    localStorage.setItem("rankboard-compact-mode", String(compactMode));
  }, [layoutMode, compactMode]);

  useEffect(() => {
    applyTheme(siteTheme, iconColor);
  }, [siteTheme, iconColor]);

  useEffect(() => {
    fetch(serverScoped("/api/site"))
      .then((response) => response.ok ? response.json() as Promise<{
        name?: string;
        rankingRefreshIntervalSeconds?: number;
        metrics?: Array<{ id: string; label: string }>;
        themeFollowIcon?: boolean;
        themeBase?: string;
        iconVersion?: string;
        defaultLanguage?: string;
        theme?: Partial<ThemeColors>;
      }> : null)
      .then((site) => {
        if (site?.name) setServerName(site.name);
        if (site?.rankingRefreshIntervalSeconds) setRankingRefreshIntervalSeconds(site.rankingRefreshIntervalSeconds);
        if (site?.metrics) {
          const details = new Map(defaultMetrics.map((item) => [item.id, item.detail]));
          const available = site.metrics.map((item) => ({
            id: item.id, label: item.label, detail: details.get(item.id) ?? ""
          }));
          setMetrics(available);
          setMetric((current) => available.some((item) => item.id === current)
            ? current : (available[0]?.id ?? current));
        }
        if (site?.iconVersion) setIconVersion(site.iconVersion);
        if (site?.defaultLanguage && !explicitLanguage()) {
          const serverLanguage = languageFromCode(site.defaultLanguage);
          if (serverLanguage) setLanguage(serverLanguage);
        }
        if (site?.theme) {
          setSiteTheme({
            followIcon: site.themeFollowIcon ?? true,
            base: site.themeBase ?? "auto",
            colors: { ...defaultTheme.colors, ...site.theme }
          });
        }
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadSites = () => fetch(serverScoped("/api/sites"))
      .then((response) => response.ok ? response.json() as Promise<{ sites?: SiteLink[] }> : null)
      .then((payload) => {
        if (!cancelled && payload?.sites) setSites(payload.sites);
      })
      .catch(() => undefined);
    loadSites();
    const interval = window.setInterval(loadSites, 30_000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (!iconVersion || iconVersion === "none") return;
    let cancelled = false;
    let objectUrl: string | null = null;
    let retryTimer: number | null = null;
    const iconUrl = serverScoped(`/site-icon/header?v=${encodeURIComponent(iconVersion)}`);

    const loadIcon = async (attempt: number) => {
      try {
        const response = await fetch(iconUrl, { cache: "default" });
        if (response.status === 429 && attempt < 4) {
          const retryAfter = Math.max(1, Number.parseInt(response.headers.get("Retry-After") ?? "3", 10));
          retryTimer = window.setTimeout(() => loadIcon(attempt + 1), retryAfter * 1000);
          return;
        }
        if (!response.ok) return;
        const blob = await response.blob();
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setIconSource(objectUrl);
      } catch {
        if (!cancelled && attempt < 4) retryTimer = window.setTimeout(() => loadIcon(attempt + 1), 3000);
      }
    };

    let favicon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!favicon) {
      favicon = document.createElement("link");
      favicon.rel = "icon";
      document.head.appendChild(favicon);
    }
    favicon.href = `/site-icon/favicon?v=${encodeURIComponent(iconVersion)}`;
    loadIcon(0);
    return () => {
      cancelled = true;
      if (retryTimer !== null) window.clearTimeout(retryTimer);
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [iconVersion]);

  const activeMetric = metrics.find((item) => item.id === metric) ?? metrics[0] ?? {
    id: "none", label: text.noMetric, detail: ""
  };
  const visiblePlayers = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return ranking?.players ?? [];
    return (ranking?.players ?? []).filter((player) => player.name.toLowerCase().includes(keyword));
  }, [query, ranking]);

  const exportTable = () => {
    if (!ranking || visiblePlayers.length === 0) return;
    const metricColumns = compactMode || tileMode ? metrics : [activeMetric];
    const headers = [text.rank, text.playerName, text.uuid, text.lastOnline, ...metricColumns.map((item) => metricLabel(item, language))];
    const rows = visiblePlayers.map((player) => [
      player.rank,
      player.name,
      player.uuid,
      formatLastOnline(player, language),
      ...metricColumns.map((item) => player.metrics?.[item.id] ?? (item.id === metric ? player.formatted : "0"))
    ]);
    const csv = "\uFEFF" + [headers, ...rows].map((row) => row.map(csvCell).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `rankboard-${period}-${metric}-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const selectMetric = (id: string) => {
    setMetric(id);
    setTileSortMetric(id);
  };

  const copyIdentity = async (player: Player) => {
    const value = `${player.name}\n${player.uuid}`;
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      const input = document.createElement("textarea");
      input.value = value;
      input.style.position = "fixed";
      input.style.opacity = "0";
      document.body.appendChild(input);
      input.select();
      document.execCommand("copy");
      input.remove();
    }
    setCopiedIdentity(player.uuid);
    window.setTimeout(() => setCopiedIdentity((current) => current === player.uuid ? null : current), 1800);
  };

  const identityTitle = (player: Player) =>
    `${copiedIdentity === player.uuid ? text.copied : text.copyIdentity}\n${player.name}\n${text.uuid}: ${player.uuid}`;

  function tileMetricValue(player: Player, item: Metric) {
    if (item.id === metric && ranking?.metric === metric) return player.value;
    const display = player.metrics?.[item.id] ?? "0";
    const exact = compactValue(display).exact.replaceAll(",", "");
    if (item.id === "playtime" || item.id === "afk") {
      const hours = Number.parseFloat(/([\d.]+)h/.exec(exact)?.[1] ?? "0");
      const minutes = Number.parseFloat(/([\d.]+)m/.exec(exact)?.[1] ?? "0");
      return hours * 60 + minutes;
    }
    return Number.parseFloat(/-?[\d.]+/.exec(exact)?.[0] ?? "0");
  }

  const tilePlayers = useMemo(() => {
    const sorted = [...visiblePlayers];
    if (!tileMode) return sorted;
    sorted.sort((left, right) => {
      const difference = tileMetricValue(right, metrics.find((item) => item.id === tileSortMetric) ?? activeMetric)
        - tileMetricValue(left, metrics.find((item) => item.id === tileSortMetric) ?? activeMetric);
      return difference || left.name.localeCompare(right.name);
    });
    return sorted.map((player, index) => ({ ...player, rank: index + 1 }));
  }, [visiblePlayers, tileMode, tileSortMetric, metric, metrics, activeMetric]);

  useLayoutEffect(() => {
    if (!tileMode) return;
    const scrollWrap = tileTableWrapRef.current;
    if (!scrollWrap) return;
    const updateScrollWidth = () => setTileTableScrollWidth(Math.max(1500, scrollWrap.scrollWidth));
    updateScrollWidth();
    const observer = new ResizeObserver(updateScrollWidth);
    observer.observe(scrollWrap);
    return () => observer.disconnect();
  }, [tileMode, tilePlayers.length, metrics.length, language]);

  const syncTileTableScroll = (source: HTMLDivElement) => {
    const target = source === tileTableTopScrollRef.current ? tileTableWrapRef.current : tileTableTopScrollRef.current;
    if (target && target.scrollLeft !== source.scrollLeft) target.scrollLeft = source.scrollLeft;
  };

  const controls = (
    <>
      <section>
        <p className="section-label">{text.statisticsPeriod}</p>
        <div className="period-list">
          {periods.map((item) => (
            <button key={item.id} className={item.id === period ? "selected" : ""} onClick={() => setPeriod(item.id)}>
              {periodLabel(item.label, language)}
            </button>
          ))}
        </div>
      </section>

      {!tileMode && <section>
        <p className="section-label">{text.metricCategory}</p>
        <div className="metric-list">
          {metrics.map((item) => (
            <button key={item.id} className={item.id === metric ? "selected" : ""} onClick={() => selectMetric(item.id)}>
              <span>{metricLabel(item, language)}</span>
              {!tileMode && <small>{metricDetail(item, language)}</small>}
            </button>
          ))}
        </div>
      </section>}

      <nav className="mod-links" aria-label={text.websiteAria}>
        <div className="mod-links-title">{text.modTitle}</div>
        <a href="https://modrinth.com/project/rankboard" target="_blank" rel="noreferrer">
          <PackageOpen aria-hidden="true" />
          <span>
            <small>{text.downloadMod}</small>
            Modrinth
          </span>
          <ExternalLink className="external-icon" aria-hidden="true" />
        </a>
        <a href="https://github.com/halfkite/rankboard" target="_blank" rel="noreferrer">
          <Github aria-hidden="true" />
          <span>
            <small>{text.sourceCode}</small>
            halfkite/rankboard
          </span>
          <ExternalLink className="external-icon" aria-hidden="true" />
        </a>
      </nav>
    </>
  );

  const viewOptions: Array<{ id: "tile" | "compact" | "normal"; label: string; Icon: typeof Table2 }> = [
    { id: "tile", label: text.detail, Icon: Table2 },
    { id: "compact", label: text.compact, Icon: Rows3 },
    { id: "normal", label: text.normal, Icon: LayoutPanelTop }
  ];

  return (
    <div className="app-shell">
      <header className="topbar glass">
        <div className="brand">
            {iconSource ? <img src={iconSource} onLoad={(event) => setIconColor(iconAverage(event.currentTarget))}
             alt={text.serverIcon} /> : <span className="brand-icon-placeholder">RB</span>}
            <BlurText text={localizedServerName(serverName, language)} delay={35} animateBy="letters" direction="top" className="brand-title" />
        </div>
        <div className="top-controls" aria-label={text.layoutMode}>
          {sites.length > 1 && (
            <div className="top-server-switcher">
              <button
                type="button"
                className="top-server-switch-button"
                title={text.switchServer}
                aria-expanded={serverMenuOpen}
                aria-haspopup="menu"
                onClick={() => setServerMenuOpen((open) => !open)}
              >
                <Server aria-hidden="true" />
                <span>{text.switchServer}</span>
                <span className="top-server-chevron" aria-hidden="true">▾</span>
              </button>
              {serverMenuOpen && <div className="top-server-list" role="menu">
                {sites.map((site) => {
                  const className = site.current ? "server-entry selected" : site.online ? "server-entry" : "server-entry disabled";
                  const title = site.online ? site.url : `${site.url} (${text.offline})`;
                  const content = (
                    <>
                      <span>{localizedServerName(site.name, language)}</span>
                      <small>{site.current ? text.current : site.online ? text.switch : text.offline}</small>
                    </>
                  );
                  if (site.current) {
                    return <span key={`top-${site.url}-${site.weight}`} className={className} title={title} aria-current="page">{content}</span>;
                  }
                  if (!site.online) {
                    return <span key={`top-${site.url}-${site.weight}`} className={className} title={title} aria-disabled="true">{content}</span>;
                  }
                  return <a key={`top-${site.url}-${site.weight}`} className={className} href={site.url} title={title}>{content}</a>;
                })}
              </div>}
            </div>
          )}
          <div className="top-status">
            <span className={ranking?.cacheReady ? "signal online" : "signal"} />
            {text.historySync}
          </div>
          <label className={onlineOnly ? "online-toggle top-online-toggle selected" : "online-toggle top-online-toggle"}>
            <input type="checkbox" checked={onlineOnly} onChange={(event) => setOnlineOnly(event.target.checked)} />
            <span>{text.onlineOnly}</span>
          </label>
          <button
            className="language-toggle"
            type="button"
            onClick={() => setLanguage((current) => current === "zh" ? "en" : "zh")}
            aria-label={text.language}
          >
            {text.languageLabel}: {text.language}
          </button>
          <div className="view-switcher" role="group" aria-label={text.layoutMode}>
            {viewOptions.map(({ id, label, Icon }) => (
              <button
                key={id}
                className={layoutMode === id ? "view-button selected" : "view-button"}
                type="button"
                aria-pressed={layoutMode === id}
                title={label}
                onClick={() => setLayoutMode(id)}
              >
                <Icon aria-hidden="true" />
                <span>{label}</span>
              </button>
            ))}
          </div>
        </div>
      </header>

      <main className={tileMode ? "workspace tile-workspace" : "workspace"}>
        {!tileMode && <aside className="sidebar glass">{controls}</aside>}

        <section className="content-area">
          {tileMode && <section className="tile-controls glass">{controls}</section>}
          <div className="toolbar glass">
            <label className="search-field">
              <span>{text.searchPlayer}</span>
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={text.enterPlayerName} />
            </label>
            <div className="total-block">
              <span>{text.total}</span>
              <strong>{ranking?.formattedTotal ?? "--"}</strong>
            </div>
            <button className="export-button" type="button" onClick={exportTable}
              disabled={!ranking || visiblePlayers.length === 0} title={text.exportTitle}>
              <Download aria-hidden="true" />
              <span>{text.export}</span>
            </button>
          </div>

          {period === "custom" && (
            <div className="date-range glass">
              <label>{text.startDate}<input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} /></label>
              <label>{text.endDate}<input type="date" value={to} min={from} max={today} onChange={(event) => setTo(event.target.value)} /></label>
              {ranking?.earliest && <span>{text.earliest}: {ranking.earliest}</span>}
            </div>
          )}

          <div className="title-row">
            <div>
              <p className="eyebrow">{period === "custom" && ranking ? `${ranking.from} – ${ranking.to}` : periodLabel(period, language)}</p>
              <h1>{metricLabel(activeMetric, language)}</h1>
            </div>
            {ranking?.cacheChecking && (
              <p className="sync-note">{text.backgroundCheck} {ranking.cacheProcessed}/{ranking.cacheTotal}</p>
            )}
          </div>

          {loading && <div className="notice glass">{text.loading}</div>}
          {error && <div className="notice error glass">{error}</div>}
          {!error && ranking?.complete === false && (
            <div className="notice error glass">
              {text.partial}: {ranking.warnings?.join(language === "zh" ? "；" : "; ") || text.incompleteRange}
            </div>
          )}

          {!loading && !error && visiblePlayers.length > 0 && tileMode && (
            <>
              <div
                className="tile-table-scrollbar"
                ref={tileTableTopScrollRef}
                onScroll={(event) => syncTileTableScroll(event.currentTarget)}
                aria-label={language === "zh" ? "横向滚动排行榜" : "Scroll leaderboard horizontally"}
              >
                <div style={{ width: `${tileTableScrollWidth}px` }} />
              </div>
              <div
                className="tile-table-wrap glass"
                ref={tileTableWrapRef}
                onScroll={(event) => syncTileTableScroll(event.currentTarget)}
              >
              <table className="tile-table">
                <thead>
                  <tr>
                    <th>{text.rank}</th>
                    <th>{text.playerName}</th>
                    <th>{text.lastOnline}</th>
                    {metrics.map((item) => (
                      <th key={item.id} className={tileSortMetric === item.id ? "sort-active" : ""}>
                        <button type="button" onClick={() => selectMetric(item.id)} title={metricDetail(item, language)}>
                          {metricLabel(item, language)}
                        </button>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {tilePlayers.map((player) => (
                    <tr key={player.uuid}>
                      <td className="tile-rank">{String(player.rank).padStart(2, "0")}</td>
                      <td>
                        <div className="tile-player"><PlayerAvatar player={player} language={language} onCopy={() => copyIdentity(player)} copyTitle={identityTitle(player)} /><span title={identityTitle(player)}>{player.name}</span></div>
                      </td>
                      <td><LastOnline player={player} language={language} /></td>
                      {metrics.map((item) => {
                        const display = player.metrics?.[item.id] ?? (item.id === metric ? player.formatted : "0");
                        const value = compactValue(display);
                        return (
                          <td key={item.id} className="metric-cell" title={value.short ? value.exact : undefined}>
                            <span className="tile-exact">{value.short || value.exact}</span>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
              </div>
            </>
          )}

          {!tileMode && <div className={compactMode ? "ranking-list compact-list" : "ranking-list"}>
            {!loading && !error && visiblePlayers.length === 0 && (
              <div className="notice glass">{text.noPlayers}</div>
            )}
            {visiblePlayers.map((player) => compactMode ? (
              <article className="ranking-card compact-card glass" key={player.uuid}>
                <div className="compact-player">
                  <span className="rank-number">{String(player.rank).padStart(2, "0")}</span>
                  <PlayerAvatar player={player} language={language} onCopy={() => copyIdentity(player)} copyTitle={identityTitle(player)} />
                  <div className="player-info">
                    <div className="player-heading">
                      <h2 title={identityTitle(player)}>{player.name}</h2>
                      <LastOnline player={player} language={language} />
                    </div>
                  </div>
                </div>
                <div className="compact-metrics" aria-label={`${player.name}${text.allMetrics}`}>
                  {metrics.map((item) => {
                    const display = player.metrics?.[item.id] ?? (item.id === metric ? player.formatted : "0");
                    const value = compactValue(display);
                    return (
                      <div className="compact-metric" key={item.id}>
                        <span>{metricLabel(item, language)}</span>
                        <strong>{value.exact}</strong>
                        {value.short && <small>{value.short}</small>}
                      </div>
                    );
                  })}
                </div>
              </article>
            ) : (
              <article className="ranking-card glass" key={player.uuid}>
                <span className="rank-number">{String(player.rank).padStart(2, "0")}</span>
                  <PlayerAvatar player={player} language={language} onCopy={() => copyIdentity(player)} copyTitle={identityTitle(player)} />
                <div className="player-info">
                  <div className="player-heading">
                    <h2 title={identityTitle(player)}>{player.name}</h2>
                    <LastOnline player={player} language={language} />
                  </div>
                  <p><span>{metricDetail(activeMetric, language)}</span></p>
                </div>
                <strong className="player-value">{player.formatted}</strong>
              </article>
            ))}
          </div>}
          {tileMode && !loading && !error && visiblePlayers.length === 0 && <div className="notice glass">{text.noPlayers}</div>}
        </section>
      </main>
    </div>
  );
}
