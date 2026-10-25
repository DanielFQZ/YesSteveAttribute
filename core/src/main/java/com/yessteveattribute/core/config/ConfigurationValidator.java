package com.yessteveattribute.core.config;

import com.yessteveattribute.core.formula.FormulaEngine;
import com.yessteveattribute.core.formula.FormulaException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cross-file checks that are independent of Minecraft and a formula runtime. */
public final class ConfigurationValidator {
    private static final Pattern PLACEHOLDER = Pattern.compile("<([^<>]+)>");
    private static final Set<String> BUILT_IN_CONTEXT = new HashSet<>();

    static {
        BUILT_IN_CONTEXT.add("lvl");
        BUILT_IN_CONTEXT.add("prev");
        BUILT_IN_CONTEXT.add("本次是否暴击");
    }

    private ConfigurationValidator() {
    }

    public static List<String> validate(AttributesConfiguration attributes,
                                        FormulasConfiguration formulas,
                                        RolesConfiguration roles,
                                        LevelsConfiguration levels) {
        List<String> errors = new ArrayList<>();
        Set<String> attributeIds = attributes.getAttributes().keySet();
        Set<String> currentIds = new HashSet<>();
        Set<String> parameterIds = new HashSet<>();
        for (ResourceDefinition resource : attributes.getResources().values()) {
            if (!currentIds.add(resource.getCurrent())) errors.add("资源 current 绑定重复: " + resource.getCurrent());
            if (formulas.get(resource.getCurrent()) != null) errors.add("资源 current 不能同时定义公式: " + resource.getCurrent());
            parameterIds.add(resource.getMaximum());
            if (resource.getRecovery() != null) parameterIds.add(resource.getRecovery());
            if (resource.getDecay() != null) parameterIds.add(resource.getDecay());
            Object initial = resource.getInitial();
            if (!(initial instanceof String) && !(initial instanceof Number))
                errors.add("资源 initial 必须是数值、maximum 或属性 ID: " + resource.getId());
            if (initial instanceof Number n && !Double.isFinite(n.doubleValue()))
                errors.add("资源 initial 必须是有限数字: " + resource.getId());
        }
        for (String id : currentIds) if (parameterIds.contains(id)) errors.add("资源 current 不能同时作为上限或速率: " + id);

        for (ResourceDefinition resource : attributes.getResources().values()) {
            checkAttributeReference(resource.getCurrent(), "resource " + resource.getId() + ".current",
                    attributeIds, formulas, errors);
            checkAttributeReference(resource.getMaximum(), "resource " + resource.getId() + ".maximum",
                    attributeIds, formulas, errors);
            if (resource.getRecovery() != null) {
                checkAttributeReference(resource.getRecovery(), "resource " + resource.getId() + ".recovery",
                        attributeIds, formulas, errors);
            }
            if (resource.getDecay() != null) {
                checkAttributeReference(resource.getDecay(), "resource " + resource.getId() + ".decay",
                        attributeIds, formulas, errors);
            }
            if (resource.getInitial() instanceof String
                    && !"maximum".equals(resource.getInitial())) {
                checkAttributeReference((String) resource.getInitial(), "resource " + resource.getId() + ".initial",
                        attributeIds, formulas, errors);
            }
        }

        for (FormulaDefinition formula : formulas.getFormulas().values()) {
            if (!attributeIds.contains(formula.getId())) {
                errors.add("公式输出未在 attributes.yml 定义: " + formula.getId()
                        + " (" + formula.getSection() + ")");
            }
            checkExpression(formula.getExpression(), formula.getSection() + "." + formula.getId(),
                    attributeIds, formulas, errors);
        }

        for (RoleDefinition role : roles.getRoles().values()) {
            if (!levels.getRules().containsKey(role.getLevelRule())) {
                errors.add("角色引用了不存在的等级规则: " + role.getId() + " -> " + role.getLevelRule());
            }
            for (String id : role.getBase().keySet()) {
                if (!attributeIds.contains(id)) {
                    errors.add("角色基础属性未定义: " + role.getId() + ".base." + id);
                }
            }
            for (Map.Entry<String, String> growth : role.getGrowth().entrySet()) {
                if (!attributeIds.contains(growth.getKey())) {
                    errors.add("角色成长属性未定义: " + role.getId() + ".growth." + growth.getKey());
                }
                checkExpression(growth.getValue(), role.getId() + ".growth." + growth.getKey(),
                        attributeIds, formulas, errors);
            }
        }

        for (LevelRule rule : levels.getRules().values()) {
            if (rule.getStart() < 0 || rule.getMax() < rule.getStart()) {
                errors.add("等级范围无效: " + rule.getId() + " (start=" + rule.getStart()
                        + ", max=" + rule.getMax() + ")");
            }
            for (LevelRule.Segment segment : rule.getExperience()) {
                checkExpression(segment.getWhen(), rule.getId() + ".experience.when", attributeIds, formulas, errors);
                checkExpression(segment.getFormula(), rule.getId() + ".experience.formula", attributeIds, formulas, errors);
            }
            for (Object promotion : rule.getPromotion()) {
                if (promotion instanceof Map) {
                    Object when = ((Map<?, ?>) promotion).get("when");
                    if (when instanceof String) {
                        checkExpression((String) when, rule.getId() + ".promotion.when", attributeIds, formulas, errors);
                    }
                }
            }
        }
        return errors;
    }

    private static void checkAttributeReference(String id, String location,
                                                Set<String> attributes,
                                                FormulasConfiguration formulas,
                                                List<String> errors) {
        if (id == null || id.isEmpty()) return;
        if (!attributes.contains(id) && !formulas.getFormulas().containsKey(id)) {
            errors.add("引用了未定义属性: " + location + " -> " + id);
        }
    }

    private static void checkExpression(String expression, String location,
                                        Set<String> attributes,
                                        FormulasConfiguration formulas,
                                        List<String> errors) {
        if (expression == null) return;
        Matcher matcher = PLACEHOLDER.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            if (BUILT_IN_CONTEXT.contains(token)) continue;
            if (token.startsWith("目标.")) {
                String targetId = token.substring("目标.".length());
                if ("属性名".equals(targetId)) continue;
                if (!attributes.contains(targetId) && !formulas.getFormulas().containsKey(targetId)) {
                    errors.add("引用了未定义目标属性: " + location + " -> " + targetId);
                }
                continue;
            }
            if (!attributes.contains(token) && !formulas.getFormulas().containsKey(token)) {
                errors.add("引用了未定义变量: " + location + " -> <" + token + ">");
            }
        }
        try {
            FormulaEngine.validateExpressionSyntax(expression);
        } catch (FormulaException exception) {
            errors.add("公式语法错误: " + location + " -> " + exception.getMessage());
        }
    }
}
