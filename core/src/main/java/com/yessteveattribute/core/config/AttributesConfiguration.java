package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed attributes.yml. Grouping is retained for UI and diagnostics only. */
public final class AttributesConfiguration {
    private final Map<String, AttributeDefinition> attributes;
    private final Map<String, ResourceDefinition> resources;

    public AttributesConfiguration(Map<String, AttributeDefinition> attributes,
                                   Map<String, ResourceDefinition> resources) {
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
    }

    public Map<String, AttributeDefinition> getAttributes() { return attributes; }
    public Map<String, ResourceDefinition> getResources() { return resources; }
    public AttributeDefinition get(String id) { return attributes.get(id); }
}
