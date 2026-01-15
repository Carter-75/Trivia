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
							() -> Text.literal(
								"Trivia enabled: " + cfg.enabled
									+ " | hint line: " + (cfg.showAnswerInstructions ? "ON" : "OFF")
									+ " | announce: " + (cfg.announceCorrectGuesses ? "ON" : "OFF")
									+ " | battle: " + (cfg.battleModeWrongGuessBroadcast ? "ON" : "OFF")
									+ " | battle name: " + (cfg.battleModeShowWrongGuesserName ? "ON" : "OFF")
									+ " | rewardCountOverride: " + cfg.rewardCountOverride
							),
							false
						);
						return 1;
					})
				)
				.then(CommandManager.literal("announce")
					.then(CommandManager.literal("on")
						.executes(ctx -> setAnnounce(ctx.getSource(), game, true))
					)
					.then(CommandManager.literal("off")
						.executes(ctx -> setAnnounce(ctx.getSource(), game, false))
					)
					.then(CommandManager.literal("toggle")
						.executes(ctx -> {
							TriviaConfig cfg = TriviaConfigManager.getConfig();
							return setAnnounce(ctx.getSource(), game, !cfg.announceCorrectGuesses);
						})
					)
				)
				.then(CommandManager.literal("battle")
					.then(CommandManager.literal("on")
						.executes(ctx -> setBattleMode(ctx.getSource(), game, true))
					)
					.then(CommandManager.literal("off")
						.executes(ctx -> setBattleMode(ctx.getSource(), game, false))
					)
					.then(CommandManager.literal("toggle")
						.executes(ctx -> {
							TriviaConfig cfg = TriviaConfigManager.getConfig();
							return setBattleMode(ctx.getSource(), game, !cfg.battleModeWrongGuessBroadcast);
						})
					)
					.then(CommandManager.literal("name")
						.then(CommandManager.literal("on")
							.executes(ctx -> setBattleName(ctx.getSource(), game, true))
						)
						.then(CommandManager.literal("off")
							.executes(ctx -> setBattleName(ctx.getSource(), game, false))
						)
						.then(CommandManager.literal("toggle")
							.executes(ctx -> {
								TriviaConfig cfg = TriviaConfigManager.getConfig();
								return setBattleName(ctx.getSource(), game, !cfg.battleModeShowWrongGuesserName);
							})
						)
					)
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

	private static int setBattleMode(ServerCommandSource source, TriviaGame game, boolean battleModeWrongGuessBroadcast) {
		try {
			TriviaConfig cfg = TriviaConfigManager.getConfig();
			if (cfg.battleModeWrongGuessBroadcast == battleModeWrongGuessBroadcast) {
				source.sendFeedback(
					() -> Text.literal("Trivia battle mode already " + (battleModeWrongGuessBroadcast ? "ON" : "OFF") + "."),
					false
				);
				return 1;
			}
			cfg.battleModeWrongGuessBroadcast = battleModeWrongGuessBroadcast;
			TriviaConfigManager.saveConfig(cfg);
			game.reloadFromDisk();
			source.sendFeedback(
				() -> Text.literal("Trivia battle mode is now " + (battleModeWrongGuessBroadcast ? "ON" : "OFF") + "."),
				true
			);
			return 1;
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Trivia battle toggle failed", e);
			source.sendError(Text.literal("Trivia battle toggle failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int setBattleName(ServerCommandSource source, TriviaGame game, boolean battleModeShowWrongGuesserName) {
		try {
			TriviaConfig cfg = TriviaConfigManager.getConfig();
			if (cfg.battleModeShowWrongGuesserName == battleModeShowWrongGuesserName) {
				source.sendFeedback(
					() -> Text.literal("Trivia battle name display already " + (battleModeShowWrongGuesserName ? "ON" : "OFF") + "."),
					false
				);
				return 1;
			}
			cfg.battleModeShowWrongGuesserName = battleModeShowWrongGuesserName;
			TriviaConfigManager.saveConfig(cfg);
			game.reloadFromDisk();
			source.sendFeedback(
				() -> Text.literal("Trivia battle name display is now " + (battleModeShowWrongGuesserName ? "ON" : "OFF") + "."),
				true
			);
			return 1;
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Trivia battle name toggle failed", e);
			source.sendError(Text.literal("Trivia battle name toggle failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int setAnnounce(ServerCommandSource source, TriviaGame game, boolean announceCorrectGuesses) {
		try {
			TriviaConfig cfg = TriviaConfigManager.getConfig();
			if (cfg.announceCorrectGuesses == announceCorrectGuesses) {
				source.sendFeedback(
					() -> Text.literal("Trivia correct-guess announce already " + (announceCorrectGuesses ? "ON" : "OFF") + "."),
					false
				);
				return 1;
			}
			cfg.announceCorrectGuesses = announceCorrectGuesses;
			TriviaConfigManager.saveConfig(cfg);
			game.reloadFromDisk();
			source.sendFeedback(
				() -> Text.literal("Trivia correct-guess announce is now " + (announceCorrectGuesses ? "ON" : "OFF") + "."),
				true
			);
			return 1;
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Trivia announce toggle failed", e);
			source.sendError(Text.literal("Trivia announce toggle failed: " + e.getMessage()));
			return 0;
		}
	}
}