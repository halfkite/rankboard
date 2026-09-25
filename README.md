# RankBoard

[![License](https://img.shields.io/github/license/halfkite/rankboard)](https://choosealicense.com/licenses/mit/)
[![Modrinth](https://img.shields.io/modrinth/dt/rankboard?color=00AF5C&label=Modrinth%20downloads&logo=modrinth)](https://modrinth.com/project/rankboard)
[![CurseForge](https://img.shields.io/curseforge/dt/1663124?logo=curseforge&label=CurseForge%20downloads&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/rankboard)
[![MC Versions](https://cf.way2muchnoise.eu/versions/For%20MC_1663124_all.svg)](https://www.curseforge.com/minecraft/mc-mods/rankboard)
[![GitHub](https://img.shields.io/github/downloads/halfkite/rankboard/total?color=161616&label=GitHub%20downloads&logo=github)](https://github.com/halfkite/rankboard/releases)

**中文** | [English](#english)

## 依赖

| 名称 | 类型 | 链接 | 备注 |
| --- | --- | --- | --- |
| Fabric Loader | Fabric 必须 | [官方](https://fabricmc.net/use/installer/) | 1.21.x 使用 0.15.11+ |
| Fabric API | Fabric 必须 | [官方](https://fabricmc.net/) | 与目标 Minecraft 版本匹配 |
| NeoForge | NeoForge 必须 | [官方](https://neoforged.net/) | 安装对应游戏版本 |

## 版本支持

注意：已发布的 1.10.5 `mc1.21.x` 单 JAR 在部分 1.21 小版本存在二进制兼容问题；1.10.6 已修复并通过 1.21–1.21.11 Fabric 与 NeoForge 逐版启动、进服测试。26.2/26.3 已通过对应服务端启动及网页 HTTP 测试，尚未完成玩家登录测试。详见 [1.10.6 兼容性排查](docs/compatibility-audit-1.10.6.md)。

| 加载器 | 游戏版本 | 状态 | 最新 RankBoard |
| --- | --- | --- | --- |
| Fabric | 1.21–1.21.11 | 逐版启动、网页与进服测试通过 | 1.10.6 |
| Fabric | 26.1、26.1.1、26.1.2 | 逐版启动、网页与进服测试通过 | 1.10.6 |
| Fabric | 26.2、26.3 | 服务端启动与网页测试通过；玩家登录未测 | 1.10.6 |
| NeoForge | 1.21–1.21.11 | 逐版启动、网页与进服测试通过 | 1.10.6 |
| NeoForge | 26.1.x | 26.1.2 启动、网页与进服测试通过 | 1.10.6 |
| NeoForge | 26.2、26.3 | 服务端启动与网页测试通过；玩家登录未测 | 1.10.6 |

## 文档

- [中文完整文档](docs/rankboard.md)
- [English documentation](docs/rankboard_en.md)

## 下载

- [Modrinth](https://modrinth.com/project/rankboard)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/rankboard)
- [GitHub Releases](https://github.com/halfkite/rankboard/releases/latest)

介绍页面的布局参考了 [Carpet-Igny-Addition](https://github.com/liuyuexiaoyu1/Carpet-Igny-Addition)。

---

## 中文

RankBoard 是一个服务端排行榜模组，支持 Fabric 和 NeoForge。玩家不需要客户端模组即可使用原版计分板和网页查看排行榜。

当前版本：`1.10.6`　|　[完整中文文档](docs/rankboard.md)　|　[English documentation](docs/rankboard_en.md)

### 功能简介

- 17 种统计榜单，支持 daily / weekly / monthly / yearly / all 五种时间周期
- 游戏内原版侧边栏：个人榜、全服榜、轮播榜，进服自动恢复
- 网页排行榜：日期范围查询、在线筛选、主题配色、多服务器切换
- 离线数据读取、后台缓存更新、IP 请求限流、白名单筛选

### 安装

**Fabric：**
1. 安装对应 Minecraft 版本的 Fabric Loader 和 Fabric API。
2. 将 JAR 放入服务器 `mods/` 目录。

**NeoForge：**
1. 安装对应 Minecraft 版本的 NeoForge。
2. 1.21.x 使用 `rankboard-1.10.6+neoforge+mc1.21.x.jar`；26.1.x 使用 `rankboard-1.10.6+neoforge+mc26.1.x.jar`；26.2、26.3 使用对应版本 JAR。
3. 将 JAR 放入服务器 `mods/` 目录。

启动服务器一次生成 `config/rankboard/` 配置目录，修改配置后重启或使用 `/leaderboard config reload`。

1.21 系列需要 Java 21+；26.x 系列需要 Java 25+。

### 快速上手

```text
/leaderboard                          打开可点击的排行榜菜单
/leaderboard mine                     查询自己的统计分数
/leaderboard display show all playtime 显示在线榜侧边栏
/leaderboard help                     查看帮助
```

更多命令、配置和游戏内操作说明请参阅[完整文档](docs/rankboard.md)。

### 构建

需要 JDK 21；构建 26.x 需要 JDK 25。

```text
gradlew.bat build
```

构建产物位于 `build/libs/`。

需要手动构建某个平台/版本家族的单次直接 JAR 时，在 PowerShell 执行（不会嵌套多个小版本 JAR）：

```text
powershell -ExecutionPolicy Bypass -File scripts/build-direct-fabric.ps1 -Target 1.21.x -OutputDirectory release -ModVersion 1.10.6
powershell -ExecutionPolicy Bypass -File scripts/build-direct-neoforge.ps1 -Target 1.21.x -OutputDirectory release -ModVersion 1.10.6
powershell -ExecutionPolicy Bypass -File scripts/build-direct-fabric.ps1 -Target 26.1.x -OutputDirectory release -ModVersion 1.10.6
powershell -ExecutionPolicy Bypass -File scripts/build-direct-neoforge.ps1 -Target 26.1.x -OutputDirectory release -ModVersion 1.10.6
```

### GitHub Actions 发布

发布流程仿照 Carpet-FGA-Addition：先在 GitHub 创建并发布一个 Release（标签使用 `1.10.6`），随后 Actions 会从该标签构建并上传产物。Fabric 与 NeoForge 是两个独立的工作流和发布任务：

- `.github/workflows/release.yml`：只构建和发布 Fabric（1.21.x、26.1.x、26.2、26.3）。
- `.github/workflows/release-neoforge.yml`：构建并发布 NeoForge；每个目标家族只直接编译一次（1.21.x、26.1.x、26.2、26.3）。

两个工作流都只将自己的 JAR 上传到现有 GitHub Release，并分别发布到 Modrinth、CurseForge；校验文件只在本地构建目录生成。需要修复某个已有 Release 时，可在对应工作流选择 **Run workflow**，填写 Release 标签、发布目标和版本筛选。

发布前需要在仓库 **Settings → Secrets and variables → Actions** 添加以下 Secrets：

- `MODRINTH_TOKEN`：Modrinth 项目令牌
- `CURSEFORGE_TOKEN`：CurseForge 作者 API 令牌

可选的 Repository Variables：`MODRINTH_PROJECT_ID`、`MODRINTH_NEOFORGE_PROJECT_ID`、`CURSEFORGE_PROJECT_ID`、`CURSEFORGE_NEOFORGE_PROJECT_ID`。未设置时使用 RankBoard 的默认项目 ID。

GitHub Release 使用工作流自带的 `GITHUB_TOKEN`。令牌不会写入源码；Release 的预发布状态会自动映射为 Modrinth/CurseForge 的 Beta，否则按正式版发布。

---

## English

Warning: The published 1.10.5 `mc1.21.x` single JAR is not binary-compatible with every 1.21 release. Version 1.10.6 fixes this and passed per-version server startup and player-login smoke tests on Fabric and NeoForge 1.21–1.21.11. Minecraft 26.2/26.3 passed server-startup and dashboard HTTP tests, but player login was not tested; see the [compatibility audit](docs/compatibility-audit-1.10.6.md).

RankBoard is a server-side leaderboard mod supporting Fabric and NeoForge. Players do not need a client-side mod to use the vanilla sidebar or the web dashboard.

Current version: `1.10.6`　|　[中文文档](docs/rankboard.md)　|　[English documentation](docs/rankboard_en.md)

### Highlights

- 17 ranking metrics with daily / weekly / monthly / yearly / all periods
- Vanilla sidebar: personal, server-wide, and carousel modes with join restoration
- Web dashboard: date range queries, online filtering, themes, multi-server switcher
- Offline data, background caching, IP rate limiting, whitelist filtering

### Installation

**Fabric:**
1. Install Fabric Loader and Fabric API for the target Minecraft version.
2. Put the JAR in the server `mods/` directory.

**NeoForge:**
1. Install NeoForge for the target Minecraft version.
2. For 1.21.x use `rankboard-1.10.6+neoforge+mc1.21.x.jar`; for 26.1.x use `rankboard-1.10.6+neoforge+mc26.1.x.jar`; use matching JARs for 26.2 and 26.3.
3. Put the JAR in the server `mods/` directory.

Start the server once to create `config/rankboard/`. Restart after editing configuration, or run `/leaderboard config reload`.

Minecraft 1.21 requires Java 21+. Minecraft 26.x requires Java 25+.

### Quick Start

```text
/leaderboard                          Open the clickable ranking menu
/leaderboard mine                     Show your personal scores
/leaderboard display show all playtime Show the playtime sidebar
/leaderboard help                     Show command help
```

See the [full documentation](docs/rankboard_en.md) for all commands, configuration, and in-game operations.

### Building

JDK 21 is required; JDK 25 is required for 26.x builds.

```text
gradlew.bat build
```

Artifacts are written to `build/libs/`.

To build a single direct JAR for a platform/version family in PowerShell (no nested per-minor JARs):

```text
powershell -ExecutionPolicy Bypass -File scripts/build-direct-fabric.ps1 -Target 1.21.x -OutputDirectory release -ModVersion 1.10.6
powershell -ExecutionPolicy Bypass -File scripts/build-direct-neoforge.ps1 -Target 1.21.x -OutputDirectory release -ModVersion 1.10.6
powershell -ExecutionPolicy Bypass -File scripts/build-direct-fabric.ps1 -Target 26.1.x -OutputDirectory release -ModVersion 1.10.6
powershell -ExecutionPolicy Bypass -File scripts/build-direct-neoforge.ps1 -Target 26.1.x -OutputDirectory release -ModVersion 1.10.6
```

### GitHub Actions publishing

The release flow follows Carpet-FGA-Addition: create and publish a GitHub Release first using the `1.10.6` tag. Actions then build from that tag. Fabric and NeoForge are intentionally separate:

- `.github/workflows/release.yml` builds and publishes Fabric only (1.21.x, 26.1.x, 26.2, and 26.3).
- `.github/workflows/release-neoforge.yml` builds and publishes NeoForge; every target family is compiled once directly (1.21.x, 26.1.x, 26.2, and 26.3).

Each workflow uploads only its own JARs to the existing GitHub Release; checksums are generated locally as sidecar files and are not uploaded as extra release assets. To repair an existing Release, use **Actions → Run workflow** in the corresponding workflow and enter the Release tag, destinations, and optional version filter.

Before publishing, add these repository Actions secrets under **Settings → Secrets and variables → Actions**:

- `MODRINTH_TOKEN`: the Modrinth project token
- `CURSEFORGE_TOKEN`: the CurseForge author API token

Optional repository variables are `MODRINTH_PROJECT_ID`, `MODRINTH_NEOFORGE_PROJECT_ID`, `CURSEFORGE_PROJECT_ID`, and `CURSEFORGE_NEOFORGE_PROJECT_ID`. The RankBoard project IDs are used when these variables are omitted.

GitHub Releases use the workflow-provided `GITHUB_TOKEN`. Tokens are never stored in source code. A Release's prerelease flag is mapped to the Beta channel; otherwise the upload is treated as a stable release.
