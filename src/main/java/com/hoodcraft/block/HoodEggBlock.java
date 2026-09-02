package com.hoodcraft.block;

import net.minecraft.sounds.SoundEvents;
import com.hoodcraft.registry.HCTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Supplier;

/**
 * A hatchable pet egg, modelled on the vanilla Sniffer Egg but with a three-tier substrate boost:
 *
 * <ul>
 *   <li>30 minutes on any ordinary block,</li>
 *   <li>15 minutes on wool,</li>
 *   <li>5 minutes on slime or honey.</li>
 * </ul>
 *
 * <p>The substrate is re-read at every stage tick rather than latched at placement, so moving the
 * egg onto (or off) a booster part-way through changes the remaining time. The egg passes through
 * three cracking stages before it hatches, each stage costing a third of the total time.
 */
public class HoodEggBlock extends Block {

    public static final MapCodec<HoodEggBlock> CODEC = simpleCodec(
            props -> new HoodEggBlock(props, () -> null));

    public static final int MAX_HATCH_LEVEL = 2;
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;

    private static final int REGULAR_HATCH_TIME_TICKS = 36000; // 30 minutes
    private static final int MEDIUM_HATCH_TIME_TICKS = 18000;  // 15 minutes on wool
    private static final int FAST_HATCH_TIME_TICKS = 6000;     // 5 minutes on slime or honey
    private static final int RANDOM_HATCH_OFFSET_TICKS = 300;

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 16.0D, 14.0D);

    private final Supplier<? extends EntityType<? extends Mob>> hatchling;

    public HoodEggBlock(Properties properties, Supplier<? extends EntityType<? extends Mob>> hatchling) {
        super(properties);
        this.hatchling = hatchling;
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static int getHatchLevel(BlockState state) {
        return state.getValue(HATCH);
    }

    private static boolean isReadyToHatch(BlockState state) {
        return getHatchLevel(state) == MAX_HATCH_LEVEL;
    }

    /** Total time this egg would take to hatch given whatever it is currently sitting on. */
    public static int totalHatchTicks(BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(HCTags.Blocks.HATCH_BOOSTERS_FAST)) {
            return FAST_HATCH_TIME_TICKS;
        }
        if (below.is(HCTags.Blocks.HATCH_BOOSTERS_MEDIUM)) {
            return MEDIUM_HATCH_TIME_TICKS;
        }
        return REGULAR_HATCH_TIME_TICKS;
    }

    /** Delay until the next of the three hatch stages. */
    private static int nextStageDelay(Level level, BlockPos pos) {
        int perStage = totalHatchTicks(level, pos) / (MAX_HATCH_LEVEL + 1);
        return perStage + level.getRandom().nextInt(RANDOM_HATCH_OFFSET_TICKS);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(state));
        level.scheduleTick(pos, this, nextStageDelay(level, pos));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isReadyToHatch(state)) {
            level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS,
                    0.7F, 0.9F + random.nextFloat() * 0.2F);
            level.setBlock(pos, state.setValue(HATCH, getHatchLevel(state) + 1), UPDATE_CLIENTS);
            level.scheduleTick(pos, this, nextStageDelay(level, pos));
            return;
        }

        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS,
                0.7F, 0.9F + random.nextFloat() * 0.2F);
        level.destroyBlock(pos, false);

        EntityType<? extends Mob> type = this.hatchling.get();
        if (type == null) {
            return;
        }
        Mob baby = type.create(level);
        if (baby == null) {
            return;
        }
        Vec3 spawn = pos.getCenter().offsetRandom(random, 0.1F);
        baby.moveTo(spawn.x, pos.getY(), spawn.z, Mth.wrapDegrees(random.nextFloat() * 360.0F), 0.0F);
        baby.setBaby(true);
        baby.setPersistenceRequired();
        level.addFreshEntity(baby);
    }
}
