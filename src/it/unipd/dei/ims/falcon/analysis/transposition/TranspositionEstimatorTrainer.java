package it.unipd.dei.ims.falcon.analysis.transposition;

import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaMatrixUtils;
import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaVector;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Training algorithm for transposition estimator
 * @author Nicola Montecchio
 */
public class TranspositionEstimatorTrainer {

	private static Random random = new Random();
	private static final int EPOCHS = 100;
	private static final int MOVESET_SIZE = 10;
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

	private static List<float[]> getMoveSet(float[] w) {
		List<float[]> ff = new LinkedList<float[]>();
		for (int i = 0; i < MOVESET_SIZE; i++) {
			float[] q = new float[w.length];
			for (int j = 0; j < q.length; j++)
				q[j] += (random.nextFloat() - 0.5f) * VARIABILITY;
			ff.add(q);
		}
		return ff;
	}

	private static String join(Collection s, String delimiter) {
		StringBuffer buffer = new StringBuffer();
		Iterator iter = s.iterator();
		while (iter.hasNext()) {
			buffer.append(iter.next());
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

	private static float evalWOnProfiles(float[] p1, float[] p2, TranspositionEstimator tpe, int expectedT) {
		int t1 = tpe.findKey(new ChromaVector(p1), 1)[0];
		int[] t2 = tpe.findKey(new ChromaVector(p2), 12);
		for(int i = 0; i < t2.length; i++) {
			if(t1-t2[i]==expectedT)
				return 1.f/(i+1);
		}
		return 0.f;
	}

	/**
	 * Two-way evaluation. For each pair, use the first transposition of one file 
	 * and get the best matching of the other; then reverse.
	 */
	private static float evaluate(float[] w, List<FilePair> pairs) {
		float totScore = 0.f;
		TranspositionEstimator tpe = new TranspositionEstimator(w);
		for (FilePair fp : pairs) {
			totScore += evalWOnProfiles(fp.getFirstProfile(), fp.getSecondProfile(), tpe, fp.firstTransp-fp.secondTransp);
			totScore += evalWOnProfiles(fp.getSecondProfile(), fp.getFirstProfile(), tpe, fp.secondTransp-fp.firstTransp);
		}
		return totScore;
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
		// random initial weights
		float[] bestWeights = new float[12];
		for (int i = 0; i < bestWeights.length; i++)
			bestWeights[i] = random.nextFloat();
		float bestScore = evaluate(bestWeights, pairs);
		System.out.println("initial random weights have score " + bestScore);
		// iterations
		for (int it = 0; it < EPOCHS; it++) {
			// evaluate a new moveset
			List<float[]> moveset = getMoveSet(bestWeights);
			List<Float> scores = new LinkedList<Float>();
			for (float[] move : moveset)
				scores.add(evaluate(move, pairs));
			// get max and eventually move
			float bs = Collections.max(scores);
			if (bs > bestScore) {
				bestScore = bs;
				bestWeights = moveset.get(scores.indexOf(bs));
				System.out.println(String.format("new best found [%f]: %s", bestScore, printArray(bestWeights)));
			}
		}
		// output best
		System.out.println(String.format("THE END - best with score %f: %s", bestScore, printArray(bestWeights)));

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
