package com.yessteveattribute.core.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the shared {@code 属性名: 值} syntax used by item NBT and Lore. */
public final class AttributeModifierParser {
    private static final Pattern ENTRY = Pattern.compile(
            "^\\s*(.+?)\\s*:\\s*((?:[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?)|(?:NaN|[-+]?Infinity))\\s*$");

    private AttributeModifierParser() {
    }

    public static Map<String, Double> parseLines(Iterable<String> lines, Set<String> knownAttributes,
                                                  Provenance provenance) {
        return parseLines(lines, knownAttributes, provenance, false);
    }

    /** Explicit NBT lists are strict; ordinary descriptive lore may be skipped. */
    public static Map<String, Double> parseLines(Iterable<String> lines, Set<String> knownAttributes,
                                               Provenance provenance, boolean strict) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            Matcher matcher = ENTRY.matcher(line);
            if (!matcher.matches()) {
                int colon = line.indexOf(':');
                String id = colon < 0 ? line.trim() : line.substring(0, colon).trim();
                if (strict || (knownAttributes != null && knownAttributes.contains(id))) {
                    throw new IllegalArgumentException("属性格式错误，应为 属性名: 数值：" + line);
                }
                continue;
            }
            String id = matcher.group(1).trim();
            if (knownAttributes != null && !knownAttributes.contains(id)) {
                throw new IllegalArgumentException("未知或暂不支持装备写入的数值属性: " + id);
            }
            double value = Double.parseDouble(matcher.group(2));
            if (!Double.isFinite(value)) throw new IllegalArgumentException("属性值不是有限数字: " + id);
            if (result.containsKey(id)) {
                throw new IllegalArgumentException("同一来源重复定义属性: " + id + " (" + provenance.detail() + ")");
            }
            result.put(id, value);
        }
        return result;
    }

    public static void apply(AttributeAccumulator accumulator, Iterable<String> lines,
                              Set<String> knownAttributes, Provenance provenance) {
        parseLines(lines, knownAttributes, provenance)
                .forEach((id, value) -> accumulator.add(id, value, provenance));
    }
}
