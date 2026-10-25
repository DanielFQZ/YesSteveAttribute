package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.LoadedConfiguration;
import com.yessteveattribute.core.formula.FormulaEngine;

/** Raw numeric defaults, role growth and equipment inputs for derived/combat calculation. */
public final class PlayerAttributeCalculator {
    public AttributeSnapshot calculate(LoadedConfiguration config, String roleId, int level) {
        return calculate(config, roleId, level, new AttributeSnapshot(java.util.Map.of(), java.util.Map.of()));
    }

    /** Equipment contributions add to the default or role-grown base values. */
    public AttributeSnapshot calculate(LoadedConfiguration config, String roleId, int level,
                                      AttributeSnapshot equipment) {
        var role = roleId.isEmpty() ? null : config.getRoles().get(roleId);
        if (!roleId.isEmpty() && role == null) throw new IllegalArgumentException("角色不存在: " + roleId);
        AttributeSnapshot growth = null;
        if (role != null) {
            var rule = config.getLevels().get(role.getLevelRule());
            if (rule == null || level < rule.getStart() || level > rule.getMax()) {
                throw new IllegalArgumentException("角色 " + roleId + " 的等级超出范围: " + level);
            }
            growth = new RoleAttributeCalculator().calculate(role, level, new FormulaEngine(config.getFormulas()));
        }
        var result = new AttributeAccumulator();
        for (var definition : config.getAttributes().getAttributes().values()) {
            String id = definition.getId();
            if (growth != null && growth.all().containsKey(id)) {
                double value = growth.get(id);
                finite(id, value);
                result.set(id, value, growth.explain(id).get(0).provenance());
            } else if (definition.getDefaultValue() instanceof Number number) {
                finite(id, number.doubleValue());
                result.set(id, number.doubleValue(), new Provenance("default", "attributes.yml", id));
            }
        }
        var base = result.snapshot();
        for (String id : equipment.all().keySet()) {
            if (!base.all().containsKey(id)) throw new IllegalArgumentException("装备引用了非数值属性: " + id);
        }
        return result.addAll(equipment).snapshot();
    }

    private static void finite(String id, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("属性不是有限数值: " + id);
    }
}
