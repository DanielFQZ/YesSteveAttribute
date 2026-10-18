package com.yessteveattribute.core.config;

import java.util.Objects;

/** One named expression from formulas.yml. */
public final class FormulaDefinition {
    private final String id;
    private final String section;
    private final String expression;

    public FormulaDefinition(String id, String section, String expression) {
        this.id = Objects.requireNonNull(id, "id");
        this.section = Objects.requireNonNull(section, "section");
        this.expression = Objects.requireNonNull(expression, "expression");
    }

    public String getId() { return id; }
    public String getSection() { return section; }
    public String getExpression() { return expression; }
}
