package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity"
})
public abstract class KaleidoscopePotBlockEntityMixin {

    @Inject(method = "hasHeatSource", at = @At("HEAD"), cancellable = true, remap = false)
    private void lab11$bridgeCfbhOvenHeat(Level level, CallbackInfoReturnable<Boolean> cir) {
        final BlockPos pos = ((BlockEntity) (Object) this).getBlockPos();
        if (!CookingPotHeatBridge.isAnyManagedOvenBelow(level, pos)) {
            return;
        }
        cir.setReturnValue(CookingPotHeatBridge.isAnyOvenHeatedBelow(level, pos, this));
    }

    // PotBlockEntity only: ignite oven after oil is placed
    @Inject(method = "onPlaceOil", at = @At("RETURN"), remap = false, require = 0)
    private void lab11$tryIgniteAfterOilPlacement(Level level, LivingEntity user, ItemStack stack,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide && Boolean.TRUE.equals(cir.getReturnValue())) {
            CookingPotHeatBridge.tryIgniteManagedOvenForPot((BlockEntity) (Object) this);
        }
    }

    // StockpotBlockEntity only: ignite oven after soup base is added
    @Inject(method = "addSoupBase", at = @At("RETURN"), remap = false, require = 0)
    private void lab11$tryIgniteAfterSoupBase(Level level, LivingEntity user, ItemStack stack,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide && Boolean.TRUE.equals(cir.getReturnValue())) {
            CookingPotHeatBridge.tryIgniteManagedOvenForPot((BlockEntity) (Object) this);
        }
    }

    // Both: ignite oven after ingredient is added
    @Inject(method = "addIngredient", at = @At("RETURN"), remap = false)
    private void lab11$tryIgniteAfterIngredient(Level level, LivingEntity user, ItemStack stack,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide && Boolean.TRUE.equals(cir.getReturnValue())) {
            CookingPotHeatBridge.tryIgniteManagedOvenForPot((BlockEntity) (Object) this);
        }
    }

    // StockpotBlockEntity only: ignite oven after lid is placed
    @Inject(method = "onLitClick", at = @At("RETURN"), remap = false, require = 0)
    private void lab11$tryIgniteAfterLidPlacement(Level level, LivingEntity user, ItemStack stack,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide && Boolean.TRUE.equals(cir.getReturnValue())) {
            CookingPotHeatBridge.tryIgniteManagedOvenForPot((BlockEntity) (Object) this);
        }
    }
}
