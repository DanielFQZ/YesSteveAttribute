# 服务端资源消耗接口

资源原子扣费和管理员指令已获用户验收，YSA 接口保留。2026-10-22 按用户要求撤回 YSS 本地起手费用与动作授权扩展，当前 YSS PR3 不读取 resource_costs，也不自动调用本 API。历史验收见 `archive/accepted-2026-10-22/SKILL_COST_TEST.zh-CN.md`，不能作为当前联动已接通的依据。后续先完善自己的 API，由用户与原作者协商最小接入。Molang 不能直接提交扣费。

## 调用方式

```java
var costs = Map.of("法力", 10.0, "精力", 20.0);
var preview = ResourceService.preview(player, costs);
var paid = ResourceService.tryConsume(player, costs);
// 也支持 ResourceService.tryConsume(player, "充能", 10.0)
if (paid.accepted()) {
    // 仅在支付已提交后继续调用方的动作。
}
```

类为 `com.yessteveattribute.forge.ResourceService`，结果为独立 core 中的 `ResourceConsumption.Result`，包含 accepted、costs、before、after、shortages，只读且与输入 Map 脱离。before/after 是全部资源的 ResourceState；costs/shortages 只包含本次请求/不足项。

只能在 Minecraft 服务器线程调用，玩家须存活、未移除且不是旁观者；创造模式也按请求消耗。费用须由调用方从可信服务端配置确定，不提供接收客户端任意扣费或最终属性值的网络入口。

## 原子性和失败语义

- 所有资源同时校验，任一余额不足：accepted=false，列出全部缺口，整笔不扣。没有“扣到零也算成功”或借用浮点容差支付的行为。
- 未知资源（即使费用是 0）、空资源名、null 费用、负数、NaN、无穷大都抛出异常，不修改余额。正费用小到 double 减法无法改变余额时也拒绝。
- 空 Map、已知资源费用 0 允许；恰好足额可扣到 0。正常 double 舍入仍存在。
- 先按当前装备重算上限与有效余额，再试算全部扣费后的派生属性；失败时不发布新快照、不改资源 NBT，也不提交装备刷新。
- 返回 before 是当前装备下的有效余额。例如旧上限 500、换装备后上限 50，则本次 before 为 50，不能按旧 500 支付。拒绝支付不阻止正常的装备轮询随后独立应用新上限。
- 成功时一次发布资源及派生属性、保存 NBT，并同步本人/观察者；HUD 与 ysa.attr 使用新快照。若提交后网络同步抛出异常，服务端记录错误但支付仍返回成功，避免调用方重试导致重复扣费。
- preview 做同样的装备和派生试算，不发布快照或写存档。accepted=true 仅表示此刻可支付，绝不是预留余额；执行时必须重新 tryConsume。装备非法属性仍可能产生诊断消息。
- 每次 tryConsume 都是一次独立交易，不接受动作 ID，不自行做技能去重。连续同 tick 请求会看到上一笔余额；技能调用方仍须按服务端动作生命周期防止重复调用。
- 本接口不是“伤害和资源”的联合事务。扣费成功后的动作失败不会自动退款；不应在每个命中目标上简单调用以实现一次施法费用。

旧 `PlayerAttributes.setResource/addResource` 是管理员/恢复型的截断赋值接口，行为不变。需要“足额才能执行”的调用方应使用 tryConsume。

## 管理指令

均沿用 `/ysa` 管理员权限（等级 2），一次只针对一个玩家：

```text
/ysa resource @s "充能" consume 10
/ysa consume @s "充能" 10 "精力" 20
/ysa consume @s "充能" 10 "精力" 20 preview
```

`consume` 接受 1～8 对资源名/非负数，资源名支持补全，重复资源名报错且整笔不扣；API Map 本身没有这个指令数量限制。尾部 `preview` 是仅预览。聊天显示成功/不足、需求、前后值和缺口。成功或可支付预览返回 1，余额不足返回 0；非法请求产生指令错误。

已通过的验收步骤见 `archive/accepted-2026-10-21/RESOURCE_CONSUMPTION_TEST.zh-CN.md`，项目进度见 `STATUS.zh-CN.md`。
