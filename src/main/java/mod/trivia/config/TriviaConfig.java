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

	public int punishEffectDurationSecondsMin = 10;
	public int punishEffectDurationSecondsMax = 600;
	public int punishEffectAmplifierMin = 1;
	public int punishEffectAmplifierMax = 10;

	public List<String> itemBlacklist = new ArrayList<>(List.of("minecraft:air"));
}