package it.unipd.dei.ims.falcon.ranking;

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
 * Define statistics for a term in the collection
 */
public class HashStats {

	// normalized [document,collection,max] frequency values
	private double ndf, ncf, nmf;

	public HashStats() {
	}

	public HashStats(double d, double c, double m) {
		ndf = d;
		ncf = c;
		nmf = m;
	}

	public double getNcf() {
		return ncf;
	}

	public double getNdf() {
		return ndf;
	}

	public double getNmf() {
		return nmf;
	}

	@Override
	public String toString() {
		return String.format("ndf=%f,ncf=%f,nmf=%f", ndf, ncf, nmf);
	}

}
