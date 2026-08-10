package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.util.List;
import java.lang.reflect.Method;

final class StockpotSoupBridge {
    private static final String CFBH_SINK_PROVIDER_CLASS = "net.blay09.mods.cookingforblockheads.block.entity.SinkBlockEntity$SinkItemProvider";
    private static final String CFBH_SINK_PROVIDER_CLASS_LEGACY = "net.blay09.mods.cookingforblockheads.tile.SinkBlockEntity$SinkItemProvider";
    private static final String SYNTHETIC_WATER_SINK_TOKEN_NAME = "lab11.stockpot.sink_water";
    private static final String SYNTHETIC_LAVA_SINK_TOKEN_NAME = "lab11.stockpot.sink_lava";

    private static final ResourceLocation SOUP_BASE_WATER_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "water");
    private static final ResourceLocation SOUP_BASE_LAVA_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "lava");
    private static final ResourceLocation SOUP_BASE_COD_BUCKET_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "cod_bucket");
    private static final ResourceLocation SOUP_BASE_SALMON_BUCKET_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "salmon_bucket");
    private static final ResourceLocation SOUP_BASE_PUFFERFISH_BUCKET_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "pufferfish_bucket");

    private static volatile Object syntheticWaterSinkToken;
    private static volatile Object syntheticLavaSinkToken;

    private StockpotSoupBridge() {
    }

    static boolean isSinkItemProvider(final Object itemProvider) {
        if (itemProvider == null) {
            return false;
        }
        final String providerClassName = itemProvider.getClass().getName();
        if (CFBH_SINK_PROVIDER_CLASS.equals(providerClassName)
                || CFBH_SINK_PROVIDER_CLASS_LEGACY.equals(providerClassName)) {
            return true;
        }
        final List<?> combinedProviders = CfbhRuntime.tryGetCombinedProviders(itemProvider);
        if (combinedProviders.isEmpty()) {
            return false;
        }
        for (final Object nestedProvider : combinedProviders) {
            if (isSinkItemProvider(nestedProvider)) {
                return true;
            }
        }
        return false;
    }

    static Object syntheticWaterSinkSoupToken() {
        final Object cached = syntheticWaterSinkToken;
        if (cached != null) {
            return cached;
        }
        final ItemStack markerStack = syntheticWaterSinkSoupMarkerStack();
        final Object created = CfbhRuntime.newIngredientTokenProxy(new CfbhRuntime.IngredientTokenView() {
            @Override
            public ItemStack peek() {
                return markerStack.copy();
            }

            @Override
            public ItemStack consume() {
                return markerStack.copy();
            }

            @Override
            public ItemStack restore(final ItemStack itemStack) {
                return ItemStack.EMPTY;
            }
        });
        syntheticWaterSinkToken = created;
        return created;
    }

    static Object syntheticLavaSinkSoupToken() {
        final Object cached = syntheticLavaSinkToken;
        if (cached != null) {
            return cached;
        }
        final ItemStack markerStack = syntheticLavaSinkSoupMarkerStack();
        final Object created = CfbhRuntime.newIngredientTokenProxy(new CfbhRuntime.IngredientTokenView() {
            @Override
            public ItemStack peek() {
                return markerStack.copy();
            }

            @Override
            public ItemStack consume() {
                return markerStack.copy();
            }

            @Override
            public ItemStack restore(final ItemStack itemStack) {
                return ItemStack.EMPTY;
            }
        });
        syntheticLavaSinkToken = created;
        return created;
    }

    static boolean isSyntheticSinkSoupMarker(final ItemStack stack) {
        return isSyntheticWaterSinkSoupMarker(stack) || isSyntheticLavaSinkSoupMarker(stack);
    }

    static boolean isSyntheticWaterSinkSoupMarker(final ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && MinecraftApiCompat.isSameItemSameData(stack, syntheticWaterSinkSoupMarkerStack());
    }

    static boolean isSyntheticLavaSinkSoupMarker(final ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && MinecraftApiCompat.isSameItemSameData(stack, syntheticLavaSinkSoupMarkerStack());
    }

    static ResourceLocation resolveRequiredSoupBaseId(final Recipe<?> recipe) {
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
            final Recipe<?> delegate = indexedRecipe.delegateRecipe();
            if (delegate != null) {
                final Object soupBaseValue = CfbhRuntime.invokeNoArg(delegate, "soupBase");
                if (soupBaseValue instanceof ResourceLocation soupBaseId) {
                    return soupBaseId;
                }
                final Object soupBaseFieldValue = resolveDeclaredFieldValue(delegate, "soupBase");
                if (soupBaseFieldValue instanceof ResourceLocation soupBaseId) {
                    return soupBaseId;
                }
            }
        }
        return SOUP_BASE_WATER_ID;
    }

    static boolean isWaterSoupBase(final ResourceLocation soupBaseId) {
        return soupBaseId != null && SOUP_BASE_WATER_ID.equals(soupBaseId);
    }

    static boolean isLavaSoupBase(final ResourceLocation soupBaseId) {
        return soupBaseId != null && SOUP_BASE_LAVA_ID.equals(soupBaseId);
    }

    static boolean isCodSoupBase(final ResourceLocation soupBaseId) {
        return soupBaseId != null && SOUP_BASE_COD_BUCKET_ID.equals(soupBaseId);
    }

    static boolean isSalmonSoupBase(final ResourceLocation soupBaseId) {
        return soupBaseId != null && SOUP_BASE_SALMON_BUCKET_ID.equals(soupBaseId);
    }

    static boolean isPufferfishSoupBase(final ResourceLocation soupBaseId) {
        return soupBaseId != null && SOUP_BASE_PUFFERFISH_BUCKET_ID.equals(soupBaseId);
    }

    static boolean isFishBucketSoupBase(final ResourceLocation soupBaseId) {
        return isCodSoupBase(soupBaseId) || isSalmonSoupBase(soupBaseId) || isPufferfishSoupBase(soupBaseId);
    }

    static ItemStack soupBaseBucketStack(final ResourceLocation soupBaseId) {
        final ItemStack registeredStack = registeredSoupBaseDisplayStack(soupBaseId);
        if (!registeredStack.isEmpty()) {
            return registeredStack;
        }
        if (isWaterSoupBase(soupBaseId)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (isLavaSoupBase(soupBaseId)) {
            return new ItemStack(Items.LAVA_BUCKET);
        }

        if (soupBaseId == null) {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.getOptional(soupBaseId).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack registeredSoupBaseDisplayStack(final ResourceLocation soupBaseId) {
        if (soupBaseId == null) {
            return ItemStack.EMPTY;
        }
        try {
            final Class<?> managerClass = Class.forName(
                    "com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager");
            final Object soupBase = managerClass.getMethod("getSoupBase", ResourceLocation.class)
                    .invoke(null, soupBaseId);
            if (soupBase == null) {
                return ItemStack.EMPTY;
            }
            final Method displayStackMethod = soupBase.getClass().getMethod("getDisplayStack");
            final Object value = displayStackMethod.invoke(soupBase);
            return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    static ItemStack fishSoupBaseIngredientStack(final ResourceLocation soupBaseId) {
        if (isCodSoupBase(soupBaseId)) {
            return new ItemStack(Items.COD);
        }
        if (isSalmonSoupBase(soupBaseId)) {
            return new ItemStack(Items.SALMON);
        }
        if (isPufferfishSoupBase(soupBaseId)) {
            return new ItemStack(Items.PUFFERFISH);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack syntheticWaterSinkSoupMarkerStack() {
        final ItemStack markerStack = new ItemStack(Items.BARRIER);
        MinecraftApiCompat.setCustomName(markerStack, Component.literal(SYNTHETIC_WATER_SINK_TOKEN_NAME));
        return markerStack;
    }

    private static ItemStack syntheticLavaSinkSoupMarkerStack() {
        final ItemStack markerStack = new ItemStack(Items.BARRIER);
        MinecraftApiCompat.setCustomName(markerStack, Component.literal(SYNTHETIC_LAVA_SINK_TOKEN_NAME));
        return markerStack;
    }

    private static Object resolveDeclaredFieldValue(final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                final var field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
