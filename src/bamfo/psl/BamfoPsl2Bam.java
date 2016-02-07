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
package bamfo.psl;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.genome.GenomeInfo;
import net.sf.samtools.*;

/**
 * Utility to convert output from BLAT into a SAM file. The program works only
 * on paired reads. It requires two sets of psl files for first and second
 * mates, and two sets of fastq files with read sequence and quality scores. The
 * program will look at multiple outputs from BLAT, chose a "best" pairing and
 * alignment, and output this into a "best.bam" file. A certain number of
 * non-best pairings and alignemntss are output into a separate bam file.
 *
 *
 * @author tkonopka
 */
public class BamfoPsl2Bam implements Runnable {

    private PslComparator pslCompare = new PslComparator();
    private Psl2multibamSettings settings;
    private boolean isReady = false;
    private boolean isPaired = false;    

    private static void printPsl2BamHelp() {
        System.out.println("Bamformatics psl2bam: a tool for converting BLAT psl output to bam files");
        System.out.println();
        System.out.println(" --genome <file>           - genome fasta file (better if indexed)");
        System.out.println(" --psl <file>              - psl files (no headers please)");
        System.out.println("                             two of these are required");
        System.out.println(" --fastq <file>            - fastq files matching psl files (reads in the same order please)");
        System.out.println("                             two of these are required");
        System.out.println(" --output <String>         - prefix for output files");
        System.out.println(" --subopt <int>            - maximal score penalty allowed relative to best match");
        System.out.println(" --insertsize <int>        - maximal distance between paired mates");
        System.out.println(" --readgroup <string>      - string added as read group");
        System.out.println();
    }

    private Psl2multibamSettings parseP2MBParameters(String[] args) {

        Psl2multibamSettings settings = new Psl2multibamSettings();

        OptionParser prs = new OptionParser();
        prs.accepts("help");
        prs.accepts("genome").withRequiredArg().ofType(File.class);
        prs.accepts("psl").withRequiredArg().ofType(File.class);
        prs.accepts("fastq").withRequiredArg().ofType(File.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);
        prs.accepts("subopt").withRequiredArg().ofType(Integer.class);
        prs.accepts("insertsize").withRequiredArg().ofType(Integer.class).defaultsTo(1000);
        prs.accepts("readgroup").withRequiredArg().ofType(String.class).defaultsTo("blat");

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            System.out.println("Error parsing command line parameters\n" + ex.getMessage());
            return null;
        }

        if (options.has("genome")) {
            settings.genomefile = (File) options.valueOf("genome");
            if (!settings.genomefile.canRead()) {
                System.out.println("Cannot read header file: " + settings.genomefile.getName());
                return null;
            }
        } else {
            System.out.println("Missing option --genome");
            return null;
        }

        if (options.has("readgroup")) {
            settings.readgroup = (String) options.valueOf("readgroup");
        }

        if (options.has("psl")) {
            List psllist = options.valuesOf("psl");
            settings.psl1 = (File) psllist.get(0);
            if (!settings.psl1.canRead()) {
                System.out.println("Cannot read psl file: " + settings.psl1.getName());
                return null;
            }
            if (psllist.size() > 1) {
                settings.psl2 = (File) psllist.get(1);
                if (!settings.psl2.canRead()) {
                    System.out.println("Cannot read psl file: " + settings.psl2.getName());
                    return null;
                }
            }
        }

        if (options.has("fastq")) {
            List fastqlist = options.valuesOf("fastq");
            settings.fastq1 = (File) fastqlist.get(0);
            if (!settings.fastq1.canRead()) {
                System.out.println("Cannot read fastq file: " + settings.fastq1.getName());
                return null;
            }
            if (fastqlist.size() > 1) {
                settings.fastq2 = (File) fastqlist.get(1);
                if (!settings.fastq2.canRead()) {
                    System.out.println("Cannot read fastq file: " + settings.fastq2.getName());
                    return null;
                }
            }
        }

        if (options.has("output")) {
            settings.outprefix = (String) options.valueOf("output");
        } else {
            System.out.println("Missing option --output");
            return null;
        }

        if (options.has("subopt")) {
            settings.maxsubopt = (Integer) options.valueOf("subopt");
        }

        if (options.has("insertsize")) {
            settings.maxinsertsize = (Integer) options.valueOf("insertisze");
        }

        return settings;
    }

    public BamfoPsl2Bam(String[] args) {

        // parse the input
        if (args == null || args.length == 0) {
            printPsl2BamHelp();
            return;
        }

        // get the settings from the command line and verify some of them
        settings = parseP2MBParameters(args);
        if (settings == null) {
            return;
        }

        // for now, require that the header is specified and that there are two psl files
        if (settings.psl1 == null && settings.psl2 == null) {
            System.out.println("Psl file(s) must be specified");
            return;
        }
        if (settings.fastq1 == null && settings.fastq2 == null) {
            System.out.println("Fastq file(s) must be specified");
            return;
        }
        if (settings.psl2 != null && settings.fastq2 != null) {
            isPaired = true;
        } else {
            if ((settings.psl2 == null && settings.fastq2 != null)
                    || (settings.psl2 != null && settings.fastq2 == null)) {
                System.out.println("Number of psl and fastq files do not match");
                return;
            }
            isPaired = false;
        }

        // for now, require that the header is specified
        if (settings.genomefile == null) {
            System.out.println("Genome must be specified");
            return;
        }

        isReady = true;

    }

    /**
     * create a header object complete with a HD and SQ lines
     *
     * @param seqinfo
     * @return
     */
    private SAMFileHeader makeCustomSAMFileHeader(GenomeInfo geninfo, String readgroup) {

        SAMFileHeader header = new SAMFileHeader();
        header.setTextHeader("@HD	VN:1.0 SO:unsorted");
        
        for (int i = 0; i < geninfo.getNumChromosomes(); i ++) {
            header.addSequence(new SAMSequenceRecord(geninfo.getChrName(i), geninfo.getChrLength(i)));
        }

        // add a read group line to the header with the string readgroup specifying the 
        // the actual RG name, the sample, and library
        SAMReadGroupRecord rgr = new SAMReadGroupRecord(readgroup);
        rgr.setPlatform("ILLUMINA");
        rgr.setLibrary(readgroup);
        rgr.setSample(readgroup);
        header.addReadGroup(new SAMReadGroupRecord(readgroup, rgr));

        return header;
    }

    private void makeSingleEndMultiBam(Psl2multibamSettings settings) throws IOException {
        
        // load information about the genome
        GenomeInfo geninfo = new GenomeInfo(settings.genomefile);
        
        // create buffered readers to read from the fastq files and the psl
        BufferedReader brfastq1 = BufferedReaderMaker.makeBufferedReader(settings.fastq1);
        BufferedReader brpsl1 = BufferedReaderMaker.makeBufferedReader(settings.psl1);

        // create the output file writers and set the appropriate headers
        SAMFileWriter bestSam, otherSam;

        // create SAMFileHeaders complete with sequence information 
        SAMFileHeader uniqueheader = makeCustomSAMFileHeader(geninfo, settings.readgroup);
        SAMFileHeader otherheader = makeCustomSAMFileHeader(geninfo, settings.readgroup);
        SAMFileHeader hh = makeCustomSAMFileHeader(geninfo, settings.readgroup);

        uniqueheader.addComment("Unique best alignments found by blat");
        otherheader.addComment("Multiple best or sub-optimal alignments found by blat");

        File bestfile = new File(settings.outprefix + ".best.bam");
        File otherfile = new File(settings.outprefix + ".other.bam");

        bestSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                uniqueheader, true, bestfile);
        otherSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                otherheader, true, otherfile);

        // start reading from the input files and creating the output bams
        
        ArrayList<String> fastq1 = new ArrayList<String>(4);
        PslEntry tempPsl1 = new PslEntry();
        ArrayList<PslEntry> psls1 = new ArrayList<PslEntry>(8);

        boolean loadpsl1 = true;

        while (readFastq(brfastq1, fastq1)) {

            // read information from the Psl
            if (loadpsl1) {
                tempPsl1 = readPsl(brpsl1, psls1, tempPsl1);
            }

            // verify that the three query names are the same
            String qname1 = fastq1.get(0);
            //System.out.println(qname1+"\t"+qname2);
            //System.out.println(psls1.get(0).Qname+"\t"+psls2.get(0).Qname);

            // check if the loaded psl entries match the fastq
            boolean match1 = qname1.equals(psls1.get(0).Qname);
            if (match1) {
                loadpsl1 = true;
            } else {
                loadpsl1 = false;
            }

            // now do the pairing, and placing into the correct bams            
            if (match1) {
                processSingleRead(psls1, fastq1, bestSam, otherSam, hh, settings.maxsubopt, true, true, settings.readgroup);
            }
        }


        // close all input and output files for politeness
        bestSam.close();
        otherSam.close();
        brfastq1.close();
        brpsl1.close();
    }

    private void makePairedEndMultiBam(Psl2multibamSettings settings) throws IOException {

        // load information about the genome
        GenomeInfo geninfo = new GenomeInfo(settings.genomefile);
                
        // create buffered readers to read from the fastq files and the psl
        BufferedReader brfastq1 = BufferedReaderMaker.makeBufferedReader(settings.fastq1);
        BufferedReader brfastq2 = BufferedReaderMaker.makeBufferedReader(settings.fastq2);
        BufferedReader brpsl1 = BufferedReaderMaker.makeBufferedReader(settings.psl1);
        BufferedReader brpsl2 = BufferedReaderMaker.makeBufferedReader(settings.psl2);

        // create the output file writers and set the appropriate headers
        SAMFileWriter bestSam, otherSam;

        // create SAMFileHeaders complete with sequence information 
        SAMFileHeader uniqueheader = makeCustomSAMFileHeader(geninfo, settings.readgroup);
        SAMFileHeader otherheader = makeCustomSAMFileHeader(geninfo, settings.readgroup);
        SAMFileHeader hh = makeCustomSAMFileHeader(geninfo, settings.readgroup);

        uniqueheader.addComment("Unique best alignments found by blat");
        otherheader.addComment("Multiple best or sub-optimal alignments found by blat");

        File bestfile = new File(settings.outprefix + ".best.bam");
        File otherfile = new File(settings.outprefix + ".other.bam");

        bestSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                uniqueheader, true, bestfile);
        otherSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                otherheader, true, otherfile);

        // start reading from the input files and creating the output bams
        boolean done = false;

        ArrayList<String> fastq1 = new ArrayList<String>(4);
        ArrayList<String> fastq2 = new ArrayList<String>(4);
        PslEntry tempPsl1 = new PslEntry();
        PslEntry tempPsl2 = new PslEntry();
        ArrayList<PslEntry> psls1 = new ArrayList<PslEntry>(8);
        ArrayList<PslEntry> psls2 = new ArrayList<PslEntry>(8);

        boolean loadpsl1 = true;
        boolean loadpsl2 = true;

        while (!done && readFastq(brfastq1, fastq1) && readFastq(brfastq2, fastq2)) {

            // read information from the Psl
            if (loadpsl1) {
                tempPsl1 = readPsl(brpsl1, psls1, tempPsl1);
            }
            if (loadpsl2) {
                tempPsl2 = readPsl(brpsl2, psls2, tempPsl2);
            }

            // verify that the three query names are the same
            String qname1 = fastq1.get(0);
            String qname2 = fastq2.get(0);
            //System.out.println(qname1+"\t"+qname2);
            //System.out.println(psls1.get(0).Qname+"\t"+psls2.get(0).Qname);
            boolean match1 = qname1.equals(psls1.get(0).Qname);
            boolean match2 = qname1.equals(psls2.get(0).Qname);
            if (qname1.equals(qname2)) {
                // check if the psl query names entries match the current fastq names
                if (match1 && match2) {
                    // everything is matching
                    //System.out.println("ok " + qname1 + " ok");                    
                    loadpsl1 = true;
                    loadpsl2 = true;
                } else if (match1 && !match2) {
                    //System.out.println("skipping "+qname1);
                    loadpsl1 = true;
                    loadpsl2 = false;
                } else if (match2 && !match1) {
                    //System.out.println("skipping "+qname2);
                    loadpsl1 = false;
                    loadpsl2 = true;
                } else {
                    //System.out.println("skipping "+qname1+" and "+qname2);
                    loadpsl1 = false;
                    loadpsl2 = false;
                }
            } else {
                System.out.println("Fastq files do not match: " + qname1 + " " + qname2);
                done = true;
            }

            // now do the pairing, and placing into the correct bams
            if (!done) {
                if (match1 && match2) {
                    processPairedReads(psls1, psls2, fastq1, fastq2, bestSam, otherSam, hh,
                            settings.maxsubopt, settings.maxinsertsize, settings.readgroup);
                } else {
                    if (match1) {
                        processSingleRead(psls1, fastq1, bestSam, otherSam, hh, settings.maxsubopt, true, true, settings.readgroup);
                    } else if (match2) {
                        processSingleRead(psls2, fastq2, bestSam, otherSam, hh, settings.maxsubopt, true, false, settings.readgroup);
                    } else {
                        // both do not match, don't do anything...
                    }
                }
            }
        }

        // close all input and output files for politeness
        bestSam.close();
        otherSam.close();
        brfastq1.close();
        brfastq2.close();
        brpsl1.close();
        brpsl2.close();

    }

    private String complementSequence(String seq) {
        //System.out.println("seq: " + seq);
        StringBuilder complement = new StringBuilder(seq.length() + 1);
        for (int i = seq.length() - 1; i >= 0; i--) {
            switch (seq.charAt(i)) {
                case 'A':
                    complement.append('T');
                    break;
                case 'T':
                    complement.append('A');
                    break;
                case 'C':
                    complement.append('G');
                    break;
                case 'G':
                    complement.append('C');
                    break;
                default:
                    complement.append('N');
                    break;
            }
        }
        //System.out.println("cpl: " + complement.toString());
        return complement.toString();
    }

    private SAMRecord createSAMRecord(PslEntry entry, ArrayList<String> fastq, SAMFileHeader hh,
            int numsubopt, int relpenalty, boolean paired, boolean firstread, String readgroup) {

        //System.out.println("in create single");        
        SAMRecord sr = new SAMRecord(hh);
        if (paired) {
            sr.setFlags(1);
        } else {
            sr.setFlags(0);
        }
        sr.setAlignmentStart(entry.Tstart + 1);
        sr.setCigarString(entry.getCigar());
        sr.setReferenceName(entry.Tname);
        sr.setReadName(entry.Qname);
        sr.setMappingQuality(Math.min(254, entry.match));
        if (paired) {
            sr.setMateUnmappedFlag(true);
            sr.setFirstOfPairFlag(firstread);
            sr.setSecondOfPairFlag(!firstread);
        }
        if (entry.strand == '-') {
            sr.setReadNegativeStrandFlag(true);
            sr.setReadBases(complementSequence(fastq.get(1)).getBytes());
            sr.setBaseQualityString(new StringBuffer(fastq.get(3)).reverse().toString());
        } else {
            sr.setReadNegativeStrandFlag(false);
            sr.setReadBases(fastq.get(1).getBytes());
            sr.setBaseQualityString(fastq.get(3));
        }
        sr.setAttribute("sb", numsubopt);
        sr.setAttribute("rp", relpenalty);
        sr.setAttribute("RG", readgroup);

        return sr;
    }

    /**
     *
     * @param psls
     * @param fastq
     * @param bestSam
     * @param otherSam
     * @param hh
     * @param maxsubopt
     * @param paired
     *
     * set to true if this is a read that should be paired, but isn't
     *
     * @param firstread
     *
     * only relevant for paired reads but where only one is mapped, set to true
     * if the read is the first in the pair
     *
     */
    private void processSingleRead(ArrayList<PslEntry> psls, ArrayList<String> fastq,
            SAMFileWriter bestSam, SAMFileWriter otherSam,
            SAMFileHeader hh, int maxsubopt, boolean paired, boolean firstread, String readgroup) {

        // sort the psl entries by blast scores               
        Collections.sort(psls, pslCompare);

        // the best score is at location 0.
        // check if it is the unique best read
        PslEntry ee = psls.get(0);
        int bestscore = ee.match;

        int bestindex = 0;
        if (psls.size() > 1 && ee.match == psls.get(1).match) {
            bestindex = -1;
        }

        int numsubopt = getNumberWithinScoreRange(psls, maxsubopt);

        // write the best unique alignment to the bestSam
        if (bestindex == 0 && !paired) {
            SAMRecord sr = createSAMRecord(ee, fastq, hh, numsubopt, bestscore - ee.match, paired, firstread, readgroup);
            bestSam.addAlignment(sr);
        } else {
            bestindex = -1;
        }

        // write the other sub-optimal alignemnts to the otherSam
        for (int j = bestindex + 1; j < numsubopt; j++) {
            ee = psls.get(j);
            SAMRecord sr = createSAMRecord(ee, fastq, hh, numsubopt, bestscore - ee.match, paired, firstread, readgroup);
            otherSam.addAlignment(sr);
        }

    }

    private SAMRecord[] createSAMRecordPair(PslEntry entry1, PslEntry entry2,
            ArrayList<String> fastq1, ArrayList<String> fastq2,
            SAMFileHeader hh, int numsubopt1, int numsubopt2, int suboptdistance1, int suboptdistance2, String readgroup) {

        //System.out.println("in create pair");

        SAMRecord[] sr = new SAMRecord[2];

        sr[0] = new SAMRecord(hh);
        // set the flag for read paired, read proper paired
        sr[0].setFlags(3);
        sr[0].setAlignmentStart(entry1.Tstart + 1);
        sr[0].setMateAlignmentStart(entry2.Tstart + 1);
        sr[0].setCigarString(entry1.getCigar());
        sr[0].setReferenceName(entry1.Tname);
        sr[0].setReadName(entry1.Qname);
        sr[0].setMappingQuality(Math.min(254, entry1.match));
        sr[0].setMateUnmappedFlag(false);
        sr[0].setMateReferenceName(entry2.Tname);
        sr[0].setFirstOfPairFlag(true);
        sr[0].setSecondOfPairFlag(false);
        sr[0].setInferredInsertSize(Math.max(entry1.Tend, entry2.Tend) - Math.min(entry1.Tstart, entry2.Tstart));
        if (entry1.strand == '-') {
            sr[0].setReadNegativeStrandFlag(true);
            sr[0].setMateNegativeStrandFlag(false);
            sr[0].setReadBases(complementSequence(fastq1.get(1)).getBytes());
            sr[0].setBaseQualityString(new StringBuffer(fastq1.get(3)).reverse().toString());
        } else {
            sr[0].setReadNegativeStrandFlag(false);
            sr[0].setMateNegativeStrandFlag(true);
            sr[0].setReadBases(fastq1.get(1).getBytes());
            sr[0].setBaseQualityString(fastq1.get(3));
        }
        sr[0].setAttribute("sb", numsubopt1);
        sr[0].setAttribute("sd", numsubopt1);
        sr[0].setAttribute("RG", readgroup);

        sr[1] = new SAMRecord(hh);
        sr[1].setFlags(3);
        sr[1].setAlignmentStart(entry2.Tstart + 1);
        sr[1].setMateAlignmentStart(entry1.Tstart + 1);
        sr[1].setCigarString(entry2.getCigar());
        sr[1].setReferenceName(entry2.Tname);
        sr[1].setReadName(entry2.Qname);
        sr[1].setMappingQuality(Math.min(254, entry2.match));
        sr[1].setMateUnmappedFlag(false);
        sr[1].setMateReferenceName(entry1.Tname);
        sr[1].setFirstOfPairFlag(false);
        sr[1].setSecondOfPairFlag(true);
        sr[1].setInferredInsertSize(-Math.max(entry1.Tend, entry2.Tend) + Math.min(entry1.Tstart, entry2.Tstart));
        if (entry2.strand == '-') {
            sr[1].setReadNegativeStrandFlag(true);
            sr[1].setMateNegativeStrandFlag(false);
            sr[1].setReadBases(complementSequence(fastq2.get(1)).getBytes());
            sr[1].setBaseQualityString(new StringBuffer(fastq2.get(3)).reverse().toString());
        } else {
            sr[1].setReadNegativeStrandFlag(false);
            sr[1].setMateNegativeStrandFlag(true);
            sr[1].setReadBases(fastq2.get(1).getBytes());
            sr[1].setBaseQualityString(fastq2.get(3));
        }
        sr[1].setAttribute("sb", numsubopt2);
        sr[1].setAttribute("sd", numsubopt2);
        sr[1].setAttribute("RG", readgroup);

        return sr;
    }

    private void getRidLargeTGaps(ArrayList<PslEntry> psllist, int Tgapthresh) {

        if (psllist.isEmpty()) {
            return;
        }

        for (int i = psllist.size() - 1; i >= 0; i--) {
            if (psllist.get(i).Tgapbases > Tgapthresh) {
                psllist.remove(i);
            }
        }

    }

    private void processPairedReads(ArrayList<PslEntry> psls1, ArrayList<PslEntry> psls2,
            ArrayList<String> fastq1, ArrayList<String> fastq2,
            SAMFileWriter bestSam, SAMFileWriter otherSam,
            SAMFileHeader hh, int maxsubopt, int maxinsertsize, String readgroup) {

        //System.out.println("psls1 and psls2 have "+psls1.size()+"\t"+psls2.size());

        getRidLargeTGaps(psls1, 100);
        getRidLargeTGaps(psls2, 100);

        //System.out.println("psls1 and psls2 have "+psls1.size()+"\t"+psls2.size());

        if (psls1.isEmpty() || psls2.isEmpty()) {
            return;
        }

        // sort the psl entries by blast scores               
        Collections.sort(psls1, pslCompare);
        Collections.sort(psls2, pslCompare);

        // count the number of reads that have scores within maxsubopt of the first entry        
        int numinrange1 = getNumberWithinScoreRange(psls1, maxsubopt);
        int numinrange2 = getNumberWithinScoreRange(psls2, maxsubopt);

        //System.out.println("number suboptimal "+numsubopt1+"\t"+numsubopt2);

        // determine if the reads are uniquely mapped. 
        // the function will return an array of two indeces. 
        // If there is a single best match, the indeces will be 0,0.
        // If not, the indeces will be -1,-1        
        // try getting true best matches
        int[] bestindeces = getBestIndeces(psls1, psls2, 2, maxinsertsize);

        PslEntry ee1 = psls1.get(0);
        PslEntry ee2 = psls2.get(0);
        int bestscore1 = ee1.match;
        int bestscore2 = ee2.match;

        //System.out.println("Best scores: "+bestscore1+"\t"+bestscore2);
        //System.out.println("Best indeces: "+bestindeces[0]+"\t"+bestindeces[1]);

        // for unique best pairings, add to bestSam
        if (bestindeces[0] == 0 && bestindeces[1] == 0) {
            // find out score distance between first and second alignments
            int suboptdistance1 = bestscore1;
            int suboptdistance2 = bestscore2;
            if (numinrange1 > 1) {
                suboptdistance1 = bestscore1 - psls1.get(1).match;
            }
            if (numinrange2 > 1) {
                suboptdistance2 = bestscore2 - psls2.get(1).match;
            }
            SAMRecord[] sr = createSAMRecordPair(ee1, ee2, fastq1, fastq2,
                    hh, numinrange1, numinrange2,
                    suboptdistance1, suboptdistance2, readgroup);
            bestSam.addAlignment(sr[0]);
            bestSam.addAlignment(sr[1]);
        }

        // for all other sub-optimal pairings, add to otherSam

        //System.out.println(fastq1.get(0) + " unqiue: " + bestindeces[0] + " " + bestindeces[1] + " subopt: " + numinrange1 + " " + numinrange2);
        for (int j = bestindeces[0] + 1; j < numinrange1; j++) {
            ee1 = psls1.get(j);
            SAMRecord sr1 = createSAMRecord(ee1, fastq1, hh, numinrange1, bestscore1 - ee1.match, true, true, readgroup);
            otherSam.addAlignment(sr1);
        }

        for (int j = bestindeces[1] + 1; j < numinrange2; j++) {
            ee2 = psls2.get(j);
            SAMRecord sr2 = createSAMRecord(ee2, fastq2, hh, numinrange2, bestscore2 - ee2.match, true, false, readgroup);
            otherSam.addAlignment(sr2);
        }


    }

    /**
     * Determines the best matching of reads from the two candidate lists
     *
     * @param psls1
     *
     * list of first mates. Must be non-empty
     *
     * @param psls2
     *
     * list of second mates. Must be non-empty
     *
     * @param maxsubopt
     *
     * when looking for properly paired reads, allow this amount leeway i.e. if
     * a read pair is non-best, but is properly paired, it will be considered on
     * par with best reads.
     *
     * @param maxinsertsize
     *
     * the maximal length between the two reads starting positions
     *
     * @return
     *
     * two index array of 0,0 if a best match is found two index array of -1,-1
     * if there is no best match
     *
     *
     */
    private int[] getBestIndeces(ArrayList<PslEntry> psls1, ArrayList<PslEntry> psls2, int maxsubopt, int maxinsertsize) {

        // initially assume the entries are unique, so the indeces of the best entries are both 0
        int[] best = new int[2];
        best[0] = 0;
        best[1] = 0;

        PslEntry e1 = psls1.get(0);
        PslEntry e2 = psls2.get(0);

        if (psls1.size() > 1 && e1.match == psls1.get(1).match) {
            best[0] = -1;
        }
        if (psls2.size() > 1 && e2.match == psls2.get(1).match) {
            best[1] = -1;
        }

        // check if best matches are properly paired
        if (best[0] == 0 && best[1] == 0) {
            if (e1.Tname.equals(e2.Tname) && Math.abs(e1.Tstart - e2.Tstart) < maxinsertsize) {
                return best;
            } else {
                best[0] = -1;
                best[0] = -1;
            }
        } else {
            // if there are many best matches, perhaps there is only one that is properly paired

            best[0] = -1;
            best[1] = -1;
            int bestscore = 0;
            int bestcount = 1;

            int ind1, ind2;
            // consider each first mate one at a time
            ind1 = 0;
            while (ind1 < psls1.size()) {
                PslEntry c1 = psls1.get(ind1);
                if (c1.match >= e1.match - maxsubopt) {

                    // consider each second mate
                    ind2 = 0;
                    while (ind2 < psls2.size()) {
                        PslEntry c2 = psls2.get(ind2);
                        if (c2.match >= e2.match - maxsubopt) {

                            // check if c1,c2 make a good pair                                        
                            if (c1.Tname.equals(c2.Tname) && Math.abs(c1.Tstart - c2.Tstart) < maxinsertsize) {
                                // yes, so let's record it in the candidates array
                                int nowscore = c1.match + c2.match;
                                if (nowscore > bestscore) {
                                    best[0] = ind1;
                                    best[1] = ind2;
                                    bestscore = nowscore;
                                    bestcount = 1;
                                } else if (nowscore == bestscore) {
                                    bestcount++;
                                }
                            }
                        } else {
                            ind2 = psls2.size();
                        }
                        ind2++;
                    } // end of while loop over ind2
                } else {
                    ind1 = psls1.size();
                }
                ind1++;

            } // end of while loop over ind1

            // check that a solution was found and that it was unique
            if (best[0] != -1 && best[1] != -1 && bestcount == 1) {

                // switch the entries so that the best entry is at index 0
                if (best[0] > 0) {
                    PslEntry c1 = psls1.get(best[0]);
                    psls1.set(best[0], psls1.get(0));
                    psls1.set(0, c1);
                }
                if (best[1] > 0) {
                    PslEntry c2 = psls2.get(best[1]);
                    psls2.set(best[1], psls2.get(0));
                    psls2.set(0, c2);
                }

                // reset the best indeces so that they point to the start of the psl lists
                best[0] = 0;
                best[1] = 0;
            } else {
                best[0] = -1;
                best[1] = -1;
            }

        }

        return best;
    }

    /**
     *
     * @param psls
     * @param maxsubopt
     *
     * difference in scores that qualify for suboptimal matches
     *
     * @return
     *
     * the number of entries in the psls list (including the first item) within
     * a score range of the first item.
     *
     */
    private int getNumberWithinScoreRange(ArrayList<PslEntry> psls, int maxsubopt) {
        int ans = 1;
        int maxscore = psls.get(0).match;
        for (int i = 1; i < psls.size(); i++) {
            if (maxscore - psls.get(i).match < maxsubopt) {
                ans++;
            } else {
                return ans;
            }
        }
        return ans;
    }

    /**
     * reads 4 lines of information from a buffered reader into an array of
     * strings the arraylist will contain the 4 lines. On line 1, the leading
     *
     * @
     * and the trailing /1 or /2 will be removed.
     *
     * @param br
     * @param ff
     * @return
     *
     * true if all was read correctly. false if reached end of file.
     * @throws IOException
     */
    private boolean readFastq(BufferedReader br, ArrayList<String> ff) throws IOException {

        // try to read from each of the buffered readers        
        // if any of the lines are null, return false
        ff.clear();
        String ss;
        ss = br.readLine();
        if (ss == null) {
            return false;
        }
        ss = ss.substring(1);
        int sslen = ss.length();
        if (ss.substring(sslen - 2, sslen).equals("/1") || ss.substring(sslen - 2, sslen).equals("/2")) {
            ss = ss.substring(0, sslen - 2);
        }
        ff.add(ss);
        ff.add(br.readLine());
        ff.add(br.readLine());
        ff.add(br.readLine());

        for (int i = 1; i < 4; i++) {
            if (ff.get(i) == null) {
                return false;
            }
        }

        // if reached here both reads were read properly
        return true;
    }

    private PslEntry readPsl(BufferedReader br, ArrayList<PslEntry> pp, PslEntry tempPsl) throws IOException {

        pp.clear();
        // add the buffered tempPsl or the first read in the file onto pp
        String curname = tempPsl.Qname;
        if (curname == null) {
            String ss = br.readLine();
            tempPsl = new PslEntry(ss);
            curname = tempPsl.Qname;
            pp.add(tempPsl);
        } else {
            pp.add(tempPsl);
        }

        // read from the psl, keep adding to the pp as long as the read name is the same
        boolean done = false;
        while (!done) {
            String ss = br.readLine();
            if (ss == null) {
                done = true;
            } else {
                tempPsl = new PslEntry(ss);
                if (curname.equals(tempPsl.Qname)) {
                    pp.add(tempPsl);
                } else {
                    done = true;
                }
            }
        }

        return tempPsl;
    }
    
    /**
     *
     * get the contents of a file, either txt or gzipped
     *
     * @param f
     * @return
     *
     * the contents of a file as one string
     *
     * @throws IOException
     *
     *
     */
    private String getFileContents(File f) throws IOException {
        BufferedReader reader = BufferedReaderMaker.makeBufferedReader(f);
        String s;
        StringBuilder sb = new StringBuilder(4096);
        boolean firstline = true;
        while ((s = reader.readLine()) != null) {
            if (firstline) {
                sb.append(s);
                firstline = false;
            } else {
                sb.append("\n").append(s);
            }
        }
        reader.close();
        return sb.toString();
    }

    @Override
    public void run() {
        if (!isReady) {
            return;
        }

        // when all parameters are ok, go and make the multibam
        try {
            if (isPaired) {
                makePairedEndMultiBam(settings);
            } else {
                makeSingleEndMultiBam(settings);
            }
        } catch (Exception ex) {
            System.out.println("Error making multibam: " + ex.getMessage());
        }
    }
}
