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
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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
import java.util.Arrays;
import java.util.HashSet;
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
    private static final String CFBH_OVEN_BLOCK_CLASS = "net.blay09.mods.cookingforblockheads.block.OvenBlock";
    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";
    private static final String LOCAL_CLIENT_HOOKS_CLASS =
            "org.lab_11.modsunified.impl.cookingforblockheads.client.DungeonOvenClientHooks";

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Unifiled.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Unifiled.MOD_ID);
    private static final RegistryObject<Block> DUNGEON_OVEN = BLOCKS.register(
            "dungeon_oven",
            DungeonOvenCompat::createDungeonOvenBlock
    );
    private static final RegistryObject<Item> DUNGEON_OVEN_ITEM = ITEMS.register(
            "dungeon_oven",
            () -> new BlockItem(DUNGEON_OVEN.get(), resolveBalmItemProperties())
    );

    private static boolean registered;

    private DungeonOvenCompat() {
    }

    private static Block createDungeonOvenBlock() {
        try {
            final Class<?> ovenBlockClass = Class.forName(CFBH_OVEN_BLOCK_CLASS);
            if (!Block.class.isAssignableFrom(ovenBlockClass)) {
                LOGGER.warn("Unable to create dungeon oven block: CFBH oven block class is not a Block.");
                return fallbackBlock();
            }

            // Forge 1.20.1 CFBH OvenBlock exposes a no-arg constructor.
            try {
                final Constructor<?> noArgConstructor = ovenBlockClass.getConstructor();
                final Object created = noArgConstructor.newInstance();
                if (created instanceof Block block) {
                    return block;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to legacy constructor signatures.
            }

            try {
                final Constructor<?> ctorWithProperties = ovenBlockClass.getConstructor(BlockBehaviour.Properties.class);
                final Object created = ctorWithProperties.newInstance(resolveBalmBlockProperties());
                if (created instanceof Block block) {
                    return block;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to legacy constructor signatures.
            }

            try {
                final Constructor<?> ctorWithColorAndProperties =
                        ovenBlockClass.getConstructor(DyeColor.class, BlockBehaviour.Properties.class);
                final Object created = ctorWithColorAndProperties.newInstance(DyeColor.BLACK, resolveBalmBlockProperties());
                if (created instanceof Block block) {
                    return block;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to final fallback.
            }

            LOGGER.warn("Unable to create dungeon oven block: no compatible OvenBlock constructor found.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to create dungeon oven block using CFBH oven implementation.", e);
        }
        return fallbackBlock();
    }

    private static BlockBehaviour.Properties resolveBalmBlockProperties() {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Object blocksApi = balmClass.getMethod("getBlocks").invoke(null);
            final Object properties = blocksApi.getClass().getMethod("blockProperties").invoke(blocksApi);
            if (properties instanceof BlockBehaviour.Properties blockProperties) {
                return blockProperties;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return BlockBehaviour.Properties.of();
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
        registerClientHooks(modEventBus);
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
        if (creativeTabContainsStack(event, "getParentEntries", dungeonOvenStack)
                || creativeTabContainsStack(event, "getSearchEntries", dungeonOvenStack)) {
            return;
        }

        event.accept(dungeonOvenStack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
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
            final Block dungeonOvenBlock = DUNGEON_OVEN.get();
            if (componentType == null || !componentType.isInstance(dungeonOvenBlock)) {
                LOGGER.warn("Unable to attach dungeon oven to CFBH oven category: dungeon oven type does not match oven array component.");
                return;
            }

            final int length = Array.getLength(rawOvens);
            for (int i = 0; i < length; i++) {
                if (Array.get(rawOvens, i) == dungeonOvenBlock) {
                    return;
                }
            }

            final Object updated = Array.newInstance(componentType, length + 1);
            for (int i = 0; i < length; i++) {
                Array.set(updated, i, Array.get(rawOvens, i));
            }
            Array.set(updated, length, dungeonOvenBlock);
            ovensField.set(null, updated);
            LOGGER.info("Attached dungeon oven block to CFBH oven category.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to attach dungeon oven block to CFBH oven category.", e);
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
