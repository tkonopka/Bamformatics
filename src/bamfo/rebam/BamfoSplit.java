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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import net.sf.samtools.*;

/**
 * Utility to split an alignment file into two parts by read ids. E.g. Given a
 * file with interesting read ids, the utility will create one bam file
 * containing only those reads, and another bam file containing the remaining
 * ones.
 *
 *
 * @author tkonopka
 */
public class BamfoSplit implements Runnable {

    // input/output
    private File inbam = null;
    private String out = "output";
    private String idfile = "stdin";
    private boolean hitsonly = false;
    // hashmap will store the ids of interesting reads
    private HashMap<String, Boolean> wantedids = new HashMap<String, Boolean>(1024);
    private boolean isReady = false;
    private final static String[] settingtypes = {"validate"};
    private BamfoSettings settings = new BamfoSettings(settingtypes);

    private void printSplitHelp() {
        System.out.println("Bamformatics split: split one bam file into two according to read ids");
        System.out.println();
        System.out.println("General options:");
        System.out.println(" --bam <File>              - input alignment");
        System.out.println(" --ids <File>              - reads ids");
        System.out.println(" --hitsonly                - output only one file with hits");
        System.out.println(" --output <String>         - prefix for output files");
        System.out.println();
        // also print options from the common set
        System.out.println(settings.printHelp());
    }

    private boolean parseSplitParameters(String[] args) {

        OptionParser prs = new OptionParser();
        // core options for multivcf        
        prs.accepts("bam").withRequiredArg().ofType(String.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);
        prs.accepts("ids").withRequiredArg().ofType(String.class);
        prs.accepts("hitsonly");

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

        if (options.has("output")) {
            out = (String) options.valueOf("output");
        } else {
            System.out.println("missing required parameter --output");
            return false;
        }

        if (options.has("hitsonly")) {
            hitsonly = true;
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

        if (options.has("ids")) {
            idfile = (String) options.valueOf("ids");
        } else {
            System.out.println("missing required parameter --ids");
            return false;
        }

        // get the genotyping-style options 
        if (!settings.getOptionValues(options)) {
            return false;
        }

        return true;
    }

    /**
     * reads all the items in the given file and records the items into the
     * hashmap "wantedids"
     *
     * @param idfile
     * @throws IOException
     */
    private void getWantedIds(String idfile) throws IOException {

        BufferedReader br = BufferedReaderMaker.makeBufferedReader(idfile);
        String s;
        while ((s = br.readLine()) != null) {
            if (!wantedids.containsKey(s)) {
                wantedids.put(s, true);
            }
        }
        br.close();

    }

    /**
     * When starting, this assumes variabts inbam, out, wantedids have been
     * filled.
     *
     * Function reads in the input bam. Looks up each read id with the wanted
     * list. Creates two output bam files. The first will contain reads in the
     * wanted list. The second will contain reads not present in the wanted
     * list.
     *
     */
    private void splitBam() {
        // open the alignment file and start processing
        SAMFileReader inputSam = new SAMFileReader(inbam);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());

        File hitsfile = new File(out + "-hits.bam");        
        File missesfile = new File(out + "-misses.bam");

        // open output Sam files for hits and misses. Use same header as for the inputSam
        SAMFileWriter hitsSam;
        SAMFileHeader outheader = inputSam.getFileHeader().clone();
        outheader.addComment("Hits output from Bamformatics split --ids " + idfile);
        hitsSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                outheader, true, hitsfile);

        SAMFileWriter missesSam = null;
        SAMFileHeader outheader2 = inputSam.getFileHeader().clone();
        outheader2.addComment("Misses output from Bamformatics split --ids " + idfile);
        if (!hitsonly) {
            missesSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                    outheader2, true, missesfile);
        }

        // read each record, check if the read name is among those wanted
        // if yes/no, copy the record into separate files
        for (final SAMRecord samRecord : inputSam) {
            String nowid = samRecord.getReadName();
            if (wantedids.containsKey(nowid)) {
                hitsSam.addAlignment(samRecord);
            } else {
                if (!hitsonly) {
                    missesSam.addAlignment(samRecord);
                }
            }
        }

        inputSam.close();
        hitsSam.close();
        if (!hitsonly) {
            missesSam.close();
        }

    }

    /**
     *
     * @param args
     *
     * command line arguments
     *
     */
    public BamfoSplit(String[] args) {
        // parse the input
        if (args == null || args.length == 0) {
            printSplitHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseSplitParameters(args)) {
            return;
        }

        isReady = true;
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

        // find the lengths of all the chromosomes
        try {
            getWantedIds(idfile);
        } catch (Exception ex) {
            System.out.println("Error processing ids: " + ex.getMessage());
            return;
        }

        try {
            splitBam();
        } catch (Exception ex) {
            System.out.println("Error splitting bam: " + ex.getMessage());
        }
    }
}
