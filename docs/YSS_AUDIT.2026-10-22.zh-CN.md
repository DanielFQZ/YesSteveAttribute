# YSS 回退与代码审计（2026-10-22）

## 结论与处置

`E:\JavaProject\YesSteveSkill-Daniel` 已撤回本地未提交改动，只保留已提交 PR 的 Camera/VFX 联动版本。本轮没有修复或扩展 YSS，没有修改第三方 YSM，没有推送或部署。报告存放在自己的 YSA 仓库，避免给 YSS PR 增加额外文件。

源码可干净构建，39 项纯 Java 回归检查通过，但存在协议兼容、服务端信任边界、时间窗与编辑器可靠性问题，不能据此宣称多人生产环境已安全可用。以下区分联动 PR 引入的问题和上游原有问题；发现原有问题不代表获准扩大修改范围。

## 上游与保留范围

| 对象 | 本轮核验值 |
| --- | --- |
| upstream | https://github.com/TT432/yessteveskill.git |
| origin | https://github.com/DanielFQZ/yessteveskill.git |
| upstream/master | `f8320d63164b9a06d2fa111c54adb1ace578a29b` |
| 本地分支 | `codex/public-combat-api-clean` |
| HEAD / origin 同名分支 / PR #3 head | `a8e18528b02c5d29b63c77527c316d005461b76f` |
| 相对上游 | 4 个提交，34 文件，998 行增加、54 行删除 |

保留提交：`2097ca4` 确认命中事件，`d5b7e9c` 公共战斗事件，`fe89aa4` Camera 战斗输入，`a8e1852` 清理测试假人与 Camera/VFX 接入、范围/根运动修正。

通过 PR ref 确认 [PR #3](https://github.com/TT432/yessteveskill/pull/3) 的 head 与当前源码一致。该提交未进入本次 fetch 得到的 upstream/master。匿名网页返回 404、API 限流，未确认 PR 当前开放/关闭状态，不把存在 PR ref 当作开放或已合并的证据。

已使用 `git restore --source=HEAD --staged --worktree .` 和 `git clean -fd` 清除本地 YSA 扩展，保留既有提交和分支历史。未创建这些未提交源码的完整可恢复备份。最终 `git status --short --untracked-files=all` 为空，`git diff --check` 通过；ignored 构建/日志目录不计入源码改动。

## 审计范围和证据等级

对全部 116 个生产 Java 文件所在模块、资源配置、构建脚本及 PR 全部差异进行仓库级静态检查；深读网络、命中/位移/滞空、状态和去重、根运动、Camera/VFX 接口、配置 Codec、工程读写、编辑器保存/导入、Windows 拖放及 YSM 资产访问等关键链路。GUI 布局、数据访问器等以结构与异常路径检查为主。

| 范围 | 生产 Java 数 | 检查重点 |
| --- | ---: | --- |
| 入口、客户端 setup、事件、mixin、network | 11 | 加载侧、事件顺序、消息方向、协议 |
| client/input、client/dnd | 8 | 原版输入兼容、原生失败路径 |
| client/gui（含 layout） | 18 | 保存/复制/删除/导入、预览释放 |
| combat、combat/config | 26 | 服务端配置、生命周期、解析失败 |
| editor/data | 13 | 数据往返、失败后保存、构造器兼容 |
| ysm | 3 | 能力/动画时钟、资产和缓存 |
| yss 顶层/attack | 25 | 命中、时间、根运动、几何和开销 |
| yss camera/extension/molang/project/runtime | 12 | 文件、关键帧、Molang、镜头 |

这是一轮全仓静态审计及离线验证，不是逐平台实机验收、渗透测试或第三方 YSM/native 内部审计。没有启动游戏/专服/多人会话，没有重新进行 YSA 全仓审计。

## 主要发现

定位均相对 `E:\JavaProject\YesSteveSkill-Daniel\src\main\java\io\github\tt432\yessteveskill\`，行号对应上述 HEAD。P1 应优先处理，P2 为明确功能或可靠性缺陷；以下均未修改源码。

### F01 · P1 · 新消息沿用旧协议号（PR 引入）

定位：`network/YssNetwork.java:31`、`:82`、`:128`。

增加消息 ID 6 `HitResolvedPacket`，但仍与上游同为协议 `1`，握手只比较该字符串。PR 服务端与原上游客户端可能完成握手；服务器确认命中即发送旧客户端未注册的消息，存在解码失败/断连风险。模组版本也同为 1.0。

建议最小方案为升级协议号并明确两端升级，或协商可选能力后再发新消息。静态确认消息和握手差异；未实机复现断连。

### F02 · P1 · 服务端信任客户端命中，VFX 扩大接受距离（上游缺口，PR 放大）

定位：`network/YssNetwork.java:148`、`:193`；`yss/attack/YssAttackServerExecutor.java:88`、`:388`、`:407`；`YssHitRange.java:7`。

攻击模式、模型、动画名、段、动画时间和目标 ID 都由客户端上报。服务端读取自己的伤害配置、检查距离/视线，却不验证真实动作起手/生命周期，也不重算几何。ONCE、ON_EXIT、INTERVAL 主要由客户端 tracker 实施，服务端每目标每段每 tick 一次不能代替动作级去重。impulse 位移和 hover 也缺少动作级次数约束。

PR 对服务器配置明确启用 `use_vfx_hitboxes` 的段，将距离上限扩至 64 格。恶意客户端可提交未与盒相交的可见目标。正常客户端仍先算相交，**不是正常游戏自动攻击 64 格内全体**；原版受伤无敌帧等也仍存在。

应由作者决定多人信任边界和同步方案。YSA 付款不能代替真实动画/几何授权；不因此自行扩建完整动作系统。

### F03 · P1 · NaN 绕过时间窗（上游已有）

定位：`yss/attack/YssAttackServerExecutor.java:113`、`:181`；`network/YssNetwork.java:164`。

网络 float 可承载 NaN；拒绝条件 `time < start || time > end` 的两次比较均为 false，因此命中/位移通过时间窗检查。应先拒绝非有限值，再比较窗口。已用 Java 17 验证表达式；未向游戏服务器发送构造包。

### F04 · P2 · 命中包携带旧采样时间（上游已有）

定位：`yss/attack/YssAttackService.java:263`、`:265`、`:268`；`YssAttackRuntime.java:297`、`:317`、`:325`。

先读 `animSeconds()`，再 `tickJudge()` 推进时钟并记录 ONCE 命中，随后以旧时间发包。例如前次 0.09、本次 0.11、窗口 0.10～0.20，客户端命中，服务器却看到 0.09 并拒绝；客户端已去重，后续未必重发。渲染也会推进时钟，影响实际复现频率，不消除调用顺序问题。

建议命中结果携带实际判定时刻。源码确认，未实机重测。

### F05 · P2 · 服务端不读取导出的 .yss（上游已有）

定位：`combat/YssCombatConfigLoader.java:43`、`:47`；`yss/YssRuntimeProjectResolver.java:39`。

客户端支持目录和二进制 `.yss`，服务端却只找 `<模型>/hit.json`。只部署导出包时，客户端能生成判定，服务器找不到配置并拒绝伤害/位移，全局移动设置同样缺配置。应统一配置源，或明确服务端必须部署展开目录。源码调用链确认，未运行专服。

### F06 · P2 · 非法配置退化为空工程，保存可覆盖原数据（上游已有）

定位：`editor/data/HitProjectEditingState.java:72`、`:81`、`:86`；`combat/config/HitProjectConfig.java:81`。

编辑器读文件失败静默返回空 state；Codec 语义错误也回落为空动画/默认移动，未向调用方提供错误。用户可能以为只是未配置，确认保存后用空/不完整结果覆盖原 hit.json。服务端语义错误也可能被当作成功的空配置缓存。

应区分缺文件、合法空工程和解析失败；失败时阻止覆盖并给出字段诊断。建议临时文件加原子替换，避免直接截断后写入失败。本轮未覆盖实际工程文件。

### F07 · P2 · 工程复制有两处独立失败路径（上游已有）

定位：`yss/project/YssProjectManager.java:92`、`:97`、`:100`、`:104`。

目标不存在时，先调用 `Files.isSameFile(sourceDir, targetDir)` 抛 `NoSuchFileException`。即便跳过它，创建目标后，walk 首项仍为源根目录，再复制到已存在的目标根目录会抛 `FileAlreadyExistsException`。

已在临时目录用 Java 17 实测两种异常。应规范化比较路径、跳过 walk 根项并处理失败半成品，未改工程数据。

### F08 · P2 · 后段位移也会禁用起手 Camera 对敌（PR 引入，需确认预期）

定位：`yss/attack/YssAttackService.java:239`；`YssAttackRuntime.java:123`。

整段动画只要存在任何 casterMoves 或 hover，就持续发布阻断原因，不检查当前窗口。Camera 当前对 `start_only` 仅在动作首帧且非临时阻断时执行，因此后半段才位移的动画也可能一直没有起手对敌。

已对照 `E:\JavaProject\YesSteveCamera @ 81a726cb` 的 `CombatAssistService`。可能是有意的保守策略，应确认是否需要按活跃窗口阻断。

### F09 · P2 · 公共构造器兼容性不完整（PR 引入）

定位：`combat/config/AnimationHitConfig.java:16`；`editor/data/MutableAnimationHitConfig.java:28`、`MutableHitSegment.java:37`。

新增字段改变构造器签名，部分类型未保留旧重载。使用旧签名的扩展重新编译会失败，旧二进制可能 `NoSuchMethodError`。HitSegment 已保留旧重载，可采用同样策略。当前核对的 Camera/VFX 反射事件签名相符，不受上述构造器问题影响。

### F10 · P2 · 战斗状态缺少完整生命周期回收（上游已有）

定位：`yss/attack/YssAttackServerExecutor.java:72`、`:78`、`:388`、`:489`。

RATE 只在玩家再次发包时清理旧段桶，活跃桶内旧目标不逐项淘汰，离线玩家最外层桶不删除。PENDING 仅由目标继续 tick 且停帧结束时移除；目标卸载/移除后可能长期滞留。未发现 logout/server stopping 清理。单 JVM 多次开关世界也应避免旧 tick 影响限频。

PENDING 另有按目标 UUID 单槽覆盖语义，不同攻击者的延迟命中可互相覆盖。这是现有代码明确设计，需作者确认多人预期，不擅自当作完整排队实现。

### F11 · P2 · 写入失败前已清除 dirty（上游已有）

定位：`client/gui/YssHitEditorScreen.java:626`、`:628`、`:633`。

doSave 在真正写入前清掉未保存标记并重置按钮；磁盘写失败后虽显示失败消息，但 dirty 不恢复。应在成功后才提交 UI 状态。源码确认，未模拟磁盘故障。

## 其他观察

- 性能：`YssAttackService.java:327` 在检查 F3+B 前采样调试几何，关闭调试仍逐帧更新模型、生成盒/线并查询 VFX。`YssRuntimeProjectResolver.java:71` 先读取工程字节，调用方随后才比较 stamp，缓存没有避免重复 I/O。未测实际帧耗时。
- 路径：`YssPathHelper.java:24`、`:53` 允许 `.`、`..`、空尾段和 Windows 保留名。`..` 规范化后可越出工程根一层。已验证 JDK 语义，未证明可远程写/删任意文件，不作此推断。
- 容量：`.yss` reader 没有总大小、条目数和单条大小上限，大包可大量占用内存。这是本地作者文件入口，未做资源耗尽实验。
- 畸形作者数据：导出 `addDescendants` 没有 visited 防重复入队，循环父子关系可能使导出不终止。运行时 sampler 的防环不覆盖该导出路径。
- Windows 拖放：撤销 GLFW target 后注册失败，没有恢复旧 target，不能保证日志所称 fallback 可用。未模拟 OLE 故障，记录为待验证边界。
- 构建依赖 mavenLocal 的 YSM dev4 和 ClientSmoke，当前机器离线通过不等于全新 CI 可复现，native/兼容元数据仍须匹配。
- YSS AGENTS 仍称 wrapper 缺失、Gradle 8.8、无回归入口，实际为 wrapper 8.12 和两个回归任务。部分 PR 文档仍称待发 PR/待验收，根运动 yaw 取值时机说明也需同步。本轮为保持精确 PR baseline 未改 YSS 文档。

## 验证记录

- Java：`C:\Users\38683\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2`。
- YSS 根目录执行 `gradlew.bat clean build --offline --console=plain` 成功，9 个任务全部实际执行。
- 距离回归 11 项、根运动 28 项，合计 39 项；JUnit 为 `NO-SOURCE`。撤回前的 14 项 JUnit 和 YSA 跨模组 GameTest 不算当前版本证据。
- 有弃用和 YSM compatibility 动态派发警告，未证明它们全部无运行影响。
- 构建日志：`C:\Users\38683\AppData\Local\Temp\yss-pr3-clean-build.log`。
- 临时 Java 验证：`isSameFile=NoSuchFileException`、`copyRoot=FileAlreadyExistsException`、`NaN_window_rejected=false`、`dotdot_escapes_root=true`。未在仓库添加测试。
- 新包包含 MinecraftCombatInputMixin、mixin JSON、refmap；此前已检查 continueAttack 的 SRG 映射。clean 后未检出撤回的 YSA 费用/伤害/动作授权类或 JUnit 类。
- Camera：本地 `dev/1.20.1 @ 81a726cb`。VFX：临时只读 clone `DanielFQZ/YesSteveVFX @ adfb105df6015e828586c6718c86e41cd967fbfb`；`VfxApi.activeHitBoxes(UUID)` 返回含 `box()` 的 `List<VfxHitBox>`，与反射匹配。VFX 盒在客户端，服务端不会自动获得几何。

## 部署差异与后续边界

新包：`E:\JavaProject\YesSteveSkill-Daniel\build\libs\yessteveskill-1.0.jar`，SHA256 `BB44D8A333C6511CAF98F62D275CC774D40342372FDB99EF303C0F8ACC085A5B`。

客户端 `E:\Games\YSM拍摄端\.minecraft\versions\1.20.1-Forge_47.4.16\mods\yessteveskill-1.0.jar` 本轮核验仍为 `F7D445D465EB8DB3552EDFB8EBF4C0CE37BA1C6257787AC84E8A535E0B5BB088`。**该包仍是撤回前扩展版本，没有替换；源码回退不等于部署回退。**旧配置也未修改。

YSA 生产代码本轮保留原样，仍有未提交成果和草稿。`YsaMolangBinding` 的 queueMolangDamage 反射目标已被撤回，应视作未接通；CombatDamageType 和类型语义需在自己的仓库收敛。可选 YSS 集成测试仍针对撤回前接口，须后续调整，不能因此重新扩建 YSS。

优先整理自己的属性/战斗/资源 API、独立测试、示例和发布清单，再按授权上传 GitHub。用户联系原作者后协商最小调用点与失败语义。本报告不授权发送消息或创建新 PR。
