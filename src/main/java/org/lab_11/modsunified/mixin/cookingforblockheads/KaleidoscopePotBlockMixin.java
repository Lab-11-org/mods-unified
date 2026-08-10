package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.PotBlock")
public class KaleidoscopePotBlockMixin {
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void onUpdateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor levelAccessor, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (direction == Direction.DOWN
                && levelAccessor instanceof Level level
                && CookingPotHeatBridge.isAnyManagedOvenBelow(level, pos)) {
            Property<?> baseProp = result.getBlock().getStateDefinition().getProperty("has_base");
            if (baseProp instanceof BooleanProperty boolProp && result.getValue(boolProp)) {
                cir.setReturnValue(result.setValue(boolProp, false));
            }
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void lab11$tryIgniteBeforeInteraction(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult,
                                                   CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (hand == InteractionHand.OFF_HAND || level.isClientSide) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && CookingPotHeatBridge.shouldIgniteForInteraction(be)) {
            CookingPotHeatBridge.tryIgniteManagedOvenForPot(be);
        }
    }

}
