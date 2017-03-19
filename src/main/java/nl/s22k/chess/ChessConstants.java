package nl.s22k.chess;

public class ChessConstants {

	public static final int MAX_PLIES = 64;
	public static final int MAX_DEPTH = 512;

	public static final String FEN_START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	public static final int EMPTY = 0;
	public static final int PAWN = 1;
	public static final int NIGHT = 2;
	public static final int BISHOP = 3;
	public static final int ROOK = 4;
	public static final int QUEEN = 5;
	public static final int KING = 6;

	public static final int WHITE = 0;
	public static final int BLACK = 1;

	public static final int[] COLOR_FACTOR = new int[] { 1, -1 };

	public static final long[] MASKS_FILE = new long[] { 0x101010101010101L, 0x202020202020202L, 0x404040404040404L, 0x808080808080808L, 0x1010101010101010L,
			0x2020202020202020L, 0x4040404040404040L, 0x8080808080808080L };
	public static final long[] MASKS_RANK = new long[] { 0xffL, 0xff00L, 0xff0000L, 0xff000000L, 0xff00000000L, 0xff0000000000L, 0xff000000000000L,
			0xff00000000000000L };
	public static final long[] MASK_ADJACENT_FILE = new long[] { 0x202020202020202L, 0x505050505050505L, 0xa0a0a0a0a0a0a0aL, 0x1414141414141414L,
			0x2828282828282828L, 0x5050505050505050L, 0xa0a0a0a0a0a0a0a0L, 0x4040404040404040L };

	public static final long[] MASK_ADJACENT_FILE_UP = new long[64];
	public static final long[] MASK_ADJACENT_FILE_DOWN = new long[64];
	static {
		for (int i = 0; i < 64; i++) {
			long adjacentFile = MASK_ADJACENT_FILE[i % 8];
			while (adjacentFile != 0) {
				if (Long.numberOfTrailingZeros(adjacentFile) > i + 1) {
					MASK_ADJACENT_FILE_UP[i] |= Util.POWER_LOOKUP[Long.numberOfTrailingZeros(adjacentFile)];
				} else if (Long.numberOfTrailingZeros(adjacentFile) < i - 1) {
					MASK_ADJACENT_FILE_DOWN[i] |= Util.POWER_LOOKUP[Long.numberOfTrailingZeros(adjacentFile)];
				}
				adjacentFile &= adjacentFile - 1;
			}
		}
	}

	public static final String FEN_WHITE_PIECES[] = { "1", "P", "N", "B", "R", "Q", "K" };
	public static final String FEN_BLACK_PIECES[] = { "1", "p", "n", "b", "r", "q", "k" };

	public static final long MASK_NOT_A_FILE = 0x7f7f7f7f7f7f7f7fL;
	public static final long MASK_NOT_H_FILE = 0xfefefefefefefefeL;

	public static final long MASK_RANK_4567 = 0xffffffff000000L;
	public static final long MASK_RANK_2345 = 0xffffffff00L;

	public static final long MASK_A1_B1 = 0xc0;
	public static final long MASK_A8_B8 = 0xc000000000000000L;
	public static final long MASK_G1_H1 = 0x3;
	public static final long MASK_G8_H8 = 0x300000000000000L;

	public static final long[] PROMOTION_RANK = { 0xff000000000000L, 0xff00 };

	public static final long[][] ROOK_IN_BETWEEN = new long[64][64];
	static {
		int i;

		// fill from->to where to > from
		for (int from = 0; from < 64; from++) {
			for (int to = from + 1; to < 64; to++) {

				// horizontal
				if (from / 8 == to / 8) {
					i = to - 1;
					while (i > from) {
						ROOK_IN_BETWEEN[from][to] |= Util.POWER_LOOKUP[i];
						i--;
					}
				}

				// vertical
				if (from % 8 == to % 8) {
					i = to - 8;
					while (i > from) {
						ROOK_IN_BETWEEN[from][to] |= Util.POWER_LOOKUP[i];
						i -= 8;
					}
				}
			}
		}

		// fill from->to where to < from
		for (int from = 0; from < 64; from++) {
			for (int to = 0; to < from; to++) {
				ROOK_IN_BETWEEN[from][to] = ROOK_IN_BETWEEN[to][from];
			}
		}
	}

	public static final long[][] BISHOP_IN_BETWEEN = new long[64][64];
	static {
		int i;

		// fill from->to where to > from
		for (int from = 0; from < 64; from++) {
			for (int to = from + 1; to < 64; to++) {

				// diagonal \
				if ((to - from) % 9 == 0 && to % 8 > from % 8) {
					i = to - 9;
					while (i > from) {
						BISHOP_IN_BETWEEN[from][to] |= Util.POWER_LOOKUP[i];
						i -= 9;
					}
				}

				// diagonal /
				if ((to - from) % 7 == 0 && to % 8 < from % 8) {
					i = to - 7;
					while (i > from) {
						BISHOP_IN_BETWEEN[from][to] |= Util.POWER_LOOKUP[i];
						i -= 7;
					}
				}
			}
		}

		// fill from->to where to < from
		for (int from = 0; from < 64; from++) {
			for (int to = 0; to < from; to++) {
				BISHOP_IN_BETWEEN[from][to] = BISHOP_IN_BETWEEN[to][from];
			}
		}
	}

	public enum ScoreType {
		EXACT(" "), ALPHA(" upperbound "), BETA(" lowerbound ");

		private ScoreType(String uci) {
			this.uci = uci;
		}

		private String uci;

		public String toString() {
			return uci;
		}
	}

}
