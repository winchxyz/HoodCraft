package com.hoodcraft.entity;

import com.hoodcraft.registry.HCEntities;
import com.hoodcraft.registry.HCSounds;
import com.hoodcraft.registry.HCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Cash Cat - the crying cat, and the second HoodCraft pet.
 *
 * <p>It sits where it spawns and weeps, which is its whole character. Cooked salmon tames and breeds
 * it, but salmon will not cheer it up: only a <strong>gold ingot</strong> does that, and only for one
 * Minecraft day, during which it drops the mascot slouch, stops crying and behaves like an ordinary
 * cat. When the day runs out it sits back down and the tears start again.
 *
 * <p>Feeding it any ingot at all is also a lottery. One time in ten thousand it coughs up a buried
 * treasure map, which is the closest a sad cat gets to paying you back.
 *
 * <p>Creepers and phantoms fear it exactly as they fear a vanilla cat. Both of those checks are
 * hardcoded against {@code Cat.class} in vanilla and cannot be extended, so they are reproduced in
 * {@link com.hoodcraft.HCCommonEvents} instead of inherited.
 */
public class CashCat extends TamableAnimal {

    private static final EntityDataAccessor<Integer> DATA_CHEER_TICKS =
            SynchedEntityData.defineId(CashCat.class, EntityDataSerializers.INT);

    /** Tames and breeds, but never cheers up. */
    private static final Ingredient FOOD = Ingredient.of(Items.COOKED_SALMON);

    /** One Minecraft day of being a normal cat. */
    public static final int CHEER_DURATION_TICKS = 24000;

    /** 1 in 10,000 - the 0.01% treasure map. */
    private static final int TREASURE_MAP_ODDS = 10000;

    private static final int TAME_CHANCE_DENOMINATOR = 3;
    private static final int TREASURE_SEARCH_RADIUS = 100;

    public CashCat(EntityType<? extends CashCat> type, Level level) {
        super(type, level);
        this.setTame(false, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHEER_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimalPanicGoal(1.5D));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new TemptGoal(this, 0.6D, FOOD, true));
        this.goalSelector.addGoal(4, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));

        // A sad cat does not go for walks. Wandering is the clearest behavioural tell that the gold
        // worked, so it is gated on the mood rather than removed and re-added as the mood changes.
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D) {
            @Override
            public boolean canUse() {
                return CashCat.this.isCheeredUp() && super.canUse();
            }
        });

        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ mood

    /** True while a gold ingot is still working. */
    public boolean isCheeredUp() {
        return this.entityData.get(DATA_CHEER_TICKS) > 0;
    }

    public int getCheerTicks() {
        return this.entityData.get(DATA_CHEER_TICKS);
    }

    private void setCheerTicks(int ticks) {
        this.entityData.set(DATA_CHEER_TICKS, Math.max(0, ticks));
    }

    // --- sitting down, and getting back up ---------------------------------
    //
    // The pose is driven from here rather than straight off the model's limb swing. Limb swing
    // twitches whenever the cat is nudged, pushed, or takes a single corrective step, and a raw
    // threshold on it makes the cat stand and drop back down every time it flickers across the
    // line. Two things fix that: hysteresis, so settling and rising need different amounts of
    // movement, and an interpolated amount, so the change is a movement rather than a snap.

    /** How much of the sit is added or removed per tick - about a third of a second either way. */
    private static final float SIT_RATE = 0.15F;
    /** Nearly motionless before it will settle. */
    private static final float ENTER_SIT_SPEED = 0.03F;
    /** Properly walking before it will rise. */
    private static final float LEAVE_SIT_SPEED = 0.20F;

    private float sitAmount;
    private float sitAmountPrev;

    /** 0 while standing, 1 while fully sat in the mascot pose. */
    public float getSitAmount(float partialTick) {
        return Mth.lerp(partialTick, this.sitAmountPrev, this.sitAmount);
    }

    private boolean wantsToSit() {
        if (this.isOrderedToSit()) {
            return true;
        }
        if (this.isCheeredUp()) {
            return false;
        }
        float threshold = this.sitAmount > 0.5F ? LEAVE_SIT_SPEED : ENTER_SIT_SPEED;
        return this.walkAnimation.speed() < threshold;
    }

    @Override
    public void tick() {
        super.tick();
        this.sitAmountPrev = this.sitAmount;
        this.sitAmount = Mth.clamp(this.sitAmount + (this.wantsToSit() ? SIT_RATE : -SIT_RATE),
                0.0F, 1.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            int cheer = this.getCheerTicks();
            if (cheer > 0) {
                this.setCheerTicks(cheer - 1);
            }
            return;
        }

        // Tears, client side only. A cheered cat has nothing to cry about.
        if (!this.isCheeredUp() && this.random.nextInt(12) == 0) {
            this.spawnTear();
        }
    }

    /**
     * Height of the eyes above the feet while sitting up in the mascot pose.
     *
     * <p>Not {@link #getEyeY()}: that is derived from the hitbox, which stays cat-sized and low
     * whatever the model is doing, so tears drawn there leak out of the middle of the chest. The
     * sitting model puts the head at model y 10.4, and 16 model units make a block, so the face is
     * (24 - 10.4) / 16 of a block up.
     */
    private static final double SITTING_EYE_HEIGHT = 0.85D;
    private static final double TEAR_FORWARD = 0.30D;

    private void spawnTear() {
        // Just in front of the face, offset to one side so it reads as an eye rather than the nose.
        float side = this.random.nextBoolean() ? 0.11F : -0.11F;
        double yaw = Math.toRadians(this.getYRot());
        double x = this.getX() - Math.sin(yaw) * TEAR_FORWARD + Math.cos(yaw) * side;
        double z = this.getZ() + Math.cos(yaw) * TEAR_FORWARD + Math.sin(yaw) * side;
        double y = this.getY() + SITTING_EYE_HEIGHT;
        this.level().addParticle(ParticleTypes.DRIPPING_WATER, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    // ------------------------------------------------------- taming, feeding

    @Override
    public boolean isFood(ItemStack stack) {
        return FOOD.test(stack);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Ingots first: they are never food, so Animal#mobInteract would only refuse them.
        if (stack.is(HCTags.Items.INGOTS)) {
            return this.feedIngot(player, hand, stack);
        }

        if (!this.isTame() && this.isFood(stack)) {
            return this.tryTame(player, stack);
        }

        // Salmon on a tame cat means breeding, or growing a kitten up. Animal handles both.
        if (this.isTame() && this.isFood(stack)) {
            return super.mobInteract(player, hand);
        }

        if (this.isTame() && this.isOwnedBy(player)) {
            if (!this.level().isClientSide) {
                this.setOrderedToSit(!this.isOrderedToSit());
                this.jumping = false;
                this.navigation.stop();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    /**
     * Gold cheers it up; any ingot buys a lottery ticket.
     *
     * <p>Gold is deliberately both: the cheer-up is the reliable payoff and the map is the long shot,
     * so feeding gold is never a wasted ingot even though the map almost never lands.
     */
    private InteractionResult feedIngot(Player player, InteractionHand hand, ItemStack stack) {
        if (this.level().isClientSide) {
            return InteractionResult.CONSUME;
        }

        boolean gold = stack.is(Items.GOLD_INGOT);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        if (gold) {
            this.setCheerTicks(CHEER_DURATION_TICKS);
            this.setOrderedToSit(false);
            this.playSound(HCSounds.CASH_CAT_CHEER.get(), 1.0F, 1.0F);
            this.level().broadcastEntityEvent(this, (byte) 7);   // hearts
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);   // smoke
        }

        if (this.random.nextInt(TREASURE_MAP_ODDS) == 0) {
            this.dropTreasureMap();
        }
        return InteractionResult.SUCCESS;
    }

    /** The 0.01%. A real buried-treasure map, pointing at a real structure. */
    private void dropTreasureMap() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos treasure = serverLevel.findNearestMapStructure(
                StructureTags.ON_TREASURE_MAPS, this.blockPosition(), TREASURE_SEARCH_RADIUS, true);
        if (treasure == null) {
            return;
        }

        ItemStack map = MapItem.create(serverLevel, treasure.getX(), treasure.getZ(), (byte) 1, true, true);
        MapItem.renderBiomePreviewMap(serverLevel, map);
        MapItemSavedData.addTargetDecoration(map, treasure, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.CUSTOM_NAME, Component.translatable("filled_map.buried_treasure"));

        this.spawnAtLocation(map);
        this.playSound(HCSounds.CASH_CAT_JACKPOT.get(), 1.0F, 1.0F);
    }

    private InteractionResult tryTame(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (this.level().isClientSide) {
            return InteractionResult.CONSUME;
        }

        if (this.random.nextInt(TAME_CHANCE_DENOMINATOR) == 0) {
            this.tame(player);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        CashCat kitten = HCEntities.CASH_CAT.get().create(level);
        if (kitten != null && this.getOwnerUUID() != null) {
            kitten.setOwnerUUID(this.getOwnerUUID());
            kitten.setTame(true, true);
        }
        return kitten;
    }

    @Override
    public boolean canMate(Animal other) {
        if (other == this || !this.isTame() || !(other instanceof CashCat mate) || !mate.isTame()) {
            return false;
        }
        return this.isInLove() && mate.isInLove();
    }

    // -------------------------------------------------------------- save/load

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("CheerTicks", this.getCheerTicks());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setCheerTicks(compound.getInt("CheerTicks"));
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
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.isCheeredUp() ? HCSounds.CASH_CAT_PURR.get() : HCSounds.CASH_CAT_SAD.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HCSounds.CASH_CAT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HCSounds.CASH_CAT_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public int getAmbientSoundInterval() {
        // A miserable cat is a quiet one; it sighs rather than meows constantly.
        return this.isCheeredUp() ? 120 : 300;
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }
}
