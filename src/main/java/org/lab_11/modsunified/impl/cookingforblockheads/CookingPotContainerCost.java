package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;

final class CookingPotContainerCost {
    private CookingPotContainerCost() {
    }

    static ItemStack resolveForIndexedRecipe(final Recipe<?> recipe,
                                             final RegistryAccess registryAccess,
                                             final String markerKey) {
        final boolean copperPotTarget = MinersDelightCupConversion.COPPER_POT_TARGET_KEY.equals(markerKey);
        return resolveInternal(recipe, registryAccess, copperPotTarget);
    }

    static ItemStack resolveForCraft(final Recipe<?> recipe,
                                     final RegistryAccess registryAccess,
                                     final boolean copperPotActive) {
        return resolveInternal(recipe, registryAccess, copperPotActive);
    }

    static ItemStack resolveForTooltip(final Recipe<?> recipe,
                                       final RegistryAccess registryAccess) {
        return resolveInternal(recipe, registryAccess, false);
    }

    private static ItemStack resolveInternal(final Recipe<?> recipe,
                                             final RegistryAccess registryAccess,
                                             final boolean copperPotOutputConversion) {
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
            return indexedRecipe.indexedContainerCost();
        }

        final ItemStack rawResult = recipe.getResultItem(registryAccess);
        if (rawResult.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack effectiveResult = rawResult.copy();
        ItemStack requiredContainer = invokeItemStackGetter(recipe, "getOutputContainer");

        if (copperPotOutputConversion) {
            final ItemStack convertedResult = MinersDelightCupConversion.convertOutputForCopperPot(rawResult);
            if (!convertedResult.isEmpty()) {
                effectiveResult = convertedResult;
                final ItemStack convertedContainer = convertedResult.getCraftingRemainingItem();
                if (!convertedContainer.isEmpty()) {
                    requiredContainer = convertedContainer;
                }
            }
        }

        if (requiredContainer.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final int outputCount = Math.max(1, effectiveResult.getCount());
        final int containerCountPerServing = Math.max(1, requiredContainer.getCount());
        final long totalContainerCount = (long) outputCount * containerCountPerServing;

        final ItemStack totalContainerCost = requiredContainer.copy();
        totalContainerCost.setCount((int) Math.min(Integer.MAX_VALUE, totalContainerCount));
        return totalContainerCost;
    }

    private static ItemStack invokeItemStackGetter(final Object target, final String methodName) {
        try {
            final Method getter = target.getClass().getMethod(methodName);
            final Object value = getter.invoke(target);
            if (value instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return ItemStack.EMPTY;
    }
}
