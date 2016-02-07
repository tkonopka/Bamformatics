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
package bamfo.tracks;

import bamfo.utils.BamfoRecord;
import bamfo.utils.BamfoSettings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMSequenceRecord;

/**
 *
 * @author tkonopka
 */
class TracksMedMapQual extends TracksCompute {

    private final int CACHELOCATIONS = 128;

    /**
     * Inner class that will hold qualities of reads at a given locus. It is
     * just a convenient wrapper for an array of integers, with a function to
     * compute the median quality.
     */
    class MapQualsList {

        private ArrayList<Integer> qualities = new ArrayList<Integer>(128);

        public void add(int q) {
            qualities.add(q);
        }

        public double getMedian() {
            if (qualities.isEmpty()) {
                return -1;
            }
            Collections.sort(qualities);
            int qsize = qualities.size();
            int qmid = qsize / 2;
            if (qsize % 2 == 0) {
                // is even, median must be averaged over two items
                double a = qualities.get(qmid);
                double b = qualities.get(qmid - 1);
                return (a + b) / 2;
            } else {
                // qsize is odd, median is easy to get at qmid
                return (double) qualities.get(qsize / 2);
            }
        }
    }

    public TracksMedMapQual(BamfoSettings settings, File bamfile, File outdir) {
        super(settings, bamfile, outdir);
    }

    /**
     *
     * @param tracklength
     * @return
     *
     * a track of desired length with all elements set to -1, indicating not
     * available mapping quality information.
     *
     */
    private double[] makeNegOneTrack(int tracklength) {
        double[] ans = new double[tracklength];
        for (int i = 0; i < ans.length; i++) {
            ans[i] = -1;
        }
        return ans;
    }

    @Override
    void computeTrack(SAMFileReader inputSam) throws FileNotFoundException, IOException {
        double[] medmapqual = makeNegOneTrack(1);

        int nowpos, lastdrain = 1;
        int nowRef = -1, nowRefLen = 0;
        String nowRefName = "none";

        SAMFileHeader samHeader = inputSam.getFileHeader();

        // find out all the chromosome names from the header        
        int numchromosomes = samHeader.getSequenceDictionary().size();
        boolean[] chromoutput = new boolean[numchromosomes];

        // will have to cache some information about mapping qualities at loci
        HashMap<Integer, MapQualsList> qualinfo = new HashMap<Integer, MapQualsList>(2 * CACHELOCATIONS);

        // read each record, for each chromosome
        for (final SAMRecord samRecord : inputSam) {

            int recordReference = samRecord.getReferenceIndex();

            // check that the record is aligned and that is primary
            // and non-duplicate
            if (recordReference > -1 && !samRecord.getNotPrimaryAlignmentFlag()
                    && !samRecord.getReadUnmappedFlag() && !samRecord.getDuplicateReadFlag()) {

                nowpos = samRecord.getAlignmentStart();

                // check if the record starts a new chromosome
                // if so, save the coverage and process next chromosome
                if (recordReference != nowRef) {
                    // save the coverage saved so far
                    if (nowRef != -1) {
                        lastdrain = getMedMapQual(medmapqual, qualinfo, lastdrain, 1 + nowRefLen);
                        chromoutput[nowRef] = true;
                        outputRleTrack(medmapqual, nowRefName, outdir);
                    }

                    // create a new coverage array for the new chromosome
                    SAMSequenceRecord ssr = samHeader.getSequence(recordReference);
                    int chromlen = ssr.getSequenceLength();
                    medmapqual = makeNegOneTrack(chromlen);

                    nowRef = recordReference;
                    nowRefName = samRecord.getReferenceName();
                    nowRefLen = (samHeader.getSequence(nowRef)).getSequenceLength();
                    lastdrain = 1;

                    // reset the qualinfo map. Could be emptied, but I recreate it instead
                    qualinfo = new HashMap<Integer, MapQualsList>(2 * CACHELOCATIONS);
                }

                // parse information from this record to 
                // add the contribution of this read to the coverage
                updateQualInfo(qualinfo, samRecord);

                // perhaps drain the map if the fill index has run too far ahead of the drain index
                if (nowpos - lastdrain > CACHELOCATIONS && nowRefLen - nowpos > CACHELOCATIONS) {
                    lastdrain = getMedMapQual(medmapqual, qualinfo, lastdrain, nowpos);
                }
            }
        } // end of loop over records

        // if there is still something left in the info map, so drain it here before exiting
        if (nowRef != -1) {
            lastdrain = getMedMapQual(medmapqual, qualinfo, lastdrain, 1 + nowRefLen);
            chromoutput[nowRef] = true;
            outputRleTrack(medmapqual, nowRefName, outdir);
        }

        saveNotPresentChromosomes(chromoutput, samHeader, outdir, -1);

    }

    private int getMedMapQual(double[] medmapqual, HashMap<Integer, MapQualsList> qualinfo, int startpos, int endpos) {

        // get the medians for all loci in the range
        // also remove data from the hashmap to save space
        for (int i = startpos; i < endpos; i++) {
            if (qualinfo.containsKey(i)) {
                medmapqual[i] = qualinfo.get(i).getMedian();
                qualinfo.remove(i);
            }
        }
        return endpos;
    }

    private void updateQualInfo(HashMap<Integer, MapQualsList> qualinfo, SAMRecord record) {

        // do not ignore read juding by its mapping quality

        // compute all needed derived qualities of the read via BamfoRecord
        BamfoRecord b2r = new BamfoRecord(record, settings.isTrimBtail(), settings.isTrimpolyedge());

        // check if the read is paired-end and if the pairs overlap.
        // set maxpos to the zero-based coordinate of the overlap
        int overlapstart = b2r.overlapstart;
        if (overlapstart < Integer.MAX_VALUE) {
            overlapstart += settings.getMinfromstart();
        }

        // determine range of the read that is genotypeable
        int imin, imax;
        if (record.getReadNegativeStrandFlag()) {
            imax = b2r.readlength - settings.getMinfromstart();
            imin = settings.getMinfromend();
        } else {
            imax = b2r.readlength - settings.getMinfromend();
            imin = settings.getMinfromstart();
        }

        int[] pos = b2r.pos;
        for (int i = imin; i < imax; i++) {
            if (pos[i] > 0) {

                MapQualsList quals = qualinfo.get(pos[i]);
                if (quals == null) {
                    quals = new MapQualsList();
                    qualinfo.put(pos[i], quals);
                }

                // save information about this base
                quals.add(b2r.mapquality);
            }
        }



    }
}
