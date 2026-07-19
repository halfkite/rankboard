import { useEffect, useMemo, useState } from "react";
import { ExternalLink, Github, PackageOpen } from "lucide-react";
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
};

function PlayerAvatar({ player }: { player: Player }) {
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
      alt={`${player.name} 的头像`}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setSourceIndex((current) => current + 1)}
    />
  );
}

function formatLastOnline(player: Player) {
  if (player.online) return "当前在线";
  if (player.lastOnline <= 0) return "最后在线：未知";
  return `最后在线：${new Date(player.lastOnline).toLocaleString("zh-CN", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
  })}`;
}

const periods = [
  { id: "all", label: "总榜" },
  { id: "day", label: "最近一日" },
  { id: "week", label: "最近一周" },
  { id: "month", label: "最近一月" }
  , { id: "custom", label: "自定义日期" }
];

const metrics: Metric[] = [
  { id: "playtime", label: "在线时间", detail: "活跃度" },
  { id: "food", label: "大胃王", detail: "食物" },
  { id: "jumps", label: "跳跃榜", detail: "移动" },
  { id: "mined", label: "挖掘榜", detail: "资源" },
  { id: "placed", label: "放置榜", detail: "建造" },
  { id: "kills", label: "击杀榜", detail: "战斗" },
  { id: "deaths", label: "死亡榜", detail: "生存" },
  { id: "trades", label: "交易榜", detail: "经济" },
  { id: "elytra", label: "鞘翅飞行榜", detail: "探索" },
  { id: "fishing", label: "钓鱼榜", detail: "休闲" },
  { id: "damage", label: "受伤害榜", detail: "生存" },
  { id: "dealt", label: "伤害输出榜", detail: "战斗" }
];

const today = new Date().toISOString().slice(0, 10);

export default function App() {
  const [period, setPeriod] = useState("all");
  const [metric, setMetric] = useState("playtime");
  const [query, setQuery] = useState("");
  const [onlineOnly, setOnlineOnly] = useState(false);
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [serverName, setServerName] = useState("Minecraft Server");
  const [rankingRefreshIntervalSeconds, setRankingRefreshIntervalSeconds] = useState(30);
  const [ranking, setRanking] = useState<RankingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const loadRanking = async () => {
      setLoading(true);
      setError(null);
      try {
        const params = new URLSearchParams({ period, metric, online: String(onlineOnly) });
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
          throw new Error("服务器排行榜服务返回了无效数据");
        }
        if (!response.ok || !payload) throw new Error(payload?.error ?? "服务器排行榜服务未启动或不可访问");
        if (!cancelled) setRanking(payload);
      } catch (requestError) {
        if (!cancelled) {
          setRanking(null);
          setError(requestError instanceof Error ? requestError.message : "无法连接服务器");
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
  }, [period, metric, onlineOnly, from, to, rankingRefreshIntervalSeconds]);

  useEffect(() => {
    fetch("/api/site")
      .then((response) => response.ok ? response.json() as Promise<{ name?: string; rankingRefreshIntervalSeconds?: number }> : null)
      .then((site) => {
        if (site?.name) setServerName(site.name);
        if (site?.rankingRefreshIntervalSeconds) setRankingRefreshIntervalSeconds(site.rankingRefreshIntervalSeconds);
      })
      .catch(() => undefined);
  }, []);

  const activeMetric = metrics.find((item) => item.id === metric) ?? metrics[0];
  const visiblePlayers = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return ranking?.players ?? [];
    return (ranking?.players ?? []).filter((player) => player.name.toLowerCase().includes(keyword));
  }, [query, ranking]);

  return (
    <div className="app-shell">
      <header className="topbar glass">
        <div className="brand">
          <img src="/site-icon" onError={(event) => (event.currentTarget.src = "/server-theme.jpg")} alt="服务器图标" />
          <div>
            <BlurText text={serverName} delay={35} animateBy="letters" direction="top" className="brand-title" />
            <span>RankBoard · 服务器排行榜</span>
          </div>
        </div>
        <div className="top-status">
          <span className={ranking?.cacheReady ? "signal online" : "signal"} />
          {ranking?.onlineOnly ? "仅在线玩家" : "历史统计同步"}
        </div>
      </header>

      <main className="workspace">
        <aside className="sidebar glass">
          <section>
            <p className="section-label">统计周期</p>
            <div className="period-list">
              {periods.map((item) => (
                <button key={item.id} className={item.id === period ? "selected" : ""} onClick={() => setPeriod(item.id)}>
                  {item.label}
                </button>
              ))}
            </div>
          </section>

          <label className="online-toggle">
            <input type="checkbox" checked={onlineOnly} onChange={(event) => setOnlineOnly(event.target.checked)} />
            <span>仅显示当前在线玩家</span>
          </label>

          <section>
            <p className="section-label">榜单分类</p>
            <div className="metric-list">
              {metrics.map((item) => (
                <button key={item.id} className={item.id === metric ? "selected" : ""} onClick={() => setMetric(item.id)}>
                  <span>{item.label}</span>
                  <small>{item.detail}</small>
                </button>
              ))}
            </div>
          </section>

          <nav className="mod-links" aria-label="RankBoard 模组链接">
            <a href="https://modrinth.com/project/rankboard" target="_blank" rel="noreferrer">
              <PackageOpen aria-hidden="true" />
              <span>
                <small>下载模组</small>
                Modrinth
              </span>
              <ExternalLink className="external-icon" aria-hidden="true" />
            </a>
            <a href="https://github.com/halfkite/rankboard" target="_blank" rel="noreferrer">
              <Github aria-hidden="true" />
              <span>
                <small>查看源码</small>
                halfkite/rankboard
              </span>
              <ExternalLink className="external-icon" aria-hidden="true" />
            </a>
          </nav>
        </aside>

        <section className="content-area">
          <div className="toolbar glass">
            <label className="search-field">
              <span>搜索玩家</span>
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="输入玩家名称" />
            </label>
            <div className="total-block">
              <span>总和</span>
              <strong>{ranking?.formattedTotal ?? "--"}</strong>
            </div>
          </div>

          {period === "custom" && (
            <div className="date-range glass">
              <label>开始日期<input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} /></label>
              <label>结束日期<input type="date" value={to} min={from} max={today} onChange={(event) => setTo(event.target.value)} /></label>
              {ranking?.earliest && <span>最早可查：{ranking.earliest}</span>}
            </div>
          )}

          <div className="title-row">
            <div>
              <p className="eyebrow">{period === "custom" && ranking ? `${ranking.from} 至 ${ranking.to}` : periods.find((item) => item.id === period)?.label}</p>
              <h1>{ranking?.label ?? activeMetric.label}</h1>
            </div>
            {ranking?.cacheChecking && (
              <p className="sync-note">后台校验 {ranking.cacheProcessed}/{ranking.cacheTotal}</p>
            )}
          </div>

          {loading && <div className="notice glass">正在读取服务器排行榜...</div>}
          {error && <div className="notice error glass">{error}</div>}

          <div className="ranking-list">
            {!loading && !error && visiblePlayers.length === 0 && (
              <div className="notice glass">当前筛选下没有可显示的玩家。</div>
            )}
            {visiblePlayers.map((player) => (
              <article className="ranking-card glass" key={player.uuid}>
                <span className="rank-number">{String(player.rank).padStart(2, "0")}</span>
                <PlayerAvatar player={player} />
                <div className="player-info">
                  <div className="player-heading">
                    <h2>{player.name}</h2>
                    <span className={player.online ? "last-online online" : "last-online"}>{formatLastOnline(player)}</span>
                  </div>
                  <p><span>{activeMetric.detail}</span><code>UUID {player.uuid}</code></p>
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
