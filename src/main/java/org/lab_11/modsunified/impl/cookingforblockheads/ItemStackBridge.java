package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ItemStackBridge {
    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";
    private static final String BALM_GET_HOOKS_METHOD = "getHooks";
    private static final String BALM_GET_BURN_TIME_METHOD = "getBurnTime";
    private static final String BALM_GET_REMAINING_ITEM_METHOD = "getCraftingRemainingItem";

    private static final String CFBH_CONFIG_CLASS = "net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig";
    private static final String CFBH_CONFIG_FUEL_MULTIPLIER_FIELD = "ovenFuelTimeMultiplier";
    private static final String CFBH_GET_ACTIVE_CONFIG_METHOD = "getActive";

    private static final String CFBH_IS_ITEM_FUEL_METHOD = "isItemFuel";

    private ItemStackBridge() {
    }

    public static boolean isValidOvenFuel(final ItemStack fuelStack) {
        try {
            final Class<?> ovenClass = OvenBridge.resolveCfbhOvenBlockEntityClass();
            if (ovenClass == null) {
                return false;
            }
            final Method isItemFuelMethod = ovenClass.getMethod(CFBH_IS_ITEM_FUEL_METHOD, ItemStack.class);
            final Object value = isItemFuelMethod.invoke(null, fuelStack);
            return value instanceof Boolean result && result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static int getBurnTime(final ItemStack fuelStack) {
        if (!isValidOvenFuel(fuelStack)) {
            return 0;
        }

        final int burnTime = readBurnTimeFromBalmHooks(fuelStack);
        if (burnTime > 0) {
            return burnTime;
        }

        // CFBH accepts cooking oil even when generic burn-time lookups return 0.
        return 800;
    }

    public static int scaleBurnTime(final int baseBurnTime) {
        try {
            final Class<?> configClass = Class.forName(CFBH_CONFIG_CLASS);
            final Method getActiveConfigMethod = configClass.getMethod(CFBH_GET_ACTIVE_CONFIG_METHOD);
            final Object activeConfig = getActiveConfigMethod.invoke(null);
            if (activeConfig == null) {
                return Math.max(1, baseBurnTime);
            }

            final Field multiplierField = activeConfig.getClass().getField(CFBH_CONFIG_FUEL_MULTIPLIER_FIELD);
            final double multiplier = multiplierField.getDouble(activeConfig);
            return (int) Math.max(1d, baseBurnTime * multiplier);
        } catch (ReflectiveOperationException ignored) {
            return Math.max(1, baseBurnTime);
        }
    }

    public static ItemStack getCraftingRemainingItem(final ItemStack fuelStack) {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Method getHooksMethod = balmClass.getMethod(BALM_GET_HOOKS_METHOD);
            final Object balmHooks = getHooksMethod.invoke(null);
            if (balmHooks == null) {
                return ItemStack.EMPTY;
            }

            final Method getRemainingItemMethod =
                    balmHooks.getClass().getMethod(BALM_GET_REMAINING_ITEM_METHOD, ItemStack.class);
            final Object value = getRemainingItemMethod.invoke(balmHooks, fuelStack);
            return value instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static int readBurnTimeFromBalmHooks(final ItemStack fuelStack) {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Method getHooksMethod = balmClass.getMethod(BALM_GET_HOOKS_METHOD);
            final Object balmHooks = getHooksMethod.invoke(null);
            if (balmHooks == null) {
                return 0;
            }

            final Method getBurnTimeMethod = balmHooks.getClass().getMethod(BALM_GET_BURN_TIME_METHOD, ItemStack.class);
            final Object value = getBurnTimeMethod.invoke(balmHooks, fuelStack);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
