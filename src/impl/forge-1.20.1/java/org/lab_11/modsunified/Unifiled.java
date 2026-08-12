package org.lab_11.modsunified;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeTarget;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeCatalog;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotContainerTooltipBridge;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotProcessorCapability;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotRecipeIndexer;
import org.lab_11.modsunified.impl.cookingforblockheads.DungeonsDelightCupRecipeMirror;
import org.lab_11.modsunified.impl.cookingforblockheads.LavaSinkCompat;
import org.lab_11.modsunified.impl.cookingforblockheads.LegacyKitchenConnectorCompat;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.ModRuntimeBindings;
import org.lab_11.modsunified.impl.platform.RuntimeBindings;
import org.slf4j.Logger;

import java.util.List;

@Mod(Unifiled.MOD_ID)
public final class Unifiled {
    public static final String MOD_ID = "lab_11_mods_unified";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModRuntimeBindings RUNTIME = RuntimeBindings.active();

    private List<CookingPotBridgeTarget> activeCookingPotTargets = List.of();

    public Unifiled() {
        this(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private Unifiled(final IEventBus modEventBus) {
        LOGGER.info("Bootstrapping runtime profile {} (loader={}, minecraft={}, loaderVersion={}).",
                RUNTIME.profile().id(),
                RUNTIME.profile().loader(),
                RUNTIME.profile().minecraftVersion(),
                RUNTIME.profile().loaderVersion());
        registerLavaSinkCompat(modEventBus);
        registerDungeonOvenCompat(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCapabilities);
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.addListener(this::onClientRecipesUpdated);
            MinecraftForge.EVENT_BUS.addListener(this::onItemTooltip);
            MinecraftForge.EVENT_BUS.addListener(this::onRenderTooltipPre);
        }
    }

    private void registerLavaSinkCompat(final IEventBus modEventBus) {
        if (!LoaderApiCompat.isModLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS)) {
            return;
        }

        LavaSinkCompat.register(modEventBus);
    }

    private void registerDungeonOvenCompat(final IEventBus modEventBus) {
        if (!LoaderApiCompat.isModLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS)
                || !LoaderApiCompat.isModLoaded(BridgeKeys.MOD_DUNGEONS_DELIGHT)) {
            return;
        }

        try {
            final Class<?> compatClass = Class.forName(RUNTIME.dungeonOvenCompatClassName());
            compatClass.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            LOGGER.info("Registered LAB-11 mods-unified dungeon oven compatibility.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified dungeon oven compatibility.", e);
        }
    }

    private void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        if (!isCookingForBlockheadsLoaded()) {
            return;
        }

        CookingPotProcessorCapability.register(event, activeCookingPotTargets);
        LOGGER.info("Registered LAB-11 mods-unified KitchenItemProcessor capability hook.");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(this::registerCompatIfSupported);
    }

    private void registerCompatIfSupported() {
        if (!isCookingForBlockheadsLoaded()) {
            LOGGER.info("Skipping cooking-pot bridge because Cooking for Blockheads is not loaded.");
            return;
        }

        refreshActiveCookingPotTargets();
        if (LegacyKitchenConnectorCompat.register(activeCookingPotTargets)) {
            LOGGER.info("Registered legacy CFBH kitchen connectors and tagged item providers.");
        } else {
            LOGGER.warn("Could not register legacy CFBH kitchen connectors and tagged item providers.");
        }
        if (activeCookingPotTargets.isEmpty()) {
            LOGGER.info("Skipping cooking-pot bridge because no supported external cooking-pot mods are currently loaded.");
            return;
        }
        registerBalmRecipeSyncListeners();
    }

    private void registerBalmRecipeSyncListeners() {
        try {
            final Class<?> bridgeClass = Class.forName(RUNTIME.recipeSyncBridgeClassName());
            bridgeClass.getMethod("registerListeners", List.class).invoke(null, activeCookingPotTargets);
            LOGGER.info("Registered LAB-11 mods-unified Balm recipe sync listeners for {}.",
                    CookingPotBridgeCatalog.describeTargets(activeCookingPotTargets));
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified Balm recipe sync listeners for cooking-pot bridges.", e);
        }
    }

    private void onClientRecipesUpdated(final RecipesUpdatedEvent event) {
        if (!isCookingForBlockheadsLoaded()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            LOGGER.warn("Skipping client recipe reinjection because the client connection is unavailable.");
            return;
        }

        injectCookingPotRecipesIntoCookingForBlockheads(
                event.getRecipeManager(),
                minecraft.getConnection().registryAccess(),
                "forge_client_recipes_updated"
        );
    }

    private void onItemTooltip(final ItemTooltipEvent event) {
        CookingPotContainerTooltipBridge.appendTooltip(event);
    }

    private void onRenderTooltipPre(final RenderTooltipEvent.Pre event) {
        CookingPotContainerTooltipBridge.adjustTooltipPosition(event);
    }

    private void injectCookingPotRecipesIntoCookingForBlockheads(final RecipeManager recipeManager,
                                                                 final net.minecraft.core.RegistryAccess registryAccess,
                                                                 final String source) {
        if (!isCookingForBlockheadsLoaded()) {
            return;
        }

        refreshActiveCookingPotTargets();
        if (activeCookingPotTargets.isEmpty()) {
            return;
        }

        DungeonsDelightCupRecipeMirror.injectMirroredRecipes(recipeManager, registryAccess, source);
        CookingPotRecipeIndexer.injectRecipes(recipeManager, registryAccess, source, activeCookingPotTargets);
    }

    private void refreshActiveCookingPotTargets() {
        activeCookingPotTargets = CookingPotBridgeCatalog.resolveActiveTargets(LOGGER);
    }

    private static boolean isCookingForBlockheadsLoaded() {
        return LoaderApiCompat.isModLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS);
    }
}
