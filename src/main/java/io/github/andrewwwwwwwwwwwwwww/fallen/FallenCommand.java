package io.github.andrewwwwwwwwwwwwwww.fallen;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /fallen debug <true|false>} — turn on the death handler's reasoning.
 *
 * <p>A body that never appears looks exactly like an ordinary death from the
 * outside, so this is the way to find out which check declined it: every path
 * that skips a body logs its reason to the server console while this is on.
 * Existing bodies are ordinary entities, so vanilla selectors still find them
 * ({@code /execute as @e[type=fallen:corpse] run ...}).
 */
public final class FallenCommand {
    private FallenCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("fallen")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            CorpseConfig.get().debugLogging = enabled;
                                            CorpseConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    enabled
                                                            ? "Fallen debug logging ON — every death decision "
                                                                    + "will be logged to the server console."
                                                            : "Fallen debug logging OFF."), true);
                                            return 1;
                                        })))));
    }
}
