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
package bamfo.stats;

import bamfo.call.LocusSNVDataList;
import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoLog;
import bamfo.utils.BamfoRecord;
import bamfo.utils.BamfoSettings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;
import jsequtils.genome.GenomeInfo;
import jsequtils.regions.GenomeBitSet;
import jsequtils.sequence.FastaReader;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;

/**
 *
 * This tool measures of base substitution error rates. This tool searches for
 * loci that have a few alternative bases. It uses the fact that when a variant
 * is present at very low allelic proportion, the alternative bases are most
 * likely a result of sequencing or other errors rather than a biological
 * reality.
 *
 * The tool can be run on whole genome or on specific regions. Bed files are
 * interpreted as intervals in 0-based coordinates. Vcf files are interpreted as
 * single positions in 1-based coordinates (only the first two columns of vcf
 * are used).
 *
 * Whether regions are specified or not, the tool always reads through the
 * entire bam file in order.
 *
 * @author tkonopka
 */
public class BamfoErrors implements Runnable {

    // input/output
    private File bamfile;
    private String out = "stdout";
    private boolean verbose = false;
    // while traversing, a certain number of items will be kept in memeory
    private static final int cachelocations = 256;
    // some settings used in determining whether data on locus are used for error estimation
    // loci are required to have a fairly large number of reads with reference allele (via maxallelic)
    // and a low number of reads with alternative allele (via maxerrordepth)    
    private double maxallelic = 0.02;
    private int maxerrordepth = 3;
    // some bases are discarded during calling, so no need to include them in the error count.
    // e.g. If an error rate is high on first 5prime base, do not count it here
    // if it is not included in variant calling anyway
    private final static String[] settingtypes = {"minbasequal", "minmapqual",
        "minfromstart", "minfromend", "notrim", "notrimQB", "genome", "validate"};
    private BamfoSettings settings = new BamfoSettings(settingtypes);
    // internal book-keeping objects
    private long[] eligible = new long[5];
    private long[][] errors = new long[5][5];
    private boolean notstranded = true;
    private boolean isReady = false;
    private final BamfoLog bamfolog;
    // options for targeted error search
    private File bedfile = null;
    private File vcffile = null;
    private boolean avoid = false;

    private void printBamErrorsHelp() {
        System.out.println("bam2x errors: a tool for estimating sequencing error rates");
        System.out.println();
        System.out.println("General options:");
        System.out.println("  --bam <File>             - input alignment file");
        System.out.println("  --output <File>          - output vcf file");
        System.out.println("  --genome <File>          - fasta file with genome sequence");
        System.out.println("  --verbose                - print progress information");
        System.out.println();
        System.out.println(settings.printHelp());
        System.out.println("Options specific to errors:");
        System.out.println("  --bed <File>             - genomic regions of interest [default whole genome]");
        System.out.println("  --vcf <File>             - genomic positions of interest [default whole genome]");
        System.out.println("  --avoid                  - use to skip declared genomic regions in calculation");
        System.out.println("  --maxallelic <double>    - maximum allelic proportion for error site [default " + maxallelic + "]");
        System.out.println("  --maxerrordepth <int>    - maximum number of alternate reads on site [default " + maxerrordepth + "]");
        System.out.println("  --notstranded            - stranded means distinguishing between errors on +- strands [default +" + notstranded + "]");

        System.out.println();
    }

    private boolean parseBamErrorsParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // bam - input bam file
        prs.accepts("bam").withRequiredArg().ofType(File.class);
        prs.accepts("verbose");
        prs.accepts("output").withRequiredArg().ofType(String.class);
        // notstranded will determine how errors on -strand are counted
        prs.accepts("notstranded");

        // options for computing coverage at a locus
        settings.addOptionsToOptionParser(prs);

        // some options for determing error sites               
        prs.accepts("maxerrordepth").withRequiredArg().ofType(Integer.class);
        prs.accepts("maxallelic").withRequiredArg().ofType(Double.class);

        // some options for defining regions of interest
        prs.accepts("bed").withRequiredArg().ofType(File.class);
        prs.accepts("vcf").withRequiredArg().ofType(File.class);
        prs.accepts("avoid");

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            System.out.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        // extract values of each option
        verbose = options.has("verbose");
        bamfolog.setVerbose(verbose);

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
        if (options.has("output")) {
            out = (String) options.valueOf("output");
        }

        // options for computing effective coverage
        if (!settings.getOptionValues(options)) {
            return false;
        }

        if (options.has("notstranded")) {
            notstranded = true;
        } else {
            notstranded = false;
        }

        // options specific for errors calculations
        if (options.has("maxerrordepth")) {
            try {
                maxerrordepth = (Integer) options.valueOf("maxerrordepth");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter maxerrordepth: " + ex.getMessage());
                return false;
            }
        }

        if (options.has("maxallelic")) {
            try {
                maxallelic = (Double) options.valueOf("maxallelic");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter maxallelic: " + ex.getMessage());
                return false;
            }
        }

        if (options.has("bed")) {
            bedfile = (File) options.valueOf("bed");
            if (!bedfile.canRead()) {
                System.out.println("bed file is not readable");
                return false;
            }
        }

        if (options.has("vcf")) {
            vcffile = (File) options.valueOf("vcf");
            if (!vcffile.canRead()) {
                System.out.println("vcf file is not readable");
                return false;
            }
        }

        avoid = options.has("avoid");

        return true;
    }

    /**
     * set up an error-calculating utility.
     *
     * @param args
     *
     * arguments as given on the command line.
     *
     */
    public BamfoErrors(String[] args) {

        bamfolog = new BamfoLog();

        if (args == null) {
            printBamErrorsHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseBamErrorsParameters(args)) {
            return;
        }

        isReady = true;
    }

    /**
     * writes the header line
     *
     * @param outstream
     * @param label
     * @throws IOException
     */
    private void writeErrorsTable(OutputStream outstream) throws IOException {
        StringBuilder sb = new StringBuilder(4096);

        sb.append("file\trefBase\taltBase\tEligible\tErrors\tErrorRate\n");

        String filename = bamfile.getName();

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i != j) {
                    double errrate = (double) errors[i][j] / (double) eligible[i];
                    sb.append(filename).append("\t").append(BamfoCommon.ZeroToFourToBase(i)).
                            append("\t").append(BamfoCommon.ZeroToFourToBase(j)).
                            append("\t").append(eligible[i]).append("\t").append(errors[i][j]).
                            append("\t").append(errrate).append("\n");
                }
            }
        }

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
    private void errorscanBam(SAMFileReader inputSam, FastaReader genomereader,
            GenomeBitSet regions) throws IOException {

        if (verbose) {
            bamfolog.log("Starting getting errors with bam2x");
            String[] temp = settings.printAllOptions().split("\n");
            for (int i = 0; i < temp.length; i++) {
                bamfolog.log(temp[i]);
            }
            bamfolog.log("##maxallelic=" + maxallelic);
            bamfolog.log("##maxerrordepth=" + maxerrordepth);
            bamfolog.log("##notstranded=" + notstranded);
        }

        SAMFileHeader samHeader = inputSam.getFileHeader();

        // make data structures that will hold the genotype evidence
        int nowRef = -1, nowpos;
        int lastdrain = 1;
        String nowRefName;
        int nowRefLen = 0;
        HashMap<Integer, LocusSNVDataList> chrinfo = new HashMap<Integer, LocusSNVDataList>(16);

        // read each record, for each chromosome
        for (final SAMRecord samRecord : inputSam) {

            // only process aligned, primary records records, with mapping quality greater than zero
            // and non-duplicate
            int recordReference = samRecord.getReferenceIndex();
            if (recordReference > -1 && !samRecord.getNotPrimaryAlignmentFlag()
                    && !samRecord.getDuplicateReadFlag()) {

                nowpos = samRecord.getAlignmentStart();

                // check if the record starts a new chromosome
                // if so, finish processing the old chromosome and prepare for the next one
                if (recordReference != nowRef) {

                    // process the remaining loci on the chromosome
                    if (nowRef != -1) {
                        lastdrain = evalerrors(chrinfo, regions, genomereader, lastdrain, 1 + nowRefLen);
                    }

                    // get information about the new chromosome
                    nowRef = recordReference;
                    nowRefName = samRecord.getReferenceName();
                    nowRefLen = (samHeader.getSequence(nowRef)).getSequenceLength();
                    lastdrain = 1;

                    // get chromosome sequence from the genome reader (perhaps skip over missing chromosomes)
                    genomereader.readNext();
                    while (genomereader.hasThis() && !nowRefName.equals(genomereader.getChromosomeName())) {
                        genomereader.readNext();
                    }
                    if (!nowRefName.equals(genomereader.getChromosomeName())) {
                        bamfolog.log("Error: chromsome " + nowRefName + " does not appear in the genome reference file");
                        bamfolog.log("(check that chromsomes appear in the same order in bam and reference)");
                        return;
                    }
                    // check that the chromsome lengths in alignment and reference match
                    if (nowRefLen != genomereader.getChromosomeLength()) {
                        bamfolog.log("Error: discordant lengths on chromosome " + nowRefName);
                        return;
                    }

                    if (verbose) {
                        bamfolog.log(nowRefName);
                    }

                    // initialize a new chrinfo object that will store pileup information
                    chrinfo = new HashMap<Integer, LocusSNVDataList>(2 * cachelocations);
                }

                // parse information from this record to 
                // add the contribution of this read to the coverage
                try {
                    modifyGenotype(chrinfo, samRecord);
                } catch (Exception ex) {
                    bamfolog.log("Error:" + ex.getMessage());
                    bamfolog.log(samRecord.getSAMString());
                    return;
                }

                // perhaps drain the chrinfo if the fill index has run too far ahead of the drain index
                if (nowpos - lastdrain > cachelocations && nowRefLen - nowpos > cachelocations) {
                    lastdrain = evalerrors(chrinfo, regions, genomereader, lastdrain, nowpos);
                }
            }
        } // end of for loop over records

        // if there is still something left in the chrinfo, genotype from the current index beyond the chromosome length
        if (nowRef != -1) {
            lastdrain = evalerrors(chrinfo, regions, genomereader, lastdrain, 1 + nowRefLen);
        }

        if (verbose) {
            bamfolog.log("Genotyping complete");
        }

    }

    private void modifyGenotype(HashMap<Integer, LocusSNVDataList> chrinfo, SAMRecord record) {

        // create an object holding the record and some derived quantities 
        // this will provide easy access to the positions of each base, etc.
        BamfoRecord b2r = new BamfoRecord(record, settings.isTrimBtail(), settings.isTrimpolyedge());

        int[] pos = b2r.pos;

        // now for each position record the information        
        for (int nowindex = 0; nowindex < b2r.readlength; nowindex++) {

            // deal with simple alignments first
            if (pos[nowindex] > 0) {
                LocusSNVDataList nowGDL = chrinfo.get(pos[nowindex]);
                if (nowGDL == null) {
                    nowGDL = new LocusSNVDataList(record.getReferenceName() + ":" + pos[nowindex]);
                }

                // save information about this base
                nowGDL.add(b2r, nowindex);

                // make sure the object is added/updated back into the chrinfo
                chrinfo.put(pos[nowindex], nowGDL);
            }
        }
    }

    /**
     * processes readsin genomic regions between startpos (included) and endpos
     * (not included)
     *
     * @param outstream
     * @param chrinfo
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
    private int evalerrors(HashMap<Integer, LocusSNVDataList> chrinfo,
            GenomeBitSet regions,
            FastaReader genomereader, int startpos, int endpos) throws IOException {

        String chrname = genomereader.getChromosomeName();

        for (int i = startpos; i < endpos; i++) {
            LocusSNVDataList lgl = chrinfo.get(i);
            if (regions.get(chrname, i - 1) && lgl != null) {
                try {
                    updateErrors(lgl, genomereader.getBaseAtPositionBase1(i));
                } catch (Exception ex) {
                    bamfolog.log(true, "Exception at " + chrname + ":" + i);
                }
            }
            if (lgl != null) {
                chrinfo.remove(i);
            }
        }

        return endpos;
    }

    /**
     *
     * @param locus
     * @return
     *
     * the eligible sites and errors arrays are modified
     *
     */
    private void updateErrors(LocusSNVDataList locus, byte refbase) {

        // check depth threshold
        if (locus.size() < settings.getMindepth()) {
            return;
        }

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
        int tottot = totplus + totminus;

        int whichref = BamfoCommon.basesToZeroToFour((byte) refbase);
        int whichrefcomplement = BamfoCommon.basesToZeroToFourComplement((byte) refbase);

        // split totals according to reference base/alternative base
        int refplus = covplus[whichref];
        int refminus = covminus[whichref];
        int altplus = totplus - refplus;
        int altminus = totminus - refminus;
        int alttot = altplus + altminus;

        // find out the index of the alternate allele
        boolean[] iserror = new boolean[5];
        int numaltalleles = 0;
        for (int i = 0; i < 5; i++) {
            if (i != whichref && covtot[i] > 0) {
                iserror[i] = true;
                numaltalleles++;
            }
        }

        // check if this is not perhaps a variant (too many alternative alleles)
        if (alttot > maxerrordepth && ((double) alttot / (double) tottot) > maxallelic) {
            return;
        }

        // count the errors differently depending on whether 
        // user asked to look at by strand or by vcf
        if (!notstranded) {
            // this is the stranded case
            eligible[whichref] += totplus;
            eligible[whichrefcomplement] += totminus;

            // if an error is present, record it
            for (int i = 0; i < 5; i++) {
                if (i != whichref && iserror[i]) {
                    errors[whichref][i] += covplus[i];
                    errors[whichrefcomplement][BamfoCommon.basesToZeroToFourComplement((byte) BamfoCommon.ZeroToFourToBase(i))] += covminus[i];
                }
            }
        } else {
            // this is the case by vcf result
            eligible[whichref] += tottot;

            // if an error is present, record it
            for (int i = 0; i < 5; i++) {
                if (i != whichref && iserror[i]) {
                    errors[whichref][i] += covtot[i];
                }
            }
        }

    }

    /**
     *
     * @param ginfo
     * @param bedfile
     * @param vcffile
     * @param avoid
     * @return
     *
     * a bitset holding value true if the user is interested in the region
     *
     * @throws IOException
     */
    private GenomeBitSet loadRegions(GenomeInfo ginfo, File bedfile, File vcffile, boolean avoid) throws IOException {

        GenomeBitSet gset = new GenomeBitSet(ginfo);
        int numchrs = ginfo.getNumChromosomes();

        // one case is when the user does not specify any bedfile or vcf file
        // then, set up so that gset has true on whole genome
        if (bedfile == null && vcffile == null) {
            // explicitly set the values of the bitsets
            // avoid == true -> set default interest to whole genome, then 
            for (int i = 0; i < numchrs; i++) {
                int chrlen = ginfo.getChrLength(i);
                String chrname = ginfo.getChrName(i);
                gset.set(chrname, 0, chrlen, true);
            }
            return gset;
        }

        // if reached here, the user has something in mind (bed or vcf)
        // for default case, avoid = false. 
        // Set the background to false, and then activate regions specified by user.
        // for non-default case, avoid=true, it's the reverse        
        for (int i = 0; i < numchrs; i++) {
            int chrlen = ginfo.getChrLength(i);
            String chrname = ginfo.getChrName(i);
            gset.set(chrname, 0, chrlen, avoid);
        }

        String s;

        // read from a bed file
        if (bedfile != null) {
            BufferedReader br = BufferedReaderMaker.makeBufferedReader(bedfile);
            while ((s = br.readLine()) != null) {
                if (!s.startsWith("#")) {
                    String[] tokens = s.split("\t", 4);
                    int start = Integer.parseInt(tokens[1]);
                    int end = Integer.parseInt(tokens[2]);
                    gset.set(tokens[0], start, end, !avoid);
                }
            }
            br.close();
        }

        // read individual positions from a vcf file
        if (vcffile != null) {
            BufferedReader br2 = BufferedReaderMaker.makeBufferedReader(vcffile);
            while ((s = br2.readLine()) != null) {
                if (!s.startsWith("#")) {
                    String[] tokens = s.split("\t", 3);
                    int pos = Integer.parseInt(tokens[1]) - 1;
                    gset.set(tokens[0], pos, pos + 1, !avoid);
                }
            }
            br2.close();
        }

        return gset;
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
        GenomeInfo ginfo;
        try {
            ginfo = new GenomeInfo(new File(settings.getGenome()));
            genomereader = new FastaReader(BufferedReaderMaker.makeBufferedReader(settings.getGenome()));
        } catch (Exception ex) {
            System.out.println("Could not open the genome file: " + ex.getMessage());
            return;
        }

        // create the output stream
        OutputStream outstream;
        if (out.equals("stdout")) {
            outstream = System.out;
        } else {
            try {
                outstream = OutputStreamMaker.makeOutputStream(out);
            } catch (Exception ex) {
                System.out.println("could not create output file");
                return;
            }
        }

        // start processing, open the SAM file and start computing
        SAMFileReader inputSam = new SAMFileReader(bamfile);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());

        try {
            GenomeBitSet regions = loadRegions(ginfo, bedfile, vcffile, avoid);
            errorscanBam(inputSam, genomereader, regions);
            writeErrorsTable(outstream);
            inputSam.close();
        } catch (Exception ex) {
            System.out.println("Error during genotyping\n");
        }

        // close the stream
        if (outstream != System.out) {
            try {
                outstream.close();
            } catch (IOException ex) {
                System.out.println("Error closing vcf");
            }
        }

    }
}
