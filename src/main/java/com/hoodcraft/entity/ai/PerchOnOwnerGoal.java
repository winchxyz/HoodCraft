package com.hoodcraft.entity.ai;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.ShoulderRidingEntity;

import java.util.EnumSet;

/**
 * Flies a tamed bird to its owner and settles it on a free shoulder.
 *
 * <p>This replaces vanilla's {@link net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal},
 * which is purely passive: it waits for the bird's bounding box to happen to intersect the owner's
 * and never moves the bird itself. A parrot only ever lands on you because its wander goal
 * eventually flies it into you by chance.
 *
 * <p>That is too unreliable here, for a reason specific to this mod. Wheat seeds are the Robin's
 * taming <em>and</em> breeding food, so a player who has just tamed one is almost certainly still
 * holding seeds - and {@code TemptGoal} halts the bird at 2.5 blocks while they do. Passive landing
 * needs contact, so under the most natural way to play, it could never happen at all.
 *
 * <p>So this goal closes the distance itself. It yields while the bird is breeding or has been told
 * to sit, and stands down once both the owner's shoulders are taken.
 */
public class PerchOnOwnerGoal extends Goal {

    /** Beyond this the bird ignores the owner and goes back to wandering. */
    private static final double START_RANGE = 16.0D;
    private static final int REPATH_INTERVAL = 10;

    private final ShoulderRidingEntity bird;
    private final double speedModifier;
    private ServerPlayer owner;
    private int repathCooldown;

    public PerchOnOwnerGoal(ShoulderRidingEntity bird, double speedModifier) {
        this.bird = bird;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.bird.isOrderedToSit() || this.bird.isLeashed() || this.bird.isInLove()) {
            return false;
        }
        // ShoulderRidingEntity imposes a five-second cooldown after being dismounted, which is what
        // stops a bird from hopping straight back on the moment a crouching player shakes it off.
        if (!this.bird.canSitOnShoulder()) {
            return false;
        }
        if (!(this.bird.getOwner() instanceof ServerPlayer player) || !canPerchOn(player)) {
            return false;
        }
        return this.bird.distanceToSqr(player) < START_RANGE * START_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.owner = (ServerPlayer) this.bird.getOwner();
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.bird.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.owner == null) {
            return;
        }
        this.bird.getLookControl().setLookAt(this.owner, 10.0F, this.bird.getMaxHeadXRot());

        // Slightly generous contact: waiting for exact bounding-box overlap on a flying mob makes
        // the landing look like it is refusing to happen.
        if (this.bird.getBoundingBox().inflate(0.35D).intersects(this.owner.getBoundingBox())) {
            // Fails harmlessly while the owner is airborne or in water; the goal simply retries.
            this.bird.setEntityOnShoulder(this.owner);
            return;
        }

        if (--this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL;
            this.bird.getNavigation().moveTo(this.owner.getX(), this.owner.getEyeY(), this.owner.getZ(),
                    this.speedModifier);
        }
    }

    private static boolean canPerchOn(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isInWater()
                && !player.isInPowderSnow
                && (player.getShoulderEntityLeft().isEmpty() || player.getShoulderEntityRight().isEmpty());
    }
}
