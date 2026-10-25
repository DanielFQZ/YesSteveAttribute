# Paper 1.20.1 / MythicMobs 5.7.2 适配说明

本仓库的 Paper 适配拆成两个插件：YesSteveAttribute 提供属性、资源和伤害服务；YesSteveSkill 只负责 MythicMobs 扩展点。Forge YSA/YSS 与这两个插件互不打包，客户端 YSS 保持 PR3 的 Camera/VFX 联动版本。

首版已实现 /ysa status、/ysa reload 和玩家自己的 /ysa attr。YSA 启动时从插件目录生成四个 YAML 文件，reload 解析和校验通过后才替换内存中的配置，失败会保留旧配置。

YSS Paper 首版通过 MythicMobs 5.7.2 的官方事件注册：

`yaml
- ysadamage{multiplier=1.0;type=COMPOSITE} @target
- ysaattribute{attribute=力量;value=>=10} ?
`

ysadamage 只接受玩家施法者和有生命目标，伤害由 YSA 服务端计算后调用 Bukkit 的一次 damage；客户端不能提交最终伤害数值。ysaattribute 首版只检查玩家属性，供 MythicMobs 条件链使用。

当前构建依赖本机 Paper API 1.20.1 缓存和开发服中的 MythicMobs 5.7.2 jar，依赖均为 compileOnly。插件已安装到目标开发服并完成启动级验收；玩家命令、reload 回滚和 MythicMobs 技能伤害仍待实际游戏验收。首启后对已有技能执行一次 /mm reload，让 MythicMobs 重新解析自定义机制。

下一步应先在目标服停机窗口备份后安装构建产物，确认插件启动日志，再用一个最小 MythicMobs 技能验证机制注册、条件判断、伤害一次性结算和 /ysa reload 回滚。
