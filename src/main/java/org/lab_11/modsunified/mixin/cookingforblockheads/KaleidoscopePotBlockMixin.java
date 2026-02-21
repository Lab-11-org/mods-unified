package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.PotBlock")
public class KaleidoscopePotBlockMixin {
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void onUpdateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor levelAccessor, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (direction == Direction.DOWN) {
            Property<?> baseProp = result.getBlock().getStateDefinition().getProperty("has_base");
            if (baseProp instanceof BooleanProperty boolProp && result.getValue(boolProp)) {
                if (isHeatSource(neighborState)) {
                    cir.setReturnValue(result.setValue(boolProp, false));
                }
            }
        }
    }

    private boolean isHeatSource(BlockState state) {
        String descId = state.getBlock().getDescriptionId();
        return descId.contains("oven") || descId.contains("stove");
    }
}
