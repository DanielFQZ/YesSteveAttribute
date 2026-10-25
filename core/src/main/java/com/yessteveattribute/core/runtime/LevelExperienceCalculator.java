package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.LevelRule;
import com.yessteveattribute.core.formula.EvaluationContext;
import com.yessteveattribute.core.formula.FormulaEngine;

import java.util.Objects;

/** Evaluates the configured experience requirement for a level transition. */
public final class LevelExperienceCalculator {
    public double requiredExperience(LevelRule rule, int level, double previousRequirement,
                                     FormulaEngine formulas) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(formulas, "formulas");
        if (level < rule.getStart() || level > rule.getMax()) {
            throw new IllegalArgumentException("等级超出规则范围: " + level
                    + " (" + rule.getStart() + "-" + rule.getMax() + ")");
        }
        EvaluationContext context = EvaluationContext.builder()
                .put("lvl", level)
                .put("prev", previousRequirement)
                .build();
        for (LevelRule.Segment segment : rule.getExperience()) {
            if (formulas.evaluateExpression(segment.getWhen(), context) != 0.0) {
                return formulas.evaluateExpression(segment.getFormula(), context);
            }
        }
        throw new IllegalArgumentException("等级没有匹配的经验段: " + rule.getId() + " level=" + level);
    }
}
