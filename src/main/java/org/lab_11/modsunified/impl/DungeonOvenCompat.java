package org.lab_11.modsunified.impl;

import com.mojang.logging.LogUtils;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lab_11.modsunified.Unifiled;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class DungeonOvenCompat {
    public static final String DUNGEON_OVEN_MARKER_KEY = "dungeon_oven";
    public static final ResourceLocation DUNGEON_OVEN_ID =
            ResourceLocation.fromNamespaceAndPath(Unifiled.MOD_ID, "dungeon_oven");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = "cookingforblockheads";
    private static final String CFBH_BLOCK_ENTITY_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.block.entity.ModBlockEntities";
    private static final String CFBH_BLOCK_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.block.ModBlocks";
    private static final String CFBH_OVEN_DEFERRED_FIELD = "oven";
    private static final String CFBH_OVENS_FIELD = "ovens";
    private static final String BLOCK_ENTITY_VALID_BLOCKS_FIELD = "validBlocks";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Unifiled.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Unifiled.MOD_ID);
    private static final DeferredHolder<Block, Block> DUNGEON_OVEN = BLOCKS.register(
            "dungeon_oven",
            () -> new OvenBlock(DyeColor.BLACK, Balm.getBlocks().blockProperties())
    );
    private static final DeferredHolder<Item, BlockItem> DUNGEON_OVEN_ITEM = ITEMS.register(
            "dungeon_oven",
            () -> new BlockItem(DUNGEON_OVEN.get(), Balm.getItems().itemProperties())
    );

    private static boolean registered;

    private DungeonOvenCompat() {
    }

    public static void register(final IEventBus modEventBus) {
        if (registered) {
            return;
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(DungeonOvenCompat::onCommonSetup);
        modEventBus.addListener(DungeonOvenCompat::onBuildCreativeModeTabContents);
        registered = true;
        LOGGER.info("Registered dungeon oven block and item.");
    }

    public static boolean isDungeonOvenBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        return DUNGEON_OVEN_ID.equals(BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()));
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            attachDungeonOvenToCfbhOvenBlockEntityType();
            attachDungeonOvenToCfbhOvenCategory();
        });
    }

    private static void onBuildCreativeModeTabContents(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || isCfbhCreativeTab(event.getTabKey())) {
            addDungeonOvenIfMissing(event);
        }
    }

    @SuppressWarnings("unchecked")
    private static void attachDungeonOvenToCfbhOvenBlockEntityType() {
        try {
            final Class<?> modBlockEntitiesClass = Class.forName(CFBH_BLOCK_ENTITY_REGISTRY_CLASS);
            final Field ovenField = modBlockEntitiesClass.getField(CFBH_OVEN_DEFERRED_FIELD);
            final Object deferredObject = ovenField.get(null);
            final Object blockEntityTypeObject = invokeNoArg(deferredObject, "get");
            if (!(blockEntityTypeObject instanceof BlockEntityType<?> ovenBlockEntityType)) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven block entity type: resolved type is invalid.");
                return;
            }

            final Field validBlocksField = resolveValidBlocksField(ovenBlockEntityType.getClass());
            if (validBlocksField == null) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven block entity type: validBlocks field not found.");
                return;
            }
            validBlocksField.setAccessible(true);

            final Object rawValidBlocks = validBlocksField.get(ovenBlockEntityType);
            if (!(rawValidBlocks instanceof Set<?> existing)) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven block entity type: validBlocks field is not a Set.");
                return;
            }

            final Set<Block> updatedBlocks = new HashSet<>((Set<Block>) existing);
            final Block dungeonOvenBlock = DUNGEON_OVEN.get();
            if (!updatedBlocks.add(dungeonOvenBlock)) {
                return;
            }

            validBlocksField.set(ovenBlockEntityType, Set.copyOf(updatedBlocks));
            LOGGER.info("Attached dungeon oven block to CFBH oven block entity type.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to attach dungeon oven block to CFBH oven block entity type.", e);
        }
    }

    private static Field resolveValidBlocksField(final Class<?> blockEntityTypeClass) {
        try {
            return BlockEntityType.class.getDeclaredField(BLOCK_ENTITY_VALID_BLOCKS_FIELD);
        } catch (NoSuchFieldException ignored) {
            for (final Field field : blockEntityTypeClass.getDeclaredFields()) {
                if (Set.class.isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            return null;
        }
    }

    private static Object invokeNoArg(final Object target, final String methodName) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }

        final Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static boolean isCfbhCreativeTab(final net.minecraft.resources.ResourceKey<CreativeModeTab> tabKey) {
        return tabKey != null && COOKING_FOR_BLOCKHEADS_MOD_ID.equals(tabKey.location().getNamespace());
    }

    private static void addDungeonOvenIfMissing(final BuildCreativeModeTabContentsEvent event) {
        final ItemStack dungeonOvenStack = new ItemStack(DUNGEON_OVEN_ITEM.get());
        if (event.getParentEntries().contains(dungeonOvenStack) || event.getSearchEntries().contains(dungeonOvenStack)) {
            return;
        }

        event.accept(dungeonOvenStack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    private static void attachDungeonOvenToCfbhOvenCategory() {
        try {
            final Class<?> modBlocksClass = Class.forName(CFBH_BLOCK_REGISTRY_CLASS);
            final Field ovensField = modBlocksClass.getField(CFBH_OVENS_FIELD);
            final Object rawOvens = ovensField.get(null);
            if (!(rawOvens instanceof OvenBlock[] ovens)) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: ovens field is not an OvenBlock array.");
                return;
            }

            final Block dungeonOvenBlock = DUNGEON_OVEN.get();
            if (!(dungeonOvenBlock instanceof OvenBlock dungeonOvenAsOven)) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: dungeon oven is not an OvenBlock.");
                return;
            }

            for (final OvenBlock oven : ovens) {
                if (oven == dungeonOvenAsOven) {
                    return;
                }
            }

            final OvenBlock[] updated = Arrays.copyOf(ovens, ovens.length + 1);
            updated[ovens.length] = dungeonOvenAsOven;
            ovensField.set(null, updated);
            LOGGER.info("Attached dungeon oven block to CFBH oven category.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to attach dungeon oven block to CFBH oven category.", e);
        }
    }
}
