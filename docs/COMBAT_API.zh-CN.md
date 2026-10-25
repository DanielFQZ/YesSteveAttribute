# 服务端伤害接口与接入边界

当前实现状态见 `STATUS.zh-CN.md`。下述接口和公式说明以已验收的独立服务端 API 为基线；工作区仍有未收敛的 Molang/伤害类型草稿，不能把它们视为已验收契约。

## 接口

```java
CombatService.Preview preview = CombatService.preview(attacker, target, multiplier, roll);
CombatService.Applied applied = CombatService.attack(attacker, target, multiplier);
```

`attacker` 为 `ServerPlayer`，`target` 为 `LivingEntity`。只能在同一服务器线程调用，双方必须同维度、存活且不是旁观者。`preview` 接受确定性随机值 `[0,1)`；`attack` 在服务器抽取随机值。倍率为有限非负数，YSA 读取当前服务端属性，调用方不能提交“最终伤害”。

战斗前立即刷新攻击者和玩家目标的装备；非玩家目标按默认值、等级 0、六槽装备生成临时快照，不建立玩家角色或资源存档。临时资源按 initial 初始化。生物原版护甲值不自动映射为 YSA 护甲；需明确配置 YSA 装备属性。

公式按模板计算基础伤害倍率、元素分配、YSA 减伤、暴击与最终值。不存在“物理/元素类型”的第二套隐式切换，此版按已配置权重公式计算；Molang 类型参数的接入语义将在 YSS 联调阶段确定。

## 原版伤害语义

伤害类型 `yessteveattribute:calculated` 通过 datapack 注册，加入 `minecraft:bypasses_armor`，`replace: false`。已由 YSA 结算护甲，因此不再经过原版 armor/toughness 减伤，也不损耗原版护甲耐久。`scaling: never` 避免游戏难度再次缩放提交值。

没有加入其他 bypass 标签：盾牌、抗性效果、保护附魔、吸收生命、无敌帧、创造免疫、PVP/队伍规则、Forge 伤害事件、图腾和死亡处理仍由原版链路执行。直接调用 YSA 时保留原版 hurt 的击退/受伤表现。

最终值 ≤0 不调用 hurt、不治疗；非有限值、超出正 float 范围或转换后为零的极小值报错。正值只调用一次 `target.hurt(new DamageSource(type, attacker), amount)`，不调用 `setHealth`，不重置 `invulnerableTime`，不在拒绝后重试。正常 double→float 舍入发生在原版边界。

`Applied` 同时保留公式结果、提交 float、入口返回状态、调用前后的生命/吸收值。`ACCEPTED` 仅表示 hurt 返回 true；事件修改、吸收、图腾等仍会让净生命差与提交值不同。`REJECTED` 不推断唯一原因，`NON_POSITIVE` 表示没有调用原版入口。

## YSS 接入状态

2026-10-22 按用户要求，`E:\JavaProject\YesSteveSkill-Daniel` 恢复到已发 Camera/VFX 联动 PR #3（`a8e1852`）。当前源码没有 YSA 伤害反射桥、damage_mode、resource_costs 或动作授权接口，协议为 1；旧的额外字段不能让当前 YSS 调用本 API。

YSA 伤害和资源 API 保留，未来由技能方在服务端选择目标和时机后显式调用，禁止客户端提交最终伤害。失败后不能隐式再走另一条 hurt 造成重复结算。动作真实性、命中去重、冷却及表现属于调用方责任，需双方约定。

此前用户验收与跨模组测试仅对应已撤回的本地扩展，历史细节见 `archive/superseded-2026-10-22-yss-integration/COMBAT_API.before-yss-rollback.zh-CN.md` 和 `archive/accepted-2026-10-20/YSS_TEST.zh-CN.md`。可选 YSS GameTest 尚未适配此回退，不能用于当前版本背书。

Molang `ysa.damage` 当前草稿反射目标 queueMolangDamage 已不存在，不能视为已接通。后续先收敛独立 API、测试和类型语义，由用户联系原作者协商接入；不在第三方 YSS 继续扩建。
