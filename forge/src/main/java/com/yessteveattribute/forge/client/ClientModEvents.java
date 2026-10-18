package com.yessteveattribute.forge.client;

import com.yessteveattribute.forge.YesSteveAttribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.ModList;

/** 客户端专用生命周期入口。服务端不会加载本类。 */
@Mod.EventBusSubscriber(modid = YesSteveAttribute.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("ysm")) {
            YesSteveAttribute.LOGGER.info("未检测到 YSM，跳过 ysa.* Molang 注册");
            return;
        }
        // YSM 的绑定表需要在首个 Molang parser 创建前注入。
        event.enqueueWork(ClientMolangRegister::register);
    }
}
