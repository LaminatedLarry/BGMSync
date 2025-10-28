package com.bgmsync;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.Identifier;

import static com.bgmsync.BGMSync.MODID;

/**
 * Payload definitions used by BGMSync (MC 1.21.1, Fabric).
 *
 * NOTE:
 * - For Stop/Test we use enum singletons (use INSTANCE, never "new").
 * - For DjOnly we expose DjOnly.of(boolean) so callers can create the right instance.
 * - Play/ForcePlay carry a String sound id.
 */
public final class BGMSyncPayloads {

    // ---------- PLAY (client should start a specific music track) ----------
    public record Play(String soundId) implements CustomPayload {
        public static final Id<Play> ID = new Id<>(Identifier.of(MODID, "play"));

        // Codec maps a String to/from this record.
        public static final PacketCodec<PacketByteBuf, Play> CODEC =
                PacketCodecs.STRING.xmap(Play::new, Play::soundId);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- STOP (stop current music only) ----------
    public enum Stop implements CustomPayload {
        INSTANCE;

        public static final Id<Stop> ID = new Id<>(Identifier.of(MODID, "stop"));

        // Unit codec (no fields) – encode/decode nothing.
        public static final PacketCodec<PacketByteBuf, Stop> CODEC =
                PacketCodecs.unit(INSTANCE);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- TEST (ask the DJ client to trigger a random music track) ----------
    public enum Test implements CustomPayload {
        INSTANCE;

        public static final Id<Test> ID = new Id<>(Identifier.of(MODID, "test"));

        public static final PacketCodec<PacketByteBuf, Test> CODEC =
                PacketCodecs.unit(INSTANCE);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- DJ ONLY (tell a client whether it is the DJ) ----------
    public record DjOnly(boolean isDj) implements CustomPayload {
        public static final Id<DjOnly> ID = new Id<>(Identifier.of(MODID, "dj_only"));

        public static final PacketCodec<PacketByteBuf, DjOnly> CODEC =
                PacketCodecs.BOOL.xmap(DjOnly::new, DjOnly::isDj);

        /** Factory used by server-side code: DjOnly.of(true/false). */
        public static DjOnly of(boolean value) { return new DjOnly(value); }

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- FORCE PLAY (server tells DJ to force play a specific music track) ----------
    public record ForcePlay(String soundId) implements CustomPayload {
        public static final Id<ForcePlay> ID = new Id<>(Identifier.of(MODID, "force_play"));

        public static final PacketCodec<PacketByteBuf, ForcePlay> CODEC =
                PacketCodecs.STRING.xmap(ForcePlay::new, ForcePlay::soundId);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private BGMSyncPayloads() {}
}
