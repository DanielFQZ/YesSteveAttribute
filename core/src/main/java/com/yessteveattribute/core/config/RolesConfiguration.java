package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RolesConfiguration {
    private final Map<String, RoleDefinition> roles;
    public RolesConfiguration(Map<String, RoleDefinition> roles) {
        this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    }
    public Map<String, RoleDefinition> getRoles() { return roles; }
    public RoleDefinition get(String id) { return roles.get(id); }
}
