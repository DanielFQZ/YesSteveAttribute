package com.yessteveattribute.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlConfigLoaderTest {
    @TempDir
    Path temp;

    @Test
    void loadsUtf8TemplateAndFlattensGroups() throws IOException {
        write("attributes.yml", """
                attributes:
                  primary:
                    力量:
                      type: number
                      unit: 点
                      default: 0
                      visible: true
                  runtime:
                    精力值: {type: resource_current, unit: 点, default: 500, visible: true}
                    精力上限: {type: resource_max, unit: 点, default: 500, visible: true}
                    精力恢复速度: {type: resource_rate, unit: 点/秒, default: 40, visible: true}
                resources:
                  精力:
                    current: 精力值
                    maximum: 精力上限
                    recovery: 精力恢复速度
                    initial: maximum
                """);
        write("formulas.yml", """
                context:
                  <lvl>: 当前等级
                resources:
                  力量: "<lvl> + 1"
                """);
        write("roles.yml", """
                roles:
                  测试:
                    display_name: 测试角色
                    level_rule: 默认
                    upgrade_conditions: []
                    base:
                      力量: 1
                    growth:
                      力量: "1 + <lvl>"
                """);
        write("levels.yml", """
                level_rules:
                  默认:
                    start: 1
                    max: 10
                    initial_exp: 0
                    experience:
                      segments:
                        - when: "<lvl> <= 10"
                          formula: "100 + <prev>"
                    promotion: []
                    overflow_exp: keep
                """);

        LoadedConfiguration config = new YamlConfigLoader().load(temp);
        assertEquals("点", config.getAttributes().get("力量").getUnit());
        assertEquals("测试角色", config.getRoles().get("测试").getDisplayName());
        assertEquals("100 + <prev>", config.getLevels().get("默认").getExperience().get(0).getFormula());
    }

    @Test
    void reportsUnknownFormulaReference() throws IOException {
        write("attributes.yml", """
                attributes:
                  primary:
                    力量: {type: number, unit: 点, default: 0, visible: true}
                """);
        write("formulas.yml", """
                damage:
                  结果: "<不存在> + 1"
                """);
        write("roles.yml", "roles: {}\n");
        write("levels.yml", "level_rules: {}\n");
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new YamlConfigLoader().load(temp));
        assertEquals(true, exception.getDiagnostics().stream()
                .anyMatch(item -> item.contains("不存在")));
    }

    @Test
    void reportsInvalidFormulaSyntaxDuringLoad() throws IOException {
        write("attributes.yml", "attributes:\n  primary:\n    力量: {type: number, unit: 点, default: 0, visible: true}\n");
        write("formulas.yml", "damage:\n  结果: \"<力量> +\"\n");
        write("roles.yml", "roles: {}\n");
        write("levels.yml", "level_rules: {}\n");
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new YamlConfigLoader().load(temp));
        assertEquals(true, exception.getDiagnostics().stream()
                .anyMatch(item -> item.contains("公式语法错误")));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(temp.resolve(name), content, StandardCharsets.UTF_8);
    }
}
