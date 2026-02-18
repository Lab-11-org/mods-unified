package org.lab_11.modsunified.mixin.cookingforblockheads;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen")
abstract class KitchenScreenMixin {
    private static final int FEEDBACK_LABEL_Y_OFFSET = 10;

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            ),
            index = 3,
            remap = false
    )
    private int lab11$moveKitchenFeedbackUp(final int y) {
        return y - FEEDBACK_LABEL_Y_OFFSET;
    }
}
