package com.yessteveattribute.forge;

import com.mojang.authlib.GameProfile;
import com.yessteveattribute.core.runtime.DamageApplication;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** Runs in a real dedicated Forge GameTest server; excluded from the release jar. */
@GameTestHolder(YesSteveAttribute.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static FakePlayer attacker(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "YsaTest"));
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)).getX(),
                helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)).getY(),
                helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)).getZ());
        player.setItemSlot(EquipmentSlot.MAINHAND, item("武器伤害: 10"));
        return player;
    }

    private static ItemStack item(String line) {
        var stack = new ItemStack(Items.STICK);
        var lines = new ListTag();
        lines.add(StringTag.valueOf(line));
        stack.getOrCreateTag().put("YSAAttributes", lines);
        return stack;
    }

    private static Cow target(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, 2, 2, 2);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100);
        return cow;
    }

    private static void equal(GameTestHelper helper, double expected, double actual, String label) {
        helper.assertTrue(Math.abs(expected - actual) < .0001, label + " expected " + expected + " got " + actual);
    }

    @GameTest(template = "empty")
    public static void armorAndImmediateEquipmentRefresh(GameTestHelper helper) {
        var player = attacker(helper);
        var cow = target(helper);
        cow.getAttribute(Attributes.ARMOR).setBaseValue(20);
        cow.setItemSlot(EquipmentSlot.MAINHAND, item("护甲: 20"));
        var preview = CombatService.preview(player, cow, 1, .5);
        equal(helper, 5, preview.calculation().damage(), "YSA armor subtracted once");
        equal(helper, 100, cow.getHealth(), "preview must not damage");
        var hit = CombatService.attack(player, cow, 1);
        equal(helper, 95, cow.getHealth(), "vanilla armor bypass");
        helper.assertTrue(hit.application().status() == DamageApplication.Status.ACCEPTED, "hit accepted");
        player.setItemSlot(EquipmentSlot.MAINHAND, item("武器伤害: 20"));
        equal(helper, 15, CombatService.preview(player, cow, 1, .5).calculation().damage(), "same-tick equipment refresh");
        if (Boolean.getBoolean("ysa.testYss")) YssIntegrationChecks.run(helper, player, cow);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absorptionResistanceAndCooldownRemainVanilla(GameTestHelper helper) {
        var player = attacker(helper);
        var cow = target(helper);
        cow.setAbsorptionAmount(3);
        cow.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        var hit = CombatService.attack(player, cow, 1);
        equal(helper, 95, cow.getHealth(), "10 * 0.8 - 3 absorption");
        equal(helper, 3, hit.absorptionLost(), "absorption loss");
        var repeated = CombatService.attack(player, cow, 1);
        helper.assertTrue(repeated.application().status() == DamageApplication.Status.REJECTED, "same hit blocked by cooldown");
        equal(helper, 95, cow.getHealth(), "cooldown preserves health");
        cow.invulnerableTime = 0; // Test setup only; production never resets it.
        CombatService.attack(player, cow, 1);
        equal(helper, 87, cow.getHealth(), "hit after cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negativeDamageAndInvulnerabilityNeverHealOrBypass(GameTestHelper helper) {
        var player = attacker(helper);
        var cow = target(helper);
        cow.setItemSlot(EquipmentSlot.MAINHAND, item("护甲: 100"));
        var negative = CombatService.attack(player, cow, 1);
        helper.assertTrue(negative.application().status() == DamageApplication.Status.NON_POSITIVE, "negative is no-op");
        equal(helper, 100, cow.getHealth(), "no healing or damage");
        equal(helper, 0, cow.invulnerableTime, "no cooldown for non-positive result");
        cow.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        cow.setInvulnerable(true);
        helper.assertTrue(CombatService.attack(player, cow, 1).application().status() == DamageApplication.Status.REJECTED,
                "invulnerability retained");
        equal(helper, 100, cow.getHealth(), "invulnerable health");
        helper.succeed();
    }

    public static final class CancelHit {
        private final Cow target;
        private int calls;
        public CancelHit(Cow target) { this.target = target; }
        @SubscribeEvent public void attack(LivingAttackEvent event) {
            if (event.getEntity() == target && event.getSource().is(CombatService.DAMAGE_TYPE)) {
                calls++;
                event.setCanceled(true);
            }
        }
    }

    @GameTest(template = "empty")
    public static void forgeCancellationAndKillCredit(GameTestHelper helper) {
        var player = attacker(helper);
        var cow = target(helper);
        var listener = new CancelHit(cow);
        MinecraftForge.EVENT_BUS.register(listener);
        try {
            var hit = CombatService.attack(player, cow, 1);
            helper.assertTrue(hit.application().status() == DamageApplication.Status.REJECTED, "Forge cancellation retained");
            equal(helper, 1, listener.calls, "one hurt attempt");
            equal(helper, 100, cow.getHealth(), "cancelled health");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        cow.setHealth(5);
        CombatService.attack(player, cow, 1);
        helper.assertTrue(cow.isDeadOrDying(), "lethal hit uses vanilla death");
        helper.assertTrue(cow.getKillCredit() == player, "kill attributed to attacker");
        helper.succeed();
    }
}
