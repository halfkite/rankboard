# RankBoard

[中文](#中文) | [English](#english)

---

## 中文

RankBoard 是一个服务端排行榜模组，支持 Fabric 和 NeoForge。玩家不需要客户端模组即可使用原版计分板和网页查看排行榜。

当前版本：`1.9.1`　|　[完整中文文档](docs/rankboard.md)　|　[English documentation](docs/rankboard_en.md)

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
2. 将 JAR 放入服务器 `mods/` 目录。

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

### GitHub Actions 发布

发布流程仿照 Carpet-FGA-Addition：先在 GitHub 创建并发布一个 Release（标签建议使用 `1.9.1` 或 `v1.9.1`），随后 Actions 会从该标签构建并上传产物。Fabric 与 NeoForge 是两个独立的工作流和发布任务：

- `.github/workflows/release.yml`：只构建和发布 Fabric（1.21.x、26.1.x、26.2）。
- `.github/workflows/release-neoforge.yml`：只构建和发布 NeoForge（各个 1.21.x、26.1.x 与 26.2 小版本）。

两个工作流都将自己的 JAR 和校验文件上传到现有 GitHub Release，并分别发布到 Modrinth、CurseForge。需要修复某个已有 Release 时，可在对应工作流选择 **Run workflow**，填写 Release 标签、发布目标和版本筛选。

发布前需要在仓库 **Settings → Secrets and variables → Actions** 添加以下 Secrets：

- `MODRINTH_TOKEN`：Modrinth 项目令牌
- `CURSEFORGE_TOKEN`：CurseForge 作者 API 令牌

可选的 Repository Variables：`MODRINTH_PROJECT_ID`、`MODRINTH_NEOFORGE_PROJECT_ID`、`CURSEFORGE_PROJECT_ID`、`CURSEFORGE_NEOFORGE_PROJECT_ID`。未设置时使用 RankBoard 的默认项目 ID。

GitHub Release 使用工作流自带的 `GITHUB_TOKEN`。令牌不会写入源码；Release 的预发布状态会自动映射为 Modrinth/CurseForge 的 Beta，否则按正式版发布。

---

## English

RankBoard is a server-side leaderboard mod supporting Fabric and NeoForge. Players do not need a client-side mod to use the vanilla sidebar or the web dashboard.

Current version: `1.9.1`　|　[中文文档](docs/rankboard.md)　|　[English documentation](docs/rankboard_en.md)

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
2. Put the JAR in the server `mods/` directory.

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

### GitHub Actions publishing

The release flow follows Carpet-FGA-Addition: create and publish a GitHub Release first (a tag such as `1.9.1` or `v1.9.1` is recommended). Actions then build from that tag. Fabric and NeoForge are intentionally separate:

- `.github/workflows/release.yml` builds and publishes Fabric only (1.21.x, 26.1.x, and 26.2).
- `.github/workflows/release-neoforge.yml` builds and publishes NeoForge only (the supported 1.21.x, 26.1.x, and 26.2 versions).

Each workflow uploads only its own JARs and checksums to the existing GitHub Release, then publishes that loader to Modrinth and CurseForge. To repair an existing Release, use **Actions → Run workflow** in the corresponding workflow and enter the Release tag, destinations, and optional version filter.

Before publishing, add these repository Actions secrets under **Settings → Secrets and variables → Actions**:

- `MODRINTH_TOKEN`: the Modrinth project token
- `CURSEFORGE_TOKEN`: the CurseForge author API token

Optional repository variables are `MODRINTH_PROJECT_ID`, `MODRINTH_NEOFORGE_PROJECT_ID`, `CURSEFORGE_PROJECT_ID`, and `CURSEFORGE_NEOFORGE_PROJECT_ID`. The RankBoard project IDs are used when these variables are omitted.

GitHub Releases use the workflow-provided `GITHUB_TOKEN`. Tokens are never stored in source code. A Release's prerelease flag is mapped to the Beta channel; otherwise the upload is treated as a stable release.
