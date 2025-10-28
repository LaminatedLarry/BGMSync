package com.bgmsync.util;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to recognize background-music SoundEvent IDs (vanilla + modded).
 * Heuristic:
 *   - include IDs whose path contains "music" or starts with "music"
 *   - exclude obvious non-BGM like records/jukebox clips
 *
 * Works with mods such as Immersive Music which typically register events
 * under paths containing "music".
 */
public final class MusicIds {
    private MusicIds() {}

    public static boolean isMusicId(Identifier id) {
        if (id == null) return false;
        String p = id.getPath();
        if (p == null) return false;
        p = p.toLowerCase();

        // exclude records/jukebox etc.
        if (p.startsWith("music_disc")) return false;
        if (p.contains("record")) return false;
        if (p.contains("jukebox")) return false;

        // include background music
        return p.startsWith("music") || p.contains(".music");
    }

    /** String form ("namespace:path") of all music-ish SoundEvent IDs */
    public static List<String> collectAll() {
        List<String> out = new ArrayList<>();
        for (Identifier id : Registries.SOUND_EVENT.getIds()) {
            if (isMusicId(id)) out.add(id.toString());
        }
        return out;
    }
}
