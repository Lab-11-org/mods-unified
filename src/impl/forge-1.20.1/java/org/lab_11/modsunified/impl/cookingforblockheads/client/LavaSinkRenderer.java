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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.cookingforblockheads.LavaSinkCompat;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

public final class LavaSinkRenderer implements BlockEntityRenderer<BlockEntity> {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation LAVA_LIQUID_MODEL =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "block/lava_sink_liquid");

    public LavaSinkRenderer(final BlockEntityRendererProvider.Context context) {
    }

    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(LavaSinkRenderer::onRegisterAdditional);
        modEventBus.addListener(LavaSinkRenderer::onRegisterRenderers);
    }

    private static void onRegisterAdditional(final ModelEvent.RegisterAdditional event) {
        event.register(LAVA_LIQUID_MODEL);
    }

    private static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LavaSinkCompat.blockEntityType(), LavaSinkRenderer::new);
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

        final ModelManager modelManager = Minecraft.getInstance().getModelManager();
        final BakedModel model = modelManager.getModel(LAVA_LIQUID_MODEL);
        if (model == modelManager.getMissingModel()) {
            return;
        }

        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
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
                combinedOverlay
        );
    }
}
