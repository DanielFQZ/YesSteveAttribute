# Yes Steve Attribute 配置模板

这是用于首轮开发、离线验算和回归测试的完整示例。它不是最终运行目录，开发者可以复制到 `config/yes_steve/` 后按项目修改。

## 编码和命名

- 所有文件使用 UTF-8，推荐编辑器保存为 UTF-8 无 BOM。
- 注释和显示文本可以使用中文。
- `id` 使用小写 ASCII 命名空间，例如 `empyrean:strength`。
- `name`、`description`、`aliases` 用于中文显示和迁移旧配置。
- 公式优先使用 `ctx` 和标准 ID；中文别名只作为作者层便利语法。

## 文件说明

```text
manifest.yml                 配置格式、版本和 reload 策略
progression/level.yml        等级、经验、升阶和等级成长
attributes/definitions.yml   属性、单位、聚合阶段和派生属性
sources/domains.yml          可注册的属性投放层
actors/player.yml            玩家职业和基础来源
actors/monster.yml           怪物模板和抗性
items/equipment.yml          普通装备、强化和随机词缀
items/artifact.yml           圣遗物式五槽位系统
items/echo.yml               声骸式费用、技能和共鸣系统
buffs/examples.yml           临时 Buff 和触发效果
combat/elements.yml          元素、抗性和反应
combat/damage_profiles.yml   伤害组成和原版伤害入口参数
combat/procs.yml             暴击和其他概率事件
skills/light_slash.yml       技能与 YSS 命中段的关联
yss/light_slash.hit.jsonc    YSS 命中段示例
ui/api.yml                   独立 UI API 契约
tests/scenarios.yml          配置加载、公式和战斗测试
formulas/advanced.js         可选的受限 JS 公式示例
```

## 当前模板的测试角色

测试角色是 20 级无畏佣兵，装备一把带随机词缀的武器、两件圣遗物和两个声骸，并获得一个短时力量 Buff。测试会覆盖等级成长、来源叠加、套装效果、声骸费用、火焰暴击、抗性、原版伤害入口和 UI 快照。

模板中的数字是为了稳定回归测试而选定的示例值，不代表最终游戏平衡。
