package com.hoodcraft.loot;

import com.hoodcraft.registry.HCLootFunctions;
import com.hoodcraft.registry.HCTags;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.Optional;

/**
 * Replaces the stack with a randomly chosen egg from the {@code hoodcraft:hood_eggs} item tag.
 *
 * <p>This exists so the 6.7% egg chance stays 6.7% no matter how many pets the mod grows to hold.
 * Expressing "a random egg" as several weighted loot entries would make the combined egg chance
 * scale with the number of pets; keeping one weight-1 entry and choosing the species here does not.
 * Adding a pet therefore means adding its egg to the tag and nothing else.
 */
public class RandomHoodEggFunction extends LootItemConditionalFunction {

    public static final MapCodec<RandomHoodEggFunction> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    commonFields(instance).apply(instance, RandomHoodEggFunction::new));

    protected RandomHoodEggFunction(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType<? extends LootItemConditionalFunction> getType() {
        return HCLootFunctions.RANDOM_HOOD_EGG.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Optional<Holder<Item>> egg = BuiltInRegistries.ITEM
                .getTag(HCTags.Items.HOOD_EGGS)
                .flatMap(tag -> tag.getRandomElement(context.getRandom()));

        // With an empty tag there is no egg to give, so the roll yields nothing rather than
        // silently handing back the placeholder item the loot table names.
        return egg.map(holder -> new ItemStack(holder.value(), stack.getCount()))
                .orElse(ItemStack.EMPTY);
    }
}
