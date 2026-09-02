package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.loot.RandomHoodEggFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HCLootFunctions {
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, HoodCraft.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomHoodEggFunction>>
            RANDOM_HOOD_EGG = LOOT_FUNCTIONS.register("random_hood_egg",
                    () -> new LootItemFunctionType<>(RandomHoodEggFunction.CODEC));

    private HCLootFunctions() {
    }
}
