
package com.bgmsync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

public class BGMSync implements ModInitializer {

    public static final String MODID = "bgmsync";

    private static UUID currentDJ = null;
    private static String currentSoundId = null;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.Play.ID, BGMSyncPayloads.Play.CODEC);
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.Stop.ID, BGMSyncPayloads.Stop.CODEC);
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.DjOnly.ID, BGMSyncPayloads.DjOnly.CODEC);
        PayloadTypeRegistry.playC2S().register(BGMSyncPayloads.Play.ID, BGMSyncPayloads.Play.CODEC);
        PayloadTypeRegistry.playC2S().register(BGMSyncPayloads.Stop.ID, BGMSyncPayloads.Stop.CODEC);
        PayloadTypeRegistry.playS2C().register(BGMSyncPayloads.Test.ID, BGMSyncPayloads.Test.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(this::chooseDJ);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentDJ = null);

        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!isDJ(context.player())) return;
                currentSoundId = payload.soundId();
                broadcastPlay(context.server(), currentSoundId);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!isDJ(context.player())) return;
                currentSoundId = null;
                broadcastStop(context.server());
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            server.execute(() -> {
                ensureDJ(server);
                ServerPlayNetworking.send(p, new BGMSyncPayloads.DjOnly(isDJ(p)));
                if (currentSoundId != null && !isDJ(p)) {
                    ServerPlayNetworking.send(p, new BGMSyncPayloads.Play(currentSoundId));
                }
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("BGMSync")
                .then(literal("test").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    ServerPlayerEntity dj = getDJ(server);
                    if (dj == null) { chooseDJ(server); dj = getDJ(server); }
                    if (dj == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] No players online to be DJ."), false);
                        return 1;
                    }
                    ServerPlayNetworking.send(dj, BGMSyncPayloads.Test.INSTANCE);
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Test triggered for DJ: " + dj.getName().getString()), true);
                    return 1;
                }))
            );
        });
    }

    private void broadcastPlay(MinecraftServer server, String soundId) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isDJ(p)) {
                ServerPlayNetworking.send(p, new BGMSyncPayloads.Play(soundId));
            }
        }
    }

    private void broadcastStop(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isDJ(p)) {
                ServerPlayNetworking.send(p, BGMSyncPayloads.Stop.INSTANCE);
            }
        }
    }

    private void chooseDJ(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) { currentDJ = null; return; }
        ServerPlayerEntity chosen = players.get(new Random().nextInt(players.size()));
        currentDJ = chosen.getUuid();
        server.getPlayerManager().broadcast(Text.literal("[BGMSync] DJ is now: " + chosen.getName().getString()), false);
    }

    private void ensureDJ(MinecraftServer server) {
        if (currentDJ == null || server.getPlayerManager().getPlayer(currentDJ) == null) { chooseDJ(server); }
    }

    public static boolean isDJ(ServerPlayerEntity p) { return p != null && p.getUuid().equals(currentDJ); }
    public static ServerPlayerEntity getDJ(MinecraftServer server) {
        if (currentDJ == null) return null;
        return server.getPlayerManager().getPlayer(currentDJ);
    }
}
