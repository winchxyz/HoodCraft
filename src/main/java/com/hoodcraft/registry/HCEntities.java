package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.entity.CashCat;
import com.hoodcraft.entity.Robin;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HCEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HoodCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Robin>> ROBIN =
            ENTITY_TYPES.register("robin", () -> EntityType.Builder.of(Robin::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.9F)
                    .eyeHeight(0.6F)
                    .clientTrackingRange(8)
                    .build("robin"));

    public static final DeferredHolder<EntityType<?>, EntityType<CashCat>> CASH_CAT =
            ENTITY_TYPES.register("cash_cat", () -> EntityType.Builder.of(CashCat::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.7F)
                    .eyeHeight(0.55F)
                    .clientTrackingRange(8)
                    .build("cash_cat"));

    private HCEntities() {
    }
}
