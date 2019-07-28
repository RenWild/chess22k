package nl.s22k.chess.eval;

import java.util.Arrays;

import nl.s22k.chess.Assert;
import nl.s22k.chess.ChessConstants;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.Util;
import nl.s22k.chess.engine.EngineConstants;

public class EvalCache {

	private static final int POWER_2_TABLE_SHIFTS = 64 - EngineConstants.POWER_2_EVAL_ENTRIES;

	// keys, scores
	private static final long[] keys = new long[(1 << EngineConstants.POWER_2_EVAL_ENTRIES) * 2];

	public static void clearValues() {
		Arrays.fill(keys, 0);
	}

	public static int getScore(final long key) {
		final int index = getIndex(key);
		final long storedKey = keys[index];
		final long score = keys[index + 1];

		if ((storedKey ^ score) == key) {
			if (Statistics.ENABLED) {
				Statistics.evalCacheHits++;
			}
			return (int) score;
		}

		if (Statistics.ENABLED) {
			Statistics.evalCacheMisses++;
		}
		return ChessConstants.CACHE_MISS;
	}

	public static void addValue(final long key, final int score) {
		if (!EngineConstants.ENABLE_EVAL_CACHE) {
			return;
		}
		if (EngineConstants.ASSERT) {
			Assert.isTrue(score <= Util.SHORT_MAX);
			Assert.isTrue(score >= Util.SHORT_MIN);
		}

		final int index = getIndex(key);
		keys[index] = key ^ score;
		keys[index + 1] = score;
	}

	private static int getIndex(final long key) {
		return (int) (key >>> POWER_2_TABLE_SHIFTS) << 1;
	}

	public static int getUsage() {
		return Util.getUsagePercentage(keys);
	}

}
