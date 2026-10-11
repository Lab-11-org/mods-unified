package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.List;

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
                BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT,
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT,
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT
        ), false, true);
        helper.succeed();
    }

    @GameTest(template = "bastion/mobs/empty")
    public static void placesBasketWithCentralKitchen(final GameTestHelper helper) {
        if (!Boolean.getBoolean("lab11.centralKitchenRuntime")) {
            helper.succeed();
            return;
        }
        if (!org.lab_11.modsunified.impl.platform.LoaderApiCompat.isModLoaded("create_central_kitchen")) {
            helper.fail("Central Kitchen is required for this regression profile");
            return;
        }
        final var basket = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                new net.minecraft.resources.ResourceLocation("farmersdelight", "basket"));
        final var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos, basket);
        if (helper.getBlockEntity(pos) == null) {
            helper.fail("Basket did not create its block entity");
            return;
        }
        helper.destroyBlock(pos);
        helper.setBlock(pos, basket);
        helper.succeed();
    }
    @GameTest(template = "bastion/mobs/empty")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void taggedCabinetProviderSurvivesDeferredAttachment(final GameTestHelper helper) throws Exception {
        final var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        final var cabinet = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                new net.minecraft.resources.ResourceLocation("farmersdelight", "oak_cabinet")).orElseThrow();
        helper.setBlock(pos, cabinet);
        final var entity = helper.getBlockEntity(pos);
        final var items = entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(() -> new AssertionError("Cabinet item handler is missing"));
        items.insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL, 3), false);
        final Class<?> api = Class.forName("net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider");
        final Object providers = Class.forName("net.blay09.mods.balm.api.Balm").getMethod("getProviders").invoke(null);
        final var capability = (net.minecraftforge.common.capabilities.Capability<Object>) providers.getClass()
                .getMethod("getCapability", Class.class).invoke(providers, api);
        final var lazy = entity.getCapability(capability);
        final Object provider = lazy.orElseThrow(() -> new AssertionError("Tagged cabinet kitchen provider is missing"));
        final var stack = (net.minecraft.world.item.ItemStack) api.getMethod("getStackInSlot", int.class)
                .invoke(provider, 0);
        if (!stack.is(net.minecraft.world.item.Items.BOWL) || stack.getCount() != 3) {
            helper.fail("Deferred kitchen provider cannot see cabinet contents");
        }
        entity.invalidateCaps();
        if (lazy.isPresent() || entity.getCapability(capability).isPresent()) {
            helper.fail("Deferred kitchen provider survived capability invalidation");
        }
        helper.succeed();
    }

}
