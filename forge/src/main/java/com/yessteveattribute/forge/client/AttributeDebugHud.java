package com.yessteveattribute.forge.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.yessteveattribute.forge.YesSteveAttribute;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Local read-only debug panel; no scoreboard objectives or client-to-server writes. */
@Mod.EventBusSubscriber(modid = YesSteveAttribute.MOD_ID, value = Dist.CLIENT)
public final class AttributeDebugHud {
    private static boolean enabled;
    private static int page;
    private static float scale = .75F;
    private static Map<String, String> previous = Map.of();
    private static List<Map.Entry<String, String>> entries = List.of();
    private static final Map<String, Long> changed = new HashMap<>();

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ysadebug")
                .executes(context -> toggle(!enabled))
                .then(Commands.literal("on").executes(context -> toggle(true)))
                .then(Commands.literal("off").executes(context -> toggle(false)))
                .then(Commands.literal("next").executes(context -> { page++; return toggle(true); }))
                .then(Commands.literal("prev").executes(context -> { page--; return toggle(true); }))
                .then(Commands.literal("page").then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> { page = IntegerArgumentType.getInteger(context, "page") - 1; return toggle(true); })))
                .then(Commands.literal("scale").then(Commands.argument("scale", DoubleArgumentType.doubleArg(.5, 1.5))
                        .executes(context -> { scale = (float) DoubleArgumentType.getDouble(context, "scale"); return toggle(true); }))));
    }

    private static int toggle(boolean state) {
        enabled = state;
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(state
                ? "YSA 实时属性调试已开启；/ysadebug off 关闭，next/prev 翻页，scale 0.75 调整大小"
                : "YSA 实时属性调试已关闭"), false);
        return 1;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
        page = 0;
        previous = Map.of();
        entries = List.of();
        changed.clear();
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.level == null || mc.options.hideGui) return;
        var display = ClientAttributes.display(mc.player.getUUID());
        long now = Util.getMillis();
        if (display != previous) {
            for (var entry : display.entrySet()) {
                if (previous.containsKey(entry.getKey()) && !Objects.equals(previous.get(entry.getKey()), entry.getValue()))
                    changed.put(entry.getKey(), now);
            }
            changed.keySet().retainAll(display.keySet());
            entries = new ArrayList<>(display.entrySet());
            previous = display;
        }
        int preferred = 160;
        for (var entry : entries) preferred = Math.max(preferred, mc.font.width(entry.getKey()) + mc.font.width(entry.getValue()) + 22);
        var layout = DebugHudLayout.calculate((int) (event.getWindow().getGuiScaledWidth() / scale),
                (int) (event.getWindow().getGuiScaledHeight() / scale), entries.size(), preferred, page);
        page = layout.page();
        var graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(scale, scale, 1);
            graphics.pose().translate(0, 0, 300);
            int x = layout.x(), y = layout.y();
            graphics.fill(x, y, x + layout.width(), y + layout.height(), 0xB8101720);
            graphics.fill(x, y, x + layout.width(), y + 14, 0xE0284055);
            String title = "YSA 属性 " + entries.size() + " 项 · " + (layout.page() + 1) + "/" + layout.pages();
            graphics.drawString(mc.font, mc.font.plainSubstrByWidth(title, layout.width() - 8), x + 4, y + 3, 0xFFFFFF, false);
            for (int i = layout.start(); i < layout.end(); i++) {
                var entry = entries.get(i);
                int local = i - layout.start();
                int left = x + (local / layout.rows()) * layout.columnWidth();
                int top = y + 17 + (local % layout.rows()) * 11;
                if (local % 2 == 0) graphics.fill(left + 2, top - 1, left + layout.columnWidth() - 2, top + 10, 0x302E4054);
                String value = mc.font.plainSubstrByWidth(entry.getValue(), Math.max(20, layout.columnWidth() / 2));
                int valueWidth = mc.font.width(value);
                String name = mc.font.plainSubstrByWidth(entry.getKey(), Math.max(0, layout.columnWidth() - valueWidth - 16));
                int color = now - changed.getOrDefault(entry.getKey(), Long.MIN_VALUE / 2) < 1500 ? 0x80FFAA
                        : entry.getValue().equals("待战斗") ? 0xE2BA6A : 0x8DD8F8;
                graphics.drawString(mc.font, name, left + 5, top, 0xE3E8EE, false);
                graphics.drawString(mc.font, value, left + layout.columnWidth() - valueWidth - 5, top, color, false);
            }
            String footer = entries.isEmpty() ? "等待服务端属性同步…"
                    : layout.pages() > 1 ? "/ysadebug next | prev" : "实时同步 · /ysadebug off";
            graphics.drawString(mc.font, mc.font.plainSubstrByWidth(footer, layout.width() - 8), x + 4,
                    y + layout.height() - 10, 0xA9BAC9, false);
        } finally { graphics.pose().popPose(); }
    }
}
