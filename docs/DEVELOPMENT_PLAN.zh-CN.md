# YesSteveAttribute 开发方案

当前状态和验收入口统一见 `docs/STATUS.zh-CN.md`；`docs/archive/` 只保存历史验收，不是当前待办。

2026-10-22 边界调整：YSS 已恢复到 Camera/VFX 已发 PR #3（a8e1852），YSA 伤害桥、起手费用与动作授权等本地扩展已撤回。后续先完成自己的 YSA API、独立测试和发布整理，再由用户联系原作者协商最小接入；不继续扩大 YSS 修改。本地客户端仍为撤回前 jar，部署与源码有差异。

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

截至 2026-10-21，仓库已经具备以下可运行部分（验收状态见 STATUS）：

- `core`：Java 17 独立核心模块，使用 SnakeYAML 以 UTF-8 加载四个配置文件；保留分组信息、检查重复 YAML 键、检查跨文件引用和等级/角色引用。
- `core`：确定性的数值公式引擎，支持短变量占位符、公式依赖、四则运算、比较、条件表达式、`Math.pow/min/max/floor/ceil/abs`，并拒绝循环依赖、除零和非有限结果。
- `core`：角色成长和等级经验计算器，按角色的 `<lvl>` 成长公式生成属性快照，按等级经验分段计算升级需求，并保留角色来源信息。
- `core`：属性来源快照、来源解释以及 `属性名: 数值` 的 NBT/Lore 文本解析器。
- `core/forge`：装备属性接入主手、副手和四个原版护甲槽；读取根 NBT `YSAAttributes` 字符串列表和 `display.Lore`，支持 JSON 文本与旧式颜色代码。单件物品跨 NBT/Lore 重复字段、未知字段、非法数值或溢出时整件忽略，不留下部分加成。每 20 个服务器 tick 检查装备，仅变化时重新解析、计算和同步；登录、角色/等级修改、reload 也重算。`/ysa attr` 显示每个贡献值、物品 ID、槽位、NBT/Lore 字段及行索引。
- `forge`：Minecraft 1.20.1 Forge 47.4.16 模块，首次启动自动生成配置；`/ysa reload` 通过校验后原子替换有效配置。
- `forge`：玩家角色、等级和资源当前值写入玩家存档，死亡/重生、登录后恢复；服务端生成只读属性快照，通过单向网络包同步本人及正在观察该玩家的客户端。快照包含配置默认值、角色成长覆盖值、装备、资源及不依赖目标/战斗事件的派生结果，暂不修改原版生命。
- `core/forge`：派生公式按依赖计算，公式结果再叠加该字段的装备贡献，下游公式读取叠加后的值；角色/default 不重复加入公式输出。支持元素亲和文本结果，最高正权重获胜、相同权重按公式参数顺序、无正权重返回“无”。资源余额变化会刷新依赖 current 的普通派生公式；资源参数仍禁止直接或间接依赖 current、目标或战斗事件，包括未执行的分支。
- `core/forge`：战斗试算计算基础伤害倍率、元素分配、减伤、暴击和最终伤害，管理员命令 `/ysa combat <攻击玩家> <有生命目标> [倍率] [随机值]` 展示中间结果，默认倍率 1、随机值 0.5；试算本身不扣血。概率须在 0～1，随机值须在 [0, 1)，公式负伤害原样展示，不隐式治疗。目标/事件依赖传递到下游字段，常驻快照将这些字段标为“待战斗”；试算不把本次战斗结果写回常驻属性，但会立即刷新装备快照。
- `core/forge`：新增 `CombatService.attack` 服务端实际伤害 API 及 `/ysa damage <攻击玩家> <有生命目标> [倍率]` 管理员测试命令，随机数由服务端生成。非正值不伤害/治疗；正值校验 float 范围后只提交一次原版 hurt。自定义伤害类型跳过原版护甲/韧性，保留其他原版防护及 Forge 事件；聊天展示公式值、提交值、接受状态、生命/吸收净变化。玩家装备即时刷新；非玩家目标使用 default + 六槽装备、等级 0。接口边界详见 `docs/COMBAT_API.zh-CN.md`。
- `core/forge`：四种资源的上限和恢复/衰减参数通过绑定属性的公式计算，支持公式依赖；角色、等级变更及 reload 时重新计算参数。current 不允许被资源参数公式引用，也不能重复绑定或同时定义公式。资源首次创建按 initial 初始化，后续保留绝对余额并限制在 0～上限；上限增加不自动补满，降低时截断。
- `forge`：资源每 20 个服务器 tick 结算一次，存活且在线时恢复/衰减，暂停和离线不补算。变化时持久化并同步本人和观察者；死亡重生保留资源余额。新增 `/ysa resource <玩家> "资源"` 查询，后接 `set <数值>` 或 `add <增减值>` 修改，命令需管理员权限。服务端 API 为 `PlayerAttributes.resource/setResource/addResource`，调用方须在服务器线程执行。可通过调试面板或 Molang 读取 `ysa.attr('精力值')` 等字段。
- `core/forge`：新增 `ResourceConsumption` 与 `ResourceService.preview/tryConsume`，支持多资源原子消费、余额不足整笔拒绝、扣费后派生公式校验与快照/存档一次提交。先试算最新装备，公式失败不扣费。管理员可用 `/ysa consume`（多资源/preview）或 `/ysa resource ... consume` 验收；调用限制和失败语义见 `docs/RESOURCE_API.zh-CN.md`。YSS 起手费用接入曾获验收，现按用户要求撤回；历史说明位于 `docs/archive/accepted-2026-10-22/SKILL_COST_TEST.zh-CN.md`，当前 YSS 不调用此 API。
- `forge/client`：`/ysadebug` 切换右侧实时调试面板，支持 `on/off`、`next/prev`、`page <页码>` 和 `scale <0.5～1.5>`。按服务端配置顺序显示所有属性（包括 visible=false、文本及待战斗字段），默认缩放 0.75、按窗口分栏，空间不足分页。数值变化短暂高亮、F1 隐藏、退出服务器自动关闭；面板只读取本人服务端快照。显示保留最多四位小数，大/极小数用科学计数法，内部精度不变。同步协议升级为 2，客户端与服务器须使用相同新版本。
- `forge`：在装有 YSM 时注册 `ysa.attr('属性名')` 和 `ysa.damage(倍率, '类型')`。`ysa.attr` 读取动画对应实体的服务端同步快照；实体尚未同步/未知属性返回 0，无实体的编辑器预览仍读取本地默认值。`ysa.damage` 仍是接口桩。
- `forge`：新增管理员命令 `/ysa role <玩家> "角色" [等级]`、`/ysa level <玩家> <等级>`、`/ysa profile <玩家>`、`/ysa attr <玩家> "属性"`。省略角色等级时使用等级规则起始值。角色成长覆盖 base/default，不额外叠加。YSA 等级独立于原版经验等级。
- `forge`：reload 在发布前试算全部在线玩家；缺失角色、等级越界、成长/资源公式运行错误或负数资源上限/速率会拒绝整次更新，保留原配置、快照和余额。成功后刷新客户端。离线玩家在下次登录时校验，失败会提示管理员修复配置或重新指定角色，保留原存档选择。
- `forge`：`/ysa reload` 会在聊天框直接显示加载统计、耗时和失败诊断；失败时继续保留上一次有效配置。

YSA 构建产物路径为 `forge/build/libs/yessteveattribute-0.1.0.jar`，测试客户端使用 1.20.1 Forge 47.4.16。YSS 的 YSA 伤害模式接入已撤回；Molang `ysa.damage` 和普通鼠标攻击仍未接通。当前工作区包含未收敛草稿，不等于部署版本。

资源、装备、派生属性、战斗试算及实时调试面板阶段均获用户功能测试通过反馈。历史验收说明已移入 `docs/archive/accepted-2026-10-19/`，当前开发及验收入口见 `docs/STATUS.zh-CN.md`。

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

当前读取根 NBT 的 `YSAAttributes: ["力量: 10"]` 和原版 `display.Lore` 字符串列表；两处同名字段视为重复，即使数值相同也整件忽略。每个列表最多 256 行，物品堆叠数量不倍增加成，背包中的物品不生效。支持 JSON 文本组件、旧式 `§` 颜色代码、正负数、小数及科学计数法；不支持 MiniMessage 或 `10%` 写法，比例使用小数。普通 Lore 描述跳过，已知属性的非法数值、严格 NBT 列表中的非法行会报错。聊天框直接显示错误；装备未变时不重复解析或刷日志，修正物品后自动恢复，也可 reload 强制刷新。

装备加成叠加在角色成长值（未指定角色时为 default）之后；公式输出的装备加成叠加在公式计算结果之后，传递给下游公式。资源 current 与 combat_input 由运行时接口控制，禁止装备写入。可直接装备增加法力上限，也可用智力/专注影响法力公式；增加资源上限保留余额，降低上限截断余额。装备或 reload 计算失败保留上一份快照与余额并提示；余额变化导致普通派生公式失败时，整次资源/派生刷新保留旧状态并提示一次，修正后恢复。战斗入口会重新验证当前装备，计算失败则中止攻击，不能用旧武器数值结算。原版生命上限和普通鼠标攻击不自动映射 YSA 数值；显式调用伤害 API 或 `/ysa damage` 才按 YSA 公式实际扣血。

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

`ysa.damage(1.44, '物理')` 仍为规划中的入口，类型语义待定，现有草稿反射目标已随 YSS 回退移除。YSA 保留服务端伤害 API，由服务端读取双方属性、计算暴击/减伤并只调用一次原版伤害入口。未来联动的目标、动作真实性、时机、去重及资源支付调用点需与原作者协商。客户端计算结果不能直接扣血，不在第三方 YSS 内自行扩建动作授权系统。

`ysa.attr` 只用于动画分支、表现或预览。最终伤害不信任客户端传来的数值。

当前 YSS PR3 使用固定 damage，不识别 YSA damage_mode，也不自动消费资源；添加旧扩展字段不能开启联动。

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
