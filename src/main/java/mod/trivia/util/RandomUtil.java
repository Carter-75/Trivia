package mod.trivia.util;

import java.util.List;
import java.util.random.RandomGenerator;

public final class RandomUtil {
	private RandomUtil() {
	}

	public static <T> T pick(List<T> list, RandomGenerator rng) {
		return list.get(rng.nextInt(list.size()));
	}

	public static int nextIntInclusive(RandomGenerator rng, int min, int max) {
		int a = Math.min(min, max);
		int b = Math.max(min, max);
		if (a == b) {
			return a;
		}
		return a + rng.nextInt(b - a + 1);
	}
}