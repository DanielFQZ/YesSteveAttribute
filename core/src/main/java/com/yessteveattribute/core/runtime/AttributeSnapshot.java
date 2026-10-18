package com.yessteveattribute.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable values and source information exposed to UI and integrations. */
public final class AttributeSnapshot {
    private final Map<String, Double> values;
    private final Map<String, List<Contribution>> contributions;

    public AttributeSnapshot(Map<String, Double> values, Map<String, List<Contribution>> contributions) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        Map<String, List<Contribution>> copied = new LinkedHashMap<>();
        contributions.forEach((id, list) -> copied.put(id, List.copyOf(list)));
        this.contributions = Collections.unmodifiableMap(copied);
    }

    public Map<String, Double> all() { return values; }
    public double get(String id) { return values.getOrDefault(id, 0.0); }
    public List<Contribution> explain(String id) { return contributions.getOrDefault(id, List.of()); }

    public record Contribution(double value, Provenance provenance) { }
}
