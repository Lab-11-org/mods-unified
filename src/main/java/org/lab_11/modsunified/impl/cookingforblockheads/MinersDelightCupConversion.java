package org.lab_11.modsunified.impl.cookingforblockheads;

import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.blay09.mods.cookingforblockheads.kitchen.CombinedKitchenItemProvider;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

final class MinersDelightCupConversion {
    static final String COPPER_POT_TARGET_KEY = BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT;
    private static final String CUP_CONVERSION_CLASS =
            "com.sammy.minersdelight.content.data.CupConversionDataMap";
    private static final String CUP_CONVERSION_METHOD = "getCupVariant";

    private static volatile Method cachedCupVariantMethod;
    private static volatile boolean cupVariantLookupFailed;

    private MinersDelightCupConversion() {
    }

    static ItemStack convertOutputForCopperPot(final ItemStack output) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final Method cupVariantMethod = resolveCupVariantMethod();
        if (cupVariantMethod == null) {
            return output.copy();
        }

        try {
            final Object value = cupVariantMethod.invoke(null, output.copy());
            if (value instanceof Optional<?> optional && optional.isPresent() && optional.get() instanceof ItemStack cupVariant) {
                return cupVariant.copy();
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to original output
        }

        return output.copy();
    }

    static boolean isCopperPotActive(final CraftingContext context) {
        for (final var itemProvider : context.getItemProviders()) {
            if (itemProvider instanceof CookingPotActivationMarkerProvider markerProvider) {
                if (markerProvider.isMarkerKey(COPPER_POT_TARGET_KEY) && markerProvider.isActiveForCurrentTableMarker()) {
                    return true;
                }
                continue;
            }

            if (itemProvider instanceof CombinedKitchenItemProvider combined) {
                for (final var nestedProvider : combined.providers()) {
                    if (nestedProvider instanceof CookingPotActivationMarkerProvider markerProvider
                            && markerProvider.isMarkerKey(COPPER_POT_TARGET_KEY)
                            && markerProvider.isActiveForCurrentTableMarker()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static Method resolveCupVariantMethod() {
        final Method cached = cachedCupVariantMethod;
        if (cached != null) {
            return cached;
        }
        if (cupVariantLookupFailed) {
            return null;
        }

        synchronized (MinersDelightCupConversion.class) {
            if (cachedCupVariantMethod != null) {
                return cachedCupVariantMethod;
            }
            if (cupVariantLookupFailed) {
                return null;
            }

            try {
                final Class<?> cupConversionClass = Class.forName(CUP_CONVERSION_CLASS);
                final Method cupVariantMethod = cupConversionClass.getMethod(CUP_CONVERSION_METHOD, ItemStack.class);
                cachedCupVariantMethod = cupVariantMethod;
                return cupVariantMethod;
            } catch (ReflectiveOperationException ignored) {
                cupVariantLookupFailed = true;
                return null;
            }
        }
    }
}
