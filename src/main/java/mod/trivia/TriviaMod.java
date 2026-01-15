package mod.trivia;

import mod.trivia.command.TriviaCommands;
import mod.trivia.config.TriviaConfigManager;
import mod.trivia.game.TriviaGame;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TriviaMod implements ModInitializer {
	public static final String MOD_ID = "trivia";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final TriviaGame GAME = new TriviaGame();

	@Override
	public void onInitialize() {
		TriviaConfigManager.loadAll();
		GAME.reloadFromDisk();

		TriviaCommands.register(GAME);

		ServerTickEvents.END_SERVER_TICK.register(server -> GAME.onServerTick(server));

		// Intercept chat attempts. Messages starting with '.' are treated as answers during an active round.
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!(sender instanceof ServerPlayerEntity player)) {
				return true;
			}
			if (!TriviaConfigManager.getConfig().enabled) {
				return true;
			}
			String raw = message.getContent().getString();
			if (raw == null || raw.isEmpty()) {
				return true;
			}
			String prefix = TriviaConfigManager.getConfig().answerPrefix;
			if (prefix == null || prefix.isBlank()) {
				prefix = ".";
			}
			if (!raw.startsWith(prefix)) {
				return true;
			}
			boolean handled = GAME.onPlayerAttempt(player, raw);
			if (handled) {
				return false;
			}
			player.sendMessage(Text.literal("Trivia: no active question right now."), false);
			return false;
		});
	}

	public static TriviaGame game() {
		return GAME;
	}
}