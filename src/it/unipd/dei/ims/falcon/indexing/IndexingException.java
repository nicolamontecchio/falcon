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

/**
 * Exception thrown during the indexing phase or when accessing the index.
 * The exception is thrown in one of the following case:
 * <ul>
 *  <li> the number of hashes per segment is not greater than the overlap size
 *  <li> {@link java.io.FileNotFoundException} when indexing songs
 *  <li> {@link java.io.IOException} when indexing songs
 *  <li> {@link org.apache.lucene.index.CorruptIndexException} when accessing
 *       index to retrieve print index information
 *  <li> {@link java.io.IOException} when accessing index to retrieve print
 *       index information
 * </ul>
 * 
 */
public class IndexingException extends Exception {
    
    private static final long serialVersionUID = 1L;

    public IndexingException(String message) {
        super(message);
    }
}
