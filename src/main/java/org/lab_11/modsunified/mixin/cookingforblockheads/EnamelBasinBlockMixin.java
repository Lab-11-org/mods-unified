package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.lab_11.modsunified.impl.cookingforblockheads.CustomizeBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.EnamelBasinBlock")
abstract class EnamelBasinBlockMixin implements EntityBlock {
    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return CustomizeBlocks.newEnamelBasinBridge(pos, state);
    }
}
