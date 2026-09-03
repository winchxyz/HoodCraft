package com.hoodcraft;

import com.hoodcraft.entity.CashCat;
import com.hoodcraft.entity.Ray;
import com.hoodcraft.registry.HCEntities;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

@EventBusSubscriber(modid = HoodCraft.MODID)
public final class HCCommonEvents {

    /** Matches vanilla's Animal#isBrightEnoughToSpawn, which is protected and so not callable here. */
    private static final int MIN_SPAWN_BRIGHTNESS = 8;

    /** Vanilla's creeper-flees-cat numbers, reused verbatim. */
    private static final float CREEPER_FLEE_DISTANCE = 6.0F;
    private static final double CREEPER_FLEE_SLOW = 1.0D;
    private static final double CREEPER_FLEE_FAST = 1.2D;
    private static final int CREEPER_AVOID_PRIORITY = 3;

    /** Vanilla's phantom-avoids-cat radius. */
    private static final double PHANTOM_SCARE_RADIUS = 16.0D;

    @SubscribeEvent
    public static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(HCEntities.RAY.get(), Ray.createAttributes().build());
        event.put(HCEntities.CASH_CAT.get(), CashCat.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(HCEntities.RAY.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) ->
                        level.getBlockState(pos.below()).is(BlockTags.PARROTS_SPAWNABLE_ON)
                                && level.getRawBrightness(pos, 0) > MIN_SPAWN_BRIGHTNESS,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(HCEntities.CASH_CAT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) ->
                        level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                                && level.getRawBrightness(pos, 0) > MIN_SPAWN_BRIGHTNESS,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * Creepers give the Cash Cat the same wide berth they give a vanilla cat.
     *
     * <p>Vanilla builds that fear into {@code Creeper#registerGoals} as an {@code AvoidEntityGoal}
     * hardcoded to {@code Cat.class}. Since the Cash Cat is a {@code TamableAnimal} rather than a
     * {@code Cat} - extending Cat would drag in goals that cannot be removed, because it re-adds
     * them from a private method whenever the mob is tamed - the goal is attached here instead, with
     * vanilla's own distances and speeds.
     */
    @SubscribeEvent
    public static void creepersFearCashCats(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
            creeper.goalSelector.addGoal(CREEPER_AVOID_PRIORITY,
                    new AvoidEntityGoal<>(creeper, CashCat.class,
                            CREEPER_FLEE_DISTANCE, CREEPER_FLEE_SLOW, CREEPER_FLEE_FAST));
        }
    }

    /**
     * Phantoms will not commit to a target with a Cash Cat nearby.
     *
     * <p>Vanilla's version of this lives inside a private inner goal on {@code Phantom} that scans
     * for {@code Cat.class}, so unlike the creeper's it cannot be attached from outside. Cancelling
     * the target change reproduces the observable behaviour - a cat in the area keeps the phantom
     * off you - without touching the goal.
     */
    @SubscribeEvent
    public static void phantomsFearCashCats(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom) || event.getNewAboutToBeSetTarget() == null) {
            return;
        }
        boolean catNearby = !phantom.level()
                .getEntitiesOfClass(CashCat.class, phantom.getBoundingBox().inflate(PHANTOM_SCARE_RADIUS))
                .isEmpty();
        if (catNearby) {
            event.setCanceled(true);
        }
    }

    private HCCommonEvents() {
    }
}
