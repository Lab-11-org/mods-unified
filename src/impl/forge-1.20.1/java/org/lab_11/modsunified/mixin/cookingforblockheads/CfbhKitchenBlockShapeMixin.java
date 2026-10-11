package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cooking for Blockheads 16.x (Forge 1.20.1) kitchen blocks use collision height 15/16.
 * NeoForge CFBH 21.x raised the same boxes to 16/16 so pots seat flush on oven cooktops.
 * Match that height on Forge so Kaleidoscope/Delight pots no longer float above ovens.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.block.BlockKitchen")
abstract class CfbhKitchenBlockShapeMixin {
    @Unique
    private static final VoxelShape LAB11$SHAPE_X = Block.box(0.5, 0.0, 0.0, 15.5, 16.0, 16.0);
    @Unique
    private static final VoxelShape LAB11$SHAPE_Z = Block.box(0.0, 0.0, 0.5, 16.0, 16.0, 15.5);

    @Inject(method = {"getShape", "m_5940_"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void lab11$fullHeightKitchenShape(final BlockState state,
                                              final BlockGetter world,
                                              final BlockPos pos,
                                              final CollisionContext context,
                                              final CallbackInfoReturnable<VoxelShape> cir) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        final Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        cir.setReturnValue(facing.getAxis() == Direction.Axis.X ? LAB11$SHAPE_X : LAB11$SHAPE_Z);
    }
}
