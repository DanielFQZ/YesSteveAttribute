package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.ResourceDefinition;

/** Immutable resource balance and rates in units per simulation second. */
public record ResourceState(ResourceDefinition definition, double current, double maximum,
                            double recovery, double decay) {
    public ResourceState {
        if (definition == null) throw new IllegalArgumentException("资源定义不能为空");
        finite("current", current);
        finite("maximum", maximum);
        finite("recovery", recovery);
        finite("decay", decay);
        if (maximum < 0 || recovery < 0 || decay < 0 || current < 0 || current > maximum)
            throw new IllegalArgumentException("资源 " + definition.getId() + " 数值范围无效");
    }

    public ResourceState set(double value) {
        finite("value", value);
        return new ResourceState(definition, Math.max(0, Math.min(maximum, value)), maximum, recovery, decay);
    }

    public ResourceState add(double delta) {
        finite("delta", delta);
        // Saturate before addition, so two finite operands cannot overflow.
        if (delta >= maximum - current) return set(maximum);
        if (delta <= -current) return set(0);
        return set(current + delta);
    }

    public ResourceState advance(double seconds) {
        finite("seconds", seconds);
        if (seconds < 0) throw new IllegalArgumentException("资源时间不能为负数");
        return add((recovery - decay) * seconds);
    }

    private static void finite(String field, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("资源 " + field + " 必须是有限数字");
    }
}
