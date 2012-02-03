package it.unipd.dei.ims.falcon.analysis.chromafeatures;

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

/**
 * Represents a Chroma Vector (stores values s.t. norm_2 = 1)
 */
public class ChromaVector {

	private float[] chromavalues = null;
	private float energy;
	// precomputed values (when not null)
	private Integer rankRep = null;
	private double kurtosis;
	private double mean;
	private double var;

	/**
	 * constructor - automatically normalizes values with 2-norm
	 * @param values
	 */
	public ChromaVector(float[] values) {
		chromavalues = new float[12];
		float sum = 0;
		energy = 0;
		for (int i = 0; i < 12; i++) {
			float v = values[i];
			chromavalues[i] = v;
			energy += v * v;
			sum += v;
		}
		float norm2 = (float) Math.sqrt(energy);
		if (sum > 0)
			for (int i = 0; i < 12; i++)
				chromavalues[i] /= norm2;
		else
			throw new IllegalArgumentException("zero vector is not allowed");
		// compute mean, variance and kurtosis (without subtracting 3)
		double k = 0;
		mean = 0;
		for (int i = 0; i < 12; i++)
			mean += chromavalues[i];
		mean /= 12;
		var = 0;
		for (int i = 0; i < 12; i++)
			var += (chromavalues[i] - mean) * (chromavalues[i] - mean);
		var /= 11;
		for (int i = 0; i < 12; i++) {
			double x = (chromavalues[i] - mean);
			x *= x;
			x *= x;
			k += x;
		}
		k /= (12 * var * var);
		kurtosis = k;
	}

	public float[] getChromaValues() {
		return chromavalues;
	}

	/**
	 * compute a rank-based representation
	 * 
	 * @param k
	 *            max ranks to consider - NOTE MUST BE leq 7
	 * @param npeaks
	 *            actual number of ranks to use
	 */
	public int rankRepresentation(int k, int npeaks) {
		int rep = 0;
		float[] chromacopy = new float[chromavalues.length];
		for (int i = 0; i < chromavalues.length; i++)
			chromacopy[i] = chromavalues[i];
		for (int i = 0; i < npeaks; i++) {
			// find i-th largest
			int largest = 0;
			for (int j = 0; j < chromacopy.length; j++)
				if (chromacopy[j] > chromacopy[largest])
					largest = j;
			// add to the representation
			rep += (largest + 1) * Integer.rotateLeft(1, 4 * (k - i - 1));
			chromacopy[largest] = -1;
		}
		rankRep = rep;
		return rankRep;
	}

	/**
	 * @return the rank representation - or null if it hasn't already been
	 *         computed
	 */
	public Integer getRankRep() {
		return rankRep;
	}

	/**
	 * compute a rank-based representation
	 * 
	 * @param k
	 *            ranks to consider - NOTE MUST BE leq 7 (otherwise more than 32
	 *            bits, 7 and not 8 beacuse of sign)
	 */
	public int rankRepresentation(int k) {
		return rankRepresentation(k, k);
	}

	/**
	 * compute a rank based representation; the number of peaks used depends on
	 * kurtosis value;
	 * 
	 * @param intervals
	 *            intervals on which kurtosis value is taken - max number of
	 *            peaks = intervals.length+1 used when kurtosis is between -inf
	 *            and intervals[0]; maxnumberofpeaks-1 when kurtosis between
	 *            intervals[0] ad itervals[0]+intervals[1] ...
	 * @return
	 */
	public int rankRepresentation(double[] intervals) {
		if (intervals.length == 0)
			return rankRepresentation(1);
		int npeaks = howmanypeaks(kurtosis, intervals);
		return rankRepresentation(intervals.length + 1, npeaks);
	}

	/**
	 * determine how many peaks should be used, based on a kurtosis value and
	 * given intervals
	 */
	public static int howmanypeaks(double kurtosis, double[] intervals) {
		double thresh = intervals[0];
		int i = 0;
		while (kurtosis > thresh && i < intervals.length - 1)
			thresh += intervals[++i];
		int npeaks = kurtosis > thresh ? intervals.length - i : intervals.length - i + 1;
		return npeaks;
	}

	/** @return kurtosis */
	public double getKurtosis() {
		return kurtosis;
	}

	/** @return the energy that the vector had before normalization */
	public float getEnergy() {
		return energy;
	}

	/** rotate (to the left) the chroma values */
	public void rotate(int n) {
		// dirty code, but works
		n = -n;
		n = (n + 12) % 12;
		float[] tmp = new float[12];
		for (int i = 0; i < 12; i++)
			tmp[i] = chromavalues[(i - n + 12) % 12];
		chromavalues = tmp;
	}

	@Override
	public String toString() {
		String s = "";
		for (int i = 0; i < 11; i++)
			s += chromavalues[i] + " ";
		s += chromavalues[11];
		return s;
	}

}
