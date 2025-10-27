
package com.bgmsync.client;

import com.bgmsync.BGMSyncPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SimpleSoundInstance;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class BGMSyncClient implements ClientModInitializer {

    private static boolean isDJ = false;
    private static boolean suppressLocalMusic = true;
    private static String currentlySynced = null;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.Play.ID, BGMSyncPayloads.Play.CODEC);
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.Stop.ID, BGMSyncPayloads.Stop.CODEC);
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.DjOnly.ID, BGMSyncPayloads.DjOnly.CODEC);
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.Test.ID, BGMSyncPayloads.Test.CODEC);
        PayloadTypeRegistry.playC2S().register(BGMSyncPayloads.Play.ID, BGMSyncPayloads.Play.CODEC);
        PayloadTypeRegistry.playC2S().register(BGMSyncPayloads.Stop.ID, BGMSyncPayloads.Stop.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            String soundId = payload.soundId();
            context.client().execute(() -> playFromDJ(soundId));
        });
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            context.client().execute(BGMSyncClient::stopSynced);
        });
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.DjOnly.ID, (payload, context) -> {
            boolean djFlag = payload.isDj();
            context.client().execute(() -> {
                isDJ = djFlag;
                suppressLocalMusic = !isDJ;
                if (!isDJ) stopAllMusic();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            context.client().execute(BGMSyncClient::triggerRandomTrackForDJ);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!isDJ && suppressLocalMusic) stopAutoMusicIfAny();
        });
    }

    private static void triggerRandomTrackForDJ() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.isPaused()) return;
        MusicSound music = mc.getMusicType();
        if (music == null) return;
        mc.getMusicTracker().play(music);
    }

    private static void playFromDJ(String soundId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Optional<SoundEvent> evt = mc.getSoundManager().get(Identifier.of(soundId));
        if (evt.isEmpty()) return;
        stopAllMusic();
        SoundInstance inst = SimpleSoundInstance.forMusic(evt.getHolder());
        mc.getSoundManager().play(inst);
        currentlySynced = soundId;
    }

    private static void stopSynced() {
        currentlySynced = null;
        stopAllMusic();
    }

    private static void stopAllMusic() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.getMusicTracker().stop();
    }

    private static void stopAutoMusicIfAny() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.getMusicTracker().stop();
    }

    public static boolean isDJ() { return isDJ; }
}
