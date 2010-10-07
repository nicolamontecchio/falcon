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

/**
 * Entry in the transposition file
 * the fields are:
 * - query file name
 * - collection file name
 * - transposition to the left for the query in order to match the tonality of the doc
 *
 */
public class TranspositionFileEntry {

    private String queryFileName;
    private String collFileName;
    private int transp;

    public TranspositionFileEntry(String queryFileName, String collFileName, int transp) {
        this.queryFileName = queryFileName.replace(".mp3.txt", "").replace(".mp3", "");
        this.collFileName = collFileName.replace(".mp3.txt", "").replace(".mp3", "");
        this.transp = transp;
    }

    public String getCollFileName() {
        return collFileName;
    }

    public String getQueryFileName() {
        return queryFileName;
    }

    public int getTransp() {
        return transp;
    }


}
