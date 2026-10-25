> 历史快照：2026-10-22 YSS 回退前。本文中的 YSS 协议 2、伤害桥、起手费用及跨模组测试仅对应已撤回的本地版本，不代表当前源码状态。当前入口见 ../../STATUS.zh-CN.md。

# 当前开发状态（会话入口）

最后更新：2026-10-22。

## 已实现且用户验收通过

- 配置加载、reload 聊天诊断、错误回滚；角色/等级、资源余额及恢复/衰减。
- 装备 NBT/Lore、来源追踪、即时装备刷新、派生公式和 `/ysadebug` 实时面板。
- 战斗试算及实际伤害入口：倍率前后诊断、YSA 护甲只结算一次，保留原版其他防护和 Forge 事件。
- YSS 显式 `damage_mode: "ysa_multiplier"`；用户于 2026-10-20 确认实际技能伤害流程正常。
- 资源原子消耗 API 和管理指令：用户于 2026-10-21 反馈“功能正常，继续下一步开发”。已归档，不再要求重复测试。

阶段通过不等于多人、盾牌/PVP/图腾、所有可选边界均已逐项实测。历史资料位于 archive，不能作为当前待办。

## 最近完成：YSS 动画起手资源消耗（2026-10-22 用户验收通过）

实际 YSS 源码为 `E:\JavaProject\YesSteveSkill-Daniel`，不要使用旧 YesSteveSkill 目录构建替换。第三方 YesSteveModel 未修改。

- `animation.<动画名>.resource_costs` 定义整段动画起手费用；编辑器新增“起手资源消耗”，例如 `充能=10;精力=20`，最多 8 项，空白免费。保留原有 VFX/镜头/根运动及伤害字段。
- 服务器读取自己的配置并调用一次 YSA ResourceService.tryConsume；空挥收费，多目标和多段不额外扣费，结束/取消/伤害被拒绝不退款。任一资源不足、未知资源或公式错误，整笔不支付且不授权动作。
- 新增起手/结束/结果包，命中、施法者位移和滞空包携带递增动作序号。重复起手不重扣；旧序号、已结束或被拒绝动作不能重新授权。新动画重播/循环是新动作并再次支付，同 tick 多个新起手拒绝。
- 客户端等服务器许可后推进 YSS 效果；拒绝时保留该次动画的普通攻击抑制。YSM 动画及独立 VFX/Camera 可能仍播放，事件发生在动画已起手之后，本轮不强行撤销第三方表现。
- 每次动作最多授权 1200 个服务器 tick，绑定维度/模型/动画；中途改费撤销旧授权。死亡、打断、关闭攻击模式、切模型、换维度取消许可并清理待执行命中；断线清理玩家状态。
- 正常结束前已登记的延迟命中仍执行，保留登记时的配置，不再次扣费。攻击实际伤害路径仍使用上一阶段的 YSA 接口，direct 和无费用旧配置继续可用。
- 修复显式 null 费用/伤害模式被 DFU 视为缺省的问题；非法配置不能静默回退免费/固定伤害。起手等待后发包使用本次命中采样时刻，避免旧时刻导致窗口校验不一致。
- **YSS 网络协议为 2，联机两端必须升级同版。**YSA 仍沿用已验收资源接口包。

## 验证与实际限制

- Daniel 完整 build 成功：JUnit 14 项全部通过（伤害模式 4、费用 4、动作账本 4、网络包 2）；命中距离 11 项及根运动 28 项回归通过。
- 新发布 jar 参与真实 Forge GameTest：9 项全部通过，同时出现 `YSS integration checks passed` 和 `YSS skill cost checks passed` 标记。
- 新增实际跨模组起手生命周期测试覆盖空挥收费、重复包、余额不足整笔拒绝、伤害/位移/滞空授权、多目标不加收、旧无序号服务端入口不能绕过收费、延迟交付、结束/取消、重播支付、免费配置及变价撤销。
- YSA 生产代码本轮未改；上一轮 59 项 JUnit（core 49 + Forge 10）已通过。本轮 GameTest 同时回归这套生产实现的伤害和资源逻辑。
- 新 YSS jar 未缺少旧包任何 class，保留 mixin/refmap，不打包 YSA/JUnit/测试类。两个仓库 diff whitespace 检查通过。
- **用户已确认本轮功能正常。** 多人网络、真实高延迟、根运动及独立特效等没有单独实测证据。等待应答时动画不暂停，高延迟或极短窗口可能错过早期命中/位移，不补算过去窗口。
- 模型/动画/时机仍由客户端上报，付款许可不是完整的服务端动画真实性、命中几何/窗口/同段去重或技能冷却验证。普通攻击与 Molang ysa.damage 未接管。跨多个动画的整套连招一次计费未实现。
- YSM 专服模型扫描仍有既有 AudioStream 客户端类加载日志，测试服务端成功启动并全通过，不扩大为 YSM 专服完整兼容性验证。

Java 17，Daniel 根目录：`gradlew.bat build --offline --console=plain`。

YSA 根目录：

```powershell
.\gradlew.bat :forge:runGameTestServer '-PyssIntegrationJar=E:/JavaProject/YesSteveSkill-Daniel/build/libs/yessteveskill-1.0.jar' --offline --console=plain
```

日志分别为 Daniel 的 `build/skill-cost-build.log`、YSA 的 `forge/build/skill-cost-integration.log`。无 yssIntegrationJar 参数时跨模组检查跳过，不得据此声称已验证 YSS。带参数的任务强制检查两种 YSS 成功标记与 9 项全通过。

## 当前部署

客户端：`E:\Games\YSM拍摄端\.minecraft\versions\1.20.1-Forge_47.4.16`。

- YSS `mods/yessteveskill-1.0.jar` SHA256：`F7D445D465EB8DB3552EDFB8EBF4C0CE37BA1C6257787AC84E8A535E0B5BB088`，与本轮构建一致。
- YSS 旧包备份：`E:\JavaProject\backups\YesSteveSkill\20261021-002043-skill-resource-costs\yessteveskill-1.0.jar`。
- YSA `mods/yessteveattribute-0.1.0.jar` SHA256：`D1FBDD4C7F0860D081C67B8ACE3A7F56EB0A699F175AF9BC9A16502917E6E13A`，沿用已验收的原子消耗包。
- 本轮未改客户端 hit.json 或属性配置。完全重启游戏加载 jar，`/ysa reload` 不能更新代码。
- 早前旧 YesSteveSkill 目录的草稿已撤回，归档在 `E:\JavaProject\backups\YesSteveSkill\20261020-ysa-superseded-source`，不是当前构建来源。

## 文档导航

- `SKILL_COST_TEST.zh-CN.md`：唯一
- `RESOURCE_API.zh-CN.md`：资源支付接口及调用方责任。
- `COMBAT_API.zh-CN.md`：伤害接口及联动边界。
- `DEVELOPMENT_PLAN.zh-CN.md`：整体架构与计划；规划不等于已实现。
- `E:\JavaProject\YesSteveSkill-Daniel\docs\skill-resource-costs.md`：本轮配置、动作协议和边界。
- `archive/accepted-2026-10-19/`、`accepted-2026-10-20/`、`accepted-2026-10-21/`、`accepted-2026-10-22/`：已通过的历史阶段，不是待办。\n