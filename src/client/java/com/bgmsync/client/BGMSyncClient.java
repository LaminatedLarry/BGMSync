package com.bgmsync.client;

import com.bgmsync.BGMSync;
import com.bgmsync.BGMSyncPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class BGMSyncClient implements ClientModInitializer {
    private static boolean djOnly = false; // true on DJ client
    private static final Random RNG = new Random();

    @Override
    public void onInitializeClient() {
        // PLAY: starts a specific track on everyone (sent from server)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            String soundId = payload.soundId();
            context.client().execute(() -> playMusicById(context.client(), soundId));
        });

        // STOP: stops any currently playing music (does not block future music)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            mc.execute(() -> mc.getMusicTracker().stop());
        });

        // TEST: server tells the DJ to start a random track; DJ then informs server which one to sync
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            mc.execute(() -> {
                if (!djOnly) return; // safety: only DJ responds
                Optional<String> pick = pickRandomSoundId();
                pick.ifPresent(id -> {
                    // Play locally on DJ…
                    playMusicById(mc, id);
                    // …and tell the server which exact track to play for everyone else
                    ClientPlayNetworking.send(new BGMSyncPayloads.Play(id));
                });
            });
        });

        // DJ_ONLY: server informs the client if they are the DJ
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.DjOnly.ID, (payload, context) -> {
            djOnly = payload.isDj();
        });

        // FORCE_PLAY: server forces the DJ to play a specific id (used by /bgmsync play <id>)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.ForcePlay.ID, (payload, context) -> {
            String id = payload.soundId();
            MinecraftClient mc = context.client();
            mc.execute(() -> {
                if (djOnly) {
                    playMusicById(mc, id);
                    // let the server propagate the same PLAY packet to everyone else
                    ClientPlayNetworking.send(new BGMSyncPayloads.Play(id));
                }
            });
        });
    }

    // ---- Helpers ----

    private static void playMusicById(MinecraftClient mc, String soundId) {
        Optional<RegistryEntry.Reference<SoundEvent>> entry = lookupSoundEntry(soundId);
        if (entry.isEmpty()) {
            BGMSync.LOGGER.warn("[BGMSync] Unknown sound id: {}", soundId);
            return;
        }
        MusicTracker tracker = mc.getMusicTracker();
        // replaceCurrent=true so it switches immediately to the requested track
        MusicSound music = new MusicSound(entry.get(), 0, 0, true);
        tracker.play(music);
    }

    private static Optional<RegistryEntry.Reference<SoundEvent>> lookupSoundEntry(String soundId) {
        Identifier id = Identifier.tryParse(soundId);
        if (id == null) return Optional.empty();
        return Registries.SOUND_EVENT.getEntry(id);
    }

    private static Optional<String> pickRandomSoundId() {
        // Build a list of all registered sound ids (vanilla + modded like Immersive Music)
        List<Identifier> ids = new ArrayList<>();
        Registries.SOUND_EVENT.getIds().forEach(ids::add);
        if (ids.isEmpty()) return Optional.empty();
        Identifier chosen = ids.get(RNG.nextInt(ids.size()));
        return Optional.of(chosen.toString());
    }
}
