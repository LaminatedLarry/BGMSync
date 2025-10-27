package com.bgmsync;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * All BGMSync payload types (both directions) + a guarded registration method.
 */
public final class BGMSyncPayloads {

    // ----- Guarded central registration -----
    private static boolean __registered = false;

    public static synchronized void registerAll() {
        if (__registered) return;
        // S2C
        PayloadTypeRegistry.playS2C().register(Play.ID, Play.CODEC);
        PayloadTypeRegistry.playS2C().register(Stop.ID, Stop.CODEC);
        PayloadTypeRegistry.playS2C().register(DjOnly.ID, DjOnly.CODEC);
        PayloadTypeRegistry.playS2C().register(Test.ID, Test.CODEC);
        // C2S
        PayloadTypeRegistry.playC2S().register(Play.ID, Play.CODEC);
        PayloadTypeRegistry.playC2S().register(Stop.ID, Stop.CODEC);
        __registered = true;
    }

    // ----- Payload definitions -----

    // PLAY: carries a stringified Identifier of the music to play
    public record Play(String soundId) implements CustomPayload {
        public static final Id<Play> ID =
                new Id<>(Identifier.of(BGMSync.MODID, "play"));
        public static final PacketCodec<RegistryByteBuf, Play> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, Play::soundId, Play::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // STOP: no fields, single instance
    public enum Stop implements CustomPayload {
        INSTANCE;
        public static final Id<Stop> ID =
                new Id<>(Identifier.of(BGMSync.MODID, "stop"));
        public static final PacketCodec<RegistryByteBuf, Stop> CODEC =
                PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // TEST: server -> client (DJ) to trigger a test music start
    public enum Test implements CustomPayload {
        INSTANCE;
        public static final Id<Test> ID =
                new Id<>(Identifier.of(BGMSync.MODID, "test"));
        public static final PacketCodec<RegistryByteBuf, Test> CODEC =
                PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // DJ_ONLY: server -> client to tell a player whether they are the DJ
    public record DjOnly(boolean isDj) implements CustomPayload {
        public static final Id<DjOnly> ID =
                new Id<>(Identifier.of(BGMSync.MODID, "dj_only"));
        public static final PacketCodec<RegistryByteBuf, DjOnly> CODEC =
                PacketCodec.tuple(PacketCodecs.BOOL, DjOnly::isDj, DjOnly::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
