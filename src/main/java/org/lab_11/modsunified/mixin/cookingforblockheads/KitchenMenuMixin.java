package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotIndexedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu")
abstract class KitchenMenuMixin {
    private static final String INDEXED_RECIPE_NAMESPACE = "lab_11_mods_unified";
    private static final String INDEXED_RECIPE_PATH_PREFIX = "cfbh_indexed/";

    @Shadow
    private List<?> matrixSlots;
    @Shadow
    private NonNullList<ItemStack> lockedInputs;

    @Shadow
    public abstract RecipeWithStatus getSelectedRecipe();

    @Inject(
            method = "updateMatrixSlots(Lnet/minecraft/world/item/crafting/Recipe;Lnet/blay09/mods/cookingforblockheads/crafting/RecipeWithStatus;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$renderIndexedRecipeMatrix(final Recipe<?> recipe,
                                                 final RecipeWithStatus status,
                                                 final CallbackInfo ci) {
        if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
            return;
        }

        final NonNullList<Ingredient> ingredients = recipe.getIngredients();
        final NonNullList<Ingredient> matrix = NonNullList.withSize(9, Ingredient.EMPTY);
        final boolean[] missingMatrix = new boolean[9];
        final int[] ingredientIndexMatrix = new int[9];

        final int syntheticIngredientCount = Math.max(0, indexedRecipe.syntheticIngredientCount());
        for (int i = syntheticIngredientCount; i < ingredients.size(); i++) {
            final int visibleIngredientIndex = i - syntheticIngredientCount;
            final int matrixSlot = visibleIngredientIndex < 9 ? visibleIngredientIndex : 8;
            matrix.set(matrixSlot, ingredients.get(i));
            missingMatrix[matrixSlot] = isMissingIngredient(status, i);
            ingredientIndexMatrix[matrixSlot] = i;
        }

        for (int i = 0; i < matrixSlots.size(); i++) {
            final Object matrixSlot = matrixSlots.get(i);
            if (matrixSlot == null) {
                continue;
            }

            final ItemStack lockedInput = resolveLockedInput(status, ingredientIndexMatrix[i]);
            invokeSetIngredient(matrixSlot, ingredientIndexMatrix[i], matrix.get(i), lockedInput);
            invokeSetMissing(matrixSlot, missingMatrix[i]);
        }

        ci.cancel();
    }

    private static boolean isMissingIngredient(final Object status, final int ingredientIndex) {
        if (status == null || ingredientIndex < 0 || ingredientIndex >= Integer.SIZE) {
            return false;
        }

        try {
            final Method missingMaskGetter = status.getClass().getMethod("missingIngredientsMask");
            final Object value = missingMaskGetter.invoke(status);
            if (value instanceof Integer missingMask) {
                return (missingMask & (1 << ingredientIndex)) != 0;
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep default false if CFBH internals change.
        }

        return false;
    }

    private static ItemStack resolveLockedInput(final Object status, final int ingredientIndex) {
        if (status == null || ingredientIndex < 0) {
            return ItemStack.EMPTY;
        }

        try {
            final Method lockedInputsGetter = status.getClass().getMethod("lockedInputs");
            final Object value = lockedInputsGetter.invoke(status);
            if (!(value instanceof List<?> lockedInputs) || ingredientIndex >= lockedInputs.size()) {
                return ItemStack.EMPTY;
            }

            final Object lockedInput = lockedInputs.get(ingredientIndex);
            if (lockedInput instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep default empty if CFBH internals change.
        }

        return ItemStack.EMPTY;
    }

    private static void invokeSetIngredient(final Object matrixSlot,
                                            final int ingredientIndex,
                                            final Ingredient ingredient,
                                            final ItemStack lockedInput) {
        try {
            final Method setIngredient = matrixSlot.getClass().getMethod(
                    "setIngredient",
                    int.class,
                    Ingredient.class,
                    ItemStack.class
            );
            setIngredient.invoke(matrixSlot, ingredientIndex, ingredient, lockedInput);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static void invokeSetMissing(final Object matrixSlot, final boolean missing) {
        try {
            final Method setMissing = matrixSlot.getClass().getMethod("setMissing", boolean.class);
            setMissing.invoke(matrixSlot, missing);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    @Inject(
            method = "nextRecipe",
            at = @At("TAIL"),
            remap = false
    )
    private void lab11$syncLockedInputsWithSelectedVariant(final int dir, final CallbackInfo ci) {
        final RecipeWithStatus selected = getSelectedRecipe();
        if (selected == null || !isIndexedRecipe(selected)) {
            return;
        }

        lab11$applySelectedIndexedVariant(selected);
    }

    @Unique
    private void lab11$applySelectedIndexedVariant(final RecipeWithStatus selected) {
        final List<ItemStack> selectedLocks = selected.lockedInputs();
        for (int i = 0; i < lockedInputs.size(); i++) {
            if (i < selectedLocks.size()) {
                final ItemStack stack = selectedLocks.get(i);
                lockedInputs.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            } else {
                lockedInputs.set(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isIndexedRecipe(final RecipeWithStatus status) {
        final var recipeId = status.recipeId();
        return recipeId != null
                && INDEXED_RECIPE_NAMESPACE.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(INDEXED_RECIPE_PATH_PREFIX);
    }
}
