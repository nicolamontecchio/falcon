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

	private double kurtosisThreshold;
	private float[] weights;

	/**
	 * constructor
	 * 
	 * @param kurtosisThreshold
	 *            minimum kurtosis for considering the chroma vector in the
	 *            final sum
	 * @param weights
	 *            if null, use default values
	 */
	public TranspositionEstimator(double kurtosisThreshold, float[] weights) {
		this.kurtosisThreshold = kurtosisThreshold;
		if (weights == null) {
			this.weights = new float[12];
			this.weights[0] = .057483513f;
			this.weights[1] = .0f;
			this.weights[2] = .35149592f;
			this.weights[3] = .1756003f;
			this.weights[4] = .3790565f;
			this.weights[5] = .11772308f;
			this.weights[6] = .36675265f;
			this.weights[7] = .37078834f;
			this.weights[8] = .2812164f;
			this.weights[9] = .2189098f;
			this.weights[10] = .0f;
			this.weights[11] = .53457695f;
		} else {
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
	}

	/**
	 * find the most probable traspositions for the given profile
	 * 
	 * @param n
	 *            number of most probable transpositions to return
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
			corr[max] = -100;
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
	private float[] getProfile(ChromaVector[] song) {
		float[] profile = new float[12];
		for (int i = 0; i < 12; i++)
			profile[i] = 0;
		for (ChromaVector v : song)
			if (v.getKurtosis() > kurtosisThreshold)
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
	 * find the nTransp most probable traspositions for the given song
	 * @param nTransp number of transpositions to return
	 */
	public int[] findKey(ChromaVector[] song, int nTransp) {
		// sum all the vectors where kurtosis > threshold
		float sum = 0;
		float[] profile = getProfile(song);
		for (int i = 0; i < 12; i++)
			sum += profile[i];
		if (sum > 0)
			return findKey(new ChromaVector(profile), nTransp);
		else {
			// profile is 0 everywhere (!) ... return random values
			Random rnd = new Random(System.nanoTime());
			int[] fakeTonalities = new int[12];
			for (int i = 0; i < 12; i++)
				fakeTonalities[i] = rnd.nextInt();
			return fakeTonalities;
		}

	}

	/**
	 * return the position of the first match for the tonality estimator,
	 * transposing the document at most one time, and the query at most 12
	 * 
	 * @param transp
	 *            true transposition, read from ground truth file
	 * @return match in the range [0 (best) - 11 (worst)]
	 */
	public int keyMatchPosition(ChromaVector[] q, ChromaVector[] d, int transp) {
		int kD = findKey(d, 1)[0];
		int[] kQ = findKey(q, 12);
		for (int i = 0; i < 12; i++) {
			if ((kQ[i] - transp + 36) % 12 == kD)
				return i;
		}
		return -1; // unreachable statement, needed however by java
	}

}
