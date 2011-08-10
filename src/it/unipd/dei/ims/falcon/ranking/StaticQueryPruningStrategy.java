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
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defines a strategy for pruning hashes in the query
 * using predefined intervals on the features, like
 * term frequency, document frequency, max frequency in the collection,
 * total frequency in the collection.
 * 
 */
public class StaticQueryPruningStrategy implements QueryPruningStrategy {

	/** helper class */
	private class Interval {

		private double low;
		private double high;

		public Interval(double l, double h) {
			if (l < 0 || l > 1 || h < 0 || h > 1 || l > h)
				throw new IllegalArgumentException("invalid values for interval bounds");
			low = l;
			high = h;
		}

		public boolean inside(double v) {
			return v >= low && v <= high;
		}

		@Override
		public String toString() {
			return String.format("[%f,%f]", low, high);
		}
	}
	// intervals-weights for normalized [term,document,collection,max] frequency
	private double wt, wd, wc, wm;
	private Interval it, id, ic, im;

	private enum INTERVAL_TYPE {
		NTF, NDF, NCF, NMF
	}

	/**
	 * @param s
	 *            specifies the hash query pruning strategy, in the same format
	 *            as the toString() method. Can be null (hashes never get
	 *            pruned)
	 * */
	public StaticQueryPruningStrategy(String s) {
		Pattern p = Pattern.compile("\\d\\.{0,1}\\d*");
		LinkedList<Double> tokens = new LinkedList<Double>();
		Matcher m = p.matcher(s);
		while (m.find())
			tokens.addLast(new Double(s.substring(m.start(), m.end())));
		if (tokens.size() != 12)
			throw new IllegalArgumentException("invalid format for query pruning strategy option");
		addInterval(INTERVAL_TYPE.NTF, tokens.pollFirst(), tokens.pollFirst(), tokens.pollFirst());
		addInterval(INTERVAL_TYPE.NDF, tokens.pollFirst(), tokens.pollFirst(), tokens.pollFirst());
		addInterval(INTERVAL_TYPE.NCF, tokens.pollFirst(), tokens.pollFirst(), tokens.pollFirst());
		addInterval(INTERVAL_TYPE.NMF, tokens.pollFirst(), tokens.pollFirst(), tokens.pollFirst());
	}

	/** @return true if hash should be retained, based on current features */
	private boolean retainHash(int hash, double ntf, HashStats hs) {
		double score = 0;
		score += it.inside(ntf) ? wt : 0;
		score += id.inside(hs.getNdf()) ? wd : 0;
		score += ic.inside(hs.getNcf()) ? wc : 0;
		score += im.inside(hs.getNmf()) ? wm : 0;
		return score > 1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * it.unipd.dei.ims.mirlucene.ranking.QueryPruningStrategy#pruneHash(int,
	 * double, it.unipd.dei.ims.mirlucene.ranking.HashStats)
	 */
	public boolean pruneHash(int hash, double ntf, HashStats hs) {
		return !retainHash(hash, ntf, hs);
	}

	// add an interval with a specific weight - note that 0 <= low < high <= 1
	// otherwise an exception gets thrown
	private void addInterval(INTERVAL_TYPE type, double weight, double low, double high) {
		Interval i = new Interval(low, high);
		switch (type) {
			case NTF:
				it = i;
				wt = weight;
				break;
			case NDF:
				id = i;
				wd = weight;
				break;
			case NCF:
				ic = i;
				wc = weight;
				break;
			case NMF:
				im = i;
				wm = weight;
				break;
			default:
				throw new IllegalArgumentException("invalid interval type specified");
		}
	}

	@Override
	/** print out in pretty form */
	public String toString() {
		String s = "";
		s += String.format("ntf:%f*%s;", wt, it.toString());
		s += String.format("ndf:%f*%s;", wd, id.toString());
		s += String.format("ncf:%f*%s;", wc, ic.toString());
		s += String.format("nmf:%f*%s;", wm, im.toString());
		return s;
	}
}
