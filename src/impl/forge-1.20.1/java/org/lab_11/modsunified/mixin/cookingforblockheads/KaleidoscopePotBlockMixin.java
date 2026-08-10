package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
        "com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.PotBlock",
        "com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock"
})
abstract class KaleidoscopePotBlockMixin {
    @Inject(method = {"getStateForPlacement", "m_5573_"}, at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void lab11$hideStandWhenPlacedOnOven(final BlockPlaceContext context,
                                                  final CallbackInfoReturnable<BlockState> cir) {
        if (CookingPotHeatBridge.isAnyManagedOvenBelow(context.getLevel(), context.getClickedPos())) {
            cir.setReturnValue(withoutStand(cir.getReturnValue()));
        }
    }

    @Inject(method = {"updateShape", "m_7417_"}, at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void lab11$updateStandForOven(final BlockState state,
                                          final Direction direction,
                                          final BlockState neighborState,
                                          final LevelAccessor levelAccessor,
                                          final BlockPos pos,
                                          final BlockPos neighborPos,
                                          final CallbackInfoReturnable<BlockState> cir) {
        if (direction == Direction.DOWN
                && levelAccessor instanceof Level level
                && CookingPotHeatBridge.isAnyManagedOvenBelow(level, pos)) {
            cir.setReturnValue(withoutStand(cir.getReturnValue()));
        }
    }

    private static BlockState withoutStand(final BlockState state) {
        if (state == null) {
            return null;
        }
        final var property = state.getBlock().getStateDefinition().getProperty("has_base");
        return property instanceof BooleanProperty hasBase ? state.setValue(hasBase, false) : state;
    }
}
