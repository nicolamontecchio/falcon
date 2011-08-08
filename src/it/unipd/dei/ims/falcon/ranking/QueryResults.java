package it.unipd.dei.ims.falcon.ranking;

import java.util.Map;

public class QueryResults {

	private Map<String, Double> results;
	private long prunedHashes;
	private long totalConsideredHashes;

	public QueryResults(Map<String, Double> results, long prunedHashes, long totalConsideredHashes) {
		this.results = results;
		this.prunedHashes = prunedHashes;
		this.totalConsideredHashes = totalConsideredHashes;
	}

	public Map<String, Double> getResults() {
		return results;
	}

	public long getPrunedHashes() {
		return prunedHashes;
	}

	public long getTotalConsideredHashes() {
		return totalConsideredHashes;
	}
}
