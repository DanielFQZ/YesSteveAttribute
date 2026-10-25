package com.yessteveattribute.core.formula;

import com.yessteveattribute.core.config.FormulaDefinition;
import com.yessteveattribute.core.config.FormulasConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small deterministic evaluator for the numeric expression subset used by the
 * configuration templates. It deliberately has no script engine or file I/O.
 * Formulas may refer to other formulas by using the same short placeholder
 * syntax as the YAML files, for example {@code <基础伤害>}.
 */
public final class FormulaEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile("<([^<>]+)>");
    private final Map<String, FormulaDefinition> formulas;

    public FormulaEngine(FormulasConfiguration configuration) {
        this.formulas = Map.copyOf(configuration.getFormulas());
    }

    public double evaluate(String formulaId, EvaluationContext context) {
        return evaluate(formulaId, context, new HashSet<>());
    }

    public double evaluateExpression(String expression, EvaluationContext context) {
        return evaluateExpression(expression, context, new HashSet<>());
    }

    /** Validates expression syntax without resolving configured variables. */
    public static void validateExpressionSyntax(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new FormulaException("公式不能为空");
        }
        Matcher matcher = PLACEHOLDER.matcher(expression);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(rewritten, "1");
        }
        matcher.appendTail(rewritten);
        new Parser(rewritten.toString(), ignored -> null, true).parse();
    }

    private double evaluate(String formulaId, EvaluationContext context, Set<String> stack) {
        FormulaDefinition definition = formulas.get(formulaId);
        if (definition == null) {
            throw new FormulaException("未知公式或变量: " + formulaId);
        }
        if (!stack.add(formulaId)) {
            throw new FormulaException("公式存在循环依赖: " + String.join(" -> ", stack) + " -> " + formulaId);
        }
        try {
            return evaluateExpression(definition.getExpression(), context, stack);
        } finally {
            stack.remove(formulaId);
        }
    }

    private double evaluateExpression(String expression, EvaluationContext context, Set<String> stack) {
        return evaluateExpression(expression, name -> {
            Double value = context.get(name);
            if (value == null && formulas.containsKey(name)) return evaluate(name, context, stack);
            return value;
        });
    }

    /** Resolve variables only in evaluated branches, including recursive formula dependencies. */
    public double evaluateExpression(String expression, Function<String, Double> resolver) {
        Map<String, String> bindings = new HashMap<>();
        Matcher matcher = PLACEHOLDER.matcher(expression);
        StringBuffer rewritten = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String token = "__v" + index++;
            bindings.put(token, name);
            matcher.appendReplacement(rewritten, token);
        }
        matcher.appendTail(rewritten);
        double result = new Parser(rewritten.toString(), token -> {
            String name = bindings.get(token);
            return name == null ? null : resolver.apply(name);
        }, false).parse();
        if (!Double.isFinite(result)) {
            throw new FormulaException("公式结果不是有限数字: " + expression);
        }
        return result;
    }

    private static final class Parser {
        private final String source;
        private final Function<String, Double> variables;
        private final boolean syntaxOnly;
        private boolean evaluating;
        private int position;

        private Parser(String source, Function<String, Double> variables, boolean syntaxOnly) {
            this.source = source;
            this.variables = variables;
            this.syntaxOnly = syntaxOnly;
            this.evaluating = !syntaxOnly;
        }

        double parse() {
            double value = conditional();
            skipWhitespace();
            if (position != source.length()) {
                throw error("无法识别的内容");
            }
            return value;
        }

        private double conditional() {
            double condition = or();
            skipWhitespace();
            if (take('?')) {
                boolean parent = evaluating;
                evaluating = parent && condition != 0;
                double whenTrue = conditional();
                expect(':');
                evaluating = parent && condition == 0;
                double whenFalse = conditional();
                evaluating = parent;
                return condition != 0.0 ? whenTrue : whenFalse;
            }
            return condition;
        }

        private double or() {
            double value = and();
            while (take("||")) {
                boolean parent = evaluating;
                evaluating = parent && value == 0;
                double right = and();
                evaluating = parent;
                value = (value != 0 || right != 0) ? 1 : 0;
            }
            return value;
        }

        private double and() {
            double value = equality();
            while (take("&&")) {
                boolean parent = evaluating;
                evaluating = parent && value != 0;
                double right = equality();
                evaluating = parent;
                value = (value != 0 && right != 0) ? 1 : 0;
            }
            return value;
        }

        private double equality() {
            double value = comparison();
            while (true) {
                if (take("==")) value = value == comparison() ? 1 : 0;
                else if (take("!=")) value = value != comparison() ? 1 : 0;
                else return value;
            }
        }

        private double comparison() {
            double value = additive();
            while (true) {
                if (take("<=")) value = value <= additive() ? 1 : 0;
                else if (take(">=")) value = value >= additive() ? 1 : 0;
                else if (take('<')) value = value < additive() ? 1 : 0;
                else if (take('>')) value = value > additive() ? 1 : 0;
                else return value;
            }
        }

        private double additive() {
            double value = multiplicative();
            while (true) {
                if (take('+')) value += multiplicative();
                else if (take('-')) value -= multiplicative();
                else return value;
            }
        }

        private double multiplicative() {
            double value = unary();
            while (true) {
                if (take('*')) value *= unary();
                else if (take('/')) {
                    double divisor = unary();
                    if (divisor == 0 && evaluating) throw error("除数不能为零");
                    value /= divisor;
                } else if (take('%')) {
                    double divisor = unary();
                    if (divisor == 0 && evaluating) throw error("取模除数不能为零");
                    value %= divisor;
                } else return value;
            }
        }

        private double unary() {
            if (take('+')) return unary();
            if (take('-')) return -unary();
            if (take('!')) return unary() == 0 ? 1 : 0;
            return primary();
        }

        private double primary() {
            skipWhitespace();
            if (take('(')) {
                double value = conditional();
                expect(')');
                return value;
            }
            if (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                return number();
            }
            String identifier = identifier();
            if (identifier.isEmpty()) throw error("需要数字、变量或函数");
            skipWhitespace();
            if (take('(')) {
                List<Double> args = new ArrayList<>();
                skipWhitespace();
                if (!take(')')) {
                    do args.add(conditional()); while (take(','));
                    expect(')');
                }
                return function(identifier, args);
            }
            if (!evaluating && !syntaxOnly) return 0;
            Double value = variables.apply(identifier);
            if (value == null) throw error("未知变量: " + identifier);
            if (!Double.isFinite(value)) throw error("变量不是有限数值: " + identifier);
            return value;
        }

        private double function(String name, List<Double> args) {
            return switch (name) {
                case "Math.pow", "pow" -> {
                    requireCount(name, args, 2);
                    yield Math.pow(args.get(0), args.get(1));
                }
                case "Math.min", "min" -> {
                    requireAtLeast(name, args, 1);
                    yield args.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                }
                case "Math.max", "max" -> {
                    requireAtLeast(name, args, 1);
                    yield args.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                }
                case "Math.floor", "floor" -> {
                    requireCount(name, args, 1);
                    yield Math.floor(args.get(0));
                }
                case "Math.ceil", "ceil" -> {
                    requireCount(name, args, 1);
                    yield Math.ceil(args.get(0));
                }
                case "Math.abs", "abs" -> {
                    requireCount(name, args, 1);
                    yield Math.abs(args.get(0));
                }
                case "最高权重元素" -> {
                    requireAtLeast(name, args, 1);
                    if (evaluating) {
                        throw error("文本函数 最高权重元素 不能作为数值公式求值");
                    }
                    yield 0;
                }
                default -> throw error("不支持的函数: " + name);
            };
        }

        private void requireCount(String name, List<Double> args, int count) {
            if (args.size() != count) throw error(name + " 参数数量必须是 " + count);
        }

        private void requireAtLeast(String name, List<Double> args, int count) {
            if (args.size() < count) throw error(name + " 至少需要 " + count + " 个参数");
        }

        private double number() {
            int start = position;
            while (position < source.length() && (Character.isDigit(source.charAt(position)) || ".eE+-".indexOf(source.charAt(position)) >= 0)) {
                if ((source.charAt(position) == '+' || source.charAt(position) == '-') && position > start
                        && source.charAt(position - 1) != 'e' && source.charAt(position - 1) != 'E') break;
                position++;
            }
            try { return Double.parseDouble(source.substring(start, position)); }
            catch (NumberFormatException ex) { throw error("无效数字"); }
        }

        private String identifier() {
            skipWhitespace();
            int start = position;
            while (position < source.length()) {
                char c = source.charAt(position);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.') position++;
                else break;
            }
            return source.substring(start, position);
        }

        private boolean take(char c) {
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == c) { position++; return true; }
            return false;
        }

        private boolean take(String text) {
            skipWhitespace();
            if (source.startsWith(text, position)) { position += text.length(); return true; }
            return false;
        }

        private void expect(char c) { if (!take(c)) throw error("缺少 '" + c + "'"); }
        private void skipWhitespace() { while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++; }
        private FormulaException error(String message) { return new FormulaException(message + "，表达式: " + source + "，位置: " + position); }
    }
}
