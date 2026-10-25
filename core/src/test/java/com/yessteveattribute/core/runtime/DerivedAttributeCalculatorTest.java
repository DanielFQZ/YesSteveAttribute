package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.*;
import com.yessteveattribute.core.formula.FormulaException;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DerivedAttributeCalculatorTest {
    static LoadedConfiguration config() throws Exception { return new YamlConfigLoader().load(Path.of("..", "templates")); }
    static AttributeSnapshot equipment(Map<String, Double> values) {
        var result = new AttributeAccumulator();
        values.forEach((id, value) -> result.add(id, value, new Provenance("nbt", "test", "mainhand")));
        return result.snapshot();
    }
    static LoadedConfiguration formula(LoadedConfiguration config, String id, String expression) {
        var formulas = new LinkedHashMap<>(config.getFormulas().getFormulas());
        formulas.put(id, new FormulaDefinition(id, "test", expression));
        return new LoadedConfiguration(config.getAttributes(), new FormulasConfiguration(formulas, Map.of()), config.getRoles(), config.getLevels());
    }
    static DerivedAttributeCalculator.Result resolve(LoadedConfiguration config, AttributeSnapshot base, Map<String, Double> saved) {
        var resources = new ResourceCalculator().calculate(config, base, 1, saved);
        return new DerivedAttributeCalculator().calculate(config, base, 1, resources);
    }

    @Test void computesPlayerOnlyFormulasAndAccountsForEveryConfiguredAttribute() throws Exception {
        var config = config();
        var base = new PlayerAttributeCalculator().calculate(config, "示例战士", 1);
        var result = resolve(config, base, Map.of());
        assertEquals(69.96, result.snapshot().get("基础伤害"), 1e-9);
        assertEquals(240.64, result.snapshot().get("生命上限"), 1e-9);
        assertEquals(69.96, result.snapshot().get("无属性伤害"), 1e-9);
        assertEquals("无", result.text().get("元素亲和"));
        assertTrue(result.pending().containsAll(Set.of("护甲减伤", "最终伤害", "暴击概率", "本次是否暴击")));
        assertFalse(result.snapshot().all().containsKey("最终伤害"));
        var keys = new HashSet<>(result.snapshot().all().keySet());
        keys.addAll(result.text().keySet());
        keys.addAll(result.pending());
        assertEquals(config.getAttributes().getAttributes().keySet(), keys);
    }

    @Test void formulaModifiersPropagateToDependentsAndResourceParametersOnlyOnce() throws Exception {
        var config = formula(config(), "精力上限", "<法力上限> * 2");
        var base = new PlayerAttributeCalculator().calculate(config, "示例战士", 1,
                equipment(Map.of("基础伤害", 10.0, "法力上限", 20.0)));
        var result = resolve(config, base, Map.of("精力", 10.0));
        assertEquals(79.96, result.snapshot().get("基础伤害"), 1e-9);
        assertEquals(79.96, result.snapshot().get("无属性伤害"), 1e-9);
        assertEquals(52.76, result.snapshot().get("法力上限"), 1e-9);
        assertEquals(105.52, result.snapshot().get("精力上限"), 1e-9);
        assertEquals(10, result.snapshot().get("精力值"));
        assertEquals(2, result.snapshot().explain("基础伤害").size());
        assertEquals("formula", result.snapshot().explain("法力上限").get(0).provenance().type());
        assertEquals("nbt", result.snapshot().explain("法力上限").get(1).provenance().type());
        for (int i = 0; i < 5; i++) {
            var next = resolve(config, base, Map.of("精力", (double) i));
            assertEquals(79.96, next.snapshot().get("基础伤害"), 1e-9);
        }
    }

    @Test void currentDependentDerivedFormulaRefreshesAndFailuresLeaveOldSnapshotIntact() throws Exception {
        var config = formula(config(), "基础伤害", "100 / (<充能值> + 1)");
        var base = new PlayerAttributeCalculator().calculate(config, "示例战士", 1);
        var before = resolve(config, base, Map.of("充能", 0.0));
        assertEquals(100, before.snapshot().get("基础伤害"));
        assertEquals(25, resolve(config, base, Map.of("充能", 3.0)).snapshot().get("基础伤害"));
        var broken = formula(config, "基础伤害", "1 / <充能值>");
        assertThrows(FormulaException.class, () -> resolve(broken, base, Map.of("充能", 0.0)));
        assertEquals(100, before.snapshot().get("基础伤害"));
    }

    @Test void detectsContextTransitivelyAndRejectsDormantCycles() throws Exception {
        var config = formula(config(), "基础伤害", "<最终伤害> + 1");
        var base = new PlayerAttributeCalculator().calculate(config, "示例战士", 1);
        assertThrows(FormulaException.class, () -> resolve(config, base, Map.of()));
        var contextual = formula(config(), "生命上限", "<目标.护甲> * 2");
        assertTrue(resolve(contextual, base, Map.of()).pending().contains("生命上限"));
        var cycle = formula(config(), "生命上限", "1 ? 10 : <生命上限>");
        assertThrows(FormulaException.class, () -> resolve(cycle, base, Map.of()));
    }

    @Test void elementAffinityUsesHighestPositiveWeightWithStableTieOrder() throws Exception {
        var config = config();
        var player = new PlayerAttributeCalculator();
        assertEquals("炽炎", resolve(config, player.calculate(config, "示例战士", 1,
                equipment(Map.of("炽炎", 10.0, "冰霜", 10.0))), Map.of()).text().get("元素亲和"));
        assertEquals("冰霜", resolve(config, player.calculate(config, "示例战士", 1,
                equipment(Map.of("炽炎", 10.0, "冰霜", 20.0))), Map.of()).text().get("元素亲和"));
    }

    @Test void displayFormattingDoesNotExposeFloatingPointTails() {
        assertEquals("7.245", AttributeDisplay.number(7.244999999999999));
        assertEquals("0", AttributeDisplay.number(-0.0));
        assertEquals("1.000e-08", AttributeDisplay.number(1e-8));
        assertEquals("1.000e+20", AttributeDisplay.number(1e20));
    }
}
