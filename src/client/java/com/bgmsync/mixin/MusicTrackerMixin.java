package com.bgmsync.mixin;

import com.bgmsync.BGMSyncPayloads;
import com.bgmsync.client.BGMSyncClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public class MusicTrackerMixin {

    /**
     * Fires whenever the client plays any background music.
     * If we are the DJ, broadcast the exact track id to the server.
     */
    @Inject(method = "play(Lnet/minecraft/sound/MusicSound;)V", at = @At("HEAD"))
    private void bgmsync$onPlay(MusicSound musicSound, CallbackInfo ci) {
        try {
            if (!BGMSyncClient.isDJ() || musicSound == null) return;

            // Access MusicSound#sound (RegistryEntry<SoundEvent>) via accessor
            RegistryEntry<SoundEvent> entry = ((MusicSoundAccessor) (Object) musicSound).bgmsync$getSound();
            if (entry == null || entry.getKey().isEmpty()) return;

            Identifier id = entry.getKey().get().getValue();
            if (id == null) return;

            ClientPlayNetworking.send(new BGMSyncPayloads.Play(id.toString()));
        } catch (Throwable ignored) {
            // Never crash due to a sync broadcast.
        }
    }

    /**
     * When the DJ's client stops music naturally, tell everyone to stop current track.
     * (Does NOT block future music.)
     */
    @Inject(method = "stop()V", at = @At("HEAD"))
    private void bgmsync$onStop(CallbackInfo ci) {
        try {
            if (!BGMSyncClient.isDJ()) return;
            ClientPlayNetworking.send(BGMSyncPayloads.Stop.INSTANCE);
        } catch (Throwable ignored) {
        }
    }
}
