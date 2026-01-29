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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TriviaMod implements ModInitializer {
	public static final String MOD_ID = "trivia";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final TriviaGame GAME = new TriviaGame();
	private static final Map<UUID, String> CHAT_MESSAGE_GUARD = new HashMap<>();

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
			if (raw != null && !raw.isEmpty()) {
				String prefix = TriviaConfigManager.getConfig().answerPrefix;
				if (prefix == null || prefix.isBlank()) {
					prefix = ".";
				}
				if (raw.startsWith(prefix)) {
					CHAT_MESSAGE_GUARD.put(player.getUuid(), raw);
				}
			}
			return !handleChatAttempt(player, raw);
		});

		// Fallback handler for environments where ALLOW_CHAT_MESSAGE does not fire.
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			if (!(sender instanceof ServerPlayerEntity player)) {
				return;
			}
			String raw = message.getContent().getString();
			String guarded = CHAT_MESSAGE_GUARD.get(player.getUuid());
			if (guarded != null && guarded.equals(raw)) {
				CHAT_MESSAGE_GUARD.remove(player.getUuid());
				return;
			}
			handleChatAttempt(player, raw);
		});
	}

	private static boolean handleChatAttempt(ServerPlayerEntity player, String raw) {
		if (player == null || raw == null || raw.isEmpty()) {
			return false;
		}
		if (!TriviaConfigManager.getConfig().enabled) {
			return false;
		}
		String prefix = TriviaConfigManager.getConfig().answerPrefix;
		if (prefix == null || prefix.isBlank()) {
			prefix = ".";
		}
		if (!raw.startsWith(prefix)) {
			return false;
		}
		TriviaMod.LOGGER.info("Trivia chat attempt from {}: {}", player.getName().getString(), raw);
		boolean handled = GAME.onPlayerAttempt(player, raw);
		TriviaMod.LOGGER.info("Trivia chat attempt handled={}, activeRound={}", handled, GAME.getActiveAnswerForAdmin() != null);
		if (!handled) {
			player.sendMessage(Text.literal("Trivia: no active question right now."), false);
		}
		return handled;
	}

	public static TriviaGame game() {
		return GAME;
	}
}