package it.unipd.dei.ims.falcon.analysis.transposition;

/**
 * Copyright 2010 University of Padova, Italy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaVector;

import java.util.Random;

/**
 * Estimates the most probable key given a set of weights and a reference
 * threshold
 */
public class TranspositionEstimator {

	private float[] weights;

	public float[] getWeights() {
		return weights.clone();
	}
	
	

	/**
	 * constructor
	 * 
	 * @param kurtosisThreshold
	 *            minimum kurtosis for considering the chroma vector in the
	 *            final sum
	 * @param weights
	 *            if null, use default values
	 */
	public TranspositionEstimator(float[] weights) {
		// TODO does this need to be normalized
		if (weights.length != 12)
			throw new IllegalArgumentException("weights.length != 12");
		float sumSquare = 0;
		for (int i = 0; i < 12; i++)
			sumSquare += weights[i] * weights[i];
		if (sumSquare == 0) // just in case ...
			for (int i = 0; i < 12; i++)
				weights[i] = 1f / 12f;
		else
			for (int i = 0; i < 12; i++)
				weights[i] /= (float) Math.sqrt(sumSquare);
		this.weights = weights;
	}

	/**
	 * Find the most probable traspositions for the given profile
	 * @param n   number of most probable transpositions to return
	 */
	public int[] findKey(ChromaVector profile, int n) {
		// compute the correlations
		float[] corr = new float[12];
		for (int i = 0; i < 12; i++) {
			corr[i] = 0;
			for (int j = 0; j < 12; j++)
				corr[i] += profile.getChromaValues()[j] * weights[j];
			profile.rotate(1);
		}
		// sort the best n
		int[] transp = new int[n];
		for (int i = 0; i < n; i++) {
			int max = 0;
			for (int j = 0; j < 12; j++)
				if (corr[j] > corr[max])
					max = j;
			transp[i] = max;
			corr[max] = Float.MIN_VALUE;
		}
		return transp;
	}

	/**
	 * Extract a profile from a whole song, represented as a Chroma matrix, by
	 * summing the chroma vectors for which kurtosis is higher than the
	 * threshold set in the constructor
	 * 
	 * @return A chroma vector as float[]
	 */
	public static float[] getProfile(ChromaVector[] song) {
		float[] profile = new float[12];
		for (int i = 0; i < 12; i++)
			profile[i] = 0;
		for (ChromaVector v : song)
			for (int i = 0; i < 12; i++)
				profile[i] += v.getChromaValues()[i];
		float e = 0;
		for (float f : profile)
			e += f * f;
		e = (float) Math.sqrt(e);
		for (int i = 0; i < 12; i++)
			profile[i] /= e;
		return profile;
	}

	/**
	 * Find the nTransp most probable traspositions for the given song
	 * @param nTransp number of transpositions to return
	 */
	public int[] findKey(ChromaVector[] song, int nTransp) {
		return findKey(new ChromaVector(getProfile(song)), nTransp);
	}
}
