package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed role/class entry from roles.yml. */
public final class RoleDefinition {
    private final String id;
    private final String displayName;
    private final String levelRule;
    private final List<Object> upgradeConditions;
    private final Map<String, Number> base;
    private final Map<String, String> growth;

    public RoleDefinition(String id, String displayName, String levelRule,
                          List<Object> upgradeConditions, Map<String, Number> base,
                          Map<String, String> growth) {
        this.id = id;
        this.displayName = displayName;
        this.levelRule = levelRule;
        this.upgradeConditions = Collections.unmodifiableList(upgradeConditions);
        this.base = Collections.unmodifiableMap(new LinkedHashMap<>(base));
        this.growth = Collections.unmodifiableMap(new LinkedHashMap<>(growth));
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getLevelRule() { return levelRule; }
    public List<Object> getUpgradeConditions() { return upgradeConditions; }
    public Map<String, Number> getBase() { return base; }
    public Map<String, String> getGrowth() { return growth; }
}
