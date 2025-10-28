package com.bgmsync.client;

import com.bgmsync.BGMSyncPayloads;
import com.bgmsync.util.MusicIds;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class BGMSyncClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean djOnly = false; // set by server via DjOnly payload
    private static final Random RNG = new Random();

    @Override
    public void onInitializeClient() {
        // Server says: play this exact track (music only on our side)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            String soundId = payload.soundId();
            context.client().execute(() -> {
                Identifier id = Identifier.tryParse(soundId);
                if (id == null || !MusicIds.isMusicId(id)) {
                    LOGGER.debug("[BGMSync] Ignoring non-music PLAY id: {}", soundId);
                    return;
                }
                playMusicById(context.client(), soundId);
            });
        });

        // Server says: stop current music (doesn't block future)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            mc.execute(() -> mc.getMusicTracker().stop());
        });

        // Server pings DJ to test; DJ chooses a random *music* id and broadcasts it
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            mc.execute(() -> {
                if (!djOnly) return;
                Optional<String> pick = pickRandomMusicId();
                pick.ifPresent(id -> {
                    playMusicById(mc, id);                     // play locally
                    ClientPlayNetworking.send(new BGMSyncPayloads.Play(id)); // sync to all
                });
            });
        });

        // Server tells us whether we are the DJ
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.DjOnly.ID, (payload, context) -> {
            djOnly = payload.isDj();
        });

        // Server forces DJ to play a specific id (from /bgmsync play); enforce music-only here too
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.ForcePlay.ID, (payload, context) -> {
            String id = payload.soundId();
            MinecraftClient mc = context.client();
            mc.execute(() -> {
                if (!djOnly) return;
                Identifier parsed = Identifier.tryParse(id);
                if (parsed == null || !MusicIds.isMusicId(parsed)) {
                    LOGGER.debug("[BGMSync] Ignoring FORCE_PLAY for non-music id: {}", id);
                    return;
                }
                playMusicById(mc, id);
                ClientPlayNetworking.send(new BGMSyncPayloads.Play(id));
            });
        });
    }

    // Public for mixins/other classes that need to know
    public static boolean isDJ() { return djOnly; }

    // ---- helpers ----

    private static void playMusicById(MinecraftClient mc, String soundId) {
        Optional<RegistryEntry.Reference<SoundEvent>> entry = lookupSoundEntry(soundId);
        if (entry.isEmpty()) {
            LOGGER.warn("[BGMSync] Unknown sound id: {}", soundId);
            return;
        }
        MusicTracker tracker = mc.getMusicTracker();
        MusicSound music = new MusicSound(entry.get(), 0, 0, true); // replace current
        tracker.play(music);
    }

    private static Optional<RegistryEntry.Reference<SoundEvent>> lookupSoundEntry(String soundId) {
        Identifier id = Identifier.tryParse(soundId);
        if (id == null) return Optional.empty();
        return Registries.SOUND_EVENT.getEntry(id);
    }

    private static Optional<String> pickRandomMusicId() {
        List<String> candidates = MusicIds.collectAll();
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(RNG.nextInt(candidates.size())));
    }
}
