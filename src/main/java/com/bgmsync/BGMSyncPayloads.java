
package com.bgmsync;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class BGMSyncPayloads {

    public record Play(String soundId) implements CustomPayload {
        public static final Id<Play> ID = new Id<>(Identifier.of("bgmsync","play"));
        public static final PacketCodec<RegistryByteBuf, Play> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, Play::soundId, Play::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Stop() implements CustomPayload {
        public static final Stop INSTANCE = new Stop();
        public static final Id<Stop> ID = new Id<>(Identifier.of("bgmsync","stop"));
        public static final PacketCodec<RegistryByteBuf, Stop> CODEC = PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Test() implements CustomPayload {
        public static final Test INSTANCE = new Test();
        public static final Id<Test> ID = new Id<>(Identifier.of("bgmsync","test"));
        public static final PacketCodec<RegistryByteBuf, Test> CODEC = PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DjOnly(boolean isDj) implements CustomPayload {
        public static final Id<DjOnly> ID = new Id<>(Identifier.of("bgmsync","dj_only"));
        public static final PacketCodec<RegistryByteBuf, DjOnly> CODEC =
                PacketCodec.tuple(PacketCodecs.BOOL, DjOnly::isDj, DjOnly::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
