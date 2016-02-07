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
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMSequenceRecord;

/**
 *
 * A helper class used when computing tracks.
 *
 * @author tkonopka
 */
class TracksCoverage extends TracksCompute {

    private final int tracktype;

    public TracksCoverage(BamfoSettings settings, File bamfile, File outdir, int tracktype) {
        super(settings, bamfile, outdir);
        this.tracktype = tracktype;
    }

    @Override
    void computeTrack(SAMFileReader inputSam) throws FileNotFoundException, IOException {

        int[] coverage = new int[1];
        coverage[0] = 0;

        int nowRef = -1;
        String nowRefName = "none";

        SAMFileHeader samHeader = inputSam.getFileHeader();

        // find out all the chromosome names from the header        
        int numchromosomes = samHeader.getSequenceDictionary().size();
        boolean[] chromoutput = new boolean[numchromosomes];

        // read each record, for each chromosome
        for (final SAMRecord samRecord : inputSam) {

            int recordReference = samRecord.getReferenceIndex();

            // check that the record is aligned and that is primary
            // and non-duplicate
            if (recordReference > -1 && !samRecord.getNotPrimaryAlignmentFlag()
                    && !samRecord.getReadUnmappedFlag() && !samRecord.getDuplicateReadFlag()) {

                // check if the record starts a new chromosome
                // if so, save the coverage and process next chromosome
                if (recordReference != nowRef) {
                    // save the coverage saved so far
                    if (nowRef != -1) {
                        chromoutput[nowRef] = true;
                        thresholdMinDepth(coverage, settings.getMindepth());
                        outputRleTrack(coverage, nowRefName, outdir);
                    }

                    // create a new coverage array for the new chromosome
                    SAMSequenceRecord ssr = samHeader.getSequence(recordReference);
                    int chromlen = ssr.getSequenceLength();
                    coverage = new int[chromlen];

                    nowRef = recordReference;
                    nowRefName = samRecord.getReferenceName();
                }

                // add the contribution of this read to the coverage
                updateTrack(coverage, samRecord);
            }
        } // end of loop over records

        // save the coverage on the last chromosome
        if (nowRef != -1) {
            chromoutput[nowRef] = true;
            thresholdMinDepth(coverage, settings.getMindepth());
            outputRleTrack(coverage, nowRefName, outdir);
        }

        // at the end, check that all chromosomes have been output
        // if not, coverage must have been zero        
        saveNotPresentChromosomes(chromoutput, samHeader, outdir, 0);

    }

    /**
     *
     * @param track
     * @param record
     */
    private void updateTrack(int[] track, SAMRecord record) {

        // ignore read if its mapquality is too low
        if (record.getMappingQuality() < settings.getMinmapqual()) {
            return;
        }

        // compute all needed derived qualities of the read via BamfoRecord
        BamfoRecord b2r = new BamfoRecord(record, settings.isTrimBtail(), settings.isTrimpolyedge());

        // the overlapstart needs to be updated because reads can be trimmed from start and finish
        int overlapstart = b2r.overlapstart;
        if (overlapstart < Integer.MAX_VALUE) {
            overlapstart += settings.getMinfromstart();
        }

        // determine range of the read that is genotypeable
        int imin = 0, imax = 0;
        if (record.getReadNegativeStrandFlag()) {
            imax = b2r.readlength - settings.getMinfromstart();
            imin = settings.getMinfromend();
        } else {
            imax = b2r.readlength - settings.getMinfromend();
            imin = settings.getMinfromstart();
        }
        byte minbasequal = settings.getMinbasequal();
        boolean NRef = settings.isNRef();

        // use read information to add info to the tracks
        switch (tracktype) {
            case TRACK_COVERAGE:
                // record coverage on high-quality bases
                updateHelper(track, b2r, overlapstart, imin, imax, minbasequal, false, true, false, NRef);
                break;
            case TRACK_READSTART:
                // like for coverage, but record only one position
                updateHelper(track, b2r, overlapstart, imin, imax, minbasequal, true, true, false, NRef);
                break;
            case TRACK_READEND:
                updateHelper(track, b2r, overlapstart, imin, imax, minbasequal, true, false, true, NRef);
                break;
            case TRACK_READSTARTEND:
                updateHelper(track, b2r, overlapstart, imin, imax, minbasequal, true, true, true, NRef);
                break;
            default:
                break;
        }
    }

    /**
     * * Helper function of modifyTrack. Loops over positions of a read and
     * modifies a track.
     *
     * Modifications are either to add to the coverage, or to add markers where
     * reads start/end.
     *
     * Most of the parameters
     *
     * @param track
     * @param pos
     * @param imin
     * @param imax
     * @param maxpos
     * @param qualities
     * @param bases
     * @param minbasequal
     * @param onlystart
     *
     * set to true to record only the first position (not whole coverage)
     *
     * @param fromstart
     *
     * set to true to compute read start positions or read coverage
     *
     * @param fromend
     *
     * set to true to record the end position of a read
     *
     * @return
     *
     * Nothing is returned, but the track array should be modified.
     *
     */
    private void updateHelper(int[] track, BamfoRecord b2r, int overlapstart,
            int imin, int imax, byte minbasequal,
            final boolean onlystart, final boolean fromstart, final boolean fromend,
            final boolean NRef) {

        if (fromstart) {
            // loop over the positions of the read and modify the track
            for (int i = imin; i < imax; i++) {
                // check that overlapping reads are not counted twice
                // and satisfy quality criteria
                if (b2r.pos[i] < overlapstart
                        && b2r.pos[i] >= 0
                        && b2r.qualities[i] >= minbasequal) {
                    if (NRef || b2r.bases[i] != 'N') {
                        track[b2r.pos[i] - 1]++;
                        if (onlystart) {
                            i = imax;
                        }
                    }
                } else {
                    i = imax;
                }
            }
        }

        if (fromend) {
            // loop over the positions of the read and modify the track
            for (int i = imax - 1; i >= imin; i--) {
                // check that overlapping reads are not counted twice
                if (b2r.pos[i] < overlapstart
                        && b2r.pos[i] >= 0
                        && b2r.qualities[i] >= minbasequal) {
                    if (NRef || b2r.bases[i] != 'N') {
                        track[b2r.pos[i] - 1]++;
                        i = -1;
                    }
                }
            }
        }

    }

    /**
     * Thresholds all coverage values below a threshold to zero.
     *
     * @param track
     *
     * coverage track. This object will be modified during the course of the
     * function.
     *
     * @param mindepth
     *
     * threshold. Values below this number will be set to zero.
     *
     */
    private void thresholdMinDepth(int[] track, int mindepth) {
        int tlen = track.length;
        for (int i = 0; i < tlen; i++) {
            if (track[i] < mindepth) {
                track[i] = 0;
            }
        }
    }
}
