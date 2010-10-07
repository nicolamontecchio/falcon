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

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

/**
 * Essentially, a {@link org.apache.lucene.search.BooleanQuery} with a potentially higher number of clauses
 *
 * @see org.apache.lucene.search.BooleanQuery
 *
 */
public class SegmentQuery extends BooleanQuery {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a empty {@link SegmentQuery}. 
     * A {@link SegmentQuery} is a {@link org.apache.lucene.search.BooleanQuery}
     * with a number of {@link org.apache.lucene.search.BooleanClause} that is
     * at most "segmentLength", that is the number of hash in a segment of the query
     *
     * @param segmentLength number of hash in a segment of the query
     */
    public SegmentQuery(int segmentLength) {
        // create a Lucene Boolean Query
        super(true);
        // re-set the maximum number of clauses
        // Default is 1024, but in our case the number of hash per segment
        // can be segmentLength
        BooleanQuery.setMaxClauseCount(segmentLength);
    }

    /**
     * Adds an {@link org.apache.lucene.search.Query} to this {@link SegmentQuery}.
     * The query added is currently a {@link HashQuery}. This query is added as 
     * a {@link org.apache.lucene.search.BooleanClause} which should occur,
     * that is with {@link org.apache.lucene.search.BooleanClause.Occur#SHOULD}
     *
     * @param hashQuery {@link HashQuery} to be added
     */
    public void add(Query hashQuery) {
        super.add(new BooleanClause(hashQuery, BooleanClause.Occur.SHOULD));
    }
}
