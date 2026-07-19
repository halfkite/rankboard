# RankBoard

## 中文

RankBoard 是一个 Fabric 服务端排行榜模组。玩家不需要安装客户端模组，即可使用原版计分板和网页查看排行榜。

当前版本：`1.5.1`

### 功能与用途

- **多项统计**：记录食物、跳跃、挖掘、放置、击杀、死亡、交易、在线时间、鞘翅飞行、钓鱼、受伤害、丢弃、拾取、合成和红石元件放置，用于制作生存、活动和竞技排行榜。
- **时间周期**：提供 `daily`、`weekly`、`monthly`、`yearly` 和 `all`，方便分别查看短期活动成绩和服务器长期记录。
- **离线数据读取**：直接读取原版 `world/stats/*.json`，即使玩家当前不在线，也能保留并查询历史成绩。
- **缓存与后台更新**：首次加载时控制读取速度，避免启动卡顿；之后只检查变化的文件，降低服务器持续开销。
- **游戏内展示**：个人榜、全服榜、轮播榜和进服恢复让玩家无需打开网页即可查看排名；抬头加 Shift 可快速打开查询菜单。
- **玩家信息**：缓存头像、显示最后在线时间，并按玩家正在查看的个人榜单同步排行榜、聊天、TAB 与头顶名牌颜色。已有其他队伍的玩家不会被 RankBoard 抢占头顶名牌效果。
- **网页排行榜**：提供日期范围、在线筛选、服务器图标、主题配色及 Modrinth/GitHub 链接，适合分享给不在游戏中的玩家。
- **请求限流**：API 按 IP 逐渐增加冷却时间，防止网页刷新或异常客户端占满服务器资源；图标和静态文件使用独立限制。
- **白名单**：可沿用服务器白名单，也可单独指定 RankBoard 统计对象，用于只展示活动成员、工作人员或指定玩家。
- **计分板清理**：检测并清除其他模组留下的计分板目标，避免多个模组争用侧边栏。

### 安装

1. 安装对应 Minecraft 版本的 Fabric Loader 和 Fabric API。
2. 将 JAR 放入服务器 `mods/` 目录。
3. 启动服务器一次，生成 `config/rankboard/` 配置目录。
4. 修改配置后重启，或使用 `/leaderboard config reload`。

1.21 系列需要 Fabric Loader `0.15.11` 或更高版本，Java 21 或更高版本。

26.x 系列需要 Fabric Loader `0.18.6` 或更高版本，Java 25 或更高版本。

### 常用命令

普通玩家：

```text
/leaderboard                                         换出聊天栏面板
/leaderboard help                                    获取指令帮助
/leaderboard <daily|weekly|monthly|yearly|all> <metric> [limit] 选择指定的日/周/月/年/总排行榜
/leaderboard mine <all|day|week|month>               查询自己的全部统计分数
/leaderboard display show <period> <metric>          显示自己的客户端原版侧边栏
/leaderboard display off                             关闭自己的客户端侧边栏
/leaderboard carousel <true|false|status>            开关或查询自己的榜单轮播
/leaderboard lookmenu <true|false|status>            开关或查询自己的抬头+蹲起菜单
```

OP：

```text
/leaderboard display show <周期> <榜单> <玩家>        为指定玩家显示个人侧边栏
/leaderboard display off <玩家>                      关闭指定玩家的个人侧边栏
/leaderboard displayfilter <榜单> <true|false|status> 管理单个榜单是否允许玩家显示
/leaderboard scoreboard show <周期> <榜单>           设置全服共享的原版侧边栏
/leaderboard scoreboard clear                         关闭全服共享侧边栏
/leaderboard scoreboard cleanup                       清理其他模组正在显示的计分板
/leaderboard scoreboard blocking <true|false|status>  自动屏蔽其他模组计分板
/leaderboard whitelist <true|false|status>           使用服务器白名单筛选排行榜
/leaderboard botfilter <true|false|status>           过滤名称以 bot_ 开头的玩家
/leaderboard customfilter <true|false|status>        过滤无法识别身份的历史玩家
/leaderboard onlinefilter <true|false|status>        只显示当前在线玩家
/leaderboard modwhitelist add <name|UUID>            添加 RankBoard 独立白名单成员
/leaderboard modwhitelist remove <name|UUID>         移除 RankBoard 独立白名单成员
/leaderboard modwhitelist list|reload                查看或重新读取独立白名单
/leaderboard cache <status|reload>                   查看或重新加载历史统计缓存
/leaderboard lookup <UUID|whitelist>                 查询 Mojang UUID 对应的玩家名
/leaderboard ratelimit clear                          清空全部网页 IP 限流与累计冷却
/leaderboard config <list|reload|get|set>            查看、修改或重载配置
/leaderboard lookmenu global <true|false|status>     OP 开关或查询全服抬头+蹲起菜单
/leaderboard namecolor <true|false|scoreboard-only|status> 设置或查询全服名字颜色模式
/leaderboard color list                              列出所有榜单颜色
/leaderboard color <metric>                          打开英中双语的 16 色点击预选菜单
/leaderboard color <metric> <颜色名|#RRGGBB>         使用 Tab 补全英文颜色名，或设置自定义 RGB
/leaderboard color reset <metric|all>                恢复单个或全部默认颜色
/leaderboard label <metric> <名称>                   自定义榜单显示名称，例如 placed py榜
/leaderboard label list                              查看所有榜单显示名称
/leaderboard label reset <metric|all>                恢复单个或全部默认名称
```

`true/false` 是推荐的新语法。旧的 `on/off` 和 `enable/disable` 仍作为兼容别名保留。

### 配置

主配置：`config/rankboard/rankboard.properties`

网页配置：`config/rankboard/rankboard-web.properties`

主配置项：

```properties
history-files-per-second=50                 # 每秒读取的历史统计文件数量
welcome-enabled=true                        # 玩家进服时发送欢迎语
welcome-name=auto                           # 欢迎语服务器名；auto 自动读取
join-menu-enabled=true                      # 玩家进服时换出聊天栏面板
join-web-hint-enabled=false                 # 玩家进服时提示网页排行榜地址
web-public-address=                         # 网站按钮/进服提示地址；留空默认 http://127.0.0.1:8765，可用指令修改
restore-scoreboard-on-join=true             # 恢复玩家上次选择的客户端计分板
look-up-sneak-menu-enabled=true             # 抬头并按住 Shift 换出聊天栏面板
carousel-enabled=true                       # 允许玩家开启榜单自动轮播
carousel-interval-seconds=30                # 榜单轮播切换间隔，单位秒
client-scoreboard-show-zero=false           # 客户端计分板显示数值为 0 的玩家
scoreboard-switch-message-enabled=true      # 切换榜单后发送提示消息
scoreboard-name-color-enabled=true          # true=排行榜/聊天/TAB/头顶；false=关闭；scoreboard-only=仅排行榜
player-name-color-render-mode=legacy        # legacy=最近原版16色；rgb=排行榜/聊天/TAB精确RGB
metric-color-food=#FFAA00                   # 大胃王榜颜色；其他榜单同样使用 metric-color-<metric>
metric-color-jumps=#FF55FF
metric-color-mined=#5555FF                  # 挖掘榜：蓝色
metric-color-placed=#00AAAA                 # 放置榜：深青色
metric-color-kills=#FF5555
metric-color-deaths=#AA0000
metric-color-trades=#55FF55                 # 交易榜：绿色
metric-color-playtime=#55FFFF               # 在线榜：青色
metric-color-elytra=#FF55FF
metric-color-fishing=#0000AA                # 钓鱼榜：深蓝色
metric-color-damage=#FF5555                 # 受伤榜：红色
metric-color-dropped=#555555
metric-color-picked=#55FF55
metric-color-crafted=#FFAA00
metric-color-redstone=#FF5555
metric-label-placed=放置榜                   # 游戏菜单、计分板标题和网页结果中显示的名称
metric-label-playtime=在线榜
metric-label-elytra=飞行榜
metric-label-damage=受伤榜
metric-label-dropped=丢垃圾榜
metric-label-picked=拾荒榜
metric-label-crafted=合成榜
metric-label-redstone=红石大蛇榜
scoreboard-title-color-enabled=true         # 计分板标题跟随榜单颜色
scoreboard-live-update-enabled=true         # 玩家行为改变统计时实时刷新榜单
scoreboard-live-update-window-seconds=30    # 高频行为检测时间窗口，单位秒
scoreboard-live-update-threshold=100        # 窗口内超过此次数后降低刷新频率
scoreboard-live-update-throttle-seconds=30  # 高频时最短刷新间隔，单位秒
foreign-scoreboard-blocking-mode=ask        # 其他模组计分板：ask/enabled/disabled
mod-whitelist-enabled=false                 # 只读取 RankBoard 独立白名单中的玩家
help-visibility=all                         # 帮助可见范围：all/op/hidden
avatar-cache-enabled=true                   # 缓存进服玩家的皮肤头像
avatar-cache-days=7                         # 玩家头像缓存保留天数
```

`redstone` 红石大蛇榜统计红石粉、火把、中继器、比较器、按钮、拉杆、压力板、探测铁轨、绊线钩、讲台、标靶、幽匿感测体、活塞、各类铁轨、钟、发射器、投掷器、门、活板门、栅栏门、漏斗、音符盒、红石灯、TNT、大型垂滴叶、合成器、命令方块，以及所有氧化和涂蜡状态的铜灯等可放置元件。

网页配置项：

```properties
host=0.0.0.0                              # 网页服务监听地址；0.0.0.0 接受所有连接
port=8765                                 # 网页服务端口
web-data-requests-per-second=1            # 单个 IP 的数据请求基础频率
web-icon-request-interval-seconds=3       # 图片基础请求间隔，单位秒
web-ranking-refresh-interval-seconds=30   # 网页排行榜整体刷新间隔，单位秒
server-name=auto                           # 网页显示的服务器名；auto 自动读取
website-icon=server-icon.png              # 服务器图标；只能放在 config/rankboard/
```

`website-icon` 只能读取 `config/rankboard/` 目录内的文件。绝对路径、越界路径和目录外符号链接都会被拒绝。

### RankBoard 白名单

启用配置：

```text
/leaderboard config set mod-whitelist-enabled true
```

白名单文件：`config/rankboard/rankboard-whitelist.json`

示例：

```json
[
  {"uuid": "00000000-0000-0000-0000-000000000000"},
  {"name": "PlayerName"}
]
```

启用后，统计文件扫描、缓存、游戏榜单和网页榜单只接受该文件中的玩家。原有服务器 `whitelistOnly` 配置仍然有效；两个白名单同时开启时取交集。

### 网页与限流

默认地址：`http://服务器地址:8765/`

接口示例：

```text
GET /api/rankings?metric=playtime&period=all
GET /api/rankings?metric=kills&period=week
GET /api/rankings?metric=playtime&from=2026-07-16&to=2026-07-20
```

数据接口 `/api/rankings` 和 `/api/site` 共享同一 IP 的 30 秒请求计数。默认每秒 1 次；30 秒内超过 30 次后，固定 30 分钟改为每 5 秒 1 次。图片默认每 3 秒 1 次；30 秒内超过 6 次后，固定 30 分钟改为每 15 秒 1 次。静态网页资源仍为每秒 1 次。

超过限制返回 HTTP `429` 和 `Retry-After`。OP 可以使用 `/leaderboard ratelimit clear` 清除所有累计冷却。

### 构建

需要 JDK 21；构建 26.x 需要 JDK 25。

```text
gradlew.bat build
```

构建产物位于 `build/libs/`。发布版本和 Minecraft 版本会写入 JAR 文件名。

多版本构建结果位于 `multi-version-builds/`，每次成功构建也会单独归档到 `mod-builds/` 的时间戳目录。

---

## English

RankBoard is a server-side Fabric leaderboard mod. Players do not need a client-side mod to use the vanilla sidebar or the web dashboard.

Current version: `1.5.1`

### Features and purpose

- **Multiple statistics**: Tracks food, jumps, mined and placed blocks, kills, deaths, trades, playtime, elytra distance, fishing, damage, dropped and picked-up items, crafted items, and placed redstone components.
- **Time periods**: `daily`, `weekly`, `monthly`, `yearly`, and `all` separate short-term event results from long-term server records.
- **Offline data**: Reads vanilla `world/stats/*.json`, so historical scores remain available when a player is offline.
- **Caching and background updates**: Throttles the first scan to avoid startup stalls, then checks only changed files to reduce ongoing server work.
- **In-game display**: Personal and server-wide sidebars, carousel rotation, join restoration, and the look-up-plus-Shift menu let players check rankings without opening a browser.
- **Player context**: Cached avatars and last-online timestamps are joined by colors synchronized with each player's active personal board across rankings, chat, TAB, and overhead names. RankBoard does not take overhead-name control from existing teams.
- **Web dashboard**: Date ranges, online-only filtering, server icons, themes, and Modrinth/GitHub links make rankings easy to share outside the game.
- **Request protection**: IP-based progressive API cooldowns prevent refresh storms or abusive clients from consuming server resources; icons and static files have separate limits.
- **Whitelists**: Keep the server whitelist behavior or use a separate RankBoard list when only event members, staff, or selected players should appear.
- **Scoreboard cleanup**: Detects and removes scoreboard objectives left by other mods so the sidebar remains under RankBoard's control.

### Installation

1. Install Fabric Loader and Fabric API for the target Minecraft version.
2. Put the JAR in the server `mods/` directory.
3. Start the server once to create `config/rankboard/`.
4. Restart after editing configuration, or run `/leaderboard config reload`.

Minecraft 1.21 releases require Fabric Loader `0.15.11+` and Java 21+.

Minecraft 26.x releases require Fabric Loader `0.18.6+` and Java 25+.

### Commands

Players:

```text
/leaderboard                                         Open the clickable ranking menu
/leaderboard help                                    Show commands, settings, and descriptions
/leaderboard <period> <metric> [limit]               Show a ranking in chat
/leaderboard mine <all|day|week|month>               Show the caller's statistic scores
/leaderboard display show <period> <metric>          Show the caller's personal sidebar
/leaderboard display off                             Hide the caller's personal sidebar
/leaderboard carousel <true|false|status>            Toggle or inspect personal carousel rotation
/leaderboard lookmenu <true|false|status>            Toggle or inspect the personal look-up+sneak menu
```

Operators:

```text
/leaderboard display show <period> <metric> <player> Show a personal sidebar for a player
/leaderboard display off <player>                    Hide a player's personal sidebar
/leaderboard displayfilter <metric> <true|false|status> Allow or block one metric from display
/leaderboard scoreboard show <period> <metric>       Set the server-wide vanilla sidebar
/leaderboard scoreboard clear                         Clear the server-wide sidebar
/leaderboard scoreboard cleanup                       Clear displayed scoreboards from other mods
/leaderboard scoreboard blocking <true|false|status> Automatically block other-mod scoreboards
/leaderboard whitelist <true|false|status>           Filter rankings by the server whitelist
/leaderboard botfilter <true|false|status>           Filter players with a bot_ name prefix
/leaderboard customfilter <true|false|status>        Filter unrecognized historical players
/leaderboard onlinefilter <true|false|status>        Restrict rankings to online players
/leaderboard modwhitelist add|remove <name|UUID>     Manage the RankBoard-only whitelist
/leaderboard modwhitelist list|reload                List or reload the RankBoard-only whitelist
/leaderboard cache <status|reload>                   Inspect or reload historical-stat cache
/leaderboard lookup <UUID|whitelist>                 Look up Mojang player names
/leaderboard ratelimit clear                          Clear all web IP rate-limit history
/leaderboard config <list|reload|get|set>            List, change, or reload settings
/leaderboard lookmenu global <true|false|status>     Operators toggle or inspect the global menu
/leaderboard namecolor <true|false|scoreboard-only|status> Set or inspect the server-wide name-color mode
/leaderboard color list                              List all metric colors
/leaderboard color <metric>                          Open the bilingual clickable 16-color preset menu
/leaderboard color <metric> <name|#RRGGBB>           Tab-complete an English color name or set a custom RGB value
/leaderboard color reset <metric|all>                Restore one or all default colors
/leaderboard label <metric> <name>                   Set a custom display name, for example placed Building
/leaderboard label list                              List all metric display names
/leaderboard label reset <metric|all>                Restore one or all default names
```

`true/false` is the recommended syntax. The old `on/off` and `enable/disable` aliases remain available for compatibility.

### Configuration

Main configuration: `config/rankboard/rankboard.properties`

Web configuration: `config/rankboard/rankboard-web.properties`

Main settings:

```properties
history-files-per-second=50                 # Historical statistic files scanned per second
welcome-enabled=true                        # Send a welcome message to a joining player
welcome-name=auto                           # Welcome name; auto reads server information
join-menu-enabled=true                      # Open the chat ranking menu on join
join-web-hint-enabled=false                 # Show the web-ranking address on join
web-public-address=                         # Website button/join hint address; blank defaults to http://127.0.0.1:8765 and can be changed by command
restore-scoreboard-on-join=true             # Restore the player's previous personal sidebar
look-up-sneak-menu-enabled=true             # Open the chat ranking menu while looking up and holding Shift
carousel-enabled=true                       # Let players enable automatic ranking rotation
carousel-interval-seconds=30                # Carousel interval in seconds
client-scoreboard-show-zero=false           # Show zero-value players in personal sidebars
scoreboard-switch-message-enabled=true      # Send a message after switching rankings
scoreboard-name-color-enabled=true          # true=ranking/chat/TAB/overhead; false=off; scoreboard-only=ranking only
player-name-color-render-mode=legacy        # legacy=nearest vanilla color; rgb=exact RGB in ranking/chat/TAB
metric-color-food=#FFAA00                   # Food metric; other colors use metric-color-<metric>
metric-color-jumps=#FF55FF
metric-color-mined=#5555FF                  # Mining: blue
metric-color-placed=#00AAAA                 # Placed blocks: dark aqua
metric-color-kills=#FF5555
metric-color-deaths=#AA0000
metric-color-trades=#55FF55                 # Trades: green
metric-color-playtime=#55FFFF               # Online: aqua
metric-color-elytra=#FF55FF
metric-color-fishing=#0000AA                # Fishing: dark blue
metric-color-damage=#FF5555                 # Damage: red
metric-color-dropped=#555555
metric-color-picked=#55FF55
metric-color-crafted=#FFAA00
metric-color-redstone=#FF5555
metric-label-placed=放置榜                   # Display name used by game menus, sidebar titles, and web results
metric-label-playtime=在线榜
metric-label-elytra=飞行榜
metric-label-damage=受伤榜
metric-label-dropped=丢垃圾榜
metric-label-picked=拾荒榜
metric-label-crafted=合成榜
metric-label-redstone=红石大蛇榜
scoreboard-title-color-enabled=true         # Color sidebar titles by metric
scoreboard-live-update-enabled=true         # Refresh rankings after player statistic changes
scoreboard-live-update-window-seconds=30    # High-frequency detection window in seconds
scoreboard-live-update-threshold=100        # Begin throttling after this many changes in the window
scoreboard-live-update-throttle-seconds=30  # Minimum high-frequency refresh interval in seconds
foreign-scoreboard-blocking-mode=ask        # Other-mod scoreboards: ask/enabled/disabled
mod-whitelist-enabled=false                 # Read only players from the RankBoard whitelist
help-visibility=all                         # Help visibility: all/op/hidden
avatar-cache-enabled=true                   # Cache joined-player skin avatars
avatar-cache-days=7                         # Avatar cache retention in days
```

The `redstone` metric counts placed power, transmission, and mechanical components, including redstone dust and torches, repeaters, comparators, buttons, levers, pressure plates, detector rails, tripwire hooks, lecterns, targets, sculk sensors, pistons, rails, bells, dispensers, droppers, doors, trapdoors, fence gates, hoppers, note blocks, redstone lamps, TNT, big dripleaves, crafters, command blocks, and every oxidized or waxed copper-bulb variant.

Web settings:

```properties
host=0.0.0.0                              # Web-server bind address; 0.0.0.0 accepts all connections
port=8765                                 # Web-server port
web-data-requests-per-second=1            # Per-IP base rate for data requests
web-icon-request-interval-seconds=3       # Base image request interval in seconds
web-ranking-refresh-interval-seconds=30   # Full ranking refresh interval in seconds
server-name=auto                           # Server name shown on the web page; auto reads server data
website-icon=server-icon.png              # Server icon, restricted to config/rankboard/
```

`website-icon` can only reference a file inside `config/rankboard/`. Absolute paths, traversal paths, and symlinks escaping that directory are rejected.

### RankBoard Whitelist

Enable it with:

```text
/leaderboard config set mod-whitelist-enabled true
```

Whitelist file: `config/rankboard/rankboard-whitelist.json`

Example:

```json
[
  {"uuid": "00000000-0000-0000-0000-000000000000"},
  {"name": "PlayerName"}
]
```

When enabled, statistics scanning, caching, in-game rankings, and web rankings accept only listed players. The existing `whitelistOnly` setting remains active; when both lists are enabled, their intersection is used.

### Web and Rate Limiting

Default address: `http://server-address:8765/`

Example endpoints:

```text
GET /api/rankings?metric=playtime&period=all
GET /api/rankings?metric=kills&period=week
GET /api/rankings?metric=playtime&from=2026-07-16&to=2026-07-20
```

`/api/rankings` and `/api/site` share a 30-second request count per IP. Data starts at one request per second; more than 30 requests in 30 seconds applies a five-second interval for 30 minutes. Images start at one request every three seconds; more than six requests in 30 seconds applies a 15-second interval for 30 minutes. Static web resources remain limited to one request per second.

Limited requests return HTTP `429` with `Retry-After`. Operators can clear all accumulated cooldowns with `/leaderboard ratelimit clear`.

### Building

JDK 21 is required; JDK 25 is required for 26.x builds.

```text
gradlew.bat build
```

Artifacts are written to `build/libs/`. The JAR filename includes the mod and Minecraft versions.

Multi-version results are collected under `multi-version-builds/`; every successful build is also archived in a timestamped directory under `mod-builds/`.
