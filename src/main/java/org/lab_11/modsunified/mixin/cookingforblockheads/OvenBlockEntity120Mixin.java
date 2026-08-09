package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity")
abstract class OvenBlockEntity120Mixin {
    @Inject(
            method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD")
    )
    private void lab11$igniteFromPotCookingRequest(final Level level,
                                                   final BlockPos pos,
                                                   final BlockState state,
                                                   final CallbackInfo ci) {
        CookingPotHeatBridge.tickOvenForPotHeat(level, pos, state);
    }
}
