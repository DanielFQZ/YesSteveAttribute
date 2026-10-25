package com.yessteveattribute.forge;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;

/** Opt-in checks against the actual YSS jar and its single-target executor (not synthetic YSA classes). */
final class YssIntegrationChecks {
    private static void equal(GameTestHelper helper, double expected, double actual, String message) {
        helper.assertTrue(Math.abs(expected - actual) < .0001, message + ": " + actual);
    }

    static void run(GameTestHelper helper, ServerPlayer player, Cow cow) {
        try {
            Class<?> segmentType = Class.forName("io.github.tt432.yessteveskill.combat.config.HitSegment");
            Codec<?> codec = (Codec<?>) segmentType.getField("CODEC").get(null);
            Object segment = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"damage\":1,\"damage_mode\":\"ysa_multiplier\",\"use_vfx_hitboxes\":true}"))
                    .result().orElseThrow();
            Class<?> executor = Class.forName("io.github.tt432.yessteveskill.yss.attack.YssAttackServerExecutor");
            var apply = executor.getDeclaredMethod("applyHit", ServerPlayer.class, LivingEntity.class, segmentType,
                    Class.forName("io.github.tt432.yessteveskill.combat.config.HitProjectConfig"),
                    Class.forName("io.github.tt432.yessteveskill.combat.config.EntityCombatStats"));
            apply.setAccessible(true);
            cow.invulnerableTime = 0;
            cow.setHealth(100);
            cow.setDeltaMovement(.12, 0, -.08);
            // Player weapon=20, target YSA armor=20. One YSA hit must subtract 15, ignoring vanilla armor=20.
            helper.assertTrue((boolean) apply.invoke(null, player, cow, segment, null, null), "YSS accepted YSA hit");
            equal(helper, 85, cow.getHealth(), "YSS→YSA damage once");
            equal(helper, .12, cow.getDeltaMovement().x, "YSS retains pre-hit horizontal x");
            equal(helper, -.08, cow.getDeltaMovement().z, "YSS retains pre-hit horizontal z");
            helper.assertTrue(!(boolean) apply.invoke(null, player, cow, segment, null, null), "cooldown rejects repeated hit");
            equal(helper, 85, cow.getHealth(), "no fallback after rejected YSA hit");

            cow.invulnerableTime = 0;
            var listener = new CombatGameTests.CancelHit(cow);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
            try {
                helper.assertTrue(!(boolean) apply.invoke(null, player, cow, segment, null, null), "Forge cancel propagates to YSS");
                equal(helper, 85, cow.getHealth(), "cancel does not fallback to direct damage");
            } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); }

            Object negative = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"damage\":0.1,\"damage_mode\":\"ysa_multiplier\",\"knockback\":{\"power\":1}}"))
                    .result().orElseThrow();
            Vec3 before = cow.getDeltaMovement();
            helper.assertTrue(!(boolean) apply.invoke(null, player, cow, negative, null, null), "negative YSA result rejects displacement");
            equal(helper, 85, cow.getHealth(), "negative never heals");
            helper.assertTrue(before.equals(cow.getDeltaMovement()), "negative skips custom knockback");

            Object direct = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"damage\":10}"))
                    .result().orElseThrow();
            helper.assertTrue((boolean) apply.invoke(null, player, cow, direct, null, null), "legacy direct hit works");
            equal(helper, 81, cow.getHealth(), "legacy 10 damage mitigated by vanilla armor 20 to 4");
            YesSteveAttribute.LOGGER.info("YSS integration checks passed: YSA hit, cooldown, cancellation, negative, velocity, direct");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("YSS integration reflection failed", ex);
        }
    }
}
