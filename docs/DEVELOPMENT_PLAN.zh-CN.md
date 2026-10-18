# YesSteveAttribute 新开发方案

## 当前范围

开发目标暂定为 Minecraft 1.20.1：Forge 客户端/集成服务器和 Paper 服务端。Forge 端按 Java 17、Forge 47.x 的现有 YSM/YSS 工程测试；Paper 端保持核心与 MythicMobs 解耦。

当前阶段只确定配置契约和接口边界，不开始实现最终战斗代码。旧的模板和旧开发计划已经删除，新的实际配置模板只有四个文件：

```text
templates/
├─ attributes.yml   属性定义
├─ formulas.yml     所有公式
├─ roles.yml        角色/职业基础数值
└─ levels.yml       等级、经验和升级条件
```

四个文件全部使用 UTF-8。属性名使用中文，公式使用短占位符，例如 `<lvl>`、`<力量>`、`<目标.护甲>`。不使用项目专属命名空间前缀，也不要求数值策划维护长 ID。

## 配置职责

### attributes.yml

只声明属性名称、类型、单位、默认值、是否显示和允许的来源。一级属性、额外属性、元素权重、元素抗性、资源和战斗临时值都在这里登记。公式不写在这里。

装备不建立独立配置文件。实现读取物品 NBT 或 Lore 中与属性名称匹配的字段，并把结果作为属性来源加入快照。NBT/Lore 的精确字段格式、优先级和冲突处理仍需作者确认。

### formulas.yml

所有属性派生、资源条、伤害、元素分配、减伤、暴击、攻击速度和最终结算公式都集中在一个文件中。复杂公式不拆成 JS 文件，也不提供第二种 `formula_ref` 写法。

公式执行器内部可以使用受限表达式引擎，但作者只接触模板中的短变量语法。加载时检查未知属性、循环依赖、单位不匹配、除零、NaN、Infinity 和超时。

### roles.yml

每个角色/职业配置六项一级属性的基础值、按 `<lvl>` 变化的公式、引用的等级规则和职业额外升级条件。生命上限、法力上限、伤害、暴击等次级属性全部由 `formulas.yml` 计算，不在职业文件重复配置。

### levels.yml

只负责等级轨道、起始/最大等级、经验公式、分段条件、升阶条件和溢出经验。`<lvl>` 是当前等级，`<prev>` 是上一次升级所需经验；第一等级的 `<prev>` 定义为 0。

## 装备读取边界

YSA 不识别具体 Mod 的物品 ID，也不为其他 Mod 写装备定义。它只提供一个通用读取器：

1. 从物品 NBT 中读取属性字段；
2. 从 Lore 文本中读取属性名称和数值；
3. 将匹配到的值交给属性快照合并；
4. 不修改物品 NBT，不覆盖其他 Mod 字段。

模板注释中的协议草案是：

```text
NBT:  attributes: {力量: 10, 暴击倍率: 0.2}
Lore:  力量: +10
```

这只是待确认的读取协议。实现前需要决定是否支持扁平 NBT、颜色代码、MiniMessage/JSON Lore、百分比写法、重复字段、负数、NBT 与 Lore 冲突，以及装备是否可以修改一级属性。

## Forge、YSM、YSS 和 Molang

客户端不实现独立的复杂技能系统。YSM 继续负责模型和动画，YesSteveVFX/Camera 继续负责表现，YSS 继续负责动画命中窗口、骨骼采样、命中去重和动作时序。

YSA Forge 客户端只注册一个可选的 YSM Molang 扩展。Molang 标识符必须使用 ASCII，中文属性名作为字符串参数传入：

```molang
// 读取属性，只用于动画分支或预览
ysa.attr('力量')

// attack1 动作提交一个攻击意图；服务端会重新读取属性和公式
ysa.attack('attack1', 1.44, '物理')

// 使用基础伤害的 144% 作为这次动作的倍率
ysa.attack('attack1', 1.44, '物理', ysa.attr('基础伤害'))
```

`ysa.attack` 只发送动作名、倍率、伤害标签等请求参数，客户端不计算最终伤害，也不能直接扣除生命。服务端必须根据 YSS 的动画名、动作名、segment index、目标和当前属性快照重新计算，最后走一次原版伤害入口。

当前 YSS 的 `hit.json` 仍把 `damage` 解析为常量并由服务端执行。因此第一项联调工作不是新建一份 YSA 的命中配置，而是给 YSS 的命中请求增加动作/攻击配置字段，或让 YSS 服务端执行器调用 YSA 的伤害接口，同时保留现有命中窗口、骨骼、去重、击退和硬直逻辑。

## Paper 与 MythicMobs

核心模块不引用 MythicMobs 类。Paper 适配器使用可选依赖；MythicMobs 桥接单独实现，并使用官方扩展点：

- `MythicMechanicLoadEvent`：注册 `ysadamage` 等技能机制；
- `MythicConditionLoadEvent`：注册可选的 `ysaattribute` 条件；
- `MythicReloadedEvent`：清理桥接缓存；
- MythicMobs 占位符：提供 `<caster.ysa.力量>`、`<target.ysa.护甲>`、`<trigger.ysa.暴击>`；
- 只读 Java API：提供属性快照、公式预览和 DamageContext。

建议的最小技能机制：

```yaml
# MythicMobs 技能组中的示例写法
- ysadamage{a=1.44;attribute=基础伤害;element=物理;cause=ENTITY_ATTACK;tags=attack1} @target
- ysaattribute{attribute=力量;operator=>=;value=100} ?
```

MythicMobs 仍负责技能触发、条件、目标选择、循环、延迟和 Buff。YSA 只负责读取属性、计算伤害和发布一次标准受伤事件。`cause`、`element`、`tags` 必须分开保留：前者兼容原版盾牌/附魔/伤害来源，中间值参与元素抗性，后者供 MythicMobs 的 DamageTag 条件使用。

暂不实现 YSA 自己的 Buff 系统、技能系统、目标选择器、外部 Mod 专用适配器或 MythicMobs 全量属性系统。

## 外部 API 与 UI

YSA 只提供读取型公共 API，不规定 UI：

```java
AttributeProvider#get(entity, "力量")
AttributeSnapshot#all(entity)
AttributeSnapshot#explain(entity, "基础伤害")
FormulaService#preview(expression, input)
DamageContext#readOnly()
```

MineBrowse、DragonCore、其他 UI 模组和插件都通过这些接口读取数据，自行决定页面和交互。写入装备、修改最终属性和直接提交最终伤害不属于 UI API。

其他 Mod 可以直接依赖 API、注册属性来源、监听只读战斗事件或向项目提交兼容 PR。YSA 不提前为每个 Mod 编写适配器。

## 开发顺序

### 1. 配置加载和共享模型

实现 UTF-8 YAML 加载、中文属性名称、短占位符解析、错误定位、属性定义和公式依赖图。先实现离线校验，不接入 Minecraft。

### 2. 公式计算器

实现 `Math.pow`、四则运算、条件、分段、`min/max/floor/ceil/abs` 和 `最高权重元素()`。加入除零、NaN、Infinity、循环依赖和单位检查。公式结果保留来源和计算 trace。

### 3. 等级与角色

实现 `levels.yml` 和 `roles.yml` 的经验、等级、条件、一级属性成长，并生成资源和战斗公式所需的属性快照。

### 4. NBT/Lore 属性读取

实现不修改物品的 NBT/Lore 读取器、重复字段合并和快照缓存。此步骤依赖作者确认读取协议。

### 5. Forge 1.20.1 与 YSM Molang

实现可选 YSM 扩展和 `ysa.attr/ysa.attack`。客户端只发请求或动作参数，服务端验证动作和命中上下文。与 YSS 联调时扩展命中请求，不能绕过现有的 hit.json 几何判定。

### 6. Paper 1.20.1 与 MythicMobs 桥接

实现 Paper 原版伤害入口、属性占位符、`ysadamage`、可选 `ysaattribute` 和 reload 监听。核心在没有 MythicMobs 时也能加载，桥接模块在插件不存在时不初始化。

### 7. 只读 API 与兼容测试

提供属性快照、公式预览、DamageContext 和战斗事件读取接口。测试 Forge 集成服务器、Paper、MythicMobs、YSM/YSS 动作请求以及配置 reload 的结果一致性。

## 当前需要作者确认的问题

这些问题会影响实现契约，我不在模板中擅自决定：

1. 一级属性是否以《数值设计.md》的“力量、耐力、敏捷、精神、智力、专注”为准？你新消息提到的“生命、精通”是否是新增或改名？
2. 怒气上限和怒气恢复速度的计算公式是什么？
3. 装备标签的固定格式是否采用 `attributes: {力量: 10}`，Lore 是否采用 `力量: +10`？NBT 与 Lore 同时存在时谁优先？
4. 一件装备是否可以通过标签同时修改一级属性、额外属性、元素权重和元素抗性？
5. 重复属性字段如何合并？百分比、倍率、负数和颜色代码是否支持？
6. 多元素伤害是否按六种元素逐项计算并分别减伤后相加？无属性权重为 0 或总权重为 0 时是否让无属性伤害占 100%？
7. 元素减伤、护甲减伤和最终伤害是否需要上限/下限，最终伤害是否最低为 0？
8. 暴击倍率作用于基础伤害、元素分配前伤害，还是最终伤害？
9. 跳跃高度公式如何转换为 Minecraft 1.20.1 的实际跳跃值？攻击间隔公式作用于 YSM 动作时间轴、原版攻速，还是两者都作用？
10. RATE（单件装备评分）和 RATING（玩家评分）的具体公式是什么？
11. `ysa.attack` 的动作名是否由 YSM 动画名直接提供，还是由 YSS 的 `animation + segment index` 组成？
