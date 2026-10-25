# YSA 属性与公式配置指南 / YSA Attribute and Formula Configuration Guide

本文面向希望为 YSA 添加属性、派生值、资源、角色或战斗公式的开发者。中文部分优先；英文部分与中文结构对应，方便协作者查阅。

This guide is for developers who want to add attributes, derived values, resources, roles, or combat formulas to YSA. Chinese comes first; the English section follows the same structure.

## 1. 配置文件和加载位置 / Configuration files and locations

YSA 总是以一组配置快照加载四个文件：

| 文件 | 用途 |
| --- | --- |
| `attributes.yml` | 属性定义和资源绑定。 |
| `formulas.yml` | 派生属性、资源参数、伤害、减伤、暴击和运行时公式。 |
| `roles.yml` | 角色的基础属性和等级成长。 |
| `levels.yml` | 等级范围、经验曲线和升阶条件。 |

Forge 路径：`config/yessteveattribute/`。

Paper 路径：`plugins/YesSteveAttribute/`。

项目中的 [`templates/`](../templates/) 是可复制的完整模板。首次启动 Forge 或 Paper 时也会自动生成默认文件。

YSA loads the four files as one immutable configuration snapshot:

- `attributes.yml`: attribute definitions and resource bindings.
- `formulas.yml`: derived, resource, damage, mitigation, critical, and runtime formulas.
- `roles.yml`: role base values and level growth.
- `levels.yml`: level ranges, experience curves, and promotion conditions.

Forge uses `config/yessteveattribute/`; Paper uses `plugins/YesSteveAttribute/`. The complete starter templates are in [`templates/`](../templates/).

## 2. 添加一个属性 / Add an attribute

在 `attributes.yml` 的 `attributes` 下增加条目。分组名称只是组织配置和 UI 的标签，不限制属性用途；属性 ID 在所有公式和接口中必须保持一致。

Add an entry below `attributes` in `attributes.yml`. Group names are organizational labels only. The attribute ID must remain identical in formulas and API calls.

```yaml
attributes:
  primary:
    力量:
      type: number
      unit: 点
      default: 0
      visible: true
    幸运:
      type: number
      unit: 点
      default: 0
      visible: true
  combat:
    暴击倍率:
      type: multiplier
      unit: 倍率
      default: 1.5
      visible: true
```

字段说明：

- `type`：给校验、调试界面和调用方使用的类型标签，例如 `number`、`multiplier`、`resource_max`、`resource_current`、`resource_rate`、`combat_value`、`combat_input`、`enum`。它不会自动改变公式行为。
- `unit`：显示单位，例如 `点`、`倍率`、`点/秒`。可以为空字符串。
- `default`：没有其他来源时的默认值。数值属性应使用数字；文本/枚举属性可以使用字符串。
- `visible`：是否建议普通 UI 显示。`false` 只影响展示建议，不会禁止公式、调试命令或 API 读取。

Field meanings:

- `type` is a validation and display label. It does not by itself change calculation semantics.
- `unit` is a display unit such as points, multiplier, or points per second.
- `default` is used when no other source supplies a value.
- `visible` controls display recommendations only; `false` does not make an attribute inaccessible.

## 3. 添加派生属性公式 / Add a derived formula

先在 `attributes.yml` 声明属性，再在 `formulas.yml` 中添加公式。公式使用字符串，属性引用使用尖括号：`<力量>`。

Declare the attribute in `attributes.yml` first, then define its expression in `formulas.yml`. Expressions are strings; attribute references use angle brackets such as `<力量>`.

```yaml
# attributes.yml
attributes:
  derived:
    幸运暴击加成:
      type: multiplier
      unit: 倍率
      default: 1
      visible: true

# formulas.yml
resources:
  幸运暴击加成: "1 + <幸运> * 0.01"
```

公式会按依赖关系求值。公式可以引用另一个公式对应的属性；循环依赖会在加载时拒绝。

Expressions are evaluated through their dependency graph. A formula may reference another formula-backed attribute. Cyclic dependencies are rejected during loading.

### 支持的语法 / Supported syntax

| 类别 | 示例 |
| --- | --- |
| 数字与四则运算 | `10`、`<力量> * 2 + 5`、`a / b` |
| 比较 | `<等级> >= 10`、`<暴击> == 0` |
| 逻辑 | `<力量> > 0 && <幸运> > 0`、`!<本次是否暴击>` |
| 条件表达式 | `<等级> >= 10 ? 2 : 1` |
| 数学函数 | `Math.pow(x, y)`、`Math.min(a, b)`、`Math.max(a, b)`、`Math.floor(x)`、`Math.ceil(x)`、`Math.abs(x)` |
| 上下文变量 | `<lvl>`、`<prev>`、`<目标.护甲>`、`<本次是否暴击>` |

Supported operators include arithmetic, comparisons, `&&`, `||`, `!`, and the ternary operator. Supported functions are `Math.pow`/`pow`, `Math.min`/`min`, `Math.max`/`max`, `Math.floor`/`floor`, `Math.ceil`/`ceil`, and `Math.abs`/`abs`.

公式不是 JavaScript，也不能访问文件、网络或任意 Java 类。除非处在未选中的条件分支中，除数为零、未知变量和非有限结果都会使本次加载失败。

The formula language is not JavaScript. It cannot access files, the network, or arbitrary Java classes. Division by zero, unknown variables, and non-finite results fail loading unless they occur in an unselected conditional branch.

## 4. 伤害、减伤和暴击 / Damage, mitigation, and critical formulas

`formulas.yml` 使用固定语义区分战斗阶段。常用字段如下：

```yaml
damage:
  基础伤害: "(<力量> * 6.6 + <武器伤害>) * <基础伤害加成>"
  无属性伤害: "<基础伤害> * <无属性伤害权重> / <总伤害权重>"

mitigation:
  护甲减伤: "<目标.护甲> * 0.25 * <目标.护甲减伤加成>"

critical:
  暴击概率: "<暴击> / (<暴击> + <目标.暴击抗性>)"
  暴击前伤害: "<无属性伤害> - <护甲减伤>"
  暴击伤害: "<暴击前伤害> * <暴击倍率>"
  最终伤害: "<本次是否暴击> == 1 ? <暴击伤害> : <暴击前伤害>"
```

`<目标.属性>` 表示目标快照中的属性；`<本次是否暴击>` 是战斗上下文输入，不是一个应由装备直接写入的常驻属性。公式只负责计算，实际扣血由明确的伤害入口决定。

`<目标.属性>` reads from the target snapshot. `<本次是否暴击>` is a combat-context input, not a persistent equipment value. Formulas calculate results; actual damage application is performed by an explicit combat entry point.

当前内置伤害类型包括 `COMPOSITE`、`PHYSICAL`、`FIRE`、`ICE`、`WIND`、`LIGHTNING`、`HOLY` 和 `SHADOW`。如果修改了伤害链，请同时检查 `CombatCalculator` 的阶段字段和对应测试。

Built-in damage types currently include `COMPOSITE`, `PHYSICAL`, `FIRE`, `ICE`, `WIND`, `LIGHTNING`, `HOLY`, and `SHADOW`. When changing the damage chain, update the corresponding calculation tests as well.

## 5. 资源 / Resources

资源把当前值和上限、恢复、衰减属性绑定起来：

```yaml
resources:
  精力:
    current: 精力值
    maximum: 精力上限
    recovery: 精力恢复速度
    initial: maximum
  充能:
    current: 充能值
    maximum: 充能上限
    decay: 充能衰减速度
    initial: 0
```

- `current` 和 `maximum` 是属性 ID，必须在 `attributes.yml` 中存在。
- `recovery`、`decay` 是可选属性；未填写时按零处理。
- `initial: maximum` 表示初始值为当前上限；也可以填写数字。
- 资源值会被限制在 `0..maximum`，减少上限时余额会自动截断到新上限。
- 资源消耗支持预览和原子提交；其中任一资源余额不足时，整笔消耗拒绝。

`current` and `maximum` must name attributes declared in `attributes.yml`. `recovery` and `decay` are optional and default to zero. `initial: maximum` starts a resource full. Resource values are clamped to `0..maximum`, and multi-resource consumption is atomic.

## 6. 角色和等级成长 / Roles and level growth

角色只写一级属性的基础值和成长公式，派生属性仍统一由 `formulas.yml` 计算：

```yaml
roles:
  示例战士:
    display_name: 示例战士
    level_rule: 默认
    upgrade_conditions: []
    base:
      力量: 10
      耐力: 12
    growth:
      力量: "10 + 0.6 * Math.pow(<lvl>, 1.6)"
      耐力: "12 + 0.8 * Math.pow(<lvl>, 1.5)"
```

`level_rule` 必须引用 `levels.yml` 中的规则。等级规则包含 `start`、`max`、`initial_exp`、经验分段和可选的 `promotion` 条件。`<lvl>` 是当前等级，`<prev>` 是上一等级的经验要求。

`level_rule` must reference a rule in `levels.yml`. A level rule contains `start`, `max`, `initial_exp`, experience segments, and optional promotion conditions. `<lvl>` is the current level and `<prev>` is the previous level's experience requirement.

## 7. 装备加成和来源 / Equipment modifiers and provenance

装备属性可以通过 YSA 支持的 NBT/Lore 格式提供。有效加成会叠加到角色和公式结果上；运行时资源当前值和战斗输入字段不能由装备直接写入。未知属性、非法数字、溢出值或同一物品解析失败时，整件物品会被忽略，不留下部分加成。

Equipment modifiers may be supplied through the supported YSA NBT/Lore formats. Valid modifiers are added to role and formula results. Runtime resource balances and combat inputs cannot be written directly by equipment. An item with unknown attributes, invalid numbers, overflow, or a parse failure is ignored as a whole.

使用 `/ysa attr` 或 Forge 调试面板查看数值时，来源信息可用于定位是角色、公式、装备还是其他运行时来源造成了变化。

Use `/ysa attr` or the Forge debug HUD to inspect provenance when diagnosing a value change.

## 8. 校验、reload 和调试流程 / Validation, reload, and debugging workflow

推荐流程：

1. 复制当前四份 YAML 作为备份。
2. 先在 `attributes.yml` 添加属性，再添加引用它的公式或角色字段。
3. 保持每个公式是字符串，并检查属性 ID 的大小写、空格和中文字符完全一致。
4. 执行 `/ysa reload`。
5. 查看聊天中的属性、公式、角色、等级数量和耗时；失败时阅读诊断中的文件路径和字段位置。
6. 用 `/ysa attr`、`/ysa resource`、`/ysa profile` 或 `/ysadebug on` 检查实际快照。

Recommended workflow:

1. Back up the four YAML files.
2. Add an attribute before adding formulas or role fields that reference it.
3. Keep every expression quoted and verify identifier spelling exactly.
4. Run `/ysa reload`.
5. Read the counts, duration, and file/field diagnostics in chat.
6. Verify the snapshot with `/ysa attr`, `/ysa resource`, `/ysa profile`, or `/ysadebug on`.

reload 是原子操作：四个文件和跨文件校验全部通过后才发布新快照。失败时会保留上一份有效配置，并尝试保留在线玩家的旧计算状态。

Reload is atomic: the new snapshot is published only after all four files and cross-file validation succeed. On failure, the last valid configuration remains active.

## 9. 最小可用示例 / Minimal working example

```yaml
# attributes.yml
attributes:
  primary:
    力量:
      type: number
      unit: 点
      default: 10
      visible: true
  derived:
    基础伤害:
      type: number
      unit: 点
      default: 0
      visible: true
resources: {}
```

```yaml
# formulas.yml
damage:
  基础伤害: "<力量> * 2"
```

如果公式引用了角色、资源或战斗上下文中的其他字段，应在相应文件中先定义这些字段，并确保所有引用的属性都已声明。

If the expression references role, resource, or combat-context fields, define them in the appropriate file first and make sure every referenced attribute is declared.

## 10. 相关代码 / Related code

- `core/src/main/java/com/yessteveattribute/core/config/YamlConfigLoader.java`：YAML 解析和字段类型检查。
- `core/src/main/java/com/yessteveattribute/core/formula/FormulaEngine.java`：公式语法和求值。
- `core/src/main/java/com/yessteveattribute/core/runtime/DerivedAttributeCalculator.java`：派生属性计算。
- `core/src/main/java/com/yessteveattribute/core/runtime/CombatCalculator.java`：战斗阶段计算。
- `forge/src/main/java/com/yessteveattribute/forge/ConfigService.java`：Forge 配置目录、reload 和回滚。
- `paper/src/main/java/com/yessteveattribute/paper/YesSteveAttributePaper.java`：Paper 配置和服务端调试页面。

When changing the configuration contract, update the templates, the relevant unit tests, and this guide together.
