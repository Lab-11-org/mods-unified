package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.lab_11.modsunified.impl.cookingforblockheads.LegacyRecipeBookCraftBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.RecipeBookMenu")
abstract class RecipeBookMenuLegacyMixin {
    @Inject(method = "findAndSendItemList", at = @At("HEAD"), remap = false)
    private void lab11$diagnoseLegacyKitchen(final CallbackInfo ci) {
        LegacyRecipeBookCraftBridge.restoreLegacyRecipes(this);
        LegacyRecipeBookCraftBridge.logKitchenDiagnosticsOnce(this);
    }

    @Inject(
            method = "tryCraft(Lnet/minecraft/world/item/ItemStack;Lnet/blay09/mods/cookingforblockheads/registry/FoodRecipeType;Lnet/minecraft/core/NonNullList;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$handleIndexedCookingPotCraft(final ItemStack outputItem,
                                                    @Coerce final Object recipeType,
                                                    final NonNullList<ItemStack> matrixStacks,
                                                    final boolean quickMove,
                                                    final CallbackInfo ci) {
        if (LegacyRecipeBookCraftBridge.tryHandleRecipeBookCraft(this, outputItem, recipeType, matrixStacks)) {
            ci.cancel();
        }
    }
}
