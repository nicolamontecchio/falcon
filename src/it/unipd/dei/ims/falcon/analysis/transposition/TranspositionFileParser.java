package it.unipd.dei.ims.falcon.analysis.transposition;

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
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Utility class for reading the transposition file
 */
public class TranspositionFileParser {

    /// read the transposition file and return an array containing all the entries
    public static TranspositionFileEntry[] getTranspositionFileContents(File transpositionFile) throws IOException {
        LinkedList<TranspositionFileEntry> entriesList = new LinkedList<TranspositionFileEntry>();
        BufferedReader in = new BufferedReader(new FileReader(transpositionFile));
        String line;
        while ((line = in.readLine()) != null) 
            if (!line.startsWith("#")) {
                StringTokenizer st = new StringTokenizer(line, ";");
                if (st.countTokens() >= 3) {
                    String q = st.nextToken();
                    String c = st.nextToken();
                    int transp = new Integer(st.nextToken());
                    entriesList.add(new TranspositionFileEntry(q, c, transp));
                }
            }
        TranspositionFileEntry[] entries = new TranspositionFileEntry[entriesList.size()];
        int i = 0;
        for(TranspositionFileEntry e : entriesList)
            entries[i++] = e;
        return entries;
    }

    /// transform an array of transposition entries into a string->string map containing just the title matchings
    public static Map<String,String> getQuery2MatchingDocTitles(TranspositionFileEntry[] tfentries) {
    	Map<String,String> q2d = new TreeMap<String, String>();
    	for(TranspositionFileEntry tfe : tfentries) 
    		q2d.put(tfe.getQueryFileName(), tfe.getCollFileName());
    	return q2d;
    }
    
    /** 
     * transform an array of transposition entries into a string->string map containing just the title matchings,
     * but only for the titles that do not have a transposition
     */
    public static Map<String,String> get0TranspQuery2MatchingDocTitles(TranspositionFileEntry[] tfentries) {
    	Map<String,String> q2d = new TreeMap<String, String>();
    	for(TranspositionFileEntry tfe : tfentries)
    		if(tfe.getTransp() == 0)
    			q2d.put(tfe.getQueryFileName(), tfe.getCollFileName());
    	return q2d;
    }
    
}
