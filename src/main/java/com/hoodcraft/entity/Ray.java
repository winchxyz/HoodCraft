package com.hoodcraft.entity;

import com.hoodcraft.entity.ai.PerchOnOwnerGoal;
import com.hoodcraft.registry.HCEntities;
import com.hoodcraft.registry.HCSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.ShoulderRidingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The Ray - the Robinhood-green bird, and the first HoodCraft pet.
 *
 * <p>Behaviour is the vanilla parrot's: it flies, it lands on its owner's shoulder, it sits when
 * told to and it takes no fall damage. It differs in two ways. It is tamed with wheat seeds rather
 * than cookies, and - unlike a parrot - a tamed pair can be bred, again with wheat seeds.
 */
public class Ray extends ShoulderRidingEntity implements FlyingAnimal {

    /** Wheat seeds do all three jobs: tempting, taming and breeding. */
    private static final Ingredient FOOD = Ingredient.of(Items.WHEAT_SEEDS);

    /**
     * One seed in three tames. Deliberately kinder than the vanilla parrot's one in ten: seeds are
     * also the breeding food here, so a stingy rate would make the pet loop tedious rather than
     * interesting.
     */
    private static final int TAME_CHANCE_DENOMINATOR = 3;

    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    private float flapping = 1.0F;
    private float nextFlap = 1.0F;

    public Ray(EntityType<? extends Ray> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D);
    }

    /**
     * Priorities are spread wider than the vanilla parrot's on purpose.
     *
     * <p>Perching sits at 2, above everything that competes for movement, because the goals below
     * it would otherwise keep the bird permanently out of arm's reach: {@code TemptGoal} halts at
     * 2.5 blocks and {@code FollowOwnerGoal} at 1, and a shoulder landing needs actual contact.
     * Since wheat seeds do the taming, the tempting and the breeding here, a player who has just
     * tamed a Ray is usually still holding the one item that would stop it landing on them.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimalPanicGoal(1.25D));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new PerchOnOwnerGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0D, FOOD, false));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 5.0F, 1.0F));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new FollowMobGoal(this, 1.0D, 3.0F, 7.0F));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    // ---------------------------------------------------------------- flight

    @Override
    public void aiStep() {
        this.calculateFlapping();
        super.aiStep();
    }

    private void calculateFlapping() {
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed += (float) (!this.onGround() && !this.isPassenger() ? 4 : -1) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }
        this.flapping *= 0.9F;
        Vec3 movement = this.getDeltaMovement();
        if (!this.onGround() && movement.y < 0.0D) {
            this.setDeltaMovement(movement.multiply(1.0D, 0.6D, 1.0D));
        }
        this.flap += this.flapping * 2.0F;
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.playSound(SoundEvents.PARROT_FLY, 0.15F, 1.0F);
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // Birds do not take fall damage.
    }

    // ------------------------------------------------------- taming, feeding

    @Override
    public boolean isFood(ItemStack stack) {
        return FOOD.test(stack);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame() && this.isFood(stack)) {
            return this.tryTame(player, stack);
        }

        // On a tamed bird seeds mean "breed" - or "grow up", if it is still a chick.
        // Animal#mobInteract already implements both.
        if (this.isTame() && this.isFood(stack)) {
            return super.mobInteract(player, hand);
        }

        if (this.isTame() && this.isOwnedBy(player) && !this.isFlying()) {
            if (!this.level().isClientSide) {
                this.setOrderedToSit(!this.isOrderedToSit());
                this.jumping = false;
                this.navigation.stop();
                this.setTarget(null);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    private InteractionResult tryTame(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        if (this.level().isClientSide) {
            return InteractionResult.CONSUME;
        }

        // EventHooks.onAnimalTame lets other mods veto the taming, as vanilla's own animals allow.
        if (this.random.nextInt(TAME_CHANCE_DENOMINATOR) == 0 && !EventHooks.onAnimalTame(this, player)) {
            this.tame(player);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Ray chick = HCEntities.RAY.get().create(level);
        if (chick != null && this.getOwnerUUID() != null) {
            // A chick is born already belonging to whoever owns its parent.
            chick.setOwnerUUID(this.getOwnerUUID());
            chick.setTame(true, true);
        }
        return chick;
    }

    @Override
    public boolean canMate(Animal other) {
        if (other == this || !this.isTame() || !(other instanceof Ray mate) || !mate.isTame()) {
            return false;
        }
        return this.isInLove() && mate.isInLove();
    }

    // ------------------------------------------------------------ misc / sfx

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        this.setOrderedToSit(false);
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof Player)) {
            super.doPush(entity);
        }
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        // Occasionally the fuller song rather than the short chirp.
        return this.random.nextInt(12) == 0 ? HCSounds.RAY_SONG.get() : HCSounds.RAY_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HCSounds.RAY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HCSounds.RAY_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (this.isBaby() ? 1.4F : 1.0F);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }
}
