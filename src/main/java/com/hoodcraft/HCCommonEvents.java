package com.hoodcraft;

import com.hoodcraft.entity.Robin;
import com.hoodcraft.registry.HCEntities;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = HoodCraft.MODID)
public final class HCCommonEvents {

    /** Matches vanilla's Animal#isBrightEnoughToSpawn, which is protected and so not callable here. */
    private static final int MIN_SPAWN_BRIGHTNESS = 8;

    @SubscribeEvent
    public static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(HCEntities.ROBIN.get(), Robin.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(HCEntities.ROBIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) ->
                        level.getBlockState(pos.below()).is(BlockTags.PARROTS_SPAWNABLE_ON)
                                && level.getRawBrightness(pos, 0) > MIN_SPAWN_BRIGHTNESS,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private HCCommonEvents() {
    }
}
