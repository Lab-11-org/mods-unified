package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity")
abstract class CookingPotBlockEntityMixin {
    @Redirect(
            method = "cookingTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;isHeated(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = false
    )
    private static boolean lab11$redirectCookingTickHeatCheck(final @Coerce Object self,
                                                              final Level level,
                                                              final BlockPos worldPosition) {
        if (level != null
                && worldPosition != null
                && CookingPotHeatBridge.shouldUseOvenHeatForPot(level, worldPosition)
                && CookingPotHeatBridge.isAnyManagedOvenBelow(level, worldPosition)) {
            return CookingPotHeatBridge.isAnyOvenHeatedBelow(level, worldPosition, self);
        }

        return CookingPotHeatBridge.callNativeIsHeated(self, level, worldPosition);
    }

    @Inject(method = "isHeated", at = @At("HEAD"), cancellable = true)
    private void lab11$bridgeCfbhOvenHeat(final CallbackInfoReturnable<Boolean> cir) {
        final BlockEntity self = (BlockEntity) (Object) this;
        final Level level = self.getLevel();
        if (level == null) {
            return;
        }

        final BlockPos worldPosition = self.getBlockPos();
        if (!CookingPotHeatBridge.shouldUseOvenHeatForPot(level, worldPosition)) {
            return;
        }

        if (!CookingPotHeatBridge.isAnyManagedOvenBelow(level, worldPosition)) {
            return;
        }

        cir.setReturnValue(CookingPotHeatBridge.isAnyOvenHeatedBelow(level, worldPosition, this));
    }
}
