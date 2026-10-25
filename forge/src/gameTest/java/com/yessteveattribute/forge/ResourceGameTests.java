package com.yessteveattribute.forge;

import com.mojang.authlib.GameProfile;
import com.yessteveattribute.core.formula.FormulaException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Resource transactions against real server profiles, NBT, formulas and Brigadier commands. */
@GameTestHolder(YesSteveAttribute.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResourceGameTests {
    private static FakePlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "YsaResourceTest"));
    }

    private static void equal(GameTestHelper helper, double expected, double actual, String label) {
        helper.assertTrue(Math.abs(expected - actual) < 1e-8, label + ": " + actual);
    }

    @GameTest(template = "empty")
    public static void atomicPaymentPersistenceAndSameTickRecheck(GameTestHelper helper) {
        var player = player(helper);
        PlayerAttributes.setResource(player, "充能", 10);
        var before = PlayerAttributes.get(player);
        var saved = player.getPersistentData().copy();
        var rejected = ResourceService.tryConsume(player, Map.of("精力", 20.0, "充能", 11.0));
        helper.assertTrue(!rejected.accepted(), "insufficient rejected");
        helper.assertTrue(PlayerAttributes.get(player) == before, "whole profile unchanged on rejection");
        helper.assertTrue(player.getPersistentData().equals(saved), "NBT unchanged on rejection");
        var preview = ResourceService.preview(player, Map.of("精力", 20.0, "充能", 10.0));
        helper.assertTrue(preview.accepted() && PlayerAttributes.get(player) == before, "preview does not commit");
        helper.assertTrue(player.getPersistentData().equals(saved), "preview does not save");
        var paid = ResourceService.tryConsume(player, Map.of("精力", 20.0, "充能", 10.0));
        helper.assertTrue(paid.accepted(), "exact payment accepted");
        equal(helper, 480, PlayerAttributes.resource(player, "精力").current(), "stamina paid");
        equal(helper, 0, PlayerAttributes.get(player).snapshot().get("充能值"), "snapshot updated immediately");
        equal(helper, 480, player.getPersistentData().getCompound("yessteveattribute").getCompound("resources").getDouble("精力"), "saved balance");
        helper.assertTrue(!ResourceService.tryConsume(player, "充能", 1).accepted(), "second same-tick call sees new balance");
        var restored = player(helper);
        restored.getPersistentData().put("yessteveattribute", player.getPersistentData().getCompound("yessteveattribute").copy());
        equal(helper, 480, PlayerAttributes.get(restored).resources().get("精力").current(), "restored saved payment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changedEquipmentIsPreparedWithoutPartialPublication(GameTestHelper helper) {
        var player = player(helper);
        var before = PlayerAttributes.get(player);
        var saved = player.getPersistentData().copy();
        var item = new ItemStack(Items.STICK);
        var lines = new ListTag();
        lines.add(StringTag.valueOf("精力上限: -450"));
        item.getOrCreateTag().put("YSAAttributes", lines);
        player.setItemSlot(EquipmentSlot.MAINHAND, item);
        var rejected = ResourceService.tryConsume(player, "精力", 51);
        helper.assertTrue(!rejected.accepted(), "new cap checked before tick polling");
        equal(helper, 50, rejected.before().get("精力").current(), "effective balance clamps to new cap");
        helper.assertTrue(PlayerAttributes.get(player) == before, "failed transaction does not publish equipment refresh");
        helper.assertTrue(player.getPersistentData().equals(saved), "failed refresh/debit does not save");
        helper.assertTrue(ResourceService.tryConsume(player, "精力", 50).accepted(), "new cap exact payment");
        equal(helper, 50, PlayerAttributes.resource(player, "精力").maximum(), "cap published with payment");
        equal(helper, 0, PlayerAttributes.resource(player, "精力").current(), "no stale 500 balance used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void formulaFailureRollsBackEveryBalance(GameTestHelper helper) throws Exception {
        var original = ConfigService.directory();
        var temporary = Files.createTempDirectory("ysa-resource-gametest-");
        try {
            for (String name : new String[]{"attributes.yml", "formulas.yml", "roles.yml", "levels.yml"})
                Files.copy(original.resolve(name), temporary.resolve(name));
            var formulas = temporary.resolve("formulas.yml");
            String source = Files.readString(formulas);
            helper.assertTrue(source.contains("<精神> * 1.2"), "expected test fixture expression");
            Files.writeString(formulas, source.replace("<精神> * 1.2", "1 / (<充能值> - 5)"));
            helper.assertTrue(ConfigService.reload(temporary), "temporary formula loaded");
            var player = player(helper);
            PlayerAttributes.setResource(player, "充能", 10);
            var before = PlayerAttributes.get(player);
            var saved = player.getPersistentData().copy();
            for (boolean preview : new boolean[]{true, false}) {
                boolean threw = false;
                try {
                    if (preview) ResourceService.preview(player, Map.of("充能", 5.0, "精力", 20.0));
                    else ResourceService.tryConsume(player, Map.of("充能", 5.0, "精力", 20.0));
                } catch (FormulaException expected) { threw = true; }
                helper.assertTrue(threw, "post-payment division by zero must reject");
                helper.assertTrue(PlayerAttributes.get(player) == before, "formula failure preserves profile");
                helper.assertTrue(player.getPersistentData().equals(saved), "formula failure preserves NBT");
            }
            helper.assertTrue(ResourceService.tryConsume(player, Map.of("充能", 4.0, "精力", 20.0)).accepted(), "valid payment");
            equal(helper, 1, PlayerAttributes.get(player).snapshot().get("生命恢复速度"), "dependent formula recomputed");
            equal(helper, 480, PlayerAttributes.resource(player, "精力").current(), "other resource paid once");
        } finally {
            helper.assertTrue(ConfigService.reload(original), "restore original configuration");
            for (String name : new String[]{"attributes.yml", "formulas.yml", "roles.yml", "levels.yml"})
                Files.deleteIfExists(temporary.resolve(name));
            Files.deleteIfExists(temporary);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandsAndThreadGuard(GameTestHelper helper) throws Exception {
        var player = player(helper);
        PlayerAttributes.setResource(player, "充能", 10);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2);
        equal(helper, 1, dispatcher.execute("ysa consume @s \"充能\" 4 \"精力\" 20 preview", source), "preview command result");
        equal(helper, 10, PlayerAttributes.resource(player, "充能").current(), "command preview no payment");
        equal(helper, 1, dispatcher.execute("ysa consume @s \"充能\" 4 \"精力\" 20", source), "multi command result");
        equal(helper, 6, PlayerAttributes.resource(player, "充能").current(), "command charge");
        equal(helper, 0, dispatcher.execute("ysa consume @s \"充能\" 7 \"精力\" 20", source), "insufficient result zero");
        equal(helper, 480, PlayerAttributes.resource(player, "精力").current(), "command all-or-nothing");
        for (String command : new String[]{"ysa consume @s \"充能\" 1 \"充能\" 2",
                "ysa consume @s \"充能\" 1 \"不存在\" 0", "ysa consume @s \"充能\" -1"}) {
            boolean rejected = false;
            try { dispatcher.execute(command, source); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid command rejected: " + command);
        }
        equal(helper, 6, PlayerAttributes.resource(player, "充能").current(), "invalid commands do not debit");
        boolean denied = false;
        try { dispatcher.execute("ysa consume @s \"充能\" 1", source.withPermission(0)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        helper.assertTrue(denied, "non-admin command rejected");
        boolean guarded = CompletableFuture.supplyAsync(() -> {
            try { ResourceService.tryConsume(player, "充能", 1); return false; }
            catch (IllegalStateException expected) { return true; }
        }).join();
        helper.assertTrue(guarded, "off-thread call rejected before accessing account");
        equal(helper, 1, dispatcher.execute("ysa resource @s \"充能\" consume 6", source), "single resource shorthand");
        equal(helper, 0, PlayerAttributes.resource(player, "充能").current(), "exact command payment");
        helper.succeed();
    }
}
