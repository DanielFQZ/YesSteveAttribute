package com.yessteveattribute.forge;

import com.yessteveattribute.core.runtime.AttributeSnapshot;
import com.yessteveattribute.core.runtime.CombatCalculator;
import com.yessteveattribute.core.runtime.CombatDamageType;
import com.yessteveattribute.core.runtime.DamageApplication;
import com.yessteveattribute.core.runtime.DerivedAttributeCalculator;
import com.yessteveattribute.core.runtime.PlayerAttributeCalculator;
import com.yessteveattribute.core.runtime.ResourceCalculator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/**
 * Server-only entry for a caller that has already authorized its target and hit window.
 * Does not select targets, validate skill reach, deduplicate hits, or consume resources.
 * Integrations must replace their original hurt call, never call both.
 */
public final class CombatService {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(YesSteveAttribute.MOD_ID, "calculated"));

    private CombatService() { }

    public record Preview(CombatCalculator.Result calculation, List<String> diagnostics) {
        public Preview { diagnostics = List.copyOf(diagnostics); }
    }

    public record Applied(Preview preview, DamageApplication.Result application,
                          float healthBefore, float healthAfter, float absorptionBefore, float absorptionAfter) {
        public float healthLost() { return Math.max(0, healthBefore - healthAfter); }
        public float absorptionLost() { return Math.max(0, absorptionBefore - absorptionAfter); }
    }

    /** Deterministic diagnostic calculation. No health/absorption changes. */
    public static Preview preview(ServerPlayer attacker, LivingEntity target, double multiplier, double roll) {
        return preview(attacker, target, multiplier, roll, CombatDamageType.COMPOSITE);
    }

    /** Deterministic diagnostic calculation for a selected physical/elemental component. */
    public static Preview preview(ServerPlayer attacker, LivingEntity target, double multiplier, double roll,
                                  CombatDamageType damageType) {
        validateEntities(attacker, target);
        var config = ConfigService.current();
        if (config == null) throw new IllegalStateException("尚无有效 YSA 配置");
        var profile = PlayerAttributes.forCombat(attacker);
        AttributeSnapshot defense;
        List<String> diagnostics = List.of();
        if (target instanceof ServerPlayer player) {
            defense = PlayerAttributes.forCombat(player).snapshot();
        } else {
            var equipment = new EquipmentAttributeReader().read(target, PlayerAttributes.numericAttributes(config));
            diagnostics = equipment.result().diagnostics();
            // Non-player targets have no YSA role/persistent resource account in this release.
            var base = new PlayerAttributeCalculator().calculate(config, "", 0, equipment.result().snapshot());
            var resources = new ResourceCalculator().calculate(config, base, 0, Map.of());
            defense = new DerivedAttributeCalculator().calculate(config, base, 0, resources).snapshot();
        }
        var calculation = new CombatCalculator().calculate(config, profile.base(), profile.level(),
                profile.snapshot(), defense, multiplier, roll, damageType);
        return new Preview(calculation, diagnostics);
    }

    /** String bridge for optional mods that cannot depend on YSA's enum at compile time. */
    public static Preview preview(ServerPlayer attacker, LivingEntity target, double multiplier, double roll,
                                  String damageType) {
        return preview(attacker, target, multiplier, roll, CombatDamageType.parse(damageType));
    }

    /** Randomness is chosen here on the server, never supplied by a client damage packet. */
    public static Applied attack(ServerPlayer attacker, LivingEntity target, double multiplier) {
        return attack(attacker, target, multiplier, CombatDamageType.COMPOSITE);
    }

    /** Server-side typed hit; callers never provide a final damage value. */
    public static Applied attack(ServerPlayer attacker, LivingEntity target, double multiplier,
                                 CombatDamageType damageType) {
        validateEntities(attacker, target);
        return apply(attacker, target, preview(attacker, target, multiplier, attacker.getRandom().nextDouble(), damageType));
    }

    /** String bridge for optional mods that cannot depend on YSA's enum at compile time. */
    public static Applied attack(ServerPlayer attacker, LivingEntity target, double multiplier, String damageType) {
        return attack(attacker, target, multiplier, CombatDamageType.parse(damageType));
    }

    private static Applied apply(ServerPlayer attacker, LivingEntity target, Preview preview) {
        float health = target.getHealth();
        float absorption = target.getAbsorptionAmount();
        var application = DamageApplication.apply(preview.calculation().damage(), amount -> {
            var type = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE);
            // One vanilla entry: death credit, shields, effects, absorption, invulnerability and Forge hooks remain.
            return target.hurt(new DamageSource(type, attacker), amount);
        });
        return new Applied(preview, application, health, target.getHealth(), absorption, target.getAbsorptionAmount());
    }

    private static void validateEntities(ServerPlayer attacker, LivingEntity target) {
        if (attacker == null || target == null) throw new IllegalArgumentException("攻击者和目标不能为空");
        var server = attacker.getServer();
        if (server == null || !server.isSameThread() || target.level().isClientSide())
            throw new IllegalStateException("YSA 伤害入口只能在服务器线程调用");
        if (attacker.level() != target.level()) throw new IllegalArgumentException("攻击者和目标必须位于同一维度");
        if (!attacker.isAlive() || attacker.isRemoved() || attacker.isSpectator())
            throw new IllegalArgumentException("攻击者必须存活且不能是旁观者");
        if (!target.isAlive() || target.isRemoved() || target.isSpectator())
            throw new IllegalArgumentException("目标必须存活且不能是旁观者");
    }
}
