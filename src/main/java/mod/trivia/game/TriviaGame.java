package mod.trivia.game;

import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.config.TriviaConfigManager;
import mod.trivia.punish.TriviaPunisher;
import mod.trivia.questions.TriviaQuestion;
import mod.trivia.questions.TriviaQuestionsManager;
import mod.trivia.reward.TriviaRewarder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class TriviaGame {
	private enum Phase {
		COOLDOWN,
		ACTIVE
	}

	private final TriviaQuestionsManager questionsManager = new TriviaQuestionsManager();
	private final TriviaRewarder rewarder = new TriviaRewarder();
	private final TriviaPunisher punisher = new TriviaPunisher();

	private final RandomGenerator rng = RandomGenerator.getDefault();

	private Phase phase = Phase.COOLDOWN;
	private long phaseTicksRemaining = 0;
	private TriviaRoundState round = new TriviaRoundState();

	public void reloadFromDisk() {
		TriviaConfigManager.loadAll();
		questionsManager.reload();
		rewarder.rebuildPools(TriviaConfigManager.getConfig());
		punisher.rebuildPools();
		resetToCooldown();
	}

	public void onServerTick(MinecraftServer server) {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		if (!cfg.enabled) {
			return;
		}

		if (phaseTicksRemaining > 0) {
			phaseTicksRemaining--;
		}

		if (phaseTicksRemaining > 0) {
			return;
		}

		if (phase == Phase.COOLDOWN) {
			startRound(server);
			return;
		}

		if (phase == Phase.ACTIVE) {
			endRound(server);
		}
	}

	/**
	 * Forces a new random question to start immediately if (and only if) no question is active.
	 * This skips any remaining cooldown. When the forced round ends, the cooldown restarts normally.
	 */
	public boolean forceStartRandomQuestionIfIdle(MinecraftServer server) {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		if (!cfg.enabled) {
			return false;
		}
		if (phase == Phase.ACTIVE && round.activeQuestion != null) {
			return false;
		}

		// Skip remaining cooldown and start immediately.
		phase = Phase.COOLDOWN;
		phaseTicksRemaining = 0;
		startRound(server);
		return phase == Phase.ACTIVE && round.activeQuestion != null;
	}

	public boolean onPlayerAttempt(ServerPlayerEntity player, String rawMessage) {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		if (!cfg.enabled) {
			return false;
		}
		if (phase != Phase.ACTIVE || round.activeQuestion == null) {
			return false;
		}

		String prefix = cfg.answerPrefix == null ? "." : cfg.answerPrefix;
		String guess = rawMessage.substring(prefix.length());
		// Requirement: trim ending spaces; ignore capitalization.
		guess = normalizeAnswer(guess);
		if (guess.isEmpty()) {
			player.sendMessage(Text.literal("Trivia: empty answer."), false);
			return true;
		}

		UUID uuid = player.getUuid();
		TriviaPlayerState ps = round.playerStates.computeIfAbsent(uuid, id -> new TriviaPlayerState());
		if (ps.solved) {
			player.sendMessage(Text.literal("Trivia: you already solved this one."), false);
			return true;
		}
		if (ps.failed) {
			player.sendMessage(Text.literal("Trivia: you already failed this one."), false);
			return true;
		}

		ps.guessedOnce = true;

		String correctAnswer = round.activeQuestion.answer == null ? "" : normalizeAnswer(round.activeQuestion.answer);
		boolean correct = correctAnswer.equals(guess);
		if (correct) {
			ps.solved = true;
			rewarder.reward(player, rng);
			player.sendMessage(Text.literal("Trivia: correct. Answer: " + correctAnswer), false);
			return true;
		}

		ps.attemptsUsed++;
		if (cfg.maxAttempts >= 0 && ps.attemptsUsed >= cfg.maxAttempts) {
			ps.failed = true;
			punisher.punish(player, cfg, rng, "max attempts");
			return true;
		}

		String triesLeft = (cfg.maxAttempts < 0)
			? "unlimited"
			: Integer.toString(Math.max(0, cfg.maxAttempts - ps.attemptsUsed));
		player.sendMessage(Text.literal("Trivia: wrong. Tries left: " + triesLeft), false);
		return true;
	}

	private static String normalizeAnswer(String s) {
		if (s == null) {
			return "";
		}
		// Trim trailing whitespace only (ending spaces) and ignore capitalization.
		return s.stripTrailing().toLowerCase(Locale.ROOT);
	}

	private void startRound(MinecraftServer server) {
		List<TriviaQuestion> qs = questionsManager.getQuestions();
		if (qs.isEmpty()) {
			// Try again later.
			phase = Phase.COOLDOWN;
			phaseTicksRemaining = 20L * 60;
			return;
		}

		TriviaConfig cfg = TriviaConfigManager.getConfig();
		round = new TriviaRoundState();
		round.activeQuestion = qs.get(rng.nextInt(qs.size()));
		phase = Phase.ACTIVE;
		phaseTicksRemaining = Math.max(20, (long) cfg.questionDurationSeconds * 20L);

		String q = round.activeQuestion.question;
		server.getPlayerManager().broadcast(Text.literal("Trivia: " + q), false);
		if (cfg.showAnswerInstructions) {
			String triesText = (cfg.maxAttempts < 0) ? "unlimited" : Integer.toString(cfg.maxAttempts);
			server.getPlayerManager().broadcast(
				Text.literal(
					"Answer with " + cfg.answerPrefix + "<answer> (tries: " + triesText + ", time: " + cfg.questionDurationSeconds + "s)"
						+ " | Admin: /trivia hint off to hide this line, /trivia disable to stop trivia"
				),
				false
			);
		}
	}

	private void endRound(MinecraftServer server) {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		String answer = (round.activeQuestion != null && round.activeQuestion.answer != null)
			? round.activeQuestion.answer.stripTrailing()
			: "";

		// Apply timeout punishments
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			TriviaPlayerState ps = round.playerStates.get(player.getUuid());
			if (ps == null) {
				continue;
			}
			if (ps.solved || ps.failed) {
				continue;
			}
			if (!ps.guessedOnce) {
				continue;
			}
			ps.failed = true;
			punisher.punish(player, cfg, rng, "time limit");
		}

		server.getPlayerManager().broadcast(Text.literal("Trivia: time is up. Answer: " + answer), false);
		resetToCooldown();
	}

	private void resetToCooldown() {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		phase = Phase.COOLDOWN;
		phaseTicksRemaining = Math.max(20, (long) cfg.cooldownSeconds * 20L);
		round = new TriviaRoundState();
		TriviaMod.LOGGER.info("Trivia cooldown started: {}s", cfg.cooldownSeconds);
	}
}