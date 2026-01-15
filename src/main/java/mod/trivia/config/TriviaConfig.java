package mod.trivia.config;

import java.util.ArrayList;
import java.util.List;

public final class TriviaConfig {
	public boolean enabled = true;

	public int questionDurationSeconds = 60;
	public int cooldownSeconds = 540;

	/**
	 * Max attempts per player per round.
	 * Use -1 for unlimited attempts until the time limit expires.
	 */
	public int maxAttempts = 3;
	public String answerPrefix = ".";
	public boolean showAnswerInstructions = true;

	/**
	 * When enabled, wrong guesses are broadcast globally ("<player>'s guess of <guess> was wrong").
	 * Note: this broadcast is also suppressed when showAnswerInstructions is OFF.
	 */
	public boolean battleModeWrongGuessBroadcast = true;

	/**
	 * Override the number of items rewarded on a correct guess.
	 * Use -1 for the normal random behavior (1..max stack size).
	 */
	public int rewardCountOverride = -1;

	public int punishEffectDurationSecondsMin = 10;
	public int punishEffectDurationSecondsMax = 600;
	public int punishEffectAmplifierMin = 1;
	public int punishEffectAmplifierMax = 10;

	public List<String> itemBlacklist = new ArrayList<>(List.of("minecraft:air"));
}