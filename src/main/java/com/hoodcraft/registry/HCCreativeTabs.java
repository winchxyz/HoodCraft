package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HCCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HoodCraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hoodcraft.main"))
                    .icon(() -> new ItemStack(HCItems.HOOD_BRUSH.get()))
                    .displayItems((params, output) -> {
                        output.accept(HCItems.BLACK_FEATHER.get());
                        output.accept(HCItems.HOOD_BRUSH.get());
                        output.accept(HCItems.ROBIN_EGG.get());
                        output.accept(HCItems.ROBIN_SPAWN_EGG.get());
                    })
                    .build());

    private HCCreativeTabs() {
    }
}
