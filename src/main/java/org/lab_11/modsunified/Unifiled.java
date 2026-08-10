package org.lab_11.modsunified;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
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
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeTarget;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeCatalog;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotContainerTooltipBridge;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotIndexedRecipe;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotKitchenHandler;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotProcessorCapability;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotRecipeIndexer;
import org.lab_11.modsunified.impl.cookingforblockheads.DungeonsDelightCupRecipeMirror;
import org.lab_11.modsunified.impl.platform.ModRuntimeBindings;
import org.lab_11.modsunified.impl.platform.RuntimeBindings;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod(Unifiled.MOD_ID)
public final class Unifiled {
    public static final String MOD_ID = "lab_11_mods_unified";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModRuntimeBindings RUNTIME = RuntimeBindings.active();

    private List<CookingPotBridgeTarget> activeCookingPotTargets = List.of();

    public Unifiled(IEventBus modEventBus) {
        LOGGER.info("Bootstrapping runtime profile {} (loader={}, minecraft={}, loaderVersion={}).",
                RUNTIME.profile().id(),
                RUNTIME.profile().loader(),
                RUNTIME.profile().minecraftVersion(),
                RUNTIME.profile().loaderVersion());
        registerCompatBlocks(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCapabilities);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(this::onClientRecipesUpdated);
            NeoForge.EVENT_BUS.addListener(this::onItemTooltip);
            NeoForge.EVENT_BUS.addListener(this::onRenderTooltipPre);
        }
    }

    private void registerCompatBlocks(final IEventBus modEventBus) {
        if (!ModList.get().isLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS)) {
            return;
        }

        try {
            final Class<?> compatClass = Class.forName(RUNTIME.dungeonOvenCompatClassName());
            compatClass.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            LOGGER.info("Registered LAB-11 mods-unified CFBH compat blocks.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified CFBH compat blocks.", e);
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
        if (activeCookingPotTargets.isEmpty()) {
            LOGGER.info("Skipping cooking-pot bridge because no supported external cooking-pot mods are currently loaded.");
            return;
        }

        registerKitchenRecipeHandlersIfSupported();
        registerKitchenProcessorFallbackProvider();
        registerBalmRecipeSyncListeners();
    }

    private void registerKitchenRecipeHandlersIfSupported() {
        final Class<?> apiClass;
        try {
            apiClass = Class.forName(RUNTIME.cfbhApiClassName());
        } catch (ClassNotFoundException ignored) {
            LOGGER.info(
                    "Skipping KitchenRecipeHandler registration because '{}' is unavailable on this runtime.",
                    RUNTIME.cfbhApiClassName()
            );
            return;
        }

        final Object handler = CookingPotKitchenHandler.createRuntimeHandlerProxy();
        if (handler == null) {
            LOGGER.info("Skipping KitchenRecipeHandler registration because runtime handler interface is unavailable.");
            return;
        }

        final Method registerKitchenRecipeHandler = resolveKitchenRecipeHandlerMethod(apiClass, handler.getClass());
        if (registerKitchenRecipeHandler == null) {
            LOGGER.info("Skipping KitchenRecipeHandler registration because no compatible API method exists on {}.",
                    apiClass.getName());
            return;
        }

        try {
            final Set<Class<?>> registeredRecipeClasses = new HashSet<>();
            registeredRecipeClasses.add(CookingPotIndexedRecipe.class);
            registerKitchenRecipeHandler.invoke(null, CookingPotIndexedRecipe.class, handler);

            for (final CookingPotBridgeTarget target : activeCookingPotTargets) {
                final Class<?> recipeClass = target.resolveRecipeClass().orElse(null);
                if (recipeClass == null || !registeredRecipeClasses.add(recipeClass)) {
                    continue;
                }
                registerKitchenRecipeHandler.invoke(null, recipeClass, handler);
            }

            LOGGER.info("Registered LAB-11 mods-unified Cooking for Blockheads bridge for {}.",
                    CookingPotBridgeCatalog.describeTargets(activeCookingPotTargets));
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register LAB-11 mods-unified cooking-pot bridge recipe handlers.", e);
        }
    }

    private static Method resolveKitchenRecipeHandlerMethod(final Class<?> apiClass, final Class<?> handlerProxyClass) {
        for (final Method method : apiClass.getMethods()) {
            if (!method.getName().equals("registerKitchenRecipeHandler")) {
                continue;
            }

            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 2 || parameterTypes[0] != Class.class) {
                continue;
            }
            if (!parameterTypes[1].isAssignableFrom(handlerProxyClass)) {
                continue;
            }
            return method;
        }
        return null;
    }

    private void registerKitchenProcessorFallbackProvider() {
        try {
            final Class<?> bridgeClass = Class.forName(RUNTIME.fallbackProviderBridgeClassName());
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
