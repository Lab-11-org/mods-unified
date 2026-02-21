package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotRecipeIndexer;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu")
abstract class KitchenMenuMixin {
    @Unique
    private String lab11$selectedVariantSignatureBeforeRefresh;

    @Inject(
            method = "nextRecipe",
            at = @At("TAIL"),
            remap = false
    )
    private void lab11$syncSelectedVariantLocks(final int dir, final CallbackInfo ci) {
        final Object selected = invokeNoArg(this, "getSelectedRecipe");
        if (!isIndexedRecipe(selected)) {
            return;
        }

        copySelectedLocksToMenu(lockedInputsOf(selected));
    }

    @ModifyVariable(
            method = "craft",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2,
            remap = false
    )
    private NonNullList<ItemStack> lab11$useSelectedVariantLocksForCraft(final NonNullList<ItemStack> incomingLockedInputs) {
        final Object selected = invokeNoArg(this, "getSelectedRecipe");
        if (!isIndexedRecipe(selected) || incomingLockedInputs == null) {
            return incomingLockedInputs;
        }

        final int requiredLockSize = resolveRequiredLockSizeForCraft(selected, incomingLockedInputs.size());
        final NonNullList<ItemStack> normalizedIncoming = normalizeLocks(incomingLockedInputs, requiredLockSize);
        copySelectedLocksToMenu(normalizedIncoming);
        return normalizedIncoming;
    }

    @Inject(
            method = "setRecipesForSelection",
            at = @At("HEAD"),
            remap = false
    )
    private void lab11$captureSelectedVariantSignature(final List<?> recipes, final CallbackInfo ci) {
        final Object selected = invokeNoArg(this, "getSelectedRecipe");
        lab11$selectedVariantSignatureBeforeRefresh = isIndexedRecipe(selected) ? signatureOf(selected) : null;
    }

    @Inject(
            method = "setRecipesForSelection",
            at = @At("RETURN"),
            remap = false
    )
    private void lab11$restoreSelectedVariantSignature(final List<?> recipes, final CallbackInfo ci) {
        final String signature = lab11$selectedVariantSignatureBeforeRefresh;
        lab11$selectedVariantSignatureBeforeRefresh = null;
        final List<?> recipesForSelection = recipesForSelection();
        if (signature == null || recipesForSelection.isEmpty()) {
            return;
        }

        for (int index = 0; index < recipesForSelection.size(); index++) {
            final Object candidate = recipesForSelection.get(index);
            if (!signature.equals(signatureOf(candidate))) {
                continue;
            }

            if (recipesForSelectionIndex() != index) {
                setRecipesForSelectionIndex(index);
                invokeNoArg(this, "updateMatrixSlots");
            }
            return;
        }
    }

    @Unique
    private NonNullList<ItemStack> lockedInputs() {
        final Object value = readFieldValue(this, "lockedInputs");
        if (value instanceof NonNullList<?> list) {
            @SuppressWarnings("unchecked")
            final NonNullList<ItemStack> cast = (NonNullList<ItemStack>) list;
            return cast;
        }
        return NonNullList.create();
    }

    @Unique
    private List<?> recipesForSelection() {
        final Object value = readFieldValue(this, "recipesForSelection");
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    @Unique
    private int recipesForSelectionIndex() {
        final Object value = readFieldValue(this, "recipesForSelectionIndex");
        if (value instanceof Integer index) {
            return index;
        }
        return 0;
    }

    @Unique
    private void setRecipesForSelectionIndex(final int index) {
        writeFieldValue(this, "recipesForSelectionIndex", index);
    }

    @Unique
    private void copySelectedLocksToMenu(final NonNullList<ItemStack> selectedLocks) {
        final NonNullList<ItemStack> lockedInputs = lockedInputs();
        final NonNullList<ItemStack> normalized = normalizeLocks(selectedLocks, lockedInputs.size());
        if (lockedInputs.size() != selectedLocks.size()) {
            writeFieldValue(this, "lockedInputs", normalizeLocks(selectedLocks, selectedLocks.size()));
            return;
        }
        for (int i = 0; i < lockedInputs.size(); i++) {
            lockedInputs.set(i, normalized.get(i));
        }
    }

    @Unique
    private static int resolveRequiredLockSizeForCraft(final Object selectedRecipeStatus,
                                                       final int defaultSize) {
        final ResourceLocation recipeId = recipeIdOf(selectedRecipeStatus);
        final Recipe<?> recipe = CookingPotRecipeIndexer.findIndexedRecipe(recipeId);
        if (recipe == null) {
            return defaultSize;
        }

        return Math.max(defaultSize, recipe.getIngredients().size());
    }

    @Unique
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

    @Unique
    private static boolean isIndexedRecipe(final Object selected) {
        if (selected == null) {
            return false;
        }

        final ResourceLocation recipeId = recipeIdOf(selected);
        return recipeId != null
                && BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    @Unique
    private static String signatureOf(final Object status) {
        final ResourceLocation recipeId = recipeIdOf(status);
        if (recipeId == null) {
            return "";
        }

        final StringBuilder signature = new StringBuilder(recipeId.toString());
        final NonNullList<ItemStack> locks = lockedInputsOf(status);
        if (locks == null || locks.isEmpty()) {
            return signature.toString();
        }

        for (int i = 0; i < locks.size(); i++) {
            final ItemStack stack = locks.get(i);
            signature.append('|').append(i).append('=');
            if (!stack.isEmpty()) {
                signature.append(stack.getItem()).append('#').append(MinecraftApiCompat.stackDataKey(stack));
            }
        }
        return signature.toString();
    }

    @Unique
    private static ResourceLocation recipeIdOf(final Object status) {
        final Object value = invokeNoArg(status, "recipeId");
        if (value instanceof ResourceLocation recipeId) {
            return recipeId;
        }
        return null;
    }

    @Unique
    private static NonNullList<ItemStack> lockedInputsOf(final Object status) {
        final Object value = invokeNoArg(status, "lockedInputs");
        if (value instanceof NonNullList<?> list) {
            @SuppressWarnings("unchecked")
            final NonNullList<ItemStack> cast = (NonNullList<ItemStack>) list;
            return cast;
        }
        return NonNullList.create();
    }

    @Unique
    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }

        try {
            final var method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object readFieldValue(final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }

        final Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void writeFieldValue(final Object target, final String fieldName, final Object value) {
        if (target == null) {
            return;
        }

        final Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return;
        }

        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    @Unique
    private static Field findField(final Class<?> ownerClass, final String fieldName) {
        Class<?> current = ownerClass;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
