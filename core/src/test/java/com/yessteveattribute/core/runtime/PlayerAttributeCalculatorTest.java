package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.*;
import com.yessteveattribute.core.formula.FormulaException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAttributeCalculatorTest {
    private final PlayerAttributeCalculator calculator = new PlayerAttributeCalculator();

    private LoadedConfiguration config(String growth) {
        return new LoadedConfiguration(new AttributesConfiguration(Map.of(
                "力量", new AttributeDefinition("力量", "primary", "number", "点", 100, true),
                "护甲", new AttributeDefinition("护甲", "combat", "number", "点", 5, true),
                "文本", new AttributeDefinition("文本", "runtime", "string", "", "无", false)), Map.of()),
                new FormulasConfiguration(Map.of(), Map.of()),
                new RolesConfiguration(Map.of("战士", new RoleDefinition("战士", "战士", "默认", List.of(),
                        Map.of("力量", 10), Map.of("力量", growth)))),
                new LevelsConfiguration(Map.of("默认", new LevelRule("默认", 1, 100, 0, List.of(), List.of(), "keep"))));
    }

    @Test void growthReplacesDefaultsAndPreservesOneEffectiveSource() {
        var config = config("10 + <lvl>");
        var before = calculator.calculate(config, "", 0);
        var after = calculator.calculate(config, "战士", 5);
        assertEquals(100, before.get("力量"));
        assertEquals(15, after.get("力量"));
        assertEquals(5, after.get("护甲"));
        assertEquals(1, after.explain("力量").size());
        assertEquals("growth.力量", after.explain("力量").get(0).provenance().detail());
        assertEquals("default", after.explain("护甲").get(0).provenance().type());
        assertFalse(after.all().containsKey("文本"));
        assertThrows(UnsupportedOperationException.class, () -> after.all().put("力量", 1.0));
    }

    @Test void rejectsInvalidSelectionAndBrokenGrowth() {
        var config = config("10 + <lvl>");
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(config, "不存在", 1));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(config, "战士", 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(config, "战士", 101));
        assertThrows(FormulaException.class, () -> calculator.calculate(config("1 / (<lvl> - 5)"), "战士", 5));
        assertEquals(11, calculator.calculate(config, "战士", 1).get("力量"));
        assertEquals(110, calculator.calculate(config, "战士", 100).get("力量"));
    }

    @Test void equipmentAddsToRoleGrowthAndRetainsItsProvenance() {
        var config = config("10 + <lvl>");
        var provenance = new Provenance("equipment", "minecraft:iron_sword", "mainhand");
        var equipment = new AttributeSnapshot(Map.of("力量", 7.5), Map.of("力量",
                List.of(new AttributeSnapshot.Contribution(7.5, provenance))));
        var result = calculator.calculate(config, "战士", 5, equipment);
        assertEquals(22.5, result.get("力量"));
        assertEquals(2, result.explain("力量").size());
        assertEquals(provenance, result.explain("力量").get(1).provenance());
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(config, "战士", 5,
                new AttributeSnapshot(Map.of("不存在", 1.0), Map.of())));
    }

    @Test void equipmentOverflowCannotCorruptAnAttributeSnapshot() {
        var config = config(Double.toString(Double.MAX_VALUE));
        var provenance = new Provenance("equipment", "test", "mainhand");
        var equipment = new AttributeSnapshot(Map.of("力量", Double.MAX_VALUE), Map.of("力量",
                List.of(new AttributeSnapshot.Contribution(Double.MAX_VALUE, provenance))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(config, "战士", 5, equipment));
    }

    @Test void equipmentUpdatesResourceParametersAndUnequippingClampsBalance() throws Exception {
        var config = new YamlConfigLoader().load(Path.of("..", "templates"));
        var equipment = new AttributeAccumulator().add("智力", 10,
                new Provenance("nbt", "test", "mainhand/YSAAttributes[0]")).snapshot();
        var bare = calculator.calculate(config, "示例战士", 10);
        var equipped = calculator.calculate(config, "示例战士", 10, equipment);
        var resources = new ResourceCalculator();
        var initial = resources.calculate(config, bare, 10, Map.of());
        var added = resources.calculate(config, equipped, 10, Map.of("法力", 20.0));
        assertEquals(initial.get("法力").maximum() + 156, added.get("法力").maximum(), 1e-9);
        assertEquals(20, added.get("法力").current());
        var removed = resources.calculate(config, bare, 10, Map.of("法力", added.get("法力").maximum()));
        assertEquals(initial.get("法力").maximum(), removed.get("法力").current(), 1e-9);
    }

    @Test void realTemplatesProduceExpectedPlayerValues() throws Exception {
        var config = new YamlConfigLoader().load(Path.of("..", "templates"));
        var first = calculator.calculate(config, "示例战士", 1);
        var tenth = calculator.calculate(config, "示例战士", 10);
        assertEquals(10.6, first.get("力量"), 1e-9);
        assertEquals(12.8, first.get("耐力"), 1e-9);
        assertEquals(33.8864302332, tenth.get("力量"), 1e-9);
        assertEquals(100, config.getLevels().get("默认").getMax());
        assertTrue(tenth.all().values().stream().allMatch(Double::isFinite));
    }
}
