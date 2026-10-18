package com.yessteveattribute.core.config;

import java.util.Objects;

/** Links a resource's current value to its maximum and recovery/decay fields. */
public final class ResourceDefinition {
    private final String id;
    private final String current;
    private final String maximum;
    private final String recovery;
    private final String decay;
    private final Object initial;

    public ResourceDefinition(String id, String current, String maximum,
                              String recovery, String decay, Object initial) {
        this.id = Objects.requireNonNull(id, "id");
        this.current = Objects.requireNonNull(current, "current");
        this.maximum = Objects.requireNonNull(maximum, "maximum");
        this.recovery = recovery;
        this.decay = decay;
        this.initial = initial;
    }

    public String getId() { return id; }
    public String getCurrent() { return current; }
    public String getMaximum() { return maximum; }
    public String getRecovery() { return recovery; }
    public String getDecay() { return decay; }
    public Object getInitial() { return initial; }
}
