package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class RuntimeSmokeGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RuntimeSmokeGameTest() {
    }

    @GameTest(template = "bastion/mobs/empty")
    public static void bootstrapsSupportedBridges(final GameTestHelper helper) {
        RuntimeSmokeGameTestSupport.verify(helper, LOGGER, List.of(
                BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT,
                BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT,
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT,
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT
        ), true, false);
        verifyWrappedContainerToken(helper);
        verifyKaleidoscopeCarrierRouting(helper);
        helper.succeed();
    }

    private static void verifyWrappedContainerToken(final GameTestHelper helper) {
        try {
            final ItemStack bowl = new ItemStack(Items.BOWL);
            final Object token = CfbhRuntime.newStaticIngredientToken(bowl);
            final Object provider = CfbhRuntime.newKitchenItemProviderProxy(new CfbhRuntime.KitchenItemProviderView() {
                @Override
                public Object findByIngredient(final Ingredient ingredient, final Collection<?> allocatedTokens) {
                    return ingredient.test(bowl) ? token : null;
                }

                @Override
                public Object findByItem(final ItemStack itemStack, final Collection<?> allocatedTokens) {
                    return MinecraftApiCompat.isSameItemSameData(itemStack, bowl) ? token : null;
                }
            });
            final Object combined = Class.forName(
                            "net.blay09.mods.cookingforblockheads.kitchen.CombinedKitchenItemProvider")
                    .getConstructor(List.class)
                    .newInstance(List.of(provider));
            final Object wrapped = CfbhRuntime.findItemToken(combined, bowl, List.of());
            if (wrapped == null || CfbhRuntime.peekIngredientToken(wrapped).isEmpty()
                    || CfbhRuntime.consumeIngredientToken(wrapped).isEmpty()
                    || !CfbhRuntime.restoreIngredientToken(wrapped, bowl.copy()).isEmpty()) {
                helper.fail("Wrapped fridge container tokens cannot complete peek, consume and restore");
            }

            final Class<?> containerProviderClass = Class.forName(
                    "net.blay09.mods.cookingforblockheads.kitchen.ContainerKitchenItemProvider");
            final Object firstProvider = containerProviderClass.getConstructor(Container.class)
                    .newInstance(new SimpleContainer(bowl.copy()));
            final Object secondProvider = containerProviderClass.getConstructor(Container.class)
                    .newInstance(new SimpleContainer(bowl.copy()));
            final List<Object> providers = List.of(firstProvider, secondProvider);
            final Map<Object, List<Object>> allocatedByProvider = new IdentityHashMap<>();
            final Object firstToken = CfbhRuntime.findAndReserveItemToken(
                    providers, bowl, allocatedByProvider);
            final Object secondToken = CfbhRuntime.findAndReserveItemToken(
                    providers, bowl, allocatedByProvider);
            if (firstToken == null || secondToken == null
                    || CfbhRuntime.peekIngredientToken(firstToken).isEmpty()
                    || CfbhRuntime.peekIngredientToken(secondToken).isEmpty()) {
                helper.fail("Independent kitchen containers share token reservations");
            }

            final Object stackedProvider = containerProviderClass.getConstructor(Container.class)
                    .newInstance(new SimpleContainer(bowl.copyWithCount(2)));
            final Map<Object, List<Object>> stackedReservations = new IdentityHashMap<>();
            if (CfbhRuntime.findAndReserveItemToken(
                    List.of(stackedProvider), bowl, stackedReservations) == null
                    || CfbhRuntime.findAndReserveItemToken(
                    List.of(stackedProvider), bowl, stackedReservations) == null
                    || CfbhRuntime.findAndReserveItemToken(
                    List.of(stackedProvider), bowl, stackedReservations) != null) {
                helper.fail("One kitchen container stack does not reserve each item exactly once");
            }

            final Object consumedProvider = containerProviderClass.getConstructor(Container.class)
                    .newInstance(new SimpleContainer(bowl.copyWithCount(2)));
            for (int i = 0; i < 2; i++) {
                final Object consumedToken = CfbhRuntime.findAndReserveItemToken(
                        List.of(consumedProvider), bowl, new IdentityHashMap<>());
                if (consumedToken == null || CfbhRuntime.consumeIngredientToken(consumedToken).isEmpty()) {
                    helper.fail("Kitchen container stacks cannot be consumed item by item");
                }
            }
            if (CfbhRuntime.findAndReserveItemToken(
                    List.of(consumedProvider), bowl, new IdentityHashMap<>()) != null) {
                helper.fail("Consumed kitchen containers remain available");
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not construct the Cooking for Blockheads fridge provider wrapper: " + e);
        }
    }

    private static void verifyKaleidoscopeCarrierRouting(final GameTestHelper helper) {
        if (CookingPotBridgeCatalog.findByKey(BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT)
                .filter(target -> target.missingRequiredModIds().isEmpty()).isEmpty()) {
            return;
        }
        final var recipeManager = helper.getLevel().getServer().getRecipeManager();
        final CookingPotIndexedRecipe carrierRecipe = CookingPotRecipeIndexer.indexedRecipes(recipeManager).stream()
                .filter(CookingPotIndexedRecipe.class::isInstance)
                .map(CookingPotIndexedRecipe.class::cast)
                .filter(recipe -> BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(recipe.targetKey()))
                .filter(recipe -> !recipe.indexedContainerCost().isEmpty())
                .findFirst()
                .orElse(null);
        if (carrierRecipe == null) {
            helper.fail("Kaleidoscope pot exposed no carrier recipe");
            return;
        }

        final ItemStack expectedCarrier = carrierRecipe.indexedContainerCost();
        final var registryAccess = helper.getLevel().registryAccess();
        if (!sameStack(expectedCarrier, CookingPotContainerCost.resolveForCraft(carrierRecipe, registryAccess, false))
                || !sameStack(expectedCarrier, CookingPotContainerCost.resolveForTooltip(carrierRecipe, registryAccess))) {
            helper.fail("Kaleidoscope carrier cost was not preserved by live craft/status resolution");
            return;
        }

        final Object carrierToken = CfbhRuntime.newStaticIngredientToken(expectedCarrier.copyWithCount(1));
        final Object provider = CfbhRuntime.newKitchenItemProviderProxy(new CfbhRuntime.KitchenItemProviderView() {
            @Override
            public Object findByIngredient(final Ingredient ingredient, final Collection<?> allocatedTokens) {
                return ingredient.test(expectedCarrier) ? carrierToken : null;
            }

            @Override
            public Object findByItem(final ItemStack itemStack, final Collection<?> allocatedTokens) {
                return MinecraftApiCompat.isSameItemSameData(itemStack, expectedCarrier) ? carrierToken : null;
            }
        });
        final boolean[] carrierRouted = {false};
        final Object processor = CfbhRuntime.newKitchenItemProcessorProxy(new CfbhRuntime.KitchenItemProcessorView() {
            @Override
            public boolean canProcess(final RecipeType<?> recipeType) {
                return recipeType == carrierRecipe.getType();
            }

            @Override
            public Object processRecipe(final Recipe<?> recipe, final List<?> ingredientTokens) {
                carrierRouted[0] = ingredientTokens.stream()
                        .map(CfbhRuntime::peekIngredientToken)
                        .anyMatch(stack -> sameStack(stack, expectedCarrier.copyWithCount(1)));
                return CfbhRuntime.kitchenOperationEmpty();
            }
        });
        if (provider == null || processor == null) {
            helper.fail("Cooking for Blockheads runtime proxies were unavailable");
            return;
        }

        new CookingPotKitchenHandler().assemble(
                new SmokeCraftingContext(List.of(provider), List.of(processor)),
                carrierRecipe,
                List.of(),
                registryAccess
        );
        if (!carrierRouted[0]) {
            helper.fail("Kaleidoscope carrier token was not routed to the cooking-pot processor");
        }
    }

    private static boolean sameStack(final ItemStack left, final ItemStack right) {
        return left.getCount() == right.getCount() && MinecraftApiCompat.isSameItemSameData(left, right);
    }

    static final class SmokeCraftingContext {
        private final List<?> itemProviders;
        private final List<?> itemProcessors;

        SmokeCraftingContext(final List<?> itemProviders, final List<?> itemProcessors) {
            this.itemProviders = itemProviders;
            this.itemProcessors = itemProcessors;
        }

        public List<?> getItemProviders() {
            return itemProviders;
        }

        public List<?> getItemProcessors() {
            return itemProcessors;
        }
    }
}
