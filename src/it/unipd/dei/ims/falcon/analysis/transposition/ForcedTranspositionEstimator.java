package it.unipd.dei.ims.falcon.analysis.transposition;

import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaVector;

/**
 * Dummy transposition estimator algorithm. 
 * Returns a predetermined value.
 * @author Nicola Montecchio
 */
public class ForcedTranspositionEstimator extends TranspositionEstimator {

	private int transp = 0;

	public ForcedTranspositionEstimator(int t) {
		super(new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
		transp = t;
	}

	private int[] dumbres(int n) {
		int[] res = new int[n];
		for (int i = 0; i < n; i++)
			res[i] = transp;
		return res;
	}

	@Override
	public int[] findKey(ChromaVector profile, int n) {
		return dumbres(n);
	}

	@Override
	public int[] findKey(ChromaVector[] song, int nTransp) {
		return dumbres(nTransp);
	}
}
