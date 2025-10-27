package com.bgmsync.mixin;

import com.bgmsync.BGMSyncPayloads;
import com.bgmsync.client.BGMSyncClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    // Fires whenever the client actually plays a sound. We filter to MUSIC category.
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("TAIL"))
    private void bgmsync$afterPlay(SoundInstance instance, CallbackInfo ci) {
        if (!BGMSyncClient.isDJ()) return;
        if (instance == null) return;
        if (instance.getCategory() != SoundCategory.MUSIC) return;

        // Send the sound ID to the server so listeners can play the same track.
        var id = instance.getId(); // net.minecraft.util.Identifier
        if (id != null) {
            ClientPlayNetworking.send(new BGMSyncPayloads.Play(id.toString()));
        }
    }
}
