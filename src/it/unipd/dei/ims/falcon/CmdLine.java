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
import it.unipd.dei.ims.falcon.indexing.Indexing;
import it.unipd.dei.ims.falcon.indexing.IndexingException;
import it.unipd.dei.ims.falcon.ranking.DocScorePair;
import it.unipd.dei.ims.falcon.ranking.QueryMethods;
import it.unipd.dei.ims.falcon.ranking.QueryParsingException;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
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

	// TODO transposition estimator not accessible yet
	// TODO query pruning strategy not accessible yet
	//	public static final String cmdline_notice = "--\nWelcome to FALCON\n"
	//					+ "FAst Lucene-based Cover sOng identificatioN\n--\n"
	//					+ "To print out the complete list of command line options, "
	//					+ "use the --help switch.\nSee the FALCON website for a quick "
	//					+ "usage tutorial:\nhttp://ims.dei.unipd.it/falcon";
	//	
	//	private static final String default_query_pruning_strategy =
	//					"ntf:0.340765*[0.001694,0.995720];ndf:0.344143*[0.007224,0.997113];"
	//					+ "ncf:0.338766*[0.001601,0.995038];nmf:0.331577*[0.002352,0.997884];";
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
		int hashes_per_segment = Integer.parseInt(cmd.getOptionValue("l", "150"));
		int overlap_per_segment = Integer.parseInt(cmd.getOptionValue("o", "50"));
		int nranks = Integer.parseInt(cmd.getOptionValue("Q", "3"));
		int subsampling = Integer.parseInt(cmd.getOptionValue("s", "1"));
		double minkurtosis = Float.parseFloat(cmd.getOptionValue("k", "0."));

		// action
		if (cmd.hasOption("i")) {
			try {
				Indexing.index(new File(cmd.getOptionValue("i")), new File(cmd.getArgs()[0]),
								hashes_per_segment, overlap_per_segment, subsampling, nranks, minkurtosis, null);
			} catch (IndexingException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			} catch (IOException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		if (cmd.hasOption("q")) {
			String queryfilepath = cmd.getOptionValue("q");
			try {
				Map<String, Double> res = QueryMethods.query(new FileInputStream(queryfilepath), new File(cmd.getArgs()[0]), hashes_per_segment, overlap_per_segment, nranks, subsampling, null, subsampling, minkurtosis, null);
				int r = 1;
				for (DocScorePair p : DocScorePair.docscore2scoredoc(res))
					System.out.println(String.format("rank %5d: %10.6f - %s", r++, p.getScore(), p.getDoc()));
			} catch (IOException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			} catch (QueryParsingException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			} catch (InterruptedException ex) {
				Logger.getLogger(CmdLine.class.getName()).log(Level.SEVERE, null, ex);
			}
		}


	}
}
