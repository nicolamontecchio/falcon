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
import it.unipd.dei.ims.falcon.analysis.transposition.ForcedTranspositionEstimator;
import it.unipd.dei.ims.falcon.analysis.transposition.TranspositionEstimator;
import it.unipd.dei.ims.falcon.indexing.Indexing;
import it.unipd.dei.ims.falcon.indexing.IndexingException;
import it.unipd.dei.ims.falcon.ranking.DocScorePair;
import it.unipd.dei.ims.falcon.ranking.QueryMethods;
import it.unipd.dei.ims.falcon.ranking.QueryParsingException;
import it.unipd.dei.ims.falcon.ranking.QueryPruningStrategy;
import it.unipd.dei.ims.falcon.ranking.QueryResults;
import it.unipd.dei.ims.falcon.ranking.StaticQueryPruningStrategy;
import java.io.BufferedReader;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Handle all the command-line operations.
 */
public class CmdLine {

	private static void doQuery(CommandLine cmd, String queryfilepath, int hashes_per_segment,
					int overlap_per_segment, int nranks, int subsampling, TranspositionEstimator tpe,
					int ntransp, double minkurtosis, QueryPruningStrategy qps, boolean verbose) {
		// TODO if verbose, print out the number of skipped hashes
		try {
			QueryResults qres = QueryMethods.query(new FileInputStream(queryfilepath),
							new File(cmd.getArgs()[0]), hashes_per_segment, overlap_per_segment, nranks,
							subsampling, tpe, ntransp, minkurtosis, qps);
			Map<String, Double> res = qres.getResults();
			int r = 1;
			System.out.println("query: " + queryfilepath);
			for (DocScorePair p : DocScorePair.docscore2scoredoc(res)) {
				System.out.println(String.format("rank %5d: %10.6f - %s", r++, p.getScore(), p.getDoc()));
				if (r == 1001)
					break;
			}
			if (verbose) {
				System.out.println(String.format("pruned|total %d %d", qres.getPrunedHashes(), qres.getTotalConsideredHashes()));
			}
		} catch (IOException ex) {
			Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
		} catch (QueryParsingException ex) {
			Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
		} catch (InterruptedException ex) {
			Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private static int[] parseIntArray(String s) {
		StringTokenizer t = new StringTokenizer(s, ",");
		int[] ia = new int[t.countTokens()];
		int ti = 0;
		while (t.hasMoreTokens())
			ia[ti] = Integer.parseInt(t.nextToken());
		return ia;
	}

	public static void main(String[] args) {

		// last argument is always index path
		Options options = new Options();
		// one of these actions has to be specified
		OptionGroup actionGroup = new OptionGroup();
		actionGroup.addOption(new Option("i", true, "perform indexing")); // if dir, all files, else only one file
		actionGroup.addOption(new Option("q", true, "perform a single query"));
		actionGroup.addOption(new Option("b", false, "perform a query batch (read from stdin)"));
		actionGroup.setRequired(true);
		options.addOptionGroup(actionGroup);

		// other options
		options.addOption(new Option("l", "segment-length", true, "length of a segment (# of chroma vectors)"));
		options.addOption(new Option("o", "segment-overlap", true, "overlap portion of a segment (# of chroma vectors)"));
		options.addOption(new Option("Q", "quantization-level", true, "quantization level for chroma vectors"));
		options.addOption(new Option("k", "min-kurtosis", true, "minimum kurtosis for indexing chroma vectors"));
		options.addOption(new Option("s", "sub-sampling", true, "sub-sampling of chroma features"));
		options.addOption(new Option("v", "verbose", false, "verbose output (including timing info)"));
		options.addOption(new Option("T", "transposition-estimator-strategy", true, "parametrization for the transposition estimator strategy"));
		options.addOption(new Option("t", "n-transp", true, "number of transposition; if not specified, no transposition is performed"));
		options.addOption(new Option("f", "force-transp", true, "force transposition by an amount of semitones"));
		options.addOption(new Option("p", "pruning", false, "enable query pruning; if -P is unspecified, use default strategy"));
		options.addOption(new Option("P", "pruning-custom", true, "custom query pruning strategy"));

		// parse
		HelpFormatter formatter = new HelpFormatter();
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
			if (cmd.getArgs().length != 1)
				throw new ParseException("no index path was specified");
		} catch (ParseException ex) {
			System.err.println("ERROR - parsing command line:");
			System.err.println(ex.getMessage());
			formatter.printHelp("falcon -{i,q,b} [options] index_path", options);
			return;
		}

		// default values
		final float[] DEFAULT_TRANSPOSITION_ESTIMATOR_STRATEGY =
						new float[]{0.65192807f, 0.0f, 0.0f, 0.0f, 0.3532628f, 0.4997167f, 0.0f, 0.41703504f, 0.0f, 0.16297342f, 0.0f, 0.0f};
		final String DEFAULT_QUERY_PRUNING_STRATEGY = "ntf:0.340765*[0.001694,0.995720];ndf:0.344143*[0.007224,0.997113];"
						+ "ncf:0.338766*[0.001601,0.995038];nmf:0.331577*[0.002352,0.997884];"; // TODO not the final one

		int hashes_per_segment = Integer.parseInt(cmd.getOptionValue("l", "150"));
		int overlap_per_segment = Integer.parseInt(cmd.getOptionValue("o", "50"));
		int nranks = Integer.parseInt(cmd.getOptionValue("Q", "3"));
		int subsampling = Integer.parseInt(cmd.getOptionValue("s", "1"));
		double minkurtosis = Float.parseFloat(cmd.getOptionValue("k", "-100."));
		boolean verbose = cmd.hasOption("v");
		int ntransp = Integer.parseInt(cmd.getOptionValue("t", "1"));
		TranspositionEstimator tpe = null;
		if (cmd.hasOption("t")) {
			if (cmd.hasOption("T")) {
				// TODO this if branch is yet to test
				Pattern p = Pattern.compile("\\d\\.\\d*");
				LinkedList<Double> tokens = new LinkedList<Double>();
				Matcher m = p.matcher(cmd.getOptionValue("T"));
				while (m.find())
					tokens.addLast(new Double(cmd.getOptionValue("T").substring(m.start(), m.end())));
				float[] strategy = new float[tokens.size()];
				if (strategy.length != 12) {
					System.err.println("invalid transposition estimator strategy");
					System.exit(1);
				}
				for (int i = 0; i < strategy.length; i++)
					strategy[i] = new Float(tokens.pollFirst());
			} else {
				tpe = new TranspositionEstimator(DEFAULT_TRANSPOSITION_ESTIMATOR_STRATEGY);
			}
		} else if (cmd.hasOption("f")) {
			int[] transps = parseIntArray(cmd.getOptionValue("f"));
			tpe = new ForcedTranspositionEstimator(transps);
			ntransp = transps.length;
		}
		QueryPruningStrategy qpe = null;
		if (cmd.hasOption("p")) {
			if (cmd.hasOption("P")) {
				qpe = new StaticQueryPruningStrategy(cmd.getOptionValue("P"));
			} else {
				qpe = new StaticQueryPruningStrategy(DEFAULT_QUERY_PRUNING_STRATEGY);
			}
		}

		// action
		if (cmd.hasOption("i")) {
			try {
				Indexing.index(new File(cmd.getOptionValue("i")), new File(cmd.getArgs()[0]),
								hashes_per_segment, overlap_per_segment, subsampling, nranks, minkurtosis, tpe, verbose);
			} catch (IndexingException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			} catch (IOException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		if (cmd.hasOption("q")) {
			String queryfilepath = cmd.getOptionValue("q");
			doQuery(cmd, queryfilepath, hashes_per_segment, overlap_per_segment, nranks, subsampling, tpe, ntransp, minkurtosis, qpe, verbose);
		}
		if (cmd.hasOption("b")) {
			try {
				long starttime = System.currentTimeMillis();
				BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				String line = null;
				while ((line = in.readLine()) != null && !line.trim().isEmpty())
					doQuery(cmd, line, hashes_per_segment, overlap_per_segment, nranks, subsampling, tpe, ntransp, minkurtosis, qpe, verbose);
				in.close();
				long endtime = System.currentTimeMillis();
				System.out.println(String.format("total time: %ds", (endtime - starttime) / 1000));
			} catch (IOException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}
}
