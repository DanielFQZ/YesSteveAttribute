package com.yessteveattribute.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeModifierParserTest {
    @Test
    void parsesChineseLoreAndPreservesProvenance() {
        Provenance source = new Provenance("lore", "item", "主手第1行");
        AttributeAccumulator accumulator = new AttributeAccumulator();
        AttributeModifierParser.apply(accumulator,
                List.of("力量: 100", "暴击倍率: 0.25", "普通文本"),
                Set.of("力量", "暴击倍率"), source);
        AttributeSnapshot snapshot = accumulator.snapshot();
        assertEquals(100.0, snapshot.get("力量"));
        assertEquals(0.25, snapshot.get("暴击倍率"));
        assertEquals(source, snapshot.explain("力量").get(0).provenance());
    }

    @Test
    void rejectsDuplicateAndUnknownFields() {
        Provenance source = new Provenance("nbt", "item", "测试");
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("力量: 1", "力量: 2"), Set.of("力量"), source));
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("不存在: 1"), Set.of("力量"), source));
    }

    @Test
    void rejectsNonFiniteNumericEntries() {
        Provenance source = new Provenance("lore", "item", "主手第1行");
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("力量: 1e309"), Set.of("力量"), source));
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("力量: NaN"), Set.of("力量"), source));
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("力量: Infinity"), Set.of("力量"), source));
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("力量: -Infinity"), Set.of("力量"), source));
    }

    @Test void acceptsSignedDecimalsAndScientificNotationButRejectsMalformedKnownValues() {
        var source = new Provenance("nbt", "item", "test");
        var values = AttributeModifierParser.parseLines(List.of("力量: -2.5", "护甲: +1e2", "暴击: .25"),
                Set.of("力量", "护甲", "暴击"), source, true);
        assertEquals(-2.5, values.get("力量"));
        assertEquals(100, values.get("护甲"));
        assertEquals(.25, values.get("暴击"));
        for (String invalid : List.of("力量: 10%", "力量: abc", "力量:")) {
            assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                    List.of(invalid), Set.of("力量"), source));
        }
        assertThrows(IllegalArgumentException.class, () -> AttributeModifierParser.parseLines(
                List.of("这是说明"), Set.of("力量"), source, true));
        assertEquals(java.util.Map.of(), AttributeModifierParser.parseLines(
                List.of("这是说明", "作者: 某人"), Set.of("力量"), source));
    }

    @Test void overflowingItemDoesNotLeavePartialContributions() {
        var source = new Provenance("nbt", "item", "test");
        var total = new AttributeAccumulator().add("力量", Double.MAX_VALUE, source);
        var item = new AttributeAccumulator().add("护甲", 5, source).add("力量", Double.MAX_VALUE, source);
        assertThrows(IllegalArgumentException.class, () -> total.addAll(item.snapshot()));
        assertEquals(Double.MAX_VALUE, total.snapshot().get("力量"));
        assertEquals(0, total.snapshot().get("护甲"));
        assertEquals(1, total.snapshot().explain("力量").size());
        assertEquals(0, total.snapshot().explain("护甲").size());
    }
}
