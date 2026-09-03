package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.block.HoodEggBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HCBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HoodCraft.MODID);

    public static final DeferredBlock<HoodEggBlock> ROBIN_EGG = BLOCKS.register("robin_egg",
            () -> new HoodEggBlock(eggProperties(MapColor.EMERALD), HCEntities.ROBIN));

    public static final DeferredBlock<HoodEggBlock> CASH_CAT_EGG = BLOCKS.register("cash_cat_egg",
            () -> new HoodEggBlock(eggProperties(MapColor.TERRACOTTA_WHITE), HCEntities.CASH_CAT));

    /**
     * Shared properties for every pet egg: as fragile as a sniffer egg, needs no support block and
     * is not affected by gravity.
     */
    private static BlockBehaviour.Properties eggProperties(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.5F)
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    private HCBlocks() {
    }
}
