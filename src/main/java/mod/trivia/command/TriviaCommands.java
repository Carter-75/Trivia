package mod.trivia.command;

import com.mojang.brigadier.CommandDispatcher;
import mod.trivia.TriviaMod;
import mod.trivia.game.TriviaGame;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class TriviaCommands {
	private TriviaCommands() {
	}

	public static void register(TriviaGame game) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			registerInternal(dispatcher, game)
		);
	}

	private static void registerInternal(CommandDispatcher<ServerCommandSource> dispatcher, TriviaGame game) {
		dispatcher.register(
			CommandManager.literal("trivia")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("reload")
					.executes(ctx -> {
						try {
							game.reloadFromDisk();
							ctx.getSource().sendFeedback(() -> Text.literal("Trivia reloaded (config + questions)."), false);
							return 1;
						} catch (Exception e) {
							TriviaMod.LOGGER.error("Trivia reload failed", e);
							ctx.getSource().sendError(Text.literal("Trivia reload failed: " + e.getMessage()));
							return 0;
						}
					})
				)
		);
	}
}