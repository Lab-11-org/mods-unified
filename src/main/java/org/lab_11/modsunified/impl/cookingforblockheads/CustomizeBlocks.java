package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CustomizeBlocks {
    public static final String DUNGEON_OVEN_MARKER_KEY = BridgeKeys.MARKER_DUNGEON_OVEN;
    public static final ResourceLocation DUNGEON_OVEN_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "dungeon_oven");
    public static final ResourceLocation LAVA_SINK_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, BridgeKeys.BLOCK_LAVA_SINK);
    private static final ResourceLocation ROOT_ADVANCEMENT_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "root");
    private static final ResourceLocation LAVA_SINK_PLACE_ADVANCEMENT_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "lava_sink_place");
    private static final ResourceLocation LAVA_SINK_BARE_HAND_ADVANCEMENT_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "lava_sink_bare_hand");

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
    private static final String LAVA_AMOUNT_TAG = "LavaAmount";
    private static final String LAVA_SINK_BARE_HAND_INTERACTIONS_TAG = "Lab11LavaSinkBareHandInteractions";
    private static final int LAVA_SINK_BARE_HAND_INTERACTIONS_REQUIRED = 2;
    private static final int LAVA_BUCKET_MB = FluidType.BUCKET_VOLUME;
    private static final int LAVA_CAPACITY_MB = Integer.MAX_VALUE;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Unifiled.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Unifiled.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Unifiled.MOD_ID);
    private static final boolean DUNGEON_OVENS_ENABLED =
            LoaderApiCompat.isModLoaded(BridgeKeys.MOD_DUNGEONS_DELIGHT);
    private static final List<DeferredHolder<Block, Block>> DUNGEON_OVEN_VARIANT_BLOCKS = new ArrayList<>();
    private static final List<DeferredHolder<Item, BlockItem>> DUNGEON_OVEN_VARIANT_ITEMS = new ArrayList<>();
    private static final List<DeferredHolder<Block, Block>> LAVA_SINK_VARIANT_BLOCKS = new ArrayList<>();
    private static final List<DeferredHolder<Item, BlockItem>> LAVA_SINK_VARIANT_ITEMS = new ArrayList<>();
    private static final DeferredHolder<Block, Block> LAVA_SINK = BLOCKS.register(
            BridgeKeys.BLOCK_LAVA_SINK,
            () -> new LavaSinkBlock(resolveLavaSinkBlockProperties())
    );
    private static final DeferredHolder<Item, BlockItem> LAVA_SINK_ITEM = ITEMS.register(
            BridgeKeys.BLOCK_LAVA_SINK,
            () -> new BlockItem(LAVA_SINK.get(), resolveBalmItemProperties())
    );
    static {
        if (DUNGEON_OVENS_ENABLED) {
            for (final DyeColor color : DyeColor.values()) {
                final String id = color == DyeColor.BLACK
                        ? "dungeon_oven"
                        : color.getSerializedName() + "_dungeon_oven";
                final DeferredHolder<Block, Block> blockHolder = BLOCKS.register(
                        id,
                        () -> createDungeonOvenBlock(color)
                );
                DUNGEON_OVEN_VARIANT_BLOCKS.add(blockHolder);
                DUNGEON_OVEN_VARIANT_ITEMS.add(ITEMS.register(
                        id,
                        () -> new BlockItem(blockHolder.get(), resolveBalmItemProperties())
                ));
            }
        }

        for (final DyeColor color : DyeColor.values()) {
            final String id = color.getSerializedName() + "_" + BridgeKeys.BLOCK_LAVA_SINK;
            final DeferredHolder<Block, Block> blockHolder = BLOCKS.register(
                    id,
                    () -> new LavaSinkBlock(resolveLavaSinkBlockProperties())
            );
            LAVA_SINK_VARIANT_BLOCKS.add(blockHolder);
            LAVA_SINK_VARIANT_ITEMS.add(ITEMS.register(
                    id,
                    () -> new BlockItem(blockHolder.get(), resolveBalmItemProperties())
            ));
        }
    }
    private static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LavaSinkBlockEntity>> LAVA_SINK_BLOCK_ENTITY_TYPE =
            BLOCK_ENTITY_TYPES.register(
                    BridgeKeys.BLOCK_LAVA_SINK,
                    () -> BlockEntityType.Builder.of(
                            LavaSinkBlockEntity::new,
                            allLavaSinkBlocks().toArray(Block[]::new)
                    ).build(null)
            );
    private static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnamelBasinBridgeBlockEntity>> ENAMEL_BASIN_BRIDGE_TYPE =
            BLOCK_ENTITY_TYPES.register(
                    "enamel_basin_bridge",
                    () -> BlockEntityType.Builder.of(
                            EnamelBasinBridgeBlockEntity::new,
                            BuiltInRegistries.BLOCK.getOptional(MinecraftApiCompat.resourceLocation(
                                    BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "enamel_basin"
                            )).orElse(LAVA_SINK.get())
                    ).build(null)
            );

    private static boolean registered;

    private CustomizeBlocks() {
    }

    private static Block createDungeonOvenBlock() {
        return createDungeonOvenBlock(DyeColor.BLACK);
    }

    private static Block createDungeonOvenBlock(final DyeColor color) {
        try {
            final Class<?> ovenBlockClass = Class.forName(CFBH_OVEN_BLOCK_CLASS);
            if (!Block.class.isAssignableFrom(ovenBlockClass)) {
                LOGGER.warn("Unable to create dungeon oven block: CFBH oven block class is not a Block.");
                return fallbackBlock();
            }

            final Constructor<?> constructor = ovenBlockClass.getConstructor(DyeColor.class, BlockBehaviour.Properties.class);
            final Object created = constructor.newInstance(color, resolveBalmBlockProperties());
            if (created instanceof Block block) {
                return block;
            }
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

    private static BlockBehaviour.Properties resolveLavaSinkBlockProperties() {
        return resolveBalmBlockProperties().noOcclusion();
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
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(CustomizeBlocks::onCommonSetup);
        modEventBus.addListener(CustomizeBlocks::onRegisterCapabilities);
        modEventBus.addListener(CustomizeBlocks::onBuildCreativeModeTabContents);
        registerClientHooks(modEventBus);
        registered = true;
        LOGGER.info("Registered CFBH compatibility blocks (lava sink{}).",
                DUNGEON_OVENS_ENABLED ? ", dungeon oven" : "");
    }

    public static boolean isDungeonOvenBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        return isDungeonOvenBlockId(BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()));
    }

    public static boolean isLavaSinkBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        return isLavaSinkBlockId(BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()));
    }

    public static boolean isEnamelBasinBridge(final BlockEntity blockEntity) {
        return blockEntity instanceof EnamelBasinBridgeBlockEntity;
    }

    public static BlockEntity newEnamelBasinBridge(final BlockPos pos, final BlockState state) {
        return new EnamelBasinBridgeBlockEntity(pos, state);
    }

    public static boolean isDungeonOvenBlockId(final ResourceLocation blockId) {
        if (blockId == null || !Unifiled.MOD_ID.equals(blockId.getNamespace())) {
            return false;
        }
        final String path = blockId.getPath();
        return "dungeon_oven".equals(path) || path.endsWith("_dungeon_oven");
    }

    public static boolean isLavaSinkBlockId(final ResourceLocation blockId) {
        if (blockId == null || !Unifiled.MOD_ID.equals(blockId.getNamespace())) {
            return false;
        }
        final String path = blockId.getPath();
        return BridgeKeys.BLOCK_LAVA_SINK.equals(path) || path.endsWith("_" + BridgeKeys.BLOCK_LAVA_SINK);
    }

    public static BlockEntityType<? extends BlockEntity> lavaSinkBlockEntityType() {
        return LAVA_SINK_BLOCK_ENTITY_TYPE.get();
    }

    public static float lavaSinkFillRatio(final BlockEntity blockEntity) {
        if (!(blockEntity instanceof LavaSinkBlockEntity lavaSinkBlockEntity)) {
            return 0f;
        }
        return lavaSinkBlockEntity.fillRatio();
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        if (!DUNGEON_OVENS_ENABLED) {
            return;
        }
        event.enqueueWork(() -> {
            attachDungeonOvenToCfbhOvenBlockEntityType();
            attachDungeonOvenToCfbhOvenCategory();
        });
    }

    private static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                LAVA_SINK_BLOCK_ENTITY_TYPE.get(),
                (blockEntity, context) -> blockEntity.getFluidHandler()
        );
    }

    private static void onBuildCreativeModeTabContents(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || isCfbhCreativeTab(event.getTabKey())) {
            if (DUNGEON_OVENS_ENABLED) {
                addDungeonOvenIfMissing(event);
            }
            addLavaSinkIfMissing(event);
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
        for (final DeferredHolder<Item, BlockItem> itemHolder : DUNGEON_OVEN_VARIANT_ITEMS) {
            final ItemStack dungeonOvenStack = new ItemStack(itemHolder.get());
            if (creativeTabContainsStack(event, "getParentEntries", dungeonOvenStack)
                    || creativeTabContainsStack(event, "getSearchEntries", dungeonOvenStack)) {
                continue;
            }
            event.accept(dungeonOvenStack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static void addLavaSinkIfMissing(final BuildCreativeModeTabContentsEvent event) {
        final List<DeferredHolder<Item, BlockItem>> allItems = new ArrayList<>(1 + LAVA_SINK_VARIANT_ITEMS.size());
        allItems.add(LAVA_SINK_ITEM);
        allItems.addAll(LAVA_SINK_VARIANT_ITEMS);

        for (final DeferredHolder<Item, BlockItem> itemHolder : allItems) {
            final ItemStack lavaSinkStack = new ItemStack(itemHolder.get());
            if (creativeTabContainsStack(event, "getParentEntries", lavaSinkStack)
                    || creativeTabContainsStack(event, "getSearchEntries", lavaSinkStack)) {
                continue;
            }
            event.accept(lavaSinkStack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
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
        final List<Block> blocks = new ArrayList<>(DUNGEON_OVEN_VARIANT_BLOCKS.size());
        for (final DeferredHolder<Block, Block> blockHolder : DUNGEON_OVEN_VARIANT_BLOCKS) {
            blocks.add(blockHolder.get());
        }
        return blocks;
    }

    private static List<Block> allLavaSinkBlocks() {
        final List<Block> blocks = new ArrayList<>(1 + LAVA_SINK_VARIANT_BLOCKS.size());
        blocks.add(LAVA_SINK.get());
        for (final DeferredHolder<Block, Block> blockHolder : LAVA_SINK_VARIANT_BLOCKS) {
            blocks.add(blockHolder.get());
        }
        return blocks;
    }

    private static void registerClientHooks(final IEventBus modEventBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        try {
            final Class<?> hooksClass = Class.forName(LOCAL_CLIENT_HOOKS_CLASS);
            hooksClass.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            LOGGER.info("Registered compatibility block client hooks.");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register compatibility block client hooks.", e);
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

    private static void grantAdvancement(final Player player, final ResourceLocation advancementId) {
        if (player == null || advancementId == null) {
            return;
        }

        try {
            final Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            if (!serverPlayerClass.isInstance(player)) {
                return;
            }

            final Object server = serverPlayerClass.getMethod("getServer").invoke(player);
            if (server == null) {
                return;
            }

            final Object advancementManager = server.getClass().getMethod("getAdvancements").invoke(server);
            if (advancementManager == null) {
                return;
            }

            final Object advancementHolder = advancementManager
                    .getClass()
                    .getMethod("get", ResourceLocation.class)
                    .invoke(advancementManager, advancementId);
            if (advancementHolder == null) {
                return;
            }

            final Object playerAdvancements = serverPlayerClass.getMethod("getAdvancements").invoke(player);
            if (playerAdvancements == null) {
                return;
            }

            final Method getOrStartProgressMethod = resolveMethodByNameAndArity(
                    playerAdvancements.getClass(),
                    "getOrStartProgress",
                    1
            );
            final Object progress = getOrStartProgressMethod.invoke(playerAdvancements, advancementHolder);
            if (progress == null) {
                return;
            }

            final Method getRemainingCriteriaMethod = resolveMethodByNameAndArity(
                    progress.getClass(),
                    "getRemainingCriteria",
                    0
            );
            final Object remainingCriteriaRaw = getRemainingCriteriaMethod.invoke(progress);
            if (!(remainingCriteriaRaw instanceof Iterable<?> remainingCriteria)) {
                return;
            }

            final List<String> remainingCriteriaNames = new ArrayList<>();
            for (final Object criterion : remainingCriteria) {
                if (criterion != null) {
                    remainingCriteriaNames.add(criterion.toString());
                }
            }

            if (remainingCriteriaNames.isEmpty()) {
                return;
            }

            final Method awardMethod = resolveMethodByNameAndArity(
                    playerAdvancements.getClass(),
                    "award",
                    2
            );
            for (final String criterionName : remainingCriteriaNames) {
                awardMethod.invoke(playerAdvancements, advancementHolder, criterionName);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to grant advancement {} via reflective bridge.", advancementId, e);
        }
    }

    private static void trackBareHandLavaSinkInteraction(final Level level, final Player player) {
        if (level == null || player == null || level.isClientSide()) {
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }

        final CompoundTag persistentData = player.getPersistentData();
        final int nextCount = Math.max(0, persistentData.getInt(LAVA_SINK_BARE_HAND_INTERACTIONS_TAG)) + 1;
        if (nextCount < LAVA_SINK_BARE_HAND_INTERACTIONS_REQUIRED) {
            persistentData.putInt(LAVA_SINK_BARE_HAND_INTERACTIONS_TAG, nextCount);
            return;
        }

        persistentData.putInt(LAVA_SINK_BARE_HAND_INTERACTIONS_TAG, 0);
        grantAdvancement(player, ROOT_ADVANCEMENT_ID);
        grantAdvancement(player, LAVA_SINK_BARE_HAND_ADVANCEMENT_ID);
    }

    private static Method resolveMethodByNameAndArity(final Class<?> ownerClass,
                                                      final String methodName,
                                                      final int arity) throws NoSuchMethodException {
        for (final Method method : ownerClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(ownerClass.getName() + "#" + methodName + "/" + arity);
    }

    private static final class LavaSinkBlock extends Block implements EntityBlock {
        private static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        private LavaSinkBlock(final BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
        }

        @Override
        public BlockState getStateForPlacement(final BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected BlockState rotate(final BlockState state, final Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(final BlockState state, final Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override
        protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public void setPlacedBy(final Level level,
                                final BlockPos pos,
                                final BlockState state,
                                final LivingEntity placer,
                                final ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide() && placer instanceof Player player) {
                grantAdvancement(player, ROOT_ADVANCEMENT_ID);
                grantAdvancement(player, LAVA_SINK_PLACE_ADVANCEMENT_ID);
            }
        }

        @Override
        protected ItemInteractionResult useItemOn(final ItemStack stack,
                                                  final BlockState state,
                                                  final Level level,
                                                  final BlockPos pos,
                                                  final Player player,
                                                  final InteractionHand hand,
                                                  final BlockHitResult hitResult) {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof LavaSinkBlockEntity lavaSinkBlockEntity)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            if (stack.isEmpty()) {
                trackBareHandLavaSinkInteraction(level, player);
                playLavaInteractionFeedback(level, pos);
                return ItemInteractionResult.SUCCESS;
            }

            if (stack.is(Items.LAVA_BUCKET)) {
                if (level.isClientSide()) {
                    playLavaInteractionFeedback(level, pos);
                    return ItemInteractionResult.SUCCESS;
                }
                if (!lavaSinkBlockEntity.addLava(LAVA_BUCKET_MB)) {
                    playLavaInteractionFeedback(level, pos);
                    return ItemInteractionResult.SUCCESS;
                }
                consumeHeldItemAndGive(player, hand, stack, new ItemStack(Items.BUCKET));
                playLavaInteractionFeedback(level, pos);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1f, level.random.nextFloat() + 0.5f);
                return ItemInteractionResult.SUCCESS;
            }

            if (!stack.is(Items.BUCKET)) {
                if (!level.isClientSide()) {
                    playLavaInteractionFeedback(level, pos);
                }
                return ItemInteractionResult.SUCCESS;
            }

            if (level.isClientSide()) {
                playLavaInteractionFeedback(level, pos);
                return ItemInteractionResult.SUCCESS;
            }

            if (!lavaSinkBlockEntity.removeLava(LAVA_BUCKET_MB)) {
                playLavaInteractionFeedback(level, pos);
                return ItemInteractionResult.SUCCESS;
            }
            consumeHeldItemAndGive(player, hand, stack, new ItemStack(Items.LAVA_BUCKET));
            playLavaInteractionFeedback(level, pos);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1f, level.random.nextFloat() + 0.5f);
            return ItemInteractionResult.SUCCESS;
        }

        @Override
        protected InteractionResult useWithoutItem(final BlockState state,
                                                   final Level level,
                                                   final BlockPos pos,
                                                   final Player player,
                                                   final BlockHitResult hitResult) {
            trackBareHandLavaSinkInteraction(level, player);
            playLavaInteractionFeedback(level, pos);
            return InteractionResult.SUCCESS;
        }

        private static void playLavaInteractionFeedback(final Level level, final BlockPos pos) {
            final double x = pos.getX() + 0.5;
            final double y = pos.getY() + 1.25;
            final double z = pos.getZ() + 0.5;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA, x, y - 0.45, z, 1, 0, 0, 0, 0);
                serverLevel.sendParticles(ParticleTypes.LAVA, x, y, z, 5, 0.5, 0.5, 0.5, 0);
                level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2f, level.random.nextFloat() + 0.5f);
                return;
            }
            level.addParticle(ParticleTypes.DRIPPING_LAVA, x, y - 0.45, z, 0, 0, 0);
            for (int i = 0; i < 5; i++) {
                level.addParticle(
                        ParticleTypes.LAVA,
                        x + (Math.random() - 0.5),
                        y + (Math.random() - 0.5),
                        z + (Math.random() - 0.5),
                        0,
                        0,
                        0
                );
            }
        }

        private static void consumeHeldItemAndGive(final Player player,
                                                   final InteractionHand hand,
                                                   final ItemStack heldStack,
                                                   final ItemStack resultStack) {
            if (player.getAbilities().instabuild) {
                player.setItemInHand(hand, resultStack);
                return;
            }

            heldStack.shrink(1);
            if (heldStack.isEmpty()) {
                player.setItemInHand(hand, resultStack);
                return;
            }

            if (!player.getInventory().add(resultStack)) {
                player.drop(resultStack, false);
            }
        }

        @Override
        public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
            return new LavaSinkBlockEntity(pos, state);
        }
    }

    private static final class LavaSinkBlockEntity extends BlockEntity {
        private int lavaAmount = LAVA_CAPACITY_MB;
        private final IFluidHandler fluidHandler = new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(final int tank) {
                if (tank != 0 || lavaAmount <= 0) {
                    return FluidStack.EMPTY;
                }
                return new FluidStack(Fluids.LAVA, lavaAmount);
            }

            @Override
            public int getTankCapacity(final int tank) {
                return tank == 0 ? LAVA_CAPACITY_MB : 0;
            }

            @Override
            public boolean isFluidValid(final int tank, final FluidStack stack) {
                return tank == 0 && stack != null && !stack.isEmpty() && stack.getFluid().isSame(Fluids.LAVA);
            }

            @Override
            public int fill(final FluidStack resource, final IFluidHandler.FluidAction action) {
                if (resource == null || resource.isEmpty() || !resource.getFluid().isSame(Fluids.LAVA)) {
                    return 0;
                }
                return resource.getAmount();
            }

            @Override
            public FluidStack drain(final FluidStack resource, final IFluidHandler.FluidAction action) {
                if (resource == null || resource.isEmpty() || !resource.getFluid().isSame(Fluids.LAVA)) {
                    return FluidStack.EMPTY;
                }
                return drain(resource.getAmount(), action);
            }

            @Override
            public FluidStack drain(final int maxDrain, final IFluidHandler.FluidAction action) {
                if (maxDrain <= 0) {
                    return FluidStack.EMPTY;
                }
                return new FluidStack(Fluids.LAVA, maxDrain);
            }
        };

        private LavaSinkBlockEntity(final BlockPos pos, final BlockState state) {
            super(LAVA_SINK_BLOCK_ENTITY_TYPE.get(), pos, state);
        }

        private IFluidHandler getFluidHandler() {
            return fluidHandler;
        }

        private boolean addLava(final int amountMb) {
            return amountMb > 0;
        }

        private boolean removeLava(final int amountMb) {
            return amountMb > 0;
        }

        private float fillRatio() {
            return Mth.clamp(lavaAmount / (float) LAVA_CAPACITY_MB, 0f, 1f);
        }

        @Override
        protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt(LAVA_AMOUNT_TAG, lavaAmount);
        }

        @Override
        protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            lavaAmount = LAVA_CAPACITY_MB;
        }

        @Override
        public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
            final CompoundTag tag = super.getUpdateTag(registries);
            tag.putInt(LAVA_AMOUNT_TAG, lavaAmount);
            return tag;
        }

        private void syncToClient() {
            setChanged();
            final Level level = getLevel();
            if (level == null) {
                return;
            }
            final BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static final class EnamelBasinBridgeBlockEntity extends BlockEntity {
        private EnamelBasinBridgeBlockEntity(final BlockPos pos, final BlockState state) {
            super(ENAMEL_BASIN_BRIDGE_TYPE.get(), pos, state);
        }
    }
}
