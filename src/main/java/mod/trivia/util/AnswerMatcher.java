package mod.trivia.util;

import java.util.Locale;

public final class AnswerMatcher {
	private AnswerMatcher() {
	}

	public static boolean isLikelyCorrectLocal(String correctAnswerRaw, String guessRaw, boolean fuzzyEnabled, int maxEditDistance) {
		String correct = normalizeStrict(correctAnswerRaw);
		String guess = normalizeStrict(guessRaw);
		if (correct.isEmpty() || guess.isEmpty()) {
			return false;
		}
		if (correct.equals(guess)) {
			return true;
		}

		// Missing spaces or extra spaces.
		String correctNoSpace = removeWhitespace(correct);
		String guessNoSpace = removeWhitespace(guess);
		if (!correctNoSpace.isEmpty() && correctNoSpace.equals(guessNoSpace)) {
			return true;
		}

		if (!fuzzyEnabled) {
			return false;
		}

		int max = Math.max(0, maxEditDistance);
		if (max == 0) {
			return false;
		}

		// Compare a looser normalization for typos/punctuation.
		String correctLoose = normalizeLoose(correctAnswerRaw);
		String guessLoose = normalizeLoose(guessRaw);
		if (!correctLoose.isEmpty() && correctLoose.equals(guessLoose)) {
			return true;
		}

		// Levenshtein within max distance (early exit).
		int limit = Math.min(max, Math.max(correctLoose.length(), guessLoose.length()));
		return levenshteinWithin(correctLoose, guessLoose, limit);
	}

	/**
	 * A strict normalization: lowercases and collapses whitespace, but does not remove punctuation.
	 */
	public static String normalizeStrict(String s) {
		if (s == null) {
			return "";
		}
		String lowered = s.strip().toLowerCase(Locale.ROOT);
		return lowered.replaceAll("\\s+", " ");
	}

	/**
	 * A loose normalization: keep only letters/digits, remove spaces/punctuation.
	 */
	public static String normalizeLoose(String s) {
		if (s == null) {
			return "";
		}
		String lowered = s.strip().toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lowered.length());
		for (int i = 0; i < lowered.length(); i++) {
			char ch = lowered.charAt(i);
			if (Character.isLetterOrDigit(ch)) {
				out.append(ch);
			}
		}
		return out.toString();
	}

	private static String removeWhitespace(String s) {
		return s.replace(" ", "").replace("\t", "");
	}

	/**
	 * Returns true if the Levenshtein distance is <= maxDistance.
	 * Uses a bounded algorithm with early exit.
	 */
	public static boolean levenshteinWithin(String a, String b, int maxDistance) {
		if (a == null || b == null) {
			return false;
		}
		if (maxDistance < 0) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		int aLen = a.length();
		int bLen = b.length();
		int lenDiff = Math.abs(aLen - bLen);
		if (lenDiff > maxDistance) {
			return false;
		}
		if (aLen == 0 || bLen == 0) {
			return Math.max(aLen, bLen) <= maxDistance;
		}

		// Ensure a is the shorter string.
		if (aLen > bLen) {
			String tmp = a;
			a = b;
			b = tmp;
			aLen = a.length();
			bLen = b.length();
		}

		int[] prev = new int[aLen + 1];
		int[] curr = new int[aLen + 1];

		for (int i = 0; i <= aLen; i++) {
			prev[i] = i;
		}

		for (int j = 1; j <= bLen; j++) {
			char bCh = b.charAt(j - 1);
			curr[0] = j;
			int rowMin = curr[0];

			// Only compute cells within the diagonal band.
			int start = Math.max(1, j - maxDistance);
			int end = Math.min(aLen, j + maxDistance);

			// Fill outside band with large values.
			for (int i = 1; i < start; i++) {
				curr[i] = maxDistance + 1;
			}

			for (int i = start; i <= end; i++) {
				int cost = (a.charAt(i - 1) == bCh) ? 0 : 1;
				int del = prev[i] + 1;
				int ins = curr[i - 1] + 1;
				int sub = prev[i - 1] + cost;
				int val = Math.min(Math.min(del, ins), sub);
				curr[i] = val;
				rowMin = Math.min(rowMin, val);
			}

			for (int i = end + 1; i <= aLen; i++) {
				curr[i] = maxDistance + 1;
			}

			if (rowMin > maxDistance) {
				return false;
			}

			int[] swap = prev;
			prev = curr;
			curr = swap;
		}

		return prev[aLen] <= maxDistance;
	}
}
