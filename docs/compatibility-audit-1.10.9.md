# 1.10.9 玩家目录兼容修复与全系列验证

## 原因与范围

用户提供的 Mac 1.21.10 日志报错为：
`NoSuchMethodError: class_3337.method_14653(GameProfile)`。
旧通用包由 1.21.4 API 编译，但 1.21.10 的白名单方法已接收
`PlayerConfigEntry`（Mojang 名称 `NameAndId`），这是游戏版本接口差异，
不是 Mac 专属系统接口错误。

1.10.9 基线按运行时白名单方法的参数类型创建玩家条目，并兼容旧玩家缓存
和新的 Services/nameToIdCache。不能通过捕获异常直接放行来绕过白名单。
源码不直接链接后期版本才有的玩家条目类。

实测还发现旧玩家命令来源由控制台来源附加 entity 创建，保留了控制台权限
及输出目标。现在使用玩家自身命令来源，恢复非 OP 的简洁进服菜单，
同时保留 OP 完整菜单。

## 基线构建

- 加载器：Fabric。
- 运行基线：Minecraft 1.21.10。
- 编译基线：1.21.4，继续直接构建 `mc1.21.x` 单 JAR，没有嵌套子版本包。
- 命令：`pwsh scripts/build-direct-fabric.ps1 -Target 1.21.x -OutputDirectory .tmp/build-1.10.9-baseline`。
- 最终归档：`mod-builds/20260930-144444/rankboard-1.10.9+fabric+mc1.21.x.jar`。
- 初次白名单修复构建归档在 `mod-builds/20260930-144116/`，不作为最终基线交付。

## 2026-09-30 测试记录

测试主机为 Windows，Java 25；尚未在 Mac 主机上实测。

1. `pwsh scripts/test-player-directory.ps1`：旧/新参数、拒绝筛选、缓存写入与保存、
   开发命名和生产 intermediary 命名的无依赖回归测试通过。
2. `npm test`（web 目录）：3 项切服 URL 参数测试通过。
3. `pwsh scripts/smoke-fabric-direct.ps1 -ModJar .tmp/build-1.10.9-baseline/rankboard-1.10.9+fabric+mc1.21.x.jar -Versions 1.21.10 -PlayerDirectoryRegression`：通过。
   - 服务端启动、网页 HTTP 200。
   - 非 OP 收到欢迎语、金色 `[展开榜单信息]`，不收到 OP 语言设置提示。
   - 添加玩家到白名单并开启榜单白名单筛选。
   - 显示个人在线榜，保存，退出重进；客户端实际收到恢复后的计分板数据包。
   - 移出白名单后该玩家不出现在榜单中。
   - 设为 OP 后重进，收到完整菜单和语言设置提示。
   - 服务端日志无 `NoSuchMethodError`、`NoClassDefFoundError` 或 tick-loop exception。

完整日志：`.tmp/fabric-direct-smoke-20260930-144445/1.21.10/`。
客户端标记为 `NON_OP_COMPACT_JOIN_OK`、`DIRECTORY_REJOIN_OK`、
`WHITELIST_DENIAL_OK`、`OP_FULL_JOIN_OK`、`JOIN_OK`。

## 当前进服提示

默认非 OP：

```text
欢迎来到 <服务器名称>
[展开榜单信息]
```

点击按钮执行 `/leaderboard`，打开完整榜单菜单。欢迎语由 `welcome-enabled`
控制；展开按钮由全服 `join-menu-enabled` 及玩家 `/leaderboard joinmenu` 设置
共同控制（玩家独立 joinmenu 设置适用于 Fabric 1.21.x）。NeoForge 和 Fabric 26.x 的展开按钮由全服 `join-menu-enabled` 控制。网页地址提示 `join-web-hint-enabled` 默认关闭。
OP 继续显示完整菜单、语言设置及符合条件的管理提示。

## 同步结果（2026-09-30）

用户确认基线后，修复已同步至两个加载器的全部四个游戏版本系列。
根项目和 NeoForge 项目的版本号均为 1.10.9。
玩家目录桥接实现的两份源码一致；Fabric 1.21.x 使用玩家自身命令来源，
Mojang 命名源码保留原生 `player.createCommandSourceStack()`，不会继承控制台权限。
共用源码还补齐了欢迎语和网页地址的中英文翻译键，并将帮助按钮接入语言文本。

### 最终构建包

每次成功构建均单独归档。下列八个包为本次最终候选；不包含 sources、校验文本文件、
`universal` 后缀或单个小版本发布包。已检查包内版本号为 1.10.9、包含玩家目录桥接类、
嵌套 JAR 数量为零，并与各自归档 manifest 的 SHA-256 一致。

| 加载器 | 游戏系列 | 最终 JAR |
| --- | --- | --- |
| Fabric | 1.21.x | [rankboard-1.10.9+fabric+mc1.21.x.jar](D:/ai/bamgdam/mod-builds/20260930-144444/rankboard-1.10.9+fabric+mc1.21.x.jar) |
| Fabric | 26.1.x | [rankboard-1.10.9+fabric+mc26.1.x.jar](D:/ai/bamgdam/mod-builds/20260930-161627/rankboard-1.10.9+fabric+mc26.1.x.jar) |
| Fabric | 26.2 | [rankboard-1.10.9+fabric+mc26.2.jar](D:/ai/bamgdam/mod-builds/20260930-161913/rankboard-1.10.9+fabric+mc26.2.jar) |
| Fabric | 26.3 | [rankboard-1.10.9+fabric+mc26.3.jar](D:/ai/bamgdam/mod-builds/20260930-162259/rankboard-1.10.9+fabric+mc26.3.jar) |
| NeoForge | 1.21.x | [rankboard-1.10.9+neoforge+mc1.21.x.jar](D:/ai/bamgdam/mod-builds/20260930-155354/rankboard-1.10.9+neoforge+mc1.21.x.jar) |
| NeoForge | 26.1.x | [rankboard-1.10.9+neoforge+mc26.1.x.jar](D:/ai/bamgdam/mod-builds/20260930-162418/rankboard-1.10.9+neoforge+mc26.1.x.jar) |
| NeoForge | 26.2 | [rankboard-1.10.9+neoforge+mc26.2.jar](D:/ai/bamgdam/mod-builds/20260930-162936/rankboard-1.10.9+neoforge+mc26.2.jar) |
| NeoForge | 26.3 | [rankboard-1.10.9+neoforge+mc26.3.jar](D:/ai/bamgdam/mod-builds/20260930-163129/rankboard-1.10.9+neoforge+mc26.3.jar) |

Fabric 构建命令为 `pwsh scripts/build-direct-fabric.ps1 -Target <系列> -OutputDirectory <隔离输出目录>`；
NeoForge 为 `pwsh scripts/build-direct-neoforge.ps1 -Target <系列> -OutputDirectory <隔离输出目录>`。
1.21.x 由 1.21.4 编译，26.1.x 由 26.1.2 编译；26.2、26.3 各自编译。
NeoForge 1.21.x 使用本地 Java 21 工具链，26.x 使用 Java 25。
各包完整构建命令和 SHA-256 位于同目录的 `build-manifest.json`。
所有目标实际构建成功，未进行构建后嵌套合并。

### 测试范围

| 加载器 | 实际游戏版本 | 结果与范围 | 日志目录 |
| --- | --- | --- | --- |
| Fabric | 1.21、1.21.1–1.21.11（12 个） | 全部通过启动、网页、非 OP 简洁菜单、白名单允许/拒绝、个人计分板显示及重进恢复、OP 完整菜单 | `.tmp/fabric-direct-smoke-20260930-160654/` |
| NeoForge | 1.21、1.21.1–1.21.11（12 个） | 同上，全部通过 | `.tmp/neoforge-direct-smoke-20260930-155354/` |
| Fabric | 26.1、26.1.1、26.1.2 | 同上，全部通过 | `.tmp/fabric-direct-smoke-20260930-161627/` |
| NeoForge | 26.1、26.1.1、26.1.2 | 同上，全部通过 | `.tmp/neoforge-direct-smoke-20260930-162418/` |
| Fabric | 26.2 | 启动、网页、实际白名单 API 允许/拒绝通过；未测玩家登录 | `.tmp/fabric-direct-smoke-20260930-162203/` |
| Fabric | 26.3 | 同上 | `.tmp/fabric-direct-smoke-20260930-162259/` |
| NeoForge | 26.2 | 同上 | `.tmp/neoforge-direct-smoke-20260930-162937/` |
| NeoForge | 26.3 | 同上 | `.tmp/neoforge-direct-smoke-20260930-163129/` |

每个日志目录含 `test-results.json`，单版本目录含服务端日志及
`join-smoke.log` 或 `directory-api-smoke.log`。共有 30 组完整登录回归和 4 组无客户端的目录 API 测试。
26.2/26.3 的测试先加载两份离线统计，确认未筛选时两人均出现，再启用白名单，
确认只保留白名单中的玩家；因此不仅是网页 HTTP 或服务端启动检查。

26.2 首次额外测试使用旧的 `world/stats` 目录，导致测试数据未加载；
核对游戏 `LevelResource.PLAYER_STATS_DIR` 后将夹具调整到 `world/players/stats`，
使用同一个已归档 JAR 重试通过，没有修改模组来绕过白名单。
部分早期 NeoForge 客户端日志存在测试客户端对原版/模组扩展数据包的解码警告；
本次断言覆盖聊天、计分板数据包和服务端运行状态，不代表图形客户端完整游玩验证。

### 边界

- 本地测试主机为 Windows，没有在 Mac 主机上实测。
- 本地测试客户端缺少 26.2/26.3 协议元数据，这两个版本不宣称玩家登录通过。
- 本次提交包含 1.10.9 源码及测试记录；尚未发布 1.10.9。
