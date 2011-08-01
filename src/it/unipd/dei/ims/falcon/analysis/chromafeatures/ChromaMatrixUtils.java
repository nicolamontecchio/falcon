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
import it.unipd.dei.ims.falcon.analysis.transposition.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Utility class containing methods for reading/writing Chroma feature matrices.
 * 
 */
public class ChromaMatrixUtils {

	public static ChromaVector[] readChromaMatrixFromStream(InputStreamReader is, int subsampling) throws IOException, NumberFormatException {
		List<ChromaVector> l = new LinkedList<ChromaVector>();
		BufferedReader in = new BufferedReader(is);
		String line = null;
		while ((line = in.readLine()) != null) {
			StringTokenizer tok = new StringTokenizer(line.trim(), ",");
			if (tok.countTokens() == 12) {
				float[] v = new float[tok.countTokens()];
				int j = 0;
				while (tok.hasMoreTokens())
					v[j++] = Float.parseFloat(tok.nextToken());
				float vsum = 0;
				for (float vi : v)
					vsum += vi;
				if (vsum > 0) // do not add zero vectors
					l.add(new ChromaVector(v));
			}
		}
		return l.toArray(new ChromaVector[0]);
	}

	private static void convertChromaMatrix(ChromaVector[] c, OutputStream os,
					int nranks, double minkurtosis) throws IOException {
		// transform into hash representation
		Integer[] h = new Integer[c.length];
		for (int i = 0; i < c.length; i++) {
			if (c[i].getKurtosis() >= minkurtosis) {
				h[i] = c[i].rankRepresentation(nranks);
			} else {
				h[i] = -1;
			}
		}
		// eventually create output dir, then write file
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
		for (Integer i : h)
			writer.write("" + i + " ");
		writer.close();
	}

	/**
	 * Concert a chroma matrix stream into integer hashes streams.
	 * The number of transposition used is equal to the lenght of 
	 * the list os.
	 * @param is input stream containing a chroma matrix in text format
	 * @param os list of output streams where the integer sequence should be written (as String)
	 * @param nranks quantization level
	 * @param transpEst instance of transposition estimator algorithm. No transposition is performed if this parameter is null.
	 * @param minkurtosis kurtosis threshold for considering a chroma vector
	 * @throws IOException 
	 */
	public static void convertStream(InputStreamReader is, List<OutputStream> os,
					int nranks, TranspositionEstimator transpEst,
					double minkurtosis) throws IOException {
		ChromaVector[] c = ChromaMatrixUtils.readChromaMatrixFromStream(is, 1);
		// init to 0-transp if no transposition estimator specified
		int[] keys = transpEst != null ? transpEst.findKey(c, os.size()) : new int[]{0};
		for (int k = 0; k < keys.length; k++) {
			// incremental in-place rotation
			int transp = keys[k];
			for (int j = 0; j < k; j++) {
				transp -= keys[j];
			}
			transp = transp % 12;
			for (int i = 0; i < c.length; i++)
				c[i].rotate(transp);
			// conversion
			convertChromaMatrix(c, os.get(k), nranks, minkurtosis);
			for(OutputStream o : os) o.flush();
		}
	}
}
