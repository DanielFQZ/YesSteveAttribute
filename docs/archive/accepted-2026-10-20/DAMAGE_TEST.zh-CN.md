> 历史归档：用户已确认实际伤害阶段测试正常（2026-10-20）。下文保留当时验收说明，不是当前待办；可选边界场景不代表逐项实测。当前状态见 ../../STATUS.zh-CN.md。

# 当前验收：实际伤害入口

状态：本轮新增功能，尚待用户实机验收。已通过的旧阶段说明在 `archive/`；当前总体状态见 `STATUS.zh-CN.md`。

## 更新与基础扣血

完全退出并重启测试客户端加载新 jar。新增数据包伤害类型需要重启后进入世界；`/ysa reload` 只重载 YSA 配置。

先确认攻击者没有带 YSA 属性的主手、副手或护甲；可先执行 `/ysa attr @s "力量"`，空装备的示例战士 1 级应为 10.6。测试命令不会自动清除装备，装备栏中的力量会正常叠加到伤害。

在测试世界清空双手、卸下带 YSA 加成的装备，使用示例战士 1 级。以下命令创建一只不会乱跑的 200 生命测试牛，不改动现有生物：

```text
/ysa role @s "示例战士" 1
/summon minecraft:cow ~ ~ ~2 {Tags:["ysa_damage_test"],NoAI:1b,PersistenceRequired:1b,Attributes:[{Name:"minecraft:generic.max_health",Base:200.0d}],Health:200.0f}
/ysa combat @s @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] 0.1
/ysa damage @s @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] 0.1
```

清空攻击者主手、副手和装备后，示例战士 1 级力量为 10.6，倍率前基础伤害为 69.96，倍率 0.1 时本次基础伤害为 6.996。`combat` 不扣血；`damage` 应显示倍率前/倍率后基础伤害、公式值、提交值、入口接受状态，以及生命约 200 → 193.004。攻击者暴击为 0 时结果确定；实际攻击随机数由服务端生成，不提供手动 roll 参数。`combat` 仍支持末尾随机值进行确定性试算。

装备属性会计入攻击者六个装备槽。比如角色力量 10.6、主手装备力量 10、副手装备力量 5 时，倍率前基础伤害是 `(10.6 + 10 + 5) × 6.6 = 168.96`，倍率 0.1 后显示 16.896；这正是装备未清空时的正常结果。

## YSA 护甲与原版护甲

间隔至少一秒后，给同一只牛增加原版护甲和 YSA 护甲：

```text
/attribute @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] minecraft:generic.armor base set 20
/item replace entity @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] weapon.mainhand with minecraft:stick{YSAAttributes:["护甲: 20"]}
/ysa combat @s @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] 0.1
/ysa damage @s @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] 0.1
```

YSA 护甲减伤为 20 × 0.25 = 5，公式最终值约 1.996；原版护甲不再次削减这笔伤害。非玩家目标此版使用配置默认值 + 六槽装备，未分配角色/等级，也不映射原版 armor 到 YSA 护甲。普通原版攻击继续遵循原版护甲规则。

## 零伤害、负值与防护

将倍率改为 0（当前牛有 YSA 护甲，公式结果为负）：

```text
/ysa damage @s @e[type=minecraft:cow,tag=ysa_damage_test,sort=nearest,limit=1] 0
```

应显示“公式结果非正数，未调用伤害入口”，生命不变，不转为治疗，也不设置新的无敌帧。

相同伤害在原版无敌帧内重复提交会被拒绝；更大伤害在无敌帧内遵循原版差额规则，YSA 不重置无敌帧。正常聊天手动输入通常已超过窗口，可用命令方块或服务端调用验证同 tick 重复。

原版抗性提升、保护附魔、盾牌、吸收生命、创造模式/无敌、PVP 与队伍规则仍生效；Forge 事件仍可取消或修改伤害。因此“公式值/提交值”和“实际净扣血”可以不同。接受状态来自 `LivingEntity.hurt`，不代表必然扣血；聊天同时展示净生命和净吸收变化。图腾等会改变净生命差，应以游戏实际行为判断。

可移除牛手中测试木棍后施加抗性提升 I，倍率 0.1 时正常净伤害约 6.996 × 0.8 = 5.5968（无其他效果且已过无敌帧）。带吸收时先扣吸收值。

## 即时装备刷新与范围

切换带 `武器伤害`/`力量` 的装备后立即执行试算或实际伤害，应使用新装备，无需等每秒的 HUD 刷新。装备导致公式计算失败时中止本次攻击，不回退到旧武器数值继续扣血。目标的非法装备属性会被整件忽略并在命令中提示。

`/ysa damage <攻击玩家> <单个有生命目标> [倍率]` 仅管理员可用，允许自己作为测试目标；要求攻击者/目标存活、同维度、非旁观者。命令是管理员测试入口，不检查技能射程或视线。普通鼠标攻击尚未改为 YSA 结算，Molang `ysa.damage` 尚未接通；YSS 技能段需显式设置 `damage_mode: "ysa_multiplier"` 才走 YSA。

## 自动验证

单元/数据测试：`gradlew :core:test :forge:test :forge:jar --offline --console=plain`，本轮 53 项通过（core 43 + Forge 10）；已检查发布 jar 包含伤害类型、语言资源、core 和 SnakeYAML，不包含 GameTest/JUnit 测试类。

真实 Forge 服务器集成测试：`gradlew :forge:runGameTestServer --offline --console=plain`。测试源码在 `forge/src/gameTest`，世界与日志在 `forge/build/gametest-run`，不会操作测试客户端存档，也不把测试类打入发布 jar。任务在启动前清空结果日志，结束时强制检查 4 项 GameTest 全部通过，避免 Forge 启动失败却返回成功码。

4 项服务端集成测试已通过，覆盖护甲只结算一次、同 tick 装备刷新、吸收/抗性/无敌帧、非正伤害/无敌实体、Forge 取消且不重试、死亡与击杀归属。实际客户端的 UI、多人盾牌/PVP/队伍/图腾等组合仍需按使用场景实测，不能把这些场景视为本次自动覆盖。

当前本地 YSM 开发依赖在专用服务器扫描模型时会记录客户端 `AudioStream` 类的 Dist 错误；本次 GameTest 服务端仍完成启动且全部通过。这不代表 YSM 专用服务器兼容性已验证，本轮未修改 YSM。
