package com.yessteveattribute.core.runtime;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DamageApplicationTest {
    @Test void positiveDamageReachesPlatformExactlyOnceAndPreservesAcceptance() {
        for (boolean accepted : new boolean[]{true, false}) {
            var calls = new AtomicInteger();
            var result = DamageApplication.apply(12.3456789, amount -> {
                calls.incrementAndGet();
                assertEquals((float) 12.3456789, amount);
                return accepted;
            });
            assertEquals(1, calls.get());
            assertEquals(accepted ? DamageApplication.Status.ACCEPTED : DamageApplication.Status.REJECTED, result.status());
            assertEquals((float) 12.3456789, result.submitted());
        }
    }

    @Test void zeroAndNegativeResultsNeverHurtOrHeal() {
        for (double value : new double[]{0, -0.0, -1, -Double.MAX_VALUE}) {
            var result = DamageApplication.apply(value, amount -> fail("Platform must not be called"));
            assertEquals(DamageApplication.Status.NON_POSITIVE, result.status());
            assertEquals(0, result.submitted());
        }
    }

    @Test void unrepresentableResultsAreRejectedBeforeAnyPlatformSideEffects() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE, Math.nextUp((double) Float.MAX_VALUE), Double.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> DamageApplication.apply(value,
                    amount -> fail("Platform must not be called")));
        }
        assertEquals(Float.MAX_VALUE, DamageApplication.apply(Float.MAX_VALUE, amount -> true).submitted());
        assertEquals(Float.MIN_VALUE, DamageApplication.apply(Float.MIN_VALUE, amount -> true).submitted());
    }

    @Test void platformExceptionsAreNotRetried() {
        var calls = new AtomicInteger();
        var error = new IllegalStateException("event handler failure");
        var caught = assertThrows(IllegalStateException.class, () -> DamageApplication.apply(10, amount -> {
            calls.incrementAndGet();
            throw error;
        }));
        assertSame(error, caught);
        assertEquals(1, calls.get());
    }
}
