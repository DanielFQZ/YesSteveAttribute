package com.yessteveattribute.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttributeNetworkTest {
    @Test void snapshotRoundTripPreservesNumbersTextPendingAndDisplayOrder() {
        var display = new LinkedHashMap<String, String>();
        display.put("力量", "10.6");
        display.put("元素亲和", "炽炎");
        display.put("最终伤害", "待战斗");
        var packet = new AttributeNetwork.SnapshotPacket(UUID.randomUUID(), Map.of("力量", 10.6), display);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            var decoded = AttributeNetwork.SnapshotPacket.decode(buffer);
            assertEquals(packet, decoded);
            assertEquals(List.copyOf(display.keySet()), List.copyOf(decoded.display().keySet()));
            assertThrows(UnsupportedOperationException.class, () -> decoded.display().clear());
        } finally { buffer.release(); }
    }

    @Test void rejectsInvalidNumericAndDisplayPayloads() {
        for (boolean duplicate : List.of(false, true)) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUUID(UUID.randomUUID());
                if (!duplicate) {
                    buffer.writeVarInt(1);
                    buffer.writeUtf("力量");
                    buffer.writeDouble(Double.NaN);
                } else {
                    buffer.writeVarInt(0);
                    buffer.writeVarInt(2);
                    buffer.writeUtf("力量"); buffer.writeUtf("1");
                    buffer.writeUtf("力量"); buffer.writeUtf("2");
                }
                assertThrows(IllegalArgumentException.class, () -> AttributeNetwork.SnapshotPacket.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
