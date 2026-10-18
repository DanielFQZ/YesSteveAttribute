package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed level and experience rule. Unknown promotion fields are retained. */
public final class LevelRule {
    public static final class Segment {
        private final String when;
        private final String formula;
        public Segment(String when, String formula) {
            this.when = when;
            this.formula = formula;
        }
        public String getWhen() { return when; }
        public String getFormula() { return formula; }
    }

    private final String id;
    private final int start;
    private final int max;
    private final Number initialExp;
    private final List<Segment> experience;
    private final List<Object> promotion;
    private final String overflowExp;

    public LevelRule(String id, int start, int max, Number initialExp,
                     List<Segment> experience, List<Object> promotion,
                     String overflowExp) {
        this.id = id;
        this.start = start;
        this.max = max;
        this.initialExp = initialExp;
        this.experience = Collections.unmodifiableList(experience);
        this.promotion = Collections.unmodifiableList(promotion);
        this.overflowExp = overflowExp;
    }

    public String getId() { return id; }
    public int getStart() { return start; }
    public int getMax() { return max; }
    public Number getInitialExp() { return initialExp; }
    public List<Segment> getExperience() { return experience; }
    public List<Object> getPromotion() { return promotion; }
    public String getOverflowExp() { return overflowExp; }
}
