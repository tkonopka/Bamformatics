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

import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoRecord;
import bamfo.utils.BamfoSettings;
import bamfo.utils.BamfoTool;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
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
 * Utility to look through vcf and associated bam files in order to extract
 * detailed information about the variants. The output is a table with
 * information of the type encoded in the vcf plus pileup from the alignment.
 *
 *
 * @author tkonopka
 */
public class BamfoVcfDetail extends BamfoTool implements Runnable {

    private ArrayList<File> vcfs = new ArrayList<>(8);
    private ArrayList<File> bams = new ArrayList<>(8);
    private ArrayList<String> labels = new ArrayList<>(8);
    private File genome = null;
    private File genomeindex = null;
    private String out = "stdout";
    // SNVs and indels will be a hashmap of hashmap. 
    // The first key will be the chromosome
    // The second key will be the variant position|dbSNP|ref|alt1
    private HashMap<String, HashMap> SNVs = new HashMap<>(256, 0.75f);
    private HashMap<String, HashMap> indels = new HashMap<>(256, 0.75f);
    // chrlengths will be a hash of chromosome lengths
    private HashMap<String, Integer> chrlengths = new HashMap<>(256, 0.75f);
    private HashMap<String, byte[]> chrsequences = new HashMap<>(256, 0.75f);
    private ArrayList<String> chrnames = new ArrayList<>();
    private int numsamples = 0;
    // last record will be used in debugging and reporting errors
    private String lastrecord;
    private boolean verbose;
    // use the common genotyping option settings    
    private final static String[] settingtypes = {"minbasequal", "minmapqual",
        "minfromstart", "minfromend", "trim", "trimQB", "NRef", "genome", "validate"};
    private BamfoSettings settings = new BamfoSettings(settingtypes);
    //String specialread = "readname";

    /**
     * reads and remembers all chromosome sequences.
     *
     * @param fr
     *
     * @return
     *
     * the number of chromosomes actually loaded into memory
     *
     * @throws IOException
     */
    private int getGenomeSequences(FastaReader fr) throws IOException {

        if (indels.isEmpty()) {
            return 0;
        }

        int ans = 0;
        while (fr.hasNext()) {
            if (Thread.currentThread().isInterrupted()) {
                bamfolog.log("interrupted");
                return ans;
            }
            fr.readNext();
            System.gc();

            String nowchrname = fr.getChromosomeName();
            // find out if there are indels reported on this chromosome
            HashMap<Integer, VariantSummary> chrIndelsSummary = (HashMap<Integer, VariantSummary>) indels.get(nowchrname);
            // if yes, read in this chromosome sequence
            if (chrIndelsSummary != null && !chrIndelsSummary.isEmpty()) {
                byte[] nowseq = fr.getSequenceBase0(0, fr.getChromosomeLength());
                chrsequences.put(nowchrname, nowseq);
                ans++;
            }

        }
        return ans;

    }

    class VariantSummary {

        String ref;
        String alt;
        String dbSNP;
        String info;
        int position;
        // the following arrays will be of length samples
        double[] qualities;
        // arrays for storing high-quality evidence for ref and alt alleles
        int[] refcounts;
        int[] altcounts;
        int[] coverage;
        // arrays for storing the low-quality evidence
        int[] refcountsLowQ;
        int[] altcountsLowQ;
        int[] coverageLowQ;
        String[] filters;

        /**
         * This is a constructor suitable for SNVs. The specified ref and alt
         * strings are used for all the samples.
         *
         *
         * @param position
         *
         * position is 1-based
         *
         * @param numsamples
         *
         * number of samples that will characterize the location
         *
         */
        public VariantSummary(int position, String ref, String alt, String dbSNP, String info, int numsamples) {
            this.position = position;
            this.ref = ref;
            this.alt = alt;

            // this.alt2 = 'N';
            this.dbSNP = dbSNP;
            this.info = info;
            // these will all be initialized to zero by java
            qualities = new double[numsamples];

            refcounts = new int[numsamples];
            altcounts = new int[numsamples];
            coverage = new int[numsamples];

            refcountsLowQ = new int[numsamples];
            altcountsLowQ = new int[numsamples];
            coverageLowQ = new int[numsamples];

            filters = new String[numsamples];

            for (int i = 0; i < numsamples; i++) {
                filters[i] = ".";
            }
        }

        /**
         *
         * @return
         *
         * the sequence of the declared indel. For an insertion it returns
         * fragment of alt. For a deletion, it returns a fragment of ref
         *
         */
        private byte[] getIndel() {

            // need to figure out if it is an indel
            if (!VcfEntry.isIndel(ref, alt)) {
                return null;
            }

            if (ref.length() > 1) {
                return ref.substring(1).getBytes();
            } else {
                return alt.substring(1).getBytes();
            }
        }
    }

    /**
     * comparator of VariantSummary. Works by comparing the position integer.
     */
    class variantInfoComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            int pos1 = ((VariantSummary) o1).position;
            int pos2 = ((VariantSummary) o2).position;
            if (pos1 < pos2) {
                return -1;
            } else if (pos1 > pos2) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    private void printMultiVcfHelp() {
        outputStream.println("Bamformatics variantdetails: a tool for combining information from multiple vcf and bam files");
        outputStream.println();
        outputStream.println("General options:");
        outputStream.println("  --genome <file>          - genome file (must be indexed)");
        outputStream.println("  --vcf <file>             - vcf files");
        outputStream.println("  --bam <file>             - matching bam files");
        outputStream.println("  --label <String>         - label for columns");
        outputStream.println("  --output <String>        - prefix for output files");
        outputStream.println("  --verbose                - print progress information");
        outputStream.println();
        outputStream.println(settings.printHelp());
    }

    private boolean parseMultiVcfParameters(String[] args) {

        //Preferences prefs = Bam2Defaults.getPreferences();
        OptionParser prs = new OptionParser();
        // core options for multivcf
        prs.accepts("vcf").withRequiredArg().ofType(String.class);
        prs.accepts("bam").withRequiredArg().ofType(String.class);
        prs.accepts("label").withRequiredArg().ofType(String.class);
        //prs.accepts("genome").withRequiredArg().ofType(String.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);
        prs.accepts("verbose");

        // options for computing coverage at a locus
        settings.addOptionsToOptionParser(prs);

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            outputStream.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        if (options.has("output")) {
            out = (String) options.valueOf("output");
        } else {
            // out will be stdout by default
        }

        // get list of vcf and bam files
        if (options.has("vcf")) {
            List vcflist = (List) options.valuesOf("vcf");
            Iterator<String> it = vcflist.iterator();
            while (it.hasNext()) {
                vcfs.add(new File(it.next()));
            }
        } else {
            outputStream.println("missing required option --vcf");
            return false;
        }
        if (options.has("bam")) {
            List bamlist = (List) options.valuesOf("bam");
            Iterator<String> it = bamlist.iterator();
            while (it.hasNext()) {
                bams.add(new File(it.next()));
            }
        } else {
            outputStream.println("missing required option --bam");
            return false;
        }
        if (options.has("label")) {
            List bamlist = (List) options.valuesOf("label");
            Iterator<String> it = bamlist.iterator();
            while (it.hasNext()) {
                labels.add(it.next());
            }
        } else {
            outputStream.println("missing required option --label");
            return false;
        }

        // check the number of arguments of each kind is the same
        if (bams.size() != vcfs.size() || labels.size() != bams.size()) {
            outputStream.println("number of vcf, bam, and label arguments do not match");
            return false;
        }

        // check the files exist
        for (int i = 0; i < vcfs.size(); i++) {
            if (!vcfs.get(i).canRead()) {
                outputStream.println("Cannot read vcf file " + (String) vcfs.get(i).getName() + " " + vcfs.get(i).exists());
                return false;
            }
            if (!bams.get(i).canRead()) {
                outputStream.println("Cannot read bam file " + (String) bams.get(i).getName());
                return false;
            }
        }

        verbose = options.has("verbose");
        bamfolog.setVerbose(verbose);

        // options for computing effective coverage
        if (!settings.getOptionValues(options)) {
            return false;
        }

        genome = new File(settings.getGenome());
        genomeindex = new File(settings.getGenome() + ".fai");
        if (!genome.exists()) {
            outputStream.println("Genome file does not exist " + genome.getName());
            return false;
        }
        if (!genomeindex.canRead()) {
            outputStream.println("Cannot read genome index file " + genomeindex.getName());
            return false;
        }

        return true;
    }

    /**
     *
     * @param args
     *
     * command line arguments.
     *
     */
    public BamfoVcfDetail(String[] args) {

        // parse the input
        if (args == null || args.length == 0) {
            printMultiVcfHelp();
            return;
        }

        // parse the parameters, exit if not successful
        try {
            if (!parseMultiVcfParameters(args)) {
                return;
            }
        } catch (Exception ex) {
            outputStream.println("IO exception: " + ex.getMessage());
            return;
        }

        numsamples = vcfs.size();

        isReady = true;
    }

    /**
     * Fill the chrlengths map with chromosome name/length pairs and prepares
     * the SNVs hashmap
     */
    private void getGenomeInfo() throws FileNotFoundException, IOException {

        BufferedReader br = BufferedReaderMaker.makeBufferedReader(genomeindex);
        String s;
        while ((s = br.readLine()) != null) {
            String[] tokens = s.split("\t");
            int nowlen = Integer.parseInt(tokens[1]);
            // keep track of chromosome lengths
            chrlengths.put(tokens[0], nowlen);
            // keep track of their order using an array
            chrnames.add(tokens[0]);
            // for each chromosome, create a map with the variant information
            if (!SNVs.containsKey(tokens[0])) {
                SNVs.put(tokens[0], new HashMap(256, 0.75f));
            }
            if (!indels.containsKey(tokens[0])) {
                indels.put(tokens[0], new HashMap(256, 0.75f));
            }
        }

        br.close();
    }

    /**
     * scans all the vcf files and records all the single-nucleotide SNVs.
     *
     * @throws IOException
     */
    private boolean getVariantInfo() throws IOException {

        // some SNVs will be skipped. Will record those in the skipped log
        OutputStream logstream = OutputStreamMaker.makeOutputStream(out + ".skipped.log.gz");
        StringBuilder sb = new StringBuilder(65536);

        // number of items that will be skipped/ignored
        // can be reported in the verbose comments
        int numskipped = 0;
        int numincompatible = 0;
        VcfEntry nowvar;

        for (int i = 0; i < numsamples; i++) {
            BufferedReader br = BufferedReaderMaker.makeBufferedReader(vcfs.get(i));
            String s;

            // skip any lines that start with ##
            s = br.readLine();
            while ((s != null) && s.startsWith("#")) {
                s = br.readLine();
            }

            while (s != null) {

                nowvar = new VcfEntry(s);

                // check that it is a SNP
                boolean isIndel = nowvar.isIndel();
                boolean isSNV = !isIndel;

                // check that it is a SNP
                if (isSNV && (nowvar.getRef().length() != 1 || nowvar.getAlt().length() != 1)) {
                    isSNV = false;
                    sb.append(labels.get(i)).append("\t").append(s).append("\n");
                    numskipped++;
                }

                // record an SNV variant... 
                if (isSNV) {
                    HashMap hm = (HashMap) SNVs.get(nowvar.getChr());
                    int varposition = nowvar.getPosition();
                    if (!putOrUpdateVariant(nowvar, hm, varposition, i)) {
                        sb.append("incompatible\t").append(labels.get(i)).append("\t").append(s).append("\n");
                        numincompatible++;
                    }
                }
                // record an indel...
                if (isIndel) {
                    HashMap hm = (HashMap) indels.get(nowvar.getChr());
                    int varposition = nowvar.getPosition();
                    if (!putOrUpdateVariant(nowvar, hm, varposition, i)) {
                        sb.append("incompatible\t").append(labels.get(i)).append("\t").append(s).append("\n");
                        numincompatible++;
                    }
                }

                s = br.readLine();
            }

            // write this vcf skipped positions to the log file
            if (sb.length() > 0) {
                logstream.write(sb.toString().getBytes());
                sb = new StringBuilder(65536);
            }
        }

        // close the log for completeness
        logstream.close();

        // perhas write information to the verbose comments        
        bamfolog.log(verbose, "SNVs:        \t" + countVariants(SNVs));
        bamfolog.log(verbose, "Indels:      \t" + countVariants(indels));
        bamfolog.log(verbose, "Skipped:     \t" + numskipped);
        bamfolog.log(verbose, "Incompatible:\t" + numincompatible);

        // everything was fine...
        return true;
    }

    /**
     * count the number of variants stored in the SNVs or indels structures
     *
     * @param mp
     *
     * a hashmap of hashmaps
     *
     * @return
     *
     * the total size of the elements stored.
     *
     */
    private int countVariants(HashMap<String, HashMap> mp) {

        int ans = 0;

        Iterator it = mp.entrySet().iterator();
        while (it.hasNext()) {
            HashMap onchroms = (HashMap) ((Entry) it.next()).getValue();
            ans += onchroms.size();
        }
        return ans;
    }

    /**
     * This is a helper function for getVariantInfo. It updates a hashmap with
     * all variants. Information about a variant nowvar is either put into the
     * hashmap, or a new entry is created in the hashmap if this is a new
     * variant.
     *
     * @param nowvar
     * @param hm
     * @param varposition
     * @param numsample
     * @return
     */
    private boolean putOrUpdateVariant(VcfEntry nowvar, HashMap hm, int varposition, int numsample) {

        // first check if the variant is already in the database
        if (hm.containsKey(varposition)) {
            // need to check that this variant is equivalent to the old variant                        
            VariantSummary vI = (VariantSummary) hm.get(varposition);
            if (nowvar.getAlt().equals(vI.alt) && nowvar.getId().equals(vI.dbSNP)) {
                vI.qualities[numsample] = Double.parseDouble(nowvar.getQuality());
                vI.filters[numsample] = nowvar.getFilter();
            } else {
                vI.qualities[numsample] = Double.parseDouble(nowvar.getQuality());
                vI.filters[numsample] = "incompatible";
                return false;
            }

        } else {
            // it is not in the database already, so add/create it
            VariantSummary vI = new VariantSummary(varposition,
                    nowvar.getRef(), nowvar.getAlt(), nowvar.getId(), nowvar.getInfo(), numsamples);
            // update the quality for this indel
            vI.qualities[numsample] = Double.parseDouble(nowvar.getQuality());
            vI.filters[numsample] = nowvar.getFilter();
            hm.put(varposition, vI);
        }
        return true;
    }

    /**
     * Create a bitset showing at which location on a chromosome the SNVs are
     * located. Positions are stored as 1-based.
     *
     * @param chrlength
     * @param hm
     * @return
     */
    private BitSet makeVariantBitSet(int chrlength, HashMap hm) {

        // create the bitset for this chromosome
        BitSet chrbs = new BitSet(chrlength + 1);

        // fill it with true bits on the variant positions        
        Iterator it = hm.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            VariantSummary vI = (VariantSummary) entry.getValue();
            chrbs.set(vI.position);
        }

        return chrbs;
    }

    /**
     * get coverage information on SNVs from one bam file.
     *
     * @param sampleindex
     *
     * determines which bam file to genotype
     *
     * @return
     */
    private boolean getOneBAMInfo(int sampleindex) {

        // open the alignment file and start processing
        SAMFileReader inputSam = new SAMFileReader(bams.get(sampleindex));
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());

        SAMFileHeader samHeader = inputSam.getFileHeader();

        // is---BS will contain boolean information about whether a location has been flagged as a variant
        BitSet isSNVsBS = null;
        BitSet isIndelsBS = null;
        // chrsummary will contain links to the VariantSummary objects
        HashMap<Integer, VariantSummary> chrSNVsSummary = null;
        HashMap<Integer, VariantSummary> chrIndelsSummary = null;
        // chrinfo will contain detail information about genotype at a locus
        HashMap<Integer, LocusSNVDataList> chrSNVsInfo = new HashMap<>(16);
        HashMap<Integer, LocusIndelDataList> chrIndelsInfo = new HashMap<>(16);

        int nowreference = -1;
        int nowRefLen = 0;

        int cachelocations = 64;

        // these will be indexes walking on the chromosome
        int nowpos = 0;
        int lastdrain = 1;

        // read each record, for each chromosome, fix the reads and write them to the output        
        for (final SAMRecord samRecord : inputSam) {

            lastrecord = samRecord.getSAMString();

            int recordReference = samRecord.getReferenceIndex();
            // only process aligned, primary records, with mapping quality equal to the minimum or more
            // and non-duplicate
            if (recordReference >= 0 && !samRecord.getNotPrimaryAlignmentFlag()
                    && !samRecord.getReadUnmappedFlag() && !samRecord.getDuplicateReadFlag()) {

                nowpos = samRecord.getAlignmentStart();

                // check if the reference is the same as on the previous record
                if (recordReference != nowreference) {
                    // a new chromosome is starting                    

                    if (Thread.currentThread().isInterrupted()) {
                        bamfolog.log("interrupted");
                        return false;
                    }

                    // drain the chrinfo on SNVs collected up to now
                    if (nowreference != -1) {
                        lastdrain = drainChrInfo(sampleindex, chrSNVsInfo, chrSNVsSummary,
                                chrIndelsInfo, chrIndelsSummary, lastdrain, 1 + nowRefLen);
                    }
                    lastdrain = 1;

                    // reset the counter/variables with information about this chromosome                    
                    nowreference = recordReference;
                    String nowchrname = samRecord.getReferenceName();
                    bamfolog.log(verbose, nowchrname);
                    nowRefLen = (samHeader.getSequence(nowreference)).getSequenceLength();

                    // extract information about SNVs or Indels 
                    chrSNVsSummary = (HashMap<Integer, VariantSummary>) SNVs.get(nowchrname);
                    isSNVsBS = makeVariantBitSet((Integer) chrlengths.get(nowchrname), chrSNVsSummary);
                    chrIndelsSummary = (HashMap<Integer, VariantSummary>) indels.get(nowchrname);
                    isIndelsBS = makeVariantBitSet((Integer) chrlengths.get(nowchrname), chrIndelsSummary);

                    // get a new object holding details of genotypes on loci
                    chrSNVsInfo = new HashMap<Integer, LocusSNVDataList>(2 * cachelocations);
                    chrIndelsInfo = new HashMap<Integer, LocusIndelDataList>(cachelocations);
                }

                // process this read   
                try {
                    fillChrInfo(samRecord, isSNVsBS, chrSNVsInfo, isIndelsBS, chrIndelsInfo);
                } catch (Exception ex) {
                    bamfolog.log("Error during extraction: " + ex.getMessage());
                    bamfolog.log(samRecord.getSAMString());
                    return false;
                }

                // perhaps drain the chrinfo if the fill index has run too far ahead of the drain index
                if (nowpos - lastdrain > cachelocations && nowRefLen - nowpos > cachelocations) {
                    if (Thread.currentThread().isInterrupted()) {
                        bamfolog.log("interrupted");
                        return false;
                    }
                    lastdrain = drainChrInfo(sampleindex, chrSNVsInfo, chrSNVsSummary,
                            chrIndelsInfo, chrIndelsSummary,
                            lastdrain, nowpos);
                }
            }
        }

        // if there is still something left in the chrinfo, 
        // make sure that it goes into the chrSummary
        if (nowreference != -1) {
            lastdrain = drainChrInfo(sampleindex, chrSNVsInfo, chrSNVsSummary,
                    chrIndelsInfo, chrIndelsSummary, lastdrain, 1 + nowRefLen);
        }

        inputSam.close();
        return true;
    }

    /**
     * extract information from bam files.
     *
     * This only calls getOneBAMInfo for each bam file needing processing.
     *
     * @return
     *
     * true if all went well.
     *
     * @throws IOException
     */
    private boolean getBAMInfo() throws IOException {

        boolean ok = true;
        for (int i = 0; i < numsamples; i++) {
            if (Thread.currentThread().isInterrupted()) {
                bamfolog.log("interrupted");
                return false;
            }
            bamfolog.log(verbose, "Processing bam " + bams.get(i).getCanonicalPath());
            ok = getOneBAMInfo(i);
            if (!ok) {
                bamfolog.log("Error during execution in file " + bams.get(i).getCanonicalPath());
                return false;
            }
            System.gc();
        }
        return ok;
    }

    private void outputMultiLog() throws FileNotFoundException, IOException {
        OutputStream outlogstream = OutputStreamMaker.makeOutputStream(out + ".log.gz");

        // output the log
        StringBuilder sb = new StringBuilder(4096);
        sb.append("label\tvcf\tbam\tfullvcf\tfullbam\n");
        for (int i = 0; i < numsamples; i++) {
            sb.append(labels.get(i)).append("\t").
                    append(vcfs.get(i).getName()).append("\t").
                    append(bams.get(i).getName()).append("\t").
                    append(vcfs.get(i).getCanonicalFile()).append("\t").
                    append(bams.get(i).getCanonicalFile()).append("\n");
        }
        outlogstream.write(sb.toString().getBytes());
        outlogstream.close();
    }

    /**
     * prints out the contents of the
     */
    private void outputMultivcf(HashMap<String, HashMap> SNVs, String outfile) throws FileNotFoundException, IOException {

        // create streams for the output 
        OutputStream outstream = OutputStreamMaker.makeOutputStream(outfile);

        // Output the header line - very important
        StringBuilder sb = new StringBuilder();
        sb.append("chr\tposition\tdbSNP\trefBase\taltBase\tinfo");
        for (int i = 0; i < numsamples; i++) {
            String nowlabel = labels.get(i);
            sb.append("\t").append(nowlabel).append(".qual").
                    append("\t").append(nowlabel).append(".filter").
                    append("\t").append(nowlabel).append(".cov").
                    append("\t").append(nowlabel).append(".cov.low").
                    append("\t").append(nowlabel).append(".ref").
                    append("\t").append(nowlabel).append(".ref.low").
                    append("\t").append(nowlabel).append(".alt").
                    append("\t").append(nowlabel).append(".alt.low");
        }
        sb.append("\n");
        outstream.write(sb.toString().getBytes());

        // Then, write out the SNVs chromosome by chromosome
        for (int i = 0; i < chrnames.size(); i++) {
            sb = new StringBuilder(262144);
            String nowchrname = chrnames.get(i);
            // get the map with SNVs for this chromosome
            HashMap variantshm = (HashMap) SNVs.get(nowchrname);

            // get all the SNVs and sort them
            ArrayList<VariantSummary> varlist = new ArrayList<>(variantshm.size());
            int varlistsize = variantshm.size();
            Iterator it = variantshm.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                varlist.add((VariantSummary) entry.getValue());
            }
            Collections.sort(varlist, new variantInfoComparator());

            // loop through the variant list and print it out
            for (int j = 0; j < varlistsize; j++) {
                VariantSummary vI = varlist.get(j);
                sb.append(nowchrname).append("\t").append(vI.position).
                        append("\t").append(vI.dbSNP).append("\t").
                        append(vI.ref).append("\t").append(vI.alt).
                        append("\t").append(vI.info);
                for (int k = 0; k < numsamples; k++) {
                    sb.append("\t").append(vI.qualities[k]).
                            append("\t").append(vI.filters[k]).
                            append("\t").append(vI.coverage[k]).
                            append("\t").append(vI.coverageLowQ[k]).
                            append("\t").append(vI.refcounts[k]).
                            append("\t").append(vI.refcountsLowQ[k]).
                            append("\t").append(vI.altcounts[k]).
                            append("\t").append(vI.altcountsLowQ[k]);
                }
                sb.append("\n");

                // write to the output if Buffer gets too large
                // write this chromosome information
                if (sb.length() > 261000) {
                    outstream.write(sb.toString().getBytes());
                    sb = new StringBuilder(262144);
                }
            }

            // write this chromosome information
            if (sb.length() > 0) {
                outstream.write(sb.toString().getBytes());
                // new string builder will be initialized at the beginning of the new chromosome
            }
        }
        outstream.close();
    }

    private void fillChrInfoForIndels(byte[] thischrsequence, SAMRecord record, ArrayList<CigarElement> cigarelements, byte[] bases,
            int maxpos,
            BitSet chrIndelsBS, HashMap<Integer, LocusIndelDataList> chrIndelsInfo) {

        int readlength = record.getReadLength();
        int mapquality = record.getMappingQuality();
        int nowp = record.getAlignmentStart();
        int nowi = 0;
        // loop over the cigars  
        for (int i = 0; i < cigarelements.size(); i++) {
            CigarElement ce = cigarelements.get(i);
            int celen = ce.getLength();

            boolean heredeletion = false;
            boolean hereinsertion = false;
            byte[] indel = null;
            int indelstart = nowp;
            int indelindex = nowi;

            switch (ce.getOperator()) {
                case M:
                    nowp += celen;
                    nowi += celen;
                    break;
                case D:
                    nowp += celen;
                    indel = BamfoCommon.getSequenceBase1(thischrsequence, indelstart, nowp - 1);
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
                    nowi += celen;
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
                int anchorpos = BamfoCommon.getAnchorPosition(thischrsequence, indelstart, indel);
                // evaluate anchor relative to the read sequence
                int readanchorpos = BamfoCommon.getAnchorPosition(bases, indelindex, indel);
                int anchorpos2 = indelstart - (indelindex - readanchorpos);

                // avoid bugs when positions fall off left edge of chromosome
                anchorpos = Math.max(1, anchorpos);
                anchorpos2 = Math.max(1, anchorpos2);
                indelstart = Math.max(1, indelstart);

                boolean usereadanchor = false;
                // use the anchor that is furthest to the left
                if (anchorpos > anchorpos2) {
                    anchorpos = anchorpos2;
                    usereadanchor = true;
                }

                // record this indel if the anchor position 
                // has been declared as interesting
                if (chrIndelsBS.get(anchorpos)) {

                    // get the anchor sequence either from the genome or from the read itself
                    byte[] anchor;
                    byte anchorbase = 'N';
                    if (usereadanchor) {
                        anchor = new byte[1 + indelindex - readanchorpos];
                        System.arraycopy(bases, readanchorpos - 1, anchor, 0, anchor.length);
                        anchorbase = bases[readanchorpos - 1];
                    } else {
                        anchor = BamfoCommon.getSequenceBase1(thischrsequence, anchorpos, indelstart);
                        anchorbase = thischrsequence[anchorpos - 1];

                    }
                    LocusIndelDataList nowIDL = chrIndelsInfo.get(anchorpos);
                    if (nowIDL == null) {
                        nowIDL = new LocusIndelDataList(anchorpos, anchorbase);
                        chrIndelsInfo.put(anchorpos, nowIDL);
                    }

                    //save information about this indel
                    nowIDL.add(anchor, indel, indelstart, mapquality, record.getReadNegativeStrandFlag(), nowi, readlength,
                            record.getReadName(), nowp >= maxpos, hereinsertion);
                }
            }
        }
    }

    /**
     * use the read information. Remember information about bases that fall on
     * declared SNVs
     *
     *
     * @param sampleindex
     * @param record
     * @param positions
     * @param chrSNVsBS
     * @param chrSNVsInfo
     *
     */
    private void fillChrInfo(SAMRecord record,
            BitSet chrSNVsBS, HashMap<Integer, LocusSNVDataList> chrSNVsInfo,
            BitSet chrIndelsBS, HashMap<Integer, LocusIndelDataList> chrIndelsInfo) {

        // create an object holding the record and some derived quantities 
        // this will provide easy access to the positions of each base, etc.
        BamfoRecord b2r = new BamfoRecord(record, settings.isTrimBtail(), settings.isTrimpolyedge());

        // deal with single bases        
        for (int nowindex = 0; nowindex < b2r.readlength; nowindex++) {

            // check that this position is declared as a variant
            // also avoid processing the position if it is an insertion or clip or whatever
            int nowpos = b2r.pos[nowindex];
            //
            if (nowpos > 0 && (chrSNVsBS.get(nowpos) || chrIndelsBS.get(nowpos))) {

                LocusSNVDataList nowGDL = chrSNVsInfo.get(nowpos);
                if (nowGDL == null) {
                    nowGDL = new LocusSNVDataList(record.getReferenceName() + ":" + nowpos);
                }

                // save information about this base
                nowGDL.add(b2r, nowindex);

                // make sure the object is added/updated back into the chrinfo
                chrSNVsInfo.put(nowpos, nowGDL);
            }

        } // end of loop over indexes in bases/positions/qualities

        // deal with indels if there are any
        if (b2r.readhasindel) {
            byte[] thisChrSequence = chrsequences.get(record.getReferenceName());
            if (thisChrSequence != null) {
                fillChrInfoForIndels(thisChrSequence, record, b2r.cigarelements, b2r.bases, b2r.overlapstart,
                        chrIndelsBS, chrIndelsInfo);
            }
        }
    }

    /**
     * extract information from the chrinfo object and copy a simplified
     * representation into the chrsummary and VariantSummary objects
     *
     * @param chrSNVsInfo
     * @param chrSNVsSummary
     * @param start
     * @param end
     * @return
     */
    private int drainChrInfo(int sampleindex,
            HashMap<Integer, LocusSNVDataList> chrSNVsInfo,
            HashMap<Integer, VariantSummary> chrSNVsSummary,
            HashMap<Integer, LocusIndelDataList> chrIndelsInfo,
            HashMap<Integer, VariantSummary> chrIndelsSummary,
            int start, int end) {

        // quick check that chrinfo actually has some data to process
        if (chrSNVsInfo.isEmpty() && chrIndelsInfo.isEmpty()) {
            return end;
        }

        // consider each position in the given interval and extract information about each present item
        for (int i = start; i < end; i++) {

            // get information about indels and SNVs at this position
            LocusIndelDataList indellocus = chrIndelsInfo.get(i);
            LocusSNVDataList SNVlocus = chrSNVsInfo.get(i);

            VariantSummary indelsummary = chrIndelsSummary.get(i);

            // drain the indels first (this will look into chrIndelsInfo and chrSNVsInfo)            
            if (indelsummary != null && SNVlocus != null) {
                // get the variant summary object
                //VariantSummary vI = (VariantSummary) chrIndelsSummary.get(i);                
                updateVIforIndels(indelsummary, SNVlocus, indellocus, sampleindex);
                chrIndelsInfo.remove(i);
            }

            // drain the SNVs
            if (SNVlocus != null) {
                // get the variant summary object
                VariantSummary vI = (VariantSummary) chrSNVsSummary.get(i);

                // the locus may be non null because there was an SNV there, 
                // or because it was used as an auxiliary for indel
                // thus the vI is not necessarily a valid object. 
                // Only update it if the position was declared an SNV                                
                if (vI != null) {
                    updateVIforSNVs(vI, SNVlocus, sampleindex);
                }
                // clean up the chrinfo map - remove the locus                 
                chrSNVsInfo.remove(i);
            }

        }
        // for the return value, give the first position that was not treated in this loop, i.e. the end position        
        return end;
    }

    /**
     * This function assumes
     *
     * @param vI
     * @param indellocus
     * @param sampleindex
     */
    private void updateVIforIndels(VariantSummary vI, LocusSNVDataList SNVlocus,
            LocusIndelDataList indellocus, int sampleindex) {

        // get an estimate of the coverage using the SNVlocus information
        // this will give valuable information about the coverage (low and high quality)                
        updateVIforSNVs(vI, SNVlocus, sampleindex);
       
        // get the byte sequence of the declared indel 
        byte[] vIseq = vI.getIndel();

        // but the ref, alt, refLowQ, altLowQ estimates will be off here. 
        // So I reset them here and then re-evaluate them further down in this function.
        vI.refcounts[sampleindex] = 0;
        vI.altcounts[sampleindex] = 0;
        vI.altcountsLowQ[sampleindex] = 0;

        // count the number of indels among high quality reads
        int altcounts = 0, altcountsLowQ = 0;

        // get information about the types of indels encountered and the number of reads
        // showing each kind
        ArrayList<LocusIndelData> hereindels = new ArrayList<>(8);
        ArrayList<Integer> counts = new ArrayList<>(8);
        if (indellocus != null) {
            indellocus.getIndelCounts(hereindels, counts,
                    settings.getMinfromstart(), settings.getMinfromend(), settings.getMinmapqual());
        }
        // look through the intermediate results and count the number of indels, i.e. alternative counts
        for (int j = 0; j < counts.size(); j++) {
            int nowcount = counts.get(j);
            LocusIndelData nowindel = hereindels.get(j);
            byte[] nowseq = nowindel.getIndel();
            if (byteEqual(nowseq, vIseq)) {
                altcounts += nowcount;
            } else {
                altcountsLowQ += nowcount;
            }
        }

        // the reported altcounts and refcounts can now be estimated
        vI.altcounts[sampleindex] = altcounts;

        // from the total coverage and from the number of alternative counts (indels)
        // compute the number of reference ounts
        vI.refcounts[sampleindex] = vI.coverage[sampleindex] - altcounts - altcountsLowQ;

        // for the vI.altCountsLowQ, I want to include the low-mapping quality reads
        // as well as those reads that report a different indel sequence.
        // so re-evaluate hereindels and counts
        int lowaltcounts = 0, lowaltcountsLowQ = 0;

        hereindels = new ArrayList<>(8);
        counts = new ArrayList<>(8);
        if (indellocus != null) {
            indellocus.getIndelCounts(hereindels, counts, 0, 0, 0);
        }

        // look through the intermediate results and count the number of indels, i.e. alternative counts
        for (int j = 0; j < counts.size(); j++) {
            int nowcount = counts.get(j);
            LocusIndelData nowindel = hereindels.get(j);
            byte[] nowseq = nowindel.getIndel();
            if (byteEqual(nowseq, vIseq)) {
                lowaltcounts += nowcount;
            } else {
                lowaltcountsLowQ += nowcount;
            }
        }

        // the reported count of low-quality alternative alleles 
        // will consist of (same sequence indels with low mapping quality)
        // plus (different sequence indels regardless of mapping quality)
        vI.altcountsLowQ[sampleindex] = (lowaltcounts - altcounts) + lowaltcountsLowQ;

    }

    /**
     * This will update some fields in vI with information from the SNVlocus.
     * The updated fields will be coverage, refcounts, altcounts, coverageLowQ,
     * refcountsLowQ, altcountsLowQ. Updates will come from one SNVlocus list,
     * i.e. will affect only on sample described in the vI.
     *
     * @param vI
     * @param SNVlocus
     * @param sampleindex
     *
     * the index of the bam file being processed.
     *
     */
    private void updateVIforSNVs(VariantSummary vI, LocusSNVDataList SNVlocus, int sampleindex) {

        // helper arrays that will store number of reads with ref/alt
        int[] covtot = new int[5];
        int[] covplus = new int[5];
        int[] covminus = new int[5];
        int[] maxns = new int[5];
        // make arrays to store counts for all information (including low quality)
        int[] lowcovtot = new int[5];
        int[] lowcovplus = new int[5];
        int[] lowcovminus = new int[5];
        int[] lowmaxns = new int[5];

        // extract the coverage counts on the locus
        int tottot = 0;
        SNVlocus.getCoverageCounts(covplus, covminus, maxns,
                settings.getMinfromstart(), settings.getMinfromend(),
                settings.getMinbasequal(), settings.getMinmapqual());
        // sum the totals 
        for (int j = 0; j < 5; j++) {
            covtot[j] = covplus[j] + covminus[j];
            tottot += covtot[j];
        }

        // extract all counts on the locus (inlcuding low coverage)
        int lowtottot = 0;
        SNVlocus.getCoverageCounts(lowcovplus, lowcovminus, lowmaxns,
                0, 0, (byte) 0, 0);
        // sum the totals 
        for (int j = 0; j < 5; j++) {
            lowcovtot[j] = lowcovplus[j] + lowcovminus[j] - covtot[j];
            lowtottot += lowcovtot[j];
        }

        // now update the variant summary object with the total "effective" coverage
        // and the evidence for the ref and alt abses
        vI.coverage[sampleindex] = tottot;
        vI.refcounts[sampleindex] = covtot[BamfoCommon.basesToZeroToFour(vI.ref.charAt(0))];
        if (settings.isNRef()) {
            vI.refcounts[sampleindex] += covtot[BamfoCommon.codeN];
        }
        vI.altcounts[sampleindex] = covtot[BamfoCommon.basesToZeroToFour(vI.alt.charAt(0))];

        // repeat for the low quality bases                
        vI.coverageLowQ[sampleindex] = lowtottot;
        vI.refcountsLowQ[sampleindex] = lowcovtot[BamfoCommon.basesToZeroToFour(vI.ref.charAt(0))];
        if (settings.isNRef()) {
            vI.refcounts[sampleindex] += lowcovtot[BamfoCommon.codeN];
        }
        vI.altcountsLowQ[sampleindex] = lowcovtot[BamfoCommon.basesToZeroToFour(vI.alt.charAt(0))];
    }

    /**
     * checks the identity of two arrays
     *
     * @param a
     * @param b
     * @return
     *
     * true if contents of a and b are exactly equal.
     */
    private static boolean byteEqual(byte[] a, byte[] b) {

        if (a == b) {
            return true;
        }

        // check their lengths
        int alen = a.length;
        if (alen != b.length) {
            return false;
        }

        // check each base one at a time
        for (int i = 0; i < alen; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        return true;
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

        long starttime = System.nanoTime();
        bamfolog.log(verbose, "Starting Bamformatics variantdetails");

        // find the lengths of all the chromosomes
        try {
            bamfolog.log(verbose, "Extracting genome information");
            getGenomeInfo();
        } catch (Exception ex) {
            outputStream.println("Error processing genome index: " + ex.getMessage());
        }

        if (Thread.currentThread().isInterrupted()) {
            bamfolog.log("interrupted");
            return;
        }

        // read all the vcfs to get positions of all the SNVs
        try {
            bamfolog.log(verbose, "Merging variant positions");
            if (!getVariantInfo()) {
                return;
            }
        } catch (Exception ex) {
            outputStream.println("Error processing vcf files: " + ex.getMessage());
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            bamfolog.log("interrupted");
            return;
        }

        // read through bam files and extract the coverage, rel, alt1 counts
        try {
            bamfolog.log(verbose, "Loading genome sequence (for indels)");
            int numloaded = getGenomeSequences(new FastaReader(BufferedReaderMaker.makeBufferedReader(settings.getGenome())));
            bamfolog.log(verbose, "Loaded " + numloaded + " chromosome sequences");
        } catch (Exception ex) {
            outputStream.println("Could not open the genome file: " + ex.getMessage());
        }

        if (Thread.currentThread().isInterrupted()) {
            bamfolog.log("interrupted");
            return;
        }

        // read through bam files and extract the coverage, rel, alt1 counts
        try {
            bamfolog.log(verbose, "Extracting information from bam files");
            if (!getBAMInfo()) {
                return;
            }
        } catch (Exception ex) {
            outputStream.println("Error processing bam files: " + ex.getMessage());
            outputStream.println(lastrecord);
        }

        if (Thread.currentThread().isInterrupted()) {
            bamfolog.log("interrupted");
            return;
        }

        // output the combined information 
        try {
            outputMultiLog();
            bamfolog.log(verbose, "Writing output for snvs");
            outputMultivcf(SNVs, out + ".SNVs.txt.gz");
            bamfolog.log(verbose, "Writing output for indels");
            outputMultivcf(indels, out + ".indels.txt.gz");
        } catch (Exception ex) {
            outputStream.println("Error producing output: " + ex.getMessage());
        }

        int runtime = (int) Math.floor((double) (System.nanoTime() - starttime) / 1000000000);
        bamfolog.log(verbose, "Done (" + runtime + " seconds)");

    }
}
