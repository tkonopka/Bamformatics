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
package bamfo.call;

import bamfo.call.OneFilter.Relation;
import bamfo.utils.BamfoTool;
import bamfo.utils.NumberChecker;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;
import jsequtils.variants.VcfEntry;

/**
 * A utility to filter VCF files. Supported operations are filtering by
 * inclusion to regions and filtering by key/threshold relations.
 *
 * Regions can be specified via bed files. Key/Threshold relations look at
 * columns 9 and 10 of the VCF table.
 *
 *
 * @author tkonopka
 */
public class BamfoVcfFilter extends BamfoTool implements Runnable {

    private File vcffile;
    private String outvcf = "stdout";
    private final ArrayList<OneFilter> filters;

    private void printBamVcfFilterHelp() {                        
        outputStream.println("bam2x filtervariants: a tool for filtering VCF files");
        outputStream.println();
        outputStream.println("General options:");
        outputStream.println("  --vcf <File>             - input variant call file (VCF)");
        outputStream.println("  --output <File>          - output vcf file");
        outputStream.println("  --filter <String>        - name of applied filter");
        outputStream.println("  --key <String>           - filter by KEY RELATION THRESHOLD");
        outputStream.println("                             e.g. use \"SF>12\" for filtering for strand bias");
        outputStream.println("  --bed <File>             - filter by genomic region");
        outputStream.println();
        outputStream.println("Note: when applying multiple filters in a single command, specify names");
        outputStream.println("      for key/threshold filters before names for by-region filters.");
        outputStream.println();
    }

    private boolean parseBam2xVcfFilterParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // bam - input bam file
        prs.accepts("vcf").withRequiredArg().ofType(File.class);
        // filter - name of the filter
        prs.accepts("filter").withRequiredArg().ofType(String.class);

        // output - output directory
        prs.accepts("output").withRequiredArg().ofType(String.class);
        // bed - will accept a bed file
        prs.accepts("bed").withRequiredArg().ofType(File.class);
        // field - will accept a field name in the 9th column of the FORMAT column
        prs.accepts("key").withRequiredArg().ofType(String.class);

        // verbose - display verbose report by chromosome
        prs.accepts("verbose");

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            outputStream.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        // extract command line paramters

        // must specify at least one of bed or key
        if (!options.has("bed") && !options.has("key")) {
            outputStream.println("Must specify one of --bed or --key");
            return false;
        }

        if (options.has("vcf")) {
            vcffile = (File) options.valueOf("vcf");
            if (!vcffile.canRead()) {
                outputStream.println("vcf file is not readable");
                return false;
            }
        } else {
            outputStream.println("missing parameter vcf");
            return false;
        }

        // if output is not set, will use stdout
        if (options.has("output")) {
            outvcf = (String) options.valueOf("output");
        }

        // from here, start parsing and creating filters that will be applied on the input

        // create array for inputs, otherwise they may not be initialized
        ArrayList<String> filternames;
        ArrayList<File> bedfiles = new ArrayList<>(4);
        ArrayList<String> keycodes = new ArrayList<>(4);

        // if output is not set, will use stdout
        if (options.has("filter")) {
            filternames = new ArrayList<>((List<String>) options.valuesOf("filter"));
        } else {
            outputStream.println("filter name is not specified");
            return false;
        }

        // get all the by-region
        if (options.has("bed")) {
            bedfiles = new ArrayList<>((List<File>) options.valuesOf("bed"));
            // check that all bed files can be read
            for (int i = 0; i < bedfiles.size(); i++) {
                File bedfile = bedfiles.get(i);
                if (!bedfile.canRead()) {
                    outputStream.println("bed file is not readable");
                    return false;
                }
            }
        }

        // get all the key codes
        if (options.has("key")) {
            keycodes = new ArrayList<>((List<String>) options.valuesOf("key"));
        }

        // make sure that filter names and bed/key fields match
        if (filternames.size() != bedfiles.size() + keycodes.size()) {
            outputStream.println("Number of filter names and configurations (--bed and --key) do not match");
            return false;
        }

        // if reached here, try to create all the filters. 
        // the names of the filters are in a single array, key track of position on this array
        int filterindex = 0;

        // Create all threshold filters first
        for (int i = 0; i < keycodes.size(); i++) {
            OneFilter keyFilter = makeKeyThresholdFilter(filternames.get(filterindex), keycodes.get(i));
            if (keyFilter != null) {
                filters.add(keyFilter);
            } else {
                outputStream.println("Could not understand key " + keycodes.get(i));
                return false;
            }
            filterindex++;
        }

        // then create the by-region filters
        for (int i = 0; i < bedfiles.size(); i++) {
            OneFilter bedFilter;
            try {
                bedFilter = new OneFilter(filternames.get(filterindex), bedfiles.get(i));
            } catch (IOException ex) {
                outputStream.println("Could not create bed filter");
                return false;
            }
            filters.add(bedFilter);
            filterindex++;
        }

        // if reached here, filtering is by field value

        return true;
    }

    /**
     * Creates the utility.
     *
     * @param args
     *
     * arguments like those accepted via a command line
     *
     */
    public BamfoVcfFilter(String[] args) {

        filters = new ArrayList<>(8);

        if (args == null) {
            printBamVcfFilterHelp();
            return;
        }

        // parse the parameters, exit if not successful        
        if (!parseBam2xVcfFilterParameters(args)) {
            return;
        }
        bamfolog.setVerbose(true);

        // signal that setup for this task has completed correctly
        isReady = true;
    }

    /**
     * A constructor which accepts ready made filters.
     *
     * @param invcf
     *
     * @param out
     *
     * @param filters
     *
     * @param logstream
     *
     * where messages will be sent.
     *
     *
     */
    public BamfoVcfFilter(File invcf, String out, ArrayList<OneFilter> filters, PrintStream logstream) {
        super(logstream);
        this.vcffile = invcf;
        this.outvcf = out;
        this.filters = filters;
        isReady = true;
    }

    private OneFilter makeKeyThresholdFilter(String filtername, String code) {

        int codelen = code.length();

        // first find the firs character of type ><= 
        //(could perhaps be down with indexOf, byt have three possible relations
        int relationstart = -1;
        for (int i = 0; i < codelen; i++) {
            char nowchar = code.charAt(i);
            if (nowchar == '>' || nowchar == '<' || nowchar == '=') {
                relationstart = i;
                i = codelen;
            }
        }
        if (relationstart < 1) {
            return null;
        }

        // find the start of the threshold
        int thresholdstart = -1;
        if (relationstart + 1 < codelen) {
            char nowchar = code.charAt(relationstart + 1);
            if (nowchar == '=') {
                thresholdstart = relationstart + 2;
            } else {
                thresholdstart = relationstart + 1;
            }
        }
        if (thresholdstart < 1 || (thresholdstart >= codelen)) {
            return null;
        }

        // get the substrings for the fieldname, relation, and threshold
        String key = code.substring(0, relationstart);
        String fieldRelationString = code.substring(relationstart, thresholdstart);
        String keyThresholdString = code.substring(thresholdstart);
        Double keyThresholdDouble = null;

        // perhaps convert the threshold into a number
        if (NumberChecker.isDouble(keyThresholdString)) {
            keyThresholdDouble = Double.valueOf(keyThresholdString);
        }

        // convert the relation string into a number
        Relation keyRelation;
        if (fieldRelationString.equals(">")) {
            keyRelation = Relation.Greater;
        } else if (fieldRelationString.equals(">=")) {
            keyRelation = Relation.GreaterOrEqual;
        } else if (fieldRelationString.equals("=")) {
            keyRelation = Relation.Equal;
        } else if (fieldRelationString.equals("<")) {
            keyRelation = Relation.Less;
        } else if (fieldRelationString.equals("<=")) {
            keyRelation = Relation.LessOrEqual;
        } else {
            return null;
        }

        if (keyThresholdDouble == null) {
            return new OneFilter(filtername, key, keyRelation, keyThresholdString);
        } else {
            return new OneFilter(filtername, key, keyRelation, keyThresholdDouble);
        }
    }

    /**
     * After the utility is initialized, it has to be "executed" by invoking
     * this method. If initialization failed, this method does not do anything.
     *
     */
    @Override
    public void run() {

        // abort if class has not been set up properly
        if (!isReady) {
            return;
        }

        // create the input/output streams
        OutputStream outstream;
        BufferedReader vcfreader;
        try {
            outstream = OutputStreamMaker.makeOutputStream(outvcf);
            vcfreader = BufferedReaderMaker.makeBufferedReader(vcffile);
        } catch (Exception ex) {
            outputStream.println("Error setting up streams");
            return;
        }

        int numfilters = filters.size();

        try {
            // copy the main header
            String header = copyHeader(vcfreader, outstream);
            // create comment lines for each applied filter
            for (int i = 0; i < numfilters; i++) {
                outstream.write(filters.get(i).getFilterHeaderLines().getBytes());
            }
            // copy the #CHROM line
            outstream.write((header + "\n").getBytes());

            // start processing the actual rows: read, filter with all filters, and write to output
            String s;
            while ((s = vcfreader.readLine()) != null && !Thread.currentThread().isInterrupted()) {                
                VcfEntry entry = new VcfEntry(s);
                for (int i = 0; i < numfilters; i++) {
                    filters.get(i).filterVcfEntry(entry);
                }
                // finally, output the entry 
                outstream.write(entry.toString().getBytes());
            }

        } catch (IOException ex) {
            outputStream.println("Error while filtering: " + ex.getMessage());
            Logger.getLogger(BamfoVcfFilter.class.getName()).log(Level.SEVERE, null, ex);
        }

        // close the input/output streams
        try {
            vcfreader.close();
            if (outstream != System.out) {
                outstream.close();
            }

        } catch (IOException ex) {
            outputStream.println("Error finalizing streams: " + ex.getMessage());
            Logger.getLogger(BamfoVcfFilter.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * Starts reading the header of a vcf file. Copies the header lines from the
     * input to the output. The header line is not copies, but it is returned by
     * the function.
     *
     * @param vcfreader
     * @param outstream
     * @return
     *
     * The line from the input file which starts with "#CHROM"
     *
     * @throws IOException
     */
    private String copyHeader(BufferedReader vcfreader, OutputStream outstream) throws IOException {

        String s = null;

        // loop and copy vcf header from file to outputstream
        boolean done = false;
        while (!done) {
            s = vcfreader.readLine();
            if (s == null || s.startsWith("#CHROM")) {
                done = true;
            } else {
                outstream.write((s + "\n").getBytes());
            }
        }

        // return the header line
        return s;
    }
}
