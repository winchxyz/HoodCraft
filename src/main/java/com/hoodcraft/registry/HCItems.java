package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.item.HoodBrushItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HCItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HoodCraft.MODID);

    /** Dropped by the Ray. The only feather the Hood Brush can be built from. */
    public static final DeferredItem<Item> BLACK_FEATHER =
            ITEMS.registerSimpleItem("black_feather", new Item.Properties());

    /** Same 64 uses and 96-tick dig as the vanilla brush, but it uncovers HoodCraft loot. */
    public static final DeferredItem<HoodBrushItem> HOOD_BRUSH =
            ITEMS.register("hood_brush", () -> new HoodBrushItem(new Item.Properties().durability(64)));

    /** Creative-only instant spawn, kept separate from the hatchable egg above. */
    public static final DeferredItem<DeferredSpawnEggItem> RAY_SPAWN_EGG =
            ITEMS.register("ray_spawn_egg", () -> new DeferredSpawnEggItem(
                    HCEntities.RAY, 0x00C805, 0x1B1B1B, new Item.Properties()));

    public static final DeferredItem<BlockItem> CASH_CAT_EGG =
            ITEMS.registerSimpleBlockItem("cash_cat_egg", HCBlocks.CASH_CAT_EGG,
                    new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<DeferredSpawnEggItem> CASH_CAT_SPAWN_EGG =
            ITEMS.register("cash_cat_spawn_egg", () -> new DeferredSpawnEggItem(
                    HCEntities.CASH_CAT, 0xD9CEBE, 0x6E6055, new Item.Properties()));

    private HCItems() {
    }
}
