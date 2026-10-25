package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.LoadedConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic combat calculation; applying damage is the platform adapter's responsibility. */
public final class CombatCalculator {
    public record Result(double baseDamage, double preMultiplierBaseDamage, double probability, boolean critical, double beforeCritical,
                         double damage, Map<String, Double> steps) {
        public Result { steps = Map.copyOf(steps); }
    }

    public Result calculate(LoadedConfiguration config, AttributeSnapshot attackerBase, int level,
                            AttributeSnapshot attackerSnapshot, AttributeSnapshot target,
                            double multiplier, double roll) {
        return calculate(config, attackerBase, level, attackerSnapshot, target, multiplier, roll,
                CombatDamageType.COMPOSITE);
    }

    /**
     * Calculates either the existing composite hit or one explicitly selected damage component.
     * Physical damage uses the configured untyped component and armor mitigation; elemental damage
     * uses only the selected element's component and resistance. Formula evaluation still happens
     * entirely on the server-side caller.
     */
    public Result calculate(LoadedConfiguration config, AttributeSnapshot attackerBase, int level,
                            AttributeSnapshot attackerSnapshot, AttributeSnapshot target,
                            double multiplier, double roll, CombatDamageType damageType) {
        if (damageType == null) throw new IllegalArgumentException("伤害类型不能为空");
        if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("倍率必须是有限非负数");
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("随机值必须位于 [0, 1)");
        for (String id : new String[]{"基础伤害", "暴击概率", "暴击前伤害", "最终伤害"}) {
            if (config.getFormulas().get(id) == null) throw new IllegalArgumentException("缺少战斗公式: " + id);
        }
        var inputs = new LinkedHashMap<String, Double>();
        target.all().forEach((id, value) -> inputs.put("目标." + id, value));
        config.getAttributes().getResources().values().forEach(r -> {
            if (attackerSnapshot.all().containsKey(r.getCurrent())) inputs.put(r.getCurrent(), attackerSnapshot.get(r.getCurrent()));
        });
        var initial = new DerivedAttributeCalculator.Resolver(config, attackerBase, level, inputs);
        double preMultiplierBaseDamage = initial.resolve("基础伤害");
        double baseDamage = preMultiplierBaseDamage * multiplier;
        if (!Double.isFinite(baseDamage) || baseDamage < 0) throw new IllegalArgumentException("基础伤害必须是有限非负数");
        inputs.put("基础伤害", baseDamage);

        if (damageType != CombatDamageType.COMPOSITE) {
            if (config.getFormulas().get(damageType.damageFormula()) == null
                    || config.getFormulas().get(damageType.mitigationFormula()) == null) {
                throw new IllegalArgumentException("缺少伤害类型公式: " + damageType.key());
            }
            var componentResolver = new DerivedAttributeCalculator.Resolver(config, attackerBase, level, inputs);
            double selectedDamage = componentResolver.resolve(damageType.damageFormula());
            double selectedMitigation = componentResolver.resolve(damageType.mitigationFormula());
            boolean physical = damageType == CombatDamageType.PHYSICAL;
            inputs.put("无属性伤害", physical ? selectedDamage : 0D);
            inputs.put("元素伤害总和", physical ? 0D : selectedDamage);
            inputs.put("元素减伤总和", physical ? 0D : selectedMitigation);
            inputs.put("护甲减伤", physical ? selectedMitigation : 0D);
            inputs.put("暴击前伤害", selectedDamage - selectedMitigation);
        }

        double probability = new DerivedAttributeCalculator.Resolver(config, attackerBase, level, inputs).resolve("暴击概率");
        if (probability < 0 || probability > 1) throw new IllegalArgumentException("暴击概率必须位于 [0, 1]");
        boolean critical = roll < probability;
        inputs.put("本次是否暴击", critical ? 1.0 : 0.0);
        var resolver = new DerivedAttributeCalculator.Resolver(config, attackerBase, level, inputs);
        double beforeCritical = resolver.resolve("暴击前伤害");
        double damage = resolver.resolve("最终伤害");
        // Keep the formula's signed result for diagnostics. It never implies healing.
        var steps = new LinkedHashMap<String, Double>();
        for (String id : new String[]{"基础伤害", "无属性伤害", "元素伤害总和", "护甲减伤", "元素减伤总和",
                "暴击概率", "本次是否暴击", "暴击前伤害", "最终伤害"}) {
            if (config.getAttributes().get(id) != null) steps.put(id, resolver.resolve(id));
        }
        return new Result(baseDamage, preMultiplierBaseDamage, probability, critical, beforeCritical, damage, steps);
    }
}
