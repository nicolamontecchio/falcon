package it.unipd.dei.ims.falcon;

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

import static java.util.Arrays.asList;

import it.unipd.dei.ims.falcon.analysis.chromafeatures.ChromaMatrixUtils;
import it.unipd.dei.ims.falcon.analysis.transposition.TranspositionEstimator;
import it.unipd.dei.ims.falcon.analysis.transposition.TranspositionFileEntry;
import it.unipd.dei.ims.falcon.analysis.transposition.TranspositionFileParser;
import it.unipd.dei.ims.falcon.indexing.Indexing;
import it.unipd.dei.ims.falcon.indexing.IndexingException;
import it.unipd.dei.ims.falcon.ranking.DocScorePair;
import it.unipd.dei.ims.falcon.ranking.QueryMethods;
import it.unipd.dei.ims.falcon.ranking.QueryParser;
import it.unipd.dei.ims.falcon.ranking.QueryParsingException;
import it.unipd.dei.ims.falcon.ranking.QueryPruningStrategy;
import it.unipd.dei.ims.falcon.ranking.StaticQueryPruningStrategy;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

// TODO add the options for avoiding transposition when converting directories

/**
 * Handle all the command-line operations.
 *
 */
public class CmdLine {

    public static final String cmdline_notice = "--\nWelcome to FALCON\n"
            + "FAst Lucene-based Cover sOng identificatioN\n--\n"
            + "To print out the complete list of command line options, "
            + "use the --help switch.\nSee the FALCON website for a quick "
            + "usage tutorial:\nhttp://ims.dei.unipd.it/falcon";

    public static void main(String[] args) throws IOException, QueryParsingException {

        System.out.println(cmdline_notice);
        // command line processing stuff
        // -----------------------------------------------------------
        OptionParser parser = new OptionParser() {

            {
                acceptsAll(asList("h", "help", "?"), "display help");
                acceptsAll(asList("s", "scores"), "show full score lists");
                acceptsAll(asList("ip", "indexpath")).withRequiredArg().ofType(String.class);
                acceptsAll(asList("qp", "querypath")).withRequiredArg().ofType(String.class);
                acceptsAll(asList("dp", "datapath")).withRequiredArg().ofType(String.class);
                acceptsAll(asList("cql"), "chroma quantization levels").withRequiredArg().ofType(Integer.class);
                acceptsAll(asList("cd", "convertdir"), "convert files in data path to hash " + "representation").withRequiredArg().ofType(String.class).describedAs("output base directory");
                acceptsAll(asList("hps", "hashperseg")).withRequiredArg().ofType(Integer.class);
                acceptsAll(asList("overlap")).withRequiredArg().ofType(Integer.class);
                acceptsAll(asList("lambda")).withRequiredArg().ofType(Float.class);
                acceptsAll(asList("tf", "transpfile")).withRequiredArg().ofType(String.class);
                acceptsAll(asList("nt", "ntransp"),
                        "number of transpositions to try when querying, default 3 (at most)").withRequiredArg().ofType(
                        Integer.class);
                acceptsAll(asList("a", "action")).withRequiredArg().ofType(String.class);
                acceptsAll(
                        asList("qps", "querypruningstrategy"),
                        "query pruning strategy - see doc. for example, do not specify "
                        + "anything for default strategy").withOptionalArg().ofType(String.class);
            }
        };
        OptionSet cmdline_options = parser.parse(args);
        if (cmdline_options.has("help")) {
            parser.printHelpOn(System.out);
            return;
        }

        // Action Switch
        // ---------------------------------------------------------------
        if (!cmdline_options.has("action")) {
            throw new IllegalArgumentException("no action specified");
        } else {

            System.out.println("-------------------------------");
            for (String arg : args) {
                System.out.print(arg + " ");
            }
            System.out.println();
            System.out.println("-------------------------------");

            if (cmdline_options.valueOf("action").equals("indexing")) {
                try {
                    String indexPath = (String) cmdline_options.valueOf("indexpath");
                    String dataPath = (String) cmdline_options.valueOf("datapath");
                    int hashPerSegment = (Integer) cmdline_options.valueOf("hashperseg");
                    int segmentOverlap = (Integer) cmdline_options.valueOf("overlap");
                    Indexing.index(indexPath, dataPath, hashPerSegment, segmentOverlap);
                    
                } catch (IndexingException ex) {
                    Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (cmdline_options.valueOf("action").equals("ranking")) {
                String indexPath = (String) cmdline_options.valueOf("indexpath");
                String dataPath = (String) cmdline_options.valueOf("querypath");
                int hashPerSegment = (Integer) cmdline_options.valueOf("hashperseg");
                int segmentOverlap = (Integer) cmdline_options.valueOf("overlap");
                if (cmdline_options.has("lambda")) {
                    QueryMethods.setLambda((Float) cmdline_options.valueOf("lambda"));
                }
                // query pruning strategy (default value, can be overridden or
                // set to null if option is not given)
                String ss = "ntf:0.340765*[0.001694,0.995720];ndf:0.344143*[0.007224,0.997113];"
                        + "ncf:0.338766*[0.001601,0.995038];nmf:0.331577*[0.002352,0.997884];";
                if (cmdline_options.has("qps")) {
                    if (cmdline_options.valueOf("qps") != null) {
                        ss = (String) cmdline_options.valueOf("qps");
                    }
                } else {
                    ss = null;
                }
                QueryPruningStrategy hqps = ss != null ? new StaticQueryPruningStrategy(ss) : null;
                if (hqps == null) {
                    System.out.println("no hqps used");
                } else {
                    System.out.println(String.format("hqps used: %s", hqps.toString()));
                }
                QueryParser qParser = new QueryParser(hqps);
                qParser.loadQueryPruningHashFeatures(indexPath);
                // Obtain the list of file used as query

                long initTime = System.currentTimeMillis();
                TranspositionFileEntry[] tfes = null;
                if ((String) cmdline_options.valueOf("transpfile") != null) {
                    tfes = TranspositionFileParser.getTranspositionFileContents(new File((String) cmdline_options.valueOf("transpfile")));
                }
                LinkedList<Integer> ranks = new LinkedList<Integer>();

                // load file names in 0/ subdirectory
                String[] filenames = (new File(dataPath, "0")).list();
                int ntransp = cmdline_options.has("ntransp") ? (Integer) cmdline_options.valueOf("ntransp") : 3;

                for (String f : filenames) {
                    // is the file in the transpositions file? otherwise skip
                    TranspositionFileEntry tfe = null; // for the current query
                    boolean skip = false;
                    if (tfes != null) {
                        skip = true;
                        for (TranspositionFileEntry e : tfes) {
                            if (e.getQueryFileName().equals(f.replaceAll(".mp3.txt", ""))) {
                                skip = false;
                                tfe = e;
                                break;
                            }
                        }
                    } else {
                        if (f.startsWith(".")) {
                            skip = true;
                        }
                    }
                    // do the query
                    if (!skip) {
                        // load all the files (at most ntransp) related to the
                        // query
                        long queryInitTime = System.currentTimeMillis();
                        System.out.println(String.format("processing %s", f));
                        List<File> queryFilesList = new LinkedList<File>();
                        for (int i = 0; i < ntransp; i++) {
                            File subdir = new File(dataPath, String.format("%d", i));
                            if (subdir.exists()) {
                                File subfile = new File(subdir, f);
                                if (subfile.exists()) {
                                    queryFilesList.add(subfile);
                                }
                            }
                        }
                        // perform the query
                        Map<String, Double> songid2finalscore = QueryMethods.performQuery(queryFilesList, new File(
                                indexPath), hashPerSegment, segmentOverlap, hqps);
                        // compute the rank
                        if (tfe != null) {
                            int r = 1;
                            for (DocScorePair p : DocScorePair.docscore2scoredoc(songid2finalscore)) {
                                if (p.getDoc().replaceAll(".mp3.txt", "").equals(tfe.getCollFileName())) {
                                    break;
                                }
                                r++;
                            }
                            System.out.println(String.format("   matching rank: %3d", r));
                            ranks.add(r);
                        }
                        if (cmdline_options.has("scores")) {
                            // print the sorted scores
                            int r = 1;
                            for (DocScorePair p : DocScorePair.docscore2scoredoc(songid2finalscore)) {
                                System.out.println(String.format("rank %3d: %10.6f - %s", r++, p.getScore(), p.getDoc()));
                            }
                        }
                        System.out.println("   query time: " + (System.currentTimeMillis() - queryInitTime) + " ms");
                    }
                }
                System.out.println("Total Query time: " + (System.currentTimeMillis() - initTime) + " [ms]");
                if (!ranks.isEmpty()) {
                    System.out.println("ranking positions");
                    for (Integer r : ranks) {
                        System.out.print(String.format("%3d, ", r));
                    }
                    System.out.println();
                    double mrr = 0;
                    for (Integer r : ranks) {
                        mrr += 1. / r;
                    }
                    mrr /= ranks.size();
                    System.out.println(String.format("Final MRR: %f", mrr));
                    if (qParser.isPruningEnabled()) {
                        double pruned_hash_ratio =
                                1. * qParser.getPrunedHashInQuerySession() / qParser.getTotalHashInQuerySession();
                        System.out.println("Total Hash: "+qParser.getTotalHashInQuerySession());
                        System.out.println("Pruned Hash: "+qParser.getPrunedHashInQuerySession());
                        System.out.println(String.format("Pruned/Total Hash: %f", pruned_hash_ratio));

                    }
                }
            } else if (cmdline_options.valueOf("action").equals("show_doc_ids")
                    || cmdline_options.valueOf("action").equals("show_seg_ids")
                    || cmdline_options.valueOf("action").equals("show_full_index")) {
                try {
                    String indexPath = (String) cmdline_options.valueOf("indexpath");
                    Indexing.indexUtils(indexPath, cmdline_options.valueOf("action").toString());
                } catch (IndexingException ex) {
                    Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (cmdline_options.valueOf("action").equals("convert")) {
                File inputdir = new File((String) cmdline_options.valueOf("datapath"));
                File outBaseDir = new File((String) cmdline_options.valueOf("convertdir"));
                int nranks = 3;
                double minkurtosis = 3.;
                TranspositionEstimator te = new TranspositionEstimator(3, null);
                int ntransp = (Integer) cmdline_options.valueOf("ntransp");
                ChromaMatrixUtils.convertdir(inputdir, outBaseDir, nranks, minkurtosis, te, ntransp);

            }
        }
    }
}
