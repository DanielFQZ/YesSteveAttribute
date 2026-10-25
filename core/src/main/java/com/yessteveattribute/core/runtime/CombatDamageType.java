package com.yessteveattribute.core.runtime;

import java.util.Map;

/** Selects one configured damage component for an explicitly typed combat hit. */
public enum CombatDamageType {
    COMPOSITE("综合", null, null),
    PHYSICAL("物理", "无属性伤害", "护甲减伤"),
    FIRE("炽炎", "炽炎伤害", "炽炎减伤"),
    ICE("冰霜", "冰霜伤害", "冰霜减伤"),
    WIND("狂风", "狂风伤害", "狂风减伤"),
    LIGHTNING("雷暴", "雷暴伤害", "雷暴减伤"),
    HOLY("圣光", "圣光伤害", "圣光减伤"),
    SHADOW("暗影", "暗影伤害", "暗影减伤");

    private static final Map<String, CombatDamageType> BY_KEY = java.util.Arrays.stream(values())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(CombatDamageType::key, value -> value));

    private final String key;
    private final String damageFormula;
    private final String mitigationFormula;

    CombatDamageType(String key, String damageFormula, String mitigationFormula) {
        this.key = key;
        this.damageFormula = damageFormula;
        this.mitigationFormula = mitigationFormula;
    }

    public String key() { return key; }
    public String damageFormula() { return damageFormula; }
    public String mitigationFormula() { return mitigationFormula; }

    public static CombatDamageType parse(String key) {
        CombatDamageType type = key == null ? null : BY_KEY.get(key.trim());
        if (type == null) throw new IllegalArgumentException("未知伤害类型：" + key);
        return type;
    }
}
