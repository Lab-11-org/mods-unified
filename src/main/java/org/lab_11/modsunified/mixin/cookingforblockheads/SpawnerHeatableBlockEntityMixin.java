package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.yirmiri.dungeonsdelight.common.block.entity.SpawnerHeatableBlockEntity")
abstract class SpawnerHeatableBlockEntityMixin {
    private static final String MONSTER_POT_ID = "dungeonsdelight:monster_pot";

    @Inject(
            method = "isHeated(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$requireDungeonOvenFuelHeat(final Level level,
                                                  final BlockPos pos,
                                                  final CallbackInfoReturnable<Boolean> cir) {
        if (level == null || pos == null) {
            return;
        }

        if (!((Object) this instanceof BlockEntity blockEntity)) {
            return;
        }

        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (blockId == null || !MONSTER_POT_ID.equals(blockId.toString())) {
            return;
        }

        if (!CookingPotHeatBridge.isAnyManagedOvenBelow(level, pos)) {
            return;
        }

        cir.setReturnValue(CookingPotHeatBridge.isDungeonOvenHeatedBelow(level, pos, blockEntity));
    }
}
