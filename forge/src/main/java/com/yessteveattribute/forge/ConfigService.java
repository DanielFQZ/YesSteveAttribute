package com.yessteveattribute.forge;

import com.yessteveattribute.core.config.AttributeDefinition;
import com.yessteveattribute.core.config.LoadedConfiguration;
import com.yessteveattribute.core.config.YamlConfigLoader;
import com.yessteveattribute.core.formula.FormulaEngine;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the active immutable configuration snapshot on the Forge side. */
public final class ConfigService {
    private static final String CONFIG_DIRECTORY = "yessteveattribute";
    private static final String[] FILES = {"attributes.yml", "formulas.yml", "roles.yml", "levels.yml"};
    private static final AtomicReference<State> STATE = new AtomicReference<>();
    private static volatile Path directory;

    private ConfigService() {
    }

    public static synchronized boolean reload() {
        return reload(FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIRECTORY));
    }

    public static synchronized boolean reload(Path configDirectory) {
        try {
            ensureDefaults(configDirectory);
            LoadedConfiguration loaded = new YamlConfigLoader().load(configDirectory);
            // Only publish after all four files and all cross-file checks pass.
            STATE.set(new State(loaded, new FormulaEngine(loaded.getFormulas())));
            directory = configDirectory;
            YesSteveAttribute.LOGGER.info("已加载 YSA 配置：{} 个属性、{} 个公式、{} 个角色、{} 条等级规则",
                    loaded.getAttributes().getAttributes().size(),
                    loaded.getFormulas().getFormulas().size(),
                    loaded.getRoles().getRoles().size(),
                    loaded.getLevels().getRules().size());
            return true;
        } catch (IOException | RuntimeException exception) {
            YesSteveAttribute.LOGGER.error("YSA 配置加载失败；保留上一次有效配置（目录：{}）", configDirectory, exception);
            return false;
        }
    }

    private static void ensureDefaults(Path configDirectory) throws IOException {
        Files.createDirectories(configDirectory);
        for (String file : FILES) {
            Path target = configDirectory.resolve(file);
            if (Files.exists(target)) continue;
            String resource = "/defaults/" + file;
            try (InputStream input = ConfigService.class.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("缺少内置模板资源: " + resource);
                Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    public static LoadedConfiguration current() {
        State state = STATE.get();
        return state == null ? null : state.configuration();
    }

    public static FormulaEngine formulas() {
        State state = STATE.get();
        return state == null ? null : state.formulas();
    }

    public static Path directory() {
        return directory;
    }

    /** Returns a configured default value, useful for client previews before a role is assigned. */
    public static double defaultValue(String id) {
        LoadedConfiguration loaded = current();
        if (loaded == null) return 0.0;
        AttributeDefinition definition = loaded.getAttributes().get(id);
        if (definition == null || !(definition.getDefaultValue() instanceof Number number)) return 0.0;
        return number.doubleValue();
    }

    public static Map<String, AttributeDefinition> definitions() {
        LoadedConfiguration loaded = current();
        return loaded == null ? Map.of() : loaded.getAttributes().getAttributes();
    }

    private record State(LoadedConfiguration configuration, FormulaEngine formulas) {
    }
}
