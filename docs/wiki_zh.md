# RankBoard Wiki（中文）

[English](wiki_en.md) | [GitHub](https://github.com/halfkite/rankboard) | [Modrinth](https://modrinth.com/project/rankboard)

RankBoard 是服务端排行榜模组。它读取原版统计数据，提供聊天栏查询、原版侧边计分板和网页排行榜；玩家通常无需安装客户端模组。

## 依赖

| 名称 | 类型 | 说明 |
| --- | --- | --- |
| Fabric API | Fabric 必需 | 安装与游戏版本匹配的 Fabric API。 |
| Fabric Loader | Fabric 必需 | 1.21 系列需要 `0.15.11+`；26.x 需要 `0.18.6+`。 |
| NeoForge | NeoForge 必需 | 安装与游戏版本精确匹配的 NeoForge。 |
| Java | 必需 | 1.21 系列使用 Java 21+；26.x 使用 Java 25+。 |

## 版本支持

| Minecraft | Fabric | NeoForge | 说明 |
| --- | --- | --- | --- |
| 1.21.1、1.21.4、1.21.8、1.21.11 | 支持 | 支持 | 使用对应小版本的 JAR。 |
| 1.21–1.21.11 | `1.21.x` Wrapper | 视发布包而定 | Fabric Wrapper 会选择内置的兼容实现。 |
| 26.1、26.1.1、26.1.2 | `26.1.x` Wrapper | 支持 | 26.x 需要 Java 25。 |
| 26.2 | 独立 JAR | 支持 | 请不要和 26.1.x 包混用。 |

> 一个服务端只安装一个 RankBoard JAR。移除旧的 `rankboard_wrapper` 或不匹配 Minecraft 版本的旧 JAR，避免加载到错误实现。

## 安装

1. 安装匹配的加载器和依赖。
2. 将对应 RankBoard JAR 放入服务端 `mods/`。
3. 启动一次服务端，生成配置目录。
4. 编辑配置后重启，或使用 `/leaderboard config reload`。

配置与缓存统一保存在：

```text
<服务器目录>/config/rankboard/
```

主要文件：

| 文件 | 用途 |
| --- | --- |
| `rankboard.properties` | 排行榜、侧边栏、玩家体验与统计扫描配置。 |
| `rankboard-web.properties` | 网页服务、主题、图标、限流和站点切换配置。 |
| `rankboard-whitelist.json` | RankBoard 独立白名单。 |
| `server-icon.png` | 网页服务器图标；优先于服务器根目录的同名文件。 |
| `rankboard-history-cache.json` | 历史统计缓存；由模组维护。 |

旧配置会在首次读取时迁移到 `config/rankboard/`。迁移完成后 `read-legacy-config=false`，之后不再重复导入旧文件。

## 排行榜内容

每项数据均可使用 `daily`、`weekly`、`monthly`、`yearly` 或 `all` 查询。

| 命令名 | 默认显示名 | 统计内容 |
| --- | --- | --- |
| `food` | 大胃王榜 | 使用的食物数量。 |
| `jumps` | 跳跃榜 | 跳跃次数。 |
| `mined` | 挖掘榜 | 挖掘的注册方块数量。 |
| `placed` | 放置榜 | 放置方块数量。 |
| `kills` | 击杀榜 | 生物击杀数。 |
| `pvp` | PvP榜 | 玩家击杀数。 |
| `deaths` | 死亡榜 | 死亡次数。 |
| `trades` | 交易榜 | 村民交互/交易统计。 |
| `playtime` | 在线榜 | 在线时间。 |
| `elytra` | 飞行榜 | 鞘翅飞行距离。 |
| `fishing` | 钓鱼榜 | 钓鱼次数。 |
| `damage` | 受伤榜 | 承受伤害，显示为原版值的十分之一。 |
| `dealt` | 输出榜 | 造成伤害，显示为原版值的十分之一。 |
| `dropped` | 丢垃圾榜 | 丢弃物品数量。 |
| `picked` | 拾荒榜 | 捡起物品数量。 |
| `crafted` | 合成榜 | 合成物品数量。 |
| `redstone` | 红石大蛇榜 | 放置的红石元件数量。 |

OP 可使用 `/leaderboard label` 自定义显示名，使用 `/leaderboard color` 自定义每项的颜色。被 OP 禁用显示的榜单会同时从菜单、网页与 API 中隐藏。

## 玩家使用

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

- `/leaderboard` 打开六行可点击菜单。
- `/leaderboard mine` 查询自己的全部统计并显示个人总览；首次使用默认总榜。
- `display off` 仅关闭显示，不会清除上次选择；`display on` 会恢复单榜、总览或轮播状态。
- 抬头并按住 Shift 可打开菜单；玩家可自行关闭此功能。
- 轮播与个人总览互斥。轮播时名称和侧边栏可使用轮播榜颜色。

## OP 管理

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

`true` 与 `false` 是推荐写法；旧的 `on/off`、`enable/disable` 仍保留为兼容别名。

### 历史扫描

RankBoard 会读取 `world/stats/*.json`，因此离线玩家也会保留成绩。首次扫描会在后台进行，扫描过程中仍可查看缓存榜单。

```properties
history-scan-threads=0
history-files-per-second=50
```

`history-scan-threads=0` 表示自动选择线程数，最多占用逻辑处理器的 50%。`history-files-per-second` 是每条扫描线程的上限，总读取上限是两者的乘积。

周期边界缺失、累计统计回退或新指标没有可信基线时，结果会标记为“部分统计”；这比把缺失历史误当作 0 更可靠。

## 网页排行榜

默认网页地址：

```text
http://<服务器地址>:8765/
```

常用网页配置：

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

- `config/rankboard/server-icon.png` 优先；缺失时回退到服务器根目录的 `server-icon.png`。
- 图标内容会生成版本哈希，浏览器会长期缓存；更换图片后会自动刷新为新地址。
- `web-theme-follow-icon=true` 会从图标取色；也可将主题项设置为 `#RRGGBB`。
- `/leaderboard webtheme blue` 恢复蓝色主题，`/leaderboard webtheme icon` 使用图标配色，`/leaderboard webtheme rgb #3F505E` 设置自定义色系。
- `/leaderboard webswitch add <地址>` 添加同一局域网或其他服务器的 RankBoard 网页；相同 IP 与端口会自动合并。

常用 API：

```text
GET /api/site
GET /api/sites
GET /api/rankings?metric=playtime&period=all
GET /api/rankings?metric=kills&period=week
```

网页数据请求与图标请求均受独立 IP 限流保护。超限时会返回 HTTP `429` 与 `Retry-After`；OP 可用 `/leaderboard ratelimit clear` 清除累计限制。

## 白名单与过滤

```properties
mod-whitelist-enabled=false
```

开启后，只有 `rankboard-whitelist.json` 中的玩家会进入统计扫描、游戏菜单、网页列表和 API 结果。服务器原版白名单过滤与 RankBoard 独立白名单可以同时启用，二者同时开启时取交集。

## 常见问题

### 玩家头像或网页图标不显示

检查 `config/rankboard/server-icon.png` 是否存在且为有效 PNG。网页图标受缓存保护，更换文件后重启或重载网页服务即可生成新版本地址。

### 榜单显示“部分统计”

这表示该周期缺少可信的起止快照，常见于新安装、升级后新增指标或历史统计文件不完整。继续运行并让模组建立新的周期边界后，后续完整周期会正常显示。

### 侧边栏被其他模组占用

使用 `/leaderboard scoreboard cleanup` 清理其他模组残留的计分板目标；必要时启用 `/leaderboard scoreboard blocking true`。

### 为什么网页频繁刷新后会被限流

数据、图标和静态资源采用不同限流策略。不要禁用浏览器缓存；图标 URL 带内容版本号，正常刷新不会重复下载同一图标。

## 许可证

RankBoard 使用 [MIT License](../LICENSE)。
