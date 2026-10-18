// 可选的受限 JS 公式示例。
// 运行时只允许读取 ctx，不允许文件、网络、反射、线程和任意 Java 类访问。
// 实际引擎可以把 exported 函数编译成 FormulaDefinition。

export function levelHealth(ctx) {
  const level = ctx.progression("empyrean:player_level");
  return 100 + Math.pow(level, 1.2) * 12;
}

export function fireCriticalChance(ctx) {
  const chance = ctx.attr("empyrean:fire_crit_chance")
    - ctx.target.attr("empyrean:fire_crit_resist");
  return Math.max(0, Math.min(1, chance));
}
