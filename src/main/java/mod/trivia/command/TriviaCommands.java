package mod.trivia.command;

import com.mojang.brigadier.CommandDispatcher;
import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.config.TriviaConfigManager;
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
				.then(CommandManager.literal("status")
					.executes(ctx -> {
						TriviaConfig cfg = TriviaConfigManager.getConfig();
						ctx.getSource().sendFeedback(() -> Text.literal("Trivia enabled: " + cfg.enabled), false);
						return 1;
					})
				)
				.then(CommandManager.literal("enable")
					.executes(ctx -> setEnabled(ctx.getSource(), game, true))
				)
				.then(CommandManager.literal("disable")
					.executes(ctx -> setEnabled(ctx.getSource(), game, false))
				)
				.then(CommandManager.literal("toggle")
					.executes(ctx -> {
						TriviaConfig cfg = TriviaConfigManager.getConfig();
						return setEnabled(ctx.getSource(), game, !cfg.enabled);
					})
				)
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

	private static int setEnabled(ServerCommandSource source, TriviaGame game, boolean enabled) {
		try {
			TriviaConfig cfg = TriviaConfigManager.getConfig();
			if (cfg.enabled == enabled) {
				source.sendFeedback(() -> Text.literal("Trivia already " + (enabled ? "enabled" : "disabled") + "."), false);
				return 1;
			}
			cfg.enabled = enabled;
			TriviaConfigManager.saveConfig(cfg);
			game.reloadFromDisk();
			source.sendFeedback(() -> Text.literal("Trivia " + (enabled ? "enabled" : "disabled") + "."), true);
			return 1;
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Trivia enable/disable failed", e);
			source.sendError(Text.literal("Trivia enable/disable failed: " + e.getMessage()));
			return 0;
		}
	}
}