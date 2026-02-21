package org.lab_11.modsunified.mixin.cookingforblockheads;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.client.render.block.StockpotBlockEntityRender")
abstract class StockpotBlockEntityRenderMixin {
    private static final String KALEIDOSCOPE_STOCKPOT_SOUP_BASE_ID_FIELD = "soupBaseId";
    private static final String KALEIDOSCOPE_POT_REFRESH_METHOD = "refresh";
    private static final ResourceLocation LEGACY_WATER_SOUP_BASE_ID =
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "water");
    private static final ResourceLocation VANILLA_WATER_SOUP_BASE_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "water");

    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void lab11$guardInvalidStockpotSoupBase(final @Coerce Object stockpot,
                                                     final float partialTick,
                                                     final PoseStack poseStack,
                                                     final MultiBufferSource buffer,
                                                     final int packedLight,
                                                     final int packedOverlay,
                                                     final CallbackInfo ci) {
        if (stockpot == null) {
            ci.cancel();
            return;
        }

        normalizeLegacySoupBase(stockpot);
        if (invokeNoArg(stockpot, "getSoupBase") != null) {
            return;
        }

        if (forceWaterSoupBase(stockpot) && invokeNoArg(stockpot, "getSoupBase") != null) {
            return;
        }

        ci.cancel();
    }

    private static void normalizeLegacySoupBase(final Object stockpot) {
        final Object soupBaseIdObject = invokeNoArg(stockpot, "getSoupBaseId");
        if (!(soupBaseIdObject instanceof ResourceLocation soupBaseId)
                || !LEGACY_WATER_SOUP_BASE_ID.equals(soupBaseId)) {
            return;
        }
        forceWaterSoupBase(stockpot);
    }

    private static boolean forceWaterSoupBase(final Object stockpot) {
        final Field soupBaseIdField = findField(stockpot.getClass(), KALEIDOSCOPE_STOCKPOT_SOUP_BASE_ID_FIELD);
        if (soupBaseIdField == null) {
            return false;
        }

        try {
            soupBaseIdField.setAccessible(true);
            soupBaseIdField.set(stockpot, VANILLA_WATER_SOUP_BASE_ID);
            invokeNoArg(stockpot, KALEIDOSCOPE_POT_REFRESH_METHOD);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            final Method method = findMethod(target.getClass(), methodName, 0);
            if (method == null) {
                return null;
            }

            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static Method findMethod(final Class<?> ownerClass,
                                     final String methodName,
                                     final int parameterCount) {
        Class<?> currentClass = ownerClass;
        while (currentClass != null) {
            for (final Method method : currentClass.getDeclaredMethods()) {
                if (methodName.equals(method.getName()) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static Field findField(final Class<?> ownerClass, final String fieldName) {
        Class<?> currentClass = ownerClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }
}
