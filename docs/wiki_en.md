# RankBoard Wiki

[中文](wiki_zh.md) | [GitHub](https://github.com/halfkite/rankboard) | [Modrinth](https://modrinth.com/project/rankboard)

RankBoard is a server-side leaderboard mod. It reads vanilla statistics and provides chat queries, vanilla sidebar scoreboards, and a web leaderboard. Players normally do not need a client-side mod.

## Dependencies

| Name | Type | Notes |
| --- | --- | --- |
| Fabric API | Required for Fabric | Install the build matching the game version. |
| Fabric Loader | Required for Fabric | `0.15.11+` for 1.21; `0.18.6+` for 26.x. |
| NeoForge | Required for NeoForge | Install the exact NeoForge build for the game version. |
| Java | Required | Java 21+ for 1.21; Java 25+ for 26.x. |

## Version support

| Minecraft | Fabric | NeoForge | Notes |
| --- | --- | --- | --- |
| 1.21.1, 1.21.4, 1.21.8, 1.21.11 | Supported | Supported | Use the JAR built for the specific game version. |
| 1.21–1.21.11 | `1.21.x` wrapper | Depends on the release bundle | The Fabric wrapper selects a bundled compatible implementation. |
| 26.1, 26.1.1, 26.1.2 | `26.1.x` wrapper | Supported | Java 25 is required. |
| 26.2 | Separate JAR | Supported | Do not mix it with the 26.1.x package. |

> Install exactly one RankBoard JAR per server. Remove legacy `rankboard_wrapper` files and JARs built for a different Minecraft version to avoid loading the wrong implementation.

## Installation

1. Install the matching loader and dependency.
2. Put the RankBoard JAR in the server `mods/` directory.
3. Start the server once to create the configuration directory.
4. Restart after editing configuration, or run `/leaderboard config reload`.

All configuration and cache files are stored in:

```text
<server directory>/config/rankboard/
```

| File | Purpose |
| --- | --- |
| `rankboard.properties` | Rankings, sidebars, player experience, and history scanning. |
| `rankboard-web.properties` | Web server, theme, icon, rate limit, and site-switcher settings. |
| `rankboard-whitelist.json` | RankBoard-only whitelist. |
| `server-icon.png` | Web server icon; takes priority over the server-root icon. |
| `rankboard-history-cache.json` | History cache managed by the mod. |

Legacy configuration is imported into `config/rankboard/` once. A completed migration writes `read-legacy-config=false`, so old files are not repeatedly imported.

## Leaderboards

Every metric supports `daily`, `weekly`, `monthly`, `yearly`, and `all` periods.

| Command key | Default label | Statistic |
| --- | --- | --- |
| `food` | Food | Food items used. |
| `jumps` | Jumps | Number of jumps. |
| `mined` | Mining | Registered blocks mined. |
| `placed` | Placing | Blocks placed. |
| `kills` | Kills | Mob kills. |
| `pvp` | PvP | Player kills. |
| `deaths` | Deaths | Death count. |
| `trades` | Trades | Villager interaction/trade statistic. |
| `playtime` | Playtime | Time played. |
| `elytra` | Flight | Elytra flight distance. |
| `fishing` | Fishing | Fishing count. |
| `damage` | Damage Taken | Damage received, displayed as one tenth of the vanilla value. |
| `dealt` | Damage Dealt | Damage dealt, displayed as one tenth of the vanilla value. |
| `dropped` | Dropped Items | Items dropped. |
| `picked` | Picked Up Items | Items picked up. |
| `crafted` | Crafted Items | Items crafted. |
| `redstone` | Redstone | Redstone components placed. |

Operators can change a display label with `/leaderboard label` and a metric color with `/leaderboard color`. A metric disabled by an operator is hidden consistently from the menu, website, and API.

## Player commands

```text
/leaderboard
/leaderboard help
/leaderboard <daily|weekly|monthly|yearly|all> <metric> [limit]
/leaderboard mine [all|day|week|month]
/leaderboard display show <period> <metric>
/leaderboard display on
/leaderboard display off
/leaderboard carousel <true|false|status>
/leaderboard lookmenu <true|false|status>
```

- `/leaderboard` opens the six-row clickable menu.
- `/leaderboard mine` shows all of the caller's statistics and opens the personal overview; first use defaults to the all-time period.
- `display off` hides a board without erasing the last choice. `display on` restores the saved single board, overview, or carousel state.
- Looking upward while holding Shift opens the menu; each player can disable this shortcut.
- Carousel and personal overview are mutually exclusive. Carousel titles and player names can use the carousel color.

## Operator administration

```text
/leaderboard scoreboard show <period> <metric>
/leaderboard scoreboard clear
/leaderboard scoreboard cleanup
/leaderboard scoreboard blocking <true|false|status>
/leaderboard displayfilter <metric> <true|false|status>
/leaderboard cache <status|reload>
/leaderboard cache threads <0-256|status>
/leaderboard whitelist <true|false|status>
/leaderboard modwhitelist <add|remove|list|reload> [name|UUID]
/leaderboard config <list|reload|get|set>
/leaderboard ratelimit clear
/leaderboard namecolor <true|false|scoreboard-only|status>
/leaderboard color <list|metric|reset> ...
/leaderboard label <list|metric|reset> ...
```

`true` and `false` are the recommended values. The legacy `on/off` and `enable/disable` aliases remain available for compatibility.

### History scanning

RankBoard reads `world/stats/*.json`, so scores remain available while a player is offline. The initial scan runs in the background and cached leaderboards remain available during the scan.

```properties
history-scan-threads=0
history-files-per-second=50
```

`history-scan-threads=0` selects a thread count automatically, capped at 50% of logical processors. `history-files-per-second` is the limit per scanning thread; the total read limit is their product.

If a period boundary is missing, a cumulative statistic moved backwards, or a new metric has no trustworthy baseline, RankBoard marks the result as partial instead of treating missing history as zero.

## Web leaderboard

Default address:

```text
http://<server address>:8765/
```

Useful web settings:

```properties
host=0.0.0.0
port=8765
server-name=auto
website-icon=server-icon.png
web-theme-follow-icon=true
web-theme-base=auto
web-public-address=
web-switcher-name=auto
web-switcher-weight=100
web-switcher-peers=
```

- `config/rankboard/server-icon.png` has priority; the server-root `server-icon.png` is the fallback.
- The icon receives a content version hash and is cached by the browser. Replacing it automatically produces a new URL.
- `web-theme-follow-icon=true` derives a palette from the icon. Theme fields also accept `#RRGGBB` values.
- `/leaderboard webtheme blue` restores the blue palette, `/leaderboard webtheme icon` uses icon colors, and `/leaderboard webtheme rgb #3F505E` applies a custom palette.
- `/leaderboard webswitch add <address>` adds another RankBoard site. Equal IP-and-port pairs are merged automatically.

Common API endpoints:

```text
GET /api/site
GET /api/sites
GET /api/rankings?metric=playtime&period=all
GET /api/rankings?metric=kills&period=week
```

Data and icon requests have separate IP-based rate limits. A limited request returns HTTP `429` with `Retry-After`; an operator can clear accumulated limits with `/leaderboard ratelimit clear`.

## Whitelists and filters

```properties
mod-whitelist-enabled=false
```

When enabled, only players in `rankboard-whitelist.json` participate in history scanning, in-game menus, web lists, and API responses. The vanilla server whitelist and the RankBoard whitelist can both be enabled; then a player must satisfy both.

## Troubleshooting

### Player avatars or the web icon are missing

Check that `config/rankboard/server-icon.png` exists and is a valid PNG. The browser cache is intentional: replacing the image produces a new versioned URL after the web service reloads.

### A leaderboard says “partial statistics”

The selected range has no trustworthy start or end snapshot. This is normal after a new installation, an upgrade that adds a metric, or incomplete historic statistics. Later complete periods will be shown normally after new boundaries are recorded.

### Another mod owns the sidebar

Use `/leaderboard scoreboard cleanup` to remove leftover objectives. Enable `/leaderboard scoreboard blocking true` when RankBoard should actively suppress other-mod scoreboards.

### Refreshing the website triggers a limit

Data, icon, and static resources have different rate-limit policies. Do not disable browser caching: versioned icon URLs allow normal refreshes without redownloading an unchanged icon.

## License

RankBoard is licensed under the [MIT License](../LICENSE).
