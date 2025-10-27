
package com.bgmsync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

public class BGMSync implements ModInitializer {

    public static final String MODID = "bgmsync";
    public static final Identifier PACKET_PLAY = Identifier.of(MODID, "play");
    public static final Identifier PACKET_STOP = Identifier.of(MODID, "stop");
    public static final Identifier PACKET_TEST = Identifier.of(MODID, "test");
    public static final Identifier PACKET_DJ_ONLY = Identifier.of(MODID, "dj_only");

    private static UUID currentDJ = null;
    private static String currentSoundId = null;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> chooseDJ(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentDJ = null);

        ServerPlayNetworking.registerGlobalReceiver(PACKET_PLAY, (server, player, handler, buf, responseSender) -> {
            String soundId = buf.readString();
            server.execute(() -> {
                if (!isDJ(player)) return;
                currentSoundId = soundId;
                broadcastPlay(server, soundId);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PACKET_STOP, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (!isDJ(player)) return;
                currentSoundId = null;
                broadcastStop(server);
            });
        });

        ServerPlayNetworking.Events.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            server.execute(() -> {
                ensureDJ(server);
                PacketByteBuf djBuf = PacketByteBufs.create();
                djBuf.writeBoolean(isDJ(p));
                ServerPlayNetworking.send(p, PACKET_DJ_ONLY, djBuf);
                if (currentSoundId != null && !isDJ(p)) {
                    PacketByteBuf playBuf = PacketByteBufs.create();
                    playBuf.writeString(currentSoundId);
                    ServerPlayNetworking.send(p, PACKET_PLAY, playBuf);
                }
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("BGMSync")
                .then(literal("test").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    ServerPlayerEntity dj = getDJ(server);
                    if (dj == null) {
                        chooseDJ(server);
                        dj = getDJ(server);
                    }
                    if (dj == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] No players online to be DJ."), false);
                        return 1;
                    }
                    ServerPlayNetworking.send(dj, PACKET_TEST, PacketByteBufs.empty());
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Test triggered for DJ: " + dj.getEntityName()), true);
                    return 1;
                }))
            );
        });
    }

    private static void broadcastPlay(MinecraftServer server, String soundId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(soundId);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isDJ(p)) {
                ServerPlayNetworking.send(p, PACKET_PLAY, buf);
            }
        }
    }

    private static void broadcastStop(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isDJ(p)) {
                ServerPlayNetworking.send(p, PACKET_STOP, PacketByteBufs.empty());
            }
        }
    }

    private static void chooseDJ(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) { currentDJ = null; return; }
        ServerPlayerEntity chosen = players.get(new Random().nextInt(players.size()));
        currentDJ = chosen.getUuid();
        server.getPlayerManager().broadcast(Text.literal("[BGMSync] DJ is now: " + chosen.getEntityName()), false);
    }

    private static void ensureDJ(MinecraftServer server) {
        if (currentDJ == null || server.getPlayerManager().getPlayer(currentDJ) == null) {
            chooseDJ(server);
        }
    }

    public static boolean isDJ(ServerPlayerEntity p) {
        return p != null && p.getUuid().equals(currentDJ);
    }

    public static ServerPlayerEntity getDJ(MinecraftServer server) {
        if (currentDJ == null) return null;
        return server.getPlayerManager().getPlayer(currentDJ);
    }
}
