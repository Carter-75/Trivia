package mod.trivia.reward;

import mod.trivia.TriviaMod;
import mod.trivia.config.TriviaConfig;
import mod.trivia.util.RandomUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class TriviaRewarder {
	private List<Item> rewardPool = List.of();

	public void rebuildPools(TriviaConfig cfg) {
		Set<Identifier> blacklist = new HashSet<>();
		for (String raw : cfg.itemBlacklist) {
			try {
				blacklist.add(Identifier.of(raw));
			} catch (Exception ignored) {
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

	public void reward(ServerPlayerEntity player, RandomGenerator rng) {
		if (rewardPool.isEmpty()) {
			player.sendMessage(Text.literal("Trivia: reward pool is empty."), false);
			return;
		}
		Item item = RandomUtil.pick(rewardPool, rng);
		ItemStack preview = new ItemStack(item);
		int maxStack = preview.getMaxCount();
		if (maxStack < 1) {
			maxStack = 1;
		}
		int count = rng.nextInt(maxStack) + 1;
		ItemStack stack = new ItemStack(item, count);
		player.getInventory().insertStack(stack);
		int droppedCount = 0;
		if (!stack.isEmpty()) {
			droppedCount = stack.getCount();
			player.dropItem(stack, false);
		}
		Identifier id = Registries.ITEM.getId(item);
		String idText = (id != null) ? id.toString() : "unknown";
		String nameText = preview.getName().getString();
		String dropSuffix = (droppedCount > 0) ? (" (dropped " + droppedCount + " due to full inventory)") : "";
		player.sendMessage(Text.literal("Trivia: reward: " + count + "x " + idText + " (" + nameText + ")" + dropSuffix), false);
	}
}