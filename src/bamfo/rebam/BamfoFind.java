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
package bamfo.rebam;

import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoSettings;
import bamfo.utils.BedRegionsCounter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.OutputStreamMaker;
import net.sf.samtools.*;

/**
 * Utility to find locations or read with certain features in a bam file. E.g.
 * find locations or read ids for all insertions. E.g. find location or reads
 * with soft clipping longer than a threshold number of bases.
 *
 *
 * @author tkonopka
 */
public class BamfoFind implements Runnable {

    private File inbam = null;
    private String out = "stdout";
    //String idfile = "stdin";
    private boolean insertions = false;
    private boolean deletions = false;
    private int softclips = Integer.MAX_VALUE;
    private boolean unpaired = false;
    private int longinsert = Integer.MAX_VALUE;
    private boolean crosschromosome = false;
    private boolean improper = false;
    private boolean unmapped = false;
    // output format can be either "ids" or "bed"
    private String report = "ids";
    private boolean isReady = false;
    private final static String[] settingtypes = {"validate"};
    private BamfoSettings settings = new BamfoSettings(settingtypes);

    private void printFindHelp() {
        System.out.println("Bamformatics getids: obtain ids of reads with certain properties");
        System.out.println();
        System.out.println("General options:");
        System.out.println(" --bam <File>              - input alignment");
        System.out.println(" --report <String>         - output format [accepted values: bed, ids]");
        System.out.println(" --output <File>           - output file or stdout");
        System.out.println();
        System.out.println("Find options:");
        System.out.println(" --insertions              - reads with insertions");
        System.out.println(" --deletions               - reads with deletions");
        System.out.println(" --indels                  - reads with insertions or deletions");
        System.out.println(" --unpaired                - reads with unmapped mate");
        System.out.println(" --unmapped                - unmapped reads");
        System.out.println(" --cross                   - paired reads on different chromosomes");
        System.out.println(" --improper                - reads with improper pairing");
        System.out.println(" --clipped <int>           - reads with <int> or more bases clipped");
        System.out.println(" --insert <int>            - insert size of <int> or more bases");

        // also print options from the common set
        System.out.println();
        System.out.println(settings.printHelp());
    }

    private boolean parseFindParameters(String[] args) {

        OptionParser prs = new OptionParser();
        // core options for multivcf        
        prs.accepts("bam").withRequiredArg().ofType(String.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);
        //prs.accepts("ids").withRequiredArg().ofType(String.class);
        prs.accepts("insertions");
        prs.accepts("deletions");
        prs.accepts("indels");
        prs.accepts("cross");
        prs.accepts("unpaired");
        prs.accepts("improper");
        prs.accepts("unmapped");
        prs.accepts("clipped").withRequiredArg().ofType(Integer.class);
        prs.accepts("insert").withRequiredArg().ofType(Integer.class);
        prs.accepts("report").withRequiredArg().ofType(String.class);

        // some options for genotyping
        settings.addOptionsToOptionParser(prs);


        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            System.out.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        if (options.has("bam")) {
            inbam = new File((String) options.valueOf("bam"));
            if (!inbam.canRead()) {
                System.out.println("Cannot read input bam file");
                return false;
            }
        } else {
            System.out.println("missing required parameter --bam");
            return false;
        }

        // if output is not set, will use stdout
        if (options.has("output")) {
            out = (String) options.valueOf("output");
        }

        if (options.has("insertions")) {
            insertions = true;
        }
        if (options.has("deletions")) {
            deletions = true;
        }
        if (options.has("unpaired")) {
            unpaired = true;
        }
        if (options.has("unmapped")) {
            unmapped = true;
        }
        if (options.has("cross")) {
            crosschromosome = true;
        }
        if (options.has("improper")) {
            improper = true;
        }
        if (options.has("indels")) {
            insertions = true;
            deletions = true;
        }
        if (options.has("clipped")) {
            softclips = (Integer) options.valueOf("clipped");
        }
        if (options.has("insert")) {
            longinsert = (Integer) options.valueOf("insert");
        }
        if (options.has("report")) {
            report = (String) options.valueOf("report");
            report = report.toLowerCase();
            if (!report.equals("bed") && !report.equals("ids") && !report.equals("id")) {
                System.out.println("Unrecognized report format: " + report);
                return false;
            }
        }

        // get the genotyping-style options 
        if (!settings.getOptionValues(options)) {
            return false;
        }

        return true;
    }

    /**
     * scans a bam file. Outputs read id names to the output file/stream.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void getIdsFromBam() throws FileNotFoundException, IOException {

        SAMFileReader inputSam = new SAMFileReader(inbam);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());

        OutputStream outs = OutputStreamMaker.makeOutputStream(out);
        StringBuilder sb = new StringBuilder(65536);

        // read each record, check if the read name is among those wanted
        // if yes/no, copy the record into separate files
        for (final SAMRecord record : inputSam) {

            Cigar readcigar = record.getCigar();
            //int[] readpos = BamfoCommon.getBasePositions(samRecord);
            boolean hit = false;

            // determine if this read should be output
            hit = hit || (insertions && BamfoCommon.containsInsertion(readcigar));
            hit = hit || (deletions && BamfoCommon.containsDeletion(readcigar));
            hit = hit || (softclips < Integer.MAX_VALUE
                    && BamfoCommon.countClippedBases(readcigar) >= softclips);
            hit = hit || getHit(record);
            
            // then actually output the id
            if (hit) {
                sb.append(record.getReadName()).append("\n");
                if (sb.length() > 65000) {
                    outs.write(sb.toString().getBytes());
                    sb = new StringBuilder(65536);
                }
            }
        }

        if (sb.length() > 0) {
            outs.write(sb.toString().getBytes());
        }

        inputSam.close();
        outs.close();
    }

    /**
     * Determines if a record matches some of the critera set forth in the 
     * command line parameters.
     * 
     * @param record
     * @return 
     * 
     * true if the record matches the criteria specified on the command line for
     * cross chromosome, unpaired, improper pair, long insert.
     * 
     */
    private boolean getHit(SAMRecord record) {
        boolean hit = false;
        hit = hit || (unpaired && record.getMateUnmappedFlag());
        hit = hit || (improper && !record.getProperPairFlag());        
        hit = hit || (unmapped && record.getReadUnmappedFlag());
        hit = hit || (crosschromosome && record.getReferenceIndex() != record.getMateReferenceIndex());
        hit = hit || (longinsert < Integer.MAX_VALUE
                && (record.getMateReferenceIndex() == record.getReferenceIndex())
                && Math.abs(record.getAlignmentStart() - record.getMateAlignmentStart()) > longinsert);
        return hit;
    }
    
    /**
     *
     * Worker function for detecting and counting events like insertions.
     *
     * @param record
     *
     * read to analyze
     *
     * @param bed
     *
     * object holding event counts. This will be modified/incremented if the
     * record contains an event of interest. It will be left alone if read does
     * not contain an event of interest.
     *
     */
    private void findAndRecord(SAMRecord record, BedRegionsCounter bed) {

        Cigar cigar = record.getCigar();
        ArrayList<CigarElement> recordce = new ArrayList(cigar.getCigarElements());

        // nowpos will hold the 0-based coordinate of the current position
        // start at the alignment start
        int nowpos = record.getAlignmentStart() - 1;

        // check if region can be recorded based on insert size, cross chromosome, etc
        boolean hit = getHit(record);        
        if (hit) {
            bed.add(record.getReferenceName(), nowpos, record.getAlignmentEnd());
        }

        for (int i = 0; i < recordce.size(); i++) {
            CigarElement ce = recordce.get(i);
            if (ce.getOperator() == CigarOperator.M) {
                // skip perfect matches 
                nowpos += ce.getLength();
            } else if (ce.getOperator() == CigarOperator.D) {
                // record the deletion, then skip the correct number of bases                
                if (deletions & !hit) {
                    bed.add(record.getReferenceName(), nowpos, nowpos + ce.getLength());
                }
                nowpos += ce.getLength();
            } else if (ce.getOperator() == CigarOperator.N || ce.getOperator() == CigarOperator.P) {
                // here the read skips a portion of the reference coordinate system
                nowpos += ce.getLength();
            } else if (ce.getOperator() == CigarOperator.S) {
                if (softclips < Integer.MAX_VALUE && ce.getLength() >= softclips && !hit) {
                    bed.add(record.getReferenceName(), nowpos, nowpos);
                }
            } else if (ce.getOperator() == CigarOperator.I) {
                if (insertions & !hit) {
                    bed.add(record.getReferenceName(), nowpos, nowpos);
                }
            } else {
                // whether the element is an insertion, or soft clip, or anything else, don't do anything
            }
        }


    }

    /**
     * scans a bam file. Outputs a bed table to the output file/stream.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void getBedFromBam() throws FileNotFoundException, IOException {

        SAMFileReader inputSam = new SAMFileReader(inbam);
        inputSam.setValidationStringency(SAMFileReader.ValidationStringency.LENIENT);

        // get the names of all chromosomes
        SAMSequenceDictionary seqdict = inputSam.getFileHeader().getSequenceDictionary();
        ArrayList<String> mychroms = new ArrayList<>();
        for (int i = 0; i < seqdict.size(); i++) {
            mychroms.add(seqdict.getSequence(i).getSequenceName());
        }

        // create a bedregions object that will hold the regions
        BedRegionsCounter mybedcounter = new BedRegionsCounter(mychroms);

        OutputStream outs = OutputStreamMaker.makeOutputStream(out);

        // read each record, check if the read name is among those wanted
        // if yes/no, copy the record into separate files
        for (final SAMRecord samRecord : inputSam) {

            // check that the record is aligned 
            int recordReference = samRecord.getReferenceIndex();
            if (recordReference > -1) {
                findAndRecord(samRecord, mybedcounter);
            }

        }

        // write out the bed table
        outs.write(mybedcounter.toString().getBytes());

        // close the files for clean exit
        inputSam.close();
        outs.close();

    }

    /**
     *
     *
     * @param args
     *
     * Command line parameters.
     *
     */
    public BamfoFind(String[] args) {

        // parse the input
        if (args == null || args.length == 0) {
            printFindHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseFindParameters(args)) {
            return;
        }

        isReady = true;
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

        try {
            if (report.equalsIgnoreCase("id") || report.equalsIgnoreCase("ids")) {
                getIdsFromBam();
            } else if (report.equals("bed")) {
                getBedFromBam();
            }

        } catch (Exception ex) {
            System.out.println("Error processing bam: " + ex.getMessage());
        }
    }
}
