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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * Utility class containing methods for reading/writing Chroma feature matrices.
 * 
 */
public class ChromaMatrixUtils {

	/**
	 * Read a matrix from a file. File format: rows \n columns \n [;-separated chroma values \n ] 
	 * 
	 * @param f
	 *            input file
	 * @param subsampling
	 *            read only a row every n
	 */
	private static float[][] readMatrixFromFile(File f, int subsampling) throws IOException {
		if (!f.canRead())
			throw new IOException("can't read file");
		// format: columns\n rows\n data
		BufferedReader reader = new BufferedReader(new FileReader(f));
		Integer cols = null;
		Integer rows = null;
		LinkedList<float[]> rowList = new LinkedList<float[]>();
		int lineNo = 1;
		int i = 0;
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			if (cols == null)
				try {
					cols = new Integer(line);
				} catch (NumberFormatException e) {
					throw new IOException("invalid columns number");
				}
			else if (rows == null) {
				try {
					rows = new Integer(line);
				} catch (NumberFormatException e) {
					throw new IOException("invalid rows number");
				}
			} else {
				if (i % subsampling == 0) {
					StringTokenizer st = new StringTokenizer(line, ";");
					if (st.countTokens() != cols)
						throw new IOException("invalid data line in file " + f.getAbsolutePath() + ": " + lineNo);
					float[] row = new float[st.countTokens()];
					if (row.length != cols)
						throw new IOException("invalid number of columns at line " + lineNo);
					int j = 0;
					while (st.hasMoreTokens()) {
						try {
							row[j++] = Float.parseFloat(st.nextToken());
						} catch (NumberFormatException e) {
							throw new IOException("invalid number, line: " + lineNo);
						}
					}
					double rowsum = 0;
					for (int q = 0; q < row.length; q++)
						rowsum += row[q];
					if (rowsum > 0)
						rowList.add(row);
				}
				i++;
			}
			lineNo++;
		}
		if (i < rows)
			throw new IOException("missing rows");
		else if (i > rows)
			throw new IOException("too many rows");
		int n = rowList.size();
		float[][] matrix = new float[rowList.size()][cols];
		for (int k = 0; k < n; k++) {
			float[] row = rowList.removeFirst();
			for (int h = 0; h < cols; h++) {
				matrix[k][h] = row[h];
			}
		}
		return matrix;
	}

	/**
	 * Read a chroma matrix from a features file
	 * 
	 * @param f
	 *            input file
	 * @param subsampling
	 *            read one row every <code>subsampling</code>
	 */
	public static ChromaVector[] readChromaMatrixFromFile(File f, int subsampling) throws IOException {
		float[][] matrix = readMatrixFromFile(f, subsampling);
		int rows = matrix.length;
		ChromaVector[] cv = new ChromaVector[rows];
		for (int i = 0; i < rows; i++)
			cv[i] = new ChromaVector(matrix[i]);
		return cv;
	}

	/**
	 * Convert a file from a chroma matrix representation to a hash
	 * representation. Output is written in subdirectories 0, 1, ..,
	 * <code>ntransp-1</code> if <code>transpEst</code> is not null, 0
	 * otherwise.
	 * 
	 * @param f
	 *            input file (chroma matrix)
	 * @param dirOut
	 *            output directory, subdirectories are created if necessary
	 * @param nranks
	 *            chroma quantization level
	 * @param minkurtosis
	 *            kurtosis threshold for hash output (chroma vectors with
	 *            smaller kurtosis get assigned a hash = -1)
	 * @param transpEst
	 *            transposition estimator, if null no transposition is performed
	 * @param ntransp
	 *            number of transpositions to output
	 * @throws IOException
	 */
	public static void convertFile(File f, File dirOut, int nranks, double minkurtosis,
			TranspositionEstimator transpEst, int ntransp) throws IOException {
		ChromaVector[] c = ChromaMatrixUtils.readChromaMatrixFromFile(f, 1);
		// init to 0-transp if no transposition estimator specified
		int[] keys = transpEst != null ? transpEst.findKey(c, ntransp) : new int[1];
		for (int k = 0; k < keys.length; k++) {
			// incremental in-place rotation
			int transp = keys[k];
			for (int j = 0; j < k; j++)
				transp -= keys[j];
			transp = transp % 12;
			for (int i = 0; i < c.length; i++)
				c[i].rotate(transp);
			// transform into hash representation
			Integer[] h = new Integer[c.length];
			for (int i = 0; i < c.length; i++) {
				if (c[i].getKurtosis() >= minkurtosis)
					h[i] = c[i].rankRepresentation(nranks);
				else
					h[i] = -1;
			}
			// eventually create output dir, then write file
			File subDirOut = new File(dirOut, String.format("%d", k));
			if (!subDirOut.exists())
				subDirOut.mkdir();
			BufferedWriter out = new BufferedWriter(new FileWriter(new File(subDirOut, f.getName())));
			for (Integer i : h)
				out.write("" + i + " ");
			out.close();
		}
	}

	/**
	 * Convert a directory of chroma matrices into hash representation.
	 * Basically calls {@link convertFile(File, File, int, double,
	 * TranspositionEstimator, int)} for each file in the directory.
	 * 
	 * @return a list of file that were not processed (typically, files
	 *         beginning with '.')
	 */
	public static Collection<File> convertdir(File dirIn, File dirOut, int nranks, double minkurtosis,
			TranspositionEstimator te, int ntransp) throws IOException {
		if (!(dirIn.isDirectory() && dirOut.isDirectory()))
			throw new IOException("invalid input or output dirs");
		File[] inFiles = dirIn.listFiles();
		Collection<File> skippedFiles = new LinkedList<File>();
		for (File f : inFiles) {
			if (!f.getName().startsWith(".")) {
				convertFile(f, dirOut, nranks, minkurtosis, te, ntransp);
			} else {
				skippedFiles.add(f);
			}
		}
		return skippedFiles;
	}

}
