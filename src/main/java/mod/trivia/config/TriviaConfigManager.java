package mod.trivia.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
			try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				TriviaConfig loaded = GSON.fromJson(reader, TriviaConfig.class);
				return loaded != null ? loaded : new TriviaConfig();
			}
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