package com.hoodcraft.item;

import com.hoodcraft.registry.HCLootTables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Hood Brush.
 *
 * <p>Mechanically identical to the vanilla brush - 96 ticks per block, one durability point per
 * completed dig - but it uncovers a different set of things. Where a vanilla brush yields whatever
 * the structure's own loot table holds, this one swaps in {@link HCLootTables#HOOD_BRUSHING}: a
 * nautilus shell, an emerald, leather boots, a stone hoe, or - at 6.7%, the same odds vanilla gives
 * a sniffer egg - one of the mod's pet eggs.
 *
 * <p>The swap deliberately happens only on blocks that still carry a loot table of their own, which
 * is to say naturally generated ones. Suspicious sand or gravel placed by a player has no loot table
 * and is left alone, so it yields nothing here exactly as it yields nothing to a vanilla brush. That
 * is what stops the eggs from being farmable.
 */
public class HoodBrushItem extends Item {

    public static final int ANIMATION_DURATION = 10;
    private static final int USE_DURATION = 200;

    public HoodBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && this.calculateHitResult(player).getType() == HitResult.Type.BLOCK) {
            player.startUsingItem(context.getHand());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (remainingUseDuration < 0 || !(entity instanceof Player player)) {
            entity.releaseUsingItem();
            return;
        }

        HitResult hitResult = this.calculateHitResult(player);
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            entity.releaseUsingItem();
            return;
        }

        // Vanilla lands one brush "stroke" every ten ticks, offset by five.
        int elapsed = this.getUseDuration(stack, entity) - remainingUseDuration + 1;
        if (elapsed % ANIMATION_DURATION != 5) {
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        HumanoidArm arm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        this.spawnDustParticles(level, blockHit, state, entity.getViewVector(0.0F), arm);

        SoundEvent sound = state.getBlock() instanceof BrushableBlock brushable
                ? brushable.getBrushSound()
                : SoundEvents.BRUSH_GENERIC;
        level.playSound(player, pos, sound, SoundSource.BLOCKS);

        if (level.isClientSide()) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable)) {
            return;
        }

        swapInHoodLoot(brushable, level);

        if (brushable.brush(level.getGameTime(), player, blockHit.getDirection())) {
            EquipmentSlot slot = stack.equals(player.getItemBySlot(EquipmentSlot.OFFHAND))
                    ? EquipmentSlot.OFFHAND
                    : EquipmentSlot.MAINHAND;
            stack.hurtAndBreak(1, entity, slot);
        }
    }

    /**
     * Point an unbrushed, naturally generated block at the HoodCraft loot table.
     *
     * <p>A block whose loot table is already null is either player-placed (never had one) or already
     * unpacked by an earlier brush (which nulls it and fixes the item). Either way it must be left
     * as it is: the first case keeps eggs unfarmable, the second stops a player from re-rolling a
     * result they did not like by switching brushes.
     */
    private static void swapInHoodLoot(BrushableBlockEntity brushable, Level level) {
        if (brushable.lootTable == null) {
            return;
        }
        long seed = brushable.lootTableSeed != 0L ? brushable.lootTableSeed : level.getRandom().nextLong();
        brushable.setLootTable(HCLootTables.HOOD_BRUSHING, seed);
    }

    private HitResult calculateHitResult(Player player) {
        return ProjectileUtil.getHitResultOnViewVector(player,
                entity -> !entity.isSpectator() && entity.isPickable(),
                player.blockInteractionRange());
    }

    private void spawnDustParticles(Level level, BlockHitResult hitResult, BlockState state,
                                    Vec3 viewVector, HumanoidArm arm) {
        double particleSpeed = 3.0D;
        int horizontalSign = arm == HumanoidArm.RIGHT ? 1 : -1;
        int particleCount = level.getRandom().nextInt(7, 12);
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Direction face = hitResult.getDirection();

        DustParticleBasis basis = DustParticleBasis.fromFace(face, viewVector, horizontalSign);
        Vec3 location = hitResult.getLocation();

        for (int i = 0; i < particleCount; i++) {
            level.addParticle(particle,
                    location.x - (face == Direction.WEST ? 1.0E-6F : 0.0F),
                    location.y,
                    location.z - (face == Direction.NORTH ? 1.0E-6F : 0.0F),
                    basis.x() * particleSpeed * level.getRandom().nextDouble(),
                    0.0D,
                    basis.z() * particleSpeed * level.getRandom().nextDouble());
        }
    }

    /** The direction dust is flicked in, which depends on the face being brushed. */
    private record DustParticleBasis(double x, double z) {
        static DustParticleBasis fromFace(Direction face, Vec3 viewVector, int horizontalSign) {
            return switch (face) {
                case DOWN, UP -> new DustParticleBasis(horizontalSign * viewVector.z(), -horizontalSign * viewVector.x());
                case NORTH -> new DustParticleBasis(horizontalSign, 0.0D);
                case SOUTH -> new DustParticleBasis(-horizontalSign, 0.0D);
                case WEST -> new DustParticleBasis(0.0D, -horizontalSign);
                case EAST -> new DustParticleBasis(0.0D, horizontalSign);
            };
        }
    }
}
