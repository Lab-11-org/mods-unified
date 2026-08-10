package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonOvenCompat {
    public static final String DUNGEON_OVEN_MARKER_KEY = BridgeKeys.MARKER_DUNGEON_OVEN;
    public static final ResourceLocation DUNGEON_OVEN_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "dungeon_oven");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS;
    private static final String[] CFBH_BLOCK_ENTITY_REGISTRY_CLASS_CANDIDATES = {
            "net.blay09.mods.cookingforblockheads.block.entity.ModBlockEntities",
            "net.blay09.mods.cookingforblockheads.tile.ModBlockEntities"
    };
    private static final String[] CFBH_BLOCK_REGISTRY_CLASS_CANDIDATES = {
            "net.blay09.mods.cookingforblockheads.block.ModBlocks"
    };
    private static final String CFBH_OVEN_DEFERRED_FIELD = "oven";
    private static final String[] CFBH_OVENS_FIELD_CANDIDATES = {"ovens", "dyedOvens"};
    private static final String BLOCK_ENTITY_VALID_BLOCKS_FIELD = "validBlocks";
    private static final String CFBH_DYED_OVEN_BLOCK_CLASS =
            "net.blay09.mods.cookingforblockheads.block.DyedOvenBlock";
    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";
    private static final String LOCAL_CLIENT_HOOKS_CLASS =
            "org.lab_11.modsunified.impl.cookingforblockheads.client.DungeonOvenClientHooks";

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Unifiled.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Unifiled.MOD_ID);
    private static final List<RegistryObject<Block>> DUNGEON_OVEN_BLOCKS = new ArrayList<>();
    private static final List<RegistryObject<Item>> DUNGEON_OVEN_ITEMS = new ArrayList<>();
    static {
        for (final DyeColor color : DyeColor.values()) {
            final String id = color == DyeColor.BLACK
                    ? "dungeon_oven"
                    : color.getName() + "_dungeon_oven";
            final RegistryObject<Block> block = BLOCKS.register(id, () -> createDungeonOvenBlock(color));
            DUNGEON_OVEN_BLOCKS.add(block);
            DUNGEON_OVEN_ITEMS.add(ITEMS.register(
                    id,
                    () -> new BlockItem(block.get(), resolveBalmItemProperties())
            ));
        }
    }

    private static boolean registered;

    private DungeonOvenCompat() {
    }

    private static Block createDungeonOvenBlock(final DyeColor color) {
        try {
            final Class<?> ovenBlockClass = Class.forName(CFBH_DYED_OVEN_BLOCK_CLASS);
            if (!Block.class.isAssignableFrom(ovenBlockClass)) {
                LOGGER.warn("Unable to create dungeon oven block: CFBH dyed oven block class is not a Block.");
                return fallbackBlock();
            }

            final Constructor<?> constructor = ovenBlockClass.getConstructor(DyeColor.class);
            final Object created = constructor.newInstance(color);
            if (created instanceof Block block) {
                return block;
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to create {} dungeon oven block using CFBH dyed oven implementation.", color, e);
        }
        return fallbackBlock();
    }

    private static Item.Properties resolveBalmItemProperties() {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Object itemsApi = balmClass.getMethod("getItems").invoke(null);
            final Object properties = itemsApi.getClass().getMethod("itemProperties").invoke(itemsApi);
            if (properties instanceof Item.Properties itemProperties) {
                return itemProperties;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return new Item.Properties();
    }

    private static Block fallbackBlock() {
        return new Block(BlockBehaviour.Properties.of());
    }

    public static void register(final IEventBus modEventBus) {
        if (registered) {
            return;
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(DungeonOvenCompat::onCommonSetup);
        modEventBus.addListener(DungeonOvenCompat::onBuildCreativeModeTabContents);
        MinecraftForge.EVENT_BUS.addListener(DungeonOvenCompat::onLootTableLoad);
        registerClientHooks(modEventBus);
        registered = true;
        LOGGER.info("Registered {} dungeon oven blocks and items.", DUNGEON_OVEN_BLOCKS.size());
    }

    public static boolean isDungeonOvenBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        return blockId != null
                && Unifiled.MOD_ID.equals(blockId.getNamespace())
                && ("dungeon_oven".equals(blockId.getPath()) || blockId.getPath().endsWith("_dungeon_oven"));
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
            final Class<?> modBlockEntitiesClass = resolveFirstPresentClass(CFBH_BLOCK_ENTITY_REGISTRY_CLASS_CANDIDATES);
            if (modBlockEntitiesClass == null) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven block entity type: ModBlockEntities class is unavailable.");
                return;
            }

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
            if (!updatedBlocks.addAll(allDungeonOvenBlocks())) {
                return;
            }

            validBlocksField.set(ovenBlockEntityType, Set.copyOf(updatedBlocks));
            LOGGER.info("Attached dungeon oven blocks to CFBH oven block entity type.");
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
        for (final RegistryObject<Item> item : DUNGEON_OVEN_ITEMS) {
            final ItemStack dungeonOvenStack = new ItemStack(item.get());
            if (creativeTabContainsStack(event, "getParentEntries", dungeonOvenStack)
                    || creativeTabContainsStack(event, "getSearchEntries", dungeonOvenStack)) {
                continue;
            }
            event.accept(dungeonOvenStack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean creativeTabContainsStack(final BuildCreativeModeTabContentsEvent event,
                                                    final String accessorMethod,
                                                    final ItemStack stack) {
        try {
            final Method method = event.getClass().getMethod(accessorMethod);
            final Object entries = method.invoke(event);
            if (entries instanceof java.util.Collection<?> collection) {
                return ((java.util.Collection<ItemStack>) collection).contains(stack);
            }
        } catch (ReflectiveOperationException ignored) {
            // Legacy 1.20 tab event API does not expose parent/search entry collections.
        }
        return false;
    }

    private static void attachDungeonOvenToCfbhOvenCategory() {
        try {
            final Class<?> modBlocksClass = resolveFirstPresentClass(CFBH_BLOCK_REGISTRY_CLASS_CANDIDATES);
            if (modBlocksClass == null) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: ModBlocks class is unavailable.");
                return;
            }

            final Field ovensField = resolveFirstPresentField(modBlocksClass, CFBH_OVENS_FIELD_CANDIDATES);
            if (ovensField == null) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: oven array field is unavailable.");
                return;
            }
            final Object rawOvens = ovensField.get(null);
            if (rawOvens == null || !rawOvens.getClass().isArray()) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: ovens field is not an array.");
                return;
            }

            final Class<?> componentType = rawOvens.getClass().getComponentType();
            final List<Block> dungeonOvenBlocks = allDungeonOvenBlocks();
            if (componentType == null || dungeonOvenBlocks.stream().anyMatch(block -> !componentType.isInstance(block))) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: dungeon oven type does not match oven array component.");
                return;
            }

            final int length = Array.getLength(rawOvens);
            final List<Block> missingBlocks = new ArrayList<>();
            for (final Block dungeonOvenBlock : dungeonOvenBlocks) {
                boolean present = false;
                for (int i = 0; i < length; i++) {
                    if (Array.get(rawOvens, i) == dungeonOvenBlock) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    missingBlocks.add(dungeonOvenBlock);
                }
            }
            if (missingBlocks.isEmpty()) {
                return;
            }

            final Object updated = Array.newInstance(componentType, length + missingBlocks.size());
            for (int i = 0; i < length; i++) {
                Array.set(updated, i, Array.get(rawOvens, i));
            }
            for (int i = 0; i < missingBlocks.size(); i++) {
                Array.set(updated, length + i, missingBlocks.get(i));
            }
            ovensField.set(null, updated);
            LOGGER.info("Attached dungeon oven blocks to CFBH oven category.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to attach dungeon oven block to CFBH oven category.", e);
        }
    }

    private static List<Block> allDungeonOvenBlocks() {
        final List<Block> blocks = new ArrayList<>(DUNGEON_OVEN_BLOCKS.size());
        for (final RegistryObject<Block> block : DUNGEON_OVEN_BLOCKS) {
            blocks.add(block.get());
        }
        return blocks;
    }

    private static void onLootTableLoad(final LootTableLoadEvent event) {
        for (final RegistryObject<Item> item : DUNGEON_OVEN_ITEMS) {
            final ResourceLocation itemId = item.getId();
            if (itemId == null || !event.getName().equals(MinecraftApiCompat.resourceLocation(
                    itemId.getNamespace(), "blocks/" + itemId.getPath()))) {
                continue;
            }
            event.setTable(LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .when(ExplosionCondition.survivesExplosion())
                            .add(LootItem.lootTableItem(item.get())))
                    .build());
            return;
        }
    }

    private static void registerClientHooks(final IEventBus modEventBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        try {
            final Class<?> hooksClass = Class.forName(LOCAL_CLIENT_HOOKS_CLASS);
            hooksClass.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            LOGGER.info("Registered dungeon oven client hooks.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register dungeon oven client hooks.", e);
        }
    }

    private static Class<?> resolveFirstPresentClass(final String[] classNameCandidates) {
        for (final String className : classNameCandidates) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Try the next known CFBH class layout.
            }
        }
        return null;
    }

    private static Field resolveFirstPresentField(final Class<?> ownerClass, final String[] fieldNameCandidates) {
        for (final String fieldName : fieldNameCandidates) {
            try {
                return ownerClass.getField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // Try the next known field name.
            }
        }
        return null;
    }
}
