package com.bgmsync;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class BGMSyncPayloads {
    private BGMSyncPayloads() {}

    // ========== PLAY ==========
    public record Play(String soundId) implements CustomPayload {
        public static final Id<Play> ID = new Id<>(Identifier.of(BGMSync.MODID, "play"));
        public static final PacketCodec<RegistryByteBuf, Play> CODEC =
                PacketCodecs.STRING.xmap(Play::new, Play::soundId);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========== STOP ==========
    public enum Stop implements CustomPayload {
        INSTANCE;
        public static final Id<Stop> ID = new Id<>(Identifier.of(BGMSync.MODID, "stop"));
        public static final PacketCodec<RegistryByteBuf, Stop> CODEC =
                PacketCodecs.unit(INSTANCE);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========== TEST (server -> DJ) ==========
    public enum Test implements CustomPayload {
        INSTANCE;
        public static final Id<Test> ID = new Id<>(Identifier.of(BGMSync.MODID, "test"));
        public static final PacketCodec<RegistryByteBuf, Test> CODEC =
                PacketCodecs.unit(INSTANCE);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========== DJ_ONLY FLAG (server -> client) ==========
    public record DjOnly(boolean isDj) implements CustomPayload {
        public static final Id<DjOnly> ID = new Id<>(Identifier.of(BGMSync.MODID, "dj_only"));
        public static final PacketCodec<RegistryByteBuf, DjOnly> CODEC =
                PacketCodecs.BOOL.xmap(DjOnly::new, DjOnly::isDj);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========== FORCE_PLAY (server -> DJ) ==========
    // Ask the DJ client to play a specific id immediately (so timing matches for everyone).
    public record ForcePlay(String soundId) implements CustomPayload {
        public static final Id<ForcePlay> ID = new Id<>(Identifier.of(BGMSync.MODID, "force_play"));
        public static final PacketCodec<RegistryByteBuf, ForcePlay> CODEC =
                PacketCodecs.STRING.xmap(ForcePlay::new, ForcePlay::soundId);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerAll() {
        // Bidirectional play/stop
        PayloadTypeRegistry.playS2C().register(Play.ID, Play.CODEC);
        PayloadTypeRegistry.playC2S().register(Play.ID, Play.CODEC);
        PayloadTypeRegistry.playS2C().register(Stop.ID, Stop.CODEC);
        PayloadTypeRegistry.playC2S().register(Stop.ID, Stop.CODEC);

        // Server -> client
        PayloadTypeRegistry.playS2C().register(Test.ID, Test.CODEC);
        PayloadTypeRegistry.playS2C().register(DjOnly.ID, DjOnly.CODEC);
        PayloadTypeRegistry.playS2C().register(ForcePlay.ID, ForcePlay.CODEC);
    }
}
