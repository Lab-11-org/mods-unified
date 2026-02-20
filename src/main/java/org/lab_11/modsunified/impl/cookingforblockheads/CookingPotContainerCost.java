package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.lang.reflect.Field;
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
            final boolean indexedCopperPotTarget =
                    MinersDelightCupConversion.COPPER_POT_TARGET_KEY.equals(indexedRecipe.targetKey());
            return resolveInternal(
                    indexedRecipe.delegateRecipe(),
                    registryAccess,
                    copperPotOutputConversion || indexedCopperPotTarget
            );
        }

        final ItemStack rawResult = recipe.getResultItem(registryAccess);
        if (rawResult.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack effectiveResult = rawResult.copy();
        ItemStack requiredContainer = resolveDeclaredContainer(recipe, registryAccess);

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
            requiredContainer = effectiveResult.getCraftingRemainingItem();
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

    private static ItemStack resolveDeclaredContainer(final Recipe<?> recipe, final RegistryAccess registryAccess) {
        ItemStack container = invokeItemStackGetter(recipe, "getOutputContainer");
        if (!container.isEmpty()) {
            return container;
        }

        container = invokeItemStackGetter(recipe, "getContainer");
        if (!container.isEmpty()) {
            return container;
        }

        container = invokeItemStackGetter(recipe, "getOutputContainer", RegistryAccess.class, registryAccess);
        if (!container.isEmpty()) {
            return container;
        }

        container = findContainerByMethodSignature(recipe);
        if (!container.isEmpty()) {
            return container;
        }

        container = findContainerByField(recipe);
        if (!container.isEmpty()) {
            return container;
        }

        return findContainerByFieldHeuristics(recipe, registryAccess);
    }

    private static ItemStack invokeItemStackGetter(final Object target, final String methodName) {
        return invokeItemStackGetter(target, methodName, null, null);
    }

    private static ItemStack invokeItemStackGetter(final Object target,
                                                   final String methodName,
                                                   final Class<?> parameterType,
                                                   final Object parameterValue) {
        try {
            final Method getter;
            final Object value;
            if (parameterType == null) {
                getter = target.getClass().getMethod(methodName);
                value = getter.invoke(target);
            } else {
                getter = target.getClass().getMethod(methodName, parameterType);
                value = getter.invoke(target, parameterValue);
            }
            if (value instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (ReflectiveOperationException ignored) {
            // Try declared method fallback for obfuscated or non-public members.
        }

        try {
            final Method getter = parameterType == null
                    ? target.getClass().getDeclaredMethod(methodName)
                    : target.getClass().getDeclaredMethod(methodName, parameterType);
            getter.setAccessible(true);
            final Object value = parameterType == null
                    ? getter.invoke(target)
                    : getter.invoke(target, parameterValue);
            if (value instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findContainerByMethodSignature(final Object target) {
        Class<?> current = target.getClass();
        while (current != null) {
            for (final Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || !ItemStack.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }

                final String methodName = method.getName().toLowerCase();
                if (!methodName.contains("container")) {
                    continue;
                }

                try {
                    method.setAccessible(true);
                    final Object value = method.invoke(target);
                    if (value instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                        return itemStack;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try next candidate.
                }
            }
            current = current.getSuperclass();
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findContainerByField(final Object target) {
        Class<?> current = target.getClass();
        while (current != null) {
            for (final Field field : current.getDeclaredFields()) {
                if (!ItemStack.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                final String fieldName = field.getName().toLowerCase();
                if (!fieldName.contains("container")) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    final Object value = field.get(target);
                    if (value instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                        return itemStack;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try next candidate.
                }
            }
            current = current.getSuperclass();
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findContainerByFieldHeuristics(final Recipe<?> recipe,
                                                            final RegistryAccess registryAccess) {
        final ItemStack resultItem = recipe.getResultItem(registryAccess);
        ItemStack candidate = ItemStack.EMPTY;

        Class<?> current = recipe.getClass();
        while (current != null) {
            for (final Field field : current.getDeclaredFields()) {
                if (!ItemStack.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    final Object value = field.get(recipe);
                    if (!(value instanceof ItemStack itemStack) || itemStack.isEmpty()) {
                        continue;
                    }
                    if (!resultItem.isEmpty() && MinecraftApiCompat.isSameItemSameData(itemStack, resultItem)) {
                        continue;
                    }

                    if (candidate.isEmpty()) {
                        candidate = itemStack;
                    } else if (!MinecraftApiCompat.isSameItemSameData(itemStack, candidate)) {
                        return candidate;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try next candidate.
                }
            }
            current = current.getSuperclass();
        }

        return candidate;
    }
}
