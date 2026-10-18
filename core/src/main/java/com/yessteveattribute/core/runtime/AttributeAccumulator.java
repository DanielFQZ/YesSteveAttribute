package com.yessteveattribute.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds an immutable attribute snapshot from role, item, buff and API contributions. */
public final class AttributeAccumulator {
    private final Map<String, Double> values = new LinkedHashMap<>();
    private final Map<String, List<AttributeSnapshot.Contribution>> contributions = new LinkedHashMap<>();

    public AttributeAccumulator set(String id, double value, Provenance provenance) {
        values.put(id, value);
        contributions.computeIfAbsent(id, ignored -> new ArrayList<>())
                .add(new AttributeSnapshot.Contribution(value, provenance));
        return this;
    }

    public AttributeAccumulator add(String id, double delta, Provenance provenance) {
        values.merge(id, delta, Double::sum);
        contributions.computeIfAbsent(id, ignored -> new ArrayList<>())
                .add(new AttributeSnapshot.Contribution(delta, provenance));
        return this;
    }

    public AttributeSnapshot snapshot() {
        return new AttributeSnapshot(values, contributions);
    }
}
