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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.SimpleFSDirectory;

// TODO modify in order to allow scoring for segments of different lengths
/**
 * Provides functionalities for performing a query
 * 
 */
public class QueryMethods {

    // smoothing parameter to combine rank and score
    private static float lambda = 1;  // TODO important remove everything related to lambda, as it was never used
    // cache which stores for each lucene identifier, namely for each
    // segment, the TITLE of the song which contains that segment
    private static Map<Integer, String> docId2songidCache;

    public static void setLambda(float lambda) {
        QueryMethods.lambda = lambda;
    }

    /**
     * Given a set of documents with multiple scores, retain the max score for
     * each document
     *
     * @param topdocs
     *            Lucene TopDocs; documents, namely segments, are ranked by
     *            score. The score of a segment is the sum of the score of its
     *            constituting hashes, specifically obtained by
     *            {@link it.unipd.dei.ims.falcon.ranking.SegmentQuery}
     * @param searcher
     *            Lucene {@link org.apache.lucene.search.IndexSearcher}
     * @return
     * @throws IOException
     */
    private static Map<String, Double> reduceMaxScoreForEachSong(TopDocs topdocs, IndexSearcher searcher)
            throws IOException {
        if (docId2songidCache == null) {
            // TODO although this should work, it has not been checked for concurrency issues
            docId2songidCache = new ConcurrentHashMap<Integer, String>();
        }
        Map<String, Double> songid2maxscore = new TreeMap<String, Double>();
        int r = 1;
        for (ScoreDoc sd : topdocs.scoreDocs) {
            String stringId = docId2songidCache.get(sd.doc);
            if (stringId == null) {
                stringId = searcher.doc(sd.doc).getField("TITLE").stringValue();
                docId2songidCache.put(sd.doc, stringId);
            }
            if (!songid2maxscore.containsKey(stringId)) {
                songid2maxscore.put(stringId, new Double(lambda * sd.score + (1 - lambda) * Math.sqrt(1. / r)));
            }
            r++;
        }
        return songid2maxscore;
    }
    // the following objects are initialized when the first query is performed
    private static IndexReader reader = null;
    private static QueryParser qParser = null;
    private static IndexSearcher searcher = null;

    /**
     * Perform a query, in the most basic form: read from a plain text file
     * where the hash values have already been computed, do not perform any kind
     * of transposition.
     *
     * @param queryFile
     *            plain text file containing the query hash values
     * @param indexDir
     *            path to the index directory
     * @param hashPerSegment
     *            number of hashes to consider per segment (typically, chroma
     *            vectors have an hopsize of 256/44100 s and hashPerSegment =
     *            2000)
     * @param segmentOverlap
     *            typically 0
     * @param pruningStrategy
     *            can be null
     * @throws IOException
     * @throws QueryParsingException
     * @return a map from the document title (typically the mp3 file name) to
     *         the similarity score
     */
    public static Map<String, Double> performQuery(File queryFile, File indexDir, int hashPerSegment,
            int segmentOverlap, QueryPruningStrategy pruningStrategy) throws IOException, QueryParsingException {

        if (reader == null) {
            reader = IndexReader.open(new SimpleFSDirectory(indexDir));
        }
        if (searcher == null) {
            searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new HashSimilarity());
        }
        if (qParser == null) {
            qParser = new QueryParser(pruningStrategy);
            qParser.loadQueryPruningHashFeatures(indexDir.getPath());
            qParser.setDocumentSegmentLength(new Integer(reader.document(0).getField("LENGTH").stringValue()));
        }

        Map<String, Double> songid2finalscore = new TreeMap<String, Double>();
        qParser.extractQuery(queryFile, hashPerSegment, segmentOverlap);
        for (int i = 0; i < qParser.getNumberOfSegments(); i++) {
            Query query = qParser.getQueryFromSegment(i);
            TopDocs td = searcher.search(query, reader.numDocs());
            Map<String, Double> songid2maxscore = reduceMaxScoreForEachSong(td, searcher);
            for (String songid : songid2maxscore.keySet()) {
                Double currentscore = songid2finalscore.get(songid);
                if (currentscore == null) {
                    currentscore = 1.;
                }
                currentscore *= Math.pow(songid2maxscore.get(songid), 1. / qParser.getNumberOfSegments());
                songid2finalscore.put(songid, currentscore);
            }
        }
        return songid2finalscore;
    }

    /**
     * Perform a query with multiple input files; results present only the
     * maximum score for each song retrieved. Use this method for performing a
     * query with transpositions. For parameters see
     * {@link #performQuery(File, File, int, int, QueryPruningStrategy)}.
     */
    public static Map<String, Double> performQuery(Collection<File> queryFiles, File indexDir, int hashPerSegment,
            int segmentOverlap, QueryPruningStrategy pruningStrategy) throws IOException, QueryParsingException {
        Map<String, Double> songid2finalscore = new TreeMap<String, Double>();
        for (File f : queryFiles) {
            Map<String, Double> singlequeryres = performQuery(f, indexDir, hashPerSegment, segmentOverlap,
                    pruningStrategy);
            for (Entry<String, Double> e : singlequeryres.entrySet()) {
                if (!songid2finalscore.containsKey(e.getKey()) || e.getValue() > songid2finalscore.get(e.getKey())) {
                    songid2finalscore.put(e.getKey(), e.getValue());
                }
            }
        }
        return songid2finalscore;
    }
}
