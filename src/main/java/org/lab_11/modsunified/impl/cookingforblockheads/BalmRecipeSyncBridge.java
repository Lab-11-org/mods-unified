package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

public final class BalmRecipeSyncBridge {
    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";
    private static final String BALM_SERVER_STARTED_EVENT_CLASS = "net.blay09.mods.balm.api.event.server.ServerStartedEvent";
    private static final String BALM_SERVER_RELOAD_FINISHED_EVENT_CLASS =
            "net.blay09.mods.balm.api.event.server.ServerReloadFinishedEvent";

    private BalmRecipeSyncBridge() {
    }

    public static void registerListeners(final List<CookingPotBridgeTarget> targets) {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Object events = balmClass.getMethod("getEvents").invoke(null);
            final Method onEventMethod = events.getClass().getMethod("onEvent", Class.class, Consumer.class);

            final Class<?> serverStartedEventClass = Class.forName(BALM_SERVER_STARTED_EVENT_CLASS);
            onEventMethod.invoke(events, serverStartedEventClass, (Consumer<Object>) event ->
                    injectFromEvent(event, "balm_server_started", targets));

            final Class<?> serverReloadFinishedEventClass = Class.forName(BALM_SERVER_RELOAD_FINISHED_EVENT_CLASS);
            onEventMethod.invoke(events, serverReloadFinishedEventClass, (Consumer<Object>) event ->
                    injectFromEvent(event, "balm_server_reload_finished", targets));
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static void injectFromEvent(final Object event,
                                        final String source,
                                        final List<CookingPotBridgeTarget> targets) {
        if (event == null) {
            return;
        }

        try {
            final Object serverObject = event.getClass().getMethod("getServer").invoke(event);
            if (!(serverObject instanceof MinecraftServer server)) {
                return;
            }

            final RecipeManager recipeManager = server.getRecipeManager();
            final RegistryAccess registryAccess = server.registryAccess();
            CookingPotRecipeIndexer.injectRecipes(recipeManager, registryAccess, source, targets);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }
}
