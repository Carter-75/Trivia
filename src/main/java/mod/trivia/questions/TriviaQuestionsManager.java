package mod.trivia.questions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfigManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TriviaQuestionsManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_QUESTIONS_RESOURCE = "trivia/default_questions.json";

	private List<TriviaQuestion> questions = new ArrayList<>();

	public Path getQuestionsPath() {
		return TriviaConfigManager.getConfigDir().resolve("questions.json");
	}

	public List<TriviaQuestion> getQuestions() {
		return Collections.unmodifiableList(questions);
	}

	public void reload() {
		this.questions = loadOrCreate();
	}

	private List<TriviaQuestion> loadOrCreate() {
		Path file = getQuestionsPath();
		try {
			Files.createDirectories(file.getParent());
			if (Files.notExists(file)) {
				writeDefaultResource(DEFAULT_QUESTIONS_RESOURCE, file);
			}
			try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (!root.has("questions")) {
					return List.of();
				}
				List<TriviaQuestion> loaded = GSON.fromJson(
					root.get("questions"),
					new TypeToken<List<TriviaQuestion>>() { }.getType()
				);
				if (loaded == null) {
					return List.of();
				}
				loaded.removeIf(q -> q == null || q.question == null || q.answer == null || q.question.isBlank());
				return loaded;
			}
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Failed to load questions", e);
			return List.of();
		}
	}

	private static void writeDefaultResource(String resourcePath, Path target) throws IOException {
		try (InputStream in = TriviaQuestionsManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Missing bundled resource: " + resourcePath);
			}
			Files.createDirectories(target.getParent());
			Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
		}
	}
}