package com.yessteveattribute.core.formula;

import com.yessteveattribute.core.config.FormulaDefinition;
import com.yessteveattribute.core.config.FormulasConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormulaEngineTest {
    @Test
    void evaluatesChinesePlaceholdersAndFormulaDependencies() {
        Map<String, FormulaDefinition> formulas = new LinkedHashMap<>();
        formulas.put("基础伤害", new FormulaDefinition("基础伤害", "damage", "<力量> * 6 + <武器伤害>"));
        formulas.put("最终伤害", new FormulaDefinition("最终伤害", "damage", "<基础伤害> * (<暴击> > 10 ? 2 : 1)"));
        FormulaEngine engine = new FormulaEngine(new FormulasConfiguration(formulas, Map.of()));

        EvaluationContext values = EvaluationContext.builder()
                .put("力量", 10)
                .put("武器伤害", 4)
                .put("暴击", 11)
                .build();
        assertEquals(128.0, engine.evaluate("最终伤害", values), 0.00001);
    }

    @Test
    void rejectsCyclesAndDivisionByZero() {
        Map<String, FormulaDefinition> formulas = new LinkedHashMap<>();
        formulas.put("甲", new FormulaDefinition("甲", "test", "<乙> + 1"));
        formulas.put("乙", new FormulaDefinition("乙", "test", "<甲> + 1"));
        FormulaEngine engine = new FormulaEngine(new FormulasConfiguration(formulas, Map.of()));
        assertThrows(FormulaException.class, () -> engine.evaluate("甲", EvaluationContext.builder().build()));
        assertThrows(FormulaException.class, () -> engine.evaluateExpression("1 / 0", EvaluationContext.builder().build()));
    }

    @Test
    void reportsFunctionArityAsFormulaError() {
        FormulaEngine engine = new FormulaEngine(new FormulasConfiguration(Map.of(), Map.of()));
        assertThrows(FormulaException.class,
                () -> engine.evaluateExpression("Math.pow(1)", EvaluationContext.builder().build()));
        assertThrows(FormulaException.class,
                () -> engine.evaluateExpression("Math.min()", EvaluationContext.builder().build()));
    }

    @Test
    void validatesTemplateTextFunctionSyntax() {
        FormulaEngine.validateExpressionSyntax("最高权重元素(<力量>, <敏捷>)");
    }

    @Test
    void consumesBothSidesOfLogicalOperators() {
        FormulaEngine engine = new FormulaEngine(new FormulasConfiguration(Map.of(), Map.of()));
        assertEquals(0.0, engine.evaluateExpression("1 >= 10 && 1 % 10 == 0", EvaluationContext.builder().build()));
        assertEquals(1.0, engine.evaluateExpression("1 >= 10 || 1 % 10 == 1", EvaluationContext.builder().build()));
    }

    @Test void shortCircuitsUnusedBranchesAndVariablesButStillChecksSyntax() {
        var engine = new FormulaEngine(new FormulasConfiguration(Map.of(), Map.of()));
        var empty = EvaluationContext.of(Map.of());
        assertEquals(7, engine.evaluateExpression("0 ? 1 / 0 : 7", empty));
        assertEquals(7, engine.evaluateExpression("1 ? 7 : <缺失>", empty));
        assertEquals(0, engine.evaluateExpression("0 && (1 % 0)", empty));
        assertEquals(1, engine.evaluateExpression("1 || <缺失>", empty));
        assertEquals(9, engine.evaluateExpression("1 ? (0 ? <缺失> : 9) : 1 / 0", empty));
        assertThrows(FormulaException.class, () -> engine.evaluateExpression("1 ? 1 / 0 : 7", empty));
        assertThrows(FormulaException.class, () -> engine.evaluateExpression("0 || <缺失>", empty));
        assertThrows(FormulaException.class, () -> engine.evaluateExpression("1 ? 7 : Math.pow(1)", empty));
        assertThrows(FormulaException.class, () -> FormulaEngine.validateExpressionSyntax("1 ? 7 : bad"));
    }
}
