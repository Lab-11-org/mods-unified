package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu")
abstract class KitchenMenuMixin {
    @Shadow
    private NonNullList<ItemStack> lockedInputs;
    @Shadow
    private List<RecipeWithStatus> recipesForSelection;
    @Shadow
    private int recipesForSelectionIndex;

    @Shadow
    public abstract RecipeWithStatus getSelectedRecipe();

    @Shadow
    private void updateMatrixSlots() {
        throw new AssertionError();
    }

    @Unique
    private String lab11$selectedVariantSignatureBeforeRefresh;

    @Inject(
            method = "nextRecipe",
            at = @At("TAIL"),
            remap = false
    )
    private void lab11$syncSelectedVariantLocks(final int dir, final CallbackInfo ci) {
        final RecipeWithStatus selected = getSelectedRecipe();
        if (!isIndexedRecipe(selected)) {
            return;
        }

        copySelectedLocksToMenu(selected.lockedInputs());
    }

    @ModifyVariable(
            method = "craft",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2,
            remap = false
    )
    private NonNullList<ItemStack> lab11$useSelectedVariantLocksForCraft(final NonNullList<ItemStack> incomingLockedInputs) {
        final RecipeWithStatus selected = getSelectedRecipe();
        if (!isIndexedRecipe(selected) || incomingLockedInputs == null) {
            return incomingLockedInputs;
        }

        final NonNullList<ItemStack> normalizedIncoming = normalizeLocks(incomingLockedInputs, incomingLockedInputs.size());
        copySelectedLocksToMenu(normalizedIncoming);
        return normalizedIncoming;
    }

    @Inject(
            method = "setRecipesForSelection",
            at = @At("HEAD"),
            remap = false
    )
    private void lab11$captureSelectedVariantSignature(final List<RecipeWithStatus> recipes, final CallbackInfo ci) {
        final RecipeWithStatus selected = getSelectedRecipe();
        lab11$selectedVariantSignatureBeforeRefresh = isIndexedRecipe(selected) ? signatureOf(selected) : null;
    }

    @Inject(
            method = "setRecipesForSelection",
            at = @At("RETURN"),
            remap = false
    )
    private void lab11$restoreSelectedVariantSignature(final List<RecipeWithStatus> recipes, final CallbackInfo ci) {
        final String signature = lab11$selectedVariantSignatureBeforeRefresh;
        lab11$selectedVariantSignatureBeforeRefresh = null;
        if (signature == null || recipesForSelection == null || recipesForSelection.isEmpty()) {
            return;
        }

        for (int index = 0; index < recipesForSelection.size(); index++) {
            final RecipeWithStatus candidate = recipesForSelection.get(index);
            if (!signature.equals(signatureOf(candidate))) {
                continue;
            }

            if (recipesForSelectionIndex != index) {
                recipesForSelectionIndex = index;
                updateMatrixSlots();
            }
            return;
        }
    }

    private void copySelectedLocksToMenu(final NonNullList<ItemStack> selectedLocks) {
        final NonNullList<ItemStack> normalized = normalizeLocks(selectedLocks, lockedInputs.size());
        for (int i = 0; i < lockedInputs.size(); i++) {
            lockedInputs.set(i, normalized.get(i));
        }
    }

    private static NonNullList<ItemStack> normalizeLocks(final NonNullList<ItemStack> selectedLocks, final int targetSize) {
        final NonNullList<ItemStack> normalized = NonNullList.withSize(targetSize, ItemStack.EMPTY);
        if (selectedLocks == null || selectedLocks.isEmpty()) {
            return normalized;
        }

        final int copyCount = Math.min(targetSize, selectedLocks.size());
        for (int i = 0; i < copyCount; i++) {
            final ItemStack stack = selectedLocks.get(i);
            normalized.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return normalized;
    }

    private static boolean isIndexedRecipe(final RecipeWithStatus selected) {
        if (selected == null) {
            return false;
        }

        final ResourceLocation recipeId = selected.recipeId();
        return recipeId != null
                && BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    private static String signatureOf(final RecipeWithStatus status) {
        final ResourceLocation recipeId = status.recipeId();
        final StringBuilder signature = new StringBuilder(recipeId.toString());
        final NonNullList<ItemStack> locks = status.lockedInputs();
        if (locks == null || locks.isEmpty()) {
            return signature.toString();
        }

        for (int i = 0; i < locks.size(); i++) {
            final ItemStack stack = locks.get(i);
            signature.append('|').append(i).append('=');
            if (!stack.isEmpty()) {
                signature.append(stack.getItem()).append('#').append(stack.getComponents());
            }
        }
        return signature.toString();
    }
}
