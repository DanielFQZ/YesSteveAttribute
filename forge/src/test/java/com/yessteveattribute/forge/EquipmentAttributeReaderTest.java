package com.yessteveattribute.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentAttributeReaderTest {
    private final EquipmentAttributeReader reader = new EquipmentAttributeReader();
    private final Set<String> attributes = Set.of("力量", "护甲", "智力");

    private static ListTag strings(String... lines) {
        var tag = new ListTag();
        for (String line : lines) tag.add(StringTag.valueOf(line));
        return tag;
    }

    private static CompoundTag nbt(String... lines) {
        var tag = new CompoundTag();
        tag.put("YSAAttributes", strings(lines));
        return tag;
    }

    private static CompoundTag lore(String... lines) {
        var tag = new CompoundTag();
        var display = new CompoundTag();
        display.put("Lore", strings(lines));
        tag.put("display", display);
        return tag;
    }

    private EquipmentAttributeReader.Result read(CompoundTag... tags) {
        var items = new java.util.ArrayList<EquipmentAttributeReader.ItemData>();
        String[] slots = {"mainhand", "offhand", "feet", "legs", "chest", "head"};
        for (int i = 0; i < tags.length; i++) items.add(new EquipmentAttributeReader.ItemData(
                slots[i], "minecraft:stick", tags[i]));
        return reader.readItems(items, attributes);
    }

    @Test void readsNbtJsonAndLegacyLoreWithLocations() {
        var result = read(nbt("力量: 10"), lore(
                "{\"text\":\"力量: \",\"color\":\"gold\",\"extra\":[{\"text\":\"2.5\"}]}",
                "§a护甲: -2", "普通物品描述"));
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(12.5, result.snapshot().get("力量"));
        assertEquals(-2, result.snapshot().get("护甲"));
        var sources = result.snapshot().explain("力量");
        assertEquals("nbt", sources.get(0).provenance().type());
        assertEquals("mainhand/YSAAttributes[0]", sources.get(0).provenance().detail());
        assertEquals("lore", sources.get(1).provenance().type());
        assertEquals("offhand/display.Lore[0]", sources.get(1).provenance().detail());
    }

    @Test void duplicateAcrossNbtAndLoreInvalidatesWholeItem() {
        var bad = nbt("力量: 10", "护甲: 5");
        bad.put("display", lore("{\"text\":\"力量: 2\"}").getCompound("display"));
        var result = read(bad, nbt("智力: 3"));
        assertEquals(1, result.diagnostics().size());
        assertEquals(0, result.snapshot().get("力量"));
        assertEquals(0, result.snapshot().get("护甲"));
        assertEquals(3, result.snapshot().get("智力"));
    }

    @Test void malformedUnknownAndNonFiniteEntriesRejectWholeItem() {
        for (var bad : List.of(nbt("护甲: 5", "力量: NaN"), nbt("护甲: 5", "错误: 1"),
                nbt("护甲: 5", "不是属性"), lore("护甲: 5", "力量: 10%"))) {
            var result = read(bad);
            assertEquals(1, result.diagnostics().size());
            assertTrue(result.snapshot().all().isEmpty());
        }
        var badType = new CompoundTag();
        badType.putInt("YSAAttributes", 1);
        assertEquals(1, read(badType).diagnostics().size());
    }

    @Test void overflowingItemDoesNotAffectOtherFieldsOrSlots() {
        var result = read(nbt("力量: " + Double.MAX_VALUE),
                nbt("护甲: 5", "力量: " + Double.MAX_VALUE), nbt("智力: 3"));
        assertEquals(1, result.diagnostics().size());
        assertEquals(Double.MAX_VALUE, result.snapshot().get("力量"));
        assertEquals(0, result.snapshot().get("护甲"));
        assertEquals(3, result.snapshot().get("智力"));
    }

    @Test void rejectsOversizedListsAndNonStringElements() {
        var list = new ListTag();
        for (int i = 0; i < 257; i++) list.add(StringTag.valueOf("说明"));
        var tag = new CompoundTag();
        tag.put("YSAAttributes", list);
        assertEquals(1, read(tag).diagnostics().size());
        var display = new CompoundTag();
        display.put("Lore", list);
        tag = new CompoundTag();
        tag.put("display", display);
        assertEquals(1, read(tag).diagnostics().size());
        var invalid = new ListTag();
        invalid.add(net.minecraft.nbt.IntTag.valueOf(1));
        display.put("Lore", invalid);
        assertEquals(1, read(tag).diagnostics().size());
    }

    @Test void emptyItemsAreHarmlessAndAllSlotsRetainSeparateSources() {
        assertTrue(read(new CompoundTag()).snapshot().all().isEmpty());
        var tag = nbt("力量: 1");
        var snapshot = read(tag, tag, tag, tag, tag, tag).snapshot();
        assertEquals(6, snapshot.get("力量"));
        assertEquals(6, snapshot.explain("力量").size());
        assertEquals("head/YSAAttributes[0]", snapshot.explain("力量").get(5).provenance().detail());
    }
}
