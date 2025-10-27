package com.bgmsync.client;

import com.bgmsync.BGMSyncPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class BGMSyncClient implements ClientModInitializer {

    private static volatile boolean IS_DJ = false;
    private static volatile String CURRENTLY_SYNCED = null; // sound id string from DJ

    @Override
    public void onInitializeClient() {
        // Ensure payload types are registered once globally (safe to call here).
        com.bgmsync.BGMSyncPayloads.registerAll();

        // Server tells us whether we are the DJ.
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.DjOnly.ID, (payload, context) -> {
            setIsDJ(payload.isDj());
        });

        // Server tells listeners to play a specific track (by Identifier string).
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            playFromDJ(payload.soundId());
        });

        // Server tells listeners to stop music.
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            stopAllMusic();
        });

        // Server asks the DJ client to start a random track (vanilla or modded).
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            playRandomForDJ();
        });
    }

    // ===== Public helpers used by mixins =====

    public static boolean isDJ() {
        return IS_DJ;
    }

    public static void setIsDJ(boolean value) {
        IS_DJ = value;
        // Listeners must never keep their own music running.
        if (!IS_DJ) stopAllMusic();
    }

    public static void stopAllMusic() {
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.getMusicTracker() != null) {
            mc.getMusicTracker().stop();
            CURRENTLY_SYNCED = null;
        }
    }

    /**
     * Called on listeners when the server sends a PLAY payload with a sound id string.
     * Converts the string id into a RegistryEntry<SoundEvent> and plays it via MusicTracker.
     */
    public static void playFromDJ(String soundId) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null) return;
        if (IS_DJ) return; // DJ already plays locally via SoundManager; don't double-play

        try {
            var id = Identifier.of(soundId);
            var key = RegistryKey.of(RegistryKeys.SOUND_EVENT, id);
            var entryOpt = mc.getNetworkHandler()
                    .getRegistryManager()
                    .get(RegistryKeys.SOUND_EVENT)
                    .getEntry(key);
            if (entryOpt.isEmpty()) return;

            RegistryEntry<SoundEvent> entry = entryOpt.get();

            // Ensure we don't layer multiple tracks.
            stopAllMusic();

            MusicSound music = new MusicSound(entry, 0, 0, true);
            mc.getMusicTracker().play(music);
            CURRENTLY_SYNCED = soundId;
        } catch (Exception ignored) {
            // If anything fails to resolve, we silently ignore to avoid client crashes.
        }
    }

    /**
     * Only the DJ is allowed to initiate music locally.
     * This uses the game's current music selection, which may be vanilla or modded,
     * and then our SoundManager mixin will broadcast it to the server.
     */
    public static void playRandomForDJ() {
        if (!IS_DJ) return;
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.getMusicTracker() == null) return;

        // Stop any current track, then let the tracker pick one (state-based random).
        mc.getMusicTracker().stop();
        // Ask tracker to start according to current context (biome/menu/etc.)
        // The tracker will schedule and begin playing; our SoundManager mixin will capture it.
        // To nudge immediate playback, request a generic "current context" music sound:
        var current = mc.getMusicType(); // returns MusicSound representing current context
        if (current != null) {
            mc.getMusicTracker().play(current);
        }
    }
}
