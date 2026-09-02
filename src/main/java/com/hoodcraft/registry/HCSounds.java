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

    public static final DeferredHolder<SoundEvent, SoundEvent> ROBIN_IDLE = register("entity.robin.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBIN_HURT = register("entity.robin.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBIN_DEATH = register("entity.robin.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBIN_SONG = register("entity.robin.song");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HoodCraft.id(name)));
    }

    private HCSounds() {
    }
}
