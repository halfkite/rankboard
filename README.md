# RankBoard

适用于 Fabric 1.21.1 服务端的排行榜模组。展示思路参考 FZ Survival Data Pack 的计分板功能，但没有复制其函数或资源。

## 功能

- `food`：大胃王，食物使用数量。
- `jumps`：蹦蹦跳跳，跳跃次数。
- `mined`、`placed`、`kills`、`deaths`、`trades`：挖掘、放置、击杀、死亡、村民交易。
- `playtime`、`elytra`、`fishing`、`damage`：在线时间、鞘翅飞行距离、钓鱼、受到伤害。
- 支持 `daily`（日）、`weekly`（周）、`monthly`（月）、`yearly`（年）、`all`（总）五个周期。
- 周期基线保存在主世界持久化数据中，重启服务端不会丢失。
- 读取 `world/stats/*.json` 原版历史统计，离线玩家同样能上榜。
- 首次启动限速读取历史统计并生成持久缓存；以后重启立即恢复榜单，后台只重读变化的玩家文件。
- 默认只显示服务器 `whitelist.json` 中的玩家，并按 UUID 匹配离线历史统计。
- 可独立开关白名单、`bot_`、未知玩家与仅在线玩家过滤。
- 榜单第一行显示当前过滤范围内所有玩家的总和分数。
- 在线时间在原版侧边栏中自动由 tick 换算为整数小时，并以 `647h` 格式显示（`72000 tick = 1 小时`）。
- 输入 `/leaderboard` 可使用彩色、可点击菜单切换自己的个人侧边栏。

## 命令

```
/leaderboard <daily|weekly|monthly|yearly|all> <metric> [limit]
```

Examples:

```
/leaderboard daily food
/leaderboard weekly jumps 20
/leaderboard all elytra 50
```

查看内置帮助：

```
/leaderboard help
```

直接输入 `/leaderboard` 会显示可点击的榜单选择菜单。

每位玩家都可以独立切换自己的原版计分板侧边栏，不需要安装客户端模组：

```
/leaderboard display show daily food
/leaderboard display show all playtime
/leaderboard display off
```

OP 可以在末尾指定在线玩家，帮助对方切换或关闭侧边栏；玩家之后仍可自行切换：

```
/leaderboard display show all playtime <player>
/leaderboard display off <player>
```

玩家可以关闭个人侧边栏中的榜单名字颜色：

```
/leaderboard namecolor on|off|status
```

OP 可以禁用、恢复或查看某类榜单的侧边栏显示：

```
/leaderboard displayfilter fishing disable
/leaderboard displayfilter fishing enable
/leaderboard displayfilter fishing status
```

管理员可以把榜单写入原版计分板并显示为全服共用侧边栏：

```
/leaderboard scoreboard show weekly mined
/leaderboard scoreboard clear
```

管理员可以控制白名单过滤，并查看当前状态：

```
/leaderboard whitelist on
/leaderboard whitelist off
/leaderboard whitelist status
```

管理员可以独立控制 `bot_` 前缀玩家屏蔽：

```
/leaderboard botfilter on
/leaderboard botfilter off
/leaderboard botfilter status
```

白名单中的名字会用于补全历史统计身份。管理员也可以控制无法从用户缓存或白名单解析的 `unknown_` 历史玩家：

```
/leaderboard customfilter on
/leaderboard customfilter off
/leaderboard customfilter status
```

仅显示当前在线玩家：

```
/leaderboard onlinefilter on|off|status
```

查询 Mojang 当前游戏名，并将结果写入服务器用户缓存：

```
/leaderboard lookup 00000000-0000-0000-0000-000000000000
/leaderboard lookup whitelist
```

查看或重新开始后台历史缓存检查：

```
/leaderboard cache status
/leaderboard cache reload
```

## 网站

服务端启动后提供只读排行榜网站，默认地址为 `http://服务器地址:8765/`。可以直接切换最近一日、最近一周、最近一月、总榜，也可以指定任意开始和结束日期。网站支持玩家头像、服务器图标/名称、自定义配色与 Modrinth/GitHub 链接。配置文件为服务端根目录的 `rankboard-web.properties`。

```properties
host=0.0.0.0
port=8765
server-name=auto
website-icon=server-icon.png
```

`server-name=auto` 会读取 `server.properties` 中的 MOTD。排行榜 API 对每个 IP 限制为每秒一次请求。

JSON API：

```
GET /api/rankings?metric=playtime&from=2026-07-16&to=2026-07-20
GET /api/rankings?metric=kills&period=week
GET /api/rankings?metric=playtime&period=all
```

日期区间统计从安装此功能后保存的首个每日快照开始；原版累计统计无法反推出安装前每一天的增量。

个人原版侧边栏互不影响；管理员设置的全服侧边栏对所有玩家生效。个人侧边栏每 30 秒自动刷新一次。

榜单查询只读取内存缓存，并以在线玩家的实时数值覆盖尚未写入文件的统计。历史文件在首次启动时按默认每秒 50 个限速读取，生成服务端根目录的 `rankboard-history-cache.json`；后续重启先加载该缓存，再后台检查文件修改时间。玩家首次进入某个统计周期时会建立基线；首次安装后的周期从服务器第一次启动时开始计算。

原版没有通用的“成功放置方块”历史统计，因此 `placed` 使用所有方块物品的使用次数作为兼容口径；可能包含少数未成功放置的右键操作。

## 构建

安装 JDK 21，然后运行仓库附带的 Gradle Wrapper：

```
./gradlew build
```

Windows：

```
gradlew.bat build
```

成品 JAR 位于 `build/libs/`，版本号和文件名自动加入北京时间构建时间戳，例如 `rankboard-1.0.0+20260716-012813.jar`。
