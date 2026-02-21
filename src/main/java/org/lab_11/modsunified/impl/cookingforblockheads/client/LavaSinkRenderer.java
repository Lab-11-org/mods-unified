package org.lab_11.modsunified.impl.cookingforblockheads.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.cookingforblockheads.CustomizeBlocks;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

public final class LavaSinkRenderer implements BlockEntityRenderer<BlockEntity> {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation LAVA_LIQUID_MODEL_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "block/lava_sink_liquid");
    public static final ModelResourceLocation LAVA_LIQUID_MODEL = new ModelResourceLocation(
            LAVA_LIQUID_MODEL_ID,
            ModelResourceLocation.STANDALONE_VARIANT
    );

    public LavaSinkRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final BlockEntity blockEntity,
                       final float partialTicks,
                       final PoseStack poseStack,
                       final MultiBufferSource buffer,
                       final int combinedLight,
                       final int combinedOverlay) {
        final Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        final float fillRatio = CustomizeBlocks.lavaSinkFillRatio(blockEntity);
        if (fillRatio <= 0f) {
            return;
        }

        final ModelManager modelManager = Minecraft.getInstance().getModelManager();
        final BakedModel model = modelManager.getModel(LAVA_LIQUID_MODEL);
        if (model == modelManager.getMissingModel()) {
            return;
        }

        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();
        poseStack.translate(0f, 0.5f - 0.5f * fillRatio, 0f);
        poseStack.scale(1f, fillRatio, 1f);
        dispatcher.getModelRenderer().tesselateBlock(
                level,
                model,
                blockEntity.getBlockState(),
                blockEntity.getBlockPos(),
                poseStack,
                buffer.getBuffer(RenderType.translucent()),
                false,
                RANDOM,
                0L,
                Integer.MAX_VALUE
        );
        poseStack.popPose();
    }
}
