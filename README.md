# RankBoard 模组说明文档（中英双语）

## 简介 \| Introduction

**中文**：RankBoard 是一款适配 Minecraft Fabric 1\.21\.1 的服务端专属排行榜计分板模组。榜单展示逻辑参考 FZ Survival 数据包计分板设计思路，未复用任何原有函数与资源，独立实现、轻量化、高性能。

**English**：RankBoard is a server\-side leaderboard scoreboard mod for Minecraft Fabric 1\.21\.1\. Its ranking display logic refers to the scoreboard design of the FZ Survival Data Pack, with none of the original functions or resources reused\. It is independently implemented, lightweight and high\-performance\.

## 核心功能 \| Core Features

**中文**

- **统计维度**：支持全方位玩家数据统计，包含 `food`（食物食用量）、`jumps`（跳跃次数）、`mined`（方块挖掘数）、`placed`（方块放置数）、`kills`（击杀数）、`deaths`（死亡数）、`trades`（村民交易次数）、`playtime`（在线时长）、`elytra`（鞘翅飞行距离）、`fishing`（钓鱼次数）、`damage`（受到伤害值）。

- **统计周期**：内置五大统计周期，`daily`（日榜）、`weekly`（周榜）、`monthly`（月榜）、`yearly`（年榜）、`all`（总榜），满足不同场景统计需求。

- **数据持久化**：周期统计基线存储于主世界持久化文件，服务器重启、重载均不会丢失数据。

- **原版数据兼容**：自动读取服务端 `world/stats/*.json` 原版统计文件，**离线玩家数据可正常入榜**，完整追溯历史记录。

- **智能缓存机制**：首次启动限速批量读取历史统计并生成全局缓存文件；后续重启秒级加载榜单，后台仅增量更新变动玩家数据，大幅降低服务器性能消耗。

- **精细化过滤**：默认仅读取白名单（`whitelist.json`）内玩家数据，支持 UUID 精准匹配离线历史记录；可独立开关白名单校验、`bot_` 机器人屏蔽、未知玩家过滤、仅在线玩家筛选。

- **聚合统计展示**：榜单首行自动展示当前筛选条件下所有玩家的总数据聚合值，直观查看服务器整体数据。

- **人性化显示优化**：在线时长自动将游戏 Tick 换算为标准小时格式（72000 Tick = 1 小时），以 `647h` 简洁样式展示于原版侧边栏计分板。

- **个性化侧边栏**：玩家无需安装客户端模组，可通过可视化彩色可点击菜单，自由切换个人专属侧边栏榜单。

**English**

- **Statistics Metrics**: Comprehensive player data statistics, including`food` \(food consumption\), `jumps` \(jump counts\), `mined` \(blocks mined\), `placed` \(blocks placed\), `kills` \(kill counts\), `deaths` \(death counts\), `trades` \(villager trades\), `playtime` \(online duration\), `elytra` \(elytra flight distance\), `fishing` \(fishing counts\), and `damage` \(damage taken\)\.

- **Time Periods**: Five built\-in statistical cycles: `daily`, `weekly`, `monthly`, `yearly`, and `all` \(total\), adapting to various statistical scenarios\.

- **Data Persistence**: Period baseline data is saved in the overworld persistent files, ensuring no data loss after server restart or reload\.

- **Vanilla Data Compatibility**: Automatically reads vanilla statistics from `world/stats/*.json`\. **Offline player data can be normally included in rankings** for complete historical tracking\.

- **Intelligent Caching**: Reads historical statistics with rate limiting on first startup and generates a global cache file\. Subsequent restarts load rankings instantly, with only changed player data updated incrementally in the background to reduce server performance overhead\.

- **Fine\-grained Filtering**: Only whitelisted players \(from `whitelist.json`\) are displayed by default, with accurate UUID matching for offline historical data\. Independent toggles for whitelist verification, `bot_` bot filtering, unknown player filtering, and online\-only player filtering are available\.

- **Aggregated Data Display**: The first line of the ranking shows the total aggregated data of all players under the current filter conditions, reflecting overall server data intuitively\.

- **Optimized Display**: Playtime is automatically converted from game ticks to hours \(72000 ticks = 1 hour\) and displayed in a concise `647h` format on the vanilla sidebar scoreboard\.

- **Personalized Sidebar**: No client\-side mod required\. Players can freely switch their personal sidebar rankings via a colorful, clickable in\-game menu\.

## 指令系统 \| Command System

**中文**：所有指令服务端全局可用，区分普通玩家权限与管理员（OP）权限，支持自定义榜单展示、过滤规则、缓存管理、玩家侧边栏控制。

**English**: All commands are server\-wide, with separate permissions for regular players and operators \(OPs\), supporting custom ranking display, filter rules, cache management, and sidebar control\.

### 通用查询指令 \| General Query Commands

基础榜单查询，支持指定周期、统计类型和展示条数

```Plain Text
/leaderboard <daily|weekly|monthly|yearly|all> <metric> [limit]
```

示例 \| Examples

```Plain Text
/leaderboard daily food
```

```Plain Text
/leaderboard weekly jumps 20
```

```Plain Text
/leaderboard all elytra 50
```

查看帮助 \| View help

```Plain Text
/leaderboard help
```

打开可视化榜单选择菜单 \| Open visual ranking selection menu

```Plain Text
/leaderboard
```

### 个人侧边栏控制 \| Personal Sidebar Control

**中文**：玩家自主控制个人计分板侧边栏，无需客户端模组，独立生效互不干扰。

**English**: Players can control their personal scoreboard sidebar independently without client mods, with isolated effects for each player\.

```Plain Text
/leaderboard display show daily food
```

```Plain Text
/leaderboard display show all playtime
```

```Plain Text
/leaderboard display off
```

侧边栏名称颜色开关 \| Toggle ranking name color on sidebar

```Plain Text
/leaderboard namecolor on|off|status
```

### 管理员侧边栏权限控制 \| OP Sidebar Permission Control

**中文**：OP 可代为指定玩家开关侧边栏，支持全局榜单展示禁用/启用。

**English**: OPs can toggle the sidebar for specified players and enable/disable global ranking display\.

代为玩家控制侧边栏 \| Control sidebar for other players

```Plain Text
/leaderboard display show all playtime <player>
```

```Plain Text
/leaderboard display off <player>
```

指定榜单类型全局开关 \| Global toggle for specific ranking types

```Plain Text
/leaderboard displayfilter fishing disable
```

```Plain Text
/leaderboard displayfilter fishing enable
```

```Plain Text
/leaderboard displayfilter fishing status
```

开启全服共用计分板侧边栏 \| Enable server\-wide public scoreboard sidebar

```Plain Text
/leaderboard scoreboard show weekly mined
```

```Plain Text
/leaderboard scoreboard clear
```

### 过滤规则管理 \| Filter Rule Management

**中文**：管理员可自由切换各类过滤规则，适配私服、公服、机器人隔离等场景。

**English**: Administrators can freely switch filter rules for private servers, public servers, bot isolation and other scenarios\.

白名单过滤开关 \| Whitelist filter toggle

```Plain Text
/leaderboard whitelist on|off|status
```

Bot玩家（bot\_前缀）屏蔽开关 \| Bot player filter toggle

```Plain Text
/leaderboard botfilter on|off|status
```

未知历史玩家过滤开关 \| Unknown historical player filter toggle

```Plain Text
/leaderboard customfilter on|off|status
```

仅在线玩家展示开关 \| Online\-only player display toggle

```Plain Text
/leaderboard onlinefilter on|off|status
```

### 缓存与玩家数据管理 \| Cache \& Player Data Management

**中文**：手动刷新玩家 Mojang 正版名称、重载历史数据缓存，修复异常数据。

**English**: Manually refresh player Mojang account names and reload historical data cache to fix abnormal data\.

查询并缓存指定UUID玩家名称 \| Look up and cache player name by UUID

```Plain Text
/leaderboard lookup <UUID>
```

批量刷新白名单玩家名称 \| Batch refresh whitelist player names

```Plain Text
/leaderboard lookup whitelist
```

查看缓存状态 / 重载缓存 \| Check cache status / Reload cache

```Plain Text
/leaderboard cache status
```

```Plain Text
/leaderboard cache reload
```

## 网页端功能 \| Web Panel Features

**中文**

- 服务端内置轻量化网页服务，启动后默认访问地址：`http://服务器地址:8765/`，纯只读展示，无高危操作。

- 支持快捷切换日/周/月/总榜，同时支持**自定义起止日期查询历史榜单**，精准查询模组安装后任意时段数据。

- 网页个性化配置：支持玩家头像展示、自定义服务器图标/名称、网站配色、Modrinth/GitHub 外链配置。

- 内置 RESTful JSON API，支持第三方程序对接，单IP每秒1次请求限流，保障服务器稳定。

- 配置文件：服务端根目录 `rankboard-web.properties`

**English**

- Built\-in lightweight web server, default access address after startup: `http://ServerIP:8765/` \(read\-only, no risky operations\)\.

- Supports quick switching of daily/weekly/monthly/all rankings, and **custom start\-end date query for historical data** to view statistics of any period after mod installation\.

- Fully customizable web interface: player avatar display, custom server icon/name, website color scheme, Modrinth/GitHub link configuration\.

- Built\-in RESTful JSON API for third\-party integration, with 1 request per second per IP rate limiting to ensure server stability\.

- Config file: `rankboard-web.properties` in server root directory

### 网页配置示例 \| Web Config Example

```Plain Text
host=0.0.0.0
port=8765
server-name=auto
website-icon=server-icon.png
```

**中文**：`server-name=auto` 自动读取服务端 `server.properties` 内的 MOTD 作为网站服务器名称。

**English**: `server-name=auto` automatically reads the MOTD in`server.properties` as the server name displayed on the website\.

### API 接口 \| API Endpoints

```Plain Text
GET /api/rankings?metric=playtime&from=2026-07-16&to=2026-07-20
GET /api/rankings?metric=kills&period=week
GET /api/rankings?metric=playtime&period=all
```

## 技术说明 \& 兼容备注 \| Technical Notes \& Compatibility

**中文**

- 历史数据限制：自定义日期统计仅支持模组安装后生成的快照数据，原版全局累计统计无法逆向推算安装前单日增量数据。

- 数据刷新机制：个人侧边栏每30秒自动刷新；榜单查询优先读取内存缓存，实时覆盖未写入文件的在线玩家最新数据，保证数据时效性。

- 缓存加载逻辑：首次启动限速每秒读取50个玩家统计文件，生成 `rankboard-history-cache.json` 缓存文件；后续重启优先加载缓存，后台增量校验文件更新。

- 数据基线规则：玩家首次进入对应统计周期时自动生成数据基线；模组首次安装的周期，以服务器首次启动时间为统计起点。

- 放置方块统计说明：原版无精准“成功放置方块”统计，`placed` 维度采用方块物品右键使用次数统计，少量未成功放置的右键操作会被计入，属于正常兼容取舍。

- 权限隔离：个人侧边栏仅对自身生效，管理员全局计分板侧边栏对全服玩家生效，互不冲突。

**English**

- Historical Data Limit: Custom date range statistics only apply to snapshots generated after mod installation\. Vanilla global cumulative statistics cannot calculate daily incremental data before the mod was installed\.

- Data Refresh Mechanism: Personal sidebar refreshes automatically every 30 seconds\. Rank queries prioritize in\-memory cache and override unwritten real\-time data of online players to ensure timeliness\.

- Cache Loading Logic: Reads 50 player statistic files per second with rate limiting on first startup and generates`rankboard-history-cache.json`\. Subsequent restarts load cache first and verify file updates incrementally in the background\.

- Baseline Rule: A data baseline is automatically created when a player first enters a statistical cycle\. The initial cycle after mod installation starts from the server's first startup time\.

- Placed Block Statistics Note: Vanilla Minecraft does not have precise "successfully placed blocks" statistics\. The `placed` metric counts block item right\-click usage, which may include a small number of failed placement actions as a reasonable compatibility trade\-off\.

- Permission Isolation: Personal sidebars only affect the individual player, while the administrator global scoreboard sidebar applies to all players without conflict\.

> （注：部分内容可能由 AI 生成）
