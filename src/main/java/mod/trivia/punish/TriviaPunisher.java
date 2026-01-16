package mod.trivia.punish;

import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.util.RandomUtil;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class TriviaPunisher {
	private List<StatusEffect> effectPool = List.of();

	public void rebuildPools() {
		List<StatusEffect> effects = new ArrayList<>();
		for (StatusEffect effect : Registries.STATUS_EFFECT) {
			if (effect != null) {
				effects.add(effect);
			}
		}
		this.effectPool = List.copyOf(effects);
		TriviaMod.LOGGER.info("Trivia punishment pool: {} effects", this.effectPool.size());
	}

	public void punish(ServerPlayerEntity player, TriviaConfig cfg, RandomGenerator rng, String reason) {
		if (effectPool.isEmpty()) {
			return;
		}
		if (cfg == null) {
			return;
		}
		StatusEffect effect = RandomUtil.pick(effectPool, rng);
		RegistryEntry<StatusEffect> effectEntry;
		try {
			effectEntry = Registries.STATUS_EFFECT.getEntry(effect);
		} catch (Exception e) {
			// Fallback: if we can't resolve an entry for some reason, skip punishment.
			TriviaMod.LOGGER.warn("Trivia: could not resolve status effect entry", e);
			return;
		}
		int durationSeconds = RandomUtil.nextIntInclusive(
			rng,
			Math.max(1, cfg.punishEffectDurationSecondsMin),
			Math.max(1, cfg.punishEffectDurationSecondsMax)
		);
		int amplifierLevel = RandomUtil.nextIntInclusive(
			rng,
			Math.max(1, cfg.punishEffectAmplifierMin),
			Math.max(1, cfg.punishEffectAmplifierMax)
		);
		int amplifier = Math.max(0, amplifierLevel - 1);
		int durationTicks = Math.max(20, durationSeconds * 20);

		player.addStatusEffect(new StatusEffectInstance(effectEntry, durationTicks, amplifier));
		player.sendMessage(Text.literal("Trivia: failed (" + reason + "). You were given a random effect."), false);
	}
}