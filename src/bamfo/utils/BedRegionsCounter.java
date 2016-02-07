/*
 * Copyright 2012 Tomasz Konopka.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * A class for holding "bed" style data.
 * The class counts non-identical (chr start end) triples.
 *  
 *  
 *
 * @author tkonopka
 */
public class BedRegionsCounter {

    private final ArrayList<String> chromosomes = new ArrayList<String>(64);
    private final HashMap<String, HashMap<String, OneInterval>> bed = new HashMap<String, HashMap<String, OneInterval>>(64);
    private final OneIntervalComparator onc = new OneIntervalComparator();

    class OneInterval {

        private final int start, end;
        private int count;

        public OneInterval(int start, int end, int count) {
            this.start = start;
            this.end = end;
            this.count = count;
        }

        public void incrementCount() {
            count++;
        }
        
        @Override
        public String toString() {
            return (start + "\t" + end + "\t" + count);
        }
    }

    /**
     * Compares two intervals based only on their start position
     */
    class OneIntervalComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            OneInterval oi1 = (OneInterval) o1;
            OneInterval oi2 = (OneInterval) o2;
            if (oi1.start < oi2.start) {
                return -1;
            } else if (oi1.start > oi2.start) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * Create an empty bed object without any sequence dictionary
     */
    public BedRegionsCounter() {
    }

    /**
     * Create an empty bed object, but declare a set of chromosomes. When
     * printing the bed, the chromosome will appear in the order specified here.
     *
     * @param chrs
     */
    public BedRegionsCounter(ArrayList<String> chrs) {
        for (int i = 0; i < chrs.size(); i++) {
            chromosomes.add(chrs.get(i));
            bed.put(chrs.get(i), new HashMap<String, OneInterval>());
        }
    }

    public void add(String chr, int start, int end) {
        String key = start + "-" + end;
        if (!bed.containsKey(chr)) {
            bed.put(chr, new HashMap<String, OneInterval>());
            chromosomes.add(chr);
        }
        HashMap<String, OneInterval> chrregions = bed.get(chr);
        OneInterval interval = chrregions.get(key);
        if (interval == null) {
            interval = new OneInterval(start, end, 1);
            chrregions.put(key, interval);
        } else {
            interval.incrementCount();
        }
    }

    /**
     * 
     * @return 
     * 
     * A table in tab separated format containing quadruples
     * (chr start end count)
     * 
     * Items are sorted by chromosome and then by start position.
     * Chromosomes appear in the order in which they were defined via the constructor
     * or via the add function.
     * 
     * 
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(4096);

        // loop over the chromosomes
        for (int i = 0; i < chromosomes.size(); i++) {
            String nowchr = chromosomes.get(i);

            // get all the regions on this chromosomes, sort them, and report each
            if (bed.containsKey(nowchr)) {
                HashMap<String, OneInterval> temphash = bed.get(nowchr);
                ArrayList<OneInterval> ni = new ArrayList<OneInterval>(temphash.values());
                Collections.sort(ni, onc);
                for (int j = 0; j < ni.size(); j++) {
                    sb.append(nowchr).append("\t").append(ni.get(j)).append("\n");
                }
            }

        }


        return sb.toString();
    }
}
