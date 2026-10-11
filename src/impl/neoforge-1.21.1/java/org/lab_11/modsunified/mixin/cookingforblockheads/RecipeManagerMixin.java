package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotRecipeIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Inject(method = "byKey", at = @At("HEAD"), cancellable = true, remap = false)
    private void lab11$resolveIndexedRecipeHolder(final ResourceLocation recipeId,
                                                  final CallbackInfoReturnable<Optional<RecipeHolder<?>>> cir) {
        if (recipeId == null || !BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())) {
            return;
        }

        final RecipeManager recipeManager = (RecipeManager) (Object) this;
        final Object holder = CookingPotRecipeIndexer.findIndexedRecipeHolder(recipeManager, recipeId);
        if (holder instanceof RecipeHolder<?> recipeHolder) {
            cir.setReturnValue(Optional.of(recipeHolder));
        }
    }
}
