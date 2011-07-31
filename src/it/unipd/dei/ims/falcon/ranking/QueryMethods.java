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
import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaMatrixUtils;
import it.unipd.dei.ims.falcon.analysis.transposition.TranspositionEstimator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	// cache which stores for each lucene identifier, namely for each
	// segment, the TITLE of the song which contains that segment
	private static Map<Integer, String> docId2songidCache;

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
			if (!songid2maxscore.containsKey(stringId))
				songid2maxscore.put(stringId, new Double(sd.score));
			r++;
		}
		return songid2maxscore;
	}
	// the following objects are initialized when the first query is performed
	private static IndexReader reader = null;
	private static IndexSearcher searcher = null;

	// TODO copy more or less the doc from the git repository 
	public static Map<String, Double> query(final InputStream query, File index, final int hps, final int overlap, int nranks, final TranspositionEstimator tpe, int ntransp, final double minkurt, QueryPruningStrategy pruningStrategy) throws IOException, QueryParsingException, InterruptedException {
		
		if (reader == null) {
			reader = IndexReader.open(new SimpleFSDirectory(index));
		}
		if (searcher == null) {
			searcher = new IndexSearcher(reader);
			searcher.setSimilarity(new HashSimilarity());
		}
		ExecutorService tpool = Executors.newCachedThreadPool();
		final List<OutputStream> os = new LinkedList<OutputStream>();               // ntransp streams of integer hashes
		final List<Map<String, Double>> allTranspRes = Collections.synchronizedList(new LinkedList<Map<String, Double>>());

		// enqueue ntransp extractQuery
		for (int i = 0; i < (tpe == null ? 1 : ntransp); i++) {
			PipedOutputStream po1 = new PipedOutputStream();
			os.add(po1);
			final QueryParser queryParser = new QueryParser(pruningStrategy);
			queryParser.loadQueryPruningHashFeatures(index.getPath());
			queryParser.setDocumentSegmentLength(new Integer(reader.document(0).getField("LENGTH").stringValue()));
			Map<String, Double> songid2finalscore = new TreeMap<String, Double>();
			final PipedInputStream pi2 = new PipedInputStream(po1);
			tpool.submit(new Runnable() {
				public void run() {
					try {
						System.out.println("performQuery - runnable A");
						queryParser.extractQuery(pi2, hps, overlap);
						System.out.println("performQuery - runnable A | query extracted");
						System.out.println("performQuery - runnable A | total segments: " + queryParser.getNumberOfSegments());
						Map<String, Double> songid2finalscore = new TreeMap<String, Double>();
						
						for (int i = 0; i < queryParser.getNumberOfSegments(); i++) {
//							System.out.println("performQuery - runnable A | segment " + i + " of " + queryParser.getNumberOfSegments());
							Query query = queryParser.getQueryFromSegment(i);
							TopDocs td = searcher.search(query, reader.numDocs());
							Map<String, Double> songid2maxscore = reduceMaxScoreForEachSong(td, searcher);
							for (String songid : songid2maxscore.keySet()) {
								Double currentscore = songid2finalscore.get(songid);
								if (currentscore == null)
									currentscore = 1.;
								currentscore *= Math.pow(songid2maxscore.get(songid), 1. / queryParser.getNumberOfSegments());
								songid2finalscore.put(songid, currentscore);
							}
						}
						System.out.println("performQuery - runnable A | out of loop");
						allTranspRes.add(songid2finalscore);
						System.out.println("performQuery - runnable A | added to global res");
					} catch (IOException ex) {
						Logger.getLogger(QueryMethods.class.getName()).log(Level.SEVERE, null, ex);
					} catch (QueryParsingException ex) {
						Logger.getLogger(QueryMethods.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			});
		}

		// enqueue conversion from initial input stream into integer hashes seq. (note that 'os' must be already constructed)
		tpool.submit(new Runnable() {
			public void run() {
				try {
					System.out.println("performQuery - runnable B");
					ChromaMatrixUtils.convertStream(new InputStreamReader(query), os, 3, tpe, minkurt);
					System.out.println("performQuery - runnable B ended");
				} catch (IOException ex) {
					Logger.getLogger(QueryMethods.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});

		
		// wait for all to complete and merge results
		Map<String, Double> finalRes = new TreeMap<String, Double>();
		tpool.shutdown();
		tpool.awaitTermination(1000, TimeUnit.DAYS);
		
		System.out.println("performQuery - about to merge results");
			
		for (Map<String, Double> singlequeryres : allTranspRes) {
			for (Entry<String, Double> e : singlequeryres.entrySet()) {
				if (!finalRes.containsKey(e.getKey()) || e.getValue() > finalRes.get(e.getKey())) {
					finalRes.put(e.getKey(), e.getValue());
				}
			}
		}
		return finalRes;
	}
}
