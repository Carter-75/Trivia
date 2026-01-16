package mod.trivia.game;

public final class TriviaPlayerState {
	public int attemptsUsed;
	public boolean guessedOnce;
	public boolean solved;
	public boolean failed;
	public int rewardCount;
	public String rewardItemName;

	public boolean aiValidationPending;
	public long aiValidationRoundId;
	public String pendingGuessDisplay;
	public String pendingGuessNormalized;

	public long lastHintMillis;
	public long lastHintRoundId;
}