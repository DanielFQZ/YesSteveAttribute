package com.yessteveattribute.core.runtime;

import com.yessteveattribute.core.config.LoadedConfiguration;
import com.yessteveattribute.core.formula.FormulaEngine;
import com.yessteveattribute.core.formula.FormulaException;

import java.util.*;
import java.util.regex.Pattern;

/** Resolves player-only formulas; contextual combat outputs stay explicitly unavailable. */
public final class DerivedAttributeCalculator {
    private static final Pattern VARIABLE = Pattern.compile("<([^<>]+)>");
    private static final Pattern ELEMENT = Pattern.compile("^最高权重元素\\s*\\((.*)\\)$");

    public record Result(AttributeSnapshot snapshot, Map<String, String> text, Set<String> pending) {
        public Result { text = Map.copyOf(text); pending = Set.copyOf(pending); }
    }

    public Result calculate(LoadedConfiguration config, AttributeSnapshot base, int level,
                            Map<String, ResourceState> resources) {
        var overrides = new LinkedHashMap<String, Double>();
        resources.values().forEach(state -> overrides.put(state.definition().getCurrent(), state.current()));
        var resolver = new Resolver(config, base, level, overrides);
        var values = new LinkedHashMap<String, Double>();
        var sources = new LinkedHashMap<String, List<AttributeSnapshot.Contribution>>();
        var text = new LinkedHashMap<String, String>();
        var pending = new LinkedHashSet<String>();
        for (var definition : config.getAttributes().getAttributes().values()) {
            String id = definition.getId();
            if (resolver.contextual(id)) { pending.add(id); continue; }
            if (definition.getDefaultValue() instanceof Number) {
                values.put(id, resolver.resolve(id));
                sources.put(id, resolver.contributions(id));
            } else {
                text.put(id, resolver.text(id));
            }
        }
        resources.values().forEach(state -> sources.put(state.definition().getCurrent(), List.of(
                new AttributeSnapshot.Contribution(state.current(), new Provenance("resource", state.definition().getId(), "current")))));
        return new Result(new AttributeSnapshot(values, sources), text, pending);
    }

    public static List<String> dependencies(String expression) {
        var result = new ArrayList<String>();
        var matcher = VARIABLE.matcher(expression);
        while (matcher.find()) result.add(matcher.group(1).trim());
        return result;
    }

    /** One calculation's memoized graph. Always construct from raw base, never a derived snapshot. */
    public static final class Resolver {
        private final LoadedConfiguration config;
        private final AttributeSnapshot base;
        private final FormulaEngine engine;
        private final Map<String, Double> overrides;
        private final Map<String, Double> cache = new HashMap<>();
        private final Map<String, Boolean> contextual = new HashMap<>();
        private final Set<String> active = new LinkedHashSet<>();
        private final Set<String> checking = new LinkedHashSet<>();
        private final Set<String> currents = new HashSet<>();
        private final Map<String, List<AttributeSnapshot.Contribution>> sources = new HashMap<>();

        public Resolver(LoadedConfiguration config, AttributeSnapshot base, int level, Map<String, Double> overrides) {
            this.config = config;
            this.base = base;
            this.engine = new FormulaEngine(config.getFormulas());
            this.overrides = new HashMap<>(overrides);
            this.overrides.put("lvl", (double) level);
            config.getAttributes().getResources().values().forEach(r -> currents.add(r.getCurrent()));
        }

        public boolean contextual(String id) {
            if (id.startsWith("目标.") || id.equals("prev") || id.equals("本次是否暴击")) return true;
            var definition = config.getAttributes().get(id);
            if (definition != null && "combat_input".equals(definition.getType())) return true;
            if (contextual.containsKey(id)) return contextual.get(id);
            if (!checking.add(id)) throw new FormulaException("公式存在循环依赖: " + checking + " -> " + id);
            boolean result = false;
            try {
                var formula = config.getFormulas().get(id);
                if (formula != null) {
                    // Walk every branch, so dormant dependency cycles are also rejected.
                    for (String dependency : dependencies(formula.getExpression())) result |= contextual(dependency);
                }
                contextual.put(id, result);
                return result;
            } finally { checking.remove(id); }
        }

        public double resolve(String id) {
            if (overrides.containsKey(id)) return finite(id, overrides.get(id));
            if (cache.containsKey(id)) return cache.get(id);
            if (currents.contains(id)) throw new FormulaException("资源参数不能依赖 current: " + id);
            if (!active.add(id)) throw new FormulaException("公式存在循环依赖: " + active + " -> " + id);
            try {
                var formula = config.getFormulas().get(id);
                double value;
                if (formula != null) {
                    value = engine.evaluateExpression(formula.getExpression(), this::resolve);
                    var contributions = new ArrayList<AttributeSnapshot.Contribution>();
                    contributions.add(new AttributeSnapshot.Contribution(value,
                            new Provenance("formula", "formulas.yml", formula.getSection() + "." + id)));
                    for (var contribution : base.explain(id)) {
                        String type = contribution.provenance().type();
                        if (type.equals("default") || type.equals("role") || type.equals("growth")) continue;
                        value = finite(id, value + contribution.value());
                        contributions.add(contribution);
                    }
                    sources.put(id, List.copyOf(contributions));
                } else {
                    var definition = config.getAttributes().get(id);
                    if (id.startsWith("目标.") || id.equals("prev") || id.equals("本次是否暴击")
                            || (definition != null && "combat_input".equals(definition.getType())))
                        throw new FormulaException("缺少上下文变量: " + id);
                    if (!base.all().containsKey(id)) throw new FormulaException("无法读取数值属性: " + id);
                    value = base.get(id);
                    sources.put(id, base.explain(id));
                }
                cache.put(id, finite(id, value));
                return value;
            } finally { active.remove(id); }
        }

        public List<AttributeSnapshot.Contribution> contributions(String id) {
            return sources.getOrDefault(id, base.explain(id));
        }

        /** Resource parameters cannot depend on balances/events, including dormant branches. */
        public double resourceParameter(String id) {
            if (contextual(id)) throw new FormulaException("资源参数不能依赖战斗/经验上下文: " + id);
            checkNoCurrent(id, new HashSet<>());
            return resolve(id);
        }

        private void checkNoCurrent(String id, Set<String> visited) {
            if (!visited.add(id)) return;
            if (currents.contains(id)) throw new FormulaException("资源参数不能依赖 current: " + id);
            var formula = config.getFormulas().get(id);
            if (formula != null) dependencies(formula.getExpression()).forEach(dependency -> checkNoCurrent(dependency, visited));
        }

        public String text(String id) {
            var formula = config.getFormulas().get(id);
            if (formula == null) return String.valueOf(config.getAttributes().get(id).getDefaultValue());
            var matcher = ELEMENT.matcher(formula.getExpression().trim());
            if (!matcher.matches()) throw new FormulaException("不支持的文本公式: " + id);
            String winner = "无";
            double maximum = 0;
            for (String argument : matcher.group(1).split(",", -1)) {
                var variable = VARIABLE.matcher(argument.trim());
                if (!variable.matches()) throw new FormulaException("最高权重元素参数必须是属性占位符: " + id);
                String name = variable.group(1).trim();
                double weight = resolve(name);
                if (weight > maximum) { maximum = weight; winner = name; }
            }
            return winner;
        }

        private static double finite(String id, double value) {
            if (!Double.isFinite(value)) throw new FormulaException("属性不是有限数值: " + id);
            return value;
        }
    }
}
