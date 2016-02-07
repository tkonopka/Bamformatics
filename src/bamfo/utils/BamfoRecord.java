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

import java.util.ArrayList;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMRecord;

/**
 * This is a wrapper/holder for a SAMRecord, which holds some pre-processing information common 
 * for the Bam2x suite.
 * 
 * For example, the wrapper creates an array pos, which holds the position of each base on the chromosome
 * The positions are computed from the read start position and the cigar.
 * 
 * @author tkonopka
 */
public class BamfoRecord {
    // a reference to the orginal record
    public final SAMRecord record;
    // some derived data from the record
    public final String recordname;
    public final byte[] qualities;
    public final byte[] bases;
    public final int maxN;
    public final int[] pos;
    public final ArrayList<CigarElement> cigarelements;
    public final int readlength;
    public final int startpos;
    public final int overlapstart;
    public final int mapquality;
    public final boolean readhasindel;
    
    // the booleans are not technically derived, they are saved from the constructor
    final boolean trimBtail;
    final boolean trimPolyedge;
    
    // some internally used items
    public final static int posIns = -2;
    public final static int posClip = -1;
    
    public BamfoRecord(SAMRecord record, boolean trimBtail, boolean trimPolyedge) {
        // copy a reference to the read
        this.record = record;
        
        // extract information about the read and store them in separate variables
        this.cigarelements = new ArrayList(record.getCigar().getCigarElements());
        this.readlength = record.getReadLength();
        this.startpos = record.getAlignmentStart();        
        this.pos = getBasePositions(cigarelements, startpos, readlength);
        this.maxN = getMaxIntron(cigarelements);
        this.bases = record.getReadBases();
        this.qualities = getFullQualities(record);
        this.recordname = record.getReadName();
        this.readhasindel = containsIndel(cigarelements);
        this.overlapstart = getOverlapStart(record);
        this.mapquality = record.getMappingQuality();
        
        // save the booleans
        this.trimBtail = trimBtail;
        this.trimPolyedge = trimPolyedge;
                
        // modify the derived data according to the booleans
        if (trimBtail) {
            trimBNtails(bases, qualities, pos);
        }
        if (trimPolyedge) {
            trimPolyPatternOnEdges(bases, pos);
        }
    }
    
    /**
     * Given a record, return an array specifying the position of each base.
     * This is trivial when the cigar is XXM, but is complicated when there are
     * insertions, deletions, splices, etc.
     *
     *
     * @param record
     * @return
     *
     * The returned positions are 1-based. Negative positions indicate special
     * features.
     *
     * -1 --> soft clip -2 --> insertion
     *
     */
    private static int[] getBasePositions(ArrayList<CigarElement> cigarelements, int startpos, int readlength) {

        int[] positions = new int[readlength];

        int nowpos = startpos;
        int nowindex = 0;

        for (int i = 0; i < cigarelements.size(); i++) {
            
            CigarElement ce = cigarelements.get(i);
            int celen = ce.getLength();

            switch (ce.getOperator()) {
                case M:
                    for (int k = 0; k < celen; k++) {
                        positions[nowindex] = nowpos;
                        nowpos++;
                        nowindex++;
                    }
                    break;
                case D:
                    nowpos += celen;
                    break;
                case N:
                    nowpos += celen;
                    break;
                case S:
                    for (int j = 0; j < celen; j++) {
                        positions[nowindex] = posClip;
                        nowindex++;
                    }
                    break;
                case I:
                    for (int j = 0; j < celen; j++) {
                        positions[nowindex] = posIns;
                        nowindex++;
                    }
                    break;
                default:
                    break;
            }            
        }

        return positions;
    }

    
    /**
     * qualities in a SAM record can be a full string or a '*' In the latter
     * case, this function returns an array of length equal to the record
     * string, but not initialized to anything (Java will put zeros).
     *
     * @param record
     * @return
     */
    public static byte[] getFullQualities(SAMRecord record) {
        byte[] qual = record.getBaseQualityString().getBytes();
        if (qual.length != record.getReadLength()) {
            qual = new byte[record.getReadLength()];            
        }
        return qual;
    }
    
    
    /**
     * for a record with a cigar, get the maximal number before the code 'N' for
     * intron
     *
     * @param record
     * @return
     */
    private static int getMaxIntron(ArrayList<CigarElement> cigarelements) {

        int maxN = 0;

        // look at each cigar element
        for (int i = 0; i < cigarelements.size(); i++) {
            CigarElement ce = cigarelements.get(i);

            if (ce.getOperator() == CigarOperator.N) {
                int herelen = ce.getLength();
                if (herelen > maxN) {
                    maxN = herelen;
                }
            }
        }

        return maxN;
    }

    /**
     *
     * @param recordcigar
     * @return
     *
     * true if the record contains an insertion or a deletion
     */
    private static boolean containsIndel(ArrayList<CigarElement> cigarelements) {
        for (int i = 0; i < cigarelements.size(); i++) {
            CigarElement ce = cigarelements.get(i);
            if (ce.getOperator() == CigarOperator.D || ce.getOperator() == CigarOperator.I) {
                return true;
            }
        }
        return false;
    }

    /**
     * gives the position of the first base where paired-end reads overlap.
     *
     * @param record
     * @return
     *
     * position of first overlap. If there is no overlap, Integer.MAX_Value
     *
     */
    private static int getOverlapStart(SAMRecord record) {

        // the logic applies only to paired reads found on the same chromosome
        if (record.getReadPairedFlag() && record.getMateReferenceIndex() == record.getReferenceIndex()) {
            int startpos = record.getAlignmentStart();
            int readend = record.getAlignmentEnd();
            int matestart = record.getMateAlignmentStart();
            if (matestart >= startpos && matestart < readend) {
                // there is overlap, and it starts at the where the mate begins
                return matestart;
            }
        }

        // if reached here, then there is no overlap
        return Integer.MAX_VALUE;

    }

    /**
     * assumes the arrays qualities and pos are at least two items long. Returns
     * nothing, but values in pos are changed (soft clipped) if the qualities
     * array has a poly-B tail at either start or end.
     *
     * @param qualities
     * @param pos
     */
    private static void trimBNtails(byte[] bases, byte[] qualities, int[] pos) {
        int readlength = qualities.length;
        if (readlength < 2) {
            return;
        }

        // trim from the start
        int index = 0;
        while (index < readlength && (qualities[index] == 'B' || bases[index] == 'N')) {
            pos[index] = posClip;
            bases[index] = 'N';
            index++;
        }

        //trim from the end
        index = readlength - 1;
        while (index >= 0 && (qualities[index] == 'B' || bases[index] == 'N')) {
            pos[index] = posClip;
            bases[index] = 'N';
            index--;
        }

    }

/**
     * assumes the arrays bases and pos are at least two items long. returns
     * nothing, but values in array pos can be changed (soft clipped) if the
     * start and end have poly-base patterns. What is trimmed is the poly-base
     * pattern plus one base.
     *
     * @param bases
     * @param pos
     * @param readlength
     *
     *
     */
    private static void trimPolyPatternOnEdges(byte[] bases, int[] pos) {

        int readlength = bases.length;
        if (readlength < 2) {
            return;
        }

        byte startbase = bases[0];
        byte endbase = bases[readlength - 1];

        // trim from the start
        int edgeindex = 1;
        if (bases[1] == startbase) {
            pos[0] = posClip;
            while (edgeindex < readlength && bases[edgeindex - 1] == startbase) {
                pos[edgeindex] = posClip;
                edgeindex++;
            }
        }

        // trim from the end
        edgeindex = readlength - 2;
        if (bases[edgeindex] == endbase) {
            pos[edgeindex + 1] = posClip;
            while (edgeindex >= 0 && bases[edgeindex + 1] == endbase) {
                pos[edgeindex] = posClip;
                edgeindex--;
            }
        }

    }
    
    /**
     * 
     * @return 
     * 
     * the value associated with the NM tag (number of mismatches to reference genome)
     * 
     */
    public int getNMtag() {        
        Object NMtag = record.getAttribute("NM");
        if (NMtag==null) {
            return 0;
        } else {
            return (Integer) NMtag;
        }        
    }
    
}
