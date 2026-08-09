package org.lab_11.modsunified.impl.cookingforblockheads;

import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public final class RecipeWithStatusCompat {
    private static final Constructor<?> CONSTRUCTOR = findConstructor();
    private static final Method LOCKED_INPUTS = findMethod("lockedInputs");

    private RecipeWithStatusCompat() {
    }

    public static RecipeWithStatus create(final ResourceLocation recipeId,
                                          final ItemStack result,
                                          final List<Ingredient> missingIngredients,
                                          final int missingIngredientsMask,
                                          final List<ItemStack> lockedInputs,
                                          final List<List<ItemStack>> ingredientOptions) {
        try {
            final Object[] arguments = CONSTRUCTOR.getParameterCount() == 6
                    ? new Object[]{recipeId, result, missingIngredients, missingIngredientsMask, lockedInputs, ingredientOptions}
                    : new Object[]{recipeId, result, missingIngredients, missingIngredientsMask, lockedInputs};
            return (RecipeWithStatus) CONSTRUCTOR.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Cooking for Blockheads RecipeWithStatus API", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<ItemStack> lockedInputs(final RecipeWithStatus status) {
        try {
            return (List<ItemStack>) LOCKED_INPUTS.invoke(status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Cooking for Blockheads RecipeWithStatus API", e);
        }
    }

    private static Constructor<?> findConstructor() {
        for (final int parameterCount : new int[]{6, 5}) {
            for (final Constructor<?> constructor : RecipeWithStatus.class.getConstructors()) {
                if (constructor.getParameterCount() == parameterCount) {
                    return constructor;
                }
            }
        }
        throw new IllegalStateException("Unsupported Cooking for Blockheads RecipeWithStatus API");
    }

    private static Method findMethod(final String name) {
        try {
            return RecipeWithStatus.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Unsupported Cooking for Blockheads RecipeWithStatus API", e);
        }
    }
}
