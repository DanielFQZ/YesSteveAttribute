> 历史快照：YSS 本地扩展已于 2026-10-22 撤回。本文联动和协议说明不代表当前源码。

# 服务端伤害接口与接入边界

当前实现状态见 `STATUS.zh-CN.md`。此文档是接口说明，不是历史验收记录。

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

没有加入其他 bypass 标签：盾牌、抗性效果、保护附魔、吸收生命、无敌帧、创造免疫、PVP/队伍规则、Forge 伤害事件、图腾和死亡处理仍由原版链路执行。直接调用 YSA 时保留原版 hurt 的击退/受伤表现；经 YSS 调用时应用下述 YSS 位移策略。

最终值 ≤0 不调用 hurt、不治疗；非有限值、超出正 float 范围或转换后为零的极小值报错。正值只调用一次 `target.hurt(new DamageSource(type, attacker), amount)`，不调用 `setHealth`，不重置 `invulnerableTime`，不在拒绝后重试。正常 double→float 舍入发生在原版边界。

`Applied` 同时保留公式结果、提交 float、入口返回状态、调用前后的生命/吸收值。`ACCEPTED` 仅表示 hurt 返回 true；事件修改、吸收、图腾等仍会让净生命差与提交值不同。`REJECTED` 不推断唯一原因，`NON_POSITIVE` 表示没有调用原版入口。

## YSS 接入状态

配套源码为 `E:\JavaProject\YesSteveSkill-Daniel`，不是旧 `YesSteveSkill` checkout。段缺省或 `damage_mode: "direct"` 时沿用 `playerAttack + segment.damage()` 固定伤害；显式 `ysa_multiplier` 时把 `damage` 作为倍率交给 `CombatService.attack`，仅调用一次，不在失败或拒绝后回退旧 hurt。

反射桥是可选依赖，调用前验证 attack/application/status 方法及 ACCEPTED、REJECTED、NON_POSITIVE 返回协议；不是版本号握手。缺失或调用错误在聊天限频反馈。客户端和服务器必须使用同一新版 YSS。当前网络协议 2 会拒绝旧协议联机；不要使用旧编辑器重存新字段。

保留 Daniel 的位移策略：无自定义水平击退时恢复命中前 x/z，配置击退时覆盖 x/z，配置击飞时覆盖 y，否则保留原版 hurt 的纵向变化。YSA 返回拒绝或非正值时不执行后续位移；段配置 damage=0 保持原有纯位移语义，不调用 YSA。停帧/打断等前置技能机制独立执行，不能把不扣血描述为整个技能完全取消。

服务器读取自己的配置倍率并检查距离/视线及每 tick 限频；模型、动画和时机仍部分来自客户端上报，客户端承担几何判定与去重。当前新增动作序号及资源支付许可，收费动作须通过服务端原子扣费后才能提交效果；完整动画真实性、独立命中窗口/同段去重仍未实现。不能据此宣称已完成多人防作弊。

已通过真实发布 jar 的单目标执行器 → YSA 跨模组服务端测试，包括伤害、护甲、无敌帧、取消、非正结果、速度和 direct 回归。本轮真实技能流程已获用户验收，历史步骤见 `archive/accepted-2026-10-20/YSS_TEST.zh-CN.md`；多人网络及多段/延迟时序尚无单独实测证据。Molang `ysa.damage` 仍为桩，其物理/元素参数不能视为已接通；后续需基于服务端授权动作接入。

资源支付通过独立 `ResourceService` 接入 YSS 动画起手，见 `RESOURCE_API.zh-CN.md` 和 `SKILL_COST_TEST.zh-CN.md`。支付与伤害仍不是联合事务；空挥、取消、伤害被拒绝不退款。正常结束前登记的延迟命中保留配置并执行，不再次支付。\n