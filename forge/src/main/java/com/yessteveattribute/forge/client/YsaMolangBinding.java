package com.yessteveattribute.forge.client;

import com.elfmcys.ysm.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.context.AnimationContext;
import net.minecraft.world.entity.Entity;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import com.elfmcys.ysm.molang.runtime.Function;
import com.yessteveattribute.forge.ConfigService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * YSA 的客户端 Molang API。
 *
 * <pre>
 * ysa.attr('力量')
 * ysa.damage(1.44, '物理')
 * </pre>
 *
 * <p>attr 读取当前实体的服务端同步快照；无实体的编辑器预览使用本地默认值。
 * damage 只登记本地玩家当前 YSS 授权动画中的一次性伤害输入，最终目标和结算仍由服务端处理。</p>
 */
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
            String name = arguments.getAsString(context, 0);
            Object owner = context.entity();
            if (owner instanceof IContext<?> molang) owner = molang.entity();
            if (owner instanceof Entity entity) {
                return (float) ClientAttributes.get(entity.getUUID(), name);
            }
            // No entity in editor previews: retain local configured defaults.
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
            float multiplier = arguments.getAsFloat(context, 0);
            String type = arguments.getAsString(context, 1);
            Object owner = context.entity();
            if (!(owner instanceof IContext<?> molang)) return 0F;
            Object entity = molang.entity();
            AnimationContext animation = molang.animationContext();
            if (!(entity instanceof Entity) || animation == null || !Float.isFinite(multiplier) || multiplier < 0F
                    || type == null || type.length() > 32) return 0F;
            return YssDamageBridge.queue(entity, multiplier, type, animation.animTime()) ? 1F : 0F;
        }
    }

    /** Optional bridge to YSS: YSA keeps compiling and loading when the skill mod is absent. */
    private static final class YssDamageBridge {
        private static volatile Method queueMethod;
        private static volatile boolean resolved;

        private static boolean queue(Object entity, float multiplier, String type, float animationTime) {
            Method method = resolve();
            if (method == null) return false;
            try {
                return Boolean.TRUE.equals(method.invoke(null, entity, multiplier, type, animationTime));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        private static Method resolve() {
            if (resolved) return queueMethod;
            synchronized (YssDamageBridge.class) {
                if (resolved) return queueMethod;
                try {
                    Class<?> service = Class.forName("io.github.tt432.yessteveskill.yss.attack.YssAttackService", false,
                            YsaMolangBinding.class.getClassLoader());
                    queueMethod = service.getMethod("queueMolangDamage", Entity.class,
                            float.class, String.class, float.class);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    queueMethod = null;
                }
                resolved = true;
                return queueMethod;
            }
        }
    }
}
