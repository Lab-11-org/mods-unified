package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

final class KaleidoscopeOilBridge {
    private static final String KALEIDOSCOPE_OIL_POT_ITEM_CLASS =
            "com.github.ysbbbbbb.kaleidoscopecookery.item.OilPotItem";
    private static final String KALEIDOSCOPE_OIL_POT_HAS_OIL_METHOD = "hasOil";
    private static final String KALEIDOSCOPE_OIL_POT_SHRINK_METHOD = "shrinkOilCount";

    private static final Method HAS_OIL = resolveMethod(KALEIDOSCOPE_OIL_POT_HAS_OIL_METHOD);
    private static final Method SHRINK_OIL_COUNT = resolveMethod(KALEIDOSCOPE_OIL_POT_SHRINK_METHOD);

    private KaleidoscopeOilBridge() {
    }

    static boolean isKaleidoscopeOil(final ItemStack stack) {
        final ResourceLocation itemId = itemId(stack);
        return itemId != null
                && BridgeKeys.MOD_KALEIDOSCOPE_COOKERY.equals(itemId.getNamespace())
                && BridgeKeys.ITEM_KALEIDOSCOPE_OIL.equals(itemId.getPath());
    }

    static boolean isKaleidoscopeOilPot(final ItemStack stack) {
        final ResourceLocation itemId = itemId(stack);
        return itemId != null
                && BridgeKeys.MOD_KALEIDOSCOPE_COOKERY.equals(itemId.getNamespace())
                && BridgeKeys.ITEM_KALEIDOSCOPE_OIL_POT.equals(itemId.getPath());
    }

    static boolean isKaleidoscopeOilPotWithOil(final ItemStack stack) {
        if (!isKaleidoscopeOilPot(stack)) {
            return false;
        }

        if (HAS_OIL == null) {
            return true;
        }

        try {
            final Object value = HAS_OIL.invoke(null, stack);
            return value instanceof Boolean boolValue && boolValue;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    static ItemStack decrementOilPotCount(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final ItemStack updated = stack.copy();
        if (!isKaleidoscopeOilPot(updated)) {
            return updated;
        }

        if (SHRINK_OIL_COUNT == null) {
            return updated;
        }

        try {
            SHRINK_OIL_COUNT.invoke(null, updated);
            return updated;
        } catch (ReflectiveOperationException ignored) {
            return updated;
        }
    }

    private static ResourceLocation itemId(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static Method resolveMethod(final String name) {
        try {
            return Class.forName(KALEIDOSCOPE_OIL_POT_ITEM_CLASS).getMethod(name, ItemStack.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
