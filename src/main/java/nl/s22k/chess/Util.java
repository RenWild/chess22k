package nl.s22k.chess;

import static nl.s22k.chess.ChessConstants.BLACK;
import static nl.s22k.chess.ChessConstants.WHITE;

public class Util {

	public static final short SHORT_MIN = Short.MIN_VALUE + 1;
	public static final short SHORT_MAX = Short.MAX_VALUE;

	public static final long[] POWER_LOOKUP = new long[64];
	static {
		for (int i = 0; i < 64; i++) {
			POWER_LOOKUP[i] = 1L << i;
		}
	}

	public static byte[] getSetBitsSlow(long value) {
		if (Long.bitCount(value) == 0) {
			return new byte[0];
		}
		byte[] setBits = new byte[Long.bitCount(value)];

		byte counter = 0;
		for (byte i = 0; i < 64; i++) {
			if ((value >> i & 1) == 1) {
				setBits[counter++] = i;
			}
		}
		return setBits;
	}

	public static void reverse(int[] array) {
		for (int i = 0; i < array.length / 2; i++) {
			int temp = array[i];
			array[i] = array[array.length - 1 - i];
			array[array.length - 1 - i] = temp;
		}
	}
	
	public static void reverse(long[] array) {
		for (int i = 0; i < array.length / 2; i++) {
			long temp = array[i];
			array[i] = array[array.length - 1 - i];
			array[array.length - 1 - i] = temp;
		}
	}

	public static long mirrorHorizontal(long bitboard) {
		long k1 = 0x5555555555555555L;
		long k2 = 0x3333333333333333L;
		long k4 = 0x0f0f0f0f0f0f0f0fL;
		bitboard = ((bitboard >> 1) & k1) | ((bitboard & k1) << 1);
		bitboard = ((bitboard >> 2) & k2) | ((bitboard & k2) << 2);
		bitboard = ((bitboard >> 4) & k4) | ((bitboard & k4) << 4);
		return bitboard;
	}

	public static long mirrorVertical(long bitboard) {
		return Long.reverseBytes(bitboard);
	}
	
	public static int getDistance(int index1, int index2){
		return Math.max(Math.abs(index1 / 8 - index2 / 8),	Math.abs((index1 & 7) - (index2 & 7)));
	}

}
