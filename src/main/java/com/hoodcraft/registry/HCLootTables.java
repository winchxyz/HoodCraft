package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

public final class HCLootTables {

    /** What the Hood Brush uncovers, on any naturally generated suspicious block. */
    public static final ResourceKey<LootTable> HOOD_BRUSHING =
            ResourceKey.create(Registries.LOOT_TABLE, HoodCraft.id("archaeology/hood_brushing"));

    /** What a plain vanilla brush gets out of the suspicious gravel we add to ancient cities. */
    public static final ResourceKey<LootTable> ANCIENT_CITY_ARCHAEOLOGY =
            ResourceKey.create(Registries.LOOT_TABLE, HoodCraft.id("archaeology/ancient_city"));

    private HCLootTables() {
    }
}
