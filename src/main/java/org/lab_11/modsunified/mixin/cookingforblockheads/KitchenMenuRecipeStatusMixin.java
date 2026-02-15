package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.blay09.mods.cookingforblockheads.crafting.KitchenImpl;
import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.blay09.mods.cookingforblockheads.network.message.SelectionRecipesListMessage;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu")
abstract class KitchenMenuRecipeStatusMixin {
    @Shadow
    private Player player;
    @Shadow
    private KitchenImpl kitchen;
    @Shadow
    private NonNullList<ItemStack> lockedInputs;
    @Shadow
    private Comparator<RecipeWithStatus> currentSorting;
    @Shadow
    private List<RecipeWithStatus> recipesForSelection;

    @Shadow
    private Collection<RecipeHolder<Recipe<?>>> getRecipesFor(ItemStack resultItem) {
        throw new AssertionError();
    }

    @Inject(
            method = "broadcastRecipesForResultItem",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$freezeLockedInputsPerVariant(final ItemStack resultItem, final CallbackInfo ci) {
        final List<RecipeWithStatus> result = new ArrayList<>();
        final CraftingContext context = new CraftingContext(kitchen, player);
        final Collection<RecipeHolder<Recipe<?>>> recipesForResult = getRecipesFor(resultItem);

        for (final RecipeHolder<Recipe<?>> recipe : recipesForResult) {
            final ItemStack recipeResultItem = recipe.value().getResultItem(player.level().registryAccess());
            final NonNullList<ItemStack> operationLocks = copyLocks(lockedInputs);
            final var operation = context.createOperation(recipe).withLockedInputs(operationLocks).prepare();

            result.add(new RecipeWithStatus(
                    recipe.id(),
                    recipeResultItem,
                    operation.getMissingIngredients(),
                    operation.getMissingIngredientsMask(),
                    copyLocks(operation.getLockedInputs())
            ));
        }

        result.sort(currentSorting);
        recipesForSelection = result;
        Balm.getNetworking().sendTo(player, new SelectionRecipesListMessage(result));
        ci.cancel();
    }

    private static NonNullList<ItemStack> copyLocks(final List<ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return NonNullList.create();
        }

        final NonNullList<ItemStack> copy = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) {
            final ItemStack stack = source.get(i);
            copy.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }
}
