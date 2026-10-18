package com.yessteveattribute.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yes Steve Attribute 的 Forge 入口。
 *
 * <p>入口只依赖 Forge 的公共类；YSM/Molang 代码放在客户端事件订阅器中，
 * 这样专用服务器没有 YSM 时也能加载核心配置与 API。</p>
 */
@Mod(YesSteveAttribute.MOD_ID)
public final class YesSteveAttribute {
    public static final String MOD_ID = "yessteveattribute";
    public static final Logger LOGGER = LoggerFactory.getLogger("YesSteveAttribute");

    public YesSteveAttribute() {
        LOGGER.info("Yes Steve Attribute Forge 模块已加载（{}）", MOD_ID);
        ConfigService.reload();
        MinecraftForge.EVENT_BUS.addListener(YesSteveAttribute::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ysa")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(context -> {
                    boolean loaded = ConfigService.reload();
                    context.getSource().sendSuccess(() -> Component.literal(
                            loaded ? "YSA 配置已重新加载" : "YSA 配置加载失败，请查看日志"), true);
                    return loaded ? 1 : 0;
                })));
    }
}
