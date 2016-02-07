/*
 * Copyright 2014 Tomasz Konopka.
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

import bamfo.call.LocusSNVDataList;
import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoRecord;
import bamfo.utils.BamfoSettings;
import bamfo.utils.BamfoTool;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.sequence.FastaReader;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMRecord;

/**
 * Tool scans a bam file and soft clips reads which do not match the reference
 * genome.
 * 
 * Started, but this tools actually does not do anything at the moment.
 *
 * @author Tomasz Konopka
 */
public class BamfoSoftClip extends BamfoTool implements Runnable {

    // input/output
    private File bamfile = null;
    private File outbamfile = null;
    private boolean verbose = false;
    // to get some parameters from 
    private final static String[] settingtypes = {"minfromstart", "minfromend",
        "genome", "validate"};
    private final BamfoSettings settings = new BamfoSettings(settingtypes);

    private void printBamfoSoftClipHelp() {
        outputStream.println("Bamformatics softclip: a tool for soft-clipping read ends in an alignment");
        outputStream.println();
        outputStream.println("General options:");
        outputStream.println("  --bam <File>             - input alignment file (sorted at least by chromosome)");
        outputStream.println("  --output <File>          - output alignment file");
        outputStream.println("  --verbose                - print progress information");
        outputStream.println();
        outputStream.println(settings.printHelp());
        outputStream.println("Warning: output alignment is not guaranteed to be sorted");
    }

    private boolean parseBamfoSoftClipParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // bam - input bam file
        prs.accepts("bam").withRequiredArg().ofType(File.class);
        // output - output directory
        prs.accepts("output").withRequiredArg().ofType(String.class);
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
            outbamfile = (File) options.valueOf("output");
        } else {
            outputStream.println("missing parameter output");
            return false;
        }

        // get the options for variant calling
        if (!settings.getOptionValues(options)) {
            return false;
        }

        return true;
    }

    /**
     *
     * Prepares to clip reads in an alignment file. The constructor initializes
     * the class. The work proper is done when the tool is run().
     *
     * @param args
     *
     * options as passed on a command line, e.g. --bam mybam.bam
     *
     */
    public BamfoSoftClip(String[] args, PrintStream logstream) {
        super(logstream);

        if (args == null) {
            printBamfoSoftClipHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseBamfoSoftClipParameters(args)) {
            return;
        }
        bamfolog.setVerbose(verbose);

        isReady = true;
    }

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

        try {
            softClipBam(bamfile, genomereader, outbamfile);
        } catch (Exception ex) {
            outputStream.println("Error during genotyping: " + ex.getMessage() + "\n");
        }

        genomereader.close();
    }

    private void softClipBam(File bamfile, FastaReader genomereader, File outbamfile) throws IOException {
        
        // open SAM for start computing
        SAMFileReader inputSam = new SAMFileReader(bamfile);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());
        SAMFileHeader samHeader = inputSam.getFileHeader();

        // open output SAM. Use the same header as before, but add a comment about clipping
        SAMFileWriter outputSam;
        SAMFileHeader outheader = inputSam.getFileHeader().clone();
        outheader.addComment("Soft-clipped bases using Bamformatics softclip " + settings.getMinfromstart() + " " + settings.getMinfromend());
        outputSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                outheader, true, outbamfile);

        // keep track of loaded chromosome
        int nowRef = -1;

        // loop through the alignment, keep track of the chromosomes
        for (final SAMRecord samRecord : inputSam) {

            // check that the record is aligned or that chromosome is not changed
            // from last record
            int recordReference = samRecord.getReferenceIndex();
            if (recordReference > -1) {

                if (recordReference != nowRef) {
                    if (Thread.currentThread().isInterrupted()) {
                        bamfolog.log("Bamformatics interrupted");
                        return;
                    }

                    // get information about the new chromosome
                    nowRef = recordReference;
                    String nowRefName = samRecord.getReferenceName();
                    int nowRefLen = (samHeader.getSequence(nowRef)).getSequenceLength();

                    // get chromosome sequence from the genome reader (perhaps skip over missing chromosomes)
                    genomereader.readNext();
                    System.gc();
                    while (genomereader.hasThis() && !nowRefName.equals(genomereader.getChromosomeName())) {
                        if (Thread.currentThread().isInterrupted()) {
                            bamfolog.log("Bamformatics interrupted");
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

                    bamfolog.log("Clipping on " + nowRefName);
                }

                // clip and output modified record
                SAMRecord clippedRecord = softClipRecord(samRecord, genomereader, samHeader);
                outputSam.addAlignment(clippedRecord);
            } else {
                // unaligned reads always go unmodified to output
                outputSam.addAlignment(samRecord);
            }
        }

        inputSam.close();
        outputSam.close();
    }

    private SAMRecord softClipRecord(SAMRecord record, FastaReader genomereader, 
            SAMFileHeader samHeader) {

        SAMRecord newrecord = new SAMRecord(samHeader);
        return record;
    }
}
