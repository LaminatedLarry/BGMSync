package com.bgmsync;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.bgmsync.BGMSync.MODID;

/**
 * Payloads for BGMSync (MC 1.21.1, Fabric).
 * - Uses explicit PacketCodec implementations to avoid API variance.
 * - Stop/Test are enum singletons (use .INSTANCE).
 * - DjOnly exposes DjOnly.of(boolean) for convenience.
 */
public final class BGMSyncPayloads {

    // ----------- PLAY (client should start a specific music track) -----------
    public record Play(String soundId) implements CustomPayload {
        public static final Id<Play> ID = new Id<>(Identifier.of(MODID, "play"));

        // Encode/decode a UTF-8 string
        public static final PacketCodec<PacketByteBuf, Play> CODEC = new PacketCodec<>() {
            @Override
            public Play decode(PacketByteBuf buf) {
                String id = buf.readString();  // length-prefixed UTF-8
                return new Play(id);
            }

            @Override
            public void encode(PacketByteBuf buf, Play value) {
                buf.writeString(value.soundId());
            }
        };

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ----------- STOP (stop current music only) -----------
    public enum Stop implements CustomPayload {
        INSTANCE;

        public static final Id<Stop> ID = new Id<>(Identifier.of(MODID, "stop"));

        // Unit codec: read/write nothing
        public static final PacketCodec<PacketByteBuf, Stop> CODEC = new PacketCodec<>() {
            @Override public Stop decode(PacketByteBuf buf) { return INSTANCE; }
            @Override public void encode(PacketByteBuf buf, Stop value) { /* no-op */ }
        };

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ----------- TEST (ask DJ client to trigger a random music track) -----------
    public enum Test implements CustomPayload {
        INSTANCE;

        public static final Id<Test> ID = new Id<>(Identifier.of(MODID, "test"));

        // Unit codec: read/write nothing
        public static final PacketCodec<PacketByteBuf, Test> CODEC = new PacketCodec<>() {
            @Override public Test decode(PacketByteBuf buf) { return INSTANCE; }
            @Override public void encode(PacketByteBuf buf, Test value) { /* no-op */ }
        };

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ----------- DJ ONLY (tell a client whether it is the DJ) -----------
    public record DjOnly(boolean isDj) implements CustomPayload {
        public static final Id<DjOnly> ID = new Id<>(Identifier.of(MODID, "dj_only"));

        public static final PacketCodec<PacketByteBuf, DjOnly> CODEC = new PacketCodec<>() {
            @Override
            public DjOnly decode(PacketByteBuf buf) {
                boolean v = buf.readBoolean();
                return new DjOnly(v);
            }

            @Override
            public void encode(PacketByteBuf buf, DjOnly value) {
                buf.writeBoolean(value.isDj());
            }
        };

        /** Convenience factory used by server code. */
        public static DjOnly of(boolean value) { return new DjOnly(value); }

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ----------- FORCE PLAY (server tells DJ to play a specific track) -----------
    public record ForcePlay(String soundId) implements CustomPayload {
        public static final Id<ForcePlay> ID = new Id<>(Identifier.of(MODID, "force_play"));

        public static final PacketCodec<PacketByteBuf, ForcePlay> CODEC = new PacketCodec<>() {
            @Override
            public ForcePlay decode(PacketByteBuf buf) {
                String id = buf.readString();
                return new ForcePlay(id);
            }

            @Override
            public void encode(PacketByteBuf buf, ForcePlay value) {
                buf.writeString(value.soundId());
            }
        };

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private BGMSyncPayloads() {}
}
