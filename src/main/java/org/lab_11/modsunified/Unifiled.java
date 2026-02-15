package org.lab_11.modsunified;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Supplier;

@Mod(Unifiled.MOD_ID)
public final class Unifiled {
    public static final String MOD_ID = "lab_11_mods_unified";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = "cookingforblockheads";
    private static final String FD_MOD_ID = "farmersdelight";
    private static final String COOKING_FOR_BLOCKHEADS_API_CLASS = "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI";
    private static final String COOKING_FOR_BLOCKHEADS_HANDLER_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler";
    private static final String COOKING_FOR_BLOCKHEADS_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.registry.CookingForBlockheadsRegistry";
    private static final String FD_RECIPE_CLASS = "vectorwing.farmersdelight.common.crafting.CookingPotRecipe";
    private static final String FD_RECIPE_TYPES_CLASS = "vectorwing.farmersdelight.common.registry.ModRecipeTypes";
    private static final String LOCAL_HANDLER_CLASS = "org.lab_11.modsunified.compat.FDCookingPotKitchenHandler";
    private static final String LOCAL_PROCESSOR_CAPABILITY_CLASS = "org.lab_11.modsunified.compat.FDCookingPotProcessorCapability";
    private static final String LOCAL_BALM_FALLBACK_PROVIDER_BRIDGE_CLASS = "org.lab_11.modsunified.compat.BalmFallbackProviderBridge";
    private static final String LOCAL_BALM_RECIPE_SYNC_BRIDGE_CLASS = "org.lab_11.modsunified.compat.BalmRecipeSyncBridge";

    public Unifiled(IEventBus modEventBus) {
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCapabilities);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(this::onClientRecipesUpdated);
        }
    }

    private void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        if (!areTargetModsLoaded()) {
            return;
        }

        try {
            final Class<?> capabilityRegistrarClass = Class.forName(LOCAL_PROCESSOR_CAPABILITY_CLASS);
            capabilityRegistrarClass.getMethod("register", RegisterCapabilitiesEvent.class).invoke(null, event);
            LOGGER.info("Registered LAB-11 mods-unified KitchenItemProcessor capability for Farmer's Delight cooking pot.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified KitchenItemProcessor capability for Farmer's Delight cooking pot.", e);
        }
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(this::registerCompatIfSupported);
    }

    private void registerCompatIfSupported() {
        if (!areTargetModsLoaded()) {
            LOGGER.info("Skipping Cooking for Blockheads and Farmer's Delight bridge because required target mods are not both loaded.");
            return;
        }

        try {
            final Class<?> apiClass = Class.forName(COOKING_FOR_BLOCKHEADS_API_CLASS);
            final Class<?> handlerClass = Class.forName(COOKING_FOR_BLOCKHEADS_HANDLER_CLASS);
            final Class<?> recipeClass = Class.forName(FD_RECIPE_CLASS);
            final Object handler = Class.forName(LOCAL_HANDLER_CLASS).getDeclaredConstructor().newInstance();

            apiClass.getMethod("registerKitchenRecipeHandler", Class.class, handlerClass)
                    .invoke(null, recipeClass, handler);
            LOGGER.info("Registered LAB-11 mods-unified Cooking for Blockheads and Farmer's Delight cooking pot bridge.");

            registerKitchenProcessorFallbackProvider();
            registerBalmRecipeSyncListeners();
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified Cooking for Blockheads and Farmer's Delight bridge.", e);
        }
    }

    private void registerKitchenProcessorFallbackProvider() {
        try {
            final Class<?> bridgeClass = Class.forName(LOCAL_BALM_FALLBACK_PROVIDER_BRIDGE_CLASS);
            final Object result = bridgeClass.getMethod("registerFallbackKitchenProcessorProvider").invoke(null);
            if (result instanceof Boolean ok && ok) {
                LOGGER.info("Registered LAB-11 mods-unified fallback KitchenItemProcessor provider for Farmer's Delight cooking pot.");
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
            bridgeClass.getMethod("registerListeners").invoke(null);
            LOGGER.info("Registered LAB-11 mods-unified Balm recipe sync listeners for Farmer's Delight cooking injections.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified Balm recipe sync listeners.", e);
        }
    }

    private void onServerStarted(final ServerStartedEvent event) {
        injectCookingPotRecipesIntoCookingForBlockheads(event.getServer());
    }

    private void onDatapackSync(final OnDatapackSyncEvent event) {
        injectCookingPotRecipesIntoCookingForBlockheads(event.getPlayerList().getServer());
    }

    private void onClientRecipesUpdated(final RecipesUpdatedEvent event) {
        if (!areTargetModsLoaded()) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectCookingPotRecipesIntoCookingForBlockheads(final MinecraftServer server) {
        if (!areTargetModsLoaded()) {
            return;
        }

        injectCookingPotRecipesIntoCookingForBlockheads(
                server.getRecipeManager(),
                server.registryAccess(),
                "neoforge_server_event"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectCookingPotRecipesIntoCookingForBlockheads(final RecipeManager recipeManager,
                                                                 final RegistryAccess registryAccess,
                                                                 final String source) {
        try {
            final Class<?> recipeTypesClass = Class.forName(FD_RECIPE_TYPES_CLASS);
            final Object cookingSupplierObject = recipeTypesClass.getField("COOKING").get(null);
            if (!(cookingSupplierObject instanceof Supplier<?> cookingSupplier)) {
                LOGGER.warn("Farmer's Delight cooking recipe type supplier is not available.");
                return;
            }

            final Object recipeTypeObject = cookingSupplier.get();
            if (!(recipeTypeObject instanceof RecipeType<?> cookingType)) {
                LOGGER.warn("Farmer's Delight cooking recipe type is not available.");
                return;
            }

            final Class<?> registryClass = Class.forName(COOKING_FOR_BLOCKHEADS_REGISTRY_CLASS);
            final Object recipesByItemIdObject = registryClass.getMethod("getRecipesByItemId").invoke(null);
            if (!(recipesByItemIdObject instanceof Multimap<?, ?> multimapRaw)) {
                LOGGER.warn("Cooking for Blockheads recipe registry shape is unexpected.");
                return;
            }

            final Multimap<ResourceLocation, RecipeHolder<Recipe<?>>> recipesByItemId = (Multimap<ResourceLocation, RecipeHolder<Recipe<?>>>) multimapRaw;

            int removed = 0;
            final var iterator = recipesByItemId.entries().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<ResourceLocation, RecipeHolder<Recipe<?>>> entry = iterator.next();
                if (entry.getValue().value().getType() == cookingType) {
                    iterator.remove();
                    removed++;
                }
            }

            int added = 0;
            final Iterable<RecipeHolder<?>> cookingRecipes = (Iterable<RecipeHolder<?>>) (Iterable<?>) recipeManager.getAllRecipesFor((RecipeType) cookingType);
            for (final RecipeHolder<?> rawRecipeHolder : cookingRecipes) {
                final RecipeHolder<Recipe<?>> recipeHolder = (RecipeHolder<Recipe<?>>) (RecipeHolder<?>) rawRecipeHolder;
                final ItemStack result = recipeHolder.value().getResultItem(registryAccess);
                if (result.isEmpty()) {
                    continue;
                }

                final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                recipesByItemId.put(itemId, recipeHolder);
                added++;
            }

            LOGGER.info("Injected {} Farmer's Delight cooking recipes into Cooking for Blockheads recipe index (removed {}) via {}.", added, removed, source);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to inject Farmer's Delight cooking recipes into Cooking for Blockheads recipe index.", e);
        }
    }

    private static boolean areTargetModsLoaded() {
        return ModList.get().isLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID) && ModList.get().isLoaded(FD_MOD_ID);
    }
}
