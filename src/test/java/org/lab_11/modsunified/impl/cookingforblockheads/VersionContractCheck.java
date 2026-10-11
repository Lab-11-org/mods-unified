package org.lab_11.modsunified.impl.cookingforblockheads;

import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.RuntimeBindings;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

final class VersionContractCheck {
    private VersionContractCheck() {
    }

    public static void main(final String[] args) throws Exception {
        checkLoadingListConstants();
        checkRecipeManagerIsUntouched();
        checkCfbhMixinTargets();
        checkSelectedMixinConfig();
        checkKitchenFeedbackAlignmentHook();
        checkFarmersDelightStoveBridge();
        checkCfbhRuntimeClasses();
        checkForgeOvenResources();
        checkRepeatedCraftContainerReservation();
    }

    // Both loaders' loading-phase mod lists must stay wired, otherwise mixin gating
    // rejects every mixin before the runtime ModList exists (1.20.1 breakage).
    private static void checkLoadingListConstants() throws Exception {
        requireFieldValue(LoaderApiCompat.class,
                "NEOFORGE_LOADING_MOD_LIST_CLASS",
                "net.neoforged.fml.loading.LoadingModList");
        requireFieldValue(LoaderApiCompat.class,
                "FORGE_LOADING_MOD_LIST_CLASS",
                "net.minecraftforge.fml.loading.LoadingModList");
    }

    // Synthetic indexed recipes must never be installed into RecipeManager. On 1.21.1
    // getRecipes() serializes byName, so such an overlay crashes update_recipes sync.
    private static void checkRecipeManagerIsUntouched() throws Exception {
        final String bytes = readResource(
                "org/lab_11/modsunified/impl/cookingforblockheads/CookingPotRecipeIndexer.class");
        if (bytes.contains("installIndexedRecipesByName") || bytes.contains("mergeIndexedRecipesByName")) {
            throw new AssertionError("Indexed recipes must not be installed into RecipeManager (breaks update_recipes sync)");
        }
        if (!bytes.contains("findIndexedRecipeHolder")) {
            throw new AssertionError("Missing CookingPotRecipeIndexer.findIndexedRecipeHolder");
        }
    }

    // CFBH 1.20.1 uses RecipeBookMenu, 1.21.1 uses KitchenMenu. Pin each mixin to the
    // class that actually exists for the version it supports.
    private static void checkCfbhMixinTargets() throws Exception {
        requireMixinTarget("RecipeBookMenuLegacyMixin",
                "net.blay09.mods.cookingforblockheads.menu.RecipeBookMenu");
        requireMixinTarget("KitchenMenuMixin",
                "net.blay09.mods.cookingforblockheads.menu.KitchenMenu");
        requireMixinTarget("KitchenMenuRecipeStatusMixin",
                "net.blay09.mods.cookingforblockheads.menu.KitchenMenu");
    }

    private static void checkSelectedMixinConfig() throws Exception {
        final String config = readResource("lab_11_mods_unified.mixins.json");
        if ("forge-1.20.1".equals(RuntimeBindings.active().profile().id())) {
            requireContains(config, "\"compatibilityLevel\": \"JAVA_17\"");
            requireContains(config, "\"RecipeBookMenuLegacyMixin\"");
            requireContains(config, "\"MonsterPotBlock120Mixin\"");
            requireAbsent(config, "\"KitchenScreenMixin\"");
            requireAbsent(config, "\"RecipeManagerMixin\"");
            requireAbsent(config, "\"KitchenMenuMixin\"");
            requireAbsent(config, "\"KitchenMenuRecipeStatusMixin\"");
            requireAbsent(config, "\"MonsterPotBlockMixin\"");
            return;
        }

        requireContains(config, "\"compatibilityLevel\": \"JAVA_21\"");
        requireContains(config, "\"RecipeManagerMixin\"");
        requireContains(config, "\"KitchenMenuMixin\"");
        requireContains(config, "\"KitchenMenuRecipeStatusMixin\"");
        requireContains(config, "\"KitchenScreenMixin\"");
        requireContains(config, "\"MonsterPotBlockMixin\"");
        requireAbsent(config, "\"RecipeBookMenuLegacyMixin\"");
        requireAbsent(config, "\"MonsterPotBlock120Mixin\"");
    }

    private static void checkKitchenFeedbackAlignmentHook() throws Exception {
        if ("forge-1.20.1".equals(RuntimeBindings.active().profile().id())) {
            return;
        }
        final String mixin = readResource(
                "org/lab_11/modsunified/mixin/cookingforblockheads/KitchenScreenMixin.class");
        requireContains(mixin, "drawCenteredString");
        requireContains(mixin, "fill");
        requireContains(mixin, "ModifyArgs");
    }

    private static void checkFarmersDelightStoveBridge() throws Exception {
        if (!CookingPotHeatBridge.isSupportedStoveId("farmersdelight:stove")
                || !CookingPotHeatBridge.isSupportedStoveId("kaleidoscope_cookery:stove")
                || CookingPotHeatBridge.isSupportedStoveId("farmersdelight:cooking_pot")) {
            throw new AssertionError("Supported stove identity bridge is broken");
        }
        final String connectors = readResource("data/cookingforblockheads/tags/block/kitchen_connectors.json");
        requireContains(connectors, "\"farmersdelight:stove\"");
        requireContains(connectors, "\"kaleidoscope_cookery:stove\"");
    }

    private static void checkCfbhRuntimeClasses() {
        if (!classExists("net.blay09.mods.cookingforblockheads.menu.KitchenMenu")) {
            return; // CFBH not on this target's classpath; targets pinned above.
        }

        requireClass("net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus");
        requireClass("net.blay09.mods.cookingforblockheads.registry.CookingForBlockheadsRegistry");
        requireClass("net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen");
        if (classExists("net.blay09.mods.cookingforblockheads.menu.RecipeBookMenu")) {
            throw new AssertionError("1.21.1 CFBH must expose KitchenMenu, not RecipeBookMenu");
        }
    }

    private static void checkForgeOvenResources() throws Exception {
        if (!"forge-1.20.1".equals(RuntimeBindings.active().profile().id())) {
            return;
        }

        final String ovens = readResource("data/lab_11_mods_unified/tags/blocks/cfbh_ovens.json");
        requireContains(ovens, "\"cookingforblockheads:oven\"");
        requireAbsent(ovens, "\"cookingforblockheads:white_oven\"");
        requireMixinTarget("CfbhKitchenBlockShapeMixin",
                "net.blay09.mods.cookingforblockheads.block.BlockKitchen");
        requireContains(readResource("lab_11_mods_unified.mixins.json"), "CfbhKitchenBlockShapeMixin");
    }

    private static void checkRepeatedCraftContainerReservation() {
        if (CookingPotProcessorCapability.missingContainerCount(1, 1, 1) != 1
                || CookingPotProcessorCapability.missingContainerCount(1, 2, 1) != 0) {
            throw new AssertionError("Queued crafts must reserve their serving containers");
        }
    }

    private static void requireMixinTarget(final String simpleName, final String expected) throws Exception {
        final String bytes = readResource("org/lab_11/modsunified/mixin/cookingforblockheads/" + simpleName + ".class");
        if (!bytes.contains(expected)) {
            throw new AssertionError(simpleName + " must target " + expected);
        }
    }

    private static void requireFieldValue(final Class<?> owner, final String fieldName, final String expected)
            throws Exception {
        final Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        if (!expected.equals(field.get(null))) {
            throw new AssertionError(owner.getSimpleName() + "." + fieldName + " must be " + expected);
        }
    }

    private static String readResource(final String path) throws Exception {
        try (InputStream stream = VersionContractCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean classExists(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static void requireClass(final String className) {
        if (!classExists(className)) {
            throw new AssertionError("Missing required CFBH class: " + className);
        }
    }

    private static void requireContains(final String value, final String expected) {
        if (!value.contains(expected)) {
            throw new AssertionError("Missing selected-profile mixin entry: " + expected);
        }
    }

    private static void requireAbsent(final String value, final String unexpected) {
        if (value.contains(unexpected)) {
            throw new AssertionError("Version-crossed mixin entry was packaged: " + unexpected);
        }
    }
}
