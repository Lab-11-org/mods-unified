package org.lab_11.modsunified.impl.runtime;

public final class RuntimeBindings {
    private static final ModRuntimeBindings ACTIVE = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("neoforge-1.21.1", "neoforge", "1.21.1", "21.1.219"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat",
            new NeoForge121BridgeTargetProvider()
    );

    private RuntimeBindings() {
    }

    public static ModRuntimeBindings active() {
        return ACTIVE;
    }
}
