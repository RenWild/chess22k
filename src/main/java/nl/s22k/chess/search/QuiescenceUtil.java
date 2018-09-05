package nl.s22k.chess.search;

import nl.s22k.chess.Assert;
import nl.s22k.chess.CheckUtil;
import nl.s22k.chess.ChessBoard;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.engine.EngineConstants;
import nl.s22k.chess.eval.EvalConstants;
import nl.s22k.chess.eval.EvalUtil;
import nl.s22k.chess.eval.SEEUtil;
import nl.s22k.chess.move.MoveGenerator;
import nl.s22k.chess.move.MoveUtil;

public class QuiescenceUtil {

	private static final int FUTILITY_MARGIN = 100;

	public static int calculateBestMove(final ChessBoard cb, final MoveGenerator moveGen, int alpha, final int beta) {

		if (Statistics.ENABLED) {
			Statistics.qNodes++;
		}

		if (NegamaxUtil.mode.get() != Mode.START) {
			return 0;
		}

		/* stand-pat check */
		int score = EvalUtil.getScore(cb);
		if (score >= beta) {
			return score;
		}

		alpha = Math.max(alpha, score);

		moveGen.startPly();
		moveGen.generateAttacks(cb);
		moveGen.setMVVLVAScores();
		moveGen.sort();

		while (moveGen.hasNext()) {
			final int move = moveGen.next();

			if (!cb.isLegal(move)) {
				continue;
			}

			// skip under promotions
			if (MoveUtil.isPromotion(move)) {
				if (MoveUtil.getMoveType(move) != MoveUtil.TYPE_PROMOTION_Q) {
					continue;
				}
			} else if (score + FUTILITY_MARGIN + EvalConstants.MATERIAL[MoveUtil.getAttackedPieceIndex(move)] < alpha) {
				// futility pruning
				continue;
			}

			// skip bad-captures
			if (EngineConstants.ENABLE_Q_PRUNE_BAD_CAPTURES && SEEUtil.getSeeCaptureScore(cb, move) <= 0) {
				continue;
			}

			cb.doMove(move);

			if (EngineConstants.ASSERT) {
				cb.changeSideToMove();
				Assert.isTrue(0 == CheckUtil.getCheckingPieces(cb));
				cb.changeSideToMove();
			}

			score = -calculateBestMove(cb, moveGen, -beta, -alpha);

			cb.undoMove(move);

			if (score >= beta) {
				moveGen.endPly();
				return score;
			}
			alpha = Math.max(alpha, score);
		}

		moveGen.endPly();
		return alpha;
	}
}
