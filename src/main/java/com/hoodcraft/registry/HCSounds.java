package com.hoodcraft.registry;

import com.hoodcraft.HoodCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Only the bird's own voice is registered here. Wing beats and the egg's cracking and hatching
 * reuse the vanilla parrot and sniffer-egg sounds, which already fit exactly and save the mod from
 * shipping near-duplicate audio.
 */
public final class HCSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, HoodCraft.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_IDLE = register("entity.ray.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_HURT = register("entity.ray.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_DEATH = register("entity.ray.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_SONG = register("entity.ray.song");

    /** Aliases onto vanilla's cat sounds - see sounds.json; no audio is shipped for these. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_CAT_SAD = register("entity.cash_cat.sad");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_CAT_PURR = register("entity.cash_cat.purr");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_CAT_HURT = register("entity.cash_cat.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_CAT_DEATH = register("entity.cash_cat.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_CAT_CHEER = register("entity.cash_cat.cheer");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_CAT_JACKPOT = register("entity.cash_cat.jackpot");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HoodCraft.id(name)));
    }

    private HCSounds() {
    }
}
