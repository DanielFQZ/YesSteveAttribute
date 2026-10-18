# YesSteveAttribute 开发方案

## 目标和边界

第一阶段目标是 Minecraft 1.20.1 Forge + Paper。Forge 端与 YSM、YSS、eyelib、YesSteveVFX、YesSteveCamera 联调；Paper 端与 MythicMobs 通过可选桥接模块联调。

YSA 只做属性、公式、资源条、伤害结算和读取接口。YSM/YSS/VFX/Camera 负责动画、命中窗口和表现，MythicMobs 负责技能触发、目标、循环、延迟和 Buff。YSA 不再实现自己的技能系统、Buff 系统、目标选择器或外部 Mod 专用适配器。

配置契约已经确认并作为仓库模板保存；Java 功能开发已从第一阶段开始。模板仍只有四个文件：

```text
templates/
├─ attributes.yml   属性定义和资源绑定
├─ formulas.yml     派生属性、资源和战斗公式
├─ roles.yml        角色/职业基础数值
└─ levels.yml       等级、经验和升级条件
```

## 当前实现状态

截至 2026-10-19，仓库已经具备以下可运行部分：

- `core`：Java 17 独立核心模块，使用 SnakeYAML 以 UTF-8 加载四个配置文件；保留分组信息、检查重复 YAML 键、检查跨文件引用和等级/角色引用。
- `core`：确定性的数值公式引擎，支持短变量占位符、公式依赖、四则运算、比较、条件表达式、`Math.pow/min/max/floor/ceil/abs`，并拒绝循环依赖、除零和非有限结果。
- `core`：属性来源快照、来源解释以及 `属性名: 数值` 的 NBT/Lore 文本解析器。
- `forge`：Minecraft 1.20.1 Forge 47.4.16 模块，首次启动自动生成配置；`/ysa reload` 通过校验后原子替换有效配置。
- `forge`：在装有 YSM 时注册 `ysa.attr('属性名')` 和 `ysa.damage(倍率, '类型')`。当前 `ysa.attr` 用于客户端预览时读取配置默认值，最终实体快照和服务端伤害桥接属于下一阶段。

当前构建产物为 `forge/build/libs/yessteveattribute-0.1.0.jar`，测试客户端使用 1.20.1 Forge 47.4.16。YSS 命中执行器与 YSA 伤害结算尚未接通，因此 `ysa.damage` 仍是接口桩，不会直接扣血。

配置文件的结构键使用英文，属性 ID、角色名、显示名称和条件文本可以使用中文。文件统一使用 UTF-8，不使用项目专属命名空间前缀。

## 现有服务端实现的参考

我通过 SSH 只读检查了 `D:\CQBAver20250904\plugins\AttributePlus` 和 `D:\CQBAver20250904\plugins\Atziluth`。两套插件都有可借鉴的边界，但不直接照搬其配置。

AttributePlus 的 `attribute.yml` 把内部变量、中文显示名、公式、优先级、战力权重、消息和读取规则分开；`config.yml` 还支持 Lore 正则、指定 NBT 列表和条件标签。这验证了“英文结构键 + 中文属性名 + 独立读取协议”的方向。它的脚本属性还区分了 update、attack/defense、other、runtime 生命周期，可作为未来事件接口的参考。

Atziluth 的属性脚本用 `priority`、`attributeName`、`placeholder`、`type` 和生命周期函数注册属性；`AttributeAPI` 支持按 source 增删临时属性，`FightData` 保存一次攻击内的伤害、属性和临时数据，MythicMobs hook 则提供占位符、技能机制和条件注册。这些能力会转化为 YSA 的来源追踪、DamageContext 和可选桥接 API。

现有脚本中也有不能直接继承的实现细节：Atziluth 的暴击脚本当前概率表达式与常见的“暴击/(暴击+抗性)”不一致，元素伤害脚本计算出结果后没有继续写回 FightData；AttributePlus 的个别示例脚本存在公式或 placeholder 复制错误。因此 YSA 只借鉴模块边界和 API 形状，公式以当前模板为准，并在加载和测试阶段验证每条公式。

## 配置契约

### attributes.yml

这里定义所有属性，但 `primary`、`combat`、`element_weight`、`element_resistance`、`derived`、`runtime` 只是策划和 UI 的分类，程序内部全部视为同等级变量。分类不会限制装备、Buff 或其他 Mod 修改属性。

每个属性条目都使用相同的字段：`type` 表示类型，`unit` 表示显示单位，`default` 表示没有来源时的默认值，`visible` 表示是否建议在面板中显示。一级属性和次级属性没有不同的配置语法。

当前测试属性使用力量、耐力、敏捷、精神、智力、专注六项一级属性；模板还包括武器伤害、护甲、暴击、暴击抗性、暴击倍率、六种元素权重、六种元素抗性、伤害中间值和资源值。

资源条使用同一套接口，模板保留四种资源：

- 法力：沿用原有的智力/专注成长公式，作为通用资源示例；
- 精力：上限 500，每秒恢复 40，初始满值；
- 充能：上限 100，初始 0，衰减速度暂为 0；
- 集中：上限 100，初始满值，每秒恢复 10。

生命上限和生命恢复仍作为计算属性，实际生命由 Minecraft 原生生命系统持有。属性定义中不写来源字段。

### formulas.yml

所有派生属性、资源参数、伤害、元素分配、减伤、暴击和最终伤害公式集中在这里。角色成长公式和等级经验公式仍分别放在 `roles.yml`、`levels.yml`，因为它们属于角色和等级规则本身。

公式变量使用短占位符，例如 `<力量>`、`<目标.护甲>`、`<lvl>`。不提供第二套 `formula_ref`、长命名空间变量或独立 JS 公式文件。

伤害结算顺序固定为：

```text
基础伤害
→ 无属性/元素分配
→ 元素减伤和护甲减伤
→ 暴击判定
→ 暴击倍率作用于暴击前最终伤害
→ 调用原版伤害入口
```

模板不设置“伤害上下限”。公式计算出多少就是多少；是否允许负伤害、是否在原版入口前处理治疗效果，作为实现时的明确语义检查项处理。

### roles.yml 与 levels.yml

`roles.yml` 只写角色/职业的六项一级属性、基础值和随 `<lvl>` 变化的公式，并引用一个等级规则。

`levels.yml` 只写等级范围、经验曲线、分段条件、升阶条件和溢出经验。`<lvl>` 是当前等级，`<prev>` 是上一次升级所需经验，第一段的 `<prev>` 为 0。

## 装备 NBT/Lore 读取

YSA 不枚举其他 Mod 的物品，也不创建装备配置文件。解析器只识别属性 ID 后面的数值：

```text
力量: 100
暴击倍率: 0.2
```

NBT 和 Lore 使用同一套 `属性名: 值` 规则。YSA 不修改物品 NBT，也不覆盖其他 Mod 的字段。

加载配置时检查重复属性定义、重复公式输出和无法解析的字段；读取物品时如果同一物品出现重复或冲突属性，记录带物品位置和字段的错误，并把该物品标为无效，要求设计师修正后 reload。具体是否兼容颜色代码、JSON/MiniMessage Lore、百分比书写和负数，在解析器实现前写成测试用例，不另建配置文件。

## 来源追踪和调试

来源不写进属性定义。属性快照内部保留 provenance 链：角色成长、装备 NBT、装备 Lore、Buff、接口写入和公式依赖都记录为只读节点。

调试模式提供以下能力：

- 查询某个实体某个属性的最终值和每个贡献值；
- 展开公式依赖和中间结果；
- 显示来源类型、物品槽位、NBT/Lore 字段和调用方；
- reload 或解析错误带文件、行号、属性名和物品定位。

这套追踪供开发者日志和 `AttributeSnapshot#explain` 使用，不影响普通 UI 配置。

## Forge、YSM、YSS 和 Molang

YSM 动画的指令帧已经提供动作时机，因此不把 YSM 动画名再传入 YSA。客户端只注册简短的 Molang 函数：

```molang
ysa.attr('力量')
ysa.damage(1.44, '物理')
```

`ysa.damage(1.44, '物理')` 表示用攻击者当前 `基础伤害` 的 144% 发起一次物理伤害请求。YSS 负责当前动画帧的命中目标、命中窗口、去重、击退和硬直；YSA 服务端重新读取攻击者和目标属性，计算暴击与减伤，并只调用一次原版伤害入口。客户端计算结果不能直接扣血。

`ysa.attr` 只用于动画分支、表现或预览。最终伤害不信任客户端传来的数值。

当前 YSS 的命中配置仍使用常量 `damage`，联调时应让命中执行器调用 YSA 的伤害请求，同时保留现有的几何判定和时序。

## Paper 和 MythicMobs

核心模块不依赖 MythicMobs。Paper 适配器提供原版伤害入口和只读 API；MythicMobs 桥接作为可选模块，使用官方扩展点注册技能机制、条件、占位符和 reload 监听。

建议接口方向：

```text
<caster.ysa.力量>
<target.ysa.护甲>
<trigger.ysa.暴击>
```

以及：

```yaml
- ysadamage{multiplier=1.44;element=物理;cause=ENTITY_ATTACK} @target
- ysaattribute{attribute=力量;operator=>=;value=100} ?
```

MythicMobs 负责触发和控制技能，YSA 只负责属性读取、公式计算、资源读写接口和一次标准伤害结算。`cause`、`element` 和技能标签分开传递，方便桥接原版伤害语义和第三方条件。

## 公共 API 和 UI

YSA 提供读取和受控写入接口，不规定 UI 实现：

```java
AttributeProvider#get(entity, "力量")
AttributeSnapshot#all(entity)
AttributeSnapshot#explain(entity, "基础伤害")
ResourceProvider#get(entity, "精力")
ResourceProvider#add(entity, "充能", 10)
FormulaService#preview(expression, input)
DamageContext#readOnly()
```

MineBrowse、DragonCore 和其他 UI 模组/插件自行决定显示方式。其他 Mod 可以直接依赖 API、注册属性写入来源、读取资源条或提交兼容 PR；YSA 不提前为每个 Mod 编写适配器。

## 开发顺序

1. **配置和模型**：加载 UTF-8 YAML，使用英文结构键和中文属性 ID，检查重复定义并建立统一属性模型。
2. **公式引擎**：实现短变量、分段公式、条件、数学函数、依赖图和错误定位；不执行未知变量、循环依赖、NaN 或 Infinity。
3. **角色、等级和资源**：实现角色成长、经验、四种资源条、资源恢复/衰减和接口读写。
4. **NBT/Lore 解析**：实现 `属性名: 值`、重复冲突错误、缓存和 provenance 追踪。
5. **战斗结算**：按模板顺序计算基础伤害、元素、减伤和暴击，并调用原版伤害入口。
6. **Forge/YSM/YSS**：注册 `ysa.attr` 和 `ysa.damage`，把 YSS 命中帧接到服务端伤害上下文。
7. **Paper/MythicMobs**：实现可选桥接、占位符、技能机制、条件和资源接口。
8. **只读 API、调试和测试**：提供公式预览、属性来源解释和 Forge/Paper 结果一致性测试。

## 暂不纳入第一阶段

- 跳跃高度和攻击间隔；
- RATE/RATING 装备评分；
- 自有技能、Buff、目标选择和 UI 系统；
- 其他 Mod 的预先适配器；
- 通过客户端传入最终伤害数值。

装备评级和 RATING 留给后续服务器开发；资源恢复和衰减数值只需修改 formulas.yml，不需要增加新的配置文件。
