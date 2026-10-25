package com.yessteveattribute.core.runtime;

import java.util.Objects;

/** Boundary from signed double formula results to one platform float damage call. */
public final class DamageApplication {
    private DamageApplication() { }

    @FunctionalInterface
    public interface Receiver {
        boolean hurt(float amount);
    }

    public enum Status { NON_POSITIVE, ACCEPTED, REJECTED }

    /** Accepted means the platform accepted hurt, not that health necessarily decreased. */
    public record Result(Status status, float submitted) { }

    public static Result apply(double calculated, Receiver receiver) {
        Objects.requireNonNull(receiver, "receiver");
        if (!Double.isFinite(calculated)) throw new IllegalArgumentException("最终伤害必须是有限数值");
        if (calculated <= 0) return new Result(Status.NON_POSITIVE, 0);
        float amount = (float) calculated;
        if (calculated > Float.MAX_VALUE || !Float.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("最终伤害超出原版 float 可表示的正数范围: " + calculated);
        }
        return new Result(receiver.hurt(amount) ? Status.ACCEPTED : Status.REJECTED, amount);
    }
}
