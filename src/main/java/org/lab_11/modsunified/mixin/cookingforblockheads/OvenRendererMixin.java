package org.lab_11.modsunified.mixin.cookingforblockheads;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat;
import org.lab_11.modsunified.impl.cookingforblockheads.client.DungeonOvenClientHooks;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.client.render.OvenRenderer")
abstract class OvenRendererMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedDungeonSwap;

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
                    ordinal = 0
            ),
            require = 0
    )
    private void lab11$renderDungeonDoor(final ModelBlockRenderer renderer,
                                         final BlockAndTintGetter level,
                                         final BakedModel originalModel,
                                         final BlockState state,
                                         final BlockPos pos,
                                         final PoseStack poseStack,
                                         final VertexConsumer vertexConsumer,
                                         final boolean checkSides,
                                         final RandomSource randomSource,
                                         final long seed,
                                         final int overlay,
                                         final @Coerce Object blockEntity,
                                         final float partialTicks,
                                         final PoseStack outerPoseStack,
                                         final MultiBufferSource buffer,
                                         final int combinedLight,
                                         final int combinedOverlay) {
        BakedModel resolvedModel = originalModel;
        if (isDungeonOven(state)) {
            final boolean activeDoor = isDoorActiveForRender(blockEntity, partialTicks);
            resolvedModel = DungeonOvenClientHooks.getDoorModel(activeDoor, originalModel);
            logFirstSwap();
        }
        renderer.tesselateBlock(level, resolvedModel, state, pos, poseStack, vertexConsumer, checkSides, randomSource, seed, overlay);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void lab11$renderDungeonDoorHandle(final ModelBlockRenderer renderer,
                                               final BlockAndTintGetter level,
                                               final BakedModel originalModel,
                                               final BlockState state,
                                               final BlockPos pos,
                                               final PoseStack poseStack,
                                               final VertexConsumer vertexConsumer,
                                               final boolean checkSides,
                                               final RandomSource randomSource,
                                               final long seed,
                                               final int overlay,
                                               final @Coerce Object blockEntity,
                                               final float partialTicks,
                                               final PoseStack outerPoseStack,
                                               final MultiBufferSource buffer,
                                               final int combinedLight,
                                               final int combinedOverlay) {
        BakedModel resolvedModel = originalModel;
        if (isDungeonOven(state)) {
            resolvedModel = DungeonOvenClientHooks.getHandleModel(originalModel);
            logFirstSwap();
        }
        renderer.tesselateBlock(level, resolvedModel, state, pos, poseStack, vertexConsumer, checkSides, randomSource, seed, overlay);
    }

    private static boolean isDungeonOven(final BlockState state) {
        if (state == null) {
            return false;
        }

        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return DungeonOvenCompat.DUNGEON_OVEN_ID.equals(blockId);
    }

    private static boolean isDoorActiveForRender(final Object blockEntity, final float partialTicks) {
        if (blockEntity == null) {
            return false;
        }

        try {
            final Object doorAnimator = blockEntity.getClass().getMethod("getDoorAnimator").invoke(blockEntity);
            if (doorAnimator == null) {
                return false;
            }

            final Object angleValue = doorAnimator.getClass()
                    .getMethod("getRenderAngle", float.class)
                    .invoke(doorAnimator, partialTicks);
            final Object burningValue = blockEntity.getClass().getMethod("isBurning").invoke(blockEntity);
            final double doorAngle = angleValue instanceof Number number ? number.doubleValue() : 1.0D;
            final boolean burning = burningValue instanceof Boolean value && value;
            return doorAngle < 0.3F && burning;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void logFirstSwap() {
        if (loggedDungeonSwap) {
            return;
        }
        loggedDungeonSwap = true;
        LOGGER.info("Applying dungeon oven door model swap in OvenRenderer.");
    }
}
