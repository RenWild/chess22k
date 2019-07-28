package nl.s22k.chess.eval;

import java.util.Arrays;

import nl.s22k.chess.Assert;
import nl.s22k.chess.ChessConstants;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.Util;
import nl.s22k.chess.engine.EngineConstants;

public class MaterialCache {

	private static final int POWER_2_TABLE_SHIFTS = 64 - EngineConstants.POWER_2_MATERIAL_ENTRIES;

	// keys, scores
	private static final int[] keys = new int[(1 << EngineConstants.POWER_2_MATERIAL_ENTRIES) * 2];

	public static void clearValues() {
		Arrays.fill(keys, 0);
	}

	public static int getScore(final int materialKey) {

		if (!EngineConstants.ENABLE_MATERIAL_CACHE) {
			return ChessConstants.CACHE_MISS;
		}

		final int index = getIndex(materialKey);
		final int xorKey = keys[index];
		final int score = keys[index + 1];

		if ((xorKey ^ score) == materialKey) {
			if (Statistics.ENABLED) {
				Statistics.materialCacheHits++;
			}
			return score;
		}

		if (Statistics.ENABLED) {
			Statistics.materialCacheMisses++;
		}
		return ChessConstants.CACHE_MISS;
	}

	public static void addValue(final int materialKey, final int score) {

		if (EngineConstants.ASSERT) {
			Assert.isTrue(score <= Util.SHORT_MAX);
			Assert.isTrue(score >= Util.SHORT_MIN);
		}

		final int index = getIndex(materialKey);
		keys[index] = materialKey ^ score;
		keys[index + 1] = score;
	}

	private static int getIndex(final int materialKey) {
		return ((materialKey * 836519301) >>> POWER_2_TABLE_SHIFTS) << 1;
	}

	public static int getUsage() {
		return Util.getUsagePercentage(keys);
	}

}
