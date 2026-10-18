package com.yessteveattribute.forge.client;

import com.elfmcys.ysm.api.annotation.Side;
import com.elfmcys.ysm.api.annotation.YsmExtension;
import com.elfmcys.ysm.client.animation.molang.CustomMolangParser;
import com.yessteveattribute.forge.YesSteveAttribute;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/** 将 YSA 的 Molang 顶级绑定注入 YSM。 */
public final class ClientMolangRegister {
    private static volatile boolean registered;

    private ClientMolangRegister() {
    }

    @SuppressWarnings("unchecked")
    public static void register() {
        if (registered) return;
        try {
            Field extraField = CustomMolangParser.class.getDeclaredField("EXTRA_BINDING");
            extraField.setAccessible(true);
            Map<String, Object> bindings = (Map<String, Object>) extraField.get(null);
            if (bindings.isEmpty()) {
                // 让 YSM 先填充其内置绑定，否则直接 put 会丢失 ysm/ctrl/tlm/args。
                CustomMolangParser.rentInstance();
            }
            bindings.put("ysa", new YsaMolangBinding());

            Field poolField = CustomMolangParser.class.getDeclaredField("PARSER_POOL");
            poolField.setAccessible(true);
            ((Collection<?>) poolField.get(null)).clear();
            registered = true;
            YesSteveAttribute.LOGGER.info("已注册 YSM Molang：ysa.attr、ysa.damage");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // YSM 内部结构变动时不影响其余 Forge 功能，只禁用这两个函数。
            YesSteveAttribute.LOGGER.error("注册 YSA Molang 绑定失败，ysa.* 暂不可用", exception);
        }
    }

    public static boolean isRegistered() {
        return registered;
    }
}
