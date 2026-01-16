package mod.trivia.client;

import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.config.TriviaConfigManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class TriviaConfigScreen extends Screen {
	private final Screen parent;
	private TriviaConfig working;

	private TextFieldWidget maxAttempts;
	private TextFieldWidget questionSeconds;
	private TextFieldWidget cooldownSeconds;
	private TextFieldWidget effectMinSeconds;
	private TextFieldWidget effectMaxSeconds;
	private TextFieldWidget effectMinPower;
	private TextFieldWidget effectMaxPower;
	private TextFieldWidget rewardCountOverride;
	private TextFieldWidget itemBlacklist;

	public TriviaConfigScreen(Screen parent) {
		super(Text.literal("Trivia Config"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.working = TriviaConfigManager.getConfig().copy();

		int x = this.width / 2 - 140;
		int y = 40;
		int w = 280;
		int h = 20;
		int gap = 24;

		this.addDrawableChild(ButtonWidget.builder(enabledLabel(this.working.enabled), btn -> {
			this.working.enabled = !this.working.enabled;
			btn.setMessage(enabledLabel(this.working.enabled));
		}).dimensions(this.width / 2 - 140, y, w, h).build());
		y += gap;

		this.addDrawableChild(ButtonWidget.builder(instructionsLabel(this.working.showAnswerInstructions), btn -> {
			this.working.showAnswerInstructions = !this.working.showAnswerInstructions;
			btn.setMessage(instructionsLabel(this.working.showAnswerInstructions));
		}).dimensions(this.width / 2 - 140, y, w, h).build());
		y += gap;

		this.addDrawableChild(ButtonWidget.builder(announceLabel(this.working.announceCorrectGuesses), btn -> {
			this.working.announceCorrectGuesses = !this.working.announceCorrectGuesses;
			btn.setMessage(announceLabel(this.working.announceCorrectGuesses));
		}).dimensions(this.width / 2 - 140, y, w, h).build());
		y += gap;

		this.addDrawableChild(ButtonWidget.builder(battleLabel(this.working.battleModeWrongGuessBroadcast), btn -> {
			this.working.battleModeWrongGuessBroadcast = !this.working.battleModeWrongGuessBroadcast;
			btn.setMessage(battleLabel(this.working.battleModeWrongGuessBroadcast));
		}).dimensions(this.width / 2 - 140, y, w, h).build());
		y += gap;

		this.addDrawableChild(ButtonWidget.builder(battleNameLabel(this.working.battleModeShowWrongGuesserName), btn -> {
			this.working.battleModeShowWrongGuesserName = !this.working.battleModeShowWrongGuesserName;
			btn.setMessage(battleNameLabel(this.working.battleModeShowWrongGuesserName));
		}).dimensions(this.width / 2 - 140, y, w, h).build());
		y += gap;

		this.maxAttempts = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Max Attempts (-1 = unlimited)"));
		this.maxAttempts.setText(Integer.toString(working.maxAttempts));
		this.addDrawableChild(this.maxAttempts);
		y += gap;

		this.questionSeconds = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Question Seconds"));
		this.questionSeconds.setText(Integer.toString(working.questionDurationSeconds));
		this.addDrawableChild(this.questionSeconds);
		y += gap;

		this.cooldownSeconds = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Cooldown Seconds"));
		this.cooldownSeconds.setText(Integer.toString(working.cooldownSeconds));
		this.addDrawableChild(this.cooldownSeconds);
		y += gap;

		this.rewardCountOverride = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Reward Count Override (-1 = random)"));
		this.rewardCountOverride.setText(Integer.toString(working.rewardCountOverride));
		this.addDrawableChild(this.rewardCountOverride);
		y += gap;

		this.effectMinSeconds = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Effect Min Seconds"));
		this.effectMinSeconds.setText(Integer.toString(working.punishEffectDurationSecondsMin));
		this.addDrawableChild(this.effectMinSeconds);
		y += gap;

		this.effectMaxSeconds = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Effect Max Seconds"));
		this.effectMaxSeconds.setText(Integer.toString(working.punishEffectDurationSecondsMax));
		this.addDrawableChild(this.effectMaxSeconds);
		y += gap;

		this.effectMinPower = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Effect Min Power (1-10)"));
		this.effectMinPower.setText(Integer.toString(working.punishEffectAmplifierMin));
		this.addDrawableChild(this.effectMinPower);
		y += gap;

		this.effectMaxPower = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Effect Max Power (1-10)"));
		this.effectMaxPower.setText(Integer.toString(working.punishEffectAmplifierMax));
		this.addDrawableChild(this.effectMaxPower);
		y += gap;

		this.itemBlacklist = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Item Blacklist (comma-separated ids)"));
		this.itemBlacklist.setText(String.join(",", working.itemBlacklist));
		this.addDrawableChild(this.itemBlacklist);
		y += gap + 10;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> {
			try {
				TriviaConfig cfg = new TriviaConfig();
				cfg.enabled = this.working.enabled;
				cfg.answerPrefix = (this.working.answerPrefix == null || this.working.answerPrefix.isBlank())
					? "."
					: this.working.answerPrefix;
				cfg.showAnswerInstructions = this.working.showAnswerInstructions;
				cfg.announceCorrectGuesses = this.working.announceCorrectGuesses;
				cfg.battleModeWrongGuessBroadcast = this.working.battleModeWrongGuessBroadcast;
				cfg.battleModeShowWrongGuesserName = this.working.battleModeShowWrongGuesserName;
				cfg.maxAttempts = parseInt(this.maxAttempts.getText(), working.maxAttempts);
				cfg.questionDurationSeconds = parseInt(this.questionSeconds.getText(), working.questionDurationSeconds);
				cfg.cooldownSeconds = parseInt(this.cooldownSeconds.getText(), working.cooldownSeconds);
				cfg.rewardCountOverride = parseInt(this.rewardCountOverride.getText(), working.rewardCountOverride);
				cfg.punishEffectDurationSecondsMin = parseInt(this.effectMinSeconds.getText(), working.punishEffectDurationSecondsMin);
				cfg.punishEffectDurationSecondsMax = parseInt(this.effectMaxSeconds.getText(), working.punishEffectDurationSecondsMax);
				cfg.punishEffectAmplifierMin = parseInt(this.effectMinPower.getText(), working.punishEffectAmplifierMin);
				cfg.punishEffectAmplifierMax = parseInt(this.effectMaxPower.getText(), working.punishEffectAmplifierMax);
				cfg.itemBlacklist = splitCsv(this.itemBlacklist.getText());
				TriviaConfigManager.saveConfig(cfg);
				this.client.setScreen(parent);
			} catch (Exception e) {
				TriviaMod.LOGGER.error("Failed to save trivia config from config screen", e);
			}
		}).dimensions(this.width / 2 - 140, y, 138, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.client.setScreen(parent))
			.dimensions(this.width / 2 + 2, y, 138, 20).build());
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private static Text enabledLabel(boolean enabled) {
		return Text.literal("Enabled: " + (enabled ? "ON" : "OFF"));
	}

	private static Text instructionsLabel(boolean show) {
		return Text.literal("Hint line (Answer with <prefix><answer>): " + (show ? "ON" : "OFF"));
	}

	private static Text announceLabel(boolean enabled) {
		return Text.literal("Announce correct guesses globally: " + (enabled ? "ON" : "OFF"));
	}

	private static Text battleLabel(boolean enabled) {
		return Text.literal("Battle mode wrong-guess broadcast: " + (enabled ? "ON" : "OFF"));
	}

	private static Text battleNameLabel(boolean enabled) {
		return Text.literal("Battle mode shows guesser name: " + (enabled ? "ON" : "OFF"));
	}

	private static List<String> splitCsv(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of("minecraft:air");
		}
		return List.of(csv.split(","))
			.stream()
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

		int helpX = 10;
		int helpY = 22;
		int line = 10;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Server-side trivia events (works in singleplayer too)."), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("A random question broadcasts after each cooldown."), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Answer in chat using: <prefix><answer> (default prefix is '.')"), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Admin commands: /trivia enable|disable|toggle|status|reload|ask|hint|announce|battle"), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Hint OFF hides the 'Answer with ...' line and removes extra hints from battle messages."), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Battle mode broadcasts wrong guesses globally (can be disabled)."), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Battle name display can be toggled with: /trivia battle name on|off"), helpX, helpY, 0xAAAAAA);
		helpY += line;
		context.drawTextWithShadow(this.textRenderer, Text.literal("Reward Count Override: -1=random, otherwise fixed (capped by item stack size)."), helpX, helpY, 0xAAAAAA);

		context.drawTextWithShadow(this.textRenderer, Text.literal("Edits apply on the server; run /trivia reload to apply immediately."), 10, this.height - 20, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
	}
}