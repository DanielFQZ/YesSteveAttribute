package com.yessteveattribute.forge.client;

import com.yessteveattribute.forge.AttributeNetwork;
import com.yessteveattribute.forge.YesSteveAttribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = YesSteveAttribute.MOD_ID, value = Dist.CLIENT)
public final class ClientAttributes {
    private static final Map<UUID, AttributeNetwork.SnapshotPacket> SNAPSHOTS = new ConcurrentHashMap<>();
    public static void accept(AttributeNetwork.SnapshotPacket packet) { SNAPSHOTS.put(packet.player(), packet); }
    public static double get(UUID player, String attribute) {
        var snapshot = SNAPSHOTS.get(player);
        return snapshot == null ? 0 : snapshot.values().getOrDefault(attribute, 0.0);
    }
    public static Map<String, String> display(UUID player) {
        var snapshot = SNAPSHOTS.get(player);
        return snapshot == null ? Map.of() : snapshot.display();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SNAPSHOTS.clear(); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) SNAPSHOTS.remove(event.getEntity().getUUID());
    }
}
