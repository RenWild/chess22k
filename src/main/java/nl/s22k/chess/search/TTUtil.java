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

	public static void init() {
		keyShifts = 64 - EngineConstants.POWER_2_TT_ENTRIES + 1;
		maxEntries = (int) Util.POWER_LOOKUP[EngineConstants.POWER_2_TT_ENTRIES - 1];

		alwaysReplaceKeys = new int[maxEntries];
		alwaysReplaceValues = new long[maxEntries];
		depthReplaceKeys = new int[maxEntries];
		depthReplaceValues = new long[maxEntries];
		usageCounter = 0;

		isInitialized = true;
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

	public static void setBestMoveInStatistics(ChessBoard chessBoard, int depth, ScoreType scoreType) {
		long value = getTTValue(chessBoard.zobristKey);
		if (value == 0) {
			throw new RuntimeException("No best-move found!!");
		}

		int move = getMove(value);
		List<Integer> moves = new ArrayList<Integer>();
		moves.add(move);
		TreeMove bestMove = new TreeMove(move, getScore(value, 0), scoreType);

		chessBoard.doMove(move);

		value = getTTValue(chessBoard.zobristKey);

		int ply = 0;
		while (value != 0 && TTUtil.getFlag(value) == TTUtil.FLAG_EXACT && depth >= 0) {
			ply++;
			depth--;
			move = getMove(value);
			moves.add(move);
			bestMove.appendMove(new TreeMove(move, getScore(value, ply), ScoreType.EXACT));
			chessBoard.doMove(move);
			value = getTTValue(chessBoard.zobristKey);
		}
		for (int i = moves.size() - 1; i >= 0; i--) {
			chessBoard.undoMove(moves.get(i));
		}

		Statistics.bestMove = bestMove;
	}

	public static void addValue(final long zobristKey, int score, final int ply, final int depth, final int flag, final int cleanMove) {

		if (EngineConstants.TEST_VALUES) {
			if (depth < 1) {
				System.out.println("Cannot add depth < 1 to TT");
			}
			if (cleanMove == 0) {
				System.out.println("Adding empty move to TT");
			}
			if (score > Util.SHORT_MAX) {
				System.out.println("Adding score to TT > MAX");
			}
			if (score < Util.SHORT_MIN) {
				System.out.println("Adding score to TT < MIN");
			}
			if (MoveUtil.getCleanMove(cleanMove) != cleanMove) {
				System.out.println("Adding non-clean move to TT");
			}
		}

		// correct mate-score
		if (score > EvalConstants.SCORE_MATE_BOUND) {
			score += ply;
		} else if (score < -EvalConstants.SCORE_MATE_BOUND) {
			score -= ply;
		}

		if (EngineConstants.TEST_VALUES) {
			if (score > Util.SHORT_MAX) {
				System.out.println("Adding score to tt > MAX: " + score);
			} else if (score < Util.SHORT_MIN) {
				System.out.println("Adding score to tt < MIN: " + score);
			}
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

		if (EngineConstants.TEST_VALUES) {
			if (score > Util.SHORT_MAX) {
				System.out.println("Retrieving score from tt > MAX");
			} else if (score < Util.SHORT_MIN) {
				System.out.println("Retrieving score from tt < MIN");
			}
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
		if (EngineConstants.TEST_VALUES) {
			if (cleanMove != MoveUtil.getCleanMove((int) cleanMove)) {
				System.out.println("Adding non clean move to tt");
			}
			if (score > Util.SHORT_MAX) {
				System.out.println("Adding score to TT > MAX " + score);
			}
			if (score < Util.SHORT_MIN) {
				System.out.println("Adding score to TT < MIN " + score);
			}
			if (depth > 255) {
				System.out.println("Adding depth to TT > MAX " + depth);
			}
		}
		return score << SCORE | halfMoveCounter << HALF_MOVE_COUNTER | cleanMove << MOVE | flag << FLAG | depth;
	}

	public static String toString(long ttValue) {
		return "score=" + TTUtil.getScore(ttValue, 0) + " " + new MoveWrapper(getMove(ttValue)) + " depth=" + TTUtil.getDepth(ttValue) + " flag="
				+ TTUtil.getFlag(ttValue);
	}

}
