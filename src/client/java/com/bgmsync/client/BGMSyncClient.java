package com.bgmsync.client;

import com.bgmsync.BGMSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
        ClientPlayNetworking.registerGlobalReceiver(BGMSync.PACKET_PLAY, (client, handler, buf, responseSender) -> {
            String soundId = buf.readString();
            client.execute(() -> playFromDJ(soundId));
        });
        ClientPlayNetworking.registerGlobalReceiver(BGMSync.PACKET_STOP, (client, handler, buf, responseSender) -> {
            client.execute(BGMSyncClient::stopSynced);
        });
        ClientPlayNetworking.registerGlobalReceiver(BGMSync.PACKET_DJ_ONLY, (client, handler, buf, responseSender) -> {
            boolean djFlag = buf.readBoolean();
            client.execute(() -> {
                isDJ = djFlag;
                suppressLocalMusic = !isDJ;
                if (!isDJ) stopAllMusic();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BGMSync.PACKET_TEST, (client, handler, buf, responseSender) -> {
            client.execute(BGMSyncClient::triggerRandomTrackForDJ);
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