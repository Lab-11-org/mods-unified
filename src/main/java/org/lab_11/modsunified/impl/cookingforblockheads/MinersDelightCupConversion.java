package org.lab_11.modsunified.impl.cookingforblockheads;

import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.blay09.mods.cookingforblockheads.kitchen.CombinedKitchenItemProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

final class MinersDelightCupConversion {
    static final String COPPER_POT_TARGET_KEY = BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT;
    private static final String DATAMAP_CUP_CONVERSION_CLASS =
            "com.sammy.minersdelight.content.data.CupConversionDataMap";
    private static final String DATAMAP_CUP_CONVERSION_METHOD = "getCupVariant";
    private static final String LEGACY_CUP_CONVERSION_CLASS =
            "com.sammy.minersdelight.logic.CupConversionReloadListener";
    private static final String LEGACY_BOWL_TO_CUP_FIELD = "BOWL_TO_CUP";

    private static volatile Method cachedCupVariantMethod;
    private static volatile Field cachedLegacyCupVariantMapField;
    private static volatile boolean cupVariantMethodLookupFailed;
    private static volatile boolean legacyCupVariantLookupFailed;

    private MinersDelightCupConversion() {
    }

    static ItemStack convertOutputForCopperPot(final ItemStack output) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final Method cupVariantMethod = resolveCupVariantMethod();
        if (cupVariantMethod != null) {
            try {
                final Object value = cupVariantMethod.invoke(null, output.copy());
                if (value instanceof Optional<?> optional && optional.isPresent() && optional.get() instanceof ItemStack cupVariant) {
                    return applyDataMapCupOutputCount(cupVariant);
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to legacy conversion lookup.
            }
        }

        final ItemStack legacyConverted = tryLegacyCupConversion(output);
        if (!legacyConverted.isEmpty()) {
            return legacyConverted;
        }

        return output.copy();
    }

    private static ItemStack applyDataMapCupOutputCount(final ItemStack cupVariant) {
        final ItemStack converted = cupVariant.copy();
        // Mirror MinersDelight CopperPotBlockEntity behavior: cup variant output gets +1 serving.
        converted.grow(1);
        return converted;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack tryLegacyCupConversion(final ItemStack output) {
        final Field legacyMapField = resolveLegacyCupVariantMapField();
        if (legacyMapField == null) {
            return ItemStack.EMPTY;
        }

        try {
            final Object mapValue = legacyMapField.get(null);
            if (!(mapValue instanceof Map<?, ?> cupMap)) {
                return ItemStack.EMPTY;
            }

            final Object cupItemObject = ((Map<Object, Object>) cupMap).get(output.getItem());
            if (!(cupItemObject instanceof Item cupItem)) {
                return ItemStack.EMPTY;
            }

            final int convertedCount = Math.min(
                    output.getCount() * 2,
                    cupItem.getMaxStackSize(cupItem.getDefaultInstance())
            );
            return new ItemStack(cupItem, Math.max(1, convertedCount));
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
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
        if (cupVariantMethodLookupFailed) {
            return null;
        }

        synchronized (MinersDelightCupConversion.class) {
            if (cachedCupVariantMethod != null) {
                return cachedCupVariantMethod;
            }
            if (cupVariantMethodLookupFailed) {
                return null;
            }

            try {
                final Class<?> cupConversionClass = Class.forName(DATAMAP_CUP_CONVERSION_CLASS);
                final Method cupVariantMethod = cupConversionClass.getMethod(DATAMAP_CUP_CONVERSION_METHOD, ItemStack.class);
                cachedCupVariantMethod = cupVariantMethod;
                return cupVariantMethod;
            } catch (ReflectiveOperationException ignored) {
                // This runtime might be on the legacy 1.20 conversion path.
                cupVariantMethodLookupFailed = true;
                return null;
            }
        }
    }

    private static Field resolveLegacyCupVariantMapField() {
        final Field cached = cachedLegacyCupVariantMapField;
        if (cached != null) {
            return cached;
        }
        if (legacyCupVariantLookupFailed) {
            return null;
        }

        synchronized (MinersDelightCupConversion.class) {
            if (cachedLegacyCupVariantMapField != null) {
                return cachedLegacyCupVariantMapField;
            }
            if (legacyCupVariantLookupFailed) {
                return null;
            }

            try {
                final Class<?> legacyClass = Class.forName(LEGACY_CUP_CONVERSION_CLASS);
                final Field bowlToCupField = legacyClass.getField(LEGACY_BOWL_TO_CUP_FIELD);
                cachedLegacyCupVariantMapField = bowlToCupField;
                return bowlToCupField;
            } catch (ReflectiveOperationException ignored) {
                legacyCupVariantLookupFailed = true;
                return null;
            }
        }
    }
}
