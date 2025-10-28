package com.bgmsync;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class BGMSyncPayloads {
    private BGMSyncPayloads() {}

    // ========= PLAY =========
    public record Play(String soundId) implements CustomPayload {
        public static final CustomPayload.Id<Play> ID =
                new CustomPayload.Id<>(Identifier.of(BGMSync.MODID, "play"));

        public static final PacketCodec<RegistryByteBuf, Play> CODEC =
                PacketCodecs.STRING.xmap(Play::new, Play::soundId);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========= STOP =========
    public enum Stop implements CustomPayload {
        INSTANCE;

        public static final CustomPayload.Id<Stop> ID =
                new CustomPayload.Id<>(Identifier.of(BGMSync.MODID, "stop"));

        public static final PacketCodec<RegistryByteBuf, Stop> CODEC =
                PacketCodec.unit(INSTANCE); // NOTE: PacketCodec.unit, not PacketCodecs.unit

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========= TEST (server -> DJ) =========
    public enum Test implements CustomPayload {
        INSTANCE;

        public static final CustomPayload.Id<Test> ID =
                new CustomPayload.Id<>(Identifier.of(BGMSync.MODID, "test"));

        public static final PacketCodec<RegistryByteBuf, Test> CODEC =
                PacketCodec.unit(INSTANCE);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========= DJ_ONLY FLAG =========
    public record DjOnly(boolean isDj) implements CustomPayload {
        public static final CustomPayload.Id<DjOnly> ID =
                new CustomPayload.Id<>(Identifier.of(BGMSync.MODID, "dj_only"));

        public static final PacketCodec<RegistryByteBuf, DjOnly> CODEC =
                PacketCodecs.BOOL.xmap(DjOnly::new, DjOnly::isDj);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ========= FORCE_PLAY (server -> DJ) =========
    public record ForcePlay(String soundId) implements CustomPayload {
        public static final CustomPayload.Id<ForcePlay> ID =
                new CustomPayload.Id<>(Identifier.of(BGMSync.MODID, "force_play"));

        public static final PacketCodec<RegistryByteBuf, ForcePlay> CODEC =
                PacketCodecs.STRING.xmap(ForcePlay::new, ForcePlay::soundId);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerAll() {
        // bidirectional ones
        PayloadTypeRegistry.playC2S().register(Play.ID, Play.CODEC);
        PayloadTypeRegistry.playS2C().register(Play.ID, Play.CODEC);

        PayloadTypeRegistry.playC2S().register(Stop.ID, Stop.CODEC);
        PayloadTypeRegistry.playS2C().register(Stop.ID, Stop.CODEC);

        // server -> client only
        PayloadTypeRegistry.playS2C().register(Test.ID, Test.CODEC);
        PayloadTypeRegistry.playS2C().register(DjOnly.ID, DjOnly.CODEC);
        PayloadTypeRegistry.playS2C().register(ForcePlay.ID, ForcePlay.CODEC);
    }
}
