package com.bgmsync;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BGMSync implements ModInitializer {

    public static final String MODID = "bgmsync";

    // Current DJ (by UUID) and the currently-playing track id (as string)
    private static UUID currentDJ = null;
    private static String currentTrackId = null; // null = no active synced track

    @Override
    public void onInitialize() {
        BGMSyncPayloads.registerAll();

        // On server start, pick a DJ (first online or random later)
        ServerLifecycleEvents.SERVER_STARTED.register(this::chooseDJOnStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            currentDJ = null;
            currentTrackId = null;
        });

        // Payloads from DJ -> server
        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            var server = context.server();
            server.execute(() -> {
                ServerPlayerEntity sender = context.player();
                if (sender == null) return;
                if (!isDJ(sender)) return; // only DJ can drive the sync

                String soundId = payload.soundId();
                currentTrackId = soundId; // remember current track

                // Tell everyone except the DJ to play this exact track
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (!p.getUuid().equals(sender.getUuid())) {
                        ServerPlayNetworking.send(p, new BGMSyncPayloads.Play(soundId));
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            var server = context.server();
            server.execute(() -> {
                ServerPlayerEntity sender = context.player();
                if (sender == null) return;
                if (!isDJ(sender)) return; // only DJ naturally signals stop

                currentTrackId = null; // nothing currently playing
                // Tell everyone except the DJ to stop
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (!p.getUuid().equals(sender.getUuid())) {
                        ServerPlayNetworking.send(p, BGMSyncPayloads.Stop.INSTANCE);
                    }
                }
            });
        });

        // When a player joins, mark if they are the DJ and, if a track is active, start it for them
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            boolean playerIsDJ = isDJ(player);
            ServerPlayNetworking.send(player, new BGMSyncPayloads.DjOnly(playerIsDJ));
            if (!playerIsDJ && currentTrackId != null) {
                // bring the joiner up-to-date with the exact current track
                ServerPlayNetworking.send(player, new BGMSyncPayloads.Play(currentTrackId));
            }
        });

        // ===== Commands (all lowercase under /bgmsync) =====
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("bgmsync")
                // /bgmsync test  -> ask the DJ client to pick & start a random track (does not block passive music)
                .then(literal("test").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    var dj = getDJ(server);
                    if (dj == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] No DJ set."), false);
                        return 0;
                    }
                    ServerPlayNetworking.send(dj, BGMSyncPayloads.Test.INSTANCE);
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Test: asked DJ to start a random track."), true);
                    return 1;
                }))

                // /bgmsync stop -> stop the CURRENT track for everyone (does NOT block future music)
                .then(literal("stop").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    // Echo a stop to everyone including DJ
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(p, BGMSyncPayloads.Stop.INSTANCE);
                    }
                    currentTrackId = null;
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Stopped current track."), true);
                    return 1;
                }))

                // /bgmsync set <playerName>  -> set a new DJ
                .then(literal("set")
                    .then(argument("player", StringArgumentType.word()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        var server = ctx.getSource().getServer();
                        ServerPlayerEntity target = server.getPlayerManager().getPlayer(name);
                        if (target == null) {
                            ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Player not found."), false);
                            return 0;
                        }
                        currentDJ = target.getUuid();
                        // tell everyone who the DJ is; also inform each client if they are DJ or not
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, new BGMSyncPayloads.DjOnly(isDJ(p)));
                        }
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] DJ set to: " + target.getName().getString()), true);
                        return 1;
                    }))
                )

                // /bgmsync who -> show current DJ
                .then(literal("who").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    var dj = getDJ(server);
                    if (dj == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] No DJ set."), false);
                        return 0;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] DJ: " + dj.getName().getString()), false);
                    return 1;
                }))

                // bare /bgmsync -> help
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "[BGMSync] Commands: /bgmsync test | /bgmsync stop | /bgmsync set <player> | /bgmsync who"
                    ), false);
                    return 1;
                })
            );
        });
    }

    // ===== Helpers =====

    private void chooseDJOnStart(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            currentDJ = null;
            currentTrackId = null;
            return;
        }
        ServerPlayerEntity chosen = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        currentDJ = chosen.getUuid();
        currentTrackId = null;

        // tell everyone who is DJ
        for (ServerPlayerEntity p : players) {
            ServerPlayNetworking.send(p, new BGMSyncPayloads.DjOnly(isDJ(p)));
        }
        server.getPlayerManager().broadcast(Text.literal("[BGMSync] DJ: " + chosen.getName().getString()), false);
    }

    private static boolean isDJ(ServerPlayerEntity p) {
        return p != null && currentDJ != null && p.getUuid().equals(currentDJ);
    }

    private static ServerPlayerEntity getDJ(MinecraftServer server) {
        if (server == null || currentDJ == null) return null;
        return server.getPlayerManager().getPlayer(currentDJ);
    }
}
