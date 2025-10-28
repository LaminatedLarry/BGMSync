package com.bgmsync.mixin;

import com.bgmsync.BGMSyncPayloads;
import com.bgmsync.client.BGMSyncClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    /**
     * Fires whenever the client starts playing any sound.
     * If we're the DJ and the sound category is MUSIC, broadcast the track id to the server.
     */
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("TAIL"))
    private void bgmsync$afterPlay(SoundInstance sound, CallbackInfo ci) {
        // Only the DJ is allowed to broadcast, and only for background music.
        if (!BGMSyncClient.isDJ()) return;
        if (sound == null || sound.getCategory() != SoundCategory.MUSIC) return;

        Identifier id = sound.getId();
        if (id == null) return;

        // Send the exact sound id (e.g., "minecraft:music.overworld") to the server.
        ClientPlayNetworking.send(new BGMSyncPayloads.Play(id.toString()));
    }
}
