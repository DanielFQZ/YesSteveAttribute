package com.yessteveattribute.core.config;

import java.util.Objects;

/** An attribute definition flattened from any group in attributes.yml. */
public final class AttributeDefinition {
    private final String id;
    private final String group;
    private final String type;
    private final String unit;
    private final Object defaultValue;
    private final boolean visible;

    public AttributeDefinition(String id, String group, String type, String unit,
                               Object defaultValue, boolean visible) {
        this.id = Objects.requireNonNull(id, "id");
        this.group = Objects.requireNonNull(group, "group");
        this.type = Objects.requireNonNull(type, "type");
        this.unit = unit == null ? "" : unit;
        this.defaultValue = defaultValue;
        this.visible = visible;
    }

    public String getId() { return id; }
    public String getGroup() { return group; }
    public String getType() { return type; }
    public String getUnit() { return unit; }
    public Object getDefaultValue() { return defaultValue; }
    public boolean isVisible() { return visible; }

    @Override
    public String toString() {
        return "AttributeDefinition{" + id + " (" + group + ")}";
    }
}
