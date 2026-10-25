package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.ResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConsumptionTest {
    private ResourceState state(String id, double balance) {
        return new ResourceState(new ResourceDefinition(id, id + "值", id + "上限", null, null, 0),
                balance, 100, 0, 0);
    }

    @Test void paysSeveralResourcesAndExactBalanceWithoutMutatingInput() {
        var before = Map.of("法力", state("法力", 10), "精力", state("精力", 30), "充能", state("充能", 8));
        var result = ResourceConsumption.evaluate(before, Map.of("法力", 10.0, "精力", 2.5));
        assertTrue(result.accepted());
        assertEquals(0, result.after().get("法力").current());
        assertEquals(27.5, result.after().get("精力").current());
        assertEquals(8, result.after().get("充能").current());
        assertEquals(10, before.get("法力").current());
    }

    @Test void anyShortageRejectsWholePaymentAndReportsEveryShortage() {
        var before = Map.of("法力", state("法力", 10), "精力", state("精力", 3), "充能", state("充能", 0));
        var result = ResourceConsumption.evaluate(before, Map.of("法力", 5.0, "精力", 4.0, "充能", 2.0));
        assertFalse(result.accepted());
        assertEquals(Map.of("精力", 1.0, "充能", 2.0), result.shortages());
        assertEquals(before, result.after());
    }

    @Test void invalidRequestsFailEvenWhenAnotherResourceIsInsufficient() {
        var before = Map.of("法力", state("法力", 10), "精力", state("精力", 0));
        for (double value : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ResourceConsumption.evaluate(before, Map.of("法力", value, "精力", 10.0)));
        }
        assertThrows(IllegalArgumentException.class, () -> ResourceConsumption.evaluate(before, Map.of("不存在", 0.0)));
        var nullCost = new LinkedHashMap<String, Double>();
        nullCost.put("法力", null);
        assertThrows(IllegalArgumentException.class, () -> ResourceConsumption.evaluate(before, nullCost));
        assertEquals(10, before.get("法力").current());
    }

    @Test void emptyAndZeroCostsAreValidNoOps() {
        var before = Map.of("法力", state("法力", 0));
        for (var costs : java.util.List.of(Map.<String, Double>of(), Map.of("法力", 0.0))) {
            var result = ResourceConsumption.evaluate(before, costs);
            assertTrue(result.accepted());
            assertEquals(before, result.after());
        }
    }

    @Test void largeRequestsDoNotOverflowAndNoEpsilonGivesAwayResource() {
        var before = Map.of("法力", state("法力", 10));
        var huge = ResourceConsumption.evaluate(before, Map.of("法力", Double.MAX_VALUE));
        assertFalse(huge.accepted());
        assertTrue(Double.isFinite(huge.shortages().get("法力")));
        assertFalse(ResourceConsumption.evaluate(before, Map.of("法力", Math.nextUp(10.0))).accepted());
        assertThrows(IllegalArgumentException.class,
                () -> ResourceConsumption.evaluate(before, Map.of("法力", Double.MIN_VALUE)));
    }

    @Test void resultsAreDetachedAndImmutable() {
        var before = new LinkedHashMap<>(Map.of("法力", state("法力", 10)));
        var costs = new LinkedHashMap<>(Map.of("法力", 2.0));
        var result = ResourceConsumption.evaluate(before, costs);
        before.clear();
        costs.clear();
        assertEquals(10, result.before().get("法力").current());
        assertEquals(2, result.costs().get("法力"));
        assertThrows(UnsupportedOperationException.class, () -> result.after().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.before().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.costs().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.shortages().clear());
    }
}
