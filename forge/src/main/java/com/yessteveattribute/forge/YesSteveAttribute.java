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
        AttributeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(YesSteveAttribute::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("ysa")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(context -> {
                    ConfigService.ReloadReport report = ConfigService.reloadDetailed();
                    if (report.success()) {
                        PlayerAttributes.syncAll();
                        context.getSource().sendSuccess(() -> Component.literal(
                                "YSA 配置重载成功：" + report.directory()
                                        + "；属性 " + report.attributes()
                                        + "，资源 " + report.resources()
                                        + "，公式 " + report.formulas()
                                        + "，角色 " + report.roles()
                                        + "，等级规则 " + report.levels()
                                        + "；耗时 " + report.elapsedMillis() + " ms"), true);
                    } else {
                        context.getSource().sendFailure(Component.literal(
                                "YSA 配置重载失败，已保留上一次有效配置（耗时 "
                                        + report.elapsedMillis() + " ms）"));
                        for (String diagnostic : report.diagnostics()) {
                            context.getSource().sendFailure(Component.literal("原因：" + diagnostic));
                        }
                    }
                    return report.success() ? 1 : 0;
                }));
        AttributeCommands.register(root);
        event.getDispatcher().register(root);
    }
}
