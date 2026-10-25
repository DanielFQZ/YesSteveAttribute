package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceCalculatorTest {
    private LoadedConfiguration templates() throws Exception {
        return new YamlConfigLoader().load(Path.of("..", "templates"));
    }

    private Map<String, ResourceState> calculate(LoadedConfiguration config, int level, Map<String, Double> saved) {
        var base = new PlayerAttributeCalculator().calculate(config, "示例战士", level);
        return new ResourceCalculator().calculate(config, base, level, saved);
    }

    private LoadedConfiguration formula(LoadedConfiguration config, String id, String expression) {
        var formulas = new HashMap<>(config.getFormulas().getFormulas());
        formulas.put(id, new FormulaDefinition(id, "resources", expression));
        return new LoadedConfiguration(config.getAttributes(), new FormulasConfiguration(formulas, Map.of()),
                config.getRoles(), config.getLevels());
    }

    @Test void initializesAllTemplateResourcesAndCalculatesManaFromGrowth() throws Exception {
        var resources = calculate(templates(), 1, Map.of());
        assertEquals(4, resources.size());
        assertEquals(500, resources.get("精力").current());
        assertEquals(40, resources.get("精力").recovery());
        assertEquals(100, resources.get("集中").current());
        assertEquals(0, resources.get("充能").current());
        assertEquals(32.76, resources.get("法力").maximum(), 1e-9);
        assertEquals(32.76, resources.get("法力").current(), 1e-9);
        assertEquals(7.245, resources.get("法力").recovery(), 1e-9);
    }

    @Test void advancesRecoveryAndDecayAndClampsBothBounds() throws Exception {
        var config = formula(templates(), "充能衰减速度", "5");
        var resources = calculate(config, 1, Map.of("精力", 0.0, "集中", 0.0, "充能", 12.0));
        assertEquals(40, resources.get("精力").advance(1).current());
        assertEquals(500, resources.get("精力").advance(30).current());
        assertEquals(10, resources.get("集中").advance(1).current());
        assertEquals(7, resources.get("充能").advance(1).current());
        assertEquals(0, resources.get("充能").advance(10).current());
        assertEquals(100, resources.get("充能").add(Double.MAX_VALUE).current());
        assertEquals(0, resources.get("充能").add(-Double.MAX_VALUE).current());
        assertEquals(12, resources.get("充能").current());
    }

    @Test void recalculationPreservesBalancesRatherThanRefilling() throws Exception {
        var config = templates();
        var saved = Map.of("精力", 300.0, "充能", 37.0, "法力", 20.0);
        var next = calculate(config, 10, saved);
        assertEquals(300, next.get("精力").current());
        assertEquals(37, next.get("充能").current());
        assertEquals(20, next.get("法力").current());
        assertEquals(46.8, next.get("法力").maximum(), 1e-9);
        assertEquals(100, next.get("集中").current()); // Newly added / missing saved entry.
        var reduced = calculate(formula(config, "精力上限", "100"), 10, saved);
        assertEquals(100, reduced.get("精力").current());
        assertEquals(300, next.get("精力").current()); // Original immutable snapshot survives.
    }

    @Test void followsFormulaDependenciesInsteadOfUsingTheirDefaults() throws Exception {
        var config = formula(templates(), "精力上限", "<法力上限> * 2");
        assertEquals(65.52, calculate(config, 1, Map.of()).get("精力").maximum(), 1e-9);
    }

    @Test void rejectsInvalidParametersAndPreservesPreviousResult() throws Exception {
        var config = templates();
        var old = calculate(config, 1, Map.of("充能", 37.0));
        for (String expression : new String[]{"-1", "1 / 0", "1e309", "<精力上限> + 1", "<精力值> + 1",
                "1 ? 500 : <精力值>", "1 ? 500 : <本次是否暴击>", "1 ? 500 : <目标.护甲>"}) {
            var broken = formula(config, "精力上限", expression);
            assertThrows(IllegalArgumentException.class, () -> calculate(broken, 1, Map.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> calculate(formula(config, "精力恢复速度", "-1"), 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> calculate(config, 1, Map.of("充能", Double.NaN)));
        assertEquals(37, old.get("充能").current());
        assertThrows(IllegalArgumentException.class, () -> old.get("充能").set(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> old.get("充能").advance(-1));
    }

    @Test void overlayExposesSyncedValuesWithoutGrowingProvenanceHistory() throws Exception {
        var config = templates();
        var base = new PlayerAttributeCalculator().calculate(config, "示例战士", 1);
        var resources = calculate(config, 1, Map.of("精力", 100.0));
        var snapshot = ResourceCalculator.overlay(base, resources);
        for (int i = 0; i < 100; i++) snapshot = ResourceCalculator.overlay(snapshot, resources);
        assertEquals(100, snapshot.get("精力值"));
        assertEquals(500, snapshot.get("精力上限"));
        assertEquals(40, snapshot.get("精力恢复速度"));
        assertEquals(10.6, snapshot.get("力量"), 1e-9);
        assertEquals(1, snapshot.explain("精力值").size());
        assertEquals("resource", snapshot.explain("精力值").get(0).provenance().type());
    }

    @Test void rejectsConflictingResourceBindingsDuringConfigurationValidation() throws Exception {
        var config = templates();
        var resources = new HashMap<>(config.getAttributes().getResources());
        resources.put("冲突", new ResourceDefinition("冲突", "精力值", "精力上限", "精力恢复速度", null, true));
        var attributes = new AttributesConfiguration(config.getAttributes().getAttributes(), resources);
        var errors = ConfigurationValidator.validate(attributes, config.getFormulas(), config.getRoles(), config.getLevels());
        assertTrue(errors.stream().anyMatch(error -> error.contains("current 绑定重复")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("initial 必须")));
    }
}
