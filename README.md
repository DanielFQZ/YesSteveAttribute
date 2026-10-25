# YesSteveAttribute（YSA）

YesSteveAttribute 是一个面向 Minecraft 1.20.1 的可配置属性与战斗计算框架。它把属性定义、派生公式、资源、角色成长和战斗计算放在独立的 YAML 配置中，并为 Forge 客户端/服务端和 Paper 服务端提供适配层。

项目的配置结构优先使用英文键，属性 ID、角色 ID 和公式变量可以使用中文。配置文件按 UTF-8 读取。

## 功能概览

- 基础属性、战斗属性、元素权重、抗性、派生属性和运行时字段。
- 数值公式、资源公式、伤害/减伤/暴击公式，以及角色等级成长公式。
- 资源当前值、上限、恢复、衰减和原子消耗。
- 装备 NBT/Lore 属性加成与来源追踪。
- Forge 1.20.1 客户端的实时属性调试 HUD和服务器同步。
- Paper 1.20.1 插件版 YSA，以及独立的 Paper YSS/MythicMobs 适配模块。
- 配置校验、循环依赖检测、reload 诊断和失败回滚。

## 模块

| 模块 | 作用 |
| --- | --- |
| `core` | 与平台无关的配置、公式、属性、资源和战斗计算核心。 |
| `forge` | Forge 47.4.16 / Minecraft 1.20.1 模组，包含客户端 HUD、网络同步和命令。 |
| `paper` | Paper 1.20.1 服务端插件，提供 `/ysa`、服务 API 和服务端调试页面。 |
| `paper-yss` | Paper 侧 MythicMobs 技能机制与 YSA 服务桥接；不包含客户端 YSS 代码。 |

## 快速开始

### Forge

1. 使用 Minecraft 1.20.1、Forge 47.4.16 和 Java 17。
2. 将 Forge 构建出的 `forge/build/libs/yessteveattribute-0.1.0.jar` 放入客户端或服务端的 `mods/`。
3. 首次启动后编辑：

   ```text
   config/yessteveattribute/attributes.yml
   config/yessteveattribute/formulas.yml
   config/yessteveattribute/roles.yml
   config/yessteveattribute/levels.yml
   ```

4. 在游戏中执行 `/ysa reload`。配置通过校验后才会替换当前配置；失败时保留上一份有效配置。
5. 使用 `/ysadebug on` 打开客户端右侧实时属性面板。

### Paper

将 `paper/build/libs/yessteveattribute-paper-0.1.0.jar` 放入 Paper 1.20.1 的 `plugins/`。配置位于：

```text
plugins/YesSteveAttribute/attributes.yml
plugins/YesSteveAttribute/formulas.yml
plugins/YesSteveAttribute/roles.yml
plugins/YesSteveAttribute/levels.yml
```

Paper 版的命令补全支持属性、资源、角色和在线玩家名称。安装 MineBrowse 后，拥有 `ysa.debug` 或 `ysa.admin` 权限的玩家可以执行 `/ysa debug` 打开服务端权威属性页面。

### 从模板开始

四份可复制的模板位于 [`templates/`](templates/)：

- [`attributes.yml`](templates/attributes.yml)：属性和资源绑定。
- [`formulas.yml`](templates/formulas.yml)：派生、伤害、减伤、暴击和运行时公式。
- [`roles.yml`](templates/roles.yml)：角色基础值和等级成长。
- [`levels.yml`](templates/levels.yml)：等级、经验和升阶规则。

完整配置说明见 [Wiki：属性与公式配置指南](wiki/Configuration-Guide.md)。

## 常用命令

Forge 和 Paper 的命令能力略有差异；下面的 Forge 示例使用 Brigadier 的实体选择器语法：

```text
/ysa reload
/ysa profile @s
/ysa role @s "示例战士" 1
/ysa level @s 10
/ysa attr @s "力量"
/ysa resource @s "精力"
/ysa resource @s "充能" set 30
/ysa resource @s "充能" add 10
/ysa consume @s "充能" 10 preview
/ysa combat @s @e[type=minecraft:cow,limit=1,sort=nearest]
/ysa damage @s @e[type=minecraft:cow,limit=1,sort=nearest]
/ysadebug on
```

Paper 版提供 `/ysa reload`、`status`、`attr`、`resource`、`role`、`level`、`profile`、`debug` 和 `ui`，输入 `/ysa` 后可以使用命令补全查看当前可用参数。

## 构建与测试

项目使用 Gradle Wrapper。Java 17 是项目的编译工具链；目标 Paper 开发服可以使用 Java 21。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.5.11-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :core:test :paper:build :paper-yss:build --offline --console=plain
```

Forge 单元测试：

```powershell
.\gradlew.bat :core:test :forge:test --offline --console=plain
```

需要真实 Forge GameTest 时，使用项目已有的 `runGameTestServer` 任务，并遵循 `forge/build.gradle` 中的运行条件。YSS 联动测试是显式可选项，不会作为生产依赖自动打包。

## 文档

- [属性与公式配置 Wiki](wiki/Configuration-Guide.md)
- [当前开发状态](docs/STATUS.zh-CN.md)
- [Paper 1.20.1 与 MythicMobs 说明](docs/PAPER_MYTHICMOBS_1.20.1.zh-CN.md)
- [战斗 API](docs/COMBAT_API.zh-CN.md)
- [资源 API](docs/RESOURCE_API.zh-CN.md)
- [YSS 回退与审计](docs/YSS_AUDIT.2026-10-22.zh-CN.md)

## 项目边界

YSA 的 Paper 适配和 Forge 模组是本项目自己的代码。客户端 `YesSteveSkill-Daniel` 与 `YesSteveModel` 是独立项目；本仓库不把它们作为生产代码依赖，也不继续扩大第三方 YSS 的修改。

当前 Paper YSS 模块只负责服务端的 MythicMobs 机制桥接。Forge 客户端调试 HUD 使用 Forge 网络同步；Paper 服务端调试数据通过 MineBrowse 页面提供，两者不是同一个网络协议。

## English

YesSteveAttribute (YSA) is a configurable attribute and combat calculation framework for Minecraft 1.20.1. Attribute definitions, derived formulas, resources, role growth, and combat formulas are stored in UTF-8 YAML files. Configuration keys use English names, while attribute, role, and formula identifiers may use Chinese.

The repository contains a platform-neutral `core`, a Forge 1.20.1 adapter, a Paper 1.20.1 plugin, and an optional Paper YSS/MythicMobs bridge. It supports attributes, formulas, resources, role and level progression, equipment modifiers, server-authoritative snapshots, debug views, validation, reload diagnostics, and rollback on invalid reloads.

Start with the files in [`templates/`](templates/) and read the bilingual [configuration guide](wiki/Configuration-Guide.md). Forge configuration is stored under `config/yessteveattribute/`; Paper configuration is stored under `plugins/YesSteveAttribute/`. Build with the Gradle Wrapper and run the `core`, `forge`, and Paper tests appropriate to your target.

The project does not include third-party `YesSteveModel` or client-side YSS code. The Paper YSS module is a server-side MythicMobs bridge, while the Forge debug HUD and the Paper MineBrowse page use separate transport paths.
