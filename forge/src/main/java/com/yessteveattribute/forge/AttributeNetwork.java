package com.yessteveattribute.forge;

import com.yessteveattribute.forge.client.ClientAttributes;
import com.yessteveattribute.core.runtime.AttributeDisplay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Only server-to-client snapshots; clients cannot submit attribute values. */
public final class AttributeNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(YesSteveAttribute.MOD_ID, "attributes"), () -> "2", "2"::equals, "2"::equals);

    public static void register() {
        CHANNEL.messageBuilder(SnapshotPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SnapshotPacket::encode).decoder(SnapshotPacket::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientAttributes.accept(packet)))
                .add();
    }

    public static void sync(ServerPlayer player, PlayerAttributes.Profile profile) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                packet(player, profile));
    }

    public static void sendTo(ServerPlayer viewer, ServerPlayer target, PlayerAttributes.Profile profile) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                packet(target, profile));
    }

    private static SnapshotPacket packet(ServerPlayer player, PlayerAttributes.Profile profile) {
        var display = new LinkedHashMap<String, String>();
        ConfigService.definitions().forEach((id, definition) -> {
            String value = profile.pending().contains(id) ? "待战斗"
                    : profile.snapshot().all().containsKey(id) ? AttributeDisplay.number(profile.snapshot().get(id))
                    : profile.text().getOrDefault(id, "未同步");
            display.put(id, value);
        });
        return new SnapshotPacket(player.getUUID(), profile.snapshot().all(), display);
    }

    public record SnapshotPacket(UUID player, Map<String, Double> values, Map<String, String> display) {
        public SnapshotPacket {
            values = Map.copyOf(values);
            display = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(display));
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(player);
            buffer.writeVarInt(values.size());
            values.forEach((key, value) -> { buffer.writeUtf(key); buffer.writeDouble(value); });
            buffer.writeVarInt(display.size());
            display.forEach((key, value) -> { buffer.writeUtf(key); buffer.writeUtf(value); });
        }
        public static SnapshotPacket decode(FriendlyByteBuf buffer) {
            UUID player = buffer.readUUID();
            int size = buffer.readVarInt();
            if (size < 0 || size > 16384) throw new IllegalArgumentException("属性同步数量无效: " + size);
            var values = new LinkedHashMap<String, Double>();
            for (int i = 0; i < size; i++) {
                String key = buffer.readUtf();
                double value = buffer.readDouble();
                if (!Double.isFinite(value) || values.putIfAbsent(key, value) != null)
                    throw new IllegalArgumentException("属性同步值无效: " + key);
            }
            int displaySize = buffer.readVarInt();
            if (displaySize < 0 || displaySize > 16384) throw new IllegalArgumentException("调试属性数量无效: " + displaySize);
            var display = new LinkedHashMap<String, String>();
            for (int i = 0; i < displaySize; i++) {
                String key = buffer.readUtf();
                if (display.putIfAbsent(key, buffer.readUtf()) != null) throw new IllegalArgumentException("调试属性重复: " + key);
            }
            return new SnapshotPacket(player, values, display);
        }
    }
}
