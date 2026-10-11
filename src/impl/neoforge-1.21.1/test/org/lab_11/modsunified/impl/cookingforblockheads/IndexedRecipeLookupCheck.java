package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class IndexedRecipeLookupCheck {
    public interface FakeKitchenOperation {
        Optional<Component> getFeedback();
    }

    private IndexedRecipeLookupCheck() {
    }

    public static void main(final String[] args) throws ReflectiveOperationException {
        final ResourceLocation normalId = MinecraftApiCompat.resourceLocation("minecraft", "bread");
        final ResourceLocation oldIndexedId = indexedId("old");
        final ResourceLocation newIndexedId = indexedId("new");

        if (CookingPotRecipeIndexer.findIndexedRecipeHolder((RecipeManager) null, normalId) != null
                || !CookingPotRecipeIndexer.indexedRecipeIds(null).isEmpty()
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(null) != null
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(normalId) != null
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(oldIndexedId) != null) {
            throw new AssertionError("Uninjected recipe holders must resolve to null");
        }

        checkManagerScopedSnapshots(oldIndexedId, newIndexedId);
        checkVariantSelection(newIndexedId, oldIndexedId);
        checkCustomNameComponent();
        checkPrefilledContainerCredit();
        if (OvenBridge.activePropertyValueForBurning(true)
                || !OvenBridge.activePropertyValueForBurning(false)) {
            throw new AssertionError("CFBH oven active property must remain inverse to burning state");
        }
        checkRetryableOperationMarker();
    }

    private static void checkManagerScopedSnapshots(final ResourceLocation oldIndexedId,
                                                    final ResourceLocation newIndexedId)
            throws ReflectiveOperationException {
        final RecipeManager oldManager = new RecipeManager(RegistryAccess.EMPTY);
        final RecipeManager newManager = new RecipeManager(RegistryAccess.EMPTY);
        final Object oldHolder = new Object();
        final Object newHolder = new Object();
        final Method cache = CookingPotRecipeIndexer.class.getDeclaredMethod(
                "cacheIndexedRecipes",
                RecipeManager.class,
                Map.class
        );
        cache.setAccessible(true);
        cache.invoke(null, oldManager, Map.of(oldIndexedId, oldHolder));
        cache.invoke(null, newManager, Map.of(newIndexedId, newHolder));

        if (CookingPotRecipeIndexer.findIndexedRecipeHolder(oldManager, oldIndexedId) != oldHolder
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(oldManager, newIndexedId) != null
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(newManager, newIndexedId) != newHolder
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(newManager, oldIndexedId) != null) {
            throw new AssertionError("Indexed recipe holders must remain scoped to their RecipeManager");
        }
        if (CookingPotRecipeIndexer.findIndexedRecipeHolder(newIndexedId) != newHolder
                || CookingPotRecipeIndexer.findIndexedRecipeHolder(oldIndexedId) != null) {
            throw new AssertionError("Manager-less lookup must use only the latest indexed snapshot");
        }

        final Set<ResourceLocation> oldIds = CookingPotRecipeIndexer.indexedRecipeIds(oldManager);
        if (!oldIds.equals(Set.of(oldIndexedId))
                || !CookingPotRecipeIndexer.indexedRecipeIds(newManager).equals(Set.of(newIndexedId))) {
            throw new AssertionError("Indexed recipe IDs must reflect each manager's snapshot");
        }
        try {
            oldIds.remove(oldIndexedId);
            throw new AssertionError("Indexed recipe ID snapshots must be immutable");
        } catch (UnsupportedOperationException ignored) {
            // expected
        }
    }

    private static void checkCustomNameComponent() throws ReflectiveOperationException {
        final Class<?> componentType = Class.forName(
                "net.minecraft.core.component.DataComponentType", false, MinecraftApiCompat.class.getClassLoader());
        if (ItemStack.class.getMethod("set", componentType, Object.class) == null) {
            throw new AssertionError("ItemStack.set must use DataComponentType's declared parameter type");
        }
    }

    private static void checkPrefilledContainerCredit() {
        if (CookingPotProcessorCapability.requiredContainerTokenCount(2, 2, 0, 0) != 0
                || CookingPotProcessorCapability.requiredContainerTokenCount(2, 1, 0, 1) != 1
                || CookingPotProcessorCapability.requiredContainerTokenCount(2, 1, 0, 2) != 1
                || CookingPotProcessorCapability.requiredContainerTokenCount(2, 0, 0, 1) != -1
                || CookingPotProcessorCapability.requiredContainerTokenCount(1, 1, 1, 1) != 1) {
            throw new AssertionError("Prefilled recipe containers must reduce, but not bypass, provider consumption");
        }
    }

    private static void checkVariantSelection(final ResourceLocation selectedId,
                                              final ResourceLocation otherId) throws ReflectiveOperationException {
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
    }

    private static void checkRetryableOperationMarker() throws ReflectiveOperationException {
        final Field operationClass = CfbhRuntime.class.getDeclaredField("kitchenOperationClass");
        operationClass.setAccessible(true);
        operationClass.set(null, FakeKitchenOperation.class);
        final Object retryable = CfbhRuntime.newRetryableKitchenOperationWithFeedback(Component.literal("retry"));
        final Object terminal = CfbhRuntime.newKitchenOperationWithFeedback(Component.literal("terminal"));
        if (!CfbhRuntime.isRetryableKitchenOperation(retryable)
                || CfbhRuntime.isRetryableKitchenOperation(terminal)) {
            throw new AssertionError("Only retry-safe pot failures may advance to another processor");
        }
    }

    private static ResourceLocation indexedId(final String path) {
        return MinecraftApiCompat.resourceLocation(
                BridgeKeys.MOD_LAB11_UNIFIED,
                BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX + path
        );
    }
}
