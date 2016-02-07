/*
 * Copyright 2012-2014 Tomasz Konopka.
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

import bamfo.Bamformatics;
import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoFisherTest;
import bamfo.utils.BamfoRecord;
import bamfo.utils.BamfoSettings;
import bamfo.utils.BamfoTool;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;
import jsequtils.sequence.FastaReader;
import jsequtils.variants.VcfEntry;
import net.sf.samtools.CigarElement;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;

/**
 * A variant caller for SNVs and Indels. It parses an alignment file and
 * produces VCF output.
 *
 *
 * @author tkonopka
 */
public class BamfoVcf extends BamfoTool implements Runnable {

    // cachelocations is used in traversal. This number of position are kept fully in memory
    private static final int CACHELOCATIONS = 128;
    // other Calling specific settings
    private File bamfile;
    private String outvcf = "stdout";
    private String samplelabel = "samplelabel";
    private boolean verbose = false;
    private final DecimalFormat scoreformat = new DecimalFormat("0.00");
    // some settings used in variant calling    
    private final static String[] settingtypes = {"minbasequal", "minmapqual",
        "minscore", "minallelic", "mindepth", "minfromstart", "minfromend",
        "strandbias", "trim", "trimQB", "NRef", "genome", "validate"};
    private final BamfoSettings settings = new BamfoSettings(settingtypes);
    private final BamfoFisherTest fisher = new BamfoFisherTest();
    // vcfformat will contain the ninth column for the vcf file
    // explanation for the two-letter codes are in the writeVcfHeader function
    private final static String vcfformat = "GT:GQ:ED:SF:MN:MM:MW:BW:DS:NM";
    //String specialread = "HWI-BRUNOP16X_0001:1:4:18029:180623#0";
    //int speciallocus = 126370647; 

    private void printBamVcfHelp() {
        outputStream.println("Bamformatics callvariants: a tool for calling variants from an alignment");
        outputStream.println();
        outputStream.println("General options:");
        outputStream.println("  --bam <File>             - input alignment file");
        outputStream.println("  --output <File>          - output vcf file");
        outputStream.println("  --genome <File>          - fasta file with genome sequence");
        outputStream.println("  --verbose                - print progress information");
        outputStream.println();
        outputStream.println(settings.printHelp());
    }

    private boolean parseBamVcfParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // bam - input bam file
        prs.accepts("bam").withRequiredArg().ofType(File.class);
        // output - output directory
        prs.accepts("output").withRequiredArg().ofType(String.class);
        // genome - genome fasta file
        //prs.accepts("genome").withRequiredArg().ofType(String.class);
        // samplelabel - samplelabel that appear in vcf file
        prs.accepts("label").withRequiredArg().ofType(String.class);
        // verbose - display verbose report by chromosome
        prs.accepts("verbose");

        // some options for variant calling
        settings.addOptionsToOptionParser(prs);

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            outputStream.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        // extract values of each option
        verbose = options.has("verbose");
        bamfolog.setVerbose(verbose);

        if (options.has("bam")) {
            bamfile = (File) options.valueOf("bam");
            if (!bamfile.canRead()) {
                outputStream.println("bam file is not readable");
                return false;
            }
        } else {
            outputStream.println("missing parameter bam");
            return false;
        }

        // if output is not set, will use stdout
        if (options.has("output")) {
            outvcf = (String) options.valueOf("output");
        } else {
            // make sure the verbose is turned off if output is sent to stdout
            verbose = false;
        }

        // if label is not set, the default will be used
        if (options.has("label")) {
            samplelabel = (String) options.valueOf("label");
        }

        // get the options for variant calling
        if (!settings.getOptionValues(options)) {
            return false;
        }

        return true;
    }

    /**
     *
     * Prepares to call variants in an alignment file. The constructor currently
     * initializes the class. The calculation proper starts in run().
     *
     * @param args
     *
     * options as passed on a command line, e.g. --bam mybam.bam --output
     * myvcf.vcf
     *
     */
    public BamfoVcf(String[] args, PrintStream logstream) {
        super(logstream);

        if (args == null) {
            printBamVcfHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseBamVcfParameters(args)) {
            return;
        }
        bamfolog.setVerbose(verbose);

        isReady = true;
    }

    /**
     * A constructor that does not parse command line, but that gets its
     * settings directly from the arguments or from the BamfoSettings object.
     *
     *
     * @param bamfile
     * @param outvcf
     * @param s
     * @param logstream
     *
     * Stream where output will be reported (will not be closed at the end)
     *
     * The settings from this object are deep-copied from this object onto an
     * independent object with the BamfoVcf class.
     *
     */
    public BamfoVcf(File bamfile, String outvcf, BamfoSettings s, PrintStream logstream) {
        super(logstream);
        this.bamfile = bamfile.getAbsoluteFile();
        this.outvcf = outvcf;
        this.verbose = true;
        this.bamfolog.setVerbose(verbose);
        synchronized (s) {
            settings.setGenome(s.getGenome());
            settings.setMinallelic(s.getMinallelic());
            settings.setMinbasequal(s.getMinbasequal());
            settings.setMindepth(s.getMindepth());
            settings.setMinfromend(s.getMinfromend());
            settings.setMinfromstart(s.getMinfromstart());
            settings.setMinmapqual(s.getMinmapqual());
            settings.setMinscore(s.getMinscore());
            settings.setStrandbias(s.getStrandbias());
            settings.setTrimBtail(s.isTrimBtail());
            settings.setTrimpolyedge(s.isTrimpolyedge());
        }
        isReady = true;
    }

    private void writeVcfHeader(OutputStream outstream, String label) throws IOException {
        StringBuilder sb = new StringBuilder(4096);
        SimpleDateFormat dateformat = new SimpleDateFormat("yyyyMMdd");

        sb.append("##fileformat=VCFv4.1\n");
        sb.append("##fileDate=").append(dateformat.format(new Date())).append("\n");
        sb.append("##source=Bamformatics v").append(Bamformatics.getVersion()).append("\n");

        // write the settings used to genotype
        sb.append(settings.printAllOptions());
        sb.append("##bamformatics.bam=").append(bamfile.getAbsolutePath()).append("\n");

        // explain the format field        
        sb.append("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n");
        sb.append("##FORMAT=<ID=GQ,Number=1,Type=Integer,Description=\"Genotype Quality\">\n");
        sb.append("##FORMAT=<ID=ED,Number=1,Type=Integer,Description=\"Effective depth used for variant calling\">\n");
        sb.append("##FORMAT=<ID=SF,Number=1,Type=Float,Description=\"Strand bias Fisher test phred\">\n");
        sb.append("##FORMAT=<ID=MN,Number=1,Type=Integer,Description=\"Maximum length of N in cigar\">\n");
        sb.append("##FORMAT=<ID=NP,Number=1,Type=Integer,Description=\"Proportion of reads with N as a called base\">\n");
        sb.append("##FORMAT=<ID=MM,Number=1,Type=Float,Description=\"Median mapping quality of all reads\">\n");
        sb.append("##FORMAT=<ID=MW,Number=1,Type=Integer,Description=\"Number of reads with low mapping quality\">\n");
        sb.append("##FORMAT=<ID=BW,Number=1,Type=Integer,Description=\"Number of reads with low base quality score\">\n");
        sb.append("##FORMAT=<ID=DS,Number=1,Type=Integer,Description=\"Number of distinct distances of variant from read start positions\">\n");
        sb.append("##FORMAT=<ID=NM,Number=1,Type=Integer,Description=\"Mean value of NM tag of variant containing reads\">\n");

        // write out the canonical line signaling start of the variant list
        sb.append("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t").append(label).append("\n");

        outstream.write(sb.toString().getBytes());
    }

    /**
     * This is the function where reads from the alignment file are read one by
     * one, and then processed for variants.
     *
     * @param inputSam
     *
     * an initialized reader of SAM files
     *
     * @param genomereader
     *
     * an initialized reader of fasta sequence (reference genome)
     *
     * @param outstream
     *
     * initialized stream where variants will be stored. This function will only
     * output the variants. Headers should be written previously
     *
     *
     * @throws IOException
     */
    private void genotypeBam(SAMFileReader inputSam, FastaReader genomereader, OutputStream outstream) throws IOException {

        // record starting time          
        bamfolog.log("Starting variant calling with Bamformatics");
        String[] temp = settings.printAllOptions().split("\n");
        for (int i = 0; i < temp.length; i++) {
            bamfolog.log(temp[i]);
        }

        SAMFileHeader samHeader = inputSam.getFileHeader();

        // make data structures that will hold the genotype evidence
        int nowRef = -1, nowpos;
        int lastdrain = 1;
        String nowRefName;
        int nowRefLen = 0;

        // the two "info" hashmap will store information about reads documenting snv and indels
        // anchored at loci 
        // For snvs, anchor point is locus of snv
        // For indels, anchor point may be position in vcf, or some place prior, depending on sequence
        // make the hashmaps large so that an appropriate number of cached locations fit without resizing.
        HashMap<Integer, LocusSNVDataList> snvinfo = new HashMap<>(2 * CACHELOCATIONS);
        HashMap<Integer, LocusIndelDataList> indelinfo = new HashMap<>(2 * CACHELOCATIONS);
        // The next hashmap will store "semi-called indels"
        HashMap<Integer, LocusIndelQuasiCalled> indelquasicalled = new HashMap<>(8);

        // read each record, for each chromosome
        for (final SAMRecord samRecord : inputSam) {

            // only process aligned, primary records records, with mapping quality greater than zero
            // and non-duplicate
            int recordReference = samRecord.getReferenceIndex();
            if (recordReference > -1 && !samRecord.getNotPrimaryAlignmentFlag()
                    && !samRecord.getReadUnmappedFlag() && !samRecord.getDuplicateReadFlag()) {

                nowpos = samRecord.getAlignmentStart();

                // check if the record starts a new chromosome
                // if so, finish processing the old chromosome and prepare for the next one
                if (recordReference != nowRef) {
                    if (Thread.currentThread().isInterrupted()) {
                        bamfolog.log("Calling interrupted");
                        return;
                    }

                    // genotype the remaining loci on the chromosome
                    if (nowRef != -1) {
                        lastdrain = genotypeLoci(outstream, snvinfo, indelinfo, indelquasicalled,
                                genomereader, lastdrain, 1 + nowRefLen);
                    }

                    // get information about the new chromosome
                    nowRef = recordReference;
                    nowRefName = samRecord.getReferenceName();
                    nowRefLen = (samHeader.getSequence(nowRef)).getSequenceLength();
                    lastdrain = 1;

                    // get chromosome sequence from the genome reader (perhaps skip over missing chromosomes)
                    genomereader.readNext();
                    System.gc();
                    while (genomereader.hasThis() && !nowRefName.equals(genomereader.getChromosomeName())) {
                        if (Thread.currentThread().isInterrupted()) {
                            bamfolog.log("Calling interrupted");
                            return;
                        }
                        genomereader.readNext();
                        System.gc();
                    }
                    if (!nowRefName.equals(genomereader.getChromosomeName())) {
                        bamfolog.log(true, "Error: chromsome " + nowRefName + " does not appear in the genome reference file");
                        bamfolog.log(true, "(check that chromsomes appear in the same order in bam and reference)");
                        return;
                    }
                    // check that the chromsome lengths in alignment and reference match
                    if (nowRefLen != genomereader.getChromosomeLength()) {
                        bamfolog.log(true, "Error: discordant lengths on chromosome " + nowRefName);
                        return;
                    }

                    bamfolog.log("Calling " + nowRefName);

                    // initialize a new chrinfo object that will store pileup information
                    snvinfo = new HashMap<Integer, LocusSNVDataList>(2 * CACHELOCATIONS);
                }

                // parse information from this record to 
                // add the contribution of this read to the coverage
                try {
                    //modifyGenotype(snvinfo, indelinfo, indelquasicalled, genomereader, samRecord);
                    modifyGenotype(snvinfo, indelinfo, genomereader, samRecord);
                } catch (Exception ex) {
                    bamfolog.log(true, "Error during calling: " + ex.getMessage());
                    bamfolog.log(true, samRecord.getSAMString());
                    return;
                }

                // perhaps drain the chrinfo if the fill index has run too far ahead of the drain index
                if (nowpos - lastdrain > CACHELOCATIONS && nowRefLen - nowpos > CACHELOCATIONS) {
                    if (Thread.currentThread().isInterrupted()) {
                        bamfolog.log("Calling interrupted");
                        return;
                    }
                    lastdrain = genotypeLoci(outstream, snvinfo, indelinfo, indelquasicalled, genomereader, lastdrain, nowpos);
                }
            }
        } // end of for loop over records

        // if there is still something left in the chrinfo, genotype from the current index beyond the chromosome length
        if (nowRef != -1) {
            lastdrain = genotypeLoci(outstream, snvinfo, indelinfo, indelquasicalled, genomereader, lastdrain, 1 + nowRefLen);
        }

        bamfolog.log("Calling complete");

        // print out some stats to check for 'memory leaks' i.e. information retained in the maps
        // the maps should be empty at the end of the analysis
        //
        //outputStream.println("snvinfo: " + snvinfo.size());
        //outputStream.println("indelinfo: " + indelinfo.size());
        //outputStream.println("indelquasicalled: " + indelquasicalled.size());

    }

    /**
     * Parses a record and stores information from the record into data
     * structures that can later be used to call variants.
     *
     * @param snvinfo
     * @param indelinfo
     * @param genomereader
     * @param record
     */
    private void modifyGenotype(HashMap<Integer, LocusSNVDataList> snvinfo,
            HashMap<Integer, LocusIndelDataList> indelinfo,
            FastaReader genomereader, SAMRecord record) {

        // create an object holding the record and some derived quantities 
        // this will provide easy access to the positions of each base, etc.
        BamfoRecord b2r = new BamfoRecord(record, settings.isTrimBtail(), settings.isTrimpolyedge());

        byte[] bases = b2r.bases;

        // for indels, will need to get the anchorbase
        byte anchorbase = 'N';

        if (b2r.readhasindel) {
            // loop over the cigars  
            int nowp = b2r.startpos;
            int nowi = 0;
            for (int i = 0; i < b2r.cigarelements.size(); i++) {
                CigarElement ce = b2r.cigarelements.get(i);
                int celen = ce.getLength();

                boolean heredeletion = false;
                boolean hereinsertion = false;
                byte[] indel = null;
                int indelstart = nowp; // start position in genome coordinates                
                int indelindex = nowi; // start position in read coordinates

                switch (ce.getOperator()) {
                    case M:
                        nowp += celen;
                        nowi += celen;
                        break;
                    case D:
                        nowp += celen;
                        indel = genomereader.getSequenceBase1(indelstart, nowp - 1);
                        indelindex = nowi;
                        heredeletion = true;
                        break;
                    case N:
                        nowp += celen;
                        break;
                    case P:
                        nowp += celen;
                        break;
                    case S:
                        nowi += celen;
                        break;
                    case I:
                        indel = new byte[celen];
                        indelindex = nowi;
                        System.arraycopy(bases, nowi, indel, 0, celen);
                        nowi += celen; // advance nowi to be ready for the next cigar operator
                        hereinsertion = true;
                        break;
                    default:
                        break;
                }

                // record this indel                 
                if ((hereinsertion || heredeletion)) {

                    // indel anchor position is one to the left
                    indelstart--;

                    // find out if the anchor is really there or should be shifted to the left

                    // try to evaluate the anchor using genomic sequence
                    int anchorpos = BamfoCommon.getAnchorPosition(genomereader, indelstart, indel);
                    // evaluate anchor relative to the read sequence
                    int readanchorpos = BamfoCommon.getAnchorPosition(bases, indelindex, indel);
                    int anchorpos2 = indelstart - (indelindex - readanchorpos);

                    // use the anchor that is furthest to the left
                    byte[] anchor;
                    if (anchorpos <= anchorpos2) {
                        anchor = genomereader.getSequenceBase1(anchorpos, indelstart);
                    } else {
                        anchor = new byte[1 + indelindex - readanchorpos];
                        System.arraycopy(bases, readanchorpos - 1, anchor, 0, anchor.length);
                        anchorpos = anchorpos2;
                        //make sure the first base of the anchor corresponds to the genome
                        //anchor[0] = genomereader.getBaseAtPositionBase1(anchorpos);
                    }
                    anchorbase = genomereader.getBaseAtPositionBase1(anchorpos);

                    // record this indel
                    LocusIndelDataList nowIDL = indelinfo.get(anchorpos);
                    if (nowIDL == null) {
                        nowIDL = new LocusIndelDataList(anchorpos, anchorbase);
                        indelinfo.put(anchorpos, nowIDL);
                    }

                    //save information about this indel into the list of indel data anchored 
                    nowIDL.add(anchor, indel, indelstart, b2r, indelindex, hereinsertion);
                }
            }

        }

        // now for each position record the information about base substitutions        
        int[] pos = b2r.pos;
        for (int nowindex = 0; nowindex < b2r.readlength; nowindex++) {
            if (pos[nowindex] > 0) {

                LocusSNVDataList nowGDL = snvinfo.get(pos[nowindex]);
                if (nowGDL == null) {
                    nowGDL = new LocusSNVDataList(pos[nowindex]);
                    snvinfo.put(pos[nowindex], nowGDL);
                }

                // save information about this base
                nowGDL.add(b2r, nowindex);
            }
        }

    }

    /**
     * calls variants in genomic regions between startpos (included) and endpos
     * (not included)
     *
     * @param outstream
     * @param snvinfo
     * @param genomereader
     * @param startpos
     *
     * 1-based coordinate system
     *
     * @param endpos
     *
     * 1-based coordinate system
     *
     * @return
     * @throws IOException
     */
    private int genotypeLoci(OutputStream outstream, HashMap<Integer, LocusSNVDataList> snvinfo,
            HashMap<Integer, LocusIndelDataList> indelinfo,
            HashMap<Integer, LocusIndelQuasiCalled> indelquasicalled,
            FastaReader genomereader, int startpos, int endpos) throws IOException {

        for (int i = startpos; i < endpos; i++) {
            try {
                // pre-process indels anchored at this location                
                LocusIndelDataList indellist = indelinfo.get(i);
                if (indellist != null) {
                    preprocessIndelGenotype(i, indellist, indelquasicalled);
                    indelinfo.remove(i);
                }

                // next genotype variants and indels starting at this location
                LocusIndelQuasiCalled liqc = indelquasicalled.get(i);
                LocusSNVDataList lgl = snvinfo.get(i);

                if (lgl != null || liqc != null) {

                    VcfEntry entry = getSNVAndIndelCall(lgl, liqc, genomereader.getBaseAtPositionBase1(i));
                    if (entry != null) {
                        entry.setChr(genomereader.getChromosomeName());
                        entry.setPosition(i);
                        outstream.write(entry.toString().getBytes());
                        // if the entry is an indel. also post-process (avoids memory leaks) 
                        if (entry.isIndel()) {
                            postprocessIndel(entry, snvinfo);
                        }

                    }
                    // importantly, remove this item from the hashmaps
                    snvinfo.remove(i);
                    indelquasicalled.remove(i);
                }
            } catch (Exception ex) {
                bamfolog.log("Exception at " + genomereader.getChromosomeName() + ":" + i);
                if (snvinfo.get(i) != null) {
                    snvinfo.get(i).print();
                }
                if (indelinfo.get(i) != null) {
                    indelinfo.get(i).print();
                }
                if (indelquasicalled.get(i) != null) {
                    outputStream.println("indelquasicalled");
                }

            }
        }

        return endpos;
    }

    /**
     *
     * @param locus
     * @return
     *
     * if the locus is judged a variant, the output is a string ready to be
     * pasted into a vcf. if the locus is not a variant, return value is null
     *
     *
     */
    private VcfEntry getSNVCall(LocusSNVDataList locus, byte refbase) {

        if (locus == null || refbase == 'N') {
            return null;
        }

        // check depth threshold
        if (locus.size() < settings.getMindepth()) {
            return null;
        }

        // Some string that will make up the vcf entry
        String alt = "";

        // get coverage information from the list at this locus
        int[] covtot = new int[5];
        int[] covplus = new int[5];
        int[] covminus = new int[5];
        int[] maxns = new int[5];
        locus.getCoverageCounts(covplus, covminus, maxns,
                settings.getMinfromstart(), settings.getMinfromend(),
                settings.getMinbasequal(), settings.getMinmapqual());
        // get coverage totals
        int totminus = 0, totplus = 0;
        for (int i = 0; i < 5; i++) {
            totplus += covplus[i];
            totminus += covminus[i];
            covtot[i] = covplus[i] + covminus[i];
        }
        // if user wants to count Ns as reference bases, shift counts here
        int whichref = BamfoCommon.basesToZeroToFour((byte) refbase);
        if (settings.isNRef()) {
            covplus[whichref] += covplus[BamfoCommon.codeN];
            covminus[whichref] += covminus[BamfoCommon.codeN];
            covtot[whichref] += covtot[BamfoCommon.codeN];
            covplus[BamfoCommon.codeN] = 0;
            covminus[BamfoCommon.codeN] = 0;
            covtot[BamfoCommon.codeN] = 0;
        }

        int tottot = totplus + totminus;

        // split totals according to reference base/alternative base        
        int refplus = covplus[whichref];
        int refminus = covminus[whichref];
        int reftot = refplus + refminus;

        int altplus = totplus - refplus;
        int altminus = totminus - refminus;
        int alttot = altplus + altminus;

        if (5 < 4 && locus.getLocusname().equals("16277852")) {
            System.out.println("ref\t" + refplus + "\t" + refminus + "\t" + reftot);
            System.out.println("alt\t" + altplus + "\t" + altminus + "\t" + alttot);
            System.out.println("tot\t" + totplus + "\t" + totminus + "\t" + tottot);
        }

        // here again check depth threshold
        if (alttot < settings.getMindepth()) {
            return null;
        }

        // compute the score
        double score = getScore(reftot, alttot);

        // exit if the score is not large enouch
        if (score < settings.getMinscore()) {
            return null;
        }

        // figure out how many of the laternative alleles are significant. Check each one in turn
        // altsignificant has one element per base, the element corresponding to the reference base
        // will be wasted but it's no big deal...
        boolean[] significant = {false, false, false, false};
        double[] scores = {0.0, 0.0, 0.0, 0.0};
        int numsignificant = 0;
        for (int i = 0; i < 4; i++) {
            // check various thresholds in turn: minimum depth, minimum allelic propotion, minimum score
            if (covtot[i] >= settings.getMindepth()) {
                double allelicproportion = (double) covtot[i] / (double) tottot;
                if (allelicproportion >= settings.getMinallelic()) {
                    scores[i] = getScore(tottot - covtot[i], covtot[i]);
                    // in addition to the score check thresholds on 
                    if (scores[i] >= settings.getMinscore()) {
                        significant[i] = true;
                        numsignificant++;
                    }
                }
            }
        }

        // abandom if none are significant or when only the reference base is significant
        if (numsignificant == 0 || (numsignificant == 1 && significant[whichref])) {
            return null;
        }

        // Compute fisher values.
        // check if the significant items are strand neutral.
        // If a variant is not strand neutral, do not make it significant.        
        double[] fisherp = new double[5];

        for (int i = 0; i < 4; i++) {
            if (significant[i]) {
                fisherp[i] = fisher.test(covplus[i], covminus[i], totplus - covplus[i], totminus - covminus[i]);
                if (fisherp[i] < settings.getStrandbias()) {
                    significant[i] = false;
                    numsignificant--;
                }
            } else {
                fisherp[i] = 1.0;
            }
        }

        // check if the significant items have more than one from start/from end read       
        int nowDS = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (significant[i]) {
                int thisUFS = locus.getNumberUniqueFromstart(BamfoCommon.ZeroToFourToByte(i));
                if (i == whichref && settings.isNRef()) {
                    thisUFS += locus.getNumberUniqueFromstart((byte) 'N');
                }
                if (thisUFS < 2) {
                    significant[i] = false;
                    numsignificant--;
                } else {
                    nowDS = Math.min(nowDS, thisUFS);
                }
            }
        }

        // again, abandom if none are significant or when only the reference base is significant
        if (numsignificant == 0 || (numsignificant == 1 && significant[whichref])) {
            return null;
        }

        // construct the string showing what the alternative alleles are        
        // while looping over the significant alleles, record the maximum intron length
        // and the minimum fisher value
        double minfisherp = 1.0;
        int maxmaxN = 0;
        int maxNMtag = 0;
        for (int i = 0; i < 4; i++) {
            if (i != whichref && significant[i]) {
                if (alt.length() == 0) {
                    alt += BamfoCommon.ZeroToFourToBase(i);
                } else {
                    alt += "," + BamfoCommon.ZeroToFourToBase(i);
                }

                maxNMtag = Math.max(maxNMtag, locus.getMeanNMtag(BamfoCommon.ZeroToFourToByte(i)));
                // also find out the maximal intron gap length for all significant variants
                if (maxns[i] > maxmaxN) {
                    maxmaxN = maxns[i];
                }
                if (fisherp[i] < minfisherp) {
                    minfisherp = fisherp[i];
                }
            }
        }

        // conver the minfisherp into phred scale
        minfisherp = Math.abs(10.0 * Math.log10(minfisherp));

        int[] lowcounts = locus.getLowQualityCounts(settings.getMinfromstart(), settings.getMinfromend(),
                settings.getMinbasequal(), settings.getMinmapqual());

        // finally, put the information together to create the summary for the sample                
        String sampleGT = getGTString(numsignificant, significant[whichref]) + ":"
                + ((int) Math.floor(score)) + ":" + tottot + ":"
                + scoreformat.format(minfisherp) + ":"
                + maxmaxN + ":" + scoreformat.format(locus.getMedianMappingQuality())
                + ":" + lowcounts[0] + ":" + lowcounts[1] + ":" + nowDS + ":" + maxNMtag;

        VcfEntry entry = new VcfEntry();
        entry.setAlt(alt);
        entry.setRef(refbase);
        entry.setQuality(scoreformat.format(score));
        entry.setFormat(vcfformat);
        entry.setGenotype(sampleGT);

        return entry;
    }

    private VcfEntry getIndelCall(LocusIndelQuasiCalled liqc, int depthplus, int depthminus) {

        if (liqc == null) {
            return null;
        }

        // determine the allelic proportion of the indel and reference
        int indeldepthplus = liqc.countplus;
        int indeldepthminus = liqc.countminus;
        int indeldepth = indeldepthplus + indeldepthminus;
        int depth = depthplus + depthminus;

        if (indeldepth < settings.getMindepth()) {
            return null;
        }
        if ((double) indeldepth / (double) depth < settings.getMinallelic()) {
            return null;
        }

        int otherdepthplus = Math.max(0, depthplus - indeldepthplus);
        int otherdepthminus = Math.max(0, depthminus - indeldepthminus);
        int otherdepth = otherdepthplus + otherdepthminus;

        // determine if the score is large enough
        double scoreindel = getScore(otherdepth, indeldepth);
        boolean significantindel = (scoreindel > settings.getMinscore());
        if (!significantindel) {
            return null;
        }

        // check if the significant items have more than one from start/from end read       
        int nowDS = liqc.distinctstart;

        // compute the strand bias
        double fisherp = fisher.test(indeldepthplus, indeldepthminus,
                otherdepthplus, otherdepthminus);
        if (fisherp < settings.getStrandbias()) {
            return null;
        }
        // convert fisher p into a phred score
        fisherp = Math.abs(10.0 * Math.log10(fisherp));

        // everything passes. 
        VcfEntry ans = new VcfEntry();
        ans.setRef(new String(liqc.ref));
        ans.setAlt(new String(liqc.alt));

        //Now determine if the item should be called homozygous or heterozygous        
        ans.setGenotype("0/1:");
        double scoreref = getScore(indeldepth, otherdepth);
        if (scoreref < settings.getMinscore() || otherdepth < settings.getMindepth()
                || (double) otherdepth / (double) depth < settings.getMinallelic()) {
            ans.setGenotype("1/1:");
        }

        ans.setQuality(scoreformat.format(scoreindel));
        ans.setFormat(vcfformat);
        ans.setGenotype(ans.getGenotype() + ((int) Math.floor(scoreindel))
                + ":" + depth
                + ":" + scoreformat.format(fisherp)
                + ":.:" // this is for MN
                + liqc.medianMappingQuality
                + ":.:.:" + nowDS + ":."); // these are for MW, BW, DS, NM

        return ans;
    }

    /**
     *
     * @param locus
     * @return
     *
     * if the locus is judged a variant, the output is a string ready to be
     * pasted into a vcf. if the locus is not a variant, return value is null
     *
     *
     */
    private VcfEntry getSNVAndIndelCall(LocusSNVDataList locus, LocusIndelQuasiCalled liqc, byte refbase) {

        // if the locus is not covered, cannot estimate anything -> something wrong
        if (locus == null || refbase == 'N') {
            return null;
        }

        // get genotype for substitutions
        VcfEntry snvans = getSNVCall(locus, refbase);

        // if there are no indels, just do the calcualtion for substitutions
        if (liqc == null) {
            return snvans;
        }

        int[] locusdepth = locus.getStrandEffectiveDepth(settings.getMinfromstart(),
                settings.getMinfromend(), settings.getMinbasequal(),
                settings.getMinmapqual());
        VcfEntry ans = getIndelCall(liqc, locusdepth[0], locusdepth[1]);

        if (ans == null) {
            return snvans;
        } else {
            if (snvans == null) {
                return ans;
            } else {
                // what to do when both are not null?
                // if the genotype is the same, report a modified 
                if (snvans.getGenotype().substring(0, 3).equals(ans.getGenotype().substring(0, 3))) {
                    ans.setAlt(snvans.getAlt() + ans.getAlt().substring(1));
                }
                return ans;
            }

        }
    }

    /**
     *
     * Produces a genotype string like 0/1 or 1/1 depending on the number of
     * alternative alleles that are deemed significant.
     *
     * @param numsignificant
     * @param covref
     * @param locus
     * @return
     */
    private static String getGTString(int numsignificant, boolean refsignificant) {
        // check if the reference base qualifies as an allele
        if (refsignificant) {
            // it does, so 0 is in the genotype string            
            switch (numsignificant) {
                case 2:
                    return "0/1";
                case 3:
                    return "0/1/2";
                default:
                    return "0/1/2/3";
            }
        } else {
            // reference base is not significant, so 0 does not appear in the genotype string            
            switch (numsignificant) {
                case 1:
                    return "1/1";
                case 2:
                    return "1/2";
                default:
                    return "1/2/3";

            }
        }
    }

    /**
     * Converts a list of reads with indels into one object with a description
     * of a consensus indel variant. This description is almost a called
     * variant, but not in VCF format. The description is inserted into the
     * hashmap.
     *
     * @param nowpos
     * @param indellist
     * @param indelquasicalled
     */
    private void preprocessIndelGenotype(int nowpos, LocusIndelDataList indellist,
            HashMap<Integer, LocusIndelQuasiCalled> indelquasicalled) {

        if (indellist == null) {
            return;
        }

        if (indellist.size() < settings.getMindepth()) {
            return;
        }

        ArrayList<LocusIndelData> indels = new ArrayList<>(8);
        ArrayList<Integer> counts = new ArrayList<>(8);

        indellist.getIndelCounts(indels, counts,
                settings.getMinfromstart(), settings.getMinfromend(), settings.getMinmapqual());

        // find the indel with the maximal count
        int maxcount = 0;
        int maxcountindex = 0;
        for (int i = 0; i < counts.size(); i++) {
            int nowcount = counts.get(i);
            if (nowcount > maxcount) {
                maxcount = nowcount;
                maxcountindex = i;
            }
        }

        // put the most abundant indels and put it into the indelquasicalled
        if (maxcount > settings.getMindepth()) {
            // record the indel with maximal counts
            LocusIndelData consensus = indels.get(maxcountindex);
            int[] strandeddepth = indellist.getStrandedIndelCount(consensus.getIndelLen());
            indelquasicalled.put(nowpos,
                    new LocusIndelQuasiCalled(consensus,
                    strandeddepth[0], strandeddepth[1],
                    indellist.getNumberUniqueFromstart(consensus.getIndelLen()),
                    indellist.getMedianMappingQuality()));
        }

    }

    private void postprocessIndel(VcfEntry entry, HashMap<Integer, LocusSNVDataList> snvinfo) {

        // get the length of the reference sequence in the vcf entry
        int entryreflen = entry.getRef().length();
        entryreflen--;

        // if the length is now greater than zero, that means we have a deletion
        // make sure the variants are not called within the deletion
        // This is easily done by removing entries from snvinfo.
        for (int i = 0; i < entryreflen; i++) {
            snvinfo.remove(entry.getPosition() + i + 1);
        }

    }

    /**
     * get a z-score value based on reference and alternative counts
     *
     * @param refcount
     * @param altcount
     * @return
     */
    private double getScore(int refcount, int altcount) {
        if (refcount + altcount <= 0) {
            return 0.0;
        }
        double AP = ((double) altcount) / ((double) (refcount + altcount));
        double APerr = Math.sqrt((double) (altcount + 1) * (refcount + 1)) / Math.pow((double) altcount + refcount + 2, 1.5);
        return 10.0 * AP / APerr;
    }

    /**
     * After the utility is intialized, it has to be "executed" by invoking this
     * method. If initialization failed, this method does not do anything.
     *
     */
    @Override
    public void run() {
        if (!isReady) {
            return;
        }

        // create a reader for the reference genome
        FastaReader genomereader;
        try {
            genomereader = new FastaReader(BufferedReaderMaker.makeBufferedReader(settings.getGenome()));
        } catch (Exception ex) {
            outputStream.println("Could not open the genome file: " + ex.getMessage());
            return;
        }

        // create the output stream
        OutputStream outstream;
        if (outvcf.equals("stdout")) {
            outstream = System.out;
        } else {
            try {
                outstream = OutputStreamMaker.makeOutputStream(outvcf);
            } catch (Exception ex) {
                outputStream.println("could not create output file: " + ex.getMessage());
                return;
            }
        }


        // start processing, open the SAM file and start computing
        SAMFileReader inputSam = new SAMFileReader(bamfile);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());

        try {
            writeVcfHeader(outstream, samplelabel);
            genotypeBam(inputSam, genomereader, outstream);
        } catch (Exception ex) {
            outputStream.println("Error during genotyping: " + ex.getMessage() + "\n");
        }

        // close the streams
        if (outstream != System.out) {
            try {
                outstream.close();
            } catch (IOException ex) {
                outputStream.println("Error closing vcf: " + ex.getMessage());
            }
        }
        genomereader.close();
    }
}
