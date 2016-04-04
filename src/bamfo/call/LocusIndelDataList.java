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
package bamfo.call;

import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A structure that will hold the information about an indel from multiple reads.
 *
 * The class is used within BamfoVcf to hold temporary information about indels.
 *
 * @author tkonopka
 */
class LocusIndelDataList {

    private final ArrayList<LocusIndelData> datalist;    
    private final int locuspos;
    private final byte anchorbase;

    public byte getAnchorbase() {
        return anchorbase;
    }
    
    public LocusIndelDataList(int locuspos, byte anchorbase) {
        this.datalist = new ArrayList<>(16);
        this.locuspos = locuspos;
        this.anchorbase = anchorbase;        
    }

    /**
     * A simplified version of the other add function. This one takes several of the arguments from a
     * BamfoRecord object. 
     * 
     * @param anchor
     * @param indel
     * @param indelstart
     * @param b2r
     * @param indexonread
     * @param insertion 
     */
    public void add(byte[] anchor, byte[] indel, int indelstart, BamfoRecord b2r, 
            int indexonread, boolean insertion) {
        
        // this blog is a safeguard against potential bugs where the indexonread may go beyond
        // the read array length (can occur if reads have unusual/wrong cigars, e.g. 30M20I20D        
        if (indexonread>=b2r.pos.length) {
            return;
        }
        
        add(anchor, indel, indelstart, b2r.mapquality, b2r.record.getReadNegativeStrandFlag(),
                indexonread, b2r.readlength, b2r.recordname, b2r.pos[indexonread]>b2r.overlapstart, 
                insertion);
    }
    
    public void add(byte[] anchor, byte[] indel, int indelstart, int mapquality, boolean minusstrand,
            int indexonread, int readlength,
            String readname, boolean overlapping, boolean insertion) {

        datalist.add(new LocusIndelData(anchorbase, anchor, indel, indelstart, minusstrand, indexonread, readlength, mapquality, insertion));

    }

    /**
     * fills in the number of bases with each type of base. Assumes the covplus
     * and covminus are already initialized. Counts evidence as long as base is
     * not too close to the ends, has high base quality, and the read is mapped
     * with confidence.
     *
     * @param covplus
     *
     * coverage on the plus strand
     *
     * @param covminus
     *
     * coverage on the minus strand
     *
     * @param minfromstart
     *
     * @param minfromend
     *
     * @param minbasequal
     *
     * @minmapqual
     *
     *
     */
    public void getIndelCounts(ArrayList<LocusIndelData> uniqueindels,
            ArrayList<Integer> indelcounts,
            int minfromstart, int minfromend, int minmapqual) {

        // subtract from the minimums so that can use > instead of >=
        minfromstart--;
        minfromend--;
        minmapqual--;

        LocusIndelDataComparator lidc = new LocusIndelDataComparator();

        // make a sublist with only those reads that satisfy minmapqual, minfromstart etc.
        ArrayList<LocusIndelData> templist = new ArrayList<>(datalist.size());
        for (int i = 0; i < datalist.size(); i++) {
            LocusIndelData now = datalist.get(i);
            if (now.mapquality > minmapqual) {
                templist.add(now);
            }
        }

        // perhaps there aren't any high-quality indels. Then return immediately.
        if (templist.isEmpty()) {
            return;
        }

        // sort the data list to get better-indels first        
        Collections.sort(templist, lidc);

        // record the unique indels and the number of times they appear
        // the first sorted indel is always included
        uniqueindels.add(templist.get(0));
        int counter = 1;
        for (int i = 1; i < templist.size(); i++) {
            if (lidc.similar(templist.get(i), templist.get(i - 1))) {
                counter++;
            } else {
                indelcounts.add(counter);
                counter = 1;
                uniqueindels.add(templist.get(i));
            }

        }
        indelcounts.add(counter);
        
        

    }

    public int size() {
        return datalist.size();
    }

    public void print() {
        System.out.println("LocusIndelDataList at locuspos "+locuspos);
        System.out.println("datalist has "+datalist.size()+" elements");
        
        ArrayList<LocusIndelData> uniqueindels = new ArrayList<>(4);
        ArrayList<Integer> indelcounts = new ArrayList<>(4);

        getIndelCounts(uniqueindels, indelcounts, 0, 0, 0);

        System.out.println("Detected " + uniqueindels.size() + " " + indelcounts.size() + " indels");

        for (int i = 0; i < uniqueindels.size(); i++) {
            System.out.println(uniqueindels.get(i).toString());
            System.out.println(indelcounts.get(i));
        }
    }
    
    /**
     * 
     * @return 
     * 
     * the median mapping quality associated with the locus
     * 
     */
    public double getMedianMappingQuality() {
        if (datalist.isEmpty()) {
            return -1;
        }
        int[] mq = new int[datalist.size()];
        for (int i=0; i<mq.length; i++) {
            mq[i] = datalist.get(i).getMapquality();
        }
        
        return BamfoCommon.getMedian(mq);
    }
    
    /**
     *
     * @return
     *
     * the number of unique distances from the start of a read
     *
     */
    public int getNumberUniqueFromstart(int len) {
        int dsize = datalist.size();
        Set<Integer> fromstart = new HashSet<>(dsize);
        for (int i = 0; i < dsize; i++) {
            LocusIndelData nowlocus = datalist.get(i);
            if (nowlocus.getIndelLen() == len) {
                fromstart.add(nowlocus.getFromstart());
            }
        }
        return fromstart.size();
    }
    
    /**
     * counts the number of indels of given length supported by reads on positive
     * and negative strand
     * 
     * @param len
     * @return 
     */
    public int[] getStrandedIndelCount(int len) {
        int[] counts = new int[2];
        int dsize = datalist.size();        
        for (int i = 0; i < dsize; i++) {
            LocusIndelData nowlocus = datalist.get(i);
            if (nowlocus.getIndelLen() == len) {
                if (nowlocus.minusstrand) {
                    counts[1]++;
                } else {
                    counts[0]++;
                }
            }
        }
        return counts;
    }
    
}
