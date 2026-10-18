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
}
