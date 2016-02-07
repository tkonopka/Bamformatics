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
package bamfo.utils.bed;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import jsequtils.file.BufferedReaderMaker;

/**
 *
 * @author tkonopka
 */
public class BedRegions {

    private final ArrayList<String> chromosomes = new ArrayList<String>(64);
    private final HashMap<String, ArrayList<OneInterval>> bed = new HashMap<String, ArrayList<OneInterval>>(64);
    private final OneIntervalStartComparator startcompare = new OneIntervalStartComparator();
    private final OneIntervalEndComparator endcompare = new OneIntervalEndComparator();

    /**
     * Create an empty bed object, but declare a set of chromosomes. When
     * printing the bed, the chromosome will appear in the order specified here.
     *
     * @param chrs
     */
    public BedRegions(File bedfile) throws IOException {

        // read all the intervals input the bed object
        BufferedReader br = BufferedReaderMaker.makeBufferedReader(bedfile);
        String s;
        while ((s = br.readLine()) != null) {
            String[] tokens = s.split("\t");
            add(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
        }
        br.close();

    }

    /**
     * Create an empty bed object, but declare a set of chromosomes. When
     * printing the bed, the chromosome will appear in the order specified here.
     *
     * @param chrs
     */
    public BedRegions() {
    }

    /**
     * Create an empty bed object, but declare a set of chromosomes. When
     * printing the bed, the chromosome will appear in the order specified here.
     *
     * @param chrs
     */
    public BedRegions(ArrayList<String> chrs) {
        for (int i = 0; i < chrs.size(); i++) {
            chromosomes.add(chrs.get(i));
            bed.put(chrs.get(i), new ArrayList<OneInterval>());
        }
    }

    public void add(String chr, int start, int end) {
        //System.out.println("adding: "+chr+"\t"+start+"\t"+end);
        // make sure the chromosome has been defined
        if (!bed.containsKey(chr)) {
            bed.put(chr, new ArrayList<OneInterval>());
            chromosomes.add(chr);
        }

        // add the interval to the chromosome
        ArrayList<OneInterval> chrregions = bed.get(chr);
        add(chrregions, new OneInterval(start, end));

    }

    /**
     *
     * fetch an interval from a list. If request is beyond the list, fetch gives
     * last item.
     *
     * @param chrregions
     * @param index
     * @return
     */
    private OneInterval getFromArray(ArrayList<OneInterval> chrregions, int index) {
        int size = chrregions.size();
        if (index < size) {
            return chrregions.get(index);
        } else {
            return chrregions.get(index - 1);
        }
    }

    /**
     * adds an interval into an already existing definition of regions
     *
     * @param chrregions
     * @param oneInterval
     */
    private void add(ArrayList<OneInterval> chrregions, OneInterval interval) {
        
        // simplest case, the first interval is always added.
        if (chrregions.isEmpty()) {
            chrregions.add(interval);
            return;
        }

        int indexByStart = Collections.binarySearch(chrregions, interval, startcompare);
        int indexByEnd = Collections.binarySearch(chrregions, interval, endcompare);

        if (indexByStart < 0) {
            indexByStart = -indexByStart - 1;
        }
        if (indexByEnd < 0) {
            indexByEnd = -indexByEnd - 1;
        }

        // check if this interval is already included in the existing Bed
        if (indexByEnd < indexByStart) {
            return;
        }

        // if reached here, the interval has to be added or merged with some existing intervals        

        int numnow = chrregions.size();

        // figure which existing elements overlap with the current 
        ArrayList<Integer> overlapping = new ArrayList<Integer>();
        for (int i = Math.max(0, indexByStart - 1); i < Math.min(indexByEnd+1, numnow); i++) {
            OneInterval nowinterval = chrregions.get(i);            
            if (interval.overlaps(nowinterval)) {                
                overlapping.add(i);
            }
        }

        // perhaps no need to update
        if (overlapping.isEmpty()) {
            chrregions.add(indexByStart, interval);
        } else {
            // create an interval that merges over all existing items
            OneInterval mergedfirst = OneInterval.merge(interval, chrregions.get(overlapping.get(0)));
            OneInterval mergedlast = OneInterval.merge(interval, chrregions.get(overlapping.get(overlapping.size() - 1)));
            OneInterval merged = OneInterval.merge(mergedfirst, mergedlast);
            
            // remove all but one of the overlapping existing intervals
            for (int i = overlapping.size() - 1; i > 0; i--) {                                
                chrregions.remove((int)overlapping.get(i));
            }
            // replace the smallest overlapping item by the merged item
            chrregions.set(overlapping.get(0), merged);
        }


    }

    public boolean containsBase0(String chr, int position) {

        ArrayList<OneInterval> chrregions = bed.get(chr);

        // if chromosome was not defined, the bed does not contain the position
        if (chrregions == null) {
            return false;
        }

        OneInterval interval = new OneInterval(position, position);

        int indexByStart = Collections.binarySearch(chrregions, interval, startcompare);
        int indexByEnd = Collections.binarySearch(chrregions, interval, endcompare);

        if (indexByStart < 0) {
            indexByStart = -indexByStart - 1;
        }
        if (indexByEnd < 0) {
            indexByEnd = -indexByEnd - 1;
        }

        if (indexByEnd < indexByStart) {
            return true;
        }


        return false;

    }

    /**
     * Creates a bed-file representation of the regions.
     * Chromosomes appear in the order in which they were added.
     * Intervals appear sorted and non-overlapping.
     * 
     * @return 
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        for (int i = 0; i < chromosomes.size(); i++) {
            String nowchrom = chromosomes.get(i);
            ArrayList<OneInterval> nowregions = bed.get(nowchrom);

            for (int j = 0; j < nowregions.size(); j++) {
                OneInterval interval = nowregions.get(j);
                sb.append(nowchrom).append("\t").append(interval.getStart()).append("\t").append(interval.getEnd()).append("\n");
            }
        }
        return sb.toString();
    }
}
