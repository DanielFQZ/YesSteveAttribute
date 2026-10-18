package com.yessteveattribute.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed formulas.yml, retaining section names for diagnostics and tooling. */
public final class FormulasConfiguration {
    private final Map<String, FormulaDefinition> formulas;
    private final Map<String, String> context;

    public FormulasConfiguration(Map<String, FormulaDefinition> formulas,
                                 Map<String, String> context) {
        this.formulas = Collections.unmodifiableMap(new LinkedHashMap<>(formulas));
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public Map<String, FormulaDefinition> getFormulas() { return formulas; }
    public Map<String, String> getContext() { return context; }
    public FormulaDefinition get(String id) { return formulas.get(id); }
}
