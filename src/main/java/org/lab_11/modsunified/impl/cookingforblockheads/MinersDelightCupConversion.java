package org.lab_11.modsunified.impl.cookingforblockheads;

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

    private static Method resolveCupVariantMethod() {
        return CupVariantMethodHolder.VALUE;
    }

    private static Field resolveLegacyCupVariantMapField() {
        return LegacyCupVariantMapFieldHolder.VALUE;
    }

    private static final class CupVariantMethodHolder {
        private static final Method VALUE = find();

        private static Method find() {
            try {
                return Class.forName(DATAMAP_CUP_CONVERSION_CLASS)
                        .getMethod(DATAMAP_CUP_CONVERSION_METHOD, ItemStack.class);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }

    private static final class LegacyCupVariantMapFieldHolder {
        private static final Field VALUE = find();

        private static Field find() {
            try {
                return Class.forName(LEGACY_CUP_CONVERSION_CLASS).getField(LEGACY_BOWL_TO_CUP_FIELD);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }
}
