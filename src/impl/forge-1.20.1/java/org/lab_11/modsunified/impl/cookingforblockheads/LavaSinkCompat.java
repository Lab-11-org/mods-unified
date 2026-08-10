package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.util.LazyOptional;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class LavaSinkCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CLIENT_HOOK_CLASS =
            "org.lab_11.modsunified.impl.cookingforblockheads.client.LavaSinkRenderer";
    private static final ResourceLocation ROOT_ADVANCEMENT_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "root");
    private static final ResourceLocation LAVA_SINK_PLACE_ADVANCEMENT_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "lava_sink_place");
    private static final ResourceLocation LAVA_SINK_BARE_HAND_ADVANCEMENT_ID =
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "lava_sink_bare_hand");
    private static final String BARE_HAND_INTERACTIONS_TAG = "Lab11LavaSinkBareHandInteractions";
    private static final int BARE_HAND_INTERACTIONS_REQUIRED = 2;
    private static final int CAPACITY = Integer.MAX_VALUE;

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Unifiled.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Unifiled.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Unifiled.MOD_ID);
    private static final List<RegistryObject<Block>> SINK_BLOCKS = new ArrayList<>();
    private static final List<RegistryObject<Item>> SINK_ITEMS = new ArrayList<>();

    static {
        registerVariant(BridgeKeys.BLOCK_LAVA_SINK);
        for (final DyeColor color : DyeColor.values()) {
            registerVariant(color.getName() + "_" + BridgeKeys.BLOCK_LAVA_SINK);
        }
    }

    private static final RegistryObject<BlockEntityType<LavaSinkBlockEntity>> BLOCK_ENTITY_TYPE =
            BLOCK_ENTITY_TYPES.register(
                    BridgeKeys.BLOCK_LAVA_SINK,
                    () -> BlockEntityType.Builder.of(
                            LavaSinkBlockEntity::new,
                            SINK_BLOCKS.stream().map(RegistryObject::get).toArray(Block[]::new)
                    ).build(null)
            );

    private static boolean registered;

    private LavaSinkCompat() {
    }

    private static void registerVariant(final String id) {
        final RegistryObject<Block> block = BLOCKS.register(
                id,
                () -> new LavaSinkBlock(BlockBehaviour.Properties.of().strength(2.5f).noOcclusion())
        );
        SINK_BLOCKS.add(block);
        SINK_ITEMS.add(ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    public static void register(final IEventBus modEventBus) {
        if (registered) {
            return;
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(LavaSinkCompat::onBuildCreativeModeTabContents);
        registerClientHooks(modEventBus);
        registered = true;
        LOGGER.info("Registered Forge 1.20.1 lava sink blocks independently of Dungeon's Delight.");
    }

    public static BlockEntityType<LavaSinkBlockEntity> blockEntityType() {
        return BLOCK_ENTITY_TYPE.get();
    }

    private static void onBuildCreativeModeTabContents(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS
                && (event.getTabKey() == null
                || !BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS.equals(event.getTabKey().location().getNamespace()))) {
            return;
        }

        for (final RegistryObject<Item> item : SINK_ITEMS) {
            event.accept(new ItemStack(item.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static void registerClientHooks(final IEventBus modEventBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        try {
            final Class<?> hooksClass = Class.forName(CLIENT_HOOK_CLASS);
            final Method registerMethod = hooksClass.getMethod("register", IEventBus.class);
            registerMethod.invoke(null, modEventBus);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to register lava sink client hooks.", e);
        }
    }

    private static void grantAdvancement(final Player player,
                                         final ResourceLocation advancementId,
                                         final String criterion) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }
        final Advancement advancement = serverPlayer.getServer().getAdvancements().getAdvancement(advancementId);
        if (advancement != null) {
            serverPlayer.getAdvancements().award(advancement, criterion);
        }
    }

    private static void trackBareHandInteraction(final Level level, final Player player) {
        if (level.isClientSide || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }
        final CompoundTag data = player.getPersistentData();
        final int nextCount = Math.max(0, data.getInt(BARE_HAND_INTERACTIONS_TAG)) + 1;
        if (nextCount < BARE_HAND_INTERACTIONS_REQUIRED) {
            data.putInt(BARE_HAND_INTERACTIONS_TAG, nextCount);
            return;
        }
        data.putInt(BARE_HAND_INTERACTIONS_TAG, 0);
        grantAdvancement(player, ROOT_ADVANCEMENT_ID, "bootstrap");
        grantAdvancement(player, LAVA_SINK_BARE_HAND_ADVANCEMENT_ID, "bare_hand");
    }

    private static final class LavaSinkBlock extends Block implements EntityBlock {
        private static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        private LavaSinkBlock(final Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        public BlockState getStateForPlacement(final BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public BlockState rotate(final BlockState state, final Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(final BlockState state, final Mirror mirror) {
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
                                @Nullable final LivingEntity placer,
                                final ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && placer instanceof Player player) {
                grantAdvancement(player, ROOT_ADVANCEMENT_ID, "bootstrap");
                grantAdvancement(player, LAVA_SINK_PLACE_ADVANCEMENT_ID, "placed");
            }
        }

        @Override
        public InteractionResult use(final BlockState state,
                                     final Level level,
                                     final BlockPos pos,
                                     final Player player,
                                     final InteractionHand hand,
                                     final BlockHitResult hitResult) {
            final ItemStack heldStack = player.getItemInHand(hand);
            if (heldStack.isEmpty()) {
                trackBareHandInteraction(level, player);
                playInteractionFeedback(level, pos);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (heldStack.is(Items.LAVA_BUCKET)) {
                if (level.isClientSide) {
                    playInteractionFeedback(level, pos);
                    return InteractionResult.SUCCESS;
                }
                consumeHeldItemAndGive(player, hand, heldStack, new ItemStack(Items.BUCKET));
                playInteractionFeedback(level, pos);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS,
                        1f, level.random.nextFloat() + 0.5f);
                return InteractionResult.SUCCESS;
            }

            if (!heldStack.is(Items.BUCKET)) {
                if (!level.isClientSide) {
                    playInteractionFeedback(level, pos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (level.isClientSide) {
                playInteractionFeedback(level, pos);
                return InteractionResult.SUCCESS;
            }
            consumeHeldItemAndGive(player, hand, heldStack, new ItemStack(Items.LAVA_BUCKET));
            playInteractionFeedback(level, pos);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS,
                    1f, level.random.nextFloat() + 0.5f);
            return InteractionResult.SUCCESS;
        }

        private static void playInteractionFeedback(final Level level, final BlockPos pos) {
            final double x = pos.getX() + 0.5;
            final double y = pos.getY() + 1.25;
            final double z = pos.getZ() + 0.5;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA, x, y - 0.45, z, 1, 0, 0, 0, 0);
                serverLevel.sendParticles(ParticleTypes.LAVA, x, y, z, 5, 0.5, 0.5, 0.5, 0);
                level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                        0.2f, level.random.nextFloat() + 0.5f);
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
            } else if (!player.getInventory().add(resultStack)) {
                player.drop(resultStack, false);
            }
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
            return new LavaSinkBlockEntity(pos, state);
        }
    }

    public static final class LavaSinkBlockEntity extends BlockEntity {
        private final IFluidHandler handler = new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(final int tank) {
                return tank == 0 ? new FluidStack(Fluids.LAVA, CAPACITY) : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(final int tank) {
                return tank == 0 ? CAPACITY : 0;
            }

            @Override
            public boolean isFluidValid(final int tank, final FluidStack stack) {
                return tank == 0 && stack.getFluid().isSame(Fluids.LAVA);
            }

            @Override
            public int fill(final FluidStack resource, final FluidAction action) {
                return resource.getFluid().isSame(Fluids.LAVA) ? resource.getAmount() : 0;
            }

            @Override
            public FluidStack drain(final FluidStack resource, final FluidAction action) {
                return resource.getFluid().isSame(Fluids.LAVA)
                        ? new FluidStack(Fluids.LAVA, resource.getAmount())
                        : FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(final int maxDrain, final FluidAction action) {
                return maxDrain > 0 ? new FluidStack(Fluids.LAVA, maxDrain) : FluidStack.EMPTY;
            }
        };
        private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> handler);

        private LavaSinkBlockEntity(final BlockPos pos, final BlockState state) {
            super(BLOCK_ENTITY_TYPE.get(), pos, state);
        }

        @Override
        public <T> LazyOptional<T> getCapability(final Capability<T> capability,
                                                  @Nullable final Direction side) {
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluidCapability.cast();
            }
            return super.getCapability(capability, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            fluidCapability.invalidate();
        }
    }
}
