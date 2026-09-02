package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class HCTags {

    public static final class Blocks {
        /** Substrates that hatch a Hood Egg in five minutes — slime and honey. */
        public static final TagKey<Block> HATCH_BOOSTERS_FAST = tag("hatch_boosters/fast");
        /** Substrates that halve the hatch time. Populated with the vanilla wool tag. */
        public static final TagKey<Block> HATCH_BOOSTERS_MEDIUM = tag("hatch_boosters/medium");

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, HoodCraft.id(path));
        }

        private Blocks() {
        }
    }

    public static final class Items {
        /** Every hatchable pet egg in the mod. The Hood Brush rolls one of these at random. */
        public static final TagKey<Item> HOOD_EGGS = tag("hood_eggs");

        private static TagKey<Item> tag(String path) {
            return TagKey.create(Registries.ITEM, HoodCraft.id(path));
        }

        private Items() {
        }
    }

    private HCTags() {
    }
}
