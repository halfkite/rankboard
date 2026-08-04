# RankBoard English Documentation

[中文](rankboard.md) | **English**

## Features

- **Multiple statistics**: Tracks food, jumps, mined and placed blocks, kills, PvP kills, deaths, trades, playtime, flight, fishing, damage taken and dealt, dropped and picked-up items, crafted items, and redstone components.
- **Trustworthy periods**: Marks incomplete periods and excludes missing boundaries or counters that moved backwards; new metrics begin at their first trustworthy baseline.
- **Time periods**: `daily`, `weekly`, `monthly`, `yearly`, and `all` separate short-term event results from long-term server records.
- **Offline data**: Reads vanilla `world/stats/*.json`, so historical scores remain available when a player is offline.
- **Caching and background updates**: Throttles the first scan to avoid startup stalls, then checks only changed files to reduce ongoing server work.
- **World data directory**: Persistent leaderboard state is stored under `world/data/rankboard/`; the legacy `world/data/rankboard_leaderboard.dat` is migrated safely at startup.
- **In-game display**: Personal and server-wide sidebars, carousel rotation, join restoration, and the look-up-plus-Shift menu let players check rankings without opening a browser.
- **Player context**: Cached avatars and last-online timestamps are joined by colors synchronized with each player's active personal board across rankings, chat, TAB, and overhead names. RankBoard does not take overhead-name control from existing teams.
- **Web dashboard**: Date ranges, online-only filtering, server icons, themes, and Modrinth/GitHub links make rankings easy to share outside the game.
- **Request protection**: IP-based progressive API cooldowns prevent refresh storms or abusive clients from consuming server resources; icons and static files have separate limits.
- **Whitelists**: Keep the server whitelist behavior or use a separate RankBoard list when only event members, staff, or selected players should appear.
- **Scoreboard cleanup**: Detects and removes scoreboard objectives left by other mods so the sidebar remains under RankBoard's control.

## Metrics

| Identifier | Default Name | Description |
| - | - | - |
| `food` | 大胃王榜 | Total food items consumed |
| `jumps` | 跳跃榜 | Jump count |
| `mined` | 挖掘榜 | Total blocks mined |
| `placed` | 放置榜 | Total blocks placed |
| `kills` | 击杀榜 | Total mob kills + player kills |
| `pvp` | PvP榜 | Player kills |
| `deaths` | 死亡榜 | Death count |
| `trades` | 交易榜 | Villager trade count |
| `playtime` | 在线榜 | Online time (displayed as h m) |
| `elytra` | 飞行榜 | Elytra flight distance (displayed as km) |
| `fishing` | 钓鱼榜 | Fish caught |
| `damage` | 受伤榜 | Damage taken (vanilla value / 10) |
| `dealt` | 输出榜 | Damage dealt (vanilla value / 10) |
| `dropped` | 丢垃圾榜 | Items dropped |
| `picked` | 拾荒榜 | Items picked up |
| `crafted` | 合成榜 | Items crafted |
| `redstone` | 红石大蛇榜 | Redstone components placed |

The `redstone` metric counts placed power, transmission, and mechanical components, including redstone dust, redstone torches, repeaters, comparators, observers, pistons, sticky pistons, dispensers, droppers, hoppers, levers, tripwire hooks, targets, daylight detectors, note blocks, redstone blocks, sculk sensors, calibrated sculk sensors, lightning rods, trapped chests, powered rails, detector rails, activator rails, rails, lecterns, jukeboxes, bells, redstone lamps, TNT, big dripleaves, crafters, command blocks, chain command blocks, repeating command blocks, and all wooden buttons, pressure plates, doors, trapdoors, fence gates, and every oxidized or waxed copper-bulb variant.

## Periods

| Identifier | Description |
| - | - |
| `daily` | Daily statistics |
| `weekly` | Weekly statistics |
| `monthly` | Monthly statistics |
| `yearly` | Yearly statistics |
| `all` | All-time cumulative |

## In-Game Operations

### Joining the Server

When a player joins, they see:

1. **Welcome message**: Shows the server name (configurable)
2. **Ranking menu**: Clickable buttons for scores, sidebar toggle, carousel, website link, and help
3. **Web hint** (off by default): Shows the web dashboard URL

### Using the Ranking Menu

Type `/leaderboard` to open a clickable menu:

- **[Query Scores]** — View all personal statistics
- **[Enable Sidebar] / [Disable Sidebar]** — Toggle the vanilla sidebar
- **[Enable Look+Sneak] / [Disable Look+Sneak]** — Toggle the look-up+Shift menu shortcut
- **[Carousel]** — Auto-rotate through all metrics for the current period
- **[Open Website]** — Open the web dashboard in a browser
- **[help]** — View help

Below the menu, each metric appears as a clickable button. Click one to display that metric on your sidebar.

### Viewing Personal Scores

```text
/leaderboard mine             Show all-time scores (overview)
/leaderboard mine all         Same as above
/leaderboard mine day         Show scores from the last day
/leaderboard mine week        Show scores from the last week
/leaderboard mine month       Show scores from the last month
```

The sidebar switches to an overview mode, and the chat shows each metric's score with clickable period buttons at the bottom.

### Sidebar Operations

```text
/leaderboard display show all playtime     Show all-time playtime sidebar
/leaderboard display show weekly kills     Show weekly kills sidebar
/leaderboard display on                    Restore the last sidebar before closing
/leaderboard display off                   Hide your sidebar
```

After closing, `display on` restores the previous state (personal board, overview, or carousel).

### Carousel Mode

Carousel automatically rotates through metrics at the configured interval (default 30 seconds).

```text
/leaderboard carousel true      Enable carousel
/leaderboard carousel false     Disable carousel
/leaderboard carousel status    Check carousel status
```

Operators can control whether the carousel title follows the current metric color:

```text
/leaderboard carousel color true     Title follows metric color
/leaderboard carousel color false    Title uses fixed aqua
/leaderboard carousel color status   Check color setting
```

### Look-Up + Sneak Menu

Enabled by default. When a player looks up (pitch above 60 degrees) and holds Shift, the ranking menu opens automatically. Players can disable it:

```text
/leaderboard lookmenu false     Disable your look-up+sneak menu
/leaderboard lookmenu true      Re-enable it
/leaderboard lookmenu status    Check status
```

### Chat Rankings

```text
/leaderboard all playtime               Show all-time playtime (top 10 by default)
/leaderboard weekly kills 20            Show top 20 weekly kills
/leaderboard monthly mined              Show monthly mining rankings
```

Results include the total, rank, player name, and score. Metric names and values are color-coded.

### Name Color Sync

When name colors are enabled (default), player names change color based on the metric they are viewing. This affects rankings, chat, TAB list, and overhead names.

```text
/leaderboard namecolor true             Enable everywhere
/leaderboard namecolor scoreboard-only  Only in rankings
/leaderboard namecolor false            Disable everywhere
/leaderboard namecolor status           Check current mode
```

Render mode can be `legacy` (nearest vanilla 16 colors) or `rgb` (exact RGB):

```text
/leaderboard config set player-name-color-render-mode rgb
```

### Help System

`/leaderboard help` shows grouped entry points that link to detailed help:

```text
/leaderboard help                       Main help menu
/leaderboard help player                Player commands
/leaderboard help scoreboard            Sidebar commands
/leaderboard help web                   Web and configuration
/leaderboard help admin                 Operator management (OP only)
/leaderboard help config                Full configuration reference (OP only)
```

Every command in the help system is clickable and fills the chat input when clicked.

## Commands

### Player Commands

| Command | Description |
| - | - |
| `/leaderboard` | Open the ranking menu |
| `/leaderboard help [group]` | Show command help |
| `/leaderboard <period> <metric> [limit]` | Show a ranking in chat |
| `/leaderboard mine <all\|day\|week\|month>` | Show personal statistic scores |
| `/leaderboard display show <period> <metric>` | Show personal client sidebar |
| `/leaderboard display on` | Restore the sidebar state saved before closing |
| `/leaderboard display off` | Hide personal client sidebar |
| `/leaderboard carousel <true\|false\|status>` | Toggle or inspect carousel rotation |
| `/leaderboard lookmenu <true\|false\|status>` | Toggle or inspect the look-up+sneak menu |

### Operator Commands

| Command | Description |
| - | - |
| `/leaderboard display show <period> <metric> <player>` | Show a personal sidebar for a player |
| `/leaderboard display off <player>` | Hide a player's personal sidebar |
| `/leaderboard displayfilter <metric> <true\|false\|status>` | Allow or block one metric from display |
| `/leaderboard scoreboard show <period> <metric>` | Set the server-wide vanilla sidebar |
| `/leaderboard scoreboard clear` | Clear the server-wide sidebar |
| `/leaderboard scoreboard cleanup` | Clear displayed scoreboards from other mods |
| `/leaderboard scoreboard blocking <true\|false\|status>` | Automatically block other-mod scoreboards |
| `/leaderboard whitelist <true\|false\|status>` | Filter rankings by the server whitelist |
| `/leaderboard botfilter <true\|false\|status>` | Filter players with a bot_ name prefix |
| `/leaderboard customfilter <true\|false\|status>` | Filter unrecognized historical players |
| `/leaderboard onlinefilter <true\|false\|status>` | Restrict rankings to online players |
| `/leaderboard modwhitelist add\|remove <name\|UUID>` | Manage the RankBoard-only whitelist |
| `/leaderboard modwhitelist list\|reload` | List or reload the RankBoard-only whitelist |
| `/leaderboard recipients <fake-only\|false\|whitelist\|blacklist\|status>` | Control which online players receive personal sidebar data |
| `/leaderboard cache <status\|reload>` | Inspect or reload historical-stat cache |
| `/leaderboard cache threads <0-256>` | Set scanner threads; 0 is automatic, capped at 50% of logical processors, and restarts a parallel scan |
| `/leaderboard cache threads status` | Show the configured and resolved scanner-thread counts |
| `/leaderboard lookup <UUID\|whitelist>` | Look up Mojang player names |
| `/leaderboard ratelimit clear` | Clear all web IP rate-limit history |
| `/leaderboard config <list\|reload\|get\|set>` | List, change, or reload settings |
| `/leaderboard lookmenu global <true\|false\|status>` | Operators toggle or inspect the global menu |
| `/leaderboard carousel color <true\|false\|status>` | Set carousel title to follow metric color or use fixed aqua |
| `/leaderboard namecolor <true\|false\|scoreboard-only\|status>` | Set or inspect the server-wide name-color mode |
| `/leaderboard color list` | List all metric colors |
| `/leaderboard color <metric>` | Open the bilingual clickable 16-color preset menu |
| `/leaderboard color <metric> <name\|#RRGGBB>` | Tab-complete an English color name or set a custom RGB value |
| `/leaderboard color reset <metric\|all>` | Restore one or all default colors |
| `/leaderboard label <metric> <name>` | Set a custom display name |
| `/leaderboard label list` | List all metric display names |
| `/leaderboard label reset <metric\|all>` | Restore one or all default names |
| `/leaderboard webtheme <icon\|blue\|rgb #RRGGBB\|true\|false\|status>` | Set web theme mode |
| `/leaderboard webswitch <name\|weight\|add\|remove\|list\|status>` | Manage the web server switcher |

`true/false` is the recommended syntax. The old `on/off` and `enable/disable` aliases remain available for compatibility.

## Configuration

Main configuration: `config/rankboard/rankboard.properties`

Web configuration: `config/rankboard/rankboard-web.properties`

### Main Configuration

```properties
# --- History ---
history-files-per-second=50                 # Files scanned per second per thread; total is this times resolved threads
history-scan-threads=0                      # Scanner threads; 0 is automatic, capped at 50% of processors

# --- Join ---
welcome-enabled=true                        # Send a welcome message on join
welcome-name=auto                           # Welcome name; auto reads server data
join-menu-enabled=true                      # Open the chat ranking menu on join
join-web-hint-enabled=false                 # Show the web-ranking address on join
website-button-enabled=true                 # Show [Open Website] in menu and help
web-public-address=                         # Website button address; blank defaults to http://127.0.0.1:8765

# --- Client Sidebar ---
restore-scoreboard-on-join=true             # Restore the player's previous personal sidebar
look-up-sneak-menu-enabled=true             # Open menu while looking up and holding Shift
carousel-enabled=true                       # Allow automatic ranking rotation
carousel-interval-seconds=30                # Carousel interval in seconds (range 3-3600)
carousel-color-follow-metric=true           # Carousel title follows current metric color; false uses fixed aqua
client-scoreboard-show-zero=false           # Show zero-value players
scoreboard-switch-message-enabled=true      # Send a message after switching rankings
scoreboard-name-color-enabled=true          # true=ranking/chat/TAB/overhead; false=off; scoreboard-only=ranking only
player-name-color-render-mode=legacy        # legacy=nearest vanilla color; rgb=exact RGB
scoreboard-title-color-enabled=true         # Color sidebar titles by metric
scoreboard-live-update-enabled=true         # Refresh rankings on statistic changes
scoreboard-live-update-window-seconds=30    # High-frequency detection window (range 1-300)
scoreboard-live-update-threshold=100        # Begin throttling after this count (range 1-100000)
scoreboard-live-update-throttle-seconds=30  # Minimum high-frequency refresh interval (range 1-3600)

# --- Filtering ---
foreign-scoreboard-blocking-mode=ask        # Other-mod scoreboard mode: ask/enabled/disabled
mod-whitelist-enabled=false                 # Read only from the RankBoard whitelist
scoreboard-recipient-filter=fake-only       # Personal sidebar data filter: fake-only/false/whitelist/blacklist
help-visibility=all                         # Help visibility: all/op/hidden
avatar-cache-enabled=true                   # Cache joined-player skin avatars
avatar-cache-days=7                         # Avatar cache retention in days (range 1-365)

# --- Metric Names ---
metric-label-food=大胃王榜
metric-label-jumps=跳跃榜
metric-label-mined=挖掘榜
metric-label-placed=放置榜
metric-label-kills=击杀榜
metric-label-pvp=PvP榜
metric-label-deaths=死亡榜
metric-label-trades=交易榜
metric-label-playtime=在线榜
metric-label-elytra=飞行榜
metric-label-fishing=钓鱼榜
metric-label-damage=受伤榜
metric-label-dealt=输出榜
metric-label-dropped=丢垃圾榜
metric-label-picked=拾荒榜
metric-label-crafted=合成榜
metric-label-redstone=红石大蛇榜

# --- Metric Colors ---
metric-color-food=#FFAA00
metric-color-jumps=#FF55FF
metric-color-mined=#5555FF
metric-color-placed=#00AAAA
metric-color-kills=#FF5555
metric-color-pvp=#AA0000
metric-color-deaths=#AA0000
metric-color-trades=#55FF55
metric-color-playtime=#55FFFF
metric-color-elytra=#FF55FF
metric-color-fishing=#0000AA
metric-color-damage=#FF5555
metric-color-dealt=#FFAA00
metric-color-dropped=#555555
metric-color-picked=#55FF55
metric-color-crafted=#FFAA00
metric-color-redstone=#FF5555
```

### Web Configuration

```properties
# --- Listening ---
host=0.0.0.0                              # Bind address; 0.0.0.0 accepts all connections
port=8765                                 # Port (range 1-65535)

# --- Display ---
server-name=auto                           # Server name on the web page; auto reads server data
website-icon=server-icon.png              # Server icon path

# --- Switcher ---
web-switcher-name=auto                     # Switcher button name; auto uses the web server name
web-switcher-weight=100                    # Lower values sort first; weight 1 is first
web-switcher-peers=                        # Other RankBoard addresses, comma-separated

# --- Rate Limiting ---
web-data-requests-per-second=1            # Per-IP base rate for data requests (range 1-100)
web-icon-request-interval-seconds=3       # Base image request interval in seconds

# --- Data ---
web-ranking-refresh-interval-seconds=30   # Full ranking refresh interval (range 1-3600)

# --- Theme ---
web-theme-follow-icon=true                # Derive palette from server icon
web-theme-base=auto                       # Base color; auto or #RRGGBB
web-theme-background=auto                 # Background color
web-theme-surface=auto                    # Panel color
web-theme-primary=auto                    # Primary color (buttons, selected items, key values)
web-theme-secondary=auto                  # Secondary color (rankings, status emphasis)
web-theme-text=auto                       # Main text color
web-theme-muted=auto                      # Secondary text color
web-theme-border=auto                     # Border color
web-theme-success=auto                    # Online status color
web-theme-danger=auto                     # Error/warning color
```

`website-icon` first reads from `config/rankboard/`; when the default icon is missing it falls back to `server-icon.png` in the server root. Absolute paths, traversal paths, and escaping symlinks are rejected.

Use `/leaderboard webtheme icon` for icon-derived colors, `/leaderboard webtheme blue` for the default blue palette, or `/leaderboard webtheme rgb #3F505E` for a custom RGB palette.

## Whitelist

Enable:

```text
/leaderboard config set mod-whitelist-enabled true
```

Whitelist file: `config/rankboard/rankboard-whitelist.json`

```json
[
  {"uuid": "00000000-0000-0000-0000-000000000000"},
  {"name": "PlayerName"}
]
```

When enabled, statistics scanning, caching, in-game rankings, and web rankings accept only listed players. The existing `whitelistOnly` setting remains active; when both lists are enabled, their intersection is used.

## Web Dashboard and Rate Limiting

Default address: `http://server-address:8765/`

Example endpoints:

```text
GET /api/rankings?metric=playtime&period=all
GET /api/rankings?metric=kills&period=week
GET /api/rankings?metric=playtime&from=2026-07-16&to=2026-07-20
```

`/api/rankings` and `/api/site` share a 30-second request count per IP. Data starts at one request per second; more than 30 requests in 30 seconds applies a five-second interval for 30 minutes. Images start at one request every three seconds; more than six requests in 30 seconds applies a 15-second interval for 30 minutes. Static web resources remain limited to one request per second.

Limited requests return HTTP `429` with `Retry-After`. Operators can clear all accumulated cooldowns with `/leaderboard ratelimit clear`.

## Multi-Server Web Switcher

Add RankBoard sites to the left-side switcher with `/leaderboard webswitch add <IP|host|URL>`. Addresses without a port inherit the current web port, and entries resolving to the same IP and port are merged.

- `/leaderboard webswitch name <name|auto>` sets this server's button name
- `/leaderboard webswitch weight <1-10000>` sets the sort order; weight `1` sorts first

## World Data

- Leaderboard history snapshots: `world/data/rankboard/`
- Player avatar cache: `world/data/rankboard/avatars/`
- Whitelist file: `config/rankboard/rankboard-whitelist.json`
- Main config: `config/rankboard/rankboard.properties`
- Web config: `config/rankboard/rankboard-web.properties`

The legacy `world/data/rankboard_leaderboard.dat` is automatically migrated at startup.

## Building

JDK 21 is required; JDK 25 is required for 26.x builds.

```text
gradlew.bat build
```

Artifacts are written to `build/libs/`. The JAR filename includes the mod and Minecraft versions.

Multi-version results are collected under `multi-version-builds/`; every successful build is also archived in a timestamped directory under `mod-builds/`.
