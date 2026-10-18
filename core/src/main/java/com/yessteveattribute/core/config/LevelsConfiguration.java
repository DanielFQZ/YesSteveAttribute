package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LevelsConfiguration {
    private final Map<String, LevelRule> rules;
    public LevelsConfiguration(Map<String, LevelRule> rules) {
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }
    public Map<String, LevelRule> getRules() { return rules; }
    public LevelRule get(String id) { return rules.get(id); }
}
