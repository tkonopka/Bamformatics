/*
 * Copyright 2016 Tomasz Konopka.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import net.sf.samtools.*;

/**
 * Utility to split an input file into demultixplexed constituents using a barcode tag. 
 * Note this type of functionality has been available for years elsewhere. The code here
 * is a simple implementation that avoids having to install another software package.
 *
 *
 * @author tkonopka
 */
public class BamfoDemultiplex implements Runnable {

    private File inbam = null;
    private File barcodesFile = null;
    private String out = "stdout";
    private String samplecolumn = "Sample";
    private String codecolumn = "Barcode";
    private boolean isReady = false;
    private final static String[] settingtypes = {"validate"};
    private BamfoSettings settings = new BamfoSettings(settingtypes);

    /**
     * Helper holder class that holds a sample name, barcode, and some handy
     * classes *
     */
    class SampleBarcode {

        String samplename = "";
        String codestring = "";
        byte[] samplecode = null;
        int samplecodelen = 0;

        public SampleBarcode(String samplename, String barcode) {
            this.samplename = samplename;
            this.codestring = barcode;
            this.samplecode = barcode.getBytes();
            this.samplecodelen = this.samplecode.length;
        }

        // count number of mismatches between this intance's barcode and the input
        public int mismatches(byte[] barcode) {
            int barcodelen = barcode.length;
            int mismatches = samplecodelen;
            for (int i = 0; i < Math.min(samplecodelen, barcodelen); i++) {
                if (barcode[i] == samplecode[i]) {
                    mismatches--;
                }
            }
            if (barcodelen < samplecodelen) {
                return samplecodelen - barcodelen;
            }
            return mismatches;
        }
    }

    private void printFindHelp() {
        System.out.println("Bamformatics demultiplex: split a bam file using BC flag");
        System.out.println();
        System.out.println("General options:");
        System.out.println(" --bam <File>              - input alignment");
        System.out.println(" --barcodes <File>         - table with barcodes");
        System.out.println(" --output <File>           - output file prefix");
        System.out.println(" --samplecolumn <String>   - column name containing name of sample");
        System.out.println(" --codecolumn <String>     - column name containing barcode sequence");
        // also print options from the common set
        System.out.println();
        System.out.println(settings.printHelp());
    }

    private boolean parseDemultiplexParameters(String[] args) {

        OptionParser prs = new OptionParser();
        // core options for multivcf        
        prs.accepts("bam").withRequiredArg().ofType(String.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);
        prs.accepts("barcodes").withRequiredArg().ofType(String.class);

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

        if (options.has("barcodes")) {
            barcodesFile = new File((String) options.valueOf("barcodes"));
            if (!barcodesFile.canRead()) {
                System.out.println("Cannot read barcodes file");
                return false;
            }
        } else {
            System.out.println("missing required parameter --barcodes");
            return false;
        }

        // get the genotyping-style options 
        if (!settings.getOptionValues(options)) {
            return false;
        }

        return true;
    }

    /**
     * Scans a table and extracts sample name/barcode information.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private ArrayList<SampleBarcode> getBarcodes() throws FileNotFoundException, IOException {

        // create an object with barcodes
        ArrayList<SampleBarcode> ans = new ArrayList<SampleBarcode>();

        int samplecol = -1;
        int codecol = -1;
        boolean isheader = true;
        String s = "";        
        BufferedReader br = BufferedReaderMaker.makeBufferedReader(barcodesFile);
        while ((s = br.readLine()) != null) {
            String[] ss = s.split("\t");
            if (!s.startsWith("#")) {
                if (isheader) {
                    // interpret ss as the header, 
                    for (int i = 0; i < ss.length; i++) {
                        if (ss[i].equals(samplecolumn)) {
                            samplecol = i;
                        }
                        if (ss[i].equals(codecolumn)) {
                            codecol = i;
                        }
                    }
                    isheader = false;
                } else {
                    // interpret as a data line
                    // extract the columns for sample name and barcode, add to the array
                    ans.add(new SampleBarcode(ss[samplecol], ss[codecol]));
                }
            }
        }
        br.close();

        return ans;
    }

    /**
     * Make sure that all sample name/barcodes declared in "codes" are unique
     *
     * @param codes
     * 
     * an array of SampleBarcodes
     * 
     * @return
     * 
     * an array of strings. When there are no duplicates, the array will be empty. 
     * When there are duplicates, the array will contain strings with duplicates. 
     * Strings will contain either the barcodes or the sample names.
     * 
     */
    private ArrayList<String> checkDuplicates(ArrayList<SampleBarcode> codes) {
        HashSet<String> names = new HashSet<>();
        HashSet<String> seqs = new HashSet<>();
        ArrayList<String> dups = new ArrayList<>();
        for (SampleBarcode nowcode : codes) {
            if (names.contains(nowcode.samplename)) {
                dups.add(nowcode.samplename);
            }
            if (seqs.contains(nowcode.codestring)) {
                dups.add(nowcode.codestring);
            }
            names.add(nowcode.samplename);
            seqs.add(nowcode.codestring);
        }
        return (dups);
    }

    private int getClosestBarcode(String barcode, ArrayList<SampleBarcode> codes) {

        byte[] nowcode = barcode.getBytes();
        int csize = codes.size();
        int hitcode = -1;
        int hitmismatches = Integer.MAX_VALUE;
        int hitcount = 0;

        for (int i = 0; i < csize; i++) {
            int nowmismatches = codes.get(i).mismatches(nowcode);
            if (nowmismatches == 0) {
                return i;
            }
            if (nowmismatches == hitmismatches) {
                hitcount++;
            }
            if (nowmismatches < hitmismatches) {
                hitcode = i;
                hitmismatches = nowmismatches;
                hitcount = 1;
            }
        }

        // if here, there was no perfect match
        if (hitcount == 1) {
            return hitcode;
        } else {
            return -1;
        }

    }

    private void demultiplex(ArrayList<SampleBarcode> codes) throws FileNotFoundException, IOException {

        // create a reader for the input file
        SAMFileReader inputSam = new SAMFileReader(inbam);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());

        // create a set of output files for each barcode
        SAMFileWriter[] outSam = new SAMFileWriter[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            SAMFileHeader outheader = inputSam.getFileHeader().clone();
            String temp = codes.get(i).samplename + "_" + codes.get(i).codestring;
            outheader.addComment("Demultiplexed reads from " + inbam.getCanonicalPath()
                    + "; sample_barcode " + temp);
            File ifile = new File(out + "_" + temp + ".bam");
            outSam[i] = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                    outheader, true, ifile);
        }
        // also make one output Sam with rejected reads
        SAMFileHeader outheader = inputSam.getFileHeader().clone();
        outheader.addComment("Demultiplexed reads from " + inbam.getCanonicalPath()
                + "; unclassified reads");
        File nnfile = new File(out + "_NNNNNN.bam");
        SAMFileWriter nnSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(outheader, true, nnfile);

        // read each record, check if the read name is among those wanted
        // if yes/no, copy the record into separate files        
        for (final SAMRecord record : inputSam) {

            String nowbarcode = record.getStringAttribute("BC");
            int hit = getClosestBarcode(nowbarcode, codes);
            if (hit < 0) {
                nnSam.addAlignment(record);
            } else {
                outSam[hit].addAlignment(record);
            }                       
        }

        // close all output files
        inputSam.close();
        for (int i = 0; i < outSam.length; i++) {
            outSam[i].close();
        }
        nnSam.close();
    }

    /**
     *
     *
     * @param args
     *
     * Command line parameters.
     *
     */
    public BamfoDemultiplex(String[] args) {

        // parse the input
        if (args == null || args.length == 0) {
            printFindHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseDemultiplexParameters(args)) {
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
            // read the barcode definition tables and check duplicates
            ArrayList<SampleBarcode> codes = getBarcodes();
            ArrayList<String> duplicates = checkDuplicates(codes);
            for (int i = 0; i < duplicates.size(); i++) {
                System.out.println("Duplicate sample name or barcode: " + duplicates.get(i));
            }
            if (duplicates.size() > 0) {
                return;
            }
            
            /*
            int minmismatch = 100;
            for (int i = 0; i < codes.size(); i++) {
                SampleBarcode nowcode = codes.get(i);
                for (int j = (i + 1); j < codes.size(); j++) {
                    int temp = nowcode.mismatches(codes.get(j).samplecode);
                    minmismatch = Math.min(temp, minmismatch);
                }
            }
            */

            demultiplex(codes);

        } catch (Exception ex) {
            System.out.println("Error demultiplexing: " + ex.getMessage());
        }
    }
}
