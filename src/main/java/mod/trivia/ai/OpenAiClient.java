package mod.trivia.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class OpenAiClient {
	private static final Gson GSON = new GsonBuilder().create();
	private static final URI CHAT_COMPLETIONS_URI = URI.create("https://api.openai.com/v1/chat/completions");

	private final HttpClient http;

	OpenAiClient() {
		this.http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	}

	CompletableFuture<String> chatCompletion(
		String apiKey,
		String model,
		List<Message> messages,
		double temperature,
		int maxTokens,
		Duration timeout
	) {
		Objects.requireNonNull(messages, "messages");

		String key = apiKey == null ? "" : apiKey.trim();
		if (key.isEmpty()) {
			return CompletableFuture.failedFuture(new IllegalStateException("OpenAI API key is missing"));
		}
		String m = (model == null || model.isBlank()) ? "gpt-4o-mini" : model.trim();

		JsonObject root = new JsonObject();
		root.addProperty("model", m);
		root.addProperty("temperature", temperature);
		root.addProperty("max_tokens", Math.max(1, maxTokens));

		JsonArray msgs = new JsonArray();
		for (Message msg : messages) {
			if (msg == null) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("role", msg.role());
			o.addProperty("content", msg.content());
			msgs.add(o);
		}
		root.add("messages", msgs);

		HttpRequest req = HttpRequest.newBuilder()
			.uri(CHAT_COMPLETIONS_URI)
			.timeout(timeout)
			.header("Authorization", "Bearer " + key)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root)))
			.build();

		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
			.thenApply(resp -> {
				int code = resp.statusCode();
				String body = resp.body();
				if (code < 200 || code >= 300) {
					throw new RuntimeException("OpenAI request failed (" + code + ")");
				}
				return extractFirstContent(body);
			});
	}

	private static String extractFirstContent(String json) {
		if (json == null || json.isBlank()) {
			return "";
		}
		JsonElement rootEl = GSON.fromJson(json, JsonElement.class);
		if (rootEl == null || !rootEl.isJsonObject()) {
			return "";
		}
		JsonObject root = rootEl.getAsJsonObject();
		JsonArray choices = root.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty()) {
			return "";
		}
		JsonObject choice0 = choices.get(0).getAsJsonObject();
		JsonObject message = choice0.getAsJsonObject("message");
		if (message == null) {
			return "";
		}
		JsonElement content = message.get("content");
		return content == null ? "" : content.getAsString();
	}

	record Message(String role, String content) {
		Message {
			role = (role == null || role.isBlank()) ? "user" : role;
			content = content == null ? "" : content;
		}
	}
}
