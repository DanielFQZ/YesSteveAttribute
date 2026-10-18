package com.yessteveattribute.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads and validates the four UTF-8 YSA configuration files. */
public final class YamlConfigLoader {
    public LoadedConfiguration load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        return load(directory.resolve("attributes.yml"),
                directory.resolve("formulas.yml"),
                directory.resolve("roles.yml"),
                directory.resolve("levels.yml"));
    }

    public LoadedConfiguration load(Path attributesFile, Path formulasFile,
                                    Path rolesFile, Path levelsFile) throws IOException {
        Map<String, Object> attributes = readMap(attributesFile);
        Map<String, Object> formulas = readMap(formulasFile);
        Map<String, Object> roles = readMap(rolesFile);
        Map<String, Object> levels = readMap(levelsFile);

        AttributesConfiguration parsedAttributes = parseAttributes(attributes, attributesFile);
        FormulasConfiguration parsedFormulas = parseFormulas(formulas, formulasFile);
        RolesConfiguration parsedRoles = parseRoles(roles, rolesFile);
        LevelsConfiguration parsedLevels = parseLevels(levels, levelsFile);

        List<String> diagnostics = ConfigurationValidator.validate(parsedAttributes,
                parsedFormulas, parsedRoles, parsedLevels);
        if (!diagnostics.isEmpty()) {
            throw new ConfigurationException(diagnostics);
        }
        return new LoadedConfiguration(parsedAttributes, parsedFormulas,
                parsedRoles, parsedLevels);
    }

    private Map<String, Object> readMap(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new ConfigurationException("配置文件不存在: " + path);
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(options);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object root = yaml.load(reader);
            if (!(root instanceof Map)) {
                throw new ConfigurationException("配置根节点必须是对象: " + path);
            }
            return castMap(root, path.toString());
        } catch (YAMLException ex) {
            throw new ConfigurationException("解析 YAML 失败 (" + path + "): " + ex.getMessage());
        }
    }

    private AttributesConfiguration parseAttributes(Map<String, Object> root, Path file) {
        Map<String, Object> groups = map(root.get("attributes"), file, "attributes");
        Map<String, AttributeDefinition> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> groupEntry : groups.entrySet()) {
            String group = groupEntry.getKey();
            Map<String, Object> attrs;
            try {
                attrs = castMap(groupEntry.getValue(), file + " -> attributes." + group);
            } catch (ConfigurationException ex) {
                errors.addAll(ex.getDiagnostics());
                continue;
            }
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                String id = entry.getKey();
                if (result.containsKey(id)) {
                    errors.add(file + " -> 属性重复定义: " + id + " (分组 " + group + ")");
                    continue;
                }
                try {
                    Map<String, Object> raw = castMap(entry.getValue(), file + " -> " + id);
                    String type = requiredString(raw, "type", file, id);
                    String unit = optionalString(raw, "unit", "");
                    Object defaultValue = raw.get("default");
                    if (!raw.containsKey("default")) {
                        errors.add(file + " -> 属性缺少 default: " + id);
                    }
                    boolean visible = booleanValue(raw.get("visible"), true, file + " -> " + id);
                    result.put(id, new AttributeDefinition(id, group, type, unit,
                            defaultValue, visible));
                } catch (ConfigurationException ex) {
                    errors.addAll(ex.getDiagnostics());
                }
            }
        }

        Map<String, ResourceDefinition> resources = new LinkedHashMap<>();
        Object rawResources = root.get("resources");
        if (rawResources != null) {
            Map<String, Object> resourceMap;
            try {
                resourceMap = castMap(rawResources, file + " -> resources");
            } catch (ConfigurationException ex) {
                errors.addAll(ex.getDiagnostics());
                resourceMap = Collections.emptyMap();
            }
            for (Map.Entry<String, Object> entry : resourceMap.entrySet()) {
                try {
                    Map<String, Object> raw = castMap(entry.getValue(), file + " -> resources." + entry.getKey());
                    String current = requiredString(raw, "current", file, "resources." + entry.getKey());
                    String maximum = requiredString(raw, "maximum", file, "resources." + entry.getKey());
                    String recovery = optionalString(raw, "recovery", null);
                    String decay = optionalString(raw, "decay", null);
                    if ((recovery == null) == (decay == null)) {
                        errors.add(file + " -> resource 必须且只能配置 recovery 或 decay: " + entry.getKey());
                    }
                    if (!raw.containsKey("initial")) {
                        errors.add(file + " -> resource 缺少 initial: " + entry.getKey());
                    }
                    resources.put(entry.getKey(), new ResourceDefinition(entry.getKey(), current,
                            maximum, recovery, decay, raw.get("initial")));
                } catch (ConfigurationException ex) {
                    errors.addAll(ex.getDiagnostics());
                }
            }
        }
        if (!errors.isEmpty()) throw new ConfigurationException(errors);
        return new AttributesConfiguration(result, resources);
    }

    private FormulasConfiguration parseFormulas(Map<String, Object> root, Path file) {
        Map<String, FormulaDefinition> formulas = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        Map<String, String> context = new LinkedHashMap<>();
        for (Map.Entry<String, Object> section : root.entrySet()) {
            String sectionName = section.getKey();
            Map<String, Object> body;
            try {
                body = castMap(section.getValue(), file + " -> " + sectionName);
            } catch (ConfigurationException ex) {
                errors.addAll(ex.getDiagnostics());
                continue;
            }
            for (Map.Entry<String, Object> item : body.entrySet()) {
                String id = item.getKey();
                if ("context".equals(sectionName)) {
                    if (item.getValue() == null) {
                        errors.add(file + " -> context 说明不能为空: " + id);
                    } else {
                        context.put(id, String.valueOf(item.getValue()));
                    }
                    continue;
                }
                if (!(item.getValue() instanceof String) && !(item.getValue() instanceof Number)) {
                    errors.add(file + " -> 公式必须是字符串: " + sectionName + "." + id);
                    continue;
                }
                if (formulas.containsKey(id)) {
                    errors.add(file + " -> 公式重复定义: " + id + " (" + sectionName + ")");
                    continue;
                }
                formulas.put(id, new FormulaDefinition(id, sectionName,
                        String.valueOf(item.getValue())));
            }
        }
        if (!errors.isEmpty()) throw new ConfigurationException(errors);
        return new FormulasConfiguration(formulas, context);
    }

    private RolesConfiguration parseRoles(Map<String, Object> root, Path file) {
        Map<String, Object> roleMap = map(root.get("roles"), file, "roles");
        Map<String, RoleDefinition> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : roleMap.entrySet()) {
            try {
                Map<String, Object> raw = castMap(entry.getValue(), file + " -> roles." + entry.getKey());
                String displayName = optionalString(raw, "display_name", entry.getKey());
                String levelRule = requiredString(raw, "level_rule", file, "roles." + entry.getKey());
                List<Object> conditions = listValue(raw.get("upgrade_conditions"), Collections.emptyList(),
                        file + " -> roles." + entry.getKey() + ".upgrade_conditions");
                Map<String, Number> base = numberMap(raw.get("base"), file + " -> roles." + entry.getKey() + ".base");
                Map<String, String> growth = stringMap(raw.get("growth"), file + " -> roles." + entry.getKey() + ".growth");
                result.put(entry.getKey(), new RoleDefinition(entry.getKey(), displayName, levelRule,
                        conditions, base, growth));
            } catch (ConfigurationException ex) {
                errors.addAll(ex.getDiagnostics());
            }
        }
        if (!errors.isEmpty()) throw new ConfigurationException(errors);
        return new RolesConfiguration(result);
    }

    private LevelsConfiguration parseLevels(Map<String, Object> root, Path file) {
        Map<String, Object> ruleMap = map(root.get("level_rules"), file, "level_rules");
        Map<String, LevelRule> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ruleMap.entrySet()) {
            try {
                Map<String, Object> raw = castMap(entry.getValue(), file + " -> level_rules." + entry.getKey());
                int start = intValue(raw.get("start"), 1, file + " -> " + entry.getKey() + ".start");
                int max = intValue(raw.get("max"), start, file + " -> " + entry.getKey() + ".max");
                Number initialExp = numberValue(raw.get("initial_exp"), 0, file + " -> " + entry.getKey() + ".initial_exp");
                Map<String, Object> exp = map(raw.get("experience"), file, entry.getKey() + ".experience");
                List<Object> segmentList = listValue(exp.get("segments"), Collections.emptyList(), file + " -> " + entry.getKey() + ".experience.segments");
                List<LevelRule.Segment> segments = new ArrayList<>();
                for (Object object : segmentList) {
                    Map<String, Object> segment = castMap(object, file + " -> " + entry.getKey() + ".experience.segments");
                    segments.add(new LevelRule.Segment(requiredString(segment, "when", file, "segment"),
                            requiredString(segment, "formula", file, "segment")));
                }
                List<Object> promotion = listValue(raw.get("promotion"), Collections.emptyList(), file + " -> " + entry.getKey() + ".promotion");
                String overflow = optionalString(raw, "overflow_exp", "keep");
                result.put(entry.getKey(), new LevelRule(entry.getKey(), start, max, initialExp,
                        segments, promotion, overflow));
            } catch (ConfigurationException ex) {
                errors.addAll(ex.getDiagnostics());
            }
        }
        if (!errors.isEmpty()) throw new ConfigurationException(errors);
        return new LevelsConfiguration(result);
    }

    private static Map<String, Object> map(Object value, Path file, String key) {
        if (value == null) throw new ConfigurationException(file + " -> 缺少对象: " + key);
        return castMap(value, file + " -> " + key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value, String location) {
        if (!(value instanceof Map)) throw new ConfigurationException(location + " 必须是对象");
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> item : source.entrySet()) {
            if (!(item.getKey() instanceof String)) {
                throw new ConfigurationException(location + " 的键必须是字符串");
            }
            result.put((String) item.getKey(), item.getValue());
        }
        return result;
    }

    private static String requiredString(Map<String, Object> map, String key, Path file, String location) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new ConfigurationException(file + " -> " + location + " 缺少非空字符串字段 " + key);
        }
        return (String) value;
    }

    private static String optionalString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback, String location) {
        if (value == null) return fallback;
        if (!(value instanceof Boolean)) throw new ConfigurationException(location + " 的值必须是布尔值");
        return (Boolean) value;
    }

    private static int intValue(Object value, int fallback, String location) {
        if (value == null) return fallback;
        if (!(value instanceof Number)) throw new ConfigurationException(location + " 的值必须是整数");
        return ((Number) value).intValue();
    }

    private static Number numberValue(Object value, Number fallback, String location) {
        if (value == null) return fallback;
        if (!(value instanceof Number)) throw new ConfigurationException(location + " 的值必须是数字");
        return (Number) value;
    }

    private static List<Object> listValue(Object value, List<Object> fallback, String location) {
        if (value == null) return fallback;
        if (!(value instanceof List)) throw new ConfigurationException(location + " 必须是数组");
        return new ArrayList<>((List<?>) value);
    }

    private static Map<String, Number> numberMap(Object value, String location) {
        Map<String, Object> map = castMap(value, location);
        Map<String, Number> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Number)) {
                throw new ConfigurationException(location + " 的属性值必须是数字: " + entry.getKey());
            }
            result.put(entry.getKey(), (Number) entry.getValue());
        }
        return result;
    }

    private static Map<String, String> stringMap(Object value, String location) {
        Map<String, Object> map = castMap(value, location);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                throw new ConfigurationException(location + " 的公式必须是字符串: " + entry.getKey());
            }
            result.put(entry.getKey(), (String) entry.getValue());
        }
        return result;
    }
}
