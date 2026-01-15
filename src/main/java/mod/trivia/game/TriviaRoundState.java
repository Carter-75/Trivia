package mod.trivia.game;

import mod.trivia.questions.TriviaQuestion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TriviaRoundState {
	public TriviaQuestion activeQuestion;
	public long ticksRemaining;
	public final Map<UUID, TriviaPlayerState> playerStates = new HashMap<>();
}