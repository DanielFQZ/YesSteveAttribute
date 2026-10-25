package com.yessteveattribute.core.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Compact display only; snapshots retain full double precision. */
public final class AttributeDisplay {
    private AttributeDisplay() { }
    public static String number(double value) {
        if (!Double.isFinite(value)) return "无效";
        double magnitude = Math.abs(value);
        if (magnitude >= 1e8 || (magnitude > 0 && magnitude < .0001)) return String.format(Locale.ROOT, "%.3e", value);
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
