# RankBoard 中文文档

**中文** | [English](rankboard_en.md)

## 功能概览

- **多项统计**：记录食物、跳跃、挖掘、放置、击杀、PvP、死亡、交易、在线时间、飞行、钓鱼、受伤、伤害输出、丢弃、拾取、合成和红石元件放置。
- **可信周期**：区分完整与部分周期，排除缺失边界或累计值回退的数据；升级后新增榜单从首次可信基线开始。
- **时间周期**：提供 `daily`、`weekly`、`monthly`、`yearly` 和 `all`，方便分别查看短期活动成绩和服务器长期记录。
- **离线数据读取**：直接读取原版 `world/stats/*.json`，即使玩家当前不在线，也能保留并查询历史成绩。
- **缓存与后台更新**：首次加载时控制读取速度，避免启动卡顿；之后只检查变化的文件，降低服务器持续开销。
- **存档数据目录**：排行榜世界状态保存在 `world/data/rankboard/`；旧的 `world/data/rankboard_leaderboard.dat` 会在启动时安全迁移。
- **游戏内展示**：个人榜、全服榜、轮播榜和进服恢复让玩家无需打开网页即可查看排名；抬头加 Shift 可快速打开查询菜单。
- **玩家信息**：缓存头像、显示最后在线时间，并按玩家正在查看的个人榜单同步排行榜、聊天、TAB 与头顶名牌颜色。已有其他队伍的玩家不会被 RankBoard 抢占头顶名牌效果。
- **网页排行榜**：提供日期范围、在线筛选、服务器图标、主题配色及 Modrinth/GitHub 链接，适合分享给不在游戏中的玩家。
- **请求限流**：API 按 IP 逐渐增加冷却时间，防止网页刷新或异常客户端占满服务器资源；图标和静态文件使用独立限制。
- **白名单**：可沿用服务器白名单，也可单独指定 RankBoard 统计对象，用于只展示活动成员、工作人员或指定玩家。
- **计分板清理**：检测并清除其他模组留下的计分板目标，避免多个模组争用侧边栏。

## 榜单列表

| 榜单标识 | 默认名称 | 说明 |
| - | - | - |
| `food` | 大胃王榜 | 使用过的食物物品总数 |
| `jumps` | 跳跃榜 | 跳跃次数 |
| `mined` | 挖掘榜 | 挖掘方块总数 |
| `placed` | 放置榜 | 放置方块总数 |
| `kills` | 击杀榜 | 击杀生物 + 击杀玩家总数 |
| `pvp` | PvP榜 | 击杀其他玩家数量 |
| `deaths` | 死亡榜 | 死亡次数 |
| `trades` | 交易榜 | 与村民交易次数 |
| `playtime` | 在线榜 | 在线时间（以 h m 格式显示） |
| `elytra` | 飞行榜 | 鞘翅飞行距离（以 km 格式显示） |
| `fishing` | 钓鱼榜 | 钓到鱼的次数 |
| `damage` | 受伤榜 | 受到的伤害（原版值 / 10） |
| `dealt` | 输出榜 | 造成的伤害（原版值 / 10） |
| `dropped` | 丢垃圾榜 | 丢弃物品数量 |
| `picked` | 拾荒榜 | 拾取物品数量 |
| `crafted` | 合成榜 | 合成物品数量 |
| `redstone` | 红石大蛇榜 | 放置红石元件数量 |

`redstone` 统计红石粉、红石火把、中继器、比较器、观察者、活塞、粘性活塞、发射器、投掷器、漏斗、拉杆、绊线钩、标靶、日光传感器、音符盒、红石块、幽匿感测体、校准幽匿感测体、避雷针、陷阱箱、充能铁轨、探测铁轨、激活铁轨、铁轨、讲台、唱片机、钟、红石灯、TNT、大型垂滴叶、合成器、命令方块、连锁命令方块、循环命令方块，以及所有木质按钮、压力板、门、活板门、栅栏门和所有氧化/涂蜡状态的铜灯。

## 时间周期

| 标识 | 说明 |
| - | - |
| `daily` | 每日统计 |
| `weekly` | 每周统计 |
| `monthly` | 每月统计 |
| `yearly` | 每年统计 |
| `all` | 全部累计 |

## 游戏内操作指南

### 进服体验

玩家进入服务器后会依次看到：

1. **欢迎语**：显示服务器名称（可在配置中关闭）
2. **排行榜菜单**：可点击的按钮列表，包含查询分数、开启/关闭榜单、轮播开关、网站入口和帮助
3. **网页提示**（默认关闭）：提示网页排行榜地址

### 使用排行榜菜单

输入 `/leaderboard` 会弹出可点击的菜单面板：

- **[查询分数]** — 查看自己的全部统计分数
- **[开启榜单] / [关闭榜单]** — 切换自己的客户端侧边栏
- **[开启抬头蹲起] / [关闭抬头蹲起]** — 切换抬头+Shift 打开菜单的功能
- **[轮播]** — 自动轮播当前周期的所有榜单
- **[打开网站]** — 在浏览器中打开网页排行榜
- **[help]** — 查看帮助

菜单下方是所有可用榜单按钮，点击即可将该榜单显示在自己的原版侧边栏上。

### 查看个人分数

```text
/leaderboard mine             查看全部累计分数（总览）
/leaderboard mine all         同上
/leaderboard mine day         查看最近一日分数
/leaderboard mine week        查看最近一周分数
/leaderboard mine month       查看最近一月分数
```

执行后侧边栏会切换为个人总览模式，同时在聊天栏显示每个榜单的分数，底部有可点击的周期切换按钮。

### 侧边栏操作

```text
/leaderboard display show all playtime     显示总计在线榜
/leaderboard display show weekly kills     显示每周击杀榜
/leaderboard display on                    恢复上次关闭前的侧边栏
/leaderboard display off                   关闭自己的侧边栏
```

侧边栏关闭后，再次使用 `display on` 可恢复之前的状态（个人单榜、总览或轮播）。

### 轮播模式

轮播会按设定间隔自动切换显示的榜单（默认 30 秒）。

```text
/leaderboard carousel true      开启轮播
/leaderboard carousel false     关闭轮播
/leaderboard carousel status    查看轮播状态
```

OP 可控制轮播标题是否跟随当前榜单颜色：

```text
/leaderboard carousel color true     标题跟随榜单颜色
/leaderboard carousel color false    标题固定使用青色
/leaderboard carousel color status   查看颜色设置
```

### 抬头+蹲起菜单

默认开启。玩家抬头（视角朝上超过 60 度）并按住 Shift 时会自动弹出排行榜菜单。可以随时关闭：

```text
/leaderboard lookmenu false     关闭自己的抬头蹲起菜单
/leaderboard lookmenu true      重新开启
/leaderboard lookmenu status    查看状态
```

### 聊天栏查询排行榜

```text
/leaderboard all playtime               查看总计在线榜（默认前 10 名）
/leaderboard weekly kills 20            查看每周击杀榜前 20 名
/leaderboard monthly mined              查看每月挖掘榜
```

结果包含总和、排名、玩家名和分数，榜单名称和数值会以对应颜色显示。

### 名字颜色同步

开启名字颜色后（默认开启），玩家名字会根据当前查看的榜单变色，影响位置包括排行榜、聊天、TAB 列表和头顶名牌。

```text
/leaderboard namecolor true             全部位置开启
/leaderboard namecolor scoreboard-only  仅排行榜变色
/leaderboard namecolor false            全部关闭
/leaderboard namecolor status           查看当前模式
```

渲染模式可选 `legacy`（原版 16 色）或 `rgb`（精确 RGB）：

```text
/leaderboard config set player-name-color-render-mode rgb
```

### 帮助系统

`/leaderboard help` 会显示分组入口，点击可进入详细帮助：

```text
/leaderboard help                       主帮助菜单
/leaderboard help player                玩家常用指令
/leaderboard help scoreboard            计分板相关指令
/leaderboard help web                   网页与配置
/leaderboard help admin                 OP 管理指令（仅 OP）
/leaderboard help config                完整配置说明（仅 OP）
```

帮助中的每条指令都可以点击直接填入聊天栏。

## 命令

### 玩家指令

| 命令 | 说明 |
| - | - |
| `/leaderboard` | 打开排行榜菜单 |
| `/leaderboard help [分组]` | 获取指令帮助 |
| `/leaderboard <周期> <榜单> [数量]` | 在聊天栏查看排行榜 |
| `/leaderboard mine <all\|day\|week\|month>` | 查询自己的统计分数 |
| `/leaderboard display show <周期> <榜单>` | 显示个人客户端侧边栏 |
| `/leaderboard display on` | 恢复关闭前的客户端侧边栏 |
| `/leaderboard display off` | 关闭个人客户端侧边栏 |
| `/leaderboard carousel <true\|false\|status>` | 控制榜单轮播 |
| `/leaderboard lookmenu <true\|false\|status>` | 控制抬头+蹲起菜单 |

### 管理员指令

| 命令 | 说明 |
| - | - |
| `/leaderboard display show <周期> <榜单> <玩家>` | 为指定玩家显示侧边栏 |
| `/leaderboard display off <玩家>` | 关闭指定玩家的侧边栏 |
| `/leaderboard displayfilter <榜单> <true\|false\|status>` | 管理单个榜单是否允许玩家显示 |
| `/leaderboard scoreboard show <周期> <榜单>` | 设置全服共享原版侧边栏 |
| `/leaderboard scoreboard clear` | 关闭全服共享侧边栏 |
| `/leaderboard scoreboard cleanup` | 清理其他模组正在显示的计分板 |
| `/leaderboard scoreboard blocking <true\|false\|status>` | 自动屏蔽其他模组计分板 |
| `/leaderboard whitelist <true\|false\|status>` | 使用服务器白名单筛选排行榜 |
| `/leaderboard botfilter <true\|false\|status>` | 过滤 bot_ 前缀玩家 |
| `/leaderboard customfilter <true\|false\|status>` | 过滤无法识别身份的历史玩家 |
| `/leaderboard onlinefilter <true\|false\|status>` | 只显示当前在线玩家 |
| `/leaderboard modwhitelist add\|remove <name\|UUID>` | 管理 RankBoard 独立白名单 |
| `/leaderboard modwhitelist list\|reload` | 查看或重新读取独立白名单 |
| `/leaderboard recipients <fake-only\|false\|whitelist\|blacklist\|status>` | 控制哪些在线玩家接收个人榜单数据 |
| `/leaderboard cache <status\|reload>` | 查看或重新加载历史统计缓存 |
| `/leaderboard cache threads <0-256>` | 设置历史扫描线程；0 自动，最多使用 50% 逻辑处理器，并立即并行重新扫描 |
| `/leaderboard cache threads status` | 查看配置值与实际使用的扫描线程数 |
| `/leaderboard lookup <UUID\|whitelist>` | 查询 Mojang UUID 对应的玩家名 |
| `/leaderboard ratelimit clear` | 清空全部网页 IP 限流与累计冷却 |
| `/leaderboard config <list\|reload\|get\|set>` | 查看、修改或重载配置 |
| `/leaderboard lookmenu global <true\|false\|status>` | OP 开关或查询全服抬头+蹲起菜单 |
| `/leaderboard carousel color <true\|false\|status>` | 轮播标题是否跟随榜单颜色 |
| `/leaderboard namecolor <true\|false\|scoreboard-only\|status>` | 设置或查询全服名字颜色模式 |
| `/leaderboard color list` | 列出所有榜单颜色 |
| `/leaderboard color <榜单>` | 打开英中双语的 16 色点击预选菜单 |
| `/leaderboard color <榜单> <颜色名\|#RRGGBB>` | 使用 Tab 补全英文颜色名，或设置自定义 RGB |
| `/leaderboard color reset <榜单\|all>` | 恢复单个或全部默认颜色 |
| `/leaderboard label <榜单> <名称>` | 自定义榜单显示名称 |
| `/leaderboard label list` | 查看所有榜单显示名称 |
| `/leaderboard label reset <榜单\|all>` | 恢复单个或全部默认名称 |
| `/leaderboard webtheme <icon\|blue\|rgb #RRGGBB\|true\|false\|status>` | 设置网页主题模式 |
| `/leaderboard webswitch <name\|weight\|add\|remove\|list\|status>` | 管理网页服务器切换列表 |

`true/false` 是推荐的新语法。旧的 `on/off` 和 `enable/disable` 仍作为兼容别名保留。

## 配置

主配置：`config/rankboard/rankboard.properties`

网页配置：`config/rankboard/rankboard-web.properties`

### 主配置

```properties
# --- 历史统计 ---
history-files-per-second=50                 # 每个扫描线程每秒读取的文件数；总上限为此值乘以实际线程数
history-scan-threads=0                      # 扫描线程；0 自动，最多使用 50% 可用处理器

# --- 进服提示 ---
welcome-enabled=true                        # 玩家进服时发送欢迎语
welcome-name=auto                           # 欢迎语服务器名；auto 自动读取
join-menu-enabled=true                      # 玩家进服时换出聊天栏面板
join-web-hint-enabled=false                 # 玩家进服时提示网页排行榜地址
website-button-enabled=true                 # 菜单和帮助中是否显示 [打开网站]
web-public-address=                         # 网站按钮地址；留空默认 http://127.0.0.1:8765

# --- 客户端计分板 ---
restore-scoreboard-on-join=true             # 恢复玩家上次选择的客户端计分板
look-up-sneak-menu-enabled=true             # 抬头并按住 Shift 打开排行榜菜单
carousel-enabled=true                       # 允许玩家开启榜单自动轮播
carousel-interval-seconds=30                # 轮播切换间隔，单位秒（范围 3-3600）
carousel-color-follow-metric=true           # 轮播标题跟随当前榜单颜色；false 固定青色
client-scoreboard-show-zero=false           # 显示数值为 0 的玩家
scoreboard-switch-message-enabled=true      # 切换榜单后发送提示消息
scoreboard-name-color-enabled=true          # true=排行榜/聊天/TAB/头顶；false=关闭；scoreboard-only=仅排行榜
player-name-color-render-mode=legacy        # legacy=原版16色；rgb=精确RGB
scoreboard-title-color-enabled=true         # 计分板标题跟随榜单颜色
scoreboard-live-update-enabled=true         # 玩家行为改变统计时实时刷新
scoreboard-live-update-window-seconds=30    # 高频行为检测窗口（范围 1-300）
scoreboard-live-update-threshold=100        # 超过此次数后降低刷新频率（范围 1-100000）
scoreboard-live-update-throttle-seconds=30  # 高频时最短刷新间隔（范围 1-3600）

# --- 玩家筛选 ---
foreign-scoreboard-blocking-mode=ask        # 其他模组计分板屏蔽模式：ask/enabled/disabled
mod-whitelist-enabled=false                 # 只读取 RankBoard 独立白名单中的玩家
scoreboard-recipient-filter=fake-only       # 个人榜单数据接收过滤：fake-only/false/whitelist/blacklist
help-visibility=all                         # 帮助可见范围：all/op/hidden
avatar-cache-enabled=true                   # 缓存进服玩家的皮肤头像
avatar-cache-days=7                         # 头像缓存有效天数（范围 1-365）

# --- 榜单名称 ---
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

# --- 榜单颜色 ---
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

### 网页配置

```properties
# --- 网页监听 ---
host=0.0.0.0                              # 监听地址；0.0.0.0 接受所有连接
port=8765                                 # 监听端口（范围 1-65535）

# --- 网页显示 ---
server-name=auto                           # 网页显示的服务器名；auto 自动读取
website-icon=server-icon.png              # 服务器图标路径

# --- 网页切换 ---
web-switcher-name=auto                     # 切换按钮名称；auto 使用网页服务器名
web-switcher-weight=100                    # 显示权重；越小越靠前，1 最先显示
web-switcher-peers=                        # 其他 RankBoard 网页地址，逗号分隔

# --- 请求限流 ---
web-data-requests-per-second=1            # 单个 IP 的数据请求基础频率（范围 1-100）
web-icon-request-interval-seconds=3       # 图片请求间隔，单位秒

# --- 网页数据 ---
web-ranking-refresh-interval-seconds=30   # 排行榜数据刷新间隔（范围 1-3600）

# --- 网页主题 ---
web-theme-follow-icon=true                # 从服务器图标自动取色
web-theme-base=auto                       # 基础色；auto 或 #RRGGBB
web-theme-background=auto                 # 背景色
web-theme-surface=auto                    # 面板色
web-theme-primary=auto                    # 主色（按钮、选中项、主要数值）
web-theme-secondary=auto                  # 辅助色（排名、状态强调）
web-theme-text=auto                       # 文字色
web-theme-muted=auto                      # 次要文字色
web-theme-border=auto                     # 边框色
web-theme-success=auto                    # 在线状态色
web-theme-danger=auto                     # 错误警告色
```

`website-icon` 优先读取 `config/rankboard/` 目录内的文件；默认图标不存在时回退到服务端根目录的 `server-icon.png`。绝对路径、越界路径和目录外符号链接都会被拒绝。

可用 `/leaderboard webtheme icon` 启用图标取色、`/leaderboard webtheme blue` 恢复默认蓝色系，或 `/leaderboard webtheme rgb #3F505E` 生成自定义 RGB 色系。

## 白名单

启用：

```text
/leaderboard config set mod-whitelist-enabled true
```

白名单文件：`config/rankboard/rankboard-whitelist.json`

```json
[
  {"uuid": "00000000-0000-0000-0000-000000000000"},
  {"name": "PlayerName"}
]
```

启用后，统计文件扫描、缓存、游戏榜单和网页榜单只接受该文件中的玩家。原有服务器 `whitelistOnly` 配置仍然有效；两个白名单同时开启时取交集。

## 网页与限流

默认地址：`http://服务器地址:8765/`

接口示例：

```text
GET /api/rankings?metric=playtime&period=all
GET /api/rankings?metric=kills&period=week
GET /api/rankings?metric=playtime&from=2026-07-16&to=2026-07-20
```

数据接口 `/api/rankings` 和 `/api/site` 共享同一 IP 的 30 秒请求计数。默认每秒 1 次；30 秒内超过 30 次后，固定 30 分钟改为每 5 秒 1 次。图片默认每 3 秒 1 次；30 秒内超过 6 次后，固定 30 分钟改为每 15 秒 1 次。静态网页资源仍为每秒 1 次。

超过限制返回 HTTP `429` 和 `Retry-After`。OP 可以使用 `/leaderboard ratelimit clear` 清除所有累计冷却。

## 多服务器网页切换

多个 RankBoard 网页可通过 `/leaderboard webswitch add <IP|域名|网址>` 加入左侧切换列表。地址未写端口时使用当前网页端口；相同 IP 和端口会自动合并。

- `/leaderboard webswitch name <名称|auto>` 设置本服按钮名称
- `/leaderboard webswitch weight <1-10000>` 设置顺序，权重 `1` 最先显示

## 存档数据

- 榜单历史快照：`world/data/rankboard/`
- 玩家头像缓存：`world/data/rankboard/avatars/`
- 白名单文件：`config/rankboard/rankboard-whitelist.json`
- 主配置：`config/rankboard/rankboard.properties`
- 网页配置：`config/rankboard/rankboard-web.properties`

旧的 `world/data/rankboard_leaderboard.dat` 会在启动时自动迁移到新目录。

## 构建

需要 JDK 21；构建 26.x 需要 JDK 25。

```text
gradlew.bat build
```

构建产物位于 `build/libs/`。发布版本和 Minecraft 版本会写入 JAR 文件名。

多版本构建结果位于 `multi-version-builds/`，每次成功构建也会单独归档到 `mod-builds/` 的时间戳目录。
