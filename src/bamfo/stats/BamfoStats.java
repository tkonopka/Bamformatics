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

import bamfo.utils.BamfoRecord;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.FileExtensionGetter;
import jsequtils.file.OutputStreamMaker;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;

/**
 * Tool for collecting miscellaneous statistics about a bam file, e.g. read lengths, 
 * insert sizes, mappinq qualities.
 *
 * @author tkonopka
 */
public class BamfoStats implements Runnable {

    private File inbam = null;
    private String out = "stdout";
    private HashMap<Integer, Long> insertlengths = new HashMap<Integer, Long>(1024);
    private HashMap<Integer, Long> readlengths = new HashMap<Integer, Long>(1024);
    private LinkedHashMap<String, Long> chrs = new LinkedHashMap<String, Long>(256);
    private HashMap<Integer, Long> mapquals = new HashMap<Integer, Long>(512);
    final private static int duplicatequal = -3;
    final private static int notprimaryqual = -2;
    final private static int notmappedqual = -1;
    private long[] basequals = new long[256];
    private IntPairComparator ipc = new IntPairComparator();
    private boolean isReady = false;

    /**
     * Simple class to store pairs of integers. See related comparator below.
     */
    private class LongPair {

        long long1, long2;

        public LongPair(long a, long b) {
            this.long1 = a;
            this.long2 = b;
        }
    }

    /**
     * Simple comparator for a pair of integers. Only looks at first integer in
     * comparison.
     */
    class IntPairComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            long longA = ((LongPair) o1).long1;
            long longB = ((LongPair) o2).long1;
            if (longA < longB) {
                return -1;
            } else if (longA > longB) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    private static void printStatsHelp() {
        System.out.println("Bamformatics stats: report various summary stats about a bam file");
        System.out.println();
        System.out.println("General options:");
        System.out.println(" --bam <file>              - input alignment");
        System.out.println(" --output <String>         - output file");
        System.out.println();

    }

    private boolean parseStatsParameters(String[] args) {

        OptionParser prs = new OptionParser();
        prs.accepts("bam").withRequiredArg().ofType(String.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);

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
            System.out.println("missing required parameter output");
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

        return true;
    }

    /**
     * 
     * @param args 
     * 
     * command line arguments.
     * 
     */
    public BamfoStats(String[] args) {

        if (args == null || args.length == 0) {
            printStatsHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseStatsParameters(args)) {
            return;
        }

        isReady = true;
        
    }

    private void computeBamStats(File inbam) {
        SAMFileReader inputSam = new SAMFileReader(inbam);

        // insert the * chromosome into the chrs hashmap
        chrs.put("*", (long) 0);

        // read each record, check if the read name is among those wanted
        // if yes/no, copy the record into separate files
        for (final SAMRecord samRecord : inputSam) {

            //if (!samRecord.getNotPrimaryAlignmentFlag()) {
            Long counter;

            // count the chromosomes
            String nowchrom = samRecord.getReferenceName();
            counter = chrs.get(nowchrom);
            if (counter == null) {
                chrs.put(nowchrom, (long) 1);
            } else {
                chrs.put(nowchrom, counter + 1);
            }

            // count the insert lengths
            int nowinsertlen = samRecord.getInferredInsertSize();
            counter = insertlengths.get(nowinsertlen);
            if (counter == null) {
                insertlengths.put(nowinsertlen, (long) 1);
            } else {
                insertlengths.put(nowinsertlen, counter + 1);
            }

            // count the insert lengths
            int nowreadlen = samRecord.getReadLength();
            counter = readlengths.get(nowreadlen);
            if (counter == null) {
                readlengths.put(nowreadlen, (long) 1);
            } else {
                readlengths.put(nowreadlen, counter + 1);
            }

            // count the mapping qualities
            int nowmapqual = samRecord.getMappingQuality();
            if (samRecord.getReadUnmappedFlag()) {
                nowmapqual = notmappedqual;
            } else if (samRecord.getNotPrimaryAlignmentFlag()) {
                nowmapqual = notprimaryqual;
            } 
            if (samRecord.getDuplicateReadFlag()) {
                nowmapqual = duplicatequal;
            }
            counter = mapquals.get(nowmapqual);
            if (counter == null) {
                mapquals.put(nowmapqual, (long) 1);
            } else {
                mapquals.put(nowmapqual, counter + 1);
            }

            // count the base qualities
            byte[] nowbasequals = BamfoRecord.getFullQualities(samRecord);
            int bql = nowbasequals.length;
            for (int i = 0; i < bql; i++) {
                int nowbasequal = nowbasequals[i];
                basequals[nowbasequal]++;
            }
        }

        inputSam.close();
    }

    private void printBamStats(String out) throws IOException {

        // separate the string out into a filename and extension

        String outextension = FileExtensionGetter.getExtension(new File(out));
        String outbase;
        if (outextension == null) {
            outbase = out;
            outextension = ".tsv";
        } else {
            outbase = out.substring(0, out.length() - outextension.length() - 1);
            outextension = "." + outextension;
        }

        // print out the various statistics into various files
        printIntegerStats(insertlengths, "insertsize", outbase + "-insertsizes" + outextension);
        printIntegerStats(readlengths, "readlength", outbase + "-readlengths" + outextension);
        printIntegerStats(mapquals, "mappingquality", outbase + "-mappingqualities" + outextension);
        printCharacterStats(basequals, "basequality", outbase + "-basequalities" + outextension);
        printStringStats(chrs, "chr", outbase + "-chromosomes" + outextension);

    }

    /**
     * Dumps contents of the hashmap into a file. (Output is sorted by the first
     * integer)
     *
     * @param datamap
     * @param out
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void printIntegerStats(HashMap<Integer, Long> datamap, String label, String out) throws FileNotFoundException, IOException {
        OutputStream outstream = OutputStreamMaker.makeOutputStream(out);
        StringBuilder sb;

        // *************** Print the table of insert size lengths ***************
        sb = new StringBuilder(1024);
        sb.append(label).append("\tcount\n");
        // extract lengths from the hashmap
        ArrayList<LongPair> lencounts = new ArrayList<LongPair>(256);
        for (Map.Entry<Integer, Long> entry : datamap.entrySet()) {
            long len = (long) entry.getKey();
            long count = entry.getValue();
            lencounts.add(new LongPair(len, count));
        }
        // sort the lengths and output
        Collections.sort(lencounts, ipc);
        for (int i = 0; i < lencounts.size(); i++) {
            sb.append(lencounts.get(i).long1).append("\t").append(lencounts.get(i).long2).append("\n");
        }
        outstream.write(sb.toString().getBytes());
        outstream.close();
    }

    private void printIntegerStats(long[] dataarray, String label, String out) throws FileNotFoundException, IOException {
        OutputStream outstream = OutputStreamMaker.makeOutputStream(out);
        StringBuilder sb;

        sb = new StringBuilder(1024);
        sb.append(label).append("\tcount\n");
        for (int i = 0; i < dataarray.length; i++) {
            sb.append(i).append("\t").append(dataarray[i]).append("\n");
        }
        outstream.write(sb.toString().getBytes());

        outstream.close();

    }

    /**
     * Dump contents of the hashmap into a file. The first integer in the
     * hashmap is written as a character (good for base qualities)
     *
     * @param datamap
     * @param out
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void printCharacterStats(long[] dataarray, String label, String out) throws FileNotFoundException, IOException {
        OutputStream outstream = OutputStreamMaker.makeOutputStream(out);
        StringBuilder sb;

        sb = new StringBuilder(1024);
        sb.append(label).append("\tcount\n");
        for (int i = 33; i < 127; i++) {
            if ((char) i == '"' || (char) i == '\\') {
                sb.append("\"\\").append((char) i);
            } else {
                sb.append("\"").append((char) i);
            }
            sb.append("\"\t").append(dataarray[i]).append("\n");
        }
        outstream.write(sb.toString().getBytes());

        outstream.close();
    }

    private void printStringStats(HashMap<String, Long> datamap, String label, String out) throws FileNotFoundException, IOException {
        OutputStream outstream = OutputStreamMaker.makeOutputStream(out);
        StringBuilder sb;

        // *************** Print the table of insert size lengths ***************
        sb = new StringBuilder(1024);
        sb.append(label).append("\tcount\n");
        // extract lengths from the hashmap        
        for (Map.Entry<String, Long> entry : datamap.entrySet()) {
            sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
        outstream.write(sb.toString().getBytes());

        outstream.close();

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
                
        // compute the statistics        
        try {
            computeBamStats(inbam);
        } catch (Exception ex) {
            System.out.println("Error computing statistics: " + ex.getMessage());
        }

        try {
            printBamStats(out);
        } catch (Exception ex) {
            System.out.println("Error outputing statistics: " + ex.getMessage());
        }
    }
}
