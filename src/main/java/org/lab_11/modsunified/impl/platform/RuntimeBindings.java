package org.lab_11.modsunified.impl.platform;

public final class RuntimeBindings {
    private static final ModRuntimeBindings FORGE_1201 = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("forge-1.20.1", "forge", "1.20.1", "47.x"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.DungeonOvenCompat",
            "net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotRecipe",
            "net.yirmiri.dungeonsdelight.common.block.entity.MonsterPotBlockEntity"
    );
    private static final ModRuntimeBindings NEOFORGE_1211 = new ModRuntimeBindings(
            new MinecraftRuntimeProfile("neoforge-1.21.1", "neoforge", "1.21.1", "21.1.219"),
            "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmFallbackProviderBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.BalmRecipeSyncBridge",
            "org.lab_11.modsunified.impl.cookingforblockheads.CustomizeBlocks",
            "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotRecipe",
            "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotBlockEntity"
    );
    private static final ModRuntimeBindings ACTIVE = selectActiveProfile();

    private RuntimeBindings() {
    }

    public static ModRuntimeBindings active() {
        return ACTIVE;
    }

    private static ModRuntimeBindings selectActiveProfile() {
        if (isClassPresent("net.minecraftforge.fml.ModList")
                && !isClassPresent("net.neoforged.fml.ModList")) {
            return FORGE_1201;
        }
        return NEOFORGE_1211;
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
