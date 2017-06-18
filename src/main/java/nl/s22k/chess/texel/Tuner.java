package nl.s22k.chess.texel;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import nl.s22k.chess.ChessBoard;
import nl.s22k.chess.Statistics;
import nl.s22k.chess.engine.EngineConstants;
import nl.s22k.chess.eval.EvalConstants;
import nl.s22k.chess.move.MagicUtil;

public class Tuner {

	private static Map<String, Double> fens = new HashMap<String, Double>();
	private static int numberOfThreads = 6;
	private static ErrorCalculator[] workers = new ErrorCalculator[numberOfThreads];
	private static ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

	public static void main(String[] args) {
		// setup
		EngineConstants.ENABLE_EVAL_CACHE = false;
		EngineConstants.ENABLE_PAWN_EVAL_CACHE = false;
		EngineConstants.isTuningSession = true;
		MagicUtil.init();

		// read all fens, including score
		loadFens();

		// init workers
		ChessBoard.initTuningInstances(numberOfThreads);
		for (int i = 0; i < numberOfThreads; i++) {
			workers[i] = new ErrorCalculator(ChessBoard.getTuningInstance(i));
		}

		// add fens to workers
		int workerIndex = 0;
		Iterator<Entry<String, Double>> iterator = fens.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, Double> entry = iterator.next();
			workers[workerIndex].addFenWithScore(entry.getKey(), entry.getValue());
			workerIndex = workerIndex == numberOfThreads - 1 ? 0 : workerIndex + 1;
		}

		// get tuned values
		List<TuningObject> tuningObjects = getTuningObjects();

		// tune
		printInfo(tuningObjects);
		localOptimize(tuningObjects);
		executor.shutdown();
		System.out.println("\nDone\n");
		for (TuningObject tuningObject : tuningObjects) {
			tuningObject.printOrgValue();
			System.out.println(tuningObject);
		}
	}

	private static List<TuningObject> getTuningObjects() {
		List<TuningObject> tuningObjects = new ArrayList<TuningObject>();

		tuningObjects.add(new TuningObject(EvalConstants.MATERIAL_SCORES, 5, "Material", false, false, 0, 1, 6));
		tuningObjects.add(new TuningObject(EvalConstants.MATERIAL_SCORES_ENDGAME, 5, "Material endgame", false, false, 0));
		tuningObjects.add(new TuningObject(EvalConstants.INDIVIDUAL_SCORES, 2, "Individual score", false, false));
		tuningObjects.add(new TuningObject(EvalConstants.PINNED_PIECE_SCORES, 4, "Pinned pieces", false, false, 0, 6));

		// king-safety
		tuningObjects.add(new TuningObject(EvalConstants.KING_SAFETY_SCORES, 4, "King safety", false, false));
		tuningObjects.add(new TuningObject(EvalConstants.KING_SAFETY_COUNTER_RANKS, 1, "King safety counter", false, true));
		tuningObjects.add(new TuningObject(EvalConstants.KING_SAFETY_ATTACK_PATTERN_COUNTER, 1, "King safety pattern", false, true));

		// mobility
		tuningObjects.add(new TuningObject(EvalConstants.MOBILITY_KNIGHT, 2, "Mobility knight", true, false));
		tuningObjects.add(new TuningObject(EvalConstants.MOBILITY_BISHOP, 2, "Mobility bishop", true, false));
		tuningObjects.add(new TuningObject(EvalConstants.MOBILITY_ROOK, 2, "Mobility rook", true, false));
		tuningObjects.add(new TuningObject(EvalConstants.MOBILITY_QUEEN, 2, "Mobility queen", true, false));

		// pawns
		// tuningObjects.add(new TuningObject(EvalConstants.PAWN_STORM_BONUS, 4, "Pawn storm", false, false, 0, 7));
		tuningObjects.add(new TuningObject(EvalConstants.PASSED_PAWN_SCORE, 5, "Passed pawn", false, false, 0, 7));
		tuningObjects.add(new TuningObject(EvalConstants.PAWN_SHIELD_BONUS, 4, "Pawn shield", false, false, 0, 7));
		tuningObjects.add(new TuningObject(EvalConstants.PASSED_PAWN_MULTIPLIERS, 1, "Passed pawn multiplier", false, false));

		// psqt
		tuningObjects.add(new PsqtTuningObject(EvalConstants.PSQT_PAWN, 5, "PSQT pawn", true, 0, 1, 2, 3, 4, 5, 6, 7, 56, 57, 58, 59, 60, 61, 62, 63));
		tuningObjects.add(
				new PsqtTuningObject(EvalConstants.PSQT_PAWN_ENDGAME, 5, "PSQT pawn endgame", true, 0, 1, 2, 3, 4, 5, 6, 7, 56, 57, 58, 59, 60, 61, 62, 63));
		tuningObjects.add(new PsqtTuningObject(EvalConstants.PSQT_ROOK, 5, "PSQT rook", true));
		tuningObjects.add(new PsqtTuningObject(EvalConstants.PSQT_BISHOP, 5, "PSQT bishop", true));
		tuningObjects.add(new PsqtTuningObject(EvalConstants.PSQT_KNIGHT, 5, "PSQT knight", true));
		tuningObjects.add(new PsqtTuningObject(EvalConstants.PSQT_KING, 5, "PSQT king", true));
		tuningObjects.add(new PsqtTuningObject(EvalConstants.PSQT_KING_ENDGAME, 5, "PSQT king endgame", true));

		return tuningObjects;
	}

	private static void loadFens() {

		// loadFen("d:\\chess\\white-wins.epd", 1, 20);
		// loadFen("d:\\chess\\draws.epd", 0.5f, 20);
		// loadFen("d:\\chess\\black-wins.epd", 0, 20);

		loadFen("d:\\chess\\quiet-labeled.epd");

		System.out.println(fens.size() + " fens found");
	}

	private static void loadFen(String fileName) {
		System.out.println("Loading " + fileName);

		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line = br.readLine();

			while (line != null) {

				String[] values = line.split(" c9 ");
				double score = 0;
				if (values[1].equals("\"1/2-1/2\";")) {
					score = 0.5;
				} else if (values[1].equals("\"1-0\";")) {
					score = 1;
				} else if (values[1].equals("\"0-1\";")) {
					score = 0;
				} else {
					throw new RuntimeException("Unknown result: " + values[1]);
				}
				fens.put(values[0], score);

				line = br.readLine();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	private static void loadFen(String fileName, double score, int bookMoves) {

		System.out.println("Loading " + fileName);

		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line = br.readLine();
			int currentMoveNumber = 0;

			while (line != null) {
				if (line.equals("")) {
					currentMoveNumber = 0;
				} else {
					currentMoveNumber++;
					if (currentMoveNumber > bookMoves) {
						fens.put(line.substring(0, line.indexOf(" c0 ")), score);
					}
				}
				line = br.readLine();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	private static void printInfo(List<TuningObject> tuningObjects) {
		Statistics.reset();
		System.out.println("\nNumber of threads: " + numberOfThreads);
		System.out.println(String.format("Initial error: %s (%s ms)", calculateErrorMultiThreaded(), Statistics.getPassedTimeMs()));
		System.out.println("\nValues that are being tuned:");

		int totalValues = 0;
		for (TuningObject tuningObject : tuningObjects) {
			System.out.println(tuningObject);
			totalValues += tuningObject.tunedValues;
		}
		System.out.println("Total values to be tuned: " + totalValues + "\n");
	}

	private static void localOptimize(List<TuningObject> tuningObjects) {
		double bestError = calculateErrorMultiThreaded();
		boolean improved = true;
		while (improved) {
			improved = false;
			for (TuningObject tuningObject : tuningObjects) {
				for (int i = 0; i < tuningObject.numberOfParameters(); i++) {
					if (tuningObject.skip(i)) {
						continue;
					}
					tuningObject.addStep(i);
					double newError = calculateErrorMultiThreaded();
					if (newError < bestError) {
						bestError = newError;
						System.out.println(tuningObject + ": " + bestError);
						improved = true;
					} else {
						tuningObject.removeStep(i);
						if (tuningObject.allScoresAboveZero && tuningObject.scoreIsZero(i)) {
							continue;
						}
						tuningObject.removeStep(i);
						newError = calculateErrorMultiThreaded();
						if (newError < bestError) {
							bestError = newError;
							System.out.println(tuningObject + ": " + bestError);
							improved = true;
						} else {
							tuningObject.addStep(i);
						}
					}
				}
			}
		}
	}

	private static double calculateErrorMultiThreaded() {
		List<Future<Double>> list = new ArrayList<Future<Double>>();
		for (int i = 0; i < numberOfThreads; i++) {
			Future<Double> submit = executor.submit(workers[i]);
			list.add(submit);
		}
		double totalError = 0;
		// now retrieve the result
		for (Future<Double> future : list) {
			try {
				totalError += future.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		return totalError / numberOfThreads;
	}

}
