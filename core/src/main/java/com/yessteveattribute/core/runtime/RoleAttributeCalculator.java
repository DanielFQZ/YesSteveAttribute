package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.RoleDefinition;
import com.yessteveattribute.core.formula.EvaluationContext;
import com.yessteveattribute.core.formula.FormulaEngine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Calculates a role's primary attributes for one level. */
public final class RoleAttributeCalculator {
    public AttributeSnapshot calculate(RoleDefinition role, int level, FormulaEngine formulas) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(formulas, "formulas");
        if (level < 0) {
            throw new IllegalArgumentException("等级不能为负数: " + level);
        }

        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Provenance> sources = new LinkedHashMap<>();
        role.getBase().forEach((id, value) -> {
            values.put(id, value.doubleValue());
            sources.put(id, new Provenance("role", role.getId(), "base." + id));
        });

        EvaluationContext context = EvaluationContext.builder().put("lvl", level).build();
        role.getGrowth().forEach((id, expression) -> {
            double value = formulas.evaluateExpression(expression, context);
            values.put(id, value);
            sources.put(id, new Provenance("role", role.getId(), "growth." + id));
        });

        AttributeAccumulator accumulator = new AttributeAccumulator();
        values.forEach((id, value) -> accumulator.set(id, value, sources.get(id)));
        return accumulator.snapshot();
    }
}
