package com.yessteveattribute.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Opt-in real YSS action-start -> resource transaction -> hit/movement authorization. */
@GameTestHolder(YesSteveAttribute.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillCostGameTests {
    private static void check(GameTestHelper h, boolean value, String message) { h.assertTrue(value, message); }
    private static void equal(GameTestHelper h, double expected, double actual, String message) {
        check(h, Math.abs(expected-actual)<.0001, message+": "+actual);
    }
    private static FakePlayer player(GameTestHelper h, String model) throws Exception {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "YssCostTest"));
        p.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)).getX(),
                h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)).getY(),
                h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)).getZ());
        var cap=Class.forName("io.github.tt432.yessteveskill.yss.attack.YssAttackModeCapability");
        cap.getMethod("setEnabled",Player.class,boolean.class).invoke(null,p,true);
        cap.getMethod("setCachedModelId",Player.class,String.class).invoke(null,p,model);
        return p;
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void skillCostLifecycle(GameTestHelper h) throws Exception {
        if (!Boolean.getBoolean("ysa.testYss")) { h.succeed(); return; }
        String model="ysa-cost-test-"+UUID.randomUUID();
        Path dir=(Path)Class.forName("io.github.tt432.yessteveskill.yss.YssPathHelper")
                .getMethod("resolveProjectDirectory",String.class).invoke(null,model);
        Files.createDirectories(dir);
        Path config=dir.resolve("hit.json");
        Files.writeString(config,"""
            {"animation":{
              "paid":{"resource_costs":{"充能":10,"精力":20},
                "segments":[{"damage":10,"use_vfx_hitboxes":true}],
                "caster_moves":[{"power":1}],"hover":{"duration":1}},
              "delayed":{"resource_costs":{"充能":10},
                "segments":[{"damage":10,"damage_timing":"delayed","use_vfx_hitboxes":true}]},
              "free":{"segments":[{"damage":10,"use_vfx_hitboxes":true}]}
            }}
            """);
        try {
            var service=Class.forName("io.github.tt432.yessteveskill.yss.attack.YssActionService");
            Method begin=service.getMethod("begin",ServerPlayer.class,long.class,String.class);
            Method allows=service.getMethod("allows",ServerPlayer.class,long.class,String.class);
            Method end=service.getMethod("end",ServerPlayer.class,long.class);
            var executor=Class.forName("io.github.tt432.yessteveskill.yss.attack.YssAttackServerExecutor");
            Method hit=executor.getMethod("executeHitEvent",ServerPlayer.class,long.class,int.class,int[].class,String.class,float.class);
            Method move=executor.getMethod("executeCasterMove",ServerPlayer.class,long.class,int.class,String.class,float.class);
            Method hover=executor.getMethod("executeHover",ServerPlayer.class,long.class,String.class);
            var paid=player(h,model);
            PlayerAttributes.setResource(paid,"充能",30);
            var rejected=player(h,model);
            PlayerAttributes.setResource(rejected,"充能",9);
            var cow=h.spawn(EntityType.COW,2,2,2); cow.setNoAi(true); cow.setNoGravity(true);
            var other=h.spawn(EntityType.COW,2,2,1); other.setNoAi(true); other.setNoGravity(true);
            float health=cow.getHealth();
            check(h,!(boolean)begin.invoke(null,rejected,1L,"paid"),"insufficient start rejected");
            equal(h,9,PlayerAttributes.resource(rejected,"充能").current(),"insufficient charge unchanged");
            equal(h,500,PlayerAttributes.resource(rejected,"精力").current(),"insufficient whole transaction");
            var velocity=rejected.getDeltaMovement();
            hit.invoke(null,rejected,1L,0,new int[]{cow.getId()},"paid",0F);
            move.invoke(null,rejected,1L,0,"paid",0F);
            hover.invoke(null,rejected,1L,"paid");
            equal(h,health,cow.getHealth(),"unpaid hit blocked");
            check(h,velocity.equals(rejected.getDeltaMovement()),"unpaid caster move blocked");
            var stateCap=Class.forName("io.github.tt432.yessteveskill.combat.CombatStateCapability");
            Object cap=stateCap.getMethod("get",net.minecraft.world.entity.LivingEntity.class).invoke(null,rejected);
            if (cap!=null) {
                Object state=stateCap.getMethod("getState").invoke(cap);
                check(h,!(boolean)state.getClass().getMethod("isHover").invoke(state),"unpaid hover blocked");
            }
            PlayerAttributes.setResource(rejected,"充能",30);
            check(h,!(boolean)begin.invoke(null,rejected,1L,"paid"),"refill cannot retry denied id");
            equal(h,30,PlayerAttributes.resource(rejected,"充能").current(),"denied replay no payment");

            check(h,(boolean)begin.invoke(null,paid,1L,"paid"),"paid action starts even without a hit");
            equal(h,20,PlayerAttributes.resource(paid,"充能").current(),"whiff pays on start");
            check(h,(boolean)begin.invoke(null,paid,1L,"paid"),"duplicate start same result");
            equal(h,20,PlayerAttributes.resource(paid,"充能").current(),"duplicate start never repays");
            hit.invoke(null,paid,999L,0,new int[]{cow.getId()},"paid",0F);
            equal(h,health,cow.getHealth(),"wrong action sequence blocked");
            executor.getMethod("executeHitEvent",ServerPlayer.class,int.class,int[].class,String.class,float.class)
                    .invoke(null,paid,0,new int[]{cow.getId()},"paid",0F);
            equal(h,health,cow.getHealth(),"legacy API cannot bypass paid authorization");
            hit.invoke(null,paid,1L,0,new int[]{cow.getId(),other.getId()},"paid",0F);
            equal(h,health-10,cow.getHealth(),"paid hit executes");
            equal(h,health-10,other.getHealth(),"second target hit");
            equal(h,20,PlayerAttributes.resource(paid,"充能").current(),"multiple targets no extra payment");
            equal(h,480,PlayerAttributes.resource(paid,"精力").current(),"stamina charged once");
            end.invoke(null,paid,1L);
            check(h,!(boolean)allows.invoke(null,paid,1L,"paid"),"ended action cannot hit");
            check(h,!(boolean)begin.invoke(null,paid,1L,"paid"),"ended action cannot reopen");
            equal(h,20,PlayerAttributes.resource(paid,"充能").current(),"end does not refund");

            var free=player(h,model);
            check(h,(boolean)begin.invoke(null,free,1L,"free"),"old animation remains free");
            equal(h,0,PlayerAttributes.resource(free,"充能").current(),"no charge required for old config");

            var delayed=player(h,model);
            h.getLevel().addNewPlayer(delayed);
            PlayerAttributes.setResource(delayed,"充能",20);
            check(h,(boolean)begin.invoke(null,delayed,1L,"delayed"),"delayed action paid");
            var delayedCow=h.spawn(EntityType.COW,2,2,2); delayedCow.setNoAi(true);
            delayedCow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
            delayedCow.setHealth(40);
            hit.invoke(null,delayed,1L,0,new int[]{delayedCow.getId()},"delayed",0F);
            equal(h,40,delayedCow.getHealth(),"delayed damage queued");
            end.invoke(null,delayed,1L);
            executor.getMethod("tryFirePending",net.minecraft.world.entity.LivingEntity.class).invoke(null,delayedCow);
            equal(h,30,delayedCow.getHealth(),"accepted pending hit survives normal action end");
            equal(h,10,PlayerAttributes.resource(delayed,"充能").current(),"delayed delivery no extra payment");
            delayed.discard();

            h.runAfterDelay(2,() -> {
                try {
                    check(h,(boolean)begin.invoke(null,paid,2L,"paid"),"next playback starts");
                    equal(h,10,PlayerAttributes.resource(paid,"充能").current(),"next playback pays again");
                    check(h,(boolean)begin.invoke(null,rejected,2L,"paid"),"new playback retries after refill");
                    equal(h,20,PlayerAttributes.resource(rejected,"充能").current(),"retry on new playback paid once");
                    service.getMethod("cancel",ServerPlayer.class).invoke(null,paid);
                    check(h,!(boolean)allows.invoke(null,paid,2L,"paid"),"cancel revokes action");
                    equal(h,10,PlayerAttributes.resource(paid,"充能").current(),"cancel no refund");
                    // Changing the server fee invalidates an existing free grant.
                    Files.writeString(config,Files.readString(config).replace("\"free\":{\"segments\"", "\"free\":{\"resource_costs\":{\"充能\":1},\"segments\""));
                    check(h,!(boolean)allows.invoke(null,free,1L,"free"),"fee change revokes free action");
                    YesSteveAttribute.LOGGER.info("YSS skill cost checks passed: start, replay, shortage, multi-target, movement, delayed, cancel, free, config change");
                    h.succeed();
                } catch (Exception ex) { throw new AssertionError("YSS cost lifecycle failed",ex); }
                finally { cleanup(config,dir); }
            });
        } catch (Throwable error) {
            cleanup(config,dir);
            throw error;
        }
    }
    private static void cleanup(Path file,Path dir) {
        try { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
        catch (Exception ex) { throw new AssertionError("Cannot clean YSS cost fixture",ex); }
    }
}
