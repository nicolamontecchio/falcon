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

import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Weight;

/**
 * Specific implementation of {@link org.apache.lucene.search.Scorer} for an {@link HashQuery}.
 * This class compute the minimum between the frequency of occurrence of an hash
 * in the query and its occurrence in the document segments
 */
final class HashScorer extends Scorer {

    @SuppressWarnings("unused")
    private static final float[] SIM_NORM_DECODER = Similarity.getNormDecoder();
    private Weight weight;
    private TermDocs termDocs;
    @SuppressWarnings("unused")
    private byte[] norms;
    private float weightValue;
    private int doc = -1;
    private final int[] docs = new int[32]; // buffered doc numbers
    private final int[] freqs = new int[32]; // buffered term freqs
    private int pointer;
    private int pointerMax;
    private int querySegmentLength;
    private float docsSegmentNorm;

    /**
     * Creates a specific {@link org.apache.lucene.search.Scorer} for the current {@link HashQuery}
     * 
     * @param weight
     *          {@link org.apache.lucene.search.Weight} for the current {@link HashQuery}
     * @param td
     *          {@link org.apache.lucene.index.TermDocs} for the current hash
     * @param similarity
     *          {@link org.apache.lucene.search.Similarity}
     * @param norms
     *          array with document norms
     * @param segmentLength
     *          number of hashes per query segment
     */
    HashScorer(Weight weight, TermDocs td, Similarity similarity, byte[] norms, int segmentLength) {
        super(similarity);
        this.weight = weight;
        this.termDocs = td;
        this.norms = norms;
        this.weightValue = weight.getValue();
        this.querySegmentLength = segmentLength;

    }

    /**
     * Creates a specific {@link org.apache.lucene.search.Scorer} for the current {@link HashQuery}
     *
     * @param weight
     *          {@link org.apache.lucene.search.Weight} for the current {@link HashQuery}
     * @param td
     *          {@link org.apache.lucene.index.TermDocs} for the current hash
     * @param similarity
     *          {@link org.apache.lucene.search.Similarity}
     * @param querySegmentLength
     *          number of hash per query segment
     * @param docsSegmentNorm
     *          document norm, i.e. reciprocal of the document segment length
     */
    HashScorer(Weight weight, TermDocs td, Similarity similarity, int querySegmentLength, float docsSegmentNorm) {
        super(similarity);
        this.weight = weight;
        this.termDocs = td;
        this.weightValue = weight.getValue();
        this.querySegmentLength = querySegmentLength;
        this.docsSegmentNorm = docsSegmentNorm;

    }

    @Override
    public void score(Collector c) throws IOException {
        score(c, Integer.MAX_VALUE, nextDoc());
    }

    @Override
    protected boolean score(Collector c, int end, int firstDocID) throws IOException {
        c.setScorer(this);
        while (doc < end) { // for docs in window
            c.collect(doc); // collect score
            if (++pointer >= pointerMax) {
                pointerMax = termDocs.read(docs, freqs); // refill buffers
                if (pointerMax != 0) {
                    pointer = 0;
                } else {
                    termDocs.close(); // close stream
                    doc = Integer.MAX_VALUE; // set to sentinel value
                    return false;
                }
            }
            doc = docs[pointer];
        }
        return true;
    }

    @Override
    public int docID() {
        return doc;
    }

    @Override
    public int nextDoc() throws IOException {
        pointer++;
        if (pointer >= pointerMax) {
            pointerMax = termDocs.read(docs, freqs); // refill buffer
            if (pointerMax != 0) {
                pointer = 0;
            } else {
                termDocs.close(); // close stream
                return doc = NO_MORE_DOCS;
            }
        }
        doc = docs[pointer];
        return doc;
    }

    @Override
    /*
     * Modified version of {@link org.apache.lucene.search#score}
     * the compute the score for the current hash as the minimum between
     * the frequency of occurrence of the hash is the document segment and
     * its frequency in the query segment
     */
    public float score() {
        assert doc != -1;
        int f = freqs[pointer];
        // float norm_dtf = norms == null ? f*docsSegmentNorm : f *
        // SIM_NORM_DECODER[norms[doc] & 0xFF];
        float norm_dtf = f * docsSegmentNorm;
        float norm_qtf = 1.0f * weightValue / querySegmentLength;
        return Math.min(norm_dtf, norm_qtf);
    }

    @Override
    public int advance(int target) throws IOException {
        // first scan in cache
        for (pointer++; pointer < pointerMax; pointer++) {
            if (docs[pointer] >= target) {
                return doc = docs[pointer];
            }
        }
        // not found in cache, seek underlying stream
        boolean result = termDocs.skipTo(target);
        if (result) {
            pointerMax = 1;
            pointer = 0;
            docs[pointer] = doc = termDocs.doc();
            freqs[pointer] = termDocs.freq();
        } else {
            doc = NO_MORE_DOCS;
        }
        return doc;
    }

    @Override

    public String toString() {
        return "scorer(" + weight + ")";
    }
}
