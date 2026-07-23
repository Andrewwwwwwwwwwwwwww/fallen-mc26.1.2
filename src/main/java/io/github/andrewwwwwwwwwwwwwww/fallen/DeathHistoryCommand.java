package io.github.andrewwwwwwwwwwwwwww.fallen;

import io.github.andrewwwwwwwwwwwwwww.fallen.net.FallenNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;

/** {@code /deathhistory <player>} — operators can view another player's death history. */
public final class DeathHistoryCommand {
    private DeathHistoryCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("deathhistory")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> {
                                    ServerPlayer viewer = ctx.getSource().getPlayerOrException();
                                    Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(ctx, "player");
                                    for (NameAndId target : targets) {
                                        FallenNetworking.sendHistory(viewer, target.id(), target.name());
                                    }
                                    if (targets.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("No such player."));
                                    }
                                    return targets.size();
                                }))));
    }
}
