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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Structure to hold references to all bases that are aligned onto a single
 * locus.
 *
 * Used within the Bamfo variant calling program.
 *
 *
 * @author tkonopka
 */
public class LocusSNVDataList {

    private ArrayList<LocusSNVData> lgdl;
    private final String locusname;
    private HashMap<String, Integer> overlappingReads = null;

    public String getLocusname() {
        return locusname;
    }

    public int getLocuspos2() {
        //return locuspos;
        return 0;
    }

    public LocusSNVDataList(String locusname) {
        this.lgdl = new ArrayList<>(16);
        this.locusname = locusname;
        this.overlappingReads = new HashMap<>(4, 0.8f);
    }

    public LocusSNVDataList(int locuspos) {
        this.lgdl = new ArrayList<>(16);
        this.locusname = "" + locuspos;
    }

    public int size() {
        return lgdl.size();
    }

    public LocusSNVData getLocusData(int index) {
        if (index < lgdl.size()) {
            return lgdl.get(index);
        }
        return null;
    }

    /**
     * This is an easy way to add a base to the list. It uses the information in
     * the BamfoRecord to get all the relevant information and put it into the
     * list.
     *
     * @param b2r
     * @param indexonread
     */
    public void add(BamfoRecord b2r, int indexonread) {

        add(b2r.bases[indexonread], b2r.qualities[indexonread], b2r.mapquality,
                b2r.record.getReadNegativeStrandFlag(), indexonread, b2r.readlength,
                b2r.recordname, b2r.pos[indexonread] >= b2r.overlapstart,
                b2r.readhasindel, b2r.maxN, b2r.getNMtag());

    }

    public void add(byte base, byte quality, int mapquality, boolean minusstrand,
            int indexonread, int readlength, String readname, boolean overlapping,
            boolean readhasindel, int maxN, int NMtag) {

        // check if this is the first time the readname is encountered
        Integer overlappingindex;
        if (overlappingReads == null) {
            overlappingindex = null;
        } else {
            overlappingindex = overlappingReads.get(readname);
        }

        if (overlappingindex == null) {
            // first time this read name is encountered, so insert the information onto the locus
            lgdl.add(new LocusSNVData(base, quality, minusstrand, indexonread, readlength,
                    mapquality, readhasindel, maxN, NMtag));

            // but if the read is said to be overlapping, then save its name into the HashMap
            // for the value, put the index of this LocusGenotypData so that it can be retrieved later
            if (overlapping) {
                // first create a hashmap if necessary
                if (overlappingReads == null) {
                    overlappingReads = new HashMap<>(8);
                }
                overlappingReads.put(readname, lgdl.size() - 1);
            }

        } else {
            // the read name is encountered before. Look up the already present information            
            LocusSNVData lgd = lgdl.get((int) overlappingindex);

            // update the attributes (base, quality, fromstart, fromend) to reflect the better quality evidence
            //byte oldquality = lgd.quality;
            int oldmapquality = lgd.getMapquality();
            //boolean oldminusstrand = lgd.minusstrand;

            // update the fromstart and fromend information 
            int nowfromstart, nowfromend;
            if (minusstrand) {
                nowfromend = indexonread;
                nowfromstart = -1 + readlength - indexonread;
            } else {
                nowfromstart = indexonread;
                nowfromend = -1 + readlength - indexonread;
            }

            // check qualities
            // replace the old information on base, quality, and strand if 
            //   - new quality is better            

            // if they are not concordant:
            // if one of the bases is N, be optimistic and record the non-N base.
            // if none of the two are N, then the base becomes unknown and is recorded as N
            if (lgd.getBase() != base) {
                if (lgd.getBase() == 'N') {
                    lgd.setBase(base);
                } else {
                    if (base != 'N') {
                        lgd.setBase((byte) 'N');
                    }
                }
            }

            if (mapquality > oldmapquality) {
                lgd.setMapquality(mapquality);
            }

            // check if the old read had an indel
            // if it did, and if this one does not, use the base as if it does not have an indel
            if (lgd.isReadhasindel() && !readhasindel) {
                lgd.setReadhasindel(readhasindel);
            }

            // replace the old fromstart from end with larger values. 
            // This is somewhat ad-hoc as it is impossible to determine exactly                       
            lgd.setFromstart(Math.max(lgd.getFromstart(), nowfromstart));
            lgd.setFromend(Math.max(lgd.getFromend(), nowfromend));
            lgd.setMaxN(Math.max(lgd.getMaxN(), maxN));
            lgd.setNMtag(Math.max(lgd.getNMtag(), NMtag));
        }

    }

    /**
     *
     * @return
     *
     * true if this locus saw some overlapping reads.
     *
     */
    public int numOverlaps() {
        return overlappingReads.size();
    }

    /**
     *
     * @return
     *
     * the number of unique distances from the start of a read
     *
     */
    public int getNumberUniqueFromstart(byte base) {
        int lsize = lgdl.size();
        Set<Integer> fromstart = new HashSet<>(lsize);
        for (int i = 0; i < lsize; i++) {
            LocusSNVData nowlocus = lgdl.get(i);
            if (nowlocus.getBase() == base) {
                fromstart.add(nowlocus.getFromstart());
            }
        }
        return fromstart.size();
    }

    /**
     *
     * @param base
     * @return
     *
     * the average NM tag value associated with reads containing the input base.
     *
     */
    public int getMeanNMtag(byte base) {
        double tottag = 0.0;
        double numtags = 0.0;
        int lsize = lgdl.size();
        for (int i = 0; i < lsize; i++) {
            LocusSNVData nowlocus = lgdl.get(i);
            if (nowlocus.getBase() == base) {
                numtags += 1;
                tottag += nowlocus.getNMtag();
            }
        }        
        return (int) Math.round(tottag / numtags);
    }

    /**
     *
     * @return
     *
     * the number of unique distances from the end of a read
     *
     */
    public int getNumberUniqueFromend(byte base) {
        Set<Integer> fromend = new HashSet<>(8);
        for (int i = 0; i < lgdl.size(); i++) {
            LocusSNVData nowlocus = lgdl.get(i);
            if (nowlocus.getBase() == base) {
                fromend.add(nowlocus.getFromend());
            }
        }
        return fromend.size();
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
    public void getCoverageCounts(int[] covplus, int[] covminus, int[] maxnvalues,
            int minfromstart, int minfromend, byte minbasequal, int minmapqual) {

        // subtract from the minimums so that can use > instead of >=
        minfromstart--;
        minfromend--;
        minmapqual--;

        int listlen = lgdl.size();
        for (int i = 0; i < listlen; i++) {
            LocusSNVData lgd = lgdl.get(i);

            // make sure the locus has appropriate distance from the read start/end position
            if (lgd.getFromstart() > minfromstart && lgd.getFromend() > minfromend
                    && lgd.quality >= minbasequal && lgd.getMapquality() > minmapqual) {
                int whichbase = BamfoCommon.basesToZeroToFour(lgd.getBase());
                if (lgd.minusstrand) {
                    covminus[whichbase]++;
                } else {
                    covplus[whichbase]++;
                }
                if (lgd.getMaxN() > maxnvalues[whichbase]) {
                    maxnvalues[whichbase] = lgd.getMaxN();
                }
            } else {
                // do not count this base in the genotyping
            }
        }

    }

    /**
     *
     * @param minfromstart
     * @param minfromend
     * @param minbasequal
     * @param minmapqual
     * @return
     *
     * the effective depth at this locus. That is the total number of bases
     * covering the position with sufficient high mapping quality, minimum
     * distance from ends, etc.
     *
     */
    public int getEffectiveDepth(int minfromstart, int minfromend, byte minbasequal, int minmapqual) {

        int depth = 0;

        // subtract from the minimums so that can use > instead of >=
        minfromstart--;
        minfromend--;
        minmapqual--;

        int listlen = lgdl.size();
        for (int i = 0; i < listlen; i++) {
            LocusSNVData lgd = lgdl.get(i);
            // make sure the locus has appropriate distance from the read start/end position
            if (lgd.getFromstart() > minfromstart && lgd.getFromend() > minfromend
                    && lgd.quality >= minbasequal && lgd.getMapquality() > minmapqual) {
                depth++;
            }
        }

        return depth;
    }

    /**
     *
     * @param minfromstart
     * @param minfromend
     * @param minbasequal
     * @param minmapqual
     * @return
     *
     * the effective depth at this locus. That is the total number of bases
     * covering the position with sufficient high mapping quality, minimum
     * distance from ends, etc. The return object is an array with plus and
     * minus strand counts.
     *
     */
    public int[] getStrandEffectiveDepth(int minfromstart, int minfromend, byte minbasequal, int minmapqual) {

        int[] depth = new int[2];

        // subtract from the minimums so that can use > instead of >=
        minfromstart--;
        minfromend--;
        minmapqual--;

        int listlen = lgdl.size();
        for (int i = 0; i < listlen; i++) {
            LocusSNVData lgd = lgdl.get(i);
            // make sure the locus has appropriate distance from the read start/end position
            if (lgd.getFromstart() > minfromstart && lgd.getFromend() > minfromend
                    && lgd.quality >= minbasequal && lgd.getMapquality() > minmapqual) {
                if (lgd.minusstrand) {
                    depth[1]++;
                } else {
                    depth[0]++;
                }
            }
        }

        return depth;
    }

    /**
     * count the number of reads with low mapping quality and low base quality
     * covering this locus
     *
     * @param minfromstart
     * @param minfromend
     * @param minbasequal
     * @param minmapqual
     *
     * @return
     *
     * array of two elements. First item is number of reads with low mapping
     * quality. Second item is number of reads with high mapping quality but low
     * base quality score.
     *
     */
    public int[] getLowQualityCounts(int minfromstart, int minfromend, byte minbasequal, int minmapqual) {

        int[] numlow = new int[2];

        // subtract from the minimums so that can use > instead of >=
        minfromstart--;
        minfromend--;

        int listlen = lgdl.size();
        for (int i = 0; i < listlen; i++) {
            LocusSNVData lgd = lgdl.get(i);
            // make sure the locus has appropriate distance from the read start/end position
            if (lgd.getFromstart() > minfromstart && lgd.getFromend() > minfromend) {
                if (lgd.getMapquality() < minmapqual) {
                    numlow[0]++;
                } else {
                    if (lgd.quality < minbasequal) {
                        numlow[1]++;
                    }
                }
            }
        }

        return numlow;
    }

    /**
     *
     * @return
     *
     * the median mapping quality associated with the locus
     *
     */
    public double getMedianMappingQuality() {
        if (lgdl.isEmpty()) {
            return -1;
        }
        int[] mq = new int[lgdl.size()];
        for (int i = 0; i < mq.length; i++) {
            mq[i] = lgdl.get(i).getMapquality();
        }

        return BamfoCommon.getMedian(mq);
    }

    /**
     * prints all information about this locus to the screen
     */
    public void print() {
        System.out.println(locusname + " ");
        for (int i = 0; i < lgdl.size(); i++) {
            LocusSNVData lgd = lgdl.get(i);
            System.out.println((char) lgd.getBase() + "\t" + lgd.quality + "\t" + lgd.getFromstart() + "\t" + lgd.getFromend());
        }
        System.out.println();
    }
}
