package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

final class IndexedRecipeLookupCheck {
    public interface FakeKitchenOperation {
        Optional<Component> getFeedback();
    }

    private IndexedRecipeLookupCheck() {
    }

    public static void main(final String[] args) {
        final ResourceLocation normalId = MinecraftApiCompat.resourceLocation("minecraft", "bread");
        final ResourceLocation oldIndexedId = indexedId("old");
        final ResourceLocation newIndexedId = indexedId("new");
        final Object normalRecipe = new Object();
        final Object newIndexedRecipe = new Object();

        final Map<ResourceLocation, Object> merged = CookingPotRecipeIndexer.mergeIndexedRecipesByName(
                Map.of(normalId, normalRecipe, oldIndexedId, new Object()),
                Map.of(newIndexedId, newIndexedRecipe)
        );
        if (merged.size() != 2
                || merged.get(normalId) != normalRecipe
                || merged.containsKey(oldIndexedId)
                || merged.get(newIndexedId) != newIndexedRecipe) {
            throw new AssertionError("Indexed RecipeManager byName overlay is invalid: " + merged.keySet());
        }

        checkVariantSelection(newIndexedId, oldIndexedId);
        if (OvenBridge.activePropertyValueForBurning(true)
                || !OvenBridge.activePropertyValueForBurning(false)) {
            throw new AssertionError("CFBH oven active property must remain inverse to burning state");
        }
        checkRetryableOperationMarker();
    }

    private static void checkVariantSelection(final ResourceLocation selectedId,
                                              final ResourceLocation otherId) {
        try {
            final Class<?> mixin = Class.forName(
                    "org.lab_11.modsunified.mixin.cookingforblockheads.KitchenMenuRecipeStatusMixin"
            );
            final Method method = mixin.getDeclaredMethod(
                    "shouldUseRequestedLocks",
                    ResourceLocation.class,
                    ResourceLocation.class
            );
            method.setAccessible(true);
            if (!Boolean.TRUE.equals(method.invoke(null, selectedId, selectedId))
                    || !Boolean.FALSE.equals(method.invoke(null, otherId, selectedId))) {
                throw new AssertionError("Indexed variant locks must apply only to the selected recipe");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void checkRetryableOperationMarker() {
        try {
            final Field operationClass = CfbhRuntime.class.getDeclaredField("kitchenOperationClass");
            operationClass.setAccessible(true);
            operationClass.set(null, FakeKitchenOperation.class);
            final Object retryable = CfbhRuntime.newRetryableKitchenOperationWithFeedback(Component.literal("retry"));
            final Object terminal = CfbhRuntime.newKitchenOperationWithFeedback(Component.literal("terminal"));
            if (!CfbhRuntime.isRetryableKitchenOperation(retryable)
                    || CfbhRuntime.isRetryableKitchenOperation(terminal)) {
                throw new AssertionError("Only retry-safe pot failures may advance to another processor");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ResourceLocation indexedId(final String path) {
        return MinecraftApiCompat.resourceLocation(
                BridgeKeys.MOD_LAB11_UNIFIED,
                BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX + path
        );
    }
}
