package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.FormulasConfiguration;
import com.yessteveattribute.core.config.LevelRule;
import com.yessteveattribute.core.config.RoleDefinition;
import com.yessteveattribute.core.formula.FormulaEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAndLevelCalculatorTest {
    private final FormulaEngine formulas = new FormulaEngine(new FormulasConfiguration(Map.of(), Map.of()));

    @Test
    void growthOverridesBaseAndKeepsProvenance() {
        RoleDefinition role = new RoleDefinition("战士", "战士", "默认", List.of(),
                Map.of("力量", 10, "耐力", 12), Map.of("力量", "10 + 0.6 * <lvl>"));

        AttributeSnapshot snapshot = new RoleAttributeCalculator().calculate(role, 5, formulas);

        assertEquals(13.0, snapshot.get("力量"), 0.00001);
        assertEquals("growth.力量", snapshot.explain("力量").get(0).provenance().detail());
        assertEquals(12.0, snapshot.get("耐力"), 0.00001);
        assertEquals("base.耐力", snapshot.explain("耐力").get(0).provenance().detail());
    }

    @Test
    void selectsTheFirstMatchingExperienceSegment() {
        LevelRule rule = new LevelRule("默认", 1, 100, 0,
                List.of(new LevelRule.Segment("<lvl> <= 10", "500 + 120 * <lvl>"),
                        new LevelRule.Segment("<lvl> > 10", "<prev> * 1.25")),
                List.of(), "keep");
        LevelExperienceCalculator calculator = new LevelExperienceCalculator();

        assertEquals(1100.0, calculator.requiredExperience(rule, 5, 0, formulas), 0.00001);
        assertEquals(1250.0, calculator.requiredExperience(rule, 11, 1000, formulas), 0.00001);
        assertThrows(IllegalArgumentException.class,
                () -> calculator.requiredExperience(rule, 0, 0, formulas));
    }
}
