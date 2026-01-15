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
						ctx.getSource().sendFeedback(
							() -> Text.literal("Trivia enabled: " + cfg.enabled + " | hint line: " + (cfg.showAnswerInstructions ? "ON" : "OFF")),
							false
						);
						return 1;
					})
				)
				.then(CommandManager.literal("hint")
					.then(CommandManager.literal("on")
						.executes(ctx -> setShowInstructions(ctx.getSource(), game, true))
					)
					.then(CommandManager.literal("off")
						.executes(ctx -> setShowInstructions(ctx.getSource(), game, false))
					)
					.then(CommandManager.literal("toggle")
						.executes(ctx -> {
							TriviaConfig cfg = TriviaConfigManager.getConfig();
							return setShowInstructions(ctx.getSource(), game, !cfg.showAnswerInstructions);
						})
					)
				)
				.then(CommandManager.literal("ask")
					.executes(ctx -> forceAsk(ctx.getSource(), game))
				)
				.then(CommandManager.literal("next")
					.executes(ctx -> forceAsk(ctx.getSource(), game))
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

	private static int forceAsk(ServerCommandSource source, TriviaGame game) {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		if (!cfg.enabled) {
			source.sendError(Text.literal("Trivia is disabled. Use /trivia enable first."));
			return 0;
		}
		boolean started = game.forceStartRandomQuestionIfIdle(source.getServer());
		if (!started) {
			source.sendError(Text.literal("A trivia question is already active."));
			return 0;
		}
		source.sendFeedback(() -> Text.literal("Started a new trivia question (cooldown restarted after it ends)."), true);
		return 1;
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

	private static int setShowInstructions(ServerCommandSource source, TriviaGame game, boolean showInstructions) {
		try {
			TriviaConfig cfg = TriviaConfigManager.getConfig();
			if (cfg.showAnswerInstructions == showInstructions) {
				source.sendFeedback(
					() -> Text.literal("Trivia instruction line already " + (showInstructions ? "ON" : "OFF") + "."),
					false
				);
				return 1;
			}
			cfg.showAnswerInstructions = showInstructions;
			TriviaConfigManager.saveConfig(cfg);
			game.reloadFromDisk();
			source.sendFeedback(
				() -> Text.literal("Trivia instruction line is now " + (showInstructions ? "ON" : "OFF") + "."),
				true
			);
			return 1;
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Trivia hint toggle failed", e);
			source.sendError(Text.literal("Trivia hint toggle failed: " + e.getMessage()));
			return 0;
		}
	}
}