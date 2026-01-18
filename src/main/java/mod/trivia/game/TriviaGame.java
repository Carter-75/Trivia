package mod.trivia.game;

import mod.trivia.ai.TriviaAiService;
import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.config.TriviaConfigManager;
import mod.trivia.punish.TriviaPunisher;
import mod.trivia.questions.TriviaQuestion;
import mod.trivia.questions.TriviaQuestionsManager;
import mod.trivia.reward.TriviaRewarder;
import mod.trivia.util.AnswerMatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class TriviaGame {
	private static final int QUESTION_NO_REPEAT_WINDOW = 20;

	private enum Phase {
		COOLDOWN,
		ACTIVE
	}

	private final TriviaQuestionsManager questionsManager = new TriviaQuestionsManager();
	private final TriviaRewarder rewarder = new TriviaRewarder();
	private final TriviaPunisher punisher = new TriviaPunisher();
	private final TriviaAiService ai = new TriviaAiService();

	private final RandomGenerator rng = RandomGenerator.getDefault();
	private final ArrayDeque<String> recentQuestionKeys = new ArrayDeque<>();
	private final HashSet<String> recentQuestionKeySet = new HashSet<>();

	private Phase phase = Phase.COOLDOWN;
	private long phaseTicksRemaining = 0;
	private TriviaRoundState round = new TriviaRoundState();
	private long roundId = 0;

	private long configLastModifiedMillis = -1;
	private long configCheckTicker = 0;

	public void reloadFromDisk() {
		TriviaConfigManager.loadAll();
		questionsManager.reload();
		rewarder.rebuildPools(TriviaConfigManager.getConfig());
		punisher.rebuildPools();
		resetToCooldown();
	}

	public String getActiveAnswerForAdmin() {
		if (phase != Phase.ACTIVE || round.activeQuestion == null) {
			return null;
		}
		String a = round.activeQuestion.answer;
		if (a == null) {
			return null;
		}
		return a.stripTrailing();
	}

	public void onServerTick(MinecraftServer server) {
		reloadConfigIfChanged(server);

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
		if (rawMessage == null || rawMessage.length() < prefix.length() || !rawMessage.startsWith(prefix)) {
			return false;
		}

		String guessDisplay = rawMessage.substring(prefix.length()).stripTrailing();
		String guessRaw = rawMessage.substring(prefix.length());
		String guess = normalizeAnswer(guessRaw);
		if (guess.isEmpty()) {
			player.sendMessage(Text.literal("Trivia: empty answer."), false);
			return true;
		}

		// Chat shortcut: .hint generates an AI hint (if enabled).
		if ("hint".equals(guess) || "h".equals(guess)) {
			return handleHintRequest(player, cfg);
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
		if (ps.aiValidationPending) {
			player.sendMessage(Text.literal("Trivia: already checking an answer, please wait..."), false);
			return true;
		}

		ps.guessedOnce = true;

		String correctAnswerRaw = round.activeQuestion.answer == null ? "" : round.activeQuestion.answer;
		boolean correctLocal = AnswerMatcher.isLikelyCorrectLocal(
			correctAnswerRaw,
			guessDisplay,
			cfg.fuzzyAnswerMatching,
			cfg.fuzzyMaxEditDistance
		);
		if (correctLocal) {
			handleCorrectGuess(player, ps, cfg, correctAnswerRaw);
			return true;
		}

		if (shouldTryAiValidation(cfg, correctAnswerRaw, guessDisplay)) {
			ps.aiValidationPending = true;
			ps.aiValidationRoundId = this.roundId;
			ps.pendingGuessDisplay = guessDisplay;
			ps.pendingGuessNormalized = guess;
			player.sendMessage(Text.literal("Trivia: checking your answer..."), false);

			String question = round.activeQuestion.question;
			ai.validateAnswer(cfg, question, correctAnswerRaw, guessDisplay)
				.thenAccept(result -> {
					MinecraftServer server = player.getServer();
					if (server == null) {
						return;
					}
					server.execute(() -> finalizeAiValidation(server, player, uuid, result));
				});
			return true;
		}

		handleWrongGuess(player, ps, cfg, guessDisplay);
		return true;
	}

	private static String normalizeAnswer(String s) {
		if (s == null) {
			return "";
		}
		// Trim trailing whitespace only (ending spaces) and ignore capitalization.
		return s.stripTrailing().toLowerCase(Locale.ROOT);
	}

	private boolean handleHintRequest(ServerPlayerEntity player, TriviaConfig cfg) {
		if (phase != Phase.ACTIVE || round.activeQuestion == null) {
			player.sendMessage(Text.literal("Trivia: no active question right now."), false);
			return true;
		}
		TriviaPlayerState ps = round.playerStates.computeIfAbsent(player.getUuid(), id -> new TriviaPlayerState());
		if (!ai.isEnabled(cfg)) {
			player.sendMessage(Text.literal("Trivia: AI hints are disabled (or API key is missing)."), false);
			return true;
		}
		// Requirement: hints are only available after at least one WRONG guess.
		if (ps.attemptsUsed <= 0) {
			player.sendMessage(Text.literal("Trivia: you can only use hints after at least 1 wrong guess."), false);
			return true;
		}

		// Global-hint mode: no private hints are allowed.
		if (cfg.aiHintsGlobalRequireAllPlayers) {
			MinecraftServer server = player.getServer();
			if (server == null) {
				player.sendMessage(Text.literal("Trivia: server unavailable."), false);
				return true;
			}

			if (round.globalHintRevealed) {
				player.sendMessage(Text.literal("Trivia: global hint already revealed this round."), false);
				return true;
			}

			// Only count players who have at least one wrong guess (attemptsUsed > 0).
			// Players who solved immediately (0 wrong guesses) never become eligible.
			boolean added = round.globalHintRequesters.add(player.getUuid());
			if (!added) {
				player.sendMessage(Text.literal("Trivia: you already requested the global hint."), false);
				return true;
			}

			int eligible = computeEligibleGlobalHintCount(server);
			int requested = computeRequestedEligibleGlobalHintCount(server);
			if (eligible <= 0) {
				// Shouldn't happen because requesters are required to have attemptsUsed>0, but keep it safe.
				player.sendMessage(Text.literal("Trivia: no eligible players for a global hint yet."), false);
				return true;
			}
			server.getPlayerManager().broadcast(
				Text.literal("Trivia: " + player.getName().getString() + " requested a global hint (" + requested + "/" + eligible + ")."),
				false
			);

			if (requested < eligible) {
				return true;
			}

			round.globalHintRevealed = true;
			server.getPlayerManager().broadcast(Text.literal("Trivia: generating a global hint..."), false);
			String q = round.activeQuestion.question;
			String a = round.activeQuestion.answer;
			ai.generateHint(cfg, q, a).thenAccept(hint -> {
				MinecraftServer srv = player.getServer();
				if (srv == null) {
					return;
				}
				srv.execute(() -> srv.getPlayerManager().broadcast(Text.literal("Trivia hint: " + hint), false));
			});
			return true;
		}

		// Default: private hint with per-player cooldown.
		long now = System.currentTimeMillis();
		int cooldownSeconds = Math.max(0, cfg.aiHintCooldownSeconds);
		long cooldownMillis = cooldownSeconds * 1000L;
		if (ps.lastHintRoundId == this.roundId && cooldownMillis > 0 && (now - ps.lastHintMillis) < cooldownMillis) {
			long left = (cooldownMillis - (now - ps.lastHintMillis) + 999) / 1000;
			player.sendMessage(Text.literal("Trivia: hint cooldown: " + left + "s"), false);
			return true;
		}
		ps.lastHintMillis = now;
		ps.lastHintRoundId = this.roundId;
		player.sendMessage(Text.literal("Trivia: generating hint..."), false);
		String q = round.activeQuestion.question;
		String a = round.activeQuestion.answer;
		ai.generateHint(cfg, q, a).thenAccept(hint -> {
			MinecraftServer server = player.getServer();
			if (server == null) {
				return;
			}
			server.execute(() -> player.sendMessage(Text.literal("Trivia hint: " + hint), false));
		});
		return true;
	}

	private static boolean isEligibleForGlobalHint(TriviaPlayerState ps) {
		if (ps == null) {
			return false;
		}
		// Eligible: at least 1 wrong guess, and still active (not solved, not failed-out).
		return ps.attemptsUsed > 0 && !ps.solved && !ps.failed;
	}

	private int computeEligibleGlobalHintCount(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		int eligible = 0;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			TriviaPlayerState ps = round.playerStates.get(p.getUuid());
			if (isEligibleForGlobalHint(ps)) {
				eligible++;
			}
		}
		return eligible;
	}

	private int computeRequestedEligibleGlobalHintCount(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		int requestedEligible = 0;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			TriviaPlayerState ps = round.playerStates.get(p.getUuid());
			if (!isEligibleForGlobalHint(ps)) {
				continue;
			}
			if (round.globalHintRequesters.contains(p.getUuid())) {
				requestedEligible++;
			}
		}
		return requestedEligible;
	}

	private void handleCorrectGuess(ServerPlayerEntity player, TriviaPlayerState ps, TriviaConfig cfg, String correctAnswerRaw) {
		ps.aiValidationPending = false;
		ps.solved = true;
		String correctAnswer = correctAnswerRaw == null ? "" : correctAnswerRaw.stripTrailing();
		TriviaRewarder.RewardResult reward = rewarder.reward(player, rng);
		if (reward != null) {
			ps.rewardCount = reward.count();
			ps.rewardItemName = reward.itemName();
		}
		player.sendMessage(Text.literal("Trivia: correct. Answer: " + normalizeAnswer(correctAnswer)), false);
		if (reward != null) {
			player.sendMessage(Text.literal("Trivia: reward: " + reward.count() + "x " + reward.itemName()), false);
		} else {
			player.sendMessage(Text.literal("Trivia: reward pool is empty."), false);
		}
		if (cfg.announceCorrectGuesses) {
			String suffix = cfg.showAnswerInstructions
				? " Hint: /trivia announce off, /trivia hint off"
				: "";
			player.getServer().getPlayerManager().broadcast(
				Text.literal("Trivia: " + player.getName().getString() + " guessed correctly!" + suffix),
				false
			);
		}
	}

	private void handleWrongGuess(ServerPlayerEntity player, TriviaPlayerState ps, TriviaConfig cfg, String guessDisplay) {
		ps.aiValidationPending = false;
		ps.guessedOnce = true;
		ps.attemptsUsed++;

		// Real-time global-hint eligibility counter updates (only when a player becomes eligible).
		if (cfg.aiHintsGlobalRequireAllPlayers && !round.globalHintRevealed && ps.attemptsUsed == 1) {
			MinecraftServer server = player.getServer();
			if (server != null) {
				int eligible = computeEligibleGlobalHintCount(server);
				int requested = computeRequestedEligibleGlobalHintCount(server);
				server.getPlayerManager().broadcast(
					Text.literal("Trivia: global hint progress (" + requested + "/" + eligible + ") eligible players."),
					false
				);
			}
		}
		if (cfg.maxAttempts >= 0 && ps.attemptsUsed >= cfg.maxAttempts) {
			ps.failed = true;
			punisher.punish(player, cfg, rng, "max attempts");
			return;
		}

		if (cfg.battleModeWrongGuessBroadcast) {
			String base = cfg.battleModeShowWrongGuesserName
				? ("Trivia: " + player.getName().getString() + "'s guess of " + guessDisplay + " was wrong.")
				: ("Trivia: a guess of " + guessDisplay + " was wrong.");
			String suffix = cfg.showAnswerInstructions
				? " Hint: /trivia battle name off, /trivia hint off"
				: "";
			player.getServer().getPlayerManager().broadcast(Text.literal(base + suffix), false);
		}

		String triesLeft = (cfg.maxAttempts < 0)
			? "unlimited"
			: Integer.toString(Math.max(0, cfg.maxAttempts - ps.attemptsUsed));
		player.sendMessage(Text.literal("Trivia: wrong. Tries left: " + triesLeft), false);
	}

	private boolean shouldTryAiValidation(TriviaConfig cfg, String correctAnswerRaw, String guessDisplay) {
		if (cfg == null || !cfg.aiSemanticAnswerValidation || !ai.isEnabled(cfg)) {
			return false;
		}
		String correctLoose = AnswerMatcher.normalizeLoose(correctAnswerRaw);
		String guessLoose = AnswerMatcher.normalizeLoose(guessDisplay);
		if (correctLoose.isEmpty() || guessLoose.isEmpty()) {
			return false;
		}
		// Only consult AI for "close" guesses to keep API usage sane.
		int max = Math.max(2, Math.max(cfg.fuzzyMaxEditDistance, 3) + 2);
		return AnswerMatcher.levenshteinWithin(correctLoose, guessLoose, max);
	}

	private void finalizeAiValidation(MinecraftServer server, ServerPlayerEntity player, UUID uuid, TriviaAiService.AiValidationResult result) {
		if (server == null || player == null || result == null) {
			return;
		}
		if (phase != Phase.ACTIVE || round.activeQuestion == null) {
			return;
		}
		TriviaPlayerState ps = round.playerStates.get(uuid);
		if (ps == null || !ps.aiValidationPending || ps.aiValidationRoundId != this.roundId) {
			return;
		}
		ps.aiValidationPending = false;

		// Player might have solved/failed via another path while we waited.
		if (ps.solved || ps.failed) {
			return;
		}

		TriviaConfig cfg = TriviaConfigManager.getConfig();
		String correctAnswerRaw = round.activeQuestion.answer;
		if (result.isCorrect()) {
			handleCorrectGuess(player, ps, cfg, correctAnswerRaw);
			return;
		}

		handleWrongGuess(player, ps, cfg, ps.pendingGuessDisplay == null ? "" : ps.pendingGuessDisplay);
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
		round.activeQuestion = pickRandomQuestionWithHistory(qs);
		this.roundId++;
		phase = Phase.ACTIVE;
		phaseTicksRemaining = Math.max(20, (long) cfg.questionDurationSeconds * 20L);

		String q = round.activeQuestion.question;
		server.getPlayerManager().broadcast(Text.literal("Trivia: " + q), false);
		if (cfg.showAnswerInstructions) {
			String triesText = (cfg.maxAttempts < 0) ? "unlimited" : Integer.toString(cfg.maxAttempts);
			String hintInfo = cfg.aiHintsGlobalRequireAllPlayers
				? "Hint: after 1+ wrong guess, all eligible players must type " + cfg.answerPrefix + "hint for 1 global hint"
				: "Hint: after 1+ wrong guess, type " + cfg.answerPrefix + "hint (AI must be enabled)";
			server.getPlayerManager().broadcast(
				Text.literal(
					"Answer with " + cfg.answerPrefix + "<answer> (tries: " + triesText + ", time: " + cfg.questionDurationSeconds + "s)"
						+ " | " + hintInfo
						+ " | Admin: /trivia hint off hides hint lines"
						+ ", /trivia battle off disables wrong-guess broadcasts"
						+ ", /trivia battle name off hides the guesser name"
						+ ", /trivia announce off hides correct-guess broadcasts"
						+ ", /trivia disable stops trivia"
				),
				false
			);
		}
	}

	private TriviaQuestion pickRandomQuestionWithHistory(List<TriviaQuestion> qs) {
		if (qs == null || qs.isEmpty()) {
			return null;
		}

		int window = Math.max(0, Math.min(QUESTION_NO_REPEAT_WINDOW, qs.size() - 1));
		if (window <= 0 || recentQuestionKeySet.isEmpty()) {
			TriviaQuestion picked = qs.get(rng.nextInt(qs.size()));
			recordPickedQuestion(picked, window);
			return picked;
		}

		List<TriviaQuestion> candidates = null;
		for (TriviaQuestion q : qs) {
			String key = questionKey(q);
			if (key.isEmpty() || !recentQuestionKeySet.contains(key)) {
				if (candidates == null) {
					candidates = new java.util.ArrayList<>();
				}
				candidates.add(q);
			}
		}

		TriviaQuestion picked;
		if (candidates == null || candidates.isEmpty()) {
			// Not enough unique questions to satisfy the window; fall back to any question.
			picked = qs.get(rng.nextInt(qs.size()));
		} else {
			picked = candidates.get(rng.nextInt(candidates.size()));
		}
		recordPickedQuestion(picked, window);
		return picked;
	}

	private void recordPickedQuestion(TriviaQuestion q, int window) {
		if (q == null) {
			return;
		}
		String key = questionKey(q);
		if (key.isEmpty()) {
			return;
		}
		recentQuestionKeys.addLast(key);
		recentQuestionKeySet.add(key);
		while (recentQuestionKeys.size() > window) {
			String removed = recentQuestionKeys.removeFirst();
			recentQuestionKeySet.remove(removed);
		}
	}

	private static String questionKey(TriviaQuestion q) {
		if (q == null) {
			return "";
		}
		String question = q.question == null ? "" : q.question.stripTrailing();
		String answer = q.answer == null ? "" : q.answer.stripTrailing();
		if (question.isBlank()) {
			return "";
		}
		return question + "\n" + answer;
	}

	private void endRound(MinecraftServer server) {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		String answer = (round.activeQuestion != null && round.activeQuestion.answer != null)
			? round.activeQuestion.answer.stripTrailing()
			: "";

		// Winner summary (rewards are granted immediately on correct guess).
		List<String> winners = new java.util.ArrayList<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			TriviaPlayerState ps = round.playerStates.get(player.getUuid());
			if (ps == null || !ps.solved) {
				continue;
			}
			String rewardText = (ps.rewardItemName != null && !ps.rewardItemName.isBlank() && ps.rewardCount > 0)
				? (ps.rewardCount + "x " + ps.rewardItemName)
				: "reward";
			winners.add(player.getName().getString() + " (" + rewardText + ")");
		}

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
		if (!winners.isEmpty()) {
			server.getPlayerManager().broadcast(Text.literal("Trivia: winners: " + String.join(", ", winners)), false);
		} else {
			server.getPlayerManager().broadcast(Text.literal("Trivia: nobody guessed correctly."), false);
		}
		resetToCooldown();
	}

	private void resetToCooldown() {
		TriviaConfig cfg = TriviaConfigManager.getConfig();
		phase = Phase.COOLDOWN;
		phaseTicksRemaining = Math.max(20, (long) cfg.cooldownSeconds * 20L);
		round = new TriviaRoundState();
		TriviaMod.LOGGER.info("Trivia cooldown started: {}s", cfg.cooldownSeconds);
	}

	private void reloadConfigIfChanged(MinecraftServer server) {
		configCheckTicker++;
		if ((configCheckTicker % 20) != 0) {
			return;
		}
		Path settings = TriviaConfigManager.getSettingsPath();
		try {
			if (Files.notExists(settings)) {
				return;
			}
			long m = Files.getLastModifiedTime(settings).toMillis();
			if (configLastModifiedMillis == -1) {
				configLastModifiedMillis = m;
				return;
			}
			if (m == configLastModifiedMillis) {
				return;
			}
			configLastModifiedMillis = m;
			TriviaConfigManager.loadAll();
			TriviaConfig cfg = TriviaConfigManager.getConfig();
			rewarder.rebuildPools(cfg);
			punisher.rebuildPools();
			TriviaMod.LOGGER.info("Trivia settings.json changed; config auto-reloaded.");
		} catch (Exception e) {
			TriviaMod.LOGGER.warn("Trivia config auto-reload failed: {}", e.getMessage());
		}
	}
}