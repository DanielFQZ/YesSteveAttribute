package com.yessteveattribute.forge;

import com.yessteveattribute.core.runtime.ResourceConsumption;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Server-only atomic debit API. Costs must come from the caller's trusted server configuration. */
public final class ResourceService {
    private ResourceService() { }

    /** Validates current equipment and post-debit formulas without changing the profile, NBT or sending a snapshot. */
    public static ResourceConsumption.Result preview(ServerPlayer player, Map<String, Double> costs) {
        validate(player);
        return PlayerAttributes.consumeResources(player, costs, true);
    }

    /** accepted=true means payment committed. Insufficient balances return false; invalid input/formulas throw before commit. */
    public static ResourceConsumption.Result tryConsume(ServerPlayer player, Map<String, Double> costs) {
        validate(player);
        return PlayerAttributes.consumeResources(player, costs, false);
    }

    public static ResourceConsumption.Result tryConsume(ServerPlayer player, String resource, double amount) {
        if (resource == null) throw new IllegalArgumentException("资源名不能为空");
        return tryConsume(player, Map.of(resource, amount));
    }

    private static void validate(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("玩家不能为空");
        if (player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("YSA 资源消耗只能在服务器线程调用");
        if (!player.isAlive() || player.isRemoved() || player.isSpectator())
            throw new IllegalArgumentException("消耗资源的玩家必须存活且不能是旁观者");
    }
}
