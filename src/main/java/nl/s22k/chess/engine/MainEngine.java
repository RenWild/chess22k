package nl.s22k.chess.engine;

import java.util.Arrays;
import java.util.Scanner;

import nl.s22k.chess.ChessBoard;
import nl.s22k.chess.ChessBoardUtil;
import nl.s22k.chess.ChessConstants;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.eval.EvalCache;
import nl.s22k.chess.eval.EvalUtil;
import nl.s22k.chess.eval.PawnEvalCache;
import nl.s22k.chess.move.MagicUtil;
import nl.s22k.chess.move.MoveWrapper;
import nl.s22k.chess.move.TreeMove;
import nl.s22k.chess.search.HeuristicUtil;
import nl.s22k.chess.search.NegamaxUtil;
import nl.s22k.chess.search.RepetitionTable;
import nl.s22k.chess.search.TTUtil;
import nl.s22k.chess.search.TimeUtil;

public class MainEngine {

	// TODO use UCI-constants

	private static ChessBoard chessBoard;
	public static boolean quiet = false;
	public static String startFen = "";

	private static Thread searchThread;
	private static Thread maxTimeThread;

	private static Object synchronizedSearch = new Object();
	private static Object synchronizedTimer = new Object();

	private static boolean calculating = false;

	public static void main(String[] args) {
		MagicUtil.init();
		chessBoard = ChessBoardUtil.getNewCB();

		// calculation thread
		searchThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						synchronized (synchronizedSearch) {
							synchronizedSearch.wait();
						}
						Statistics.reset();
						NegamaxUtil.start(chessBoard);
						calculating = false;
						maxTimeThread.interrupt();
					} catch (Throwable t) {
						ErrorLogger.log(chessBoard, t);
					}
				}
			}
		};
		searchThread.setName("search");
		searchThread.setDaemon(true);
		searchThread.start();

		// max time thread
		maxTimeThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						synchronized (synchronizedTimer) {
							synchronizedTimer.wait();
						}

						// check needed because of spurious wakeups??
						if (calculating) {
							Thread.sleep(TimeUtil.getMaxTime());
							NegamaxUtil.stop = true;
						}
					} catch (InterruptedException e) {
						// do nothing
					}
				}
			}
		};
		maxTimeThread.setName("max-timer");
		maxTimeThread.setDaemon(true);
		maxTimeThread.start();

		MainEngine.start();
	}

	private static void start() {
		try {
			Scanner sc = new Scanner(System.in);

			while (sc.hasNextLine()) {
				String[] tokens = sc.nextLine().split(" ");
				if (tokens[0].equals("uci")) {
					System.out.println("id name chess22k " + getVersion());
					System.out.println("id author Sander MvdB");
					System.out.println("option name Hash type spin default 128 min 1 max 1024");
					System.out.println("uciok");
				} else if (tokens[0].equals("isready")) {
					System.out.println("readyok");
				} else if (tokens[0].equals("ucinewgame")) {
					HeuristicUtil.clearTables();
					TTUtil.clearValues();
					PawnEvalCache.clearValues();
					EvalCache.clearValues();
				} else if (tokens[0].equals("position")) {
					if (tokens[1].equals("startpos")) {
						chessBoard = ChessBoardUtil.getNewCB();
						if (tokens.length == 2) {
							// position startpos
							position(new String[] {});
						} else {
							// position startpos moves f2f3 g1a3 ...
							position(Arrays.copyOfRange(tokens, 3, tokens.length));
						}
					} else {
						// position fen 4k3/8/8/8/8/3K4 b kq - 0 1 moves f2f3 g1a3 ...
						String fen = tokens[2] + " " + tokens[3] + " " + tokens[4] + " " + tokens[5];
						if (tokens.length > 6) {
							fen += " " + tokens[6];
						}
						if (tokens.length > 6) {
							fen += " " + tokens[7];
						}
						chessBoard = ChessBoardUtil.getNewCB(fen);
						if (tokens.length == 8) {
							// position fen 4k3/8/8/8/8/3K4 b kq - 0 1
							position(new String[] {});
						} else {
							// position fen 4k3/8/8/8/8/3K4 b kq - 0 1 moves f2f3 g1a3 ...
							position(Arrays.copyOfRange(tokens, 9, tokens.length));
						}
					}
				} else if (tokens[0].equals("go")) {
					if (NegamaxUtil.stop) {
						go(tokens, chessBoard.moveCounter, chessBoard.colorToMove);
					}
				} else if (tokens[0].equals("eval")) {
					eval();
				} else if (tokens[0].equals("setoption")) {
					setOption(tokens[2], tokens[4]);
				} else if (tokens[0].equals("quit")) {
					sc.close();
					System.exit(0);
				} else if (tokens[0].equals("stop")) {
					NegamaxUtil.stop = true;
				} else {
					System.out.println("Unknown command: " + tokens[0]);
				}
			}
		} catch (Throwable t) {
			ErrorLogger.log(chessBoard, t);
		}

	}

	private static void setOption(String optionName, String optionValue) {
		// setoption name Hash value 128
		if (optionName.toLowerCase().equals("hash")) {
			int value = Integer.parseInt(optionValue);
			switch (value) {
			case 1:
			case 2:
			case 4:
			case 8:
			case 16:
			case 32:
			case 64:
			case 128:
			case 256:
			case 512:
			case 1024:
				int power2Entries = (int) (Math.log(value) / Math.log(2) + 16);
				if (EngineConstants.POWER_2_TT_ENTRIES != power2Entries) {
					EngineConstants.POWER_2_TT_ENTRIES = power2Entries;
					TTUtil.init();
				}
				break;
			default:
				System.out.println("Hash-size must be between 1-1024 and a multiple of 2. Setting default size of 128mb");
				power2Entries = (int) (Math.log(128) / Math.log(2) + 16);
				if (EngineConstants.POWER_2_TT_ENTRIES != power2Entries) {
					EngineConstants.POWER_2_TT_ENTRIES = power2Entries;
					TTUtil.init();
				}
			}

		} else {
			System.out.println("Unknown option: " + optionName);
		}
	}

	private static void go(String[] goCommandTokens, int moveCount, int colorToMove) {
		// go movestogo 30 wtime 3600000 btime 3600000
		// go wtime 40847 btime 48019 winc 0 binc 0 movestogo 20
		long totalTimeLeft = Long.MAX_VALUE;
		int movesToGo = 0;
		NegamaxUtil.maxDepth = ChessConstants.MAX_PLIES;

		// go
		// go infinite
		if (goCommandTokens.length != 1 && !goCommandTokens[1].equals("infinite")) {
			for (int i = 1; i < goCommandTokens.length; i++) {
				if (goCommandTokens[i].equals("movestogo")) {
					movesToGo = Integer.parseInt(goCommandTokens[i + 1]);
				} else if (goCommandTokens[i].equals("depth")) {
					NegamaxUtil.maxDepth = Integer.parseInt(goCommandTokens[i + 1]);
				}

				if (colorToMove == ChessConstants.WHITE) {
					if (goCommandTokens[i].equals("wtime")) {
						totalTimeLeft = Integer.parseInt(goCommandTokens[i + 1]);
					}
				} else {
					if (goCommandTokens[i].equals("btime")) {
						totalTimeLeft = Integer.parseInt(goCommandTokens[i + 1]);
					}
				}
			}
		}
		TimeUtil.setTimeWindow(totalTimeLeft, moveCount, movesToGo);

		calculating = true;
		synchronized (synchronizedSearch) {
			synchronizedSearch.notify();
		}
		synchronized (synchronizedTimer) {
			synchronizedTimer.notify();
		}
	}

	private static void position(String[] moveTokens) {
		// apply moves
		for (String moveToken : moveTokens) {
			MoveWrapper move = new MoveWrapper(moveToken, chessBoard);
			chessBoard.doMove(move.move);
			RepetitionTable.addValue(chessBoard.zobristKey);
		}
		TTUtil.halfMoveCounter = chessBoard.moveCounter;
		startFen = chessBoard.toString();
	}

	private static void eval() {
		System.out.println("Material (no pawn) : " + EvalUtil.calculateMaterialScores(chessBoard));
		System.out.println("Position           : " + EvalUtil.calculatePositionScores(chessBoard));
		System.out.println("Mobility           : " + EvalUtil.calculateMobilityScores(chessBoard));
		System.out.println("King-safety        : " + EvalUtil.calculateKingSafetyScores(chessBoard));
		System.out.println("Pawn               : " + EvalUtil.calculatePawnScores(chessBoard));
		System.out.println("Pawn-passed        : " + EvalUtil.calculatePassedPawnScores(chessBoard));
		System.out.println("Bonus              : " + EvalUtil.calculateBonusses(chessBoard));
		System.out.println("Penalties          : " + EvalUtil.calculatePenalties(chessBoard));
		System.out.println("Total              : " + EvalUtil.calculateScore(chessBoard));
	}

	public static void sendBestMove() {
		if (quiet) {
			return;
		}
		Statistics.print();
		TreeMove bestMove = Statistics.bestMove;
		System.out.println("bestmove " + new MoveWrapper(bestMove.move));
	}

	public static void sendPlyInfo() {
		if (quiet) {
			return;
		}
		TreeMove bestMove = Statistics.bestMove;

		// info depth 1 seldepth 2 score cp 50 pv d2d4 d7d5 e2e3 hashfull 0 nps 1000 nodes 22
		// info depth 4 seldepth 10 score cp 40 upperbound pv d2d4 d7d5 e2e3 hashfull 0 nps 30000 nodes 1422
		System.out.println("info depth " + Statistics.depth + " seldepth " + Statistics.maxDepth + " time " + Statistics.getPassedTime() + " score cp "
				+ bestMove.score + bestMove.scoreType + "nps " + Statistics.calculateNps() + " nodes " + Statistics.moveCount + " hashfull "
				+ TTUtil.usageCounter * 1000 / (TTUtil.maxEntries * 2) + " pv " + bestMove);
	}

	private static String getVersion() {
		String version = null;
		Package pkg = new MainEngine().getClass().getPackage();
		if (pkg != null) {
			version = pkg.getImplementationVersion();
			if (version == null) {
				version = pkg.getSpecificationVersion();
			}
		}
		version = version == null ? "" : version.trim();
		return version.isEmpty() ? "v?" : version;
	}

}
