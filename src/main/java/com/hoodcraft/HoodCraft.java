package com.hoodcraft;

import com.hoodcraft.registry.HCBlocks;
import com.hoodcraft.registry.HCCreativeTabs;
import com.hoodcraft.registry.HCEntities;
import com.hoodcraft.registry.HCItems;
import com.hoodcraft.registry.HCLootFunctions;
import com.hoodcraft.registry.HCSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HoodCraft — pets modelled on the mascots of Robinhood Chain tokens.
 *
 * <p>Every mob in this mod follows the same content pipeline: a mob, a hatchable egg block that
 * only the Hood Brush can uncover, and a taming/breeding food. Adding a mob means adding one entry
 * to each registry class below; nothing else in the mod needs to change.
 */
@Mod(HoodCraft.MODID)
public class HoodCraft {
    public static final String MODID = "hoodcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("HoodCraft");

    public HoodCraft(IEventBus modEventBus, ModContainer modContainer) {
        HCSounds.SOUNDS.register(modEventBus);
        HCBlocks.BLOCKS.register(modEventBus);
        HCItems.ITEMS.register(modEventBus);
        HCEntities.ENTITY_TYPES.register(modEventBus);
        HCLootFunctions.LOOT_FUNCTIONS.register(modEventBus);
        HCCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
