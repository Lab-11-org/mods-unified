package org.lab_11.modsunified.impl.cookingforblockheads.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

public final class DungeonOvenClientHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PRIMARY_VARIANT = "standalone";
    private static final String[] FALLBACK_VARIANTS = {"inventory", "normal"};
    private static final ResourceLocation DOOR_MODEL_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "blocks/dungeon_oven_door");
    private static final ResourceLocation ACTIVE_DOOR_MODEL_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "blocks/dungeon_oven_door_active");
    private static final ResourceLocation HANDLE_MODEL_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "blocks/dungeon_oven_door_handle");
    public static final ModelResourceLocation DOOR_MODEL = new ModelResourceLocation(
            DOOR_MODEL_ID,
            PRIMARY_VARIANT
    );
    public static final ModelResourceLocation ACTIVE_DOOR_MODEL = new ModelResourceLocation(
            ACTIVE_DOOR_MODEL_ID,
            PRIMARY_VARIANT
    );
    public static final ModelResourceLocation HANDLE_MODEL = new ModelResourceLocation(
            HANDLE_MODEL_ID,
            PRIMARY_VARIANT
    );

    private static boolean registered;
    private static boolean warnedMissingDoorModel;
    private static boolean warnedMissingHandleModel;

    private DungeonOvenClientHooks() {
    }

    public static void register(final IEventBus modEventBus) {
        if (registered) {
            return;
        }

        registered = true;
        modEventBus.addListener(DungeonOvenClientHooks::onRegisterAdditional);
        LOGGER.info("Registered dungeon oven client hook listeners.");
    }

    private static void onRegisterAdditional(final ModelEvent.RegisterAdditional event) {
        event.register(DOOR_MODEL);
        event.register(ACTIVE_DOOR_MODEL);
        event.register(HANDLE_MODEL);
        LOGGER.info("Registered additional dungeon oven door models.");
    }

    public static BakedModel getDoorModel(final boolean active, final BakedModel fallback) {
        final BakedModel model = active
                ? resolveModel(ACTIVE_DOOR_MODEL_ID, ACTIVE_DOOR_MODEL)
                : resolveModel(DOOR_MODEL_ID, DOOR_MODEL);
        if (model == null) {
            warnMissingDoorRef(active);
            return fallback;
        }

        return model;
    }

    public static BakedModel getHandleModel(final BakedModel fallback) {
        final BakedModel model = resolveModel(HANDLE_MODEL_ID, HANDLE_MODEL);
        if (model == null) {
            warnMissingHandleRef();
            return fallback;
        }

        return model;
    }

    private static BakedModel resolveModel(final ResourceLocation modelPath, final ModelResourceLocation primaryModelId) {
        final ModelManager modelManager = Minecraft.getInstance().getModelManager();
        BakedModel model = findModel(modelManager, primaryModelId);
        if (model != null) {
            return model;
        }

        for (final String variant : FALLBACK_VARIANTS) {
            model = findModel(modelManager, new ModelResourceLocation(modelPath, variant));
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    private static BakedModel findModel(final ModelManager modelManager, final ModelResourceLocation modelId) {
        final BakedModel model = modelManager.getModel(modelId);
        return model == modelManager.getMissingModel() ? null : model;
    }

    private static void warnMissingDoorRef(final boolean active) {
        if (warnedMissingDoorModel) {
            return;
        }
        warnedMissingDoorModel = true;
        LOGGER.warn("Dungeon oven {} door model is unresolved; using fallback CFBH black door model.",
                active ? "active" : "inactive");
    }

    private static void warnMissingHandleRef() {
        if (warnedMissingHandleModel) {
            return;
        }
        warnedMissingHandleModel = true;
        LOGGER.warn("Dungeon oven door handle model is unresolved; using fallback CFBH black handle model.");
    }
}
