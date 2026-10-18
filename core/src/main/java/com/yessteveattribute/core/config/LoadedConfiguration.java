package com.yessteveattribute.core.config;

/** Complete four-file configuration snapshot. It is immutable and safe to swap on reload. */
public final class LoadedConfiguration {
    private final AttributesConfiguration attributes;
    private final FormulasConfiguration formulas;
    private final RolesConfiguration roles;
    private final LevelsConfiguration levels;

    public LoadedConfiguration(AttributesConfiguration attributes,
                               FormulasConfiguration formulas,
                               RolesConfiguration roles,
                               LevelsConfiguration levels) {
        this.attributes = attributes;
        this.formulas = formulas;
        this.roles = roles;
        this.levels = levels;
    }

    public AttributesConfiguration getAttributes() { return attributes; }
    public FormulasConfiguration getFormulas() { return formulas; }
    public RolesConfiguration getRoles() { return roles; }
    public LevelsConfiguration getLevels() { return levels; }
}
