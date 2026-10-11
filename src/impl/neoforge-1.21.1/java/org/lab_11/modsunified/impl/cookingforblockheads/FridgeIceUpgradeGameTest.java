package org.lab_11.modsunified.impl.cookingforblockheads;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FridgeIceUpgradeGameTest {
    private FridgeIceUpgradeGameTest() {}

    @GameTest(template = "bastion/mobs/empty")
    public static void transfersVirtualAndRealSnowballs(final GameTestHelper helper) throws Exception {
        verify(helper, false);
    }

    @GameTest(template = "bastion/mobs/empty")
    public static void transfersKaleidoscopeSnowballs(final GameTestHelper helper) throws Exception {
        verify(helper, true);
    }

    private static void verify(final GameTestHelper helper, final boolean kaleidoscope) throws Exception {
        final var level = helper.getLevel();
        final var manager = level.getRecipeManager();
        final var player = FakePlayerFactory.getMinecraft(level);
        final boolean addedPlayer = !level.players().contains(player);
        final List<RecipeHolder<?>> original = new ArrayList<>(manager.getRecipes());
        // Test-only datapack-style pot recipe: vanilla FD has no snowball cooking recipe.
        // Installing it only in this test exercises the real serializer/index/menu/transfer path.
        final var fixture = new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("lab11_test", "snowball_stew"),
                Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(kaleidoscope ? """
                {"type":"kaleidoscope_cookery:pot","time":200,"stir_fry_count":3,
                 "ingredients":[{"item":"minecraft:snowball"}],
                 "result":{"id":"minecraft:cooked_beef"}}
                """ : """
                {"type":"farmersdelight:cooking","cookingtime":200,"experience":0,
                 "ingredients":[{"item":"minecraft:snowball"}],
                 "result":{"id":"farmersdelight:beef_stew"}}
                """).getAsJsonObject()).getOrThrow());
        try {
            final List<RecipeHolder<?>> withFixture = new ArrayList<>(original);
            withFixture.add(fixture);
            manager.replaceRecipes(withFixture);
            refresh(manager, level);
            final BlockPos tablePos = new BlockPos(1, 1, 1);
            final BlockPos fridgePos = tablePos.west();
            final BlockPos stovePos = tablePos.east();
            final BlockPos potPos = stovePos.above();
            helper.setBlock(tablePos, block("cookingforblockheads:white_cooking_table"));
            helper.setBlock(fridgePos, block("cookingforblockheads:white_fridge"));
            helper.setBlock(stovePos, block("farmersdelight:stove").defaultBlockState()
                    .setValue(BlockStateProperties.LIT, true));
            helper.setBlock(potPos, block(kaleidoscope ? "kaleidoscope_cookery:pot" : "farmersdelight:cooking_pot"));
            final BlockEntity fridge = helper.getBlockEntity(fridgePos);
            final Container inventory = (Container) fridge.getClass().getMethod("getContainer").invoke(fridge);
            inventory.setItem(0, new ItemStack(Items.SNOWBALL, 4));
            inventory.setItem(1, new ItemStack(Items.BOWL, 16));
            final BlockEntity pot = helper.getBlockEntity(potPos);
            final IItemHandler potInventory = kaleidoscope ? null
                    : (IItemHandler) pot.getClass().getMethod("getInventory").invoke(pot);
            if (kaleidoscope) {
                inventory.setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "oil")), 16));
            }
            player.setPos(helper.absolutePos(tablePos).getCenter());
            // Interactive cookware resolves a nearby player from the level.
            if (addedPlayer) level.players().add(player);
            // FakePlayer discards outbound packets, but NeoForge checks connection attributes first.
            // Supply an inert local channel; no client packet delivery is claimed by this test.
            final var connection = player.connection.getConnection();
            if (connection.channel() == null) {
                final var channelField = net.minecraft.network.Connection.class.getDeclaredField("channel");
                channelField.setAccessible(true);
                channelField.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
            }
            final Class<?> menuClass = Class.forName("net.blay09.mods.cookingforblockheads.menu.KitchenMenu");
            final Class<?> kitchenClass = Class.forName("net.blay09.mods.cookingforblockheads.crafting.KitchenImpl");
            final Object kitchen = kitchenClass.getConstructor(Level.class, BlockPos.class)
                    .newInstance(level, helper.absolutePos(tablePos));
            final Object menu = menuClass.getConstructor(MenuType.class, int.class, Player.class, kitchenClass)
                    .newInstance(null, 1, player, kitchen);
            final Recipe<?> indexed = CookingPotRecipeIndexer.indexedRecipes(manager).stream()
                    .filter(CookingPotIndexedRecipe.class::isInstance)
                    .map(CookingPotIndexedRecipe.class::cast)
                    .filter(recipe -> recipe.delegateRecipe() == fixture.value()).findFirst().orElseThrow();
            final ResourceLocation indexedId = CookingPotRecipeIndexer.indexedRecipeIds(manager).stream()
                    .filter(id -> CookingPotRecipeIndexer.findIndexedRecipe(id) == indexed).findFirst().orElseThrow();
            final var craft = menuClass.getMethod("craft", ResourceLocation.class, List.class, boolean.class, boolean.class);
            final NonNullList<ItemStack> matrix = NonNullList.withSize(indexed.getIngredients().size(), ItemStack.EMPTY);

            fridge.getClass().getMethod("setHasIceUpgrade", boolean.class).invoke(fridge, true);
            menuClass.getMethod("broadcastAvailableRecipes").invoke(menu);
            menuClass.getMethod("handleRequestSelectionRecipes", ItemStack.class, ResourceLocation.class, List.class)
                    .invoke(menu, fixture.value().getResultItem(level.registryAccess()), indexedId, matrix);
            menuClass.getMethod("broadcastRecipesForResultItem", ItemStack.class)
                    .invoke(menu, fixture.value().getResultItem(level.registryAccess()));
            if (kaleidoscope) {
                for (int i = 1; i <= 3; i++) {
                    craft.invoke(menu, indexedId, matrix, false, false);
                    final List<?> inputs = (List<?>) pot.getClass().getMethod("getInputs").invoke(pot);
                    check(inputs.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast)
                            .anyMatch(stack -> stack.is(Items.SNOWBALL) && stack.getCount() == 1),
                            "Kaleidoscope virtual snowball was not transferred on craft " + i);
                    check(inventory.getItem(0).getCount() == 4, "Kaleidoscope consumed real snowballs with ice upgrade");
                    pot.getClass().getMethod("reset").invoke(pot);
                }
                LogUtils.getLogger().info("LAB11 E2E Kaleidoscope virtual snowball transfers passed: 3, real stack preserved");
                helper.succeed();
                return;
            }
            for (int i = 1; i <= 3; i++) {
                craft.invoke(menu, indexedId, matrix, false, false);
                check(countSnowballs(potInventory) == i, "Virtual snowball was not transferred on craft " + i);
                check(inventory.getItem(0).getCount() == 4, "Ice upgrade consumed real snowballs");
            }
            fridge.getClass().getMethod("setHasIceUpgrade", boolean.class).invoke(fridge, false);
            craft.invoke(menu, indexedId, matrix, false, false);
            check(countSnowballs(potInventory) == 4 && inventory.getItem(0).getCount() == 3,
                    "Removing ice upgrade did not restore real-stack consumption");
            inventory.setItem(0, ItemStack.EMPTY);
            craft.invoke(menu, indexedId, matrix, false, false);
            check(countSnowballs(potInventory) == 4, "Transfer succeeded without snowballs or ice upgrade");
            fridge.getClass().getMethod("setHasIceUpgrade", boolean.class).invoke(fridge, true);
            craft.invoke(menu, indexedId, matrix, false, false);
            check(countSnowballs(potInventory) == 5 && inventory.getItem(0).isEmpty(),
                    "Ice upgrade did not provide snowballs from an empty fridge slot");
            check(inventory.getItem(1).getCount() == 11, "Serving container accounting changed");
            LogUtils.getLogger().info("LAB11 E2E snowball transfer passed: virtual=3, real=1, unavailable=blocked, virtual-empty=1");
            helper.succeed();
        } finally {
            if (addedPlayer) level.players().remove(player);
            manager.replaceRecipes(original);
            refresh(manager, level);
        }
    }

    private static void refresh(final RecipeManager manager, final Level level) {
        CookingPotRecipeIndexer.injectRecipes(manager, level.registryAccess(), "snowball_regression",
                CookingPotBridgeCatalog.resolveActiveTargets(LogUtils.getLogger()));
    }

    private static Block block(final String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new AssertionError("Missing block " + id));
    }

    private static int countSnowballs(final IItemHandler inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(Items.SNOWBALL)) {
                count += inventory.getStackInSlot(slot).getCount();
            }
        }
        return count;
    }

    private static void check(final boolean value, final String message) {
        if (!value) throw new AssertionError(message);
    }
}
