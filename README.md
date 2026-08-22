# RankBoard

[中文](#中文) | [English](#english)

---

## 中文

RankBoard 是一个服务端排行榜模组，支持 Fabric 和 NeoForge。玩家不需要客户端模组即可使用原版计分板和网页查看排行榜。

当前版本：`1.9.0`　|　[完整中文文档](docs/rankboard.md)　|　[English documentation](docs/rankboard_en.md)

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

仓库中的 `.github/workflows/release.yml` 可以在 GitHub Actions 中构建并发布 Fabric 版本。进入 **Actions → Build and publish RankBoard → Run workflow**，填写版本号和 Minecraft 版本即可。

发布前需要在仓库 **Settings → Secrets and variables → Actions** 添加以下 Secrets：

- `MODRINTH_TOKEN`：Modrinth 项目令牌
- `CURSEFORGE_TOKEN`：CurseForge 作者 API 令牌

GitHub Release 使用工作流自带的 `GITHUB_TOKEN`。令牌不会写入源码；工作流默认构建 Fabric 26.2，并发布为 Beta。

---

## English

RankBoard is a server-side leaderboard mod supporting Fabric and NeoForge. Players do not need a client-side mod to use the vanilla sidebar or the web dashboard.

Current version: `1.9.0`　|　[中文文档](docs/rankboard.md)　|　[English documentation](docs/rankboard_en.md)

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

The repository includes `.github/workflows/release.yml`. Open **Actions → Build and publish RankBoard → Run workflow**, then enter the release version and Minecraft version.

Before publishing, add these repository Actions secrets under **Settings → Secrets and variables → Actions**:

- `MODRINTH_TOKEN`: the Modrinth project token
- `CURSEFORGE_TOKEN`: the CurseForge author API token

GitHub Releases use the workflow-provided `GITHUB_TOKEN`. Tokens are never stored in source code. The workflow defaults to Fabric 26.2 and a Beta release.
