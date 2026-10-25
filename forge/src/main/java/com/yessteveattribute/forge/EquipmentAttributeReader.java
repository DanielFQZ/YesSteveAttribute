package com.yessteveattribute.forge;

import com.yessteveattribute.core.runtime.AttributeAccumulator;
import com.yessteveattribute.core.runtime.AttributeModifierParser;
import com.yessteveattribute.core.runtime.AttributeSnapshot;
import com.yessteveattribute.core.runtime.Provenance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads numeric modifiers from an explicit item NBT list and normal item lore. */
public final class EquipmentAttributeReader {
    public static final String NBT_LIST_KEY = "YSAAttributes";
    private static final Pattern LEGACY_FORMAT = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);

    public record Result(AttributeSnapshot snapshot, List<String> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
    }

    public record EquipmentView(List<ItemStack> stacks, Result result) {
        public EquipmentView { stacks = List.copyOf(stacks); }
    }

    /** Copy only for checkpoints; unchanged equipment need not be parsed again. */
    public static List<ItemStack> capture(LivingEntity player) {
        return SLOTS.stream().map(slot -> player.getItemBySlot(slot).copy()).toList();
    }

    public EquipmentView read(LivingEntity player, Set<String> numericAttributes) {
        return read(capture(player), numericAttributes);
    }

    public EquipmentView read(List<ItemStack> stacks, Set<String> numericAttributes) {
        if (stacks.size() != SLOTS.size()) throw new IllegalArgumentException("装备快照必须包含六个槽位");
        var items = new ArrayList<ItemData>();
        for (int index = 0; index < SLOTS.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (stack.isEmpty()) continue;
            items.add(new ItemData(SLOTS.get(index).getName(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getTag()));
        }
        return new EquipmentView(stacks, readItems(items, numericAttributes));
    }

    /** The NBT boundary can be tested without starting Forge's transformed registries. */
    record ItemData(String slot, String itemId, CompoundTag tag) { }

    Result readItems(List<ItemData> items, Set<String> numericAttributes) {
        var accumulator = new AttributeAccumulator();
        var diagnostics = new ArrayList<String>();
        for (var data : items) {
            try {
                var nbt = new ArrayList<String>();
                var lore = new ArrayList<String>();
                appendNbtLines(data.tag(), nbt);
                appendLoreLines(data.tag(), lore);
                var item = new AttributeAccumulator();
                var seen = new LinkedHashSet<String>();
                applyLines(item, seen, nbt, numericAttributes, data.itemId(), data.slot(), "nbt", NBT_LIST_KEY, true);
                applyLines(item, seen, lore, numericAttributes, data.itemId(), data.slot(), "lore", "display.Lore", false);
                accumulator.addAll(item.snapshot());
            } catch (RuntimeException ex) {
                String message = data.slot() + " " + data.itemId() + "：" + ex.getMessage();
                diagnostics.add(message);
            }
        }
        return new Result(accumulator.snapshot(), diagnostics);
    }

    private static void applyLines(AttributeAccumulator item, Set<String> seen, List<String> lines,
                                   Set<String> attributes, String itemId, String slot,
                                   String type, String field, boolean strict) {
        for (int i = 0; i < lines.size(); i++) {
            var source = new Provenance(type, itemId, slot + "/" + field + "[" + i + "]");
            var values = AttributeModifierParser.parseLines(List.of(lines.get(i)), attributes, source, strict);
            values.forEach((id, value) -> {
                if (!seen.add(id)) throw new IllegalArgumentException("同一物品重复定义属性: " + id + " (" + source.detail() + ")");
                item.add(id, value, source);
            });
        }
    }

    private static void appendNbtLines(CompoundTag tag, List<String> lines) {
        if (tag == null || !tag.contains(NBT_LIST_KEY)) return;
        Tag raw = tag.get(NBT_LIST_KEY);
        if (!(raw instanceof ListTag list)) throw new IllegalArgumentException("NBT " + NBT_LIST_KEY + " 必须是字符串列表");
        if (list.size() > 256) throw new IllegalArgumentException("NBT " + NBT_LIST_KEY + " 条目超过 256 项");
        for (Tag element : list) {
            if (!(element instanceof StringTag string)) throw new IllegalArgumentException("NBT " + NBT_LIST_KEY + " 含非字符串条目");
            lines.add(clean(string.getAsString()));
        }
    }

    private static void appendLoreLines(CompoundTag root, List<String> lines) {
        if (root == null || !root.contains("display", Tag.TAG_COMPOUND)) return;
        CompoundTag display = root.getCompound("display");
        if (!display.contains("Lore")) return;
        Tag raw = display.get("Lore");
        if (!(raw instanceof ListTag lore)) throw new IllegalArgumentException("display.Lore 必须是列表");
        if (lore.size() > 256) throw new IllegalArgumentException("Lore 超过 256 行");
        for (Tag element : lore) {
            if (!(element instanceof StringTag string)) throw new IllegalArgumentException("Lore 含非字符串行");
            String json = string.getAsString();
            try {
                Component component = Component.Serializer.fromJson(json);
                lines.add(clean(component == null ? json : component.getString()));
            } catch (RuntimeException ignored) {
                // Accept legacy/plain text lore as well as serialized 1.20.1 components.
                lines.add(clean(json));
            }
        }
    }

    private static String clean(String line) {
        return LEGACY_FORMAT.matcher(line).replaceAll("").trim();
    }

    public static boolean sameItems(List<ItemStack> left, List<ItemStack> right) {
        if (left == right) return true;
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.isSameItemSameTags(left.get(i), right.get(i))) return false;
        }
        return true;
    }
}
