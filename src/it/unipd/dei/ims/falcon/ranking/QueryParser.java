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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

/**
 * Provides functionalities to extract segments for the song in input.
 * The songs needs to be represented as a sequence of hashes, actually the 
 * result of the analysis phase. The input file is a text file where the hash
 * sequence is reported in a single line.
 * <p>
 * The {@link it.unipd.dei.ims.falcon.ranking.QueryParser#extractQuery}
 * method divides the sequence in possibly overlapping segments of fixed size.
 * Both the size of the segment and the size of the overlap are specified as
 * input parameters. Each subsequence of hashes is represented as a sequence
 * of distinct hashes together with their occurrence in the segment. In other
 * words a bag of features representation is obtained for each segment, and
 * it is maintained in a
 * {@link it.unipd.dei.ims.falcon.ranking.QueryParser.SegmentBagOfFeatures}.
 * Each query is then represented as a list of segment bag of features
 * representations.
 * <p>
 * In order to search the index for the most promising segments matching a
 * specific segment of the query, the method
 * {@link it.unipd.dei.ims.falcon.ranking.QueryParser#getQueryFromSegment(int)}
 * can be adopted to obtain a {@link it.unipd.dei.ims.falcon.ranking.SegmentQuery}
 * which is a logical OR of {@link it.unipd.dei.ims.falcon.ranking.HashQuery}.
 * <p>
 * In order to reduce the computational issue due to the possibly high number
 * of hashes per segment, when building the query for the segment some hashes
 * can be pruned. The specific strategy adopted is that specified by
 * {@link it.unipd.dei.ims.falcon.ranking.QueryPruningStrategy}.
 * The query pruning strategy uses a set of features to discriminate between
 * hashes to prune and hashes to retain. The features are those stored in the
 * file "qpruning_features.map" during the indexing phase (the file is stored 
 * in the index folder). The features are loaded in an {@link java.util.Map} 
 * (hash to features map) by the method
 * {@link it.unipd.dei.ims.falcon.ranking.QueryParser#loadQueryPruningHashFeatures(java.lang.String) }.
 * <p>
 * Methods {@link it.unipd.dei.ims.falcon.ranking.QueryParser#getTotalHashInQuerySession()}
 * and {@link it.unipd.dei.ims.falcon.ranking.QueryParser#getPrunedHashInQuerySession()}
 * return respectively the total number of hash extracted and the total number
 * of pruned hash for all the queries in an evaluation session.
 * An evaluation session is constituted by a set of queries using the same
 * instance of {@link it.unipd.dei.ims.falcon.ranking.QueryParser}.
 * <p>
 *
 * @see it.unipd.dei.ims.falcon.ranking.HashQuery
 * @see it.unipd.dei.ims.falcon.ranking.SegmentQuery
 * @see it.unipd.dei.ims.falcon.ranking.QueryPruningStrategy
 * 
 */
public class QueryParser {

    // list of bag of features representation for the segments extracted from
    // the query
    private List<SegmentBagOfFeatures> querySegments;
    // map of the feature to describe each hash
    private Map<Integer, HashStats> hashFeatureMap;
    // strategy adopted to prune hash in each segment of the query
    private QueryPruningStrategy pruningStrategy;
    // if true, query pruning is enabled
    private boolean pruning_enabled;
    // total number of hashes pruned when during an evaluation session
    // where the same query parser is used
    private static long prunedHash;
    // total number of hashes should be used to build a the segments queries
    // for the queries considered during an evaluation session using the same
    // query parser
    private static long totalHash;
    // number of hashes which constitute a segment of the query
    private int querySegmentLength;
    // number of hashes in a segment of a document in the index
    private float docsSegmentNorm;

    /**
     * Creates a query parser with a specific strategy for query pruning 
     * The total number of hashes should be evaluated without pruning and
     * and the total number of pruned hash is set to zero when a new instance
     * of query parser is created.
     *
     * @param pruningStrategy
     *          specific strategy adopted for query pruning
     */
    public QueryParser(QueryPruningStrategy pruningStrategy) {
        this.pruningStrategy = pruningStrategy;
        this.pruning_enabled = pruningStrategy != null;
        prunedHash = 0; // set to zero the number of pruned hash in this query session
        totalHash = 0;  // set to zero the total number of hash in this query session
    }

    /**
     * Bag of features representation for a segment
     */
    class SegmentBagOfFeatures {

        // map from hashes appearing in the segment to frequency of 
        // occurrence in the segment
        private HashMap<Integer, Integer> segmentBagOfFeatures;

        /**
         * Creates a bag of feature representation for the current segment.
         * Requires a map from the hashes to their identifiers in the segment
         * and the occurrence frequency of the hashes in the segment
         *
         * @param hashID_localPointers_map
         *              map from hash values to their identifiers in the segment
         * @param hash_freq_per_segment
         *              
         */
        public SegmentBagOfFeatures(TreeMap<Integer, Integer> hashID_localPointers_map, int[] hash_freq_per_segment) {
            segmentBagOfFeatures = new HashMap<Integer, Integer>();
            Set<Integer> hashIDs = hashID_localPointers_map.keySet();
            for (int h : hashIDs) {
                segmentBagOfFeatures.put(h, hash_freq_per_segment[hashID_localPointers_map.get(h)]);
            }
        }

        /**
         * Returns the frequency of occurrence of an hash in the segment
         * @param hash_ID
         *          identifier of the hash
         * @return
         *          frequency of occurrence of the specified hash
         */
        public int getHashFrequency(int hash_ID) {
            return this.segmentBagOfFeatures.get(hash_ID);
        }

        /**
         * Returns the distinct hashes in the segment
         *
         * @return  the distinct hash appearing in the segment
         */
        public Set<Integer> getDistinctHashInSegment() {
            return this.segmentBagOfFeatures.keySet();
        }

        /**
         * Returns the number of distinct hash in the segment
         *
         * @return  number of distinct hash in the segment
         */
        public int getNumberOfDistinctHash() {
            return this.segmentBagOfFeatures.size();
        }
    }

    /**
     * Returns the number of segment in the query
     *
     * @return number of segment in the query
     */
    public int getNumberOfSegments() {
        return this.querySegments.size();
    }

    /**
     * Extracts segments from the file and put the song in a set of (hash,hash_frequency) items
     *
     * @param queryFile
     *            text file containing the hashes in the song
     * @param hashPerSegment
     *            number of hash per segment
     * @param hashInSegmentOverlap
     *            number of hash in the segment overlap
     * @throws QueryParsingException
     */
		@Deprecated
    public void extractQuery(File queryFile, int hashPerSegment, int hashInSegmentOverlap) throws QueryParsingException {

        // the number of hash in a segment cannot be equal to the number
        // of hash in the overlap: that will lead to an infinite loop
        if (hashPerSegment == hashInSegmentOverlap) {
            throw new QueryParsingException("Number of hash per segment cannot be equal to the"
                    + "number of hash in the overlap");
        }

        querySegmentLength = hashPerSegment;

        querySegments = new ArrayList<SegmentBagOfFeatures>();

        BufferedReader buffReader = null;
        int cur_hash;

        // cache of hashes adopted to manage segment overlap
        LinkedList<Integer> hashCache = new LinkedList<Integer>();
        // list of local hash identifiers avaliable after the removal
        // of hash non in the overlap 
        LinkedList<Integer> localHashPointersAvailable = new LinkedList<Integer>();

        try {

            buffReader = new BufferedReader(new FileReader(queryFile));
            // string containing all hashes for the current song
            String content = buffReader.readLine();
            // scanner to extract hash
            Scanner scanner = new Scanner(content);
            // number of segments currently considered for the song being
            // processed
            int hashSegment = 0;
            // number of hash
            int curHashInSegment = 0;
            // map from the HASH to the ID of the hash in the currently
            // considered segment
            TreeMap<Integer, Integer> hashID_localPointer_map = new TreeMap<Integer, Integer>();

            int[] hash_freq_per_segment = new int[hashPerSegment];
            // identifier of the hash in the segment
            int localHashPointer = 0;

            while (scanner.hasNext()) {
                // current considered hash
                cur_hash = Integer.parseInt(scanner.next());
                if (cur_hash == -1) {
                    continue;
                }
                // add current hash to the cache
                hashCache.add(cur_hash);

                curHashInSegment++;
                // update local map for segment hash IDs
                if (hashID_localPointer_map.containsKey(cur_hash)) {
                    localHashPointer = hashID_localPointer_map.get(cur_hash);
                } else {
                    if (localHashPointersAvailable.isEmpty()) {
                        localHashPointer = hashID_localPointer_map.size();
                    } else {
                        localHashPointer = localHashPointersAvailable.poll();
                        assert !hashID_localPointer_map.containsValue(localHashPointer);
                    }
                    hashID_localPointer_map.put(cur_hash, localHashPointer);
                }
                // increment the frequency of occurrence of the current hash
                hash_freq_per_segment[localHashPointer]++;
                // if we are at the end of the segment
                if (hashPerSegment - curHashInSegment == 0) {
                    // add the posting lists for the distinct hashes in the
                    // current segment to the list of posting lists for the
                    // entire song
                    querySegments.add(new SegmentBagOfFeatures(hashID_localPointer_map, hash_freq_per_segment));
                    // store the number of distinct hashes in this segment
                    // segmentLengths.add(hashID_localPointer_map.size());
                    // identifier for the next segment
                    hashSegment++;

                    if (hashInSegmentOverlap == 0) {
                        // create a new vector for segment hash frequencies
                        hash_freq_per_segment = new int[hashPerSegment];
                        // create a new set for distinct hashes
                        hashID_localPointer_map = new TreeMap<Integer, Integer>();
                        // init the identifier of the hash for the next segment
                        localHashPointer = 0;
                        // set to zero the number of hash for the next segment
                        curHashInSegment = 0;
                    } else {
                        for (int h = 0; h < hashPerSegment - hashInSegmentOverlap; h++) {
                            int curHashToRemove = hashCache.poll();
                            int curHashLocaID = hashID_localPointer_map.get(curHashToRemove);
                            // decrease the frequency for the current hash
                            // to be removed
                            hash_freq_per_segment[curHashLocaID]--;
                            if (hash_freq_per_segment[curHashLocaID] == 0) {
                                localHashPointersAvailable.add(curHashLocaID);
                                hashID_localPointer_map.remove(curHashToRemove);
                            }
                        }
                        curHashInSegment = hashInSegmentOverlap;
                    }
                }
            }

        } catch (IOException ex) {
            throw new QueryParsingException("IOException: Error during query extraction");
        } finally {
            try {
                buffReader.close();
            } catch (IOException ex) {
                Logger.getLogger(QueryParser.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

		/**
     * Extracts segments from the file and put the song in a set of (hash,hash_frequency) items
     *
     * @param queryFile
     *            text stream containing the hashes in the song
     * @param hashPerSegment
     *            number of hash per segment
     * @param hashInSegmentOverlap
     *            number of hash in the segment overlap
     * @throws QueryParsingException
     */
		public void extractQuery(InputStream query, int hashPerSegment, int hashInSegmentOverlap) throws QueryParsingException {

        // the number of hash in a segment cannot be equal to the number
        // of hash in the overlap: that will lead to an infinite loop
        if (hashPerSegment == hashInSegmentOverlap) {
            throw new QueryParsingException("Number of hash per segment cannot be equal to the"
                    + "number of hash in the overlap");
        }

        querySegmentLength = hashPerSegment;

        querySegments = new ArrayList<SegmentBagOfFeatures>();

        BufferedReader buffReader = null;
        int cur_hash;

        // cache of hashes adopted to manage segment overlap
        LinkedList<Integer> hashCache = new LinkedList<Integer>();
        // list of local hash identifiers avaliable after the removal
        // of hash non in the overlap 
        LinkedList<Integer> localHashPointersAvailable = new LinkedList<Integer>();

        try {

            buffReader = new BufferedReader(new InputStreamReader(query));
            // string containing all hashes for the current song
            String content = buffReader.readLine();
            // scanner to extract hash
            Scanner scanner = new Scanner(content);
            // number of segments currently considered for the song being
            // processed
            int hashSegment = 0;
            // number of hash
            int curHashInSegment = 0;
            // map from the HASH to the ID of the hash in the currently
            // considered segment
            TreeMap<Integer, Integer> hashID_localPointer_map = new TreeMap<Integer, Integer>();

            int[] hash_freq_per_segment = new int[hashPerSegment];
            // identifier of the hash in the segment
            int localHashPointer = 0;

            while (scanner.hasNext()) {
                // current considered hash
                cur_hash = Integer.parseInt(scanner.next());
                if (cur_hash == -1) {
                    continue;
                }
                // add current hash to the cache
                hashCache.add(cur_hash);

                curHashInSegment++;
                // update local map for segment hash IDs
                if (hashID_localPointer_map.containsKey(cur_hash)) {
                    localHashPointer = hashID_localPointer_map.get(cur_hash);
                } else {
                    if (localHashPointersAvailable.isEmpty()) {
                        localHashPointer = hashID_localPointer_map.size();
                    } else {
                        localHashPointer = localHashPointersAvailable.poll();
                        assert !hashID_localPointer_map.containsValue(localHashPointer);
                    }
                    hashID_localPointer_map.put(cur_hash, localHashPointer);
                }
                // increment the frequency of occurrence of the current hash
                hash_freq_per_segment[localHashPointer]++;
                // if we are at the end of the segment
                if (hashPerSegment - curHashInSegment == 0) {
                    // add the posting lists for the distinct hashes in the
                    // current segment to the list of posting lists for the
                    // entire song
                    querySegments.add(new SegmentBagOfFeatures(hashID_localPointer_map, hash_freq_per_segment));
                    // store the number of distinct hashes in this segment
                    // segmentLengths.add(hashID_localPointer_map.size());
                    // identifier for the next segment
                    hashSegment++;

                    if (hashInSegmentOverlap == 0) {
                        // create a new vector for segment hash frequencies
                        hash_freq_per_segment = new int[hashPerSegment];
                        // create a new set for distinct hashes
                        hashID_localPointer_map = new TreeMap<Integer, Integer>();
                        // init the identifier of the hash for the next segment
                        localHashPointer = 0;
                        // set to zero the number of hash for the next segment
                        curHashInSegment = 0;
                    } else {
                        for (int h = 0; h < hashPerSegment - hashInSegmentOverlap; h++) {
                            int curHashToRemove = hashCache.poll();
                            int curHashLocaID = hashID_localPointer_map.get(curHashToRemove);
                            // decrease the frequency for the current hash
                            // to be removed
                            hash_freq_per_segment[curHashLocaID]--;
                            if (hash_freq_per_segment[curHashLocaID] == 0) {
                                localHashPointersAvailable.add(curHashLocaID);
                                hashID_localPointer_map.remove(curHashToRemove);
                            }
                        }
                        curHashInSegment = hashInSegmentOverlap;
                    }
                }
            }

        } catch (IOException ex) {
            throw new QueryParsingException("IOException: Error during query extraction");
        } finally {
            try {
                buffReader.close();
            } catch (IOException ex) {
                Logger.getLogger(QueryParser.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
				
    }
		
		
		
    /**
     * Returns a {@link SegmentQuery} from the segment "segmentNumber" 
     *
     * @param segmentNumber
     *            number of the segment of the query song
     * @return {@link SegmentQuery} from the segment at the specified segmentNumber
     */
    public Query getQueryFromSegment(int segmentNumber) {

        // creates an empty query
        SegmentQuery query = new SegmentQuery(querySegmentLength);
        // gets the bag of features representation for the segment with
        // identifier "segmentNumber"
        SegmentBagOfFeatures segmentBagOfFeatures = querySegments.get(segmentNumber);

        HashStats hashStats;
        int hashFreq;

        for (int hash : segmentBagOfFeatures.getDistinctHashInSegment()) {
            totalHash++;    // increment total number of hash
            // get collection statistics for current hash
            hashStats = this.hashFeatureMap.get(hash);
            // decomment for query length normalization //
            // segmentLengths.get(segmentNumber)
            hashFreq = segmentBagOfFeatures.getHashFrequency(hash);

            // check if the hash can be pruned
            if (pruning_enabled && pruningStrategy.pruneHash(hash, (double) hashFreq / querySegmentLength, hashStats)) {
                prunedHash++;
                continue;
            } else {    //  otherwise create a query
                query.add(
                        new HashQuery(
                        new Term("CONTENT", Integer.toString(hash)),
                        segmentBagOfFeatures.getHashFrequency(hash),
                        querySegmentLength,docsSegmentNorm));
            }
        }
        return query;
    }

    /**
     * Enables query pruning
     */
    public void enablePruning() {
        this.pruning_enabled = true;
    }

    /**
     * Loads the hash features adopted for query pruning
     *
     * @param indexPath
     *              full path to the folder where the index is stored
     * @throws FileNotFoundException
     */
    public void loadQueryPruningHashFeatures(String indexPath) throws FileNotFoundException {
        // create the hash-to-features map
        hashFeatureMap = new TreeMap<Integer, HashStats>();

        Scanner scan = new Scanner(new File(new File(indexPath).getAbsolutePath() + "/qpruning_features.map"));

        while (scan.hasNextLine()) {
            // each line is an entry of the hashmap
            Scanner lineScan = new Scanner(scan.nextLine());
            // the first element in the line is the key
            int key = Integer.parseInt(lineScan.next());
            // the remaining entries are the values associated to the above key
            HashStats hStats = new HashStats(
                    Double.parseDouble(lineScan.next()),
                    Double.parseDouble(lineScan.next()),
                    Double.parseDouble(lineScan.next()));
            hashFeatureMap.put(key, hStats);
        }
    }

    /**
     * Returns the total number of hashes considered in the evaluation session
     * 
     * @return  total number of hashes in the current query evaluation session
     */
    public long getTotalHashInQuerySession() {
        return totalHash;
    }

    /**
     * Returns the number of pruned hashes in the evaluation session
     *
     * @return  total number of pruned hashes in the current query evaluation session
     */
    public long getPrunedHashInQuerySession() {
        return prunedHash;
    }

    /**
     * Returns true is query pruning is enabled, false otherwise
     * 
     * @return  true if query pruning is enabled, false otherwise
     */
    public boolean isPruningEnabled() {
        return this.pruning_enabled;
    }

    /**
     * Sets the length of each segment
     * @param documentsSegmentLength    length of document segments
     */
    public void setDocumentSegmentLength(int documentsSegmentLength){
        docsSegmentNorm = 1.0f/documentsSegmentLength;
    }
}
