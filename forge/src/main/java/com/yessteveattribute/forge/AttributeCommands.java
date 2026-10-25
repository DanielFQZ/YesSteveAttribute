package com.yessteveattribute.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.yessteveattribute.core.runtime.AttributeDisplay;

/** Administrative commands; all changes are calculated on the server. */
public final class AttributeCommands {
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
            message -> Component.literal("YSA：" + message));

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("consume").then(Commands.argument("player", EntityArgument.player())
                .then(costArguments(1))));
        root.then(Commands.literal("damage").then(Commands.argument("attacker", EntityArgument.player())
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> damage(context, 1))
                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0))
                                .executes(context -> damage(context, DoubleArgumentType.getDouble(context, "multiplier")))))));
        root.then(Commands.literal("combat").then(Commands.argument("attacker", EntityArgument.player())
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> combat(context, 1, .5))
                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0))
                                .executes(context -> combat(context, DoubleArgumentType.getDouble(context, "multiplier"), .5))
                                .then(Commands.argument("roll", DoubleArgumentType.doubleArg(0, Math.nextDown(1.0)))
                                        .executes(context -> combat(context, DoubleArgumentType.getDouble(context, "multiplier"),
                                                DoubleArgumentType.getDouble(context, "roll"))))))));
        root.then(Commands.literal("resource").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("resource", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                ConfigService.current() == null ? java.util.List.of() : ConfigService.current().getAttributes().getResources().keySet()
                                        .stream().map(StringArgumentType::escapeIfRequired).toList(), builder))
                        .executes(context -> resource(context, "get"))
                        .then(Commands.literal("set").then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                .executes(context -> resource(context, "set"))))
                        .then(Commands.literal("add").then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                .executes(context -> resource(context, "add"))))
                        .then(Commands.literal("consume").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(context -> consume(context, java.util.Map.of(
                                        StringArgumentType.getString(context, "resource"),
                                        DoubleArgumentType.getDouble(context, "amount")), false)))))));
        root.then(Commands.literal("role").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("role", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                ConfigService.current() == null ? java.util.List.of() : ConfigService.current().getRoles().getRoles().keySet()
                                        .stream().map(StringArgumentType::escapeIfRequired).toList(), builder))
                        .executes(context -> setRole(context, null))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(context -> setRole(context, IntegerArgumentType.getInteger(context, "level")))))));
        root.then(Commands.literal("level").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("level", IntegerArgumentType.integer(0)).executes(context -> {
                    var player = EntityArgument.getPlayer(context, "player");
                    try {
                        var current = PlayerAttributes.get(player);
                        if (current.role().isEmpty()) throw new IllegalArgumentException("请先通过 /ysa role 指定角色");
                        return assign(context, player, current.role(), IntegerArgumentType.getInteger(context, "level"));
                    } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
                }))));
        root.then(Commands.literal("profile").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
            var player = EntityArgument.getPlayer(context, "player");
            try {
                var profile = PlayerAttributes.get(player);
                context.getSource().sendSuccess(() -> Component.literal("YSA " + player.getGameProfile().getName()
                        + "：角色 " + (profile.role().isEmpty() ? "未指定" : profile.role()) + "，等级 " + profile.level()
                        + "，数值属性 " + profile.snapshot().all().size()), false);
                return 1;
            } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
        })));
        root.then(Commands.literal("attr").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("attribute", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigService.definitions().keySet()
                                .stream().map(StringArgumentType::escapeIfRequired).toList(), builder))
                        .executes(context -> {
                            var player = EntityArgument.getPlayer(context, "player");
                            String id = StringArgumentType.getString(context, "attribute");
                            try {
                                var profile = PlayerAttributes.get(player);
                                if (profile.pending().contains(id) || profile.text().containsKey(id)) {
                                    String value = profile.pending().contains(id) ? "待战斗（需要目标或战斗上下文）" : profile.text().get(id);
                                    context.getSource().sendSuccess(() -> Component.literal("YSA " + id + " = " + value), false);
                                    return 1;
                                }
                                var snapshot = profile.snapshot();
                                if (!snapshot.all().containsKey(id)) throw new IllegalArgumentException("不存在数值属性: " + id);
                                String source = snapshot.explain(id).stream().map(c -> c.provenance().type() + ":"
                                                + c.provenance().source() + "/" + c.provenance().detail() + "=" + AttributeDisplay.number(c.value()))
                                        .collect(java.util.stream.Collectors.joining(", "));
                                context.getSource().sendSuccess(() -> Component.literal("YSA " + player.getGameProfile().getName()
                                        + " · " + id + " = " + AttributeDisplay.number(snapshot.get(id)) + "；来源：" + source), false);
                                return 1;
                            } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
                        }))));
    }

    private static int combat(CommandContext<CommandSourceStack> context, double multiplier, double roll) throws CommandSyntaxException {
        var attacker = EntityArgument.getPlayer(context, "attacker");
        var target = livingTarget(context);
        try {
            var preview = CombatService.preview(attacker, target, multiplier, roll);
            var result = preview.calculation();
            context.getSource().sendSuccess(() -> Component.literal("YSA 战斗试算（未扣血）："
                    + attacker.getGameProfile().getName() + " → " + target.getName().getString()
                    + "，倍率 " + AttributeDisplay.number(multiplier) + "，随机值 " + AttributeDisplay.number(roll)), false);
            reportTargetEquipment(context, preview);
            context.getSource().sendSuccess(() -> Component.literal(
                    "  倍率前基础伤害 = " + AttributeDisplay.number(result.preMultiplierBaseDamage())
                            + "；本次倍率后基础伤害 = " + AttributeDisplay.number(result.baseDamage())), false);
            for (String id : java.util.List.of("基础伤害", "无属性伤害", "元素伤害总和", "护甲减伤", "元素减伤总和",
                    "暴击概率", "本次是否暴击", "暴击前伤害", "最终伤害")) {
                if (result.steps().containsKey(id)) context.getSource().sendSuccess(() -> Component.literal(
                        "  " + (id.equals("基础伤害") ? "基础伤害（本次倍率后）" : id) + " = "
                                + AttributeDisplay.number(result.steps().get(id))), false);
            }
            return 1;
        } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
    }

    private static LivingEntity livingTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var target = EntityArgument.getEntity(context, "target");
        if (!(target instanceof LivingEntity living)) throw ERROR.create("目标必须是有生命的实体");
        return living;
    }

    private static int damage(CommandContext<CommandSourceStack> context, double multiplier) throws CommandSyntaxException {
        var attacker = EntityArgument.getPlayer(context, "attacker");
        var target = livingTarget(context);
        try {
            var result = CombatService.attack(attacker, target, multiplier);
            String status = switch (result.application().status()) {
                case NON_POSITIVE -> "公式结果非正数，未调用伤害入口";
                case ACCEPTED -> "原版伤害入口已接受";
                case REJECTED -> "原版伤害入口已拒绝（可能为无敌帧、盾牌、免疫或事件拦截）";
            };
            context.getSource().sendSuccess(() -> Component.literal("YSA 实际伤害："
                    + attacker.getGameProfile().getName() + " → " + target.getName().getString()
                    + "；公式 " + AttributeDisplay.number(result.preview().calculation().damage())
                    + "，提交 " + AttributeDisplay.number(result.application().submitted())
                    + "，暴击 " + (result.preview().calculation().critical() ? "是" : "否") + "；" + status), false);
            context.getSource().sendSuccess(() -> Component.literal("  生命 " + AttributeDisplay.number(result.healthBefore())
                    + " → " + AttributeDisplay.number(result.healthAfter()) + "（净减少 " + AttributeDisplay.number(result.healthLost())
                    + "），吸收 " + AttributeDisplay.number(result.absorptionBefore()) + " → "
                    + AttributeDisplay.number(result.absorptionAfter()) + "（净减少 " + AttributeDisplay.number(result.absorptionLost()) + "）"), false);
            reportTargetEquipment(context, result.preview());
            return result.application().status() == com.yessteveattribute.core.runtime.DamageApplication.Status.ACCEPTED ? 1 : 0;
        } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
    }

    private static void reportTargetEquipment(CommandContext<CommandSourceStack> context, CombatService.Preview preview) {
        for (String diagnostic : preview.diagnostics()) {
            context.getSource().sendSuccess(() -> Component.literal("YSA 目标装备属性已忽略：" + diagnostic), false);
        }
    }

    private static int setRole(CommandContext<CommandSourceStack> context, Integer level) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String id = StringArgumentType.getString(context, "role");
        try {
            var config = ConfigService.current();
            if (config == null) throw new IllegalStateException("尚无有效配置");
            var role = config.getRoles().get(id);
            if (role == null) throw new IllegalArgumentException("角色不存在: " + id);
            return assign(context, player, id, level == null ? config.getLevels().get(role.getLevelRule()).getStart() : level);
        } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
    }

    private static int resource(CommandContext<CommandSourceStack> context, String operation) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String id = StringArgumentType.getString(context, "resource");
        try {
            var state = switch (operation) {
                case "set" -> PlayerAttributes.setResource(player, id, DoubleArgumentType.getDouble(context, "amount"));
                case "add" -> PlayerAttributes.addResource(player, id, DoubleArgumentType.getDouble(context, "amount"));
                default -> PlayerAttributes.resource(player, id);
            };
            context.getSource().sendSuccess(() -> Component.literal("YSA " + player.getGameProfile().getName()
                    + " · " + id + " = " + AttributeDisplay.number(state.current()) + " / " + AttributeDisplay.number(state.maximum())
                    + "；恢复 " + AttributeDisplay.number(state.recovery()) + "/秒，衰减 " + AttributeDisplay.number(state.decay()) + "/秒"), false);
            return 1;
        } catch (RuntimeException ex) { throw ERROR.create(ex.getMessage()); }
    }

    /** Up to eight resource/amount pairs; the API itself has no command-line count limit. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> costArguments(int index) {
        var amount = Commands.argument("costAmount" + index, DoubleArgumentType.doubleArg(0))
                .executes(context -> consume(context, index, false))
                .then(Commands.literal("preview").executes(context -> consume(context, index, true)));
        if (index < 8) amount.then(costArguments(index + 1));
        return Commands.argument("costResource" + index, StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        ConfigService.current() == null ? java.util.List.of() : ConfigService.current().getAttributes().getResources().keySet()
                                .stream().map(StringArgumentType::escapeIfRequired).toList(), builder))
                .then(amount);
    }

    private static int consume(CommandContext<CommandSourceStack> context, int count, boolean preview) throws CommandSyntaxException {
        var costs = new java.util.LinkedHashMap<String, Double>();
        for (int index = 1; index <= count; index++) {
            String id = StringArgumentType.getString(context, "costResource" + index);
            double value = DoubleArgumentType.getDouble(context, "costAmount" + index);
            if (costs.putIfAbsent(id, value) != null) throw ERROR.create("消耗资源重复: " + id + "；整笔未扣除");
        }
        return consume(context, costs, preview);
    }

    private static int consume(CommandContext<CommandSourceStack> context, java.util.Map<String, Double> costs,
                               boolean preview) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        try {
            var result = preview ? ResourceService.preview(player, costs) : ResourceService.tryConsume(player, costs);
            String title = result.accepted() ? (preview ? "可支付（仅预览，未扣除）" : "扣除成功") : "余额不足，整笔未扣除";
            context.getSource().sendSuccess(() -> Component.literal("YSA " + player.getGameProfile().getName() + " 资源消耗：" + title), false);
            result.costs().forEach((id, amount) -> {
                String shortage = result.shortages().containsKey(id)
                        ? "；缺少 " + AttributeDisplay.number(result.shortages().get(id)) : "";
                context.getSource().sendSuccess(() -> Component.literal("  " + id + "：需要 " + AttributeDisplay.number(amount)
                        + "；" + AttributeDisplay.number(result.before().get(id).current()) + " → "
                        + AttributeDisplay.number(result.after().get(id).current()) + shortage), false);
            });
            return result.accepted() ? 1 : 0;
        } catch (RuntimeException ex) { throw ERROR.create("消耗未提交：" + ex.getMessage()); }
    }

    private static int assign(CommandContext<CommandSourceStack> context, ServerPlayer player, String role, int level) {
        PlayerAttributes.assign(player, role, level);
        context.getSource().sendSuccess(() -> Component.literal("YSA 已设置 " + player.getGameProfile().getName()
                + "：角色 " + role + "，等级 " + level + "，属性已同步"), true);
        return 1;
    }
}
