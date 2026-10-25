package com.yessteveattribute.forge;

import com.yessteveattribute.core.config.LoadedConfiguration;
import com.yessteveattribute.core.runtime.AttributeSnapshot;
import com.yessteveattribute.core.runtime.PlayerAttributeCalculator;
import com.yessteveattribute.core.runtime.ResourceCalculator;
import com.yessteveattribute.core.runtime.ResourceState;
import com.yessteveattribute.core.runtime.ResourceConsumption;
import com.yessteveattribute.core.runtime.DerivedAttributeCalculator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;

/** Server-thread-owned snapshots. Role, level and resource balances are persisted. */
@Mod.EventBusSubscriber(modid = YesSteveAttribute.MOD_ID)
public final class PlayerAttributes {
    private static final String KEY = "yessteveattribute";
    private static final Map<UUID, Profile> PROFILES = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> EQUIPMENT_CHECKPOINTS = new HashMap<>();
    private static final Set<UUID> RESOURCE_ERRORS = new java.util.HashSet<>();

    public record Profile(String role, int level, AttributeSnapshot snapshot, Map<String, ResourceState> resources,
                          List<ItemStack> equipment, AttributeSnapshot base, Map<String, String> text, Set<String> pending) {
        public Profile {
            resources = Map.copyOf(resources);
            equipment = equipment.stream().map(ItemStack::copy).toList();
            text = Map.copyOf(text);
            pending = Set.copyOf(pending);
        }
    }

    private static Profile calculate(ServerPlayer player, LoadedConfiguration config) {
        var tag = player.getPersistentData().getCompound(KEY);
        var equipment = new EquipmentAttributeReader().read(player, numericAttributes(config));
        warnEquipment(player, equipment.result());
        return calculate(config, tag.getString("role"), tag.getInt("level"), savedResources(player), equipment);
    }

    private static Map<String, Double> savedResources(ServerPlayer player) {
        var saved = new HashMap<String, Double>();
        var resources = player.getPersistentData().getCompound(KEY).getCompound("resources");
        for (String id : resources.getAllKeys()) {
            if (!resources.contains(id, Tag.TAG_ANY_NUMERIC)) throw new IllegalArgumentException("资源存档不是数值: " + id);
            saved.put(id, resources.getDouble(id));
        }
        return saved;
    }

    private static Profile calculate(LoadedConfiguration config, String role, int level, Map<String, Double> saved,
                                     EquipmentAttributeReader.EquipmentView equipment) {
        if (config == null) throw new IllegalStateException("尚无有效配置，请修复后 /ysa reload");
        var base = new PlayerAttributeCalculator().calculate(config, role, level, equipment.result().snapshot());
        var resources = new ResourceCalculator().calculate(config, base, level, saved);
        var resolved = new DerivedAttributeCalculator().calculate(config, base, level, resources);
        return new Profile(role, level, resolved.snapshot(), resources, equipment.stacks(), base, resolved.text(), resolved.pending());
    }

    static Set<String> numericAttributes(LoadedConfiguration config) {
        var result = new java.util.LinkedHashSet<String>();
        if (config != null) config.getAttributes().getAttributes().values().forEach(definition -> {
            if (definition.getDefaultValue() instanceof Number) result.add(definition.getId());
        });
        // Formula outputs accept additive modifiers; balances and combat events own their inputs.
        if (config != null) {
            config.getAttributes().getResources().values().forEach(definition -> result.remove(definition.getCurrent()));
            config.getAttributes().getAttributes().values().stream().filter(d -> "combat_input".equals(d.getType()))
                    .forEach(d -> result.remove(d.getId()));
        }
        return result;
    }

    private static void warnEquipment(ServerPlayer player, EquipmentAttributeReader.Result result) {
        for (String diagnostic : result.diagnostics()) {
            player.sendSystemMessage(Component.literal("YSA 装备属性已忽略：" + diagnostic));
            YesSteveAttribute.LOGGER.warn("忽略玩家 {} 的装备属性：{}", player.getGameProfile().getName(), diagnostic);
        }
    }

    public static Profile get(ServerPlayer player) {
        return PROFILES.computeIfAbsent(player.getUUID(), ignored -> {
            var profile = calculate(player, ConfigService.current());
            save(player, profile);
            EQUIPMENT_CHECKPOINTS.put(player.getUUID(), profile.equipment());
            return profile;
        });
    }

    public static Profile assign(ServerPlayer player, String role, int level) {
        var equipment = new EquipmentAttributeReader().read(player, numericAttributes(ConfigService.current()));
        warnEquipment(player, equipment.result());
        Profile next = calculate(ConfigService.current(), role, level, savedResources(player), equipment);
        update(player, next);
        EQUIPMENT_CHECKPOINTS.put(player.getUUID(), next.equipment());
        return next;
    }

    /** Combat may happen before the next equipment polling tick; never use stale equipped values. */
    public static Profile forCombat(ServerPlayer player) {
        var profile = get(player);
        var stacks = EquipmentAttributeReader.capture(player);
        if (EquipmentAttributeReader.sameItems(profile.equipment(), stacks)) return profile;
        var equipment = new EquipmentAttributeReader().read(stacks, numericAttributes(ConfigService.current()));
        if (!EquipmentAttributeReader.sameItems(EQUIPMENT_CHECKPOINTS.get(player.getUUID()), stacks)) {
            warnEquipment(player, equipment.result());
        }
        // On calculation failure throw to the caller: old snapshots must not be used for a hit.
        var next = calculate(ConfigService.current(), profile.role(), profile.level(), currentResources(profile), equipment);
        update(player, next);
        EQUIPMENT_CHECKPOINTS.put(player.getUUID(), next.equipment());
        return next;
    }

    private static void save(ServerPlayer player, Profile profile) {
        var tag = player.getPersistentData().getCompound(KEY).copy();
        tag.putString("role", profile.role());
        tag.putInt("level", profile.level());
        var resources = new CompoundTag();
        profile.resources().forEach((id, state) -> resources.putDouble(id, state.current()));
        tag.put("resources", resources);
        player.getPersistentData().put(KEY, tag);
    }

    private static void update(ServerPlayer player, Profile next) {
        save(player, next);
        PROFILES.put(player.getUUID(), next);
        AttributeNetwork.sync(player, next);
    }

    public static ResourceState resource(ServerPlayer player, String id) {
        var resource = get(player).resources().get(id);
        if (resource == null) throw new IllegalArgumentException("资源不存在: " + id);
        return resource;
    }

    public static ResourceState setResource(ServerPlayer player, String id, double value) {
        return changeResource(player, id, resource(player, id).set(value));
    }

    public static ResourceState addResource(ServerPlayer player, String id, double delta) {
        return changeResource(player, id, resource(player, id).add(delta));
    }

    private static ResourceState changeResource(ServerPlayer player, String id, ResourceState next) {
        var profile = get(player);
        var resources = new HashMap<>(profile.resources());
        resources.put(id, next);
        update(player, withResources(profile, resources));
        return next;
    }

    private static Profile withResources(Profile profile, Map<String, ResourceState> resources) {
        var resolved = new DerivedAttributeCalculator().calculate(ConfigService.current(), profile.base(), profile.level(), resources);
        return new Profile(profile.role(), profile.level(), resolved.snapshot(), resources, profile.equipment(),
                profile.base(), resolved.text(), resolved.pending());
    }

    /** Prepare equipment and every balance/formula before publishing anything, including on first use. */
    static ResourceConsumption.Result consumeResources(ServerPlayer player, Map<String, Double> costs, boolean preview) {
        var profile = PROFILES.get(player.getUUID());
        if (profile == null) {
            profile = calculate(player, ConfigService.current());
        } else {
            var stacks = EquipmentAttributeReader.capture(player);
            if (!EquipmentAttributeReader.sameItems(profile.equipment(), stacks)) {
                var equipment = new EquipmentAttributeReader().read(stacks, numericAttributes(ConfigService.current()));
                if (!EquipmentAttributeReader.sameItems(EQUIPMENT_CHECKPOINTS.get(player.getUUID()), stacks))
                    warnEquipment(player, equipment.result());
                profile = calculate(ConfigService.current(), profile.role(), profile.level(), currentResources(profile), equipment);
            }
        }
        var result = ResourceConsumption.evaluate(profile.resources(), costs);
        if (!result.accepted()) return result;
        var next = withResources(profile, result.after()); // May fail: neither the debit nor refreshed equipment is published yet.
        if (!preview) {
            save(player, next);
            PROFILES.put(player.getUUID(), next);
            EQUIPMENT_CHECKPOINTS.put(player.getUUID(), next.equipment());
            RESOURCE_ERRORS.remove(player.getUUID());
            // A delivery failure after commit must not look like a failed debit that a caller should retry.
            try { AttributeNetwork.sync(player, next); }
            catch (RuntimeException ex) {
                YesSteveAttribute.LOGGER.error("玩家 {} 资源已扣除，但快照同步失败；不要重复扣费", player.getUUID(), ex);
            }
        }
        return result;
    }

    /** Calculate all players before configuration publication; never mutate on failure. */
    public static Map<UUID, Profile> prepareReload(LoadedConfiguration config) {
        var prepared = new HashMap<UUID, Profile>();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (var player : server.getPlayerList().getPlayers()) {
                try {
                    var equipment = new EquipmentAttributeReader().read(player, numericAttributes(config));
                    warnEquipment(player, equipment.result());
                    var tag = player.getPersistentData().getCompound(KEY);
                    prepared.put(player.getUUID(), calculate(config, tag.getString("role"), tag.getInt("level"),
                            savedResources(player), equipment));
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException("玩家 " + player.getGameProfile().getName() + ": " + ex.getMessage(), ex);
                }
            }
        }
        return prepared;
    }

    public static void publish(Map<UUID, Profile> prepared) {
        RESOURCE_ERRORS.clear();
        PROFILES.clear();
        PROFILES.putAll(prepared);
        EQUIPMENT_CHECKPOINTS.clear();
        prepared.forEach((uuid, profile) -> EQUIPMENT_CHECKPOINTS.put(uuid, profile.equipment()));
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (var player : server.getPlayerList().getPlayers()) {
            var profile = prepared.get(player.getUUID());
            if (profile != null) save(player, profile);
        }
    }

    public static void syncAll() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (var player : server.getPlayerList().getPlayers()) AttributeNetwork.sync(player, get(player));
    }

    private static void restore(ServerPlayer player) {
        RESOURCE_ERRORS.remove(player.getUUID());
        PROFILES.remove(player.getUUID());
        EQUIPMENT_CHECKPOINTS.remove(player.getUUID());
        try {
            AttributeNetwork.sync(player, get(player));
        } catch (RuntimeException ex) {
            // Preserve the saved selection so an administrator can repair its configuration.
            player.sendSystemMessage(Component.literal("YSA 属性恢复失败：" + ex.getMessage()));
            YesSteveAttribute.LOGGER.error("恢复玩家 {} 属性失败", player.getUUID(), ex);
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(KEY)) {
            event.getEntity().getPersistentData().put(KEY, event.getOriginal().getPersistentData().getCompound(KEY).copy());
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            try { AttributeNetwork.sendTo(viewer, target, get(target)); }
            catch (RuntimeException ex) { YesSteveAttribute.LOGGER.warn("无法同步玩家 {} 属性: {}", target.getUUID(), ex.getMessage()); }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        RESOURCE_ERRORS.remove(event.getEntity().getUUID());
        PROFILES.remove(event.getEntity().getUUID());
        EQUIPMENT_CHECKPOINTS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { PROFILES.clear(); EQUIPMENT_CHECKPOINTS.clear(); RESOURCE_ERRORS.clear(); }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            var profile = PROFILES.get(player.getUUID());
            if (profile == null || !player.isAlive()) continue;
            var stacks = EquipmentAttributeReader.capture(player);
            if (!EquipmentAttributeReader.sameItems(EQUIPMENT_CHECKPOINTS.get(player.getUUID()), stacks)) {
                EQUIPMENT_CHECKPOINTS.put(player.getUUID(), stacks);
                var equipment = new EquipmentAttributeReader().read(stacks, numericAttributes(ConfigService.current()));
                warnEquipment(player, equipment.result());
                try {
                    var nextEquipment = calculate(ConfigService.current(), profile.role(), profile.level(),
                            currentResources(profile), equipment);
                    update(player, nextEquipment);
                    profile = nextEquipment;
                } catch (RuntimeException ex) {
                    player.sendSystemMessage(Component.literal("YSA 装备属性更新失败，保留上一份属性：" + ex.getMessage()));
                    YesSteveAttribute.LOGGER.error("更新玩家 {} 装备属性失败", player.getUUID(), ex);
                }
            }
            var next = new HashMap<String, ResourceState>();
            profile.resources().forEach((id, state) -> next.put(id, state.advance(1)));
            if (!next.equals(profile.resources())) {
                try {
                    update(player, withResources(profile, next));
                    RESOURCE_ERRORS.remove(player.getUUID());
                } catch (RuntimeException ex) {
                    if (RESOURCE_ERRORS.add(player.getUUID())) {
                        player.sendSystemMessage(Component.literal("YSA 资源/派生属性更新失败，保留上次状态，请修正公式：" + ex.getMessage()));
                        YesSteveAttribute.LOGGER.error("更新玩家 {} 资源/派生属性失败", player.getUUID(), ex);
                    }
                }
            }
        }
    }

    private static Map<String, Double> currentResources(Profile profile) {
        var values = new HashMap<String, Double>();
        profile.resources().forEach((id, state) -> values.put(id, state.current()));
        return values;
    }
}
