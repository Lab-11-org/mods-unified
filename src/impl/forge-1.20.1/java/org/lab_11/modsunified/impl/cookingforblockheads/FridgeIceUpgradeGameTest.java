package org.lab_11.modsunified.impl.cookingforblockheads;

import com.google.gson.JsonParser;
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
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

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
        final List<Recipe<?>> original = new ArrayList<>(manager.getRecipes());
        // Test-only datapack-style pot recipe: vanilla FD has no snowball cooking recipe.
        // Installing it only in this test exercises the real serializer/index/menu/transfer path.
        final Recipe<?> fixture = RecipeManager.fromJson(new ResourceLocation("lab11_test", "snowball_stew"),
                JsonParser.parseString(kaleidoscope ? """
                {"type":"kaleidoscope_cookery:pot","time":200,"stir_fry_count":3,
                 "ingredients":[{"item":"minecraft:snowball"}],
                 "result":{"item":"minecraft:cooked_beef"}}
                """ : """
                {"type":"farmersdelight:cooking","cookingtime":200,"experience":0,
                 "ingredients":[{"item":"minecraft:snowball"}],
                 "result":{"item":"farmersdelight:beef_stew"}}
                """).getAsJsonObject());
        try {
            final List<Recipe<?>> withFixture = new ArrayList<>(original);
            withFixture.add(fixture);
            manager.replaceRecipes(withFixture);
            refresh(manager, level);
            final BlockPos tablePos = new BlockPos(1, 1, 1);
            final BlockPos fridgePos = tablePos.west();
            final BlockPos stovePos = tablePos.east();
            final BlockPos potPos = stovePos.above();
            helper.setBlock(tablePos, block("cookingforblockheads:cooking_table"));
            helper.setBlock(fridgePos, block("cookingforblockheads:fridge"));
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
                        new ResourceLocation("kaleidoscope_cookery", "oil")), 16));
            }
            final var player = FakePlayerFactory.getMinecraft(level);
            final Class<?> menuClass = Class.forName("net.blay09.mods.cookingforblockheads.menu.RecipeBookMenu");
            final Class<?> kitchenClass = Class.forName("net.blay09.mods.cookingforblockheads.KitchenMultiBlock");
            final Object kitchen = kitchenClass.getMethod("buildFromLocation", Level.class, BlockPos.class)
                    .invoke(null, level, helper.absolutePos(tablePos));
            final Object menu = menuClass.getConstructor(MenuType.class, int.class, Player.class)
                    .newInstance(null, 1, player);
            menuClass.getMethod("allowCrafting").invoke(menu);
            menuClass.getMethod("setKitchenMultiBlock", kitchenClass).invoke(menu, kitchen);
            final Class<?> typeClass = Class.forName("net.blay09.mods.cookingforblockheads.registry.FoodRecipeType");
            final Object type = typeClass.getField("CRAFTING").get(null);
            final var craft = menuClass.getMethod("tryCraft", ItemStack.class, typeClass, NonNullList.class, boolean.class);
            final ItemStack result = fixture.getResultItem(level.registryAccess());
            final NonNullList<ItemStack> matrix = NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.SNOWBALL));

            fridge.getClass().getMethod("setHasIceUpgrade", boolean.class).invoke(fridge, true);
            if (kaleidoscope) {
                for (int i = 1; i <= 3; i++) {
                    craft.invoke(menu, result, type, matrix, false);
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
                craft.invoke(menu, result, type, matrix, false);
                check(countSnowballs(potInventory) == i, "Virtual snowball was not transferred on craft " + i);
                check(inventory.getItem(0).getCount() == 4, "Ice upgrade consumed real snowballs");
            }
            fridge.getClass().getMethod("setHasIceUpgrade", boolean.class).invoke(fridge, false);
            craft.invoke(menu, result, type, matrix, false);
            check(countSnowballs(potInventory) == 4 && inventory.getItem(0).getCount() == 3,
                    "Removing ice upgrade did not restore real-stack consumption");
            inventory.setItem(0, ItemStack.EMPTY);
            craft.invoke(menu, result, type, matrix, false);
            check(countSnowballs(potInventory) == 4, "Transfer succeeded without snowballs or ice upgrade");
            fridge.getClass().getMethod("setHasIceUpgrade", boolean.class).invoke(fridge, true);
            craft.invoke(menu, result, type, matrix, false);
            check(countSnowballs(potInventory) == 5 && inventory.getItem(0).isEmpty(),
                    "Ice upgrade did not provide snowballs from an empty fridge slot");
            check(inventory.getItem(1).getCount() == 11, "Serving container accounting changed");
            LogUtils.getLogger().info("LAB11 E2E snowball transfer passed: virtual=3, real=1, unavailable=blocked, virtual-empty=1");
            helper.succeed();
        } finally {
            manager.replaceRecipes(original);
            refresh(manager, level);
        }
    }

    private static void refresh(final RecipeManager manager, final Level level) {
        CookingPotRecipeIndexer.injectRecipes(manager, level.registryAccess(), "snowball_regression",
                CookingPotBridgeCatalog.resolveActiveTargets(LogUtils.getLogger()));
    }

    private static Block block(final String id) {
        return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(id)).orElseThrow(() -> new AssertionError("Missing block " + id));
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
