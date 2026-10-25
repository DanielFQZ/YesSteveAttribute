# 当前开发状态（会话入口）

最后更新：2026-10-26，YSA 代码、README 和开发者 Wiki 已提交并推送。

## 当前决策与边界

用户要求停止扩大第三方 YSS 修改：只保留此前提交 PR 的 Camera/VFX 联动部分。后续优先完善自己的 YSA，整理独立 API、文档和发布内容，再由用户联系原作者协商接入。不自动继续原 YSS 扩展计划，不自行发新 PR、推送或部署。

- YSS 唯一工作目录：`E:\JavaProject\YesSteveSkill-Daniel`。
- YSS 分支 `codex/public-combat-api-clean`，HEAD `a8e18528b02c5d29b63c77527c316d005461b76f`，与上游 PR #3 head 一致；工作树干净。
- 上游 `TT432/yessteveskill` 的 `master` 当前为 `f8320d63164b9a06d2fa111c54adb1ace578a29b`；PR 内容尚不在该分支祖先链内。未确认 PR 开放/关闭状态。
- 保留公共战斗事件、Camera 输入/攻击辅助/根运动联动、VFX HitBlock 接入及其范围/坐标修正。相对上游为 4 个提交、34 个文件、998 行增加、54 行删除。
- 已撤回 YSS 本地未提交的 YSA 伤害模式/反射桥、动画起手费用、动作授权协议、Molang 伤害草稿和相关测试/文档。当前 YSS 协议恢复为 **1**。
- 禁止修改第三方 `YesSteveModel`；不要从旧 `YesSteveSkill` 目录构建替换。

详细发现和验证依据见 [YSS 回退与审计报告](YSS_AUDIT.2026-10-22.zh-CN.md)。报告是问题清单，不代表获准修改 YSS。

## YSA 保留的开发成果

配置加载、reload 聊天诊断与回滚，角色/等级，装备 NBT/Lore 与来源追踪、即时刷新，派生公式，资源余额/恢复/衰减，`/ysadebug` 面板，服务端战斗试算/伤害 API，以及资源原子消费 API 均保留。此前用户对相应阶段的功能验收仍有效；不因 YSS 回退要求重新进行全部历史验收。

本轮新增 Paper 命令执行/动态补全和只读服务端调试页面；核心测试与 Paper 构建已重新运行。历史多人、平台、边界场景仍以实际证据为准。

## 当前不能视为已接通的内容

- YSS 当前源码不再读取 `damage_mode: "ysa_multiplier"`、`resource_costs`，没有 YSA 支付授权或动作序号协议。历史验收对应的是已撤回的本地 YSS 扩展。
- YSA 中 `ysa.damage`、`CombatDamageType` 等仍有上一轮未完成草稿；当前 Molang 反射调用的 `YssAttackService.queueMolangDamage` 已不存在，不能视为可用伤害入口。
- YSA 的可选 `-PyssIntegrationJar=...` GameTest 仍假设撤回前的 YSS 接口。**不要使用当前 PR3 jar 运行旧联动验收并期待通过，也不要重新扩大 YSS 来迎合旧测试。**后续应先收敛自己的测试边界。
- 本轮 YSA 代码已整理为提交 `76695d4` 并推送到 `origin/main`；README 和双语开发者 Wiki 位于仓库根目录和 `wiki/`。

## 源码验证与部署差异

YSS 本轮在 Java 17 下执行 `gradlew.bat clean build --offline --console=plain` 成功：距离回归 11 项、根运动回归 28 项通过；JUnit 为 `NO-SOURCE`，不属于 JUnit 或真实游戏/专服验证。已检查 mixin/refmap 存在、撤回的 YSA 类未混入新包。

- 新构建 YSS：`E:\JavaProject\YesSteveSkill-Daniel\build\libs\yessteveskill-1.0.jar`。
- SHA256：`BB44D8A333C6511CAF98F62D275CC774D40342372FDB99EF303C0F8ACC085A5B`。
- 客户端目录：`E:\Games\YSM拍摄端\.minecraft\versions\1.20.1-Forge_47.4.16`。
- 客户端 YSS 已备份并替换为 PR3 clean jar；旧 SHA256 为 `F7D445D465EB8DB3552EDFB8EBF4C0CE37BA1C6257787AC84E8A535E0B5BB088`，当前部署 jar SHA256 为 `BB44D8A333C6511CAF98F62D275CC774D40342372FDB99EF303C0F8ACC085A5B`。客户端 YSS 后续不再扩大修改。
- 客户端 YSA 既有记录：SHA256 `D1FBDD4C7F0860D081C67B8ACE3A7F56EB0A699F175AF9BC9A16502917E6E13A`；本轮未重新核验或替换它。
- Paper 目标服已确认是 Paper 1.20.1、Java 21、MythicMobs 5.7.2；本轮未把新构建的 YSA/YSS Paper 插件安装到目标服。
- 目标服已于 2026-10-25 14:10 启动并监听 25566；YSA、YSS、MineBrowse、MythicMobs 均完成启用，启动日志未发现 YSA/YSS 异常。
- 已在目标服插件目录备份旧同名文件（备份目录为 D:\MCserver\苍穹彼岸高版本开发端\deployment-backups\20261025-140941），并安装 yessteveattribute-paper-0.1.0.jar、yessteveskill-paper-0.1.0.jar。
- 当前尚未做真实玩家连接、/ysa 命令和 MythicMobs 技能运行验收；首启后如需让已有 MythicMobs 技能识别新机制，应执行一次 /mm reload。
- 本轮已推送 GitHub；没有修改客户端配置。不要把 `/ysa reload` 当成更新 jar 的方式。

## 当前 Paper 适配进度

- 已加入独立 `paper` 模块：复用 `core`，提供配置初始化、原子 reload、`/ysa` 动态补全、属性/资源/角色/等级/profile 查询与管理、玩家属性快照、资源查询/修改和服务端调试数据接口。
- `/ysa debug` 与 `/ysa ui` 会打开 `ysa_debug` 临时页面；页面沿用 EmpyreanUI 的服务端业务适配模式，通过 MineBrowse 提供显示和只读 RPC，明确标注服务端权威数据。EmpyreanUI 0.1.0 当前没有公开第三方注册 API，因此 YSA 不依赖其内部实现。
- 已加入独立 `paper-yss` 模块：仅依赖 YSA 公共服务和 MythicMobs 5.7.2，使用官方机制/条件事件注册 `ysadamage` 与 `ysaattribute`，不包含 Forge 或客户端 YSS 代码。
- 本地构建验证：`:core:test`、`:paper:build`、`:paper-yss:build` 均成功。当前部署到目标服的 `paper/build/libs/yessteveattribute-paper-0.1.0.jar` SHA256 为 `B9B98DAB20C6D31A15806D47098F8FFAADC54C7EED0CB28F1EF96394373D888E`；`paper-yss/build/libs/yessteveskill-paper-0.1.0.jar` SHA256 为 `491C8955BCC8916C699C479ABCA3C0312A26EF418006304166A2D7A0FAACEB65`。YSA JAR 未打包 MineBrowse/Gson；运行时使用服务端已有 MineBrowse。当前还没有真实服务器技能/伤害或页面验收。

## 后续顺序

1. 在隔离测试服安装两个 Paper jar，验证插件启用、`/ysa reload` 成功/失败回滚和 MythicMobs 机制注册。
2. 补齐 Paper 玩家存档、在线重算和目标属性映射，再做 MythicMobs 实际伤害/条件测试。
3. 用户前台启动目标服后，验证 `/ysa` 补全、`/ysa debug` 页面及服务端配置/快照显示；UI 不进入 YSA core，也不修改客户端 YSS。
4. 继续完善 Paper 玩家存档、在线重算和真实 MythicMobs 技能验收。
5. 用户联系原作者后，再按双方约定提供最小接入点；YSS 审计问题可供沟通，不自行开始全面重构。

## 文档导航

- `YSS_AUDIT.2026-10-22.zh-CN.md`：本轮回退、全仓静态审计与部署差异。
- `RESOURCE_API.zh-CN.md`、`COMBAT_API.zh-CN.md`：自己的 API 与调用方责任。
- `DEVELOPMENT_PLAN.zh-CN.md`：整体规划；第三方接入以本次边界为准。
- `archive/accepted-*`：此前用户验收通过的历史测试，不是当前待办。
- `archive/superseded-2026-10-22-yss-integration/`：回退前状态快照，不能作为当前源码能力说明。\n
