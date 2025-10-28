package com.bgmsync;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
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

    private static UUID currentDJ = null;
    private static String currentTrackId = null; // null = none

    // ===== Suggestions for /bgmsync play <sound_id> =====
    private static final SuggestionProvider<ServerCommandSource> SOUND_SUGGESTER = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        Registry<net.minecraft.sound.SoundEvent> reg = server.getRegistryManager().get(RegistryKeys.SOUND_EVENT);
        for (RegistryKey<net.minecraft.sound.SoundEvent> key : reg.getKeys()) {
            builder.suggest(key.getValue().toString());
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        BGMSyncPayloads.registerAll();

        ServerLifecycleEvents.SERVER_STARTED.register(this::chooseDJOnStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            currentDJ = null;
            currentTrackId = null;
        });

        // DJ -> Server: a specific track started (natural or forced/Test)
        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Play.ID, (payload, context) -> {
            var server = context.server();
            server.execute(() -> {
                ServerPlayerEntity sender = context.player();
                if (sender == null || !isDJ(sender)) return;

                String soundId = payload.soundId();
                currentTrackId = soundId;

                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (!p.getUuid().equals(sender.getUuid())) {
                        ServerPlayNetworking.send(p, new BGMSyncPayloads.Play(soundId));
                    }
                }
            });
        });

        // DJ -> Server: current track stopped naturally
        ServerPlayNetworking.registerGlobalReceiver(BGMSyncPayloads.Stop.ID, (payload, context) -> {
            var server = context.server();
            server.execute(() -> {
                ServerPlayerEntity sender = context.player();
                if (sender == null || !isDJ(sender)) return;

                currentTrackId = null;
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (!p.getUuid().equals(sender.getUuid())) {
                        ServerPlayNetworking.send(p, BGMSyncPayloads.Stop.INSTANCE);
                    }
                }
            });
        });

        // On join: tell player whether they are DJ, and if a track is active, start it for them
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            boolean playerIsDJ = isDJ(player);
            ServerPlayNetworking.send(player, new BGMSyncPayloads.DjOnly(playerIsDJ));
            if (!playerIsDJ && currentTrackId != null) {
                ServerPlayNetworking.send(player, new BGMSyncPayloads.Play(currentTrackId));
            }
        });

        // ===== Commands =====
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("bgmsync")

                // /bgmsync test  (ask DJ client to pick a random track; doesn't block passive music)
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

                // /bgmsync stop  (stop only the current track for everyone; future music unaffected)
                .then(literal("stop").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(p, BGMSyncPayloads.Stop.INSTANCE);
                    }
                    currentTrackId = null;
                    ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Stopped current track."), true);
                    return 1;
                }))

                // /bgmsync play <sound_id>  (DJ-only; plays exact specified track)
                .then(literal("play")
                    .then(argument("sound_id", StringArgumentType.greedyString()).suggests(SOUND_SUGGESTER).executes(ctx -> {
                        var server = ctx.getSource().getServer();
                        var dj = getDJ(server);
                        if (dj == null) {
                            ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] No DJ set."), false);
                            return 0;
                        }
                        // Only allow the current DJ (or ops using console)
                        ServerPlayerEntity executor = ctx.getSource().getPlayer();
                        if (executor != null && !isDJ(executor) && !ctx.getSource().hasPermissionLevel(2)) {
                            ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Only the DJ can use /bgmsync play."), false);
                            return 0;
                        }

                        String idStr = StringArgumentType.getString(ctx, "sound_id").trim();
                        Identifier id;
                        try {
                            id = Identifier.of(idStr);
                        } catch (IllegalArgumentException ex) {
                            ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Invalid id: " + idStr), false);
                            return 0;
                        }

                        Registry<net.minecraft.sound.SoundEvent> reg = server.getRegistryManager().get(RegistryKeys.SOUND_EVENT);
                        if (!reg.containsId(id)) {
                            ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Unknown track: " + id), false);
                            return 0;
                        }

                        // Ask DJ client to force-play THIS specific id (so timing is the same for everyone).
                        ServerPlayNetworking.send(dj, new BGMSyncPayloads.ForcePlay(id.toString()));
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] Playing " + id + " on DJ."), true);
                        return 1;
                    }))
                )

                // /bgmsync set <player>
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
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, new BGMSyncPayloads.DjOnly(isDJ(p)));
                        }
                        ctx.getSource().sendFeedback(() -> Text.literal("[BGMSync] DJ set to: " + target.getName().getString()), true);
                        return 1;
                    }))
                )

                // /bgmsync who
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

                // bare /bgmsync
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "[BGMSync] /bgmsync test | /bgmsync stop | /bgmsync play <sound_id> | /bgmsync set <player> | /bgmsync who"
                    ), false);
                    return 1;
                })
            );
        });
    }

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
