package mod.trivia.reward;

import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.util.RandomUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class TriviaRewarder {
	public record RewardResult(int count, String itemName) {
	}

	private List<Item> rewardPool = List.of();
	private int rewardCountOverride = -1;

	public void rebuildPools(TriviaConfig cfg) {
		this.rewardCountOverride = cfg != null ? cfg.rewardCountOverride : -1;

		Set<Identifier> blacklist = new HashSet<>();
		if (cfg != null && cfg.itemBlacklist != null) {
			for (String raw : cfg.itemBlacklist) {
				try {
					blacklist.add(Identifier.of(raw));
				} catch (Exception ignored) {
				}
			}
		}

		List<Item> items = new ArrayList<>();
		for (Item item : Registries.ITEM) {
			if (item == null || item == Items.AIR) {
				continue;
			}
			Identifier id = Registries.ITEM.getId(item);
			if (id != null && blacklist.contains(id)) {
				continue;
			}
			items.add(item);
		}
		this.rewardPool = List.copyOf(items);
		TriviaMod.LOGGER.info("Trivia reward pool: {} items (blacklist: {})", this.rewardPool.size(), blacklist.size());
	}

	public RewardResult reward(ServerPlayerEntity player, RandomGenerator rng) {
		if (rewardPool.isEmpty()) {
			return null;
		}
		Item item = RandomUtil.pick(rewardPool, rng);
		ItemStack preview = new ItemStack(item);
		int maxStack = preview.getMaxCount();
		if (maxStack < 1) {
			maxStack = 1;
		}
		int count;
		int override = this.rewardCountOverride;
		if (override <= 0) {
			count = rng.nextInt(maxStack) + 1;
		} else {
			count = Math.min(override, maxStack);
		}
		ItemStack stack = new ItemStack(item, count);
		player.getInventory().insertStack(stack);
		if (!stack.isEmpty()) {
			player.dropItem(stack, false);
		}
		String nameText = preview.getName().getString();
		return new RewardResult(count, nameText);
	}
}