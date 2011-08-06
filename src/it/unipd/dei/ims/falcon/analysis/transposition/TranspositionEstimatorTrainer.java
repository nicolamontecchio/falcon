package it.unipd.dei.ims.falcon.analysis.transposition;

import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaMatrixUtils;
import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaVector;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Training algorithm for transposition estimator
 * @author Nicola Montecchio
 */
public class TranspositionEstimatorTrainer {

	private static Random random = new Random();
	private static final int EPOCHS = 2000;
	private static final int MOVESET_SIZE = 30;
	private static final float VARIABILITY = 0.2f;

	private class FilePair {

		private File first;
		private File second;
		private int firstTransp;
		private int secondTransp;
		private ChromaVector[] firstChroma;
		private ChromaVector[] secondChroma;
		private float[] firstProfile;
		private float[] secondProfile;

		/** Construct a new file pair. Chroma matrices are automatically read and randomly transposed */
		public FilePair(File f, File s) throws FileNotFoundException, IOException {
			first = f;
			second = s;
			firstChroma = ChromaMatrixUtils.readChromaFile(first);
			secondChroma = ChromaMatrixUtils.readChromaFile(second);
			firstTransp = random.nextInt(12);
			secondTransp = random.nextInt(12);
			for (ChromaVector v : firstChroma)
				v.rotate(firstTransp);
			for (ChromaVector v : secondChroma)
				v.rotate(secondTransp);
			firstProfile = TranspositionEstimator.getProfile(firstChroma);
			secondProfile = TranspositionEstimator.getProfile(secondChroma);
		}

		public int getFirstTransp() {
			return firstTransp;
		}

		public ChromaVector[] getFirstChroma() {
			return firstChroma;
		}

		public ChromaVector[] getSecondChroma() {
			return secondChroma;
		}

		public int getSecondTransp() {
			return secondTransp;
		}

		public float[] getFirstProfile() {
			return firstProfile;
		}

		public float[] getSecondProfile() {
			return secondProfile;
		}
	}

	private static void normalize1(float[] ww) {
		for (int i = 0; i < ww.length; i++)
			if (ww[i] < 0)
				ww[i] = 0;
		float sum = 0;
		for (float w : ww)
			sum += w;
		if (sum > 0.0001)
			for (int i = 0; i < ww.length; i++)
				ww[i] /= sum;
		else
			for (int i = 0; i < ww.length; i++)
				ww[i] = 1.f / ww.length;
	}

	private static List<float[]> getMoveSet(float[] w) {
		List<float[]> ff = new LinkedList<float[]>();
		for (int i = 0; i < MOVESET_SIZE; i++) {
			float[] q = new float[w.length];
			for (int j = 0; j < q.length; j++)
				q[j] += (random.nextFloat() - 0.5f) * VARIABILITY;
			normalize1(q);
			ff.add(q);
		}
		return ff;
	}

	private static String join(Collection s, String delimiter) {
		StringBuilder buffer = new StringBuilder();
		Iterator iter = s.iterator();
		while (iter.hasNext()) {
			buffer.append(iter.next().toString());
			if (iter.hasNext()) {
				buffer.append(delimiter);
			}
		}
		return buffer.toString();
	}

	private static String printArray(float[] w) {
		List<Float> f = new LinkedList<Float>();
		for (float x : w)
			f.add(x);
		return "[" + join(f, ",") + "]";
	}

	private static String printArray(int[] w) {
		List<Integer> f = new LinkedList<Integer>();
		for (int x : w)
			f.add(x);
		return "[" + join(f, ",") + "]";
	}

	// return the rank of the found match, as float
	private static int getMatchingRank(float[] p1, float[] p2, TranspositionEstimator tpe, int expectedT) {
		int t1 = tpe.findKey(new ChromaVector(p1), 1)[0];
		int[] t2 = tpe.findKey(new ChromaVector(p2), 12);
		for (int i = 0; i < t2.length; i++) {
			if ((120 + t2[i] - t1 - expectedT) % 12 == 0)
				return 1 + i;
		}
		// should never be reached ...
		{
			System.out.println("AAAAAAAAAAAA");
			System.out.println("    t1 = " + t1);
			System.out.println("    t2 = " + printArray(t2));
			System.out.println("    exp = " + expectedT);
			System.out.println("    p1 = " + printArray(p1));
			System.out.println("    p2 = " + printArray(p2));
			System.out.println("    w = " + printArray(tpe.getWeights()));
		}
		return Integer.MAX_VALUE;

	}

	/**
	 * Two-way evaluation. For each pair, use the first transposition of one file 
	 * and get the best matching of the other; then reverse.
	 */
	private static float evaluate(float[] w, List<FilePair> pairs) {
		float totScore = 0.f;
		TranspositionEstimator tpe = new TranspositionEstimator(w);
		for (FilePair fp : pairs) {
			int r1 = getMatchingRank(fp.getFirstProfile(), fp.getSecondProfile(), tpe, fp.firstTransp - fp.secondTransp);
			int r2 = getMatchingRank(fp.getSecondProfile(), fp.getFirstProfile(), tpe, fp.secondTransp - fp.firstTransp);
			totScore += r1 > 0 ? 1.f / (r1 * r1) : 1;
			totScore += r2 > 0 ? 1.f / (r2 * r2) : 1;
		}
		return totScore;
	}

	private static void evaluateVerbose(float[] w, List<FilePair> pairs) {
		Map<Float, Integer> rankCount = new TreeMap<Float, Integer>();
		TranspositionEstimator tpe = new TranspositionEstimator(w);
		for (FilePair fp : pairs) {
			float r1 = getMatchingRank(fp.getFirstProfile(), fp.getSecondProfile(), tpe, fp.firstTransp - fp.secondTransp);
			float r2 = getMatchingRank(fp.getSecondProfile(), fp.getFirstProfile(), tpe, fp.secondTransp - fp.firstTransp);
			rankCount.put(r1, rankCount.containsKey(r1) ? rankCount.get(r1) + 1 : 1);
			rankCount.put(r2, rankCount.containsKey(r2) ? rankCount.get(r2) + 1 : 1);
		}
		for (Float f : new TreeSet<Float>(rankCount.keySet()))
			System.out.println(String.format("       [%2d]: %5d", f.intValue(), rankCount.get(f)));
	}

	public void train() throws IOException {
		// read all file pairs from STDIN
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		List<FilePair> pairs = new LinkedList<FilePair>();
		List<String> couple = new LinkedList<String>();
		String line = null;
		while ((line = in.readLine()) != null) {
			couple.add(line);
			if (couple.size() == 2) {
				pairs.add(new FilePair(new File(couple.get(0)), new File(couple.get(1))));
				couple.clear();
			}
		}
		in.close();
		// split into 5/1 training/validation set
		List<FilePair> trainingPairs = new LinkedList<FilePair>();
		List<FilePair> validationPairs = new LinkedList<FilePair>();
		Collections.shuffle(pairs, random);
		int p = 0;
		while (!pairs.isEmpty())
			(p++ % 5 != 0 ? trainingPairs : validationPairs).add(pairs.remove(0));

		// random initial weights
		float[] bestWeights = new float[12];
		for (int i = 0; i < bestWeights.length; i++)
			bestWeights[i] = random.nextFloat();
		normalize1(bestWeights);
		float bestTrainingScore = evaluate(bestWeights, pairs);
		System.out.println("initial random weights have score " + bestTrainingScore);
		// iterations
		for (int it = 0; it < EPOCHS; it++) {
			// evaluate a new moveset
			List<float[]> moveset = getMoveSet(bestWeights);
			List<Float> tScores = new LinkedList<Float>();
			for (float[] move : moveset)
				tScores.add(evaluate(move, trainingPairs));
			// get max and eventually move
			float bs = Collections.max(tScores);
			if (bs > bestTrainingScore) {
				bestTrainingScore = bs;
				bestWeights = moveset.get(tScores.indexOf(bs));
				float vScore = evaluate(bestWeights, validationPairs);
				System.out.println(String.format("new best found [t: %f v: %f]: %s", bestTrainingScore, vScore, printArray(bestWeights)));
				System.out.println("  on training:");
				evaluateVerbose(bestWeights, trainingPairs);		
				System.out.println("  on validation:");
				evaluateVerbose(bestWeights, validationPairs);		
			}
		}
		// output best
		evaluateVerbose(bestWeights, pairs);
	}

	/**
	 * Read from STDIN a list of files. Files are coupled so that
	 * every even line is a different recording (cover) of the previous odd line.
	 * It is supposed that every recording couple is recorded in the same main key.
	 * Transpositions are scrambled so that any bias is avoided, then weights
	 * are trained using a randomized hill climbing approach.
	 */
	public static void main(String[] args) throws IOException {
		new TranspositionEstimatorTrainer().train();
	}
}
