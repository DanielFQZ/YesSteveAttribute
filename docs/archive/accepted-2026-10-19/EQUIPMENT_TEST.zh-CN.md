> 历史归档：本阶段已获用户功能验收通过。以下是当时的测试说明，不代表当前实现状态、当前部署包或待办；不得据此重新要求验收或判定功能缺失。当前状态以 ../../STATUS.zh-CN.md 为准。未逐项举证的多人/边界场景不等同于已实测。

# 装备 NBT/Lore 验收

本轮新增装备属性读取、来源查询和自动刷新。完全退出并重启客户端后进入允许命令的测试世界；`/ysa reload` 只能刷新配置，不能加载新 jar。测试物品用 `/give` 创建，不覆盖现有装备。

## 1. Lore 基础加成与同步

先清空双手、卸下其他带 YSA 属性的装备，执行：

```text
/ysa role @s "示例战士" 10
/ysa attr @s "力量"
/give @s minecraft:iron_sword{display:{Lore:['{"text":"力量: 10","color":"gold","italic":false}']}} 1
```

默认模板的 10 级力量约为 33.8864302332。将新铁剑拿在主手，保持游戏运行至少 1 秒后执行：

```text
/ysa attr @s "力量"
/ysmclient molang execute ysa.attr('力量')
```

力量应比空手增加 10，约 43.88643；`/ysa attr` 来源应新增 `lore:minecraft:iron_sword/mainhand/display.Lore[0]=10.0`。Molang 返回 float，允许小数舍入差异。切换空手或移入普通背包，约 1 秒后恢复；按 F 移至副手仍应加 10，来源槽位变为 `offhand`。无需 reload。

## 2. NBT、多槽位与资源联动

```text
/give @s minecraft:stick{YSAAttributes:["力量: 5","智力: 10"]} 1
/give @s minecraft:iron_helmet{YSAAttributes:["护甲: 8"]} 1
```

将木棍和第一步铁剑分别放在双手、穿戴头盔。约 1 秒后力量总增量 15、护甲增量 8，来源分别展示 NBT/Lore 与对应槽位。头盔只放背包时无效；物品数量不倍增加成。

```text
/ysa attr @s "智力"
/ysa resource @s "法力"
```

默认模板 10 级角色智力由 3 变为 13，法力上限由 46.8 变为 202.8。上限增加不会立即补满余额，正常恢复继续。将法力补满后卸下木棍：

```text
/ysa resource @s "法力" set 999
```

移走木棍并等待约 1 秒，法力上限和余额均应回到不超过 46.8。

## 3. 错误隔离与恢复

```text
/give @s minecraft:stick{YSAAttributes:["力量: 10","护甲: 9"],display:{Lore:['{"text":"力量: 2"}']}} 1
```

持有这根木棍后应收到一次“同一物品重复定义属性”的聊天提示，指出槽位和 `display.Lore[0]`。该物品的力量与护甲均不生效，其他槽位有效装备仍生效。保持装备不变十秒，不应刷屏；切回有效物品后自动恢复。

```text
/give @s minecraft:stick{YSAAttributes:["力量: NaN"]} 1
/give @s minecraft:stick{display:{Lore:['{"text":"力量: 10%"}']}} 1
/give @s minecraft:stick{YSAAttributes:["不存在: 1"]} 1
```

分别持有，应提示非法数值、格式或属性错误，不导致崩溃。支持 `力量: -2.5`、`力量: +1e2`；比例属性用小数，百分号写法尚不支持。

## 4. 刷新与回归

持有有效装备执行 `/ysa reload`，加成应恰好生效一次。修改角色等级、正常退出后重进、切换维度时不能丢失或重复叠加。多人场景由观察该玩家的客户端确认 `ysa.attr` 使用对应玩家快照。

装备阶段验收后，下一版已接入派生公式及公式输出的装备加成，详见 `DERIVED_DEBUG_TEST.zh-CN.md`。资源当前值及 combat_input 仍不允许直接写入装备；原版生命与扣血尚未接入。

## 自动验证

Java 17 下运行 `./gradlew :core:test :forge:test :forge:jar --offline --console=plain`：28 个 core 测试及 6 个 Forge NBT/Lore 读取测试通过，完成 jar 重混淆。覆盖有效值与来源、重复/非法/超大列表、整件原子叠加与溢出隔离、角色叠加和资源上限变化。Forge 测试直接使用真实 NBT 与 JSON 文本解析，不启动游戏；槽位捕获、tick 刷新、存档和网络同步仍以本清单实机验收为准。

## 测试包部署记录

2026-10-19 已替换 `E:\Games\YSM拍摄端\.minecraft\versions\1.20.1-Forge_47.4.16\mods\yessteveattribute-0.1.0.jar`，部署文件与构建产物 SHA-256 相同：`AE5BAEAA01526B0901E813B4BDDCD1A9D9E67F2FBE177B6CE51D443BFAE2ACEA`。

上一版备份：`E:\JavaProject\backups\YesSteveAttribute\20261019-195049-before-equipment\yessteveattribute-0.1.0.jar`。
