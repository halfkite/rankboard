import { useEffect, useMemo, useState } from "react";
import { Download, ExternalLink, Github, LayoutPanelTop, PackageOpen, Server } from "lucide-react";
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
    language: "中文", currentOnline: "当前在线", lastOnlineUnknown: "最后在线：未知", lastOnline: "最后在线",
    total: "总和", query: "查询", all: "总榜", day: "最近一日", week: "最近一周", month: "最近一月", custom: "自定义日期",
    compact: "紧凑视图", normal: "普通视图", switchServer: "服务器切换", current: "当前", switch: "切换", offline: "离线",
    statisticsPeriod: "统计周期", onlineOnly: "仅显示当前在线玩家", metricCategory: "榜单分类", downloadMod: "下载模组", sourceCode: "查看源码",
    searchPlayer: "搜索玩家", enterPlayerName: "输入玩家名称", export: "导出表格", exportTitle: "导出当前筛选结果为 CSV 表格",
    startDate: "开始日期", endDate: "结束日期", earliest: "最早可查", backgroundCheck: "后台校验", loading: "正在读取服务器排行榜...",
    noPlayers: "当前筛选下没有可显示的玩家。", partial: "部分统计", incompleteRange: "统计范围缺少完整边界",
    historySync: "历史统计同步", onlyOnline: "仅在线玩家", rank: "排名", playerName: "玩家名称", uuid: "UUID", value: "数值",
    serverIcon: "服务器图标", websiteAria: "RankBoard 模组链接", allMetrics: "的全部榜单数值", noMetric: "无可用榜单",
    playtimeDetail: "活跃度", foodDetail: "食物", jumpsDetail: "移动", minedDetail: "资源", placedDetail: "建造",
    killsDetail: "战斗", pvpDetail: "玩家对战", deathsDetail: "生存", tradesDetail: "经济", elytraDetail: "探索",
    fishingDetail: "休闲", damageDetail: "生存", dealtDetail: "战斗", droppedDetail: "物品", pickedDetail: "物品",
    craftedDetail: "制造", redstoneDetail: "红石", modTitle: "RankBoard排行榜模组"
  },
  en: {
    language: "English", currentOnline: "Online now", lastOnlineUnknown: "Last online: unknown", lastOnline: "Last online",
    total: "Total", query: "Query", all: "All time", day: "Last day", week: "Last week", month: "Last month", custom: "Custom dates",
    compact: "Compact view", normal: "Normal view", switchServer: "Server switcher", current: "Current", switch: "Switch", offline: "Offline",
    statisticsPeriod: "Statistics period", onlineOnly: "Show online players only", metricCategory: "Leaderboards", downloadMod: "Download mod", sourceCode: "View source",
    searchPlayer: "Search players", enterPlayerName: "Enter a player name", export: "Export table", exportTitle: "Export the filtered result as a CSV table",
    startDate: "Start date", endDate: "End date", earliest: "Earliest available", backgroundCheck: "Background verification", loading: "Loading server leaderboard...",
    noPlayers: "No players are available for the current filters.", partial: "Partial statistics", incompleteRange: "The selected range has incomplete boundaries",
    historySync: "History synchronized", onlyOnline: "Online players only", rank: "Rank", playerName: "Player name", uuid: "UUID", value: "Value",
    serverIcon: "Server icon", websiteAria: "RankBoard mod links", allMetrics: "'s leaderboard values", noMetric: "No available leaderboard",
    playtimeDetail: "Activity", foodDetail: "Food", jumpsDetail: "Movement", minedDetail: "Resources", placedDetail: "Building",
    killsDetail: "Combat", pvpDetail: "Player combat", deathsDetail: "Survival", tradesDetail: "Economy", elytraDetail: "Exploration",
    fishingDetail: "Leisure", damageDetail: "Survival", dealtDetail: "Combat", droppedDetail: "Items", pickedDetail: "Items",
    craftedDetail: "Crafting", redstoneDetail: "Redstone", modTitle: "RankBoard Leaderboard Mod"
  }
};

const METRIC_TEXT: Record<string, { zh: string; en: string }> = {
  playtime: { zh: "在线时间", en: "Playtime" }, food: { zh: "大胃王", en: "Food eater" }, jumps: { zh: "跳跃榜", en: "Jumps" },
  mined: { zh: "挖掘榜", en: "Mining" }, placed: { zh: "放置榜", en: "Placing" }, kills: { zh: "击杀榜", en: "Kills" },
  pvp: { zh: "PvP榜", en: "PvP" }, deaths: { zh: "死亡榜", en: "Deaths" }, trades: { zh: "交易榜", en: "Trades" },
  elytra: { zh: "鞘翅飞行榜", en: "Flight" }, fishing: { zh: "钓鱼榜", en: "Fishing" }, damage: { zh: "受伤榜", en: "Damage taken" },
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

function PlayerAvatar({ player, language }: { player: Player; language: Language }) {
  const [sourceIndex, setSourceIndex] = useState(0);
  const uuid = player.uuid.replaceAll("-", "");
  const sources = [
    `/avatar/${player.uuid}`,
    `https://crafthead.net/avatar/${uuid}/64`,
    `https://minotar.net/avatar/${encodeURIComponent(player.name)}/64`
  ];

  if (sourceIndex >= sources.length) {
    return <span className="avatar avatar-fallback" aria-hidden="true">{player.name.slice(0, 1).toUpperCase()}</span>;
  }

  return (
    <img
      className="avatar"
      src={sources[sourceIndex]}
      alt={language === "zh" ? `${player.name} 的头像` : `${player.name}'s avatar`}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setSourceIndex((current) => current + 1)}
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
  { id: "food", label: "大胃王榜", detail: "食物" },
  { id: "jumps", label: "跳跃榜", detail: "移动" },
  { id: "mined", label: "挖掘榜", detail: "资源" },
  { id: "placed", label: "放置榜", detail: "建造" },
  { id: "kills", label: "击杀榜", detail: "战斗" },
  { id: "pvp", label: "PvP榜", detail: "玩家对战" },
  { id: "deaths", label: "死亡榜", detail: "生存" },
  { id: "trades", label: "交易榜", detail: "经济" },
  { id: "elytra", label: "飞行榜", detail: "探索" },
  { id: "fishing", label: "钓鱼榜", detail: "休闲" },
  { id: "damage", label: "受伤榜", detail: "生存" },
  { id: "dealt", label: "输出榜", detail: "战斗" },
  { id: "dropped", label: "丢垃圾榜", detail: "物品" },
  { id: "picked", label: "拾荒榜", detail: "物品" },
  { id: "crafted", label: "合成榜", detail: "制造" },
  { id: "redstone", label: "红石大蛇榜", detail: "红石" }
];

const today = new Date().toISOString().slice(0, 10);

function periodLabel(id: string, language: Language) {
  return UI_TEXT[language][id] ?? id;
}

function metricLabel(item: Metric, language: Language) {
  const translated = METRIC_TEXT[item.id];
  const defaultItem = defaultMetrics.find((candidate) => candidate.id === item.id);
  return translated && defaultItem?.label === item.label ? translated[language] : item.label;
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

function explicitLanguage(): Language | null {
  const queryLanguage = languageFromCode(new URLSearchParams(window.location.search).get("lang") ?? undefined);
  if (queryLanguage) return queryLanguage;
  return languageFromCode(localStorage.getItem("rankboard-language") ?? undefined);
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
  const [compactMode, setCompactMode] = useState(() => localStorage.getItem("rankboard-compact-mode") === "true");
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
        const params = new URLSearchParams({ period, metric, online: String(onlineOnly), compact: String(compactMode) });
        if (period === "custom") {
          params.set("from", from);
          params.set("to", to);
        }
        const response = await fetch(`/api/rankings?${params}`);
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
          setMetrics((items) => items.map((item) => item.id === metric
            ? { ...item, label: payload.label }
            : item));
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
  }, [period, metric, onlineOnly, from, to, compactMode, rankingRefreshIntervalSeconds, language]);

  useEffect(() => {
    localStorage.setItem("rankboard-compact-mode", String(compactMode));
  }, [compactMode]);

  useEffect(() => {
    applyTheme(siteTheme, iconColor);
  }, [siteTheme, iconColor]);

  useEffect(() => {
    fetch("/api/site")
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
    const loadSites = () => fetch("/api/sites")
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
    const iconUrl = `/site-icon/header?v=${encodeURIComponent(iconVersion)}`;

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
    const metricColumns = compactMode ? metrics : [activeMetric];
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

  return (
    <div className="app-shell">
      <header className="topbar glass">
        <div className="brand">
            {iconSource ? <img src={iconSource} onLoad={(event) => setIconColor(iconAverage(event.currentTarget))}
             alt={text.serverIcon} /> : <span className="brand-icon-placeholder">RB</span>}
          <BlurText text={serverName} delay={35} animateBy="letters" direction="top" className="brand-title" />
        </div>
        <div className="top-status">
          <strong>{text.modTitle}</strong>
          <span className={ranking?.cacheReady ? "signal online" : "signal"} />
          {ranking?.onlineOnly ? text.onlyOnline : text.historySync}
        </div>
        <button
          className="language-toggle"
          type="button"
          onClick={() => setLanguage((current) => current === "zh" ? "en" : "zh")}
          aria-label={text.language}
        >
          Language: {text.language}
        </button>
        <button
          className={compactMode ? "layout-toggle selected" : "layout-toggle"}
          type="button"
          aria-pressed={compactMode}
          title={compactMode ? text.normal : text.compact}
          onClick={() => setCompactMode((enabled) => !enabled)}
        >
          <LayoutPanelTop aria-hidden="true" />
          <span>{compactMode ? text.normal : text.compact}</span>
        </button>
      </header>

      <main className="workspace">
        <aside className="sidebar glass">
          {sites.length > 1 && (
            <section className="server-switcher">
              <p className="section-label">{text.switchServer}</p>
              <div className="server-list">
                {sites.map((site) => (
                  <button
                    key={`${site.url}-${site.weight}`}
                    className={site.current ? "selected" : ""}
                    disabled={site.current || !site.online}
                    title={site.online ? site.url : `${site.url} (${text.offline})`}
                    onClick={() => window.location.assign(site.url)}
                  >
                    <Server aria-hidden="true" />
                    <span>{site.name}</span>
                    <small>{site.current ? text.current : site.online ? text.switch : text.offline}</small>
                  </button>
                ))}
              </div>
            </section>
          )}
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

          <label className="online-toggle">
            <input type="checkbox" checked={onlineOnly} onChange={(event) => setOnlineOnly(event.target.checked)} />
            <span>{text.onlineOnly}</span>
          </label>

          <section>
            <p className="section-label">{text.metricCategory}</p>
            <div className="metric-list">
              {metrics.map((item) => (
                <button key={item.id} className={item.id === metric ? "selected" : ""} onClick={() => setMetric(item.id)}>
                  <span>{metricLabel(item, language)}</span>
                  <small>{metricDetail(item, language)}</small>
                </button>
              ))}
            </div>
          </section>

          <nav className="mod-links" aria-label={text.websiteAria}>
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
        </aside>

        <section className="content-area">
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

          <div className={compactMode ? "ranking-list compact-list" : "ranking-list"}>
            {!loading && !error && visiblePlayers.length === 0 && (
              <div className="notice glass">{text.noPlayers}</div>
            )}
            {visiblePlayers.map((player) => compactMode ? (
              <article className="ranking-card compact-card glass" key={player.uuid}>
                <div className="compact-player">
                  <span className="rank-number">{String(player.rank).padStart(2, "0")}</span>
                  <PlayerAvatar player={player} language={language} />
                  <div className="player-info">
                    <div className="player-heading">
                      <h2>{player.name}</h2>
                      <span className={player.online ? "last-online online" : "last-online"}>{formatLastOnline(player, language)}</span>
                    </div>
                    <p><code>UUID {player.uuid}</code></p>
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
                  <PlayerAvatar player={player} language={language} />
                <div className="player-info">
                  <div className="player-heading">
                    <h2>{player.name}</h2>
                      <span className={player.online ? "last-online online" : "last-online"}>{formatLastOnline(player, language)}</span>
                  </div>
                    <p><span>{metricDetail(activeMetric, language)}</span><code>{text.uuid} {player.uuid}</code></p>
                </div>
                <strong className="player-value">{player.formatted}</strong>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
