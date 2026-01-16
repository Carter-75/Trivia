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
	 * When enabled, broadcasts a global message when someone guesses correctly ("<player> guessed correctly!").
	 * Private reward messages are unaffected.
	 */
	public boolean announceCorrectGuesses = true;

	/**
	 * When enabled, wrong guesses are broadcast globally ("<player>'s guess of <guess> was wrong").
	 * Note: when showAnswerInstructions is OFF, the broadcast remains but the extra hint suffix is omitted.
	 */
	public boolean battleModeWrongGuessBroadcast = true;

	/**
	 * When battleModeWrongGuessBroadcast is enabled, controls whether the broadcast includes the player's name.
	 */
	public boolean battleModeShowWrongGuesserName = true;

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

	public TriviaConfig copy() {
		TriviaConfig c = new TriviaConfig();
		c.enabled = this.enabled;
		c.questionDurationSeconds = this.questionDurationSeconds;
		c.cooldownSeconds = this.cooldownSeconds;
		c.maxAttempts = this.maxAttempts;
		c.answerPrefix = this.answerPrefix;
		c.showAnswerInstructions = this.showAnswerInstructions;
		c.announceCorrectGuesses = this.announceCorrectGuesses;
		c.battleModeWrongGuessBroadcast = this.battleModeWrongGuessBroadcast;
		c.battleModeShowWrongGuesserName = this.battleModeShowWrongGuesserName;
		c.rewardCountOverride = this.rewardCountOverride;
		c.punishEffectDurationSecondsMin = this.punishEffectDurationSecondsMin;
		c.punishEffectDurationSecondsMax = this.punishEffectDurationSecondsMax;
		c.punishEffectAmplifierMin = this.punishEffectAmplifierMin;
		c.punishEffectAmplifierMax = this.punishEffectAmplifierMax;
		c.itemBlacklist = (this.itemBlacklist == null) ? new ArrayList<>() : new ArrayList<>(this.itemBlacklist);
		return c;
	}
}