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
package bamfo.tracks;

import bamfo.utils.BamfoCommon;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.OutputStreamMaker;
import jsequtils.file.RleWriter;
import net.sf.samtools.*;

/**
 * Utility to compute pileup entropy of an alignment. It takes an alignment and
 * looks at running windows of certain length. In each window, it makes a matrix
 * with rows (positions) and columns (bases like ATCG, insertion, deletion). It
 * computes the Shannon entropy of this matrix and outputs it.
 *
 * The output is a directory with one file per chromosome.
 *
 *
 * @author tkonopka
 */
public class BamfoEntropy implements Runnable {

    private int windowsize = 5;
    //private int filldraininterval = 128;
    private File bamfile = null;
    private File outdir = null;
    private DecimalFormat dblformat = new DecimalFormat("0.0000");
    // for the Runnable
    private boolean isReady = false;

    private static void printBamEntropyHelp() {
        System.out.println("Bamformatics entropy: compute the pileup entropy of an alignment");
        System.out.println();
        System.out.println(" --bam <File>           - input alignment file");
        System.out.println(" --output <File>        - output directory");
        System.out.println(" --window <int>         - width of entropy window");
        System.out.println(" --dblformat <String>   - string determining how floating point numbers are displayed");
        System.out.println();
    }

    /**
     *
     * @param args
     *
     * command line arguments
     *
     */
    public BamfoEntropy(String[] args) {

        if (args == null || args.length == 0) {
            printBamEntropyHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseBamEntropyParameters(args)) {
            return;
        }

        isReady = true;
    }

    private boolean parseBamEntropyParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // options will be 
        // window - size of window where to compute the entropy        
        prs.accepts("window").withRequiredArg().ofType(Integer.class);
        // bam - input bam file
        prs.accepts("bam").withRequiredArg().ofType(File.class);
        // output - output directory
        prs.accepts("output").withRequiredArg().ofType(File.class);
        // dblformat - how to write the entropy
        prs.accepts("dblformat").withRequiredArg().ofType(String.class);


        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            System.out.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        if (options.has("bam")) {
            bamfile = (File) options.valueOf("bam");
            if (!bamfile.canRead()) {
                System.out.println("bam file is not readable");
                return false;
            }
        } else {
            System.out.println("missing parameter bam");
            return false;
        }

        if (options.has("window")) {
            windowsize = (Integer) options.valueOf("window");
        }

        if (options.has("output")) {
            outdir = (File) options.valueOf("output");
            if (!outdir.exists()) {
                if (outdir.mkdirs()) {
                    // everything is ok
                } else {
                    System.out.println("Could not create output directory " + outdir.getAbsolutePath());
                    return false;
                }
            } else {
                System.out.println("Directory " + outdir.getAbsolutePath() + " already exists. Contents will be overwritten");
            }
        } else {
            System.out.println("missing required parameter output");
            return false;
        }

        if (options.has("dblformat")) {
            dblformat = new DecimalFormat((String) options.valueOf("dblformat"));
        }


        return true;
    }

    private void computePileupEntropy(SAMFileReader inputSam, File outdir, int windowsize) throws IOException {

        SAMFileHeader samHeader = inputSam.getFileHeader();

        // indexes will walk along the chromosomes
        int indexfill = 1;
        int indexdrain = 1;
        // filldraininterval will determine how much information to cache in memory before computing the entropy        
        int filldraininterval = 4 * windowsize;
        double[] entropy = null;

        int nowRefLen = 0;

        HashMap<Integer, LocusPileup> chrinfo = null;

        // work chromosome by chromosome
        int nowRef = -1;
        String nowRefName = "none";

        // read each record, for each chromosome
        for (final SAMRecord samRecord : inputSam) {

            int recordReference = samRecord.getReferenceIndex();

            // check if this record is aligned and non-duplicate
            if (recordReference > -1 && !samRecord.getReadUnmappedFlag()
                    && !samRecord.getDuplicateReadFlag()) {

                indexfill = samRecord.getAlignmentStart();

                // check if the record starts a new chromosome
                // if so, save the coverage and process next chromosome
                if (recordReference != nowRef) {
                    // save the coverage saved so far
                    if (nowRef != -1) {
                        indexdrain = drainEntropyChrInfo(entropy, chrinfo, windowsize, indexdrain, nowRefLen);
                        OutputStream os = OutputStreamMaker.makeOutputStream(outdir.getCanonicalPath(), nowRefName + ".txt.gz");
                        RleWriter.write(os, entropy, true, dblformat);
                        os.close();
                    }

                    // get information about this chromosome
                    nowRef = recordReference;
                    nowRefName = samRecord.getReferenceName();
                    SAMSequenceRecord ssr = samHeader.getSequence(recordReference);
                    nowRefLen = ssr.getSequenceLength();
                    entropy = new double[nowRefLen];

                    // initialize a new chrinfo object that will store pileup information
                    chrinfo = new HashMap<Integer, LocusPileup>(2 * filldraininterval, 0.75f);
                    indexdrain = 1;

                    System.gc();
                }

                // add the contribution of this read to the coverage
                updatePileup(chrinfo, samRecord);

                // perhaps drain the chrinfo if the fill index has run too far ahead of the drain index
                if (indexfill - indexdrain > filldraininterval && nowRefLen - indexfill > windowsize) {
                    //printLocusPileups(chrinfo, indexdrain, indexfill);
                    indexdrain = drainEntropyChrInfo(entropy, chrinfo, windowsize, indexdrain, indexfill);
                }

            }

        }

        if (nowRef != -1) {
            indexdrain = drainEntropyChrInfo(entropy, chrinfo, windowsize, indexdrain, nowRefLen);
            OutputStream os = OutputStreamMaker.makeOutputStream(outdir.getCanonicalPath(), nowRefName + ".txt.gz");
            RleWriter.write(os, entropy, true, dblformat);
            os.close();
        }

    }

    private void printLocusPileups(HashMap<Integer, LocusPileup> chrinfo, int from, int to) {

        for (int pos = from; pos < to; pos++) {
            LocusPileup lp = chrinfo.get(pos);
            if (lp != null) {
                System.out.print(pos + "\t");
                for (int i = 0; i < BamfoCommon.numcodes; i++) {
                    System.out.print("\t" + lp.counts[i]);
                }
                System.out.println();
            }
        }
    }

    private void updateLocusPileup(HashMap<Integer, LocusPileup> chrinfo, int position, int basecode) {

        // check if the position is already in the chrinfo
        LocusPileup lp = chrinfo.get(position);
        if (lp == null) {
            //System.out.println("Creating a LP object at "+position);
            lp = new LocusPileup(BamfoCommon.numcodes);
            chrinfo.put(position, lp);
        } else {
            //System.out.println("reusing LP at "+position);
        }

        // here, lp is the object than contains information about the locus
        // increment the necessary counts
        lp.counts[basecode]++;

    }

    private void updatePileup(HashMap<Integer, LocusPileup> chrinfo, SAMRecord record) {

        if (chrinfo == null) {
            return;
        }

        Cigar recordcigar = record.getCigar();
        int startpos = record.getAlignmentStart();
        // convert to zero-based coordinate
        int pos = startpos;

        ArrayList<CigarElement> recordce = new ArrayList(recordcigar.getCigarElements());
        int[] basecodes = BamfoCommon.basesToZeroToFour(record.getReadBases());
        int readindex = 0;

        for (int i = 0; i < recordce.size(); i++) {
            CigarElement ce = recordce.get(i);
            if (ce.getOperator() == CigarOperator.M) {
                // augment the coverage at the locations
                for (int k = 0; k < ce.getLength(); k++) {
                    updateLocusPileup(chrinfo, pos, basecodes[readindex]);
                    readindex++;
                    pos++;
                }
            } else if (ce.getOperator() == CigarOperator.D) {
                // take note that at this locus there is a deletion starting
                updateLocusPileup(chrinfo, pos, BamfoCommon.codeDel);
                // advance the chromosome position by many, but do not advance the read position
                pos += ce.getLength();
            } else if (ce.getOperator() == CigarOperator.N || ce.getOperator() == CigarOperator.P) {
                // do not increase the counts for this
                // advance the chromosome position by many, but do not advance the read position
                pos += ce.getLength();
            } else if (ce.getOperator() == CigarOperator.I) {
                // here the read skips a portion of the reference coordinate system
                updateLocusPileup(chrinfo, pos, BamfoCommon.codeIns);
                // here advance the index on the read bases but not on the chromosome                
                readindex += ce.getLength();
            } else if (ce.getOperator() == CigarOperator.S) {
                // here the read has been clipped
                updateLocusPileup(chrinfo, pos, BamfoCommon.codeClip);
                readindex += ce.getLength();
            } else {
                // whether the element is an insertion, or soft clip, or anything else, don't do anything
                System.out.println("Unrecognized cigar string " + record.getCigarString() + " in record " + record.getReadName());
            }
        }


    }

    /**
     * computes entropy of the pileup.
     *
     * @param entropy
     *
     * array with entropy. This will be modified on certain locations
     *
     * @param chrinfo
     *
     * a hashmap with LocusPilepup information
     *
     * @param windowsize
     *
     * window where the entropy is computed
     *
     * @param startpos
     *
     * start of interval where entropy will be computed
     *
     * @param endpos
     *
     * end of interval for computation
     *
     *
     * @return
     *
     * after computing the entropy on an interval, this returns the start
     * position of the remaining regions.
     *
     *
     */
    private int drainEntropyChrInfo(double[] entropy, HashMap<Integer, LocusPileup> chrinfo,
            int windowsize, int startpos, int endpos) {

        //System.out.println("draining positions " + startpos + "-" + endpos);

        double nowN;

        double sumXlogXstart, sumXlogXmiddle, sumXlogXend;
        double sumXstart, sumXmiddle, sumXend;

        // int entropylen = entropy.length;

        double log2 = Math.log(2.0);

        // windowoffset will be the center of the window. e.g. if windowsize is 5, this will give 2
        // i.e. the third element inside the element is the center
        int windowoffset = (windowsize / 2);
        int lastpos = endpos - windowoffset;

        LocusPileup lp;

        // get information about the first position
        lp = chrinfo.get(startpos);
        sumXstart = 0.0;
        sumXlogXstart = 0.0;
        if (lp != null) {
            for (int i = 0; i < BamfoCommon.numcodes; i++) {
                sumXstart += lp.counts[i];
                if (lp.counts[i] > 0) {
                    sumXlogXstart += (double) lp.counts[i] * Math.log((double) lp.counts[i]) / log2;
                }
            }
        }

        // get the middle of window
        sumXmiddle = 0.0;
        sumXlogXmiddle = 0.0;
        for (int pos = startpos; pos < startpos + windowsize - 1; pos++) {
            lp = chrinfo.get(pos);
            if (lp != null) {
                for (int i = 0; i < BamfoCommon.numcodes; i++) {
                    sumXmiddle += lp.counts[i];
                    if (lp.counts[i] > 0) {
                        sumXlogXmiddle += (double) lp.counts[i] * Math.log((double) lp.counts[i]) / log2;
                    }
                }
            }
        }

        // get information about the last position in the window
        lp = chrinfo.get(startpos - 1 + windowsize);
        sumXend = 0.0;
        sumXlogXend = 0.0;
        if (lp != null) {
            for (int i = 0; i < BamfoCommon.numcodes; i++) {
                sumXend += lp.counts[i];
                if (lp.counts[i] > 0) {
                    sumXlogXend += (double) lp.counts[i] * Math.log((double) lp.counts[i]) / log2;
                }
            }
        }

        // get total counts within the window
        nowN = sumXmiddle + sumXend;

        // compute the entropy of the first item. Here the formula is the same
        // as inside the loop below, but the sumXstart and sumXlogXstart are zero by construction
        if (nowN > 0 && chrinfo.get(startpos + windowoffset) != null) {
            entropy[startpos - 1 + windowoffset] = -(sumXlogXmiddle + sumXlogXend
                    - ((sumXmiddle + sumXend) * (Math.log(nowN) / log2))) / nowN;
        }

        for (int pos = startpos + 1; pos < lastpos; pos++) {

            // adjust the sums for the middle by adding the old end and substracting the old beginning
            sumXmiddle += sumXend - sumXstart;
            sumXlogXmiddle += sumXlogXend - sumXlogXstart;
            nowN -= sumXstart;

            // get the first pos in the window (will be subtracted on next iteration)
            lp = chrinfo.get(pos);
            sumXstart = 0.0;
            sumXlogXstart = 0.0;
            if (lp != null) {
                for (int i = 0; i < BamfoCommon.numcodes; i++) {
                    sumXstart += lp.counts[i];
                    if (lp.counts[i] > 0) {
                        sumXlogXstart += (double) lp.counts[i] * Math.log((double) lp.counts[i]) / log2;
                    }
                }
            }

            // get the last pos in window (will be added in this iteration)
            lp = chrinfo.get(pos + windowsize - 1);
            sumXend = 0.0;
            sumXlogXend = 0.0;
            if (lp != null) {
                for (int i = 0; i < BamfoCommon.numcodes; i++) {
                    sumXend += lp.counts[i];
                    if (lp.counts[i] > 0) {
                        sumXlogXend += (double) lp.counts[i] * Math.log((double) lp.counts[i]) / log2;
                    }
                }
            }

            // adjust the nowN
            nowN += sumXend;

            // compute the entropy for this window.
            // record it if there is coverage on this location
            if (nowN > 0 && (chrinfo.get(pos + windowoffset) != null)) {
                entropy[pos - 1 + windowoffset] = -(sumXlogXmiddle + sumXlogXend
                        - ((sumXmiddle + sumXend) * (Math.log(nowN) / log2))) / nowN;
            }

        }



        // clean up the chrinfor object, i.e. remove information about loci that will no longer be required
        for (int i = startpos; i < lastpos - 1; i++) {
            if (chrinfo.containsKey(i)) {
                chrinfo.remove(i);
            }
        }

        return lastpos;
    }

    /**
     * After the utility is initialized, it has to be "executed" by invoking
     * this method. If initialization failed, this method does not do anything.
     *
     */
    @Override
    public void run() {
        if (!isReady) {
            return;
        }

        // start processing, open the SAM file and start computing
        SAMFileReader inputSam = new SAMFileReader(bamfile);
        try {
            computePileupEntropy(inputSam, outdir, windowsize);
        } catch (IOException ex) {
            System.out.println("Error computing entropy: " + ex.getMessage());
        }
        inputSam.close();
    }
}
