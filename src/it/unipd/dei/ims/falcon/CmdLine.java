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
 */
public class CmdLine {

	public static final String cmdline_notice = "--\nWelcome to FALCON\n"
			+ "FAst Lucene-based Cover sOng identificatioN\n--\n"
			+ "To print out the complete list of command line options, "
			+ "use the --help switch.\nSee the FALCON website for a quick "
			+ "usage tutorial:\nhttp://ims.dei.unipd.it/falcon";
	private static final String default_query_pruning_strategy =
			"ntf:0.340765*[0.001694,0.995720];ndf:0.344143*[0.007224,0.997113];"
			+ "ncf:0.338766*[0.001601,0.995038];nmf:0.331577*[0.002352,0.997884];";

	// conversion step
	// TODO add the 'notransposition' option
	private static void conversion(OptionSet cmdline_options) throws IOException {
		File inputdir = new File((String) cmdline_options.valueOf("datapath"));
		File outBaseDir = new File((String) cmdline_options.valueOf("convertdir"));
		int nranks = 3;
		double minkurtosis = 3.;
		TranspositionEstimator te = new TranspositionEstimator(3, null);
		int ntransp = (Integer) cmdline_options.valueOf("ntransp");
		ChromaMatrixUtils.convertdir(inputdir, outBaseDir, nranks, minkurtosis, te, ntransp);
	}

	// indexing step
	private static void indexing(OptionSet cmdline_options) throws IOException {
		try {
			String indexPath = (String) cmdline_options.valueOf("indexpath");
			String dataPath = (String) cmdline_options.valueOf("datapath");
			int hashPerSegment = (Integer) cmdline_options.valueOf("hashperseg");
			int segmentOverlap = (Integer) cmdline_options.valueOf("overlap");
			Indexing.index(indexPath, dataPath, hashPerSegment, segmentOverlap);

		} catch (IndexingException ex) {
			Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	// ranking step
	// TODO add the 'notransposition' option
	private static void ranking(OptionSet cmdline_options) throws IOException, QueryParsingException {
		String indexPath = (String) cmdline_options.valueOf("indexpath");
		String queryDataPath = (String) cmdline_options.valueOf("querypath");
		int hashPerSegment = (Integer) cmdline_options.valueOf("hashperseg");
		int segmentOverlap = (Integer) cmdline_options.valueOf("overlap");
		if (cmdline_options.has("lambda")) {
			QueryMethods.setLambda((Float) cmdline_options.valueOf("lambda"));
		}
		// query pruning strategy (default value, can be overridden or set to null if option is not given)
		String ss = cmdline_options.has("qps")
				? (cmdline_options.valueOf("qps") != null
				? (String) cmdline_options.valueOf("qps") : default_query_pruning_strategy) : null;
		QueryPruningStrategy hqps = ss != null ? new StaticQueryPruningStrategy(ss) : null;
		QueryParser qParser = new QueryParser(hqps);
		qParser.loadQueryPruningHashFeatures(indexPath);


		// load file names in 0/ subdirectory
		String[] filenames = (new File(queryDataPath, "0")).list();
		int ntransp = cmdline_options.has("ntransp") ? (Integer) cmdline_options.valueOf("ntransp") : 3;

		for (String f : filenames) {
			// depending on just qp or also qf, do a whole directory or a single query
			// TODO complete here
			// TODO here also goes the 'notransp' stuff
			List<File> queryFilesList = new LinkedList<File>();
			for (int i = 0; i < ntransp; i++) {
				File subdir = new File(queryDataPath, String.format("%d", i));
				if (subdir.exists()) {
					File subfile = new File(subdir, f);
					if (subfile.exists()) {
						queryFilesList.add(subfile);
					}
				}
			}
			Map<String, Double> songid2finalscore =
					QueryMethods.performQuery(queryFilesList, new File(indexPath), hashPerSegment, segmentOverlap, hqps);
			int r = 1;
			for (DocScorePair p : DocScorePair.docscore2scoredoc(songid2finalscore)) {
				System.out.println(String.format("rank %3d: %10.6f - %s", r++, p.getScore(), p.getDoc()));
			}

		}
	}

	public static void main(String[] args) throws IOException, QueryParsingException {

		System.out.println(cmdline_notice);
		// command line processing stuff
		// -----------------------------------------------------------
		OptionParser parser = new OptionParser() {

			{
				acceptsAll(asList("h", "help", "?"), "display help");
				acceptsAll(asList("c", "conversion"), "perform chroma conversion");
				acceptsAll(asList("i", "indexing"), "perform indexing");
				acceptsAll(asList("r", "ranking"), "perform ranking");
				acceptsAll(asList("ip", "indexpath")).withRequiredArg().ofType(String.class);
				acceptsAll(asList("qp", "querypath"), "path for query files").withRequiredArg().ofType(String.class);
				acceptsAll(asList("qf", "queryfile"), "single-file query").withRequiredArg().ofType(String.class);
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
				acceptsAll(asList("stats")).withRequiredArg().ofType(String.class);
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

		if (cmdline_options.has("conversion")) {
			conversion(cmdline_options);
		} else if (cmdline_options.has("indexing")) {
			indexing(cmdline_options);
		} else if (cmdline_options.has("ranking")) {
			ranking(cmdline_options);
		} else if (cmdline_options.has("stats")) {
			if (cmdline_options.valueOf("stats").equals("show_doc_ids")
					|| cmdline_options.valueOf("stats").equals("show_seg_ids")
					|| cmdline_options.valueOf("stats").equals("show_full_index")) {
				try {
					String indexPath = (String) cmdline_options.valueOf("indexpath");
					Indexing.indexUtils(indexPath, cmdline_options.valueOf("stats").toString());
				} catch (IndexingException ex) {
					Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}
}
