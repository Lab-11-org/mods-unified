package org.lab_11.modsunified;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeTarget;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeCatalog;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotContainerTooltipBridge;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotIndexedRecipe;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotKitchenHandler;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotProcessorCapability;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotRecipeIndexer;
import org.lab_11.modsunified.impl.cookingforblockheads.DungeonsDelightCupRecipeMirror;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod(Unifiled.MOD_ID)
public final class Unifiled {
    public static final String MOD_ID = "lab_11_mods_unified";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String COOKING_FOR_BLOCKHEADS_API_CLASS = "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI";
    private static final String COOKING_FOR_BLOCKHEADS_HANDLER_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler";
    private static final String LOCAL_BALM_FALLBACK_PROVIDER_BRIDGE_CLASS = "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge";
    private static final String LOCAL_BALM_RECIPE_SYNC_BRIDGE_CLASS = "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge";
    private static final String LOCAL_DUNGEON_OVEN_COMPAT_CLASS = "org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat";

    private List<CookingPotBridgeTarget> activeCookingPotTargets = List.of();

    public Unifiled(IEventBus modEventBus) {
        registerDungeonOvenCompat(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCapabilities);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(this::onClientRecipesUpdated);
            NeoForge.EVENT_BUS.addListener(this::onItemTooltip);
            NeoForge.EVENT_BUS.addListener(this::onRenderTooltipPre);
        }
    }

    private void registerDungeonOvenCompat(final IEventBus modEventBus) {
        if (!ModList.get().isLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS)
                || !ModList.get().isLoaded(BridgeKeys.MOD_DUNGEONS_DELIGHT)) {
            return;
        }

        try {
            final Class<?> compatClass = Class.forName(LOCAL_DUNGEON_OVEN_COMPAT_CLASS);
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

        refreshActiveCookingPotTargets();
        if (activeCookingPotTargets.isEmpty()) {
            return;
        }

        CookingPotProcessorCapability.register(event, activeCookingPotTargets);
        LOGGER.info("Registered LAB-11 mods-unified KitchenItemProcessor capabilities for {}.",
                CookingPotBridgeCatalog.describeTargets(activeCookingPotTargets));
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
        if (activeCookingPotTargets.isEmpty()) {
            LOGGER.info("Skipping cooking-pot bridge because no supported external cooking-pot mods are currently loaded.");
            return;
        }

        try {
            final Class<?> apiClass = Class.forName(COOKING_FOR_BLOCKHEADS_API_CLASS);
            final Class<?> handlerClass = Class.forName(COOKING_FOR_BLOCKHEADS_HANDLER_CLASS);
            final Object handler = new CookingPotKitchenHandler();
            final var registerKitchenRecipeHandler =
                    apiClass.getMethod("registerKitchenRecipeHandler", Class.class, handlerClass);

            registerKitchenRecipeHandler.invoke(null, CookingPotIndexedRecipe.class, handler);

            final Set<Class<?>> registeredRecipeClasses = new HashSet<>();
            for (final CookingPotBridgeTarget target : activeCookingPotTargets) {
                final Class<?> recipeClass = target.resolveRecipeClass().orElse(null);
                if (recipeClass == null || !registeredRecipeClasses.add(recipeClass)) {
                    continue;
                }

                registerKitchenRecipeHandler.invoke(null, recipeClass, handler);
            }
            LOGGER.info("Registered LAB-11 mods-unified Cooking for Blockheads bridge for {}.",
                    CookingPotBridgeCatalog.describeTargets(activeCookingPotTargets));

            registerKitchenProcessorFallbackProvider();
            registerBalmRecipeSyncListeners();
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified cooking-pot bridge.", e);
        }
    }

    private void registerKitchenProcessorFallbackProvider() {
        try {
            final Class<?> bridgeClass = Class.forName(LOCAL_BALM_FALLBACK_PROVIDER_BRIDGE_CLASS);
            final Object result = bridgeClass
                    .getMethod("registerFallbackKitchenProcessorProvider", List.class)
                    .invoke(null, activeCookingPotTargets);
            if (result instanceof Boolean ok && ok) {
                LOGGER.info("Registered LAB-11 mods-unified fallback KitchenItemProcessor providers for {}.",
                        CookingPotBridgeCatalog.describeTargets(activeCookingPotTargets));
            } else {
                LOGGER.warn("Skipping KitchenItemProcessor fallback registration because Balm NeoForge providers are unavailable.");
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified fallback KitchenItemProcessor provider.", e);
        }
    }

    private void registerBalmRecipeSyncListeners() {
        try {
            final Class<?> bridgeClass = Class.forName(LOCAL_BALM_RECIPE_SYNC_BRIDGE_CLASS);
            bridgeClass.getMethod("registerListeners", List.class).invoke(null, activeCookingPotTargets);
            LOGGER.info("Registered LAB-11 mods-unified Balm recipe sync listeners for {}.",
                    CookingPotBridgeCatalog.describeTargets(activeCookingPotTargets));
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified Balm recipe sync listeners for cooking-pot bridges.", e);
        }
    }

    private void onServerStarted(final ServerStartedEvent event) {
        injectCookingPotRecipesIntoCookingForBlockheads(event.getServer().getRecipeManager(), event.getServer().registryAccess(), "neoforge_server_started");
    }

    private void onDatapackSync(final OnDatapackSyncEvent event) {
        final MinecraftServer server = event.getPlayerList().getServer();
        injectCookingPotRecipesIntoCookingForBlockheads(server.getRecipeManager(), server.registryAccess(), "neoforge_datapack_sync");
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
                "neoforge_client_recipes_updated"
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
        return ModList.get().isLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS);
    }
}
