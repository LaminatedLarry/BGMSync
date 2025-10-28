package com.bgmsync.client;

import com.bgmsync.BGMSyncPayloads;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

public final class BGMSyncClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Set by server via DjOnly payload
    private static boolean djOnly = false;

    private static final Random RNG = new Random();

    @Override
    public void onInitializeClient() {
        // Server tells clients to play this exact music track
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            String soundId = payload.soundId();
            context.client().execute(() -> playMusicById(context.client(), soundId));
        });

        // Stop only current music (don’t block future music)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            mc.execute(() -> mc.getMusicTracker().stop());
        });

        // DJ gets told to pick a random music track, then broadcasts which one via PLAY
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            mc.execute(() -> {
                if (!djOnly) return;
                Optional<String> pick = pickRandomMusicId();
                pick.ifPresent(id -> {
                    playMusicById(mc, id);                 // play locally on DJ
                    ClientPlayNetworking.send(new BGMSyncPayloads.Play(id)); // sync others
                });
            });
        });

        // Server tells us whether we are DJ
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.DjOnly.ID, (payload, context) -> {
            djOnly = payload.isDj();
        });

        // Server requests the DJ to play a specific track (from /bgmsync play <id>)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.ForcePlay.ID, (payload, context) -> {
            String id = payload.soundId();
            MinecraftClient mc = context.client();
            mc.execute(() -> {
                if (!djOnly) return;
                playMusicById(mc, id);
                ClientPlayNetworking.send(new BGMSyncPayloads.Play(id)); // sync others
            });
        });
    }

    // ----- API for mixins / other classes -----
    public static boolean isDJ() {
        return djOnly;
    }

    // ----- Internals -----

    private static void playMusicById(MinecraftClient mc, String soundId) {
        if (!isLikelyMusicId(soundId)) {
            LOGGER.warn("[BGMSync] Blocked non-music id from playing as music: {}", soundId);
            return;
        }
        Optional<RegistryEntry.Reference<SoundEvent>> entry = lookupSoundEntry(soundId);
        if (entry.isEmpty()) {
            LOGGER.warn("[BGMSync] Unknown sound id: {}", soundId);
            return;
        }
        MusicTracker tracker = mc.getMusicTracker();
        MusicSound music = new MusicSound(entry.get(), 0, 0, true); // replaceCurrent = true
        tracker.play(music);
    }

    private static Optional<RegistryEntry.Reference<SoundEvent>> lookupSoundEntry(String soundId) {
        Identifier id = Identifier.tryParse(soundId);
        if (id == null) return Optional.empty();
        return Registries.SOUND_EVENT.getEntry(id);
    }

    private static Optional<String> pickRandomMusicId() {
        List<Identifier> all = new ArrayList<>();
        Registries.SOUND_EVENT.getIds().forEach(all::add);

        List<Identifier> musicOnly = new ArrayList<>(all.size());
        for (Identifier id : all) {
            if (isLikelyMusicId(id)) musicOnly.add(id);
        }
        if (musicOnly.isEmpty()) return Optional.empty();

        Identifier chosen = musicOnly.get(RNG.nextInt(musicOnly.size()));
        return Optional.of(chosen.toString());
    }

    private static boolean isLikelyMusicId(Identifier id) {
        return isLikelyMusicId(id.toString());
    }

    /**
     * Heuristic that matches vanilla + modded (e.g., Immersive Music) tracks.
     * Accepts ids whose path indicates background music or music discs.
     * Examples:
     *  - minecraft:music.menu
     *  - minecraft:music.overworld.*
     *  - minecraft:music.nether.*, minecraft:music.end.*
     *  - *:music_*, *:*_music, *:bgm_*, *:*_bgm, *:record.*, *:music_disc.*
     */
    private static boolean isLikelyMusicId(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        // quick namespace/path sanity
        int colon = s.indexOf(':');
        String path = (colon >= 0 && colon + 1 < s.length()) ? s.substring(colon + 1) : s;

        // explicit vanilla prefixes
        if (path.startsWith("music")) return true;                 // music, music.menu, music.overworld.*
        if (path.startsWith("record.")) return true;               // record.*
        if (path.startsWith("music_disc")) return true;            // music_disc.*

        // common mod naming
        if (path.contains("bgm")) return true;                     // *_bgm / bgm_*
        if (path.contains(".music") || path.contains("_music")) return true;
        if (path.contains("music/")) return true;                  // some mods nest under folders

        // exclude very common non-music buckets to reduce false positives
        if (path.startsWith("ui.") || path.startsWith("entity.") || path.startsWith("block.") || path.startsWith("item.")) {
            return false;
        }
        return false;
    }
}
