package org.lab_11.modsunified.impl.runtime;

public final class RuntimeBindings {
    private static final String PROFILE_OVERRIDE_PROPERTY = "lab11.runtime.profile";
    private static final ModRuntimeBindings NEOFORGE_1201 = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("neoforge-1.20.1", "neoforge", "1.20.1", "20.x"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat",
            new NeoForge1201BridgeTargetProvider()
    );
    private static final ModRuntimeBindings NEOFORGE_1211 = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("neoforge-1.21.1", "neoforge", "1.21.1", "21.1.219"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat",
            new NeoForge121BridgeTargetProvider()
    );
    private static final ModRuntimeBindings ACTIVE = selectActiveProfile();

    private RuntimeBindings() {
    }

    public static ModRuntimeBindings active() {
        return ACTIVE;
    }

    private static ModRuntimeBindings selectActiveProfile() {
        final String override = System.getProperty(PROFILE_OVERRIDE_PROPERTY, "").trim();
        if (!override.isEmpty()) {
            if (NEOFORGE_1201.profile().id().equalsIgnoreCase(override)) {
                return NEOFORGE_1201;
            }
            if (NEOFORGE_1211.profile().id().equalsIgnoreCase(override)) {
                return NEOFORGE_1211;
            }
        }

        final String minecraftVersion = detectMinecraftVersion();
        if (minecraftVersion.startsWith("1.20.1")) {
            return NEOFORGE_1201;
        }
        return NEOFORGE_1211;
    }

    private static String detectMinecraftVersion() {
        try {
            final var currentVersion = net.minecraft.SharedConstants.getCurrentVersion();
            if (currentVersion != null) {
                final String name = currentVersion.getName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the default profile when runtime version lookup is unavailable.
        }
        return NEOFORGE_1211.profile().minecraftVersion();
    }
}
