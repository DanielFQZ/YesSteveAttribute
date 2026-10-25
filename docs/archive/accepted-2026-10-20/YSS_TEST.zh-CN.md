> 已通过并归档（2026-10-20）：用户反馈“测试完成，没有问题。”下文是当时的验收步骤，不再作为当前待办；此确认不扩展为所有可选、多人与边界场景均已逐项实测。当前状态见 [STATUS.zh-CN.md](../../STATUS.zh-CN.md)。

# 当前验收：YSS 技能伤害接入 YSA

状态：代码、构建和服务端跨模组测试通过，等待真实技能动画验收。此前实际伤害命令测试已归档，不必重做。

## 1. 重启并配置一个技能段

完全退出并重启拍摄端。打开你原来使用的 YSS 命中编辑器，选择 `鸣潮_心月狐` 的一个可稳定命中的攻击动画，只让一个测试段参与本轮命中：

- 伤害模式：`YSA倍率`。
- 伤害倍率：`0.1`（10%，不是 10）。
- 命中重置：`once`，避免 interval 在一次动画内持续扣血。
- 先用即时出伤，保留原来的骨骼、时间窗和 VFX 碰撞盒设置。
- 保存（必要时再次确认），重新打开确认模式和倍率保留。

先备份自己的 hit.json；测试完可恢复。没有自动修改你的技能配置。JSON 对应是在已有段内加入 `"damage_mode":"ysa_multiplier"`，并把该段 `"damage"` 设为 `0.1`，不要用这两个字段替换整个段或文件。

本地单人世界的服务端按文件时间/大小刷新段配置；若客户端仍使用旧判定，退出重进世界。`/ysa reload` 只刷新 YSA。多人服务器需同时更新两端 YSS jar，并把相同 hit.json 放到服务器；仅客户端保存不足以更新远端服务器。

## 2. 实际技能命中一次

使用示例配置，清空双手和带 YSA 属性的护甲，避免残留力量/武器伤害/暴击加成。执行：

```text
/ysa role @s "示例战士" 1
/ysa attr @s "力量"
/summon minecraft:cow ~ ~ ~2 {Tags:["ysa_yss_test"],NoAI:1b,PersistenceRequired:1b,Attributes:[{Name:"minecraft:generic.max_health",Base:200.0d}],Health:200.0f}
/ysa combat @s @e[type=minecraft:cow,tag=ysa_yss_test,sort=nearest,limit=1] 0.1
```

空装备时力量应为 10.6，倍率前基础伤害 69.96，试算结果约 6.996。若实际配置与示例不同，以试算值为准，先排除暴击和目标状态的影响。

面对测试牛，触发刚配置的 YSS 技能，让一个段命中一次，然后查询：

```text
/data get entity @e[type=minecraft:cow,tag=ysa_yss_test,sort=nearest,limit=1] Health
```

预期生命约 `193.004`，单次扣 `6.996`；不应只扣 `0.1`，也不应附加一笔固定伤害。本步通过真实技能触发，不用 `/ysa damage` 代替。

若数值多扣，先检查是否连招触发了其他段/动画、是否使用 interval、是否附带普通攻击；记录触发次数和前后生命。原版无敌帧仍存在，连续命中不保证每次都接受。

## 3. 验证护甲只结算一次

对同一只牛设置原版护甲和 YSA 护甲，等待至少 1 秒：

```text
/attribute @e[type=minecraft:cow,tag=ysa_yss_test,sort=nearest,limit=1] minecraft:generic.armor base set 20
/item replace entity @e[type=minecraft:cow,tag=ysa_yss_test,sort=nearest,limit=1] weapon.mainhand with minecraft:stick{YSAAttributes:["护甲: 20"]}
```

再让同一个技能段命中一次，查询 Health，预期本次减少约 `1.996`；若上一步只命中一次，剩余约 `191.008`。原版护甲 20 不应再次减少这笔伤害。

## 4. 兼容性与反馈

- 改回“固定值”，伤害设为 10，确认原有技能仍可工作；其伤害遵循原版护甲规则，不再按 YSA 属性公式计算。
- 保存后关闭并重开编辑器，原来的 VFX 碰撞盒、镜头、位移和段时序设置应保留。
- 可选：在 YSA 倍率模式将倍率设为 0.01，当前护甲使公式结果为负，应不扣血、不治疗，也不执行伤害后的击退/击飞。
- 注意：配置倍率为 0 是兼容旧行为的纯位移段，不调用 YSA；要测试公式非正应使用上面的正倍率 0.01。
- YSA 缺失/API 失配/公式调用失败会在聊天提示（10 秒限频），不会改用固定伤害。停帧/打断等前置机制不属于该回退控制。不要为了测试缺失依赖而直接改动正式存档。

回报第 2、3 步生命值与编辑器模式截图即可。真实技能流程通过后，再推进后续联动；多人授权、Molang 伤害和资源消费尚不属于本轮完成范围。
