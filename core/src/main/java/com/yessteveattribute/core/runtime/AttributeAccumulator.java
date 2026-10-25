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
        requireFinite(id, value);
        values.put(id, value);
        contributions.computeIfAbsent(id, ignored -> new ArrayList<>())
                .add(new AttributeSnapshot.Contribution(value, provenance));
        return this;
    }

    public AttributeAccumulator add(String id, double delta, Provenance provenance) {
        requireFinite(id, delta);
        double total = values.getOrDefault(id, 0.0) + delta;
        requireFinite(id, total);
        values.put(id, total);
        contributions.computeIfAbsent(id, ignored -> new ArrayList<>())
                .add(new AttributeSnapshot.Contribution(delta, provenance));
        return this;
    }

    public AttributeSnapshot snapshot() {
        return new AttributeSnapshot(values, contributions);
    }

    /** Add a complete item transactionally, so one overflowing field rejects the whole item. */
    public AttributeAccumulator addAll(AttributeSnapshot snapshot) {
        var totals = new LinkedHashMap<>(values);
        for (String id : snapshot.all().keySet()) {
            for (var contribution : snapshot.explain(id)) {
                requireFinite(id, contribution.value());
                double total = totals.getOrDefault(id, 0.0) + contribution.value();
                requireFinite(id, total);
                totals.put(id, total);
            }
        }
        values.clear();
        values.putAll(totals);
        snapshot.all().keySet().forEach(id -> contributions.computeIfAbsent(id, ignored -> new ArrayList<>())
                .addAll(snapshot.explain(id)));
        return this;
    }

    private static void requireFinite(String id, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("属性 " + id + " 必须是有限数值");
    }
}
