package mod.trivia.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.util.AnswerMatcher;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class TriviaAiService {
	private static final Gson GSON = new GsonBuilder().create();

	private final OpenAiClient client = new OpenAiClient();
	private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "trivia-openai");
		t.setDaemon(true);
		return t;
	});

	// Basic global rate limiting to avoid API spam.
	private final AtomicLong lastRequestMillis = new AtomicLong(0);

	public boolean isEnabled(TriviaConfig cfg) {
		return cfg != null && cfg.aiEnabled && cfg.openAiApiKey != null && !cfg.openAiApiKey.isBlank();
	}

	public CompletableFuture<String> generateHint(TriviaConfig cfg, String question, String answer) {
		Objects.requireNonNull(cfg, "cfg");
		String q = question == null ? "" : question.strip();
		String a = answer == null ? "" : answer.strip();
		if (q.isBlank() || a.isBlank()) {
			return CompletableFuture.completedFuture("Hint unavailable.");
		}
		if (!isEnabled(cfg)) {
			return CompletableFuture.completedFuture("Hint unavailable.");
		}

		if (!tryRateLimitOk()) {
			return CompletableFuture.completedFuture("AI is busy; try again in a moment.");
		}

		String system = String.join("\n",
			"You are a Minecraft trivia hint generator.",
			"Rules:",
			"- Do NOT reveal the answer directly.",
			"- Do NOT include the answer, even partially.",
			"- Provide ONE helpful hint, aimed at a player.",
			"- Keep it short (<= 160 characters).",
			"- No quotes, no extra commentary.",
			"- If you cannot comply, output exactly: HINT_UNAVAILABLE"
		);

		String user = String.join("\n",
			"Question:",
			q,
			"",
			"Answer (hidden from players; DO NOT reveal):",
			a,
			"",
			"Return only the hint text."
		);

		Duration timeout = Duration.ofSeconds(Math.max(1, cfg.aiRequestTimeoutSeconds));
		return CompletableFuture.supplyAsync(() -> null, executor)
			.thenCompose(ignored -> client.chatCompletion(
				cfg.openAiApiKey,
				cfg.openAiModel,
				List.of(
					new OpenAiClient.Message("system", system),
					new OpenAiClient.Message("user", user)
				),
				0.2,
				140,
				timeout
			))
			.thenApply(raw -> sanitizeHint(raw, a))
			.exceptionally(ex -> {
				TriviaMod.LOGGER.warn("Trivia AI hint failed: {}", ex.getMessage());
				return "Hint unavailable.";
			});
	}

	public CompletableFuture<AiValidationResult> validateAnswer(TriviaConfig cfg, String question, String canonicalAnswer, String playerGuess) {
		Objects.requireNonNull(cfg, "cfg");
		String q = question == null ? "" : question.strip();
		String a = canonicalAnswer == null ? "" : canonicalAnswer.strip();
		String g = playerGuess == null ? "" : playerGuess.strip();

		if (q.isBlank() || a.isBlank() || g.isBlank()) {
			return CompletableFuture.completedFuture(new AiValidationResult(false, "missing input"));
		}
		if (!isEnabled(cfg)) {
			return CompletableFuture.completedFuture(new AiValidationResult(false, "ai disabled"));
		}
		if (!cfg.aiSemanticAnswerValidation) {
			return CompletableFuture.completedFuture(new AiValidationResult(false, "aiSemanticAnswerValidation disabled"));
		}

		if (!tryRateLimitOk()) {
			return CompletableFuture.completedFuture(new AiValidationResult(false, "ai busy"));
		}

		String system = String.join("\n",
			"You are a strict trivia answer judge for a Minecraft server.",
			"You will be given a trivia question, the canonical correct answer, and a player's guess.",
			"Decide whether the guess should be accepted as correct.",
			"Accept when:",
			"- The guess is the same answer with different casing, punctuation, missing/extra spaces.",
			"- Minor spelling errors/typos that clearly refer to the same answer.",
			"- Common abbreviations or well-known equivalent names (e.g., USA vs United States).",
			"Reject when:",
			"- The guess is a different entity/meaning.",
			"- The guess is only a partial answer unless the canonical answer is itself partial.",
			"Output MUST be valid JSON with keys: isCorrect (boolean), reason (string).",
			"No extra keys, no markdown."
		);

		JsonObject payload = new JsonObject();
		payload.addProperty("question", q);
		payload.addProperty("canonicalAnswer", a);
		payload.addProperty("playerGuess", g);

		String user = "Decide if playerGuess is correct.\n\n" + GSON.toJson(payload);

		Duration timeout = Duration.ofSeconds(Math.max(1, cfg.aiRequestTimeoutSeconds));
		return CompletableFuture.supplyAsync(() -> null, executor)
			.thenCompose(ignored -> client.chatCompletion(
				cfg.openAiApiKey,
				cfg.openAiModel,
				List.of(
					new OpenAiClient.Message("system", system),
					new OpenAiClient.Message("user", user)
				),
				0.0,
				120,
				timeout
			))
			.thenApply(TriviaAiService::parseValidation)
			.exceptionally(ex -> {
				TriviaMod.LOGGER.warn("Trivia AI validation failed: {}", ex.getMessage());
				return new AiValidationResult(false, "ai error");
			});
	}

	private boolean tryRateLimitOk() {
		long now = System.currentTimeMillis();
		long last = lastRequestMillis.get();
		// 1 request per ~2 seconds globally.
		if (now - last < 2000) {
			return false;
		}
		return lastRequestMillis.compareAndSet(last, now);
	}

	private static String sanitizeHint(String rawHint, String answer) {
		String hint = rawHint == null ? "" : rawHint.strip();
		if (hint.isBlank() || "HINT_UNAVAILABLE".equalsIgnoreCase(hint)) {
			return "Hint unavailable.";
		}

		// Guard: if the model leaked the answer (even loosely), refuse to show it.
		String leakedCheckHint = AnswerMatcher.normalizeLoose(hint);
		String leakedCheckAnswer = AnswerMatcher.normalizeLoose(answer);
		if (!leakedCheckAnswer.isEmpty() && leakedCheckHint.contains(leakedCheckAnswer)) {
			return "Hint unavailable.";
		}

		// Hard cap.
		if (hint.length() > 180) {
			hint = hint.substring(0, 180).strip();
		}
		return hint;
	}

	private static AiValidationResult parseValidation(String raw) {
		if (raw == null || raw.isBlank()) {
			return new AiValidationResult(false, "empty response");
		}
		try {
			JsonElement el = GSON.fromJson(raw, JsonElement.class);
			if (el == null || !el.isJsonObject()) {
				return new AiValidationResult(false, "non-json response");
			}
			JsonObject obj = el.getAsJsonObject();
			boolean ok = obj.has("isCorrect") && obj.get("isCorrect").getAsBoolean();
			String reason = obj.has("reason") ? obj.get("reason").getAsString() : "";
			return new AiValidationResult(ok, reason);
		} catch (Exception e) {
			return new AiValidationResult(false, "parse error");
		}
	}

	public record AiValidationResult(boolean isCorrect, String reason) {
	}
}
