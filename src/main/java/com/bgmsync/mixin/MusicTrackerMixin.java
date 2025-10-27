
package com.bgmsync.mixin;

import com.bgmsync.BGMSync;
import com.bgmsync.client.BGMSyncClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public class MusicTrackerMixin {
    @Inject(method = "play(Lnet/minecraft/sound/MusicSound;)V", at = @At("TAIL"))
    private void bgmsync$afterPlay(MusicSound musicSound, CallbackInfo ci) {
        if (!BGMSyncClient.isDJ()) return;
        try {
            var holder = musicSound.value();
            Identifier id = holder.getKey().id();
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(id.toString());
            if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                ClientPlayNetworking.send(BGMSync.PACKET_PLAY, buf);
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void bgmsync$afterStop(CallbackInfo ci) {
        if (!BGMSyncClient.isDJ()) return;
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            ClientPlayNetworking.send(BGMSync.PACKET_STOP, PacketByteBufs.empty());
        }
    }
}
