package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity")
abstract class HeatableBlockEntityMixin {
    @Inject(
            method = "isHeated(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$useManagedOvenFuelHeat(final Level level,
                                              final BlockPos pos,
                                              final CallbackInfoReturnable<Boolean> cir) {
        if (level == null || pos == null) {
            return;
        }

        if (!((Object) this instanceof BlockEntity blockEntity)) {
            return;
        }

        if (!CookingPotHeatBridge.shouldUseOvenHeatForPot(level, pos)) {
            return;
        }

        if (!CookingPotHeatBridge.isAnyManagedOvenBelow(level, pos)) {
            return;
        }

        cir.setReturnValue(CookingPotHeatBridge.isAnyOvenHeatedBelow(level, pos, blockEntity));
    }
}
