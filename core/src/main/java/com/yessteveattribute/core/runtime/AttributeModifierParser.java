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
            "^\\s*(.+?)\\s*:\\s*([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?)\\s*$");

    private AttributeModifierParser() {
    }

    public static Map<String, Double> parseLines(Iterable<String> lines, Set<String> knownAttributes,
                                                  Provenance provenance) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            Matcher matcher = ENTRY.matcher(line);
            if (!matcher.matches()) continue;
            String id = matcher.group(1).trim();
            if (knownAttributes != null && !knownAttributes.contains(id)) {
                throw new IllegalArgumentException("未知属性字段: " + id);
            }
            if (result.containsKey(id)) {
                throw new IllegalArgumentException("同一来源重复定义属性: " + id + " (" + provenance.detail() + ")");
            }
            result.put(id, Double.parseDouble(matcher.group(2)));
        }
        return result;
    }

    public static void apply(AttributeAccumulator accumulator, Iterable<String> lines,
                              Set<String> knownAttributes, Provenance provenance) {
        parseLines(lines, knownAttributes, provenance)
                .forEach((id, value) -> accumulator.add(id, value, provenance));
    }
}
