package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotHeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.sammy.minersdelight.content.block.copper_pot.CopperPotBlock")
abstract class CopperPotBlockMixin {
    @Redirect(
            method = "animateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/sammy/minersdelight/content/block/copper_pot/CopperPotBlockEntity;isHeated()Z"
            ),
            remap = false
    )
    private boolean lab11$redirectAnimateTickHeatCheck(final @Coerce Object potBlockEntity) {
        if (potBlockEntity instanceof BlockEntity blockEntity) {
            final Level level = blockEntity.getLevel();
            final BlockPos worldPosition = blockEntity.getBlockPos();
            if (level != null
                    && worldPosition != null
                    && CookingPotHeatBridge.shouldUseOvenHeatForPot(level, worldPosition)
                    && CookingPotHeatBridge.isAnyManagedOvenBelow(level, worldPosition)) {
                return CookingPotHeatBridge.isAnyOvenHeatedBelow(level, worldPosition, potBlockEntity);
            }
        }

        return callNativeIsHeatedNoArg(potBlockEntity);
    }

    private static boolean callNativeIsHeatedNoArg(final Object self) {
        if (self == null) {
            return false;
        }

        try {
            final var method = self.getClass().getMethod("isHeated");
            final Object value = method.invoke(self);
            return value instanceof Boolean result && result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
