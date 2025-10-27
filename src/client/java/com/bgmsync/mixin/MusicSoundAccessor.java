package com.bgmsync.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MusicSound.class)
public interface MusicSoundAccessor {
    @Accessor("event")
    RegistryEntry<SoundEvent> bgmsync$getEvent();
}
