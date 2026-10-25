package com.yessteveattribute.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure, all-or-nothing resource debit. The caller owns persistence and derived-attribute validation. */
public final class ResourceConsumption {
    private ResourceConsumption() { }

    public record Result(boolean accepted, Map<String, Double> costs, Map<String, ResourceState> before,
                         Map<String, ResourceState> after, Map<String, Double> shortages) {
        public Result {
            costs = immutable(costs);
            before = immutable(before);
            after = immutable(after);
            shortages = immutable(shortages);
        }
    }

    public static Result evaluate(Map<String, ResourceState> balances, Map<String, Double> costs) {
        if (balances == null || costs == null) throw new IllegalArgumentException("资源余额和消耗不能为空");
        var before = immutable(balances);
        var requested = new LinkedHashMap<String, Double>();
        // Validate the entire request before checking affordability; invalid entries never become a partial debit.
        costs.forEach((id, amount) -> {
            if (id == null || id.isBlank() || !before.containsKey(id))
                throw new IllegalArgumentException("资源不存在: " + id);
            if (amount == null || !Double.isFinite(amount) || amount < 0)
                throw new IllegalArgumentException("资源 " + id + " 消耗必须是有限非负数");
            requested.put(id, amount);
        });
        var shortages = new LinkedHashMap<String, Double>();
        requested.forEach((id, amount) -> {
            double balance = before.get(id).current();
            if (balance < amount) shortages.put(id, amount - balance);
        });
        if (!shortages.isEmpty()) return new Result(false, requested, before, before, shortages);

        var after = new LinkedHashMap<>(before);
        requested.forEach((id, amount) -> {
            var state = before.get(id);
            double remaining = state.current() - amount;
            // Do not report successful payment when floating-point rounding would leave the balance unchanged.
            if (amount > 0 && remaining == state.current())
                throw new IllegalArgumentException("资源 " + id + " 消耗过小，无法在当前余额精度下扣除");
            after.put(id, state.set(remaining));
        });
        return new Result(true, requested, before, after, Map.of());
    }

    private static <T> Map<String, T> immutable(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
