package it.unipd.dei.ims.falcon.indexing;

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

import it.unipd.dei.ims.falcon.ranking.HashSimilarity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Scanner;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;

/**
 * Indexing class provides functionalities to index songs in a specified folder.
 * Each song needs to be represented as a text file, whose content is the
 * sequence of hashes extracted during the analysis phase. The entire hash sequence
 * for the song needs to be in a single text line. 
 * <p>
 * The basic rationale underlying the indexing step is that each song is mapped
 * in a set of possible overlapping subsequence of hashes of fixed length.
 * Each subsequence is named "segment". Both the number of hashes per segment,
 * namely the segment length, and the number of hashes in the overlap, namely
 * the overlap size, are specified as parameters for the method
 * {@link it.unipd.dei.ims.falcon.indexing.Indexing#index(java.lang.String, java.lang.String, int, int)}.
 * together with the full path to the folder containing the song collection and
 * the full path to the folder where the index will be stored.
 * <p>
 * Each obtained segment is mapped in a Lucene {@link org.apache.lucene.document.Document}
 * and written in the index. Each segment {@link org.apache.lucene.document.Document}
 * has three Lucene {@link org.apache.lucene.document.Field}'s:
 * <ul>
 *  <li> "CONTENT": sequence of hashes for the current segments; a white space
 *       is adopted as hash delimiter; the configuration currently used for this
 *       {@link org.apache.lucene.document.Field} is:
 *       <ul>
 *          <li>{@link org.apache.lucene.document.Field.Store#NO}
 *          <li>{@link org.apache.lucene.document.Field.Index#ANALYZED}
 *          <li>{@link org.apache.lucene.document.Field.TermVector#NO}
 *        </ul>
 *  <li> "TITLE": identifier of the song which the segment belongs to, e.g. "song2".
 *       The configuration currently used for this {@link org.apache.lucene.document.Field} is:
 *       <ul>
 *          <li>{@link org.apache.lucene.document.Field.Store#YES}
 *          <li>{@link org.apache.lucene.document.Field.Index#NOT_ANALYZED_NO_NORMS}
 *       </ul>
 *  <li> "ID": identifier of the segment; for instance the identifier "song2_4"
 *       denotes the fourth segment of the song "song2". The configuration
 *       currently used for this {@link org.apache.lucene.document.Field} is:
 *       <ul>
 *          <li>{@link org.apache.lucene.document.Field.Store#YES}
 *          <li>{@link org.apache.lucene.document.Field.Index#NOT_ANALYZED_NO_NORMS}
 *       </ul>
 * </ul>
 * If the total number of hashes in a segment is not a multiple of the specified
 * number of hashes per segment, the remaining part of the sequence is truncated.
 * <p>
 *
 * After indexing, collection wide statistics for each hash are computed and
 * stored in the file "qpruning_features.map" in the index folder.
 * Each line in this file corresponds to a distinct hash and contains four entries:
 * <ol>
 *  <li>the hash value;
 *  <li>the normalized document frequency, that is the number of document,
 *      namely segments, where the hash occurs, divided by the total number
 *      of segments in the index;
 *  <li>the normalized total collection frequency of the hash, that is the total
 *      number of occurrence of the hash in the entire collection divided by
 *      the sum of the total collection frequency of all the distinct hashes;
 *  <li>normalized maximum frequency for the current hash, that is the maximum
 *      value computed over all the segments in the index of the number of
 *      hash occurrence in a segment divided by the segment length.
 * </ol>
 * When the index is updated, the map is re-built.
 * <p>
 * 
 */
public class Indexing {

    /**
     * Indexes all the songs in the specified path.
     * The index is created in the specified directory "indexPath". If an index
     * already exists in that path, adds the songs to the existing index.
     * Each song is processed by the method
     * {@link it.unipd.dei.ims.falcon.indexing.Indexing#indexSong}
     * which maps the song into a set of segments, each of one is mapped in a
     * Lucene {@link org.apache.lucene.document.Document}.
     * The segments have fixed length, specifically are constituted by 
     * "hashPerSegment" hashes. There can be an overlap of "hashInOverlap"
     * hashes between two segments. The number of hash in the overlap must be
     * smaller than the number of hash per segments, otherwise an
     * {@link it.unipd.dei.ims.falcon.indexing.IndexingException} is thrown.
     * <p>
     * Once the index has been created or updated, writes a map into a file.
     * The map associates a set of features to each hash. Those features are
     * based on occurrence statistics of the hash in the entire collection.
     * In the event of an index update the map is re-built and the map file
     * is over-written.
     * 
     * @param indexPath
     *            path to the folder where the index will be stored
     * @param dataPath
     *            path to the collection of files to be indexed
     * @param hashPerSegment
     *            number of hash per segment
     * @param hashInOverlap
     *            number of hash in the overlap between segments
     * @throws IndexingException
     */
    public static void index(String indexPath, String dataPath, int hashPerSegment, int hashInOverlap)
            throws IndexingException {
        // check if the number of hash per segment is smaller than the
        // number of hash in the overlap between segments, otherwise
        // an exception is thrown
        if (hashPerSegment <= hashInOverlap) {
            throw new IndexingException(
                    "Number of hashes in the overlap cannot be equal to "
                    + "the number of hash per segment");
        }

        try {
            // create the directory where the index will be stored,
            // if the directory does not exist
            File idxDir = new File(indexPath);
            if (!idxDir.exists()) {
                idxDir.mkdir();
            }
            // if true, the Lucene IndexWriter creates a new index
            boolean create = true;
            // checks if a Lucene index already exists in the specified folder
            // where the index will be stored
            Directory indexDir = new SimpleFSDirectory(idxDir, null);
            if (IndexReader.indexExists(indexDir)) {
                // if an index already exists, disable new index creation
                create = false;
            }
            // initializes Lucene Analyzer and IndexWriter
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_30);
            IndexWriter writer = new IndexWriter(indexDir, analyzer, create, IndexWriter.MaxFieldLength.UNLIMITED);
            writer.setSimilarity(new HashSimilarity());
            // gets the list of files in the data path
            File[] inputfiles = (new File(dataPath)).listFiles();
            
            for (File file : inputfiles) {
                // if the current considered files exists and is not hidden
                if (file.exists() && !file.getName().startsWith(".")) {
                    System.out.println(file.getName());
                    // add to the index the current song
                    indexSong(writer, file, hashPerSegment, hashInOverlap);
                }
            }
            writer.optimize();  // index optimization
            writer.close();
            // writes hash features into the "qpruning_features.map" in the
            // folder where the index is stored
            PrintWriter pw = new PrintWriter(idxDir.getAbsolutePath() + "/qpruning_features.map");
            // opens the created (or updated) index 
            IndexReader reader = IndexReader.open(new SimpleFSDirectory(new File(indexPath)));
            // gets the total number of distinct segments in the index
            int numSegments = reader.numDocs();
            // gets the total number of hashes in the collection
            long total_hcf = numSegments * hashPerSegment;
            // gets distinct hashes in the collection
            TermEnum hashes = reader.terms();

            while (hashes.next()) {
                if (!hashes.term().field().equals("CONTENT")) {
                    continue;
                }
                Term curHash = hashes.term();
                pw.print(curHash.text() + "\t");
                // prints normalized document frequency
                pw.print((double) reader.docFreq(curHash) / numSegments + "\t");
                // gets posting list for the current hash
                TermDocs curHash_pl = reader.termDocs(curHash);
                // computation of the frequency of the current hash in the
                // entire collection -- value initialization
                long hcf = 0;
                // initializes the normalized maximum frequency value
                double nmf = 0;
                // initializes the normalized frequency for max computation
                double cur_nf = 0;
                // processes posting list entries
                while (curHash_pl.next()) {
                    // computation of the normalized frequency for
                    // the current hash
                    cur_nf = (double) curHash_pl.freq() / hashPerSegment;
                    // update max if necessary
                    if (cur_nf > nmf) {
                        nmf = cur_nf;
                    }
                    hcf += curHash_pl.freq();
                }
                // prints normalized total collection frequency and
                // normalized maximum frequency for the current hash
                pw.print((double) hcf / total_hcf + "\t" + nmf + "\n");
            }
            pw.flush();
            pw.close();
        } catch (FileNotFoundException ex) {
            throw new IndexingException("FileNotFoundException when indexing");
        } catch (IOException ex) {
            throw new IndexingException("IOException when indexing");
        }
    }

    /**
     * Maps the song in the input file in a set of {@link org.apache.lucene.document.Document}'s and index them.
     * Each song is divided in a set of possibly overlapping segments of fixed
     * length; in particular "hashPerSegment" is the number of hash in each
     * segment. The number of hashes in the overlap is "hashInOverlap".
     * If the number of hashes in the last segment is less the "hashPerSegment",
     * this sequence of hashes is discarded.
     * Each segment is mapped in a {@link org.apache.lucene.document.Document}
     * with three {@link org.apache.lucene.document.Field}'s:
     * <ul>
     *  <li> "CONTENT": sequence of hashes in the current segment
     *  <li> "TITLE": title of the file the current segment belongs to
     *  <li> "ID": identifier of the segments, that is the form "TITLE"_i,
     *       where "i" is the consecutive segment number in the song, e.g.
     *       "song2_3" denotes the third segment of the song "song2
     * </ul>
     * 
     * @param writer
     *          {@link org.apache.lucene.index.IndexWriter} for the current index
     * @param strBuilder
     *          {@link java.lang.StringBuilder} where each segment is stored during indexing
     * @param file
     *          file where the text representation of the song is stored
     * @param hashPerSegment
     *          number of hashes in each segment
     * @param hashInOverlap
     *          number of hashes in the overlap among segments
     * @throws FileNotFoundException
     * @throws IOException
     */
    static void indexSong(IndexWriter writer, File file,
            int hashPerSegment, int hashInOverlap) throws FileNotFoundException, IOException {

        BufferedReader buffReader = new BufferedReader(new FileReader(file));
        String content = buffReader.readLine();
        Scanner scanner = new Scanner(content);

        // number of segments in the current document
        int hashSegment = 1;
        // number of hash processed in the current segment
        int curHashInSegment = 0;

        LinkedList<Integer> hashCache = new LinkedList<Integer>();

        StringBuilder strBuilder = new StringBuilder();

        while (scanner.hasNext()) {

            int curHash = Integer.parseInt(scanner.next());
            if (curHash == -1) {
                continue;
            }

            strBuilder.append(curHash).append(" ");
            hashCache.add(curHash);

            curHashInSegment++;

            // number of hash processed and in the buffer equals the number
            // of hash per segment
            if (hashPerSegment - curHashInSegment == 0) {

                // create a Lucene Document for the current segment
                Document doc = new Document();
                // add the field for the content of the document
                // this field will be analyzed and indexed, but not store
                // in its non-parsed form
                doc.add(new Field("CONTENT", strBuilder.toString(), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO));
                // add the field for the identifier of the currently processed song
                doc.add(new Field("TITLE", file.getName(), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
                // add the field for the identifier of the current segment
                doc.add(new Field("ID", file.getName() + "_" + hashSegment, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
                // add a filed to store the length of this segment
                doc.add(new Field("LENGTH", Integer.toString(hashPerSegment), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
                writer.addDocument(doc);

                if (strBuilder.length() > 0) {
                    // creates a new string builder for the next segment
                    strBuilder = new StringBuilder();

                    if (hashInOverlap == 0) { // if the overlap size is zero
                        // the number of segment already processed for the next
                        // segment is zero
                        curHashInSegment = 0;
                    } else {
                        // if overlap is required, removes from the cache
                        // the segments not in the overlap
                        for (int h = 0; h < hashPerSegment - hashInOverlap; h++) {
                            hashCache.poll();
                        }
                        // fills the buffer with the hashes in the overlap
                        int h = 0;
                        Iterator<Integer> iter = hashCache.iterator();
                        while (iter.hasNext() && h < hashInOverlap) {
                            strBuilder.append(iter.next()).append(" ");
                            h++;
                        }
                        // update the number of hashes currently processed for
                        // the next segment to the number of hashes in the overlap
                        // since they are in common for the two segments
                        curHashInSegment = hashInOverlap;
                    }
                }
                // increases the number of segments for the current song
                hashSegment++;
            }
        }
    }

    /**
     * Prints information on the songs stored in the index in the specified path.
     * The specific information printed is that specified by the "option".
     * Available options are:
     * <ul>
     *  <li> "show_doc_ids": prints the internal index identifier of all the
     *       segments in the index together with the title of the song which
     *       the segment belongs to;
     *  <li> "show_seg_ids": prints the internal index identifier of all the
     *       segments in the index together with the segment identifier;
     *  <li> "show_full_index": print all the distinct hashes in the index
     *       and the posting list associated to each hash     *
     * </ul>
     *  
     * @param indexPath
     *                  full path to the folder where the index is stored
     * @param option
     *                  option which specified the requested information
     *                  
     * @throws IndexingException
     */
    public static void indexUtils(String indexPath, String option) throws IndexingException {
        IndexReader reader;
        try {
            reader = IndexReader.open(new SimpleFSDirectory(new File(indexPath), null));

            if (option.equals("show_doc_ids")) {
                //  prints all the internal segment identifiers together with
                //  the title of the song of the considered segment.
                //  For instance, "[6] song2" denotes that the segment with
                //  internal identifier "6" belongs to the song with title "song2"
                for (int d = 0; d < reader.numDocs(); d++) {
                    System.out.println("[" + d + "] " + reader.document(d).getField("TITLE").stringValue());
                }
            } else if (option.equals("show_seg_ids")) {
                //  prints all the internal segment identifiers together with
                //  the identifier of the segment.
                //  For instance, "[8] song2_3" denotes that the third segment
                //  of "song2" has internal identifier "8
                for (int d = 0; d < reader.numDocs(); d++) {
                    System.out.println("[" + d + "] " + reader.document(d).getField("ID").stringValue());
                }

            } else if (option.equals("show_full_index")) {
                // print the full index, that is each hash with the associated
                // posting list
                TermEnum terms = reader.terms();
                while (terms.next()) {
                    System.out.print(terms.term() + " [SF: " + terms.docFreq() + "] <");

                    TermPositions poss = reader.termPositions(terms.term());
                    while (poss.next()) {
                        System.out.print(" " + reader.document(poss.doc()).getField("ID").stringValue() + " ("
                                + poss.freq() + "), ");
                    }
                    System.out.println(">");
                }
            }
        } catch (CorruptIndexException ex) {
            throw new IndexingException("CorruptIndexException when accessing index for printing information");
        } catch (IOException ex) {
            throw new IndexingException("IOException when accessing index for printing information");
        }
    }
}
