package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.LoadedConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes bound resource parameters at login, role/level changes and reload. */
public final class ResourceCalculator {
    public Map<String, ResourceState> calculate(LoadedConfiguration config, AttributeSnapshot base,
                                               int level, Map<String, Double> saved) {
        var resolver = new DerivedAttributeCalculator.Resolver(config, base, level, Map.of());
        var result = new LinkedHashMap<String, ResourceState>();
        for (var definition : config.getAttributes().getResources().values()) {
            try {
                double maximum = resolver.resourceParameter(definition.getMaximum());
                double recovery = definition.getRecovery() == null ? 0 : resolver.resourceParameter(definition.getRecovery());
                double decay = definition.getDecay() == null ? 0 : resolver.resourceParameter(definition.getDecay());
                var state = new ResourceState(definition, 0, maximum, recovery, decay);
                // Validate initial even when an existing balance is present.
                Object initial = definition.getInitial();
                double initialValue;
                if (initial instanceof Number number) initialValue = number.doubleValue();
                else if ("maximum".equals(initial)) initialValue = maximum;
                else if (initial instanceof String id) initialValue = resolver.resourceParameter(id);
                else throw new IllegalArgumentException("initial 必须是数值、maximum 或属性 ID");
                state = state.set(initialValue);
                result.put(definition.getId(), saved.containsKey(definition.getId()) ? state.set(saved.get(definition.getId())) : state);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("资源 " + definition.getId() + ": " + ex.getMessage(), ex);
            }
        }
        return Map.copyOf(result);
    }

    /** Replace resource values and their effective sources, without accumulating history each tick. */
    public static AttributeSnapshot overlay(AttributeSnapshot base, Map<String, ResourceState> resources) {
        var values = new LinkedHashMap<>(base.all());
        var sources = new LinkedHashMap<String, List<AttributeSnapshot.Contribution>>();
        base.all().keySet().forEach(id -> sources.put(id, base.explain(id)));
        for (var state : resources.values()) {
            var d = state.definition();
            put(values, sources, d.getCurrent(), state.current(), d.getId(), "current");
            put(values, sources, d.getMaximum(), state.maximum(), d.getId(), "maximum");
            if (d.getRecovery() != null) put(values, sources, d.getRecovery(), state.recovery(), d.getId(), "recovery");
            if (d.getDecay() != null) put(values, sources, d.getDecay(), state.decay(), d.getId(), "decay");
        }
        return new AttributeSnapshot(values, sources);
    }

    private static void put(Map<String, Double> values, Map<String, List<AttributeSnapshot.Contribution>> sources,
                            String id, double value, String resource, String field) {
        values.put(id, value);
        sources.put(id, List.of(new AttributeSnapshot.Contribution(value, new Provenance("resource", resource, field))));
    }
}
