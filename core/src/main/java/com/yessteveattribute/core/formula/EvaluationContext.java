package com.yessteveattribute.core.formula;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Numeric inputs visible to one formula evaluation. */
public final class EvaluationContext {
    private final Map<String, Double> values;

    private EvaluationContext(Map<String, Double> values) {
        this.values = Map.copyOf(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EvaluationContext of(Map<String, ? extends Number> values) {
        Builder builder = builder();
        values.forEach((key, value) -> builder.put(key, value));
        return builder.build();
    }

    public Double get(String name) {
        return values.get(name);
    }

    public Map<String, Double> values() {
        return values;
    }

    public static final class Builder {
        private final Map<String, Double> values = new LinkedHashMap<>();

        public Builder put(String name, Number value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            values.put(name, value.doubleValue());
            return this;
        }

        public EvaluationContext build() {
            return new EvaluationContext(values);
        }
    }
}
