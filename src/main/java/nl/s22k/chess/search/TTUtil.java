package nl.s22k.chess.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.s22k.chess.ChessBoard;
import nl.s22k.chess.ChessConstants.ScoreType;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.Util;
import nl.s22k.chess.engine.EngineConstants;
import nl.s22k.chess.eval.EvalConstants;
import nl.s22k.chess.move.MoveUtil;
import nl.s22k.chess.move.MoveWrapper;
import nl.s22k.chess.move.TreeMove;

public class TTUtil {

	private static int keyShifts;
	public static int maxEntries;

	private static int[] alwaysReplaceKeys;
	private static long[] alwaysReplaceValues;
	private static int[] depthReplaceKeys;
	private static long[] depthReplaceValues;

	public static long usageCounter;

	public static final int FLAG_EXACT = 0;
	public static final int FLAG_UPPER = 1;
	public static final int FLAG_LOWER = 2;

	public static long halfMoveCounter = 0;

	// ///////////////////// DEPTH //8 bits
	private static final int FLAG = 8; // 2
	private static final int MOVE = 10; // 22
	private static final int HALF_MOVE_COUNTER = 32; // 16
	private static final int SCORE = 48; // 16

	public static boolean isInitialized = false;

	public static void init(final boolean force) {
		if (force || !isInitialized) {
			keyShifts = 64 - EngineConstants.POWER_2_TT_ENTRIES + 1;
			maxEntries = (int) Util.POWER_LOOKUP[EngineConstants.POWER_2_TT_ENTRIES - 1];

			alwaysReplaceKeys = new int[maxEntries];
			alwaysReplaceValues = new long[maxEntries];
			depthReplaceKeys = new int[maxEntries];
			depthReplaceValues = new long[maxEntries];
			usageCounter = 0;

			isInitialized = true;
		}
	}

	public static void clearValues() {
		if (!isInitialized) {
			return;
		}
		Arrays.fill(alwaysReplaceKeys, 0);
		Arrays.fill(alwaysReplaceValues, 0);
		Arrays.fill(depthReplaceKeys, 0);
		Arrays.fill(depthReplaceValues, 0);
		usageCounter = 0;
	}

	public static long getTTValue(final long zkKey) {

		final int index = getZobristIndex(zkKey);

		if (alwaysReplaceKeys[index] == (int) zkKey) {
			if (Statistics.ENABLED) {
				Statistics.ttHits++;
			}

			if (depthReplaceKeys[index] == (int) zkKey && getDepth(depthReplaceValues[index]) > getDepth(alwaysReplaceValues[index])) {
				return depthReplaceValues[index];
			}

			return alwaysReplaceValues[index];
		}

		if (depthReplaceKeys[index] == (int) zkKey) {
			if (Statistics.ENABLED) {
				Statistics.ttHits++;
			}
			return depthReplaceValues[index];
		}

		if (Statistics.ENABLED) {
			Statistics.ttMisses++;
		}

		return 0;
	}

	private static int getZobristIndex(final long zobristKey) {
		// TODO optimal distribution??
		if (keyShifts == 64) {
			return 0;
		}
		return (int) (zobristKey >>> keyShifts);
	}

	public static void setBestMoveInStatistics(ChessBoard chessBoard, ScoreType scoreType) {
		if (NegamaxUtil.stop) {
			return;
		}
		long value = getTTValue(chessBoard.zobristKey);
		if (value == 0) {
			throw new RuntimeException("No best-move found!!");
		}

		int move = getMove(value);
		List<Integer> moves = new ArrayList<Integer>(8);
		moves.add(move);
		TreeMove bestMove = new TreeMove(move, getScore(value, 0), scoreType);
		chessBoard.doMove(move);

		for (int i = 0; i < 8; i++) {
			value = getTTValue(chessBoard.zobristKey);
			if (value == 0) {
				break;
			}
			move = getMove(value);
			moves.add(move);
			bestMove.appendMove(new TreeMove(move));
			chessBoard.doMove(move);
		}
		for (int i = moves.size() - 1; i >= 0; i--) {
			chessBoard.undoMove(moves.get(i));
		}

		Statistics.bestMove = bestMove;
	}

	public static void addValue(final long zobristKey, int score, final int ply, final int depth, final int flag, final int cleanMove) {

		if (NegamaxUtil.stop) {
			return;
		}

		if (EngineConstants.ASSERT) {
			assert depth >= 1 : "Cannot add depth < 1 to TT";
			assert cleanMove != 0 : "Adding empty move to TT";
			assert score >= Util.SHORT_MIN && score <= Util.SHORT_MAX : "Adding incorrect score to TT: " + score;
			assert MoveUtil.getCleanMove(cleanMove) == cleanMove : "Adding non-clean move to TT";
			assert MoveUtil.getSourcePieceIndex(cleanMove) != 0 : "Adding move with empty source-index to T";
		}

		// correct mate-score
		if (score > EvalConstants.SCORE_MATE_BOUND) {
			score += ply;
		} else if (score < -EvalConstants.SCORE_MATE_BOUND) {
			score -= ply;
		}

		if (EngineConstants.ASSERT) {
			assert score >= Util.SHORT_MIN && score <= Util.SHORT_MAX : "Adding incorrect score to TT: " + score;
		}

		final int index = getZobristIndex(zobristKey);
		final long value = createValue(score, cleanMove, flag, depth);

		if (Statistics.ENABLED) {
			if (alwaysReplaceKeys[index] == 0) {
				usageCounter++;
			}
		}

		// TODO do not store if already stored in depth-TT?
		alwaysReplaceKeys[index] = (int) zobristKey;
		alwaysReplaceValues[index] = value;

		if (depth > getDepth(depthReplaceValues[index]) || halfMoveCounter != getHalfMoveCounter(depthReplaceValues[index])) {
			if (Statistics.ENABLED) {
				if (depthReplaceKeys[index] == 0) {
					usageCounter++;
				}
			}
			depthReplaceKeys[index] = (int) zobristKey;
			depthReplaceValues[index] = value;
		}
	}

	public static int getScore(final long value, final int ply) {
		int score = (int) (value >> SCORE);

		// correct mate-score
		if (score > EvalConstants.SCORE_MATE_BOUND) {
			score -= ply;
		} else if (score < -EvalConstants.SCORE_MATE_BOUND) {
			score += ply;
		}

		if (EngineConstants.ASSERT) {
			assert score >= Util.SHORT_MIN && score <= Util.SHORT_MAX : "Retrieving incorrect score from TT: " + score;
		}

		return score;
	}

	public static int getHalfMoveCounter(final long value) {
		return (int) (value >>> HALF_MOVE_COUNTER & 0xffff);
	}

	public static int getDepth(final long value) {
		return (int) (value & 0xff);
	}

	public static int getFlag(final long value) {
		return (int) (value >>> FLAG & 3);
	}

	public static int getMove(final long value) {
		return (int) (value >>> MOVE & 0x3fffff);
	}

	// SCORE,HALF_MOVE_COUNTER,MOVE,FLAG,DEPTH
	public static long createValue(final long score, final long cleanMove, final long flag, final long depth) {
		if (EngineConstants.ASSERT) {
			assert cleanMove == MoveUtil.getCleanMove((int) cleanMove) : "Adding non clean move to tt";
			assert score >= Util.SHORT_MIN && score <= Util.SHORT_MAX : "Adding incorrect score to TT: " + score;
			assert depth <= 255 : "Adding depth to TT > MAX " + depth;
		}
		return score << SCORE | halfMoveCounter << HALF_MOVE_COUNTER | cleanMove << MOVE | flag << FLAG | depth;
	}

	public static String toString(long ttValue) {
		return "score=" + TTUtil.getScore(ttValue, 0) + " " + new MoveWrapper(getMove(ttValue)) + " depth=" + TTUtil.getDepth(ttValue) + " flag="
				+ TTUtil.getFlag(ttValue);
	}

}
