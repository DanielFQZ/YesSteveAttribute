package com.yessteveattribute.forge.client;

import com.elfmcys.ysm.api.annotation.Side;
import com.elfmcys.ysm.api.annotation.YsmExtension;
import com.elfmcys.ysm.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import com.elfmcys.ysm.molang.runtime.Function;
import com.yessteveattribute.forge.ConfigService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * YSA 的客户端 Molang API 桩。
 *
 * <pre>
 * ysa.attr('力量')
 * ysa.damage(1.44, '物理')
 * </pre>
 *
 * <p>当前版本先完成语法和注册链路。属性快照与服务端伤害桥接完成后，
 * 两个函数会复用同一套核心计算接口。</p>
 */
@YsmExtension(side = Side.CLIENT)
public final class YsaMolangBinding extends ContextBinding {
    public YsaMolangBinding() {
        function("attr", new AttributeFunction());
        function("damage", new DamageFunction());
    }

    private static final class AttributeFunction implements Function {
        @Override
        public boolean validateArgumentSize(int size) {
            return size == 1;
        }

        @Nullable
        @Override
        public Object evaluate(@NotNull ExecutionContext<?> context,
                               @NotNull ArgumentCollection arguments) {
            // 第一阶段返回当前配置中的默认值；玩家快照接入后在这里替换为实体属性读取。
            String name = arguments.getAsString(context, 0);
            return (float) ConfigService.defaultValue(name);
        }
    }

    private static final class DamageFunction implements Function {
        @Override
        public boolean validateArgumentSize(int size) {
            return size == 2;
        }

        @Nullable
        @Override
        public Object evaluate(@NotNull ExecutionContext<?> context,
                               @NotNull ArgumentCollection arguments) {
            // TODO: 将倍率与伤害类型写入待处理攻击上下文，由服务端按原版规则结算。
            arguments.getAsFloat(context, 0);
            arguments.getAsString(context, 1);
            return 0F;
        }
    }
}
