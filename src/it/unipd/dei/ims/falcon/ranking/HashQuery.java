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
import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.ToStringUtils;

/**
 * A Query that matches segments containing an hash. 
 * This query is a modified version of a
 * {@link org.apache.lucene.search.TermQuery}
 * Different HashQuery's can be combined in a
 * {@link it.unipd.dei.ims.falcol.ranking.SegmentQuery}.
 * 
 */
public class HashQuery extends Query {

	private static final long serialVersionUID = 1L;
	private Term term;
	private int qtf;
	private int querySegmentLength;
	private float docsSegmentNorm;

	private class HashWeight extends Weight {

		private static final long serialVersionUID = 1L;
		private Similarity similarity;
		private float value;

		public HashWeight(Searcher searcher, int qtf) throws IOException {
			this.similarity = getSimilarity(searcher);
			value = qtf;
		}

		@Override
		public String toString() {
			return "weight(" + HashQuery.this + ")";
		}

		@Override
		public Query getQuery() {
			return HashQuery.this;
		}

		@Override
		public float getValue() {
			return value;
		}

		@Override
		public float sumOfSquaredWeights() {
			return 1;
		}

		@Override
		public void normalize(float queryNorm) {
			value *= queryNorm;
		}

		@Override
		public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder, boolean topScorer) throws IOException {
			TermDocs termDocs = reader.termDocs(term);

			if (termDocs == null)
				return null;

			return new HashScorer(this, termDocs, similarity, querySegmentLength, docsSegmentNorm);
		}

		@Override
		public Explanation explain(IndexReader reader, int i) throws IOException {
			throw new UnsupportedOperationException("Not supported yet.");
		}
	}

	public HashQuery(Term t, int tf) {
		term = t;
		qtf = tf;
	}

	public HashQuery(Term t, int tf, int segmentLength) {
		term = t;
		qtf = tf;
		querySegmentLength = segmentLength;
	}

	public HashQuery(Term t, int tf, int querySegmentLength, float docsSegmentNorm) {
		term = t;
		qtf = tf;
		this.querySegmentLength = querySegmentLength;
		this.docsSegmentNorm = docsSegmentNorm;
	}

	@Override
	public Weight createWeight(Searcher searcher) throws IOException {
		return new HashWeight(searcher, qtf);
	}

	/** Prints a user-readable version of this query. */
	@Override
	public String toString(String field) {
		StringBuilder buffer = new StringBuilder();
		if (!term.field().equals(field)) {
			buffer.append(term.field());
			buffer.append(":");
		}
		buffer.append(term.text());
		buffer.append("[");
		buffer.append(qtf);
		buffer.append("]");
		buffer.append(ToStringUtils.boost(getBoost()));
		return buffer.toString();
	}
}
