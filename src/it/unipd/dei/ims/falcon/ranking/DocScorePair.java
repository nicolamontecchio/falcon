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

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

public class DocScorePair implements Comparable<DocScorePair> {
	private String doc;
	private double score;

	public String getDoc() {
		return doc;
	}

	public double getScore() {
		return score;
	}

	public int compareTo(DocScorePair o) {
		if (getScore() < o.getScore())
			return 1;
		if (getScore() > o.getScore())
			return -1;
		return getDoc().compareTo(o.getDoc());
	}

	public DocScorePair(String d, double s) {
		setDoc(d);
		setScore(s);
	}

	// / convert a map[docid->score] to a sorted set
	public static SortedSet<DocScorePair> docscore2scoredoc(Map<String, Double> map) {
		SortedSet<DocScorePair> scores = new TreeSet<DocScorePair>();
		for (Entry<String, Double> e : map.entrySet())
			scores.add(new DocScorePair(e.getKey(), e.getValue()));
		return scores;
	}

	public void setDoc(String doc) {
		this.doc = doc;
	}

	public void setScore(double score) {
		this.score = score;
	}
}