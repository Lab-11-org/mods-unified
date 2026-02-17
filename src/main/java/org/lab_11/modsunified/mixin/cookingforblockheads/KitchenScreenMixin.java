package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.blay09.mods.balm.mixin.AbstractContainerScreenAccessor;
import net.minecraft.resources.ResourceLocation;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen")
abstract class KitchenScreenMixin {
    private static final int FEEDBACK_LABEL_Y_OFFSET = 10;
    private static final String CRAFT_MATRIX_FAKE_SLOT_CLASS =
            "net.blay09.mods.cookingforblockheads.menu.slot.CraftMatrixFakeSlot";

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

    @Redirect(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/blay09/mods/cookingforblockheads/menu/slot/CraftMatrixFakeSlot;isLocked()Z"
            ),
            remap = false
    )
    private boolean lab11$hideIndexedRecipeLockOverlay(final Object slot) {
        if (isIndexedRecipeSelected()) {
            return false;
        }
        return invokeBooleanNoArg(slot, "isLocked");
    }

    @Inject(
            method = "mouseClicked",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$disableIndexedRecipeLockClick(final double mouseX,
                                                     final double mouseY,
                                                     final int button,
                                                     final CallbackInfoReturnable<Boolean> cir) {
        if (button != 1 || !isIndexedRecipeSelected()) {
            return;
        }

        final Object hoveredSlot = resolveHoveredSlot();
        if (isCraftMatrixSlot(hoveredSlot)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "mouseScrolled",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$disableIndexedRecipeLockScroll(final double mouseX,
                                                      final double mouseY,
                                                      final double deltaX,
                                                      final double deltaY,
                                                      final CallbackInfoReturnable<Boolean> cir) {
        if (deltaY == 0 || !isIndexedRecipeSelected()) {
            return;
        }

        final Object hoveredSlot = resolveHoveredSlot();
        if (!isCraftMatrixSlot(hoveredSlot)) {
            return;
        }

        final Object visibleStacks = invokeNoArg(hoveredSlot, "getVisibleStacks");
        if (visibleStacks instanceof List<?> stacks && stacks.size() > 1) {
            cir.setReturnValue(true);
        }
    }

    private boolean isIndexedRecipeSelected() {
        final Object menu = invokeNoArg(this, "getMenu");
        if (menu == null) {
            return false;
        }

        final Object selectedRecipeWithStatus = invokeNoArg(menu, "getSelectedRecipe");
        if (selectedRecipeWithStatus == null) {
            return false;
        }

        final Object recipeIdObject = invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (!(recipeIdObject instanceof ResourceLocation recipeId)) {
            return false;
        }

        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    private Object resolveHoveredSlot() {
        try {
            return ((AbstractContainerScreenAccessor) this).getHoveredSlot();
        } catch (ClassCastException ignored) {
            return null;
        }
    }

    private static boolean isCraftMatrixSlot(final Object slot) {
        return slot != null && CRAFT_MATRIX_FAKE_SLOT_CLASS.equals(slot.getClass().getName());
    }

    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeBooleanNoArg(final Object target, final String methodName) {
        final Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean bool && bool;
    }
}
