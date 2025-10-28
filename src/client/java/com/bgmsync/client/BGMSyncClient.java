package com.bgmsync.client;

import com.bgmsync.BGMSync;
import com.bgmsync.BGMSyncPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class BGMSyncClient implements ClientModInitializer {

    private static boolean IS_DJ = false;

    public static boolean isDJ() { return IS_DJ; }

    @Override
    public void onInitializeClient() {
        // DJ flag updates
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.DjOnly.ID, (payload, context) -> {
            IS_DJ = payload.isDj();
        });

        // Listeners: play exact track relayed from server (never start music on our own).
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            if (IS_DJ) return; // DJ should not be driven by server Play (DJ produces the music)
            var mc = MinecraftClient.getInstance();
            var entry = resolveSoundEntry(payload.soundId());
            if (entry.isEmpty()) return;

            // Replace current music with this exact track
            playNow(mc.getMusicTracker(), entry.get());
        });

        // Stop current track only (does not block future music)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            MinecraftClient.getInstance().getMusicTracker().stop();
        });

        // Server asks DJ to randomly start a track (used by /bgmsync test)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Test.ID, (payload, context) -> {
            if (!IS_DJ) return;
            var mc = MinecraftClient.getInstance();
            var pick = pickRandomMusicEntry();
            if (pick.isEmpty()) return;
            playNow(mc.getMusicTracker(), pick.get());

            // Broadcast to server what we actually started
            var id = entryIdOf(pick.get());
            if (id != null) {
                ClientPlayNetworking.send(new BGMSyncPayloads.Play(id.toString()));
            }
        });

        // Server asks DJ to force-play a specific id (used by /bgmsync play <id>)
        ClientPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.ForcePlay.ID, (payload, context) -> {
            if (!IS_DJ) return;
            var entry = resolveSoundEntry(payload.soundId());
            if (entry.isEmpty()) return;

            var tracker = MinecraftClient.getInstance().getMusicTracker();
            playNow(tracker, entry.get());

            var id = entryIdOf(entry.get());
            if (id != null) {
                ClientPlayNetworking.send(new BGMSyncPayloads.Play(id.toString()));
            }
        });
    }

    // ===== Utilities =====

    private static Optional<RegistryEntry<SoundEvent>> resolveSoundEntry(String idStr) {
        try {
            Identifier id = Identifier.of(idStr);
            var mc = MinecraftClient.getInstance();
            var rm = (mc.getNetworkHandler() != null && mc.getNetworkHandler().getRegistryManager() != null)
                    ? mc.getNetworkHandler().getRegistryManager()
                    : mc.getRegistryManager();
            var reg = rm.get(RegistryKeys.SOUND_EVENT);
            var key = RegistryKey.of(RegistryKeys.SOUND_EVENT, id);
            return reg.getEntry(key);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static void playNow(MusicTracker tracker, RegistryEntry<SoundEvent> entry) {
        // minDelay=0, maxDelay=0, replaceCurrent=true
        tracker.play(new MusicSound(entry, 0, 0, true));
    }

    private static Identifier entryIdOf(RegistryEntry<SoundEvent> entry) {
        return entry.getKey().map(RegistryKey::getValue).orElse(null);
    }

    // Very lightweight random picker over music-tagged entries (fallback: any sound entry)
    private static Optional<RegistryEntry<SoundEvent>> pickRandomMusicEntry() {
        var mc = MinecraftClient.getInstance();
        var rm = (mc.getNetworkHandler() != null && mc.getNetworkHandler().getRegistryManager() != null)
                ? mc.getNetworkHandler().getRegistryManager()
                : mc.getRegistryManager();
        var reg = rm.get(RegistryKeys.SOUND_EVENT);

        // Collect all entries whose id contains ".music" or "music." as a heuristic that works with many mods.
        List<RegistryEntry<SoundEvent>> all = new ArrayList<>();
        reg.streamEntries().forEach(all::add);

        List<RegistryEntry<SoundEvent>> musicish = new ArrayList<>();
        for (var e : all) {
            Identifier id = entryIdOf(e);
            if (id != null) {
                String s = id.toString();
                if (s.contains("music")) musicish.add(e);
            }
        }
        List<RegistryEntry<SoundEvent>> pool = musicish.isEmpty() ? all : musicish;
        if (pool.isEmpty()) return Optional.empty();

        return Optional.of(pool.get(new Random().nextInt(pool.size())));
    }
}
