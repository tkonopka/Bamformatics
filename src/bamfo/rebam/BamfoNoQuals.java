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
import bamfo.utils.BamfoRecord;
import bamfo.utils.BamfoSettings;
import java.io.File;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sf.samtools.*;

/**
 * Utility takes a bam file, writes a new bam file with all base qualities reset.
 * 
 * New base qualities will have values either '!' for low quality, '~' for high quality.
 * Qualities with value B will be kept, as they may have special significance.
 * 
 * If all goes well and the thresholds are used consistently, this utility can reduce 
 * the disk space used by an alignment by 40% while retaining all information
 * relevant for variant calling with BamfoVcf
 *
 *
 * @author tkonopka
 */
public class BamfoNoQuals implements Runnable {

    private File inbamfile = null;
    private File outbamfile = null;    
    private final static String[] settingtypes = {"minbasequal","validate"};
    private final BamfoSettings settings = new BamfoSettings(settingtypes);
    // for the Runnable implementation
    private boolean isReady = false;

    private void printBamNoQualsHelp() {
        System.out.println("Bamformatics noquals: tool for removing base qualities from an alignment");
        System.out.println();
        System.out.println(" --bam <File>           - input alignment file");
        System.out.println(" --output <File>        - output bam file");
        System.out.println();
        System.out.println(settings.printHelp());
    }

    private boolean parseBamNoQualsParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // options will be                 
        // bam - input bam file
        prs.accepts("bam").withRequiredArg().ofType(File.class);
        // output - output bam file
        prs.accepts("output").withRequiredArg().ofType(File.class);
        // minbasequal - will determine which bases in reads are kept and which one will be replaced by N
        prs.accepts("minbasequal").withRequiredArg().ofType(String.class);
        prs.accepts("notrimQB");

        // get options for base qualities and trimming
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
            inbamfile = (File) options.valueOf("bam");
            if (!inbamfile.canRead()) {
                System.out.println("bam file is not readable");
                return false;
            }
        } else {
            System.out.println("missing parameter bam");
            return false;
        }

        if (options.has("output")) {
            outbamfile = (File) options.valueOf("output");
        } else {
            System.out.println("missing required parameter output");
            return false;
        }

        // get the options for genotyping
        if (!settings.getOptionValues(options)) {
            return false;
        }

        return true;
    }

    public BamfoNoQuals(String[] args) {

        if (args == null || args.length == 0) {
            printBamNoQualsHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseBamNoQualsParameters(args)) {
            return;
        }

        isReady = true;
    }
    
    private void removeBaseQualities(File inbamfile, File outbamfile) {

        // legacy: get minbasequal and trimming options here
        byte minbasequal = settings.getMinbasequal();        

        // ***************************
        // first prepare the input and output SAM file reader/writer

        // open the input SAM 
        SAMFileReader inputSam = new SAMFileReader(inbamfile);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());        
        
        // open an output SAM. Use the same header as before, but add a comment about base qualities
        SAMFileWriter outputSam;
        SAMFileHeader outheader = inputSam.getFileHeader().clone();
        outheader.addComment("Removed base quality scores using Bamformatics noquals " + (char) minbasequal);
        outputSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                outheader, true, outbamfile);

        for (final SAMRecord samRecord : inputSam) {

            // make sure the bases that will remain in the record will be high quality bases
            byte[] qualities = BamfoRecord.getFullQualities(samRecord);

            int readlen = qualities.length;

            // change the quality string
            for (int i = 0; i < readlen; i++) {
                if (qualities[i] == 'B') {
                    // leave this alone, as may have special meaning
                } else {
                    if (qualities[i] < minbasequal) {
                        // replace low qualities by one very low representative value
                        qualities[i] = '!';
                    } else {
                        // replace high quality by one very high representative value
                        qualities[i] = '~';
                    }
                }
            }

            // replace the quality string
            samRecord.setBaseQualityString(new String(qualities));

            // write the record to the output
            outputSam.addAlignment(samRecord);
        }

        // close the input and outputs for politeness
        inputSam.close();
        outputSam.close();
    }

    /**
     * After the utility is initialized, it has to be "executed" by invoking this method.
     * If initialization failed, this method does not do anything.
     * 
     */
    @Override
    public void run() {
        if (!isReady) {
            return;
        }

        // do the actual processing - remove base quality scores
        try {
            removeBaseQualities(inbamfile, outbamfile);
        } catch (Exception ex) {
            System.out.println("Error removing qualities: " + ex.getMessage());
        }
    }
}
