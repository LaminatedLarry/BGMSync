package com.bgmsync.mixin;

import com.bgmsync.BGMSyncPayloads;
import com.bgmsync.client.BGMSyncClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
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

            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.getNetworkHandler() == null) return;

            var entry = musicSound.event();
            if (entry == null) return;

            var id = entry.registryKey().getValue(); // Identifier
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
            ClientPlayNetworking.send(new BGMSyncPayloads.Stop());
        } catch (Throwable ignored) {
        }
    }
}
