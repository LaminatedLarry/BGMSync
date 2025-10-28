package com.bgmsync.client;

import com.bgmsync.BGMSyncPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.sound.v1.ClientSoundEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class BGMSyncClient implements ClientModInitializer {

    private static final Random RNG = new Random();
    private static volatile boolean IS_DJ = false;
    private static volatile String CURRENTLY_SYNCED = null;

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

        // Server tells clients to stop music.
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            stopAllMusic();
        });

        // Server asks the DJ client to start a random track (vanilla or modded).
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            playRandomForDJ(); // This will also broadcast C2S to the server.
        });

        // === Natural music sync (no mixins) ===
        // AFTER_PLAY fires after any sound starts playing client-side.
        ClientSoundEvents.AFTER_PLAY.register((sound, manager) -> {
            try {
                if (sound != null && sound.getCategory() == SoundCategory.MUSIC && IS_DJ) {
                    Identifier id = sound.getId();
                    if (id != null) {
                        ClientPlayNetworking.send(new BGMSyncPayloads.Play(id.toString()));
                        CURRENTLY_SYNCED = id.toString();
                    }
                }
            } catch (Throwable ignored) {
                // Never crash the client due to a sound hook.
            }
        });
    }

    // ===== Public helpers used elsewhere =====

    public static boolean isDJ() {
        return IS_DJ;
    }

    public static void setIsDJ(boolean value) {
        IS_DJ = value;
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

        // DJ already plays locally; listeners only.
        if (IS_DJ) return;

        try {
            var id = Identifier.of(soundId);
            var key = RegistryKey.of(RegistryKeys.SOUND_EVENT, id);

            Registry<SoundEvent> reg = mc.getNetworkHandler()
                    .getRegistryManager()
                    .get(RegistryKeys.SOUND_EVENT);

            var entryOpt = reg.getEntry(key);
            if (entryOpt.isEmpty()) return;
            RegistryEntry<SoundEvent> entry = entryOpt.get();

            stopAllMusic(); // ensure we don't layer tracks
            MusicSound music = new MusicSound(entry, 0, 0, true);
            mc.getMusicTracker().play(music);
            CURRENTLY_SYNCED = soundId;
        } catch (Exception ignored) {
            // If anything fails to resolve, we silently ignore to avoid client crashes.
        }
    }

    /**
     * Only the DJ is allowed to initiate music locally.
     *  1) Pick a random "music-like" SoundEvent (vanilla or modded).
     *  2) Send C2S PLAY with that id so the server can broadcast to listeners.
     *  3) Play it locally for the DJ.
     */
    public static void playRandomForDJ() {
        if (!IS_DJ) return;
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null || mc.getMusicTracker() == null) return;

        // Gather candidates from the live registry the client got from server.
        Registry<SoundEvent> reg = mc.getNetworkHandler().getRegistryManager().get(RegistryKeys.SOUND_EVENT);

        List<Identifier> candidates = new ArrayList<>();
        List<Identifier> fallback = new ArrayList<>();

        // 1.21.1: iterate IDs
        for (Identifier id : reg.getIds()) {
            if (id == null) continue;
            String path = id.getPath();
            if (path.contains("music")) {
                candidates.add(id);
            } else {
                fallback.add(id);
            }
        }

        Identifier chosenId = null;
        if (!candidates.isEmpty()) {
            chosenId = candidates.get(RNG.nextInt(candidates.size()));
        } else if (!fallback.isEmpty()) {
            chosenId = fallback.get(RNG.nextInt(fallback.size()));
        }
        if (chosenId == null) return;

        // Resolve entry for playback
        var key = RegistryKey.of(RegistryKeys.SOUND_EVENT, chosenId);
        var entryOpt = reg.getEntry(key);
        if (entryOpt.isEmpty()) return;
        RegistryEntry<SoundEvent> chosenEntry = entryOpt.get();

        String idString = chosenId.toString();

        // 1) Tell server which track to sync (C2S):
        ClientPlayNetworking.send(new BGMSyncPayloads.Play(idString));

        // 2) Stop any local current track
        mc.getMusicTracker().stop();

        // 3) Play it locally for the DJ right now
        MusicSound music = new MusicSound(chosenEntry, 0, 0, true);
        mc.getMusicTracker().play(music);

        CURRENTLY_SYNCED = idString;
    }
}
