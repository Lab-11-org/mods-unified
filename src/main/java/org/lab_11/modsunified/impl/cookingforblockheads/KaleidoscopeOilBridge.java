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

    private static volatile Class<?> cachedOilPotItemClass;
    private static volatile boolean oilPotItemClassInitialized;

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

        final Class<?> oilPotItemClass = oilPotItemClass();
        if (oilPotItemClass == null) {
            return true;
        }

        try {
            final Method hasOilMethod = oilPotItemClass.getMethod(KALEIDOSCOPE_OIL_POT_HAS_OIL_METHOD, ItemStack.class);
            final Object value = hasOilMethod.invoke(null, stack);
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

        final Class<?> oilPotItemClass = oilPotItemClass();
        if (oilPotItemClass == null) {
            return updated;
        }

        try {
            final Method shrinkMethod = oilPotItemClass.getMethod(KALEIDOSCOPE_OIL_POT_SHRINK_METHOD, ItemStack.class);
            shrinkMethod.invoke(null, updated);
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

    private static Class<?> oilPotItemClass() {
        if (oilPotItemClassInitialized) {
            return cachedOilPotItemClass;
        }

        Class<?> resolved = null;
        try {
            resolved = Class.forName(KALEIDOSCOPE_OIL_POT_ITEM_CLASS);
        } catch (ClassNotFoundException ignored) {
            // Optional dependency is absent.
        }
        cachedOilPotItemClass = resolved;
        oilPotItemClassInitialized = true;
        return resolved;
    }
}
