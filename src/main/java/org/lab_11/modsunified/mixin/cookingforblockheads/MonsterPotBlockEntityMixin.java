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
@Mixin(targets = "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotBlockEntity")
abstract class MonsterPotBlockEntityMixin {
    @Redirect(
            method = "cookingTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/yirmiri/dungeonsdelight/common/block/monster_pot/MonsterPotBlockEntity;isHeated(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = false
    )
    private static boolean lab11$redirectCookingTickHeatCheck(final @Coerce Object self,
                                                              final Level level,
                                                              final BlockPos worldPosition) {
        if (level != null
                && worldPosition != null
                && CookingPotHeatBridge.isAnyManagedOvenBelow(level, worldPosition)) {
            return CookingPotHeatBridge.isDungeonOvenHeatedBelow(level, worldPosition, self);
        }

        return callNativeIsHeated(self, level, worldPosition);
    }

    @Redirect(
            method = "animationTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/yirmiri/dungeonsdelight/common/block/monster_pot/MonsterPotBlockEntity;isHeated(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = false
    )
    private static boolean lab11$redirectAnimationTickHeatCheck(final @Coerce Object self,
                                                                final Level level,
                                                                final BlockPos worldPosition) {
        if (level != null
                && worldPosition != null
                && CookingPotHeatBridge.isAnyManagedOvenBelow(level, worldPosition)) {
            return CookingPotHeatBridge.isDungeonOvenHeatedBelow(level, worldPosition, self);
        }

        return callNativeIsHeated(self, level, worldPosition);
    }

    @Inject(method = "isHeated()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void lab11$requireDungeonOvenHeat(final CallbackInfoReturnable<Boolean> cir) {
        final BlockEntity self = (BlockEntity) (Object) this;
        final Level level = self.getLevel();
        if (level == null) {
            return;
        }

        final BlockPos worldPosition = self.getBlockPos();
        if (!CookingPotHeatBridge.isAnyManagedOvenBelow(level, worldPosition)) {
            return;
        }

        cir.setReturnValue(CookingPotHeatBridge.isDungeonOvenHeatedBelow(level, worldPosition, this));
    }

    private static boolean callNativeIsHeated(final Object self, final Level level, final BlockPos worldPosition) {
        if (self == null) {
            return false;
        }

        try {
            final var method = self.getClass().getMethod("isHeated", Level.class, BlockPos.class);
            final Object value = method.invoke(self, level, worldPosition);
            return value instanceof Boolean result && result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
