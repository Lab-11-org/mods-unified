package org.lab_11.modsunified.impl.platform;

import org.lab_11.modsunified.impl.platform.neoforge.v1_21_1.NeoForge121BridgeTargetProvider;
import org.lab_11.modsunified.impl.platform.v1_20_1.Minecraft1201BridgeTargetProvider;

public final class RuntimeBindings {
    private static final BridgeTargetProvider MINECRAFT_1201_TARGETS = new Minecraft1201BridgeTargetProvider();
    private static final ModRuntimeBindings FORGE_1201 = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("forge-1.20.1", "forge", "1.20.1", "47.x"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat",
            MINECRAFT_1201_TARGETS
    );
    private static final ModRuntimeBindings NEOFORGE_1211 = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("neoforge-1.21.1", "neoforge", "1.21.1", "21.1.219"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.CustomizeBlocks",
            new NeoForge121BridgeTargetProvider()
    );
    private static final ModRuntimeBindings ACTIVE = selectActiveProfile();

    private RuntimeBindings() {
    }

    public static ModRuntimeBindings active() {
        return ACTIVE;
    }

    private static ModRuntimeBindings selectActiveProfile() {
        final String minecraftVersion = detectMinecraftVersion();
        if (isClassPresent("net.minecraftforge.fml.ModList")
                && !isClassPresent("net.neoforged.fml.ModList")
                && minecraftVersion.startsWith("1.20.1")) {
            return FORGE_1201;
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

    private static boolean isClassPresent(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
