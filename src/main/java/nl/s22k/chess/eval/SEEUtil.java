package nl.s22k.chess.eval;

import static nl.s22k.chess.ChessConstants.BISHOP;
import static nl.s22k.chess.ChessConstants.KING;
import static nl.s22k.chess.ChessConstants.NIGHT;
import static nl.s22k.chess.ChessConstants.PAWN;
import static nl.s22k.chess.ChessConstants.QUEEN;
import static nl.s22k.chess.ChessConstants.ROOK;

import nl.s22k.chess.Assert;
import nl.s22k.chess.Bitboard;
import nl.s22k.chess.ChessBoard;
import nl.s22k.chess.ChessConstants;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.Util;
import nl.s22k.chess.engine.EngineConstants;
import nl.s22k.chess.move.MagicUtil;
import nl.s22k.chess.move.MoveUtil;
import nl.s22k.chess.move.StaticMoves;

public class SEEUtil {

	private static int getSmallestAttackSeeMove(final long pieces[], final int colorToMove, final int toIndex, final long allPieces, final long slidingMask) {

		// TODO stop when bad-capture

		// put 'super-piece' in see position
		long attackMove;

		// pawn non-promotion attacks
		attackMove = StaticMoves.PAWN_ATTACKS[1 - colorToMove][toIndex] & pieces[PAWN] & allPieces & Bitboard.RANK_NON_PROMOTION[colorToMove];
		if (attackMove != 0) {
			return MoveUtil.createSeeAttackMove(Long.numberOfTrailingZeros(attackMove), PAWN);
		}

		// knight attacks
		attackMove = pieces[NIGHT] & StaticMoves.KNIGHT_MOVES[toIndex] & allPieces;
		if (attackMove != 0) {
			return MoveUtil.createSeeAttackMove(Long.numberOfTrailingZeros(attackMove), NIGHT);
		}

		// bishop attacks
		if ((pieces[BISHOP] & slidingMask) != 0) {
			attackMove = pieces[BISHOP] & MagicUtil.getBishopMoves(toIndex, allPieces) & allPieces;
			if (attackMove != 0) {
				return MoveUtil.createSeeAttackMove(Long.numberOfTrailingZeros(attackMove), BISHOP);
			}
		}

		// rook attacks
		if ((pieces[ROOK] & slidingMask) != 0) {
			attackMove = pieces[ROOK] & MagicUtil.getRookMoves(toIndex, allPieces) & allPieces;
			if (attackMove != 0) {
				return MoveUtil.createSeeAttackMove(Long.numberOfTrailingZeros(attackMove), ROOK);
			}
		}

		// queen attacks
		if ((pieces[QUEEN] & slidingMask) != 0) {
			attackMove = pieces[QUEEN] & MagicUtil.getQueenMoves(toIndex, allPieces) & allPieces;
			if (attackMove != 0) {
				return MoveUtil.createSeeAttackMove(Long.numberOfTrailingZeros(attackMove), QUEEN);
			}
		}

		// pawn promotion attacks
		if ((pieces[PAWN] & Bitboard.RANK_PROMOTION[colorToMove]) != 0) {
			attackMove = StaticMoves.PAWN_ATTACKS[1 - colorToMove][toIndex] & pieces[PAWN] & allPieces & Bitboard.RANK_PROMOTION[colorToMove];
			if (attackMove != 0) {
				return MoveUtil.createPromotionAttack(MoveUtil.TYPE_PROMOTION_Q, Long.numberOfTrailingZeros(attackMove), toIndex, 0);
			}
		}

		// king attacks
		attackMove = pieces[KING] & StaticMoves.KING_MOVES[toIndex];
		if (attackMove != 0) {
			return MoveUtil.createSeeAttackMove(Long.numberOfTrailingZeros(attackMove), KING);
		}

		return 0;
	}

	private static int getSeeScore(final ChessBoard cb, final int colorToMove, final int toIndex, final int attackedPieceIndex, long allPieces,
			long slidingMask) {

		if (Statistics.ENABLED) {
			Statistics.seeNodes++;
		}

		final int move = getSmallestAttackSeeMove(cb.pieces[colorToMove], colorToMove, toIndex, allPieces, slidingMask);

		/* skip if the square isn't attacked anymore by this side */
		if (move == 0) {
			return 0;
		}
		if (attackedPieceIndex == KING) {
			return 3000;
		}

		allPieces ^= Util.POWER_LOOKUP[MoveUtil.getFromIndex(move)];
		slidingMask &= allPieces;

		// add score when promotion
		if (MoveUtil.isPromotion(move)) {

			/* Do not consider captures if they lose material, therefore max zero */
			return Math.max(0, EvalConstants.PROMOTION_SCORE[ChessConstants.QUEEN] + EvalConstants.MATERIAL[attackedPieceIndex]
					- getSeeScore(cb, 1 - colorToMove, toIndex, QUEEN, allPieces, slidingMask));
		} else {

			/* Do not consider captures if they lose material, therefore max zero */
			return Math.max(0, EvalConstants.MATERIAL[attackedPieceIndex]
					- getSeeScore(cb, 1 - colorToMove, toIndex, MoveUtil.getSourcePieceIndex(move), allPieces, slidingMask));
		}

	}

	public static int getSeeCaptureScore(final ChessBoard cb, final int move) {

		if (EngineConstants.ASSERT) {
			if (MoveUtil.getAttackedPieceIndex(move) == 0) {
				Assert.isTrue(MoveUtil.getMoveType(move) != 0);
			}
		}

		final int index = MoveUtil.getToIndex(move);
		final long allPieces = cb.allPieces & ~Util.POWER_LOOKUP[MoveUtil.getFromIndex(move)];
		final long slidingMask = (MagicUtil.bishopMovesEmptyBoard[index] | MagicUtil.rookMovesEmptyBoard[index]) & allPieces;

		// add score when promotion
		if (MoveUtil.isPromotion(move)) {
			return EvalConstants.PROMOTION_SCORE[MoveUtil.getMoveType(move)] + EvalConstants.MATERIAL[MoveUtil.getAttackedPieceIndex(move)]
					- getSeeScore(cb, cb.colorToMoveInverse, index, MoveUtil.getMoveType(move), allPieces, slidingMask);
		} else {
			return EvalConstants.MATERIAL[MoveUtil.getAttackedPieceIndex(move)]
					- getSeeScore(cb, cb.colorToMoveInverse, index, MoveUtil.getSourcePieceIndex(move), allPieces, slidingMask);
		}

	}
}
