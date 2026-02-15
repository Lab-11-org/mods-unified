package org.lab_11.modsunified.impl;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.client.RecipesUpdatedEvent;
import net.blay09.mods.balm.api.event.server.ServerReloadFinishedEvent;
import net.blay09.mods.balm.api.event.server.ServerStartedEvent;

import java.util.List;

public final class BalmRecipeSyncBridge {
    private BalmRecipeSyncBridge() {
    }

    public static void registerListeners(final List<CookingPotBridgeTarget> targets) {
        Balm.getEvents().onEvent(ServerStartedEvent.class, event ->
                CookingPotRecipeIndexer.injectRecipes(
                        event.getServer().getRecipeManager(),
                        event.getServer().registryAccess(),
                        "balm_server_started",
                        targets
                ));
        Balm.getEvents().onEvent(ServerReloadFinishedEvent.class, event ->
                CookingPotRecipeIndexer.injectRecipes(
                        event.getServer().getRecipeManager(),
                        event.getServer().registryAccess(),
                        "balm_server_reload_finished",
                        targets
                ));
        Balm.getEvents().onEvent(RecipesUpdatedEvent.class, event ->
                CookingPotRecipeIndexer.injectRecipes(
                        event.getRecipeManager(),
                        event.getRegistryAccess(),
                        "balm_client_recipes_updated",
                        targets
                ));
    }
}
