package mod.trivia.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mod.trivia.TriviaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TriviaConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_SETTINGS_RESOURCE = "trivia/default_settings.json";

	private static TriviaConfig config;

	private TriviaConfigManager() {
	}

	public static void loadAll() {
		config = loadOrCreateConfig();
	}

	public static TriviaConfig getConfig() {
		if (config == null) {
			config = new TriviaConfig();
		}
		return config;
	}

	public static Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(TriviaMod.MOD_ID);
	}

	public static Path getSettingsPath() {
		return getConfigDir().resolve("settings.json");
	}

	private static TriviaConfig loadOrCreateConfig() {
		Path dir = getConfigDir();
		Path file = getSettingsPath();
		try {
			Files.createDirectories(dir);
			if (Files.notExists(file)) {
				writeDefaultResource(DEFAULT_SETTINGS_RESOURCE, file);
			}
			String json = Files.readString(file, StandardCharsets.UTF_8);
			TriviaConfig loaded = GSON.fromJson(json, TriviaConfig.class);
			TriviaConfig cfg = loaded != null ? loaded : new TriviaConfig();

			// Migrate older configs: if the key is missing, default to showing the instruction line.
			boolean changed = false;
			try {
				JsonElement root = JsonParser.parseString(json);
				if (root != null && root.isJsonObject()) {
					JsonObject obj = root.getAsJsonObject();
					if (!obj.has("showAnswerInstructions")) {
						cfg.showAnswerInstructions = true;
						changed = true;
					}
					if (!obj.has("battleModeWrongGuessBroadcast")) {
						cfg.battleModeWrongGuessBroadcast = true;
						changed = true;
					}
					if (!obj.has("rewardCountOverride")) {
						cfg.rewardCountOverride = -1;
						changed = true;
					}
					if (!obj.has("battleModeShowWrongGuesserName")) {
						cfg.battleModeShowWrongGuesserName = true;
						changed = true;
					}
				}
			} catch (Exception ignored) {
				// If the JSON isn't parseable here, the outer try/catch will handle it.
			}

			if (changed) {
				Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
			}

			return cfg;
		} catch (Exception e) {
			TriviaMod.LOGGER.error("Failed to load config; using defaults", e);
			return new TriviaConfig();
		}
	}

	public static void saveConfig(TriviaConfig cfg) throws IOException {
		Path file = getSettingsPath();
		Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
		config = cfg;
	}

	private static void writeDefaultResource(String resourcePath, Path target) throws IOException {
		try (InputStream in = TriviaConfigManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Missing bundled resource: " + resourcePath);
			}
			Files.createDirectories(target.getParent());
			Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
		}
	}
}