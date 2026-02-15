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
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.lab_11.modsunified.impl.CookingPotBridgeTarget;
import org.lab_11.modsunified.impl.CookingPotContainerTooltipBridge;
import org.lab_11.modsunified.impl.CookingPotIndexedRecipe;
import org.lab_11.modsunified.impl.CookingPotKitchenHandler;
import org.lab_11.modsunified.impl.CookingPotProcessorCapability;
import org.lab_11.modsunified.impl.CookingPotRecipeIndexer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod(Unifiled.MOD_ID)
public final class Unifiled {
    public static final String MOD_ID = "lab_11_mods_unified";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = "cookingforblockheads";
    private static final String FARMERS_DELIGHT_MOD_ID = "farmersdelight";
    private static final String DUNGEONS_DELIGHT_MOD_ID = "dungeonsdelight";
    private static final String MINERS_DELIGHT_MOD_ID = "minersdelight";
    private static final String COOKING_FOR_BLOCKHEADS_API_CLASS = "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI";
    private static final String COOKING_FOR_BLOCKHEADS_HANDLER_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler";
    private static final String LOCAL_BALM_FALLBACK_PROVIDER_BRIDGE_CLASS = "org.lab_11.modsunified.impl.BalmFallbackProviderBridge";
    private static final String LOCAL_BALM_RECIPE_SYNC_BRIDGE_CLASS = "org.lab_11.modsunified.impl.BalmRecipeSyncBridge";

    private List<CookingPotBridgeTarget> activeCookingPotTargets = List.of();

    public Unifiled(IEventBus modEventBus) {
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCapabilities);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(this::onClientRecipesUpdated);
            NeoForge.EVENT_BUS.addListener(this::onItemTooltip);
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
                describeTargets(activeCookingPotTargets));
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
                    describeTargets(activeCookingPotTargets));

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
                        describeTargets(activeCookingPotTargets));
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
                    describeTargets(activeCookingPotTargets));
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

        CookingPotRecipeIndexer.injectRecipes(recipeManager, registryAccess, source, activeCookingPotTargets);
    }

    private void refreshActiveCookingPotTargets() {
        activeCookingPotTargets = resolveActiveCookingPotTargets();
    }

    private List<CookingPotBridgeTarget> resolveActiveCookingPotTargets() {
        final List<CookingPotBridgeTarget> candidates = List.of(
                new CookingPotBridgeTarget(
                        "farmersdelight_cooking_pot",
                        "FarmersDelight Cooking Pot",
                        List.of(FARMERS_DELIGHT_MOD_ID),
                        "vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
                        "vectorwing.farmersdelight.common.registry.ModRecipeTypes",
                        "COOKING",
                        "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity",
                        "vectorwing.farmersdelight.common.registry.ModBlockEntityTypes",
                        "COOKING_POT"
                ),
                new CookingPotBridgeTarget(
                        "dungeonsdelight_monster_pot",
                        "DungeonsDelight Monster Pot",
                        List.of(DUNGEONS_DELIGHT_MOD_ID, FARMERS_DELIGHT_MOD_ID),
                        "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotRecipe",
                        "net.yirmiri.dungeonsdelight.core.registry.DDRecipeRegistries",
                        "MONSTER_COOKING_RECIPE_TYPE",
                        "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotBlockEntity",
                        "net.yirmiri.dungeonsdelight.core.registry.DDBlockEntities",
                        "MONSTER_COOKING_POT"
                ),
                new CookingPotBridgeTarget(
                        "minersdelight_copper_pot",
                        "MinersDelight Copper Pot",
                        List.of(MINERS_DELIGHT_MOD_ID, FARMERS_DELIGHT_MOD_ID),
                        "vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
                        "vectorwing.farmersdelight.common.registry.ModRecipeTypes",
                        "COOKING",
                        "com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity",
                        "com.sammy.minersdelight.setup.MDBlockEntities",
                        "COPPER_POT"
                )
        );

        final List<CookingPotBridgeTarget> activeTargets = new ArrayList<>();
        for (final CookingPotBridgeTarget target : candidates) {
            if (!target.isModSetLoaded()) {
                continue;
            }

            if (target.resolveRecipeClass().isEmpty()
                    || target.resolveRecipeType().isEmpty()
                    || target.resolveBlockEntityClass().isEmpty()
                    || target.resolveBlockEntityType().isEmpty()) {
                LOGGER.warn("Skipping cooking-pot bridge target '{}' because one or more reflective bindings are unavailable.",
                        target.displayName());
                continue;
            }

            activeTargets.add(target);
        }

        return List.copyOf(activeTargets);
    }

    private static boolean isCookingForBlockheadsLoaded() {
        return ModList.get().isLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID);
    }

    private static String describeTargets(final List<CookingPotBridgeTarget> targets) {
        final List<String> names = new ArrayList<>(targets.size());
        for (final CookingPotBridgeTarget target : targets) {
            names.add(target.displayName());
        }
        return String.join(", ", names);
    }
}
