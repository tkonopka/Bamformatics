/*
 * Copyright 2013 Tomasz Konopka.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bamfo.utils;

import java.io.BufferedReader;
import java.io.File;
import jsequtils.file.BufferedReaderMaker;

/**
 * Static function that peaks into a file and checks if it contains a text file.
 *
 *
 * @author tomasz
 */
public class TextFileChecker {

    private static int checkMaxCharacters = 1000;
    private static double maxProportionNonPrintable = 0.01;

    /**
     * Checks that the given file is text file, e.g. can be read as a table.
     * Note: zip/gzip/bz2 files will also be classified as text files if they
     * are compressed representation of text files.
     *
     * @param f
     * @return
     */
    public static boolean isTextFile(File file) {

        if (!file.canRead()) {
            return false;
        }

        try {
            BufferedReader br = BufferedReaderMaker.makeBufferedReader(file);

            int numtested = 0;
            int numbad = 0;

            char[] buffer = new char[checkMaxCharacters];
            int numread = br.read(buffer);
            br.close();

            if (numread < 0) {
                return true;
            }
            for (int i = 0; i < numread && i < buffer.length; i++) {
                char nowchar = buffer[i];
                if (isNonPrintable(nowchar)) {
                    numbad++;
                }
                numtested++;
            }

            if ((((double) numbad) / numread) > maxProportionNonPrintable) {
                return false;
            }
        } catch (Exception ex) {
            // if any exception occurs, then report is not a text file
            return false;
        }
        return true;
    }

    /**
     *
     * @param cc
     * @return
     *
     * true if the character is non-printable. false if character is printable
     * or is one of: tab, newline, carriage return
     *
     */
    private static boolean isNonPrintable(char cc) {
        byte bb = (byte) cc;
        if ((bb > 30 && bb < 129) || bb == '\n' || bb == '\t' || bb == '\r') {
            return false;
        }
        return true;
    }
}
