package com.yessteveattribute.core.runtime;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.yessteveattribute.core.runtime.DerivedAttributeCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatCalculatorTest {
    private CombatCalculator.Result calculate(Map<String, Double> attack, Map<String, Double> defense, double multiplier, double roll) throws Exception {
        return calculate(attack, defense, multiplier, roll, CombatDamageType.COMPOSITE);
    }

    private CombatCalculator.Result calculate(Map<String, Double> attack, Map<String, Double> defense,
                                               double multiplier, double roll, CombatDamageType type) throws Exception {
        var config = config();
        var player = new PlayerAttributeCalculator();
        var attacker = player.calculate(config, "", 0, equipment(attack));
        var target = player.calculate(config, "", 0, equipment(defense));
        return new CombatCalculator().calculate(config, attacker, 0, resolve(config, attacker, Map.of()).snapshot(),
                resolve(config, target, Map.of()).snapshot(), multiplier, roll, type);
    }

    @Test void zeroCriticalRatingsDoNotDivideByZero() throws Exception {
        var result = calculate(Map.of("力量", 10.0, "武器伤害", 20.0), Map.of(), 1, .5);
        assertEquals(86, result.damage(), 1e-9);
        assertEquals(86, result.preMultiplierBaseDamage(), 1e-9);
        assertEquals(0, result.probability());
        assertFalse(result.critical());
    }

    @Test void splitsElementsThenSubtractsDefenseThenAppliesCriticalMultiplier() throws Exception {
        var attack = Map.of("力量", 10.0, "武器伤害", 20.0, "炽炎", 30.0, "暴击", 10.0);
        var defense = Map.of("护甲", 20.0, "炽炎抗性", 10.0, "暴击抗性", 10.0);
        var result = calculate(attack, defense, 1, .25);
        assertEquals(51.6, result.steps().get("无属性伤害"), 1e-9);
        assertEquals(34.4, result.steps().get("元素伤害总和"), 1e-9);
        assertEquals(78, result.beforeCritical(), 1e-9);
        assertEquals(.5, result.probability());
        assertTrue(result.critical());
        assertEquals(117, result.damage(), 1e-9);
        assertEquals(78, calculate(attack, defense, 1, .5).damage(), 1e-9);
        assertEquals(246, calculate(attack, defense, 2, .25).damage(), 1e-9);
    }

    @Test void derivedOutputEquipmentContributionsSurviveCombatRecalculation() throws Exception {
        var result = calculate(Map.of("力量", 10.0, "基础伤害", 10.0, "最终伤害", 5.0), Map.of(), 2, .5);
        assertEquals(152, result.baseDamage(), 1e-9);
        assertEquals(76, result.preMultiplierBaseDamage(), 1e-9);
        assertEquals(157, result.damage(), 1e-9);
    }

    @Test void explicitDamageTypeSelectsOnlyItsConfiguredComponentAndDefense() throws Exception {
        var attack = Map.of("力量", 10.0, "武器伤害", 20.0, "炽炎", 30.0, "暴击", 10.0);
        var defense = Map.of("护甲", 20.0, "炽炎抗性", 10.0, "暴击抗性", 10.0);
        var physical = calculate(attack, defense, 1, .5, CombatDamageType.PHYSICAL);
        var fire = calculate(attack, defense, 1, .5, CombatDamageType.FIRE);
        var composite = calculate(attack, defense, 1, .5);

        assertEquals(46.6, physical.damage(), 1e-9);
        assertEquals(51.6, physical.steps().get("无属性伤害"), 1e-9);
        assertEquals(5, physical.steps().get("护甲减伤"), 1e-9);
        assertEquals(31.4, fire.damage(), 1e-9);
        assertEquals(34.4, fire.steps().get("元素伤害总和"), 1e-9);
        assertEquals(3, fire.steps().get("元素减伤总和"), 1e-9);
        assertEquals(78, composite.damage(), 1e-9);
        assertEquals(CombatDamageType.FIRE, CombatDamageType.parse(" 炽炎 "));
        assertThrows(IllegalArgumentException.class, () -> CombatDamageType.parse("不存在"));
    }

    @Test void preservesNegativeFormulaResultAndRejectsInvalidInputs() throws Exception {
        assertEquals(-25, calculate(Map.of(), Map.of("护甲", 100.0), 1, .5).damage());
        assertThrows(IllegalArgumentException.class, () -> calculate(Map.of(), Map.of(), -1, .5));
        assertThrows(IllegalArgumentException.class, () -> calculate(Map.of(), Map.of(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> calculate(Map.of(), Map.of(), Double.NaN, .5));
        assertThrows(com.yessteveattribute.core.formula.FormulaException.class,
                () -> calculate(Map.of("基础伤害", Double.MAX_VALUE), Map.of(), 2, .5));
    }
}
