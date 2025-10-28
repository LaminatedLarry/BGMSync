package com.bgmsync;

import com.bgmsync.util.MusicIds;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BGMSync implements ModInitializer {
    public static final String MODID = "bgmsync";

    public static final Identifier PACKET_PLAY      = Identifier.of(MODID, "play");
    public static final Identifier PACKET_STOP      = Identifier.of(MODID, "stop");
    public static final Identifier PACKET_TEST      = Identifier.of(MODID, "test");
    public static final Identifier PACKET_DJ_ONLY   = Identifier.of(MODID, "dj_only");
    public static final Identifier PACKET_FORCEPLAY = Identifier.of(MODID, "force_play");

    private static UUID currentDJ = null;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::chooseDJ);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentDJ = null);

        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            String id = payload.soundId();
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null || !MusicIds.isMusicId(parsed)) return;
            context.server().execute(() -> broadcastPlay(context.server(), id));
        });

        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) ->
            context.server().execute(() -> broadcastStop(context.server()))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                ServerPlayerEntity p = handler.player;
                boolean isDj = isDJ(p);
                ServerPlayNetworking.send(p, BGMSyncPayloads.DjOnly.of(isDj));
            })
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("bgmsync")
                .then(literal("test").executes(ctx -> {
                    ServerPlayerEntity dj = getDJ(ctx.getSource().getServer());
                    if (dj == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] No DJ available."), false);
                        return 0;
                    }
                    // FIX: Test is a singleton/enum – use INSTANCE
                    ServerPlayNetworking.send(dj, BGMSyncPayloads.Test.INSTANCE);
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Test triggered for DJ: " + dj.getName().getString()), true);
                    return 1;
                }))

                .then(literal("stop").executes(ctx -> {
                    broadcastStop(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Stopped current music for all players."), true);
                    return 1;
                }))

                .then(literal("set")
                    .then(argument("player", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
                        .executes(this::cmdSetDj)))

                .then(literal("who").executes(ctx -> {
                    ServerPlayerEntity dj = getDJ(ctx.getSource().getServer());
                    String name = (dj == null) ? "none" : dj.getName().getString();
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] DJ: " + name), false);
                    return 1;
                }))

                .then(literal("play")
                    .then(argument("id", StringArgumentType.string())
                        .suggests((c, b) -> {
                            for (String id : MusicIds.collectAll()) b.suggest(id);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            Identifier parsed = Identifier.tryParse(id);
                            if (parsed == null || !MusicIds.isMusicId(parsed)) {
                                ctx.getSource().sendError(Text.literal("[BGMSync] Not a music track id: " + id));
                                return 0;
                            }
                            ServerPlayerEntity dj = getDJ(ctx.getSource().getServer());
                            if (dj == null) {
                                ctx.getSource().sendError(Text.literal("[BGMSync] No DJ available."));
                                return 0;
                            }
                            ServerPlayNetworking.send(dj, new BGMSyncPayloads.ForcePlay(id));
                            ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Requested: " + id), true);
                            return 1;
                        })))
            );
        });
    }

    private int cmdSetDj(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        try {
            Collection<com.mojang.authlib.GameProfile> gps =
                net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "player");
            if (gps.isEmpty()) {
                ctx.getSource().sendError(Text.literal("[BGMSync] No such player."));
                return 0;
            }
            UUID target = gps.iterator().next().getId();
            ServerPlayerEntity chosen = server.getPlayerManager().getPlayer(target);
            if (chosen == null) {
                ctx.getSource().sendError(Text.literal("[BGMSync] Player not online."));
                return 0;
            }
            currentDJ = chosen.getUuid();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                boolean isDj = isDJ(p);
                ServerPlayNetworking.send(p, BGMSyncPayloads.DjOnly.of(isDj));
            }
            server.getPlayerManager().broadcast(Text.literal("[BGMSync] DJ is now: " + chosen.getName().getString()), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[BGMSync] Failed to set DJ: " + e.getMessage()));
            return 0;
        }
    }

    private void broadcastPlay(MinecraftServer server, String soundId) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new BGMSyncPayloads.Play(soundId));
        }
    }

    private void broadcastStop(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            // FIX: Stop is a singleton/enum – use INSTANCE
            ServerPlayNetworking.send(p, BGMSyncPayloads.Stop.INSTANCE);
        }
    }

    private void chooseDJ(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        currentDJ = players.isEmpty() ? null : players.get(0).getUuid();
        for (ServerPlayerEntity p : players) {
            boolean isDj = isDJ(p);
            ServerPlayNetworking.send(p, BGMSyncPayloads.DjOnly.of(isDj));
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
