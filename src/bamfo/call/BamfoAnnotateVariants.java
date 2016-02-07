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
package bamfo.call;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;

/**
 * A tool to fill the variant label column in a vcf file with labels from a
 * database, e.g. dbSNP.
 *
 *
 * @author tkonopka
 */
public class BamfoAnnotateVariants implements Runnable {

    private String vcffile = "stdin";
    private String dbfile = null;
    private String outfile = "stdout";
    private boolean resetquality = true;
    private boolean isReady = false;
    private PrintStream outlog = System.out;
        
    private void printBamAnnotateVariantsHelp() {
        outlog.println("Bamformatics annotatevariants: add/replace dbSNP labels in a vcf file");
        outlog.println();
        outlog.println(" --vcf <File>           - input vcf file");
        outlog.println(" --output <File>        - output vcf file");
        outlog.println(" --database <File>      - vcf file with variant database");
        outlog.println(" --resetquality         - a boolean flag. When set, all qualities fields will be set");
        outlog.println("                          to values from the database");
        outlog.println();
        outlog.println("Note: for this tool to function propertly, the vcf and database files");
        outlog.println("      must be sorted in an equivalent manner by chromosome and position");
        outlog.println();
    }

    private boolean parseBamAnnotateVariantsParameters(String[] args) {

        OptionParser prs = new OptionParser();

        prs.accepts("vcf").withRequiredArg().ofType(String.class);
        prs.accepts("output").withRequiredArg().ofType(String.class);
        prs.accepts("database").withRequiredArg().ofType(String.class);
        prs.accepts("resetquality").withRequiredArg().ofType(String.class);

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            outlog.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        if (options.has("vcf")) {
            vcffile = (String) options.valueOf("vcf");
        } else {
            outlog.println("missing parameter bam");
            return false;
        }

        if (options.has("output")) {
            outfile = (String) options.valueOf("output");
        } else {
            outlog.println("missing required parameter output");
        }

        if (options.has("database")) {
            dbfile = (String) options.valueOf("database");
        } else {
            outlog.println("missing parameter database");
            return false;
        }

        resetquality = options.has("resetquality");

        return true;
    }

    public BamfoAnnotateVariants(String[] args) {
        if (args == null) {
            printBamAnnotateVariantsHelp();
            return;
        }
        // parse the parameters, exit if not successful
        if (!parseBamAnnotateVariantsParameters(args)) {
            return;
        }
        isReady = true;
    }

    /**
     * Constructor that does not parse command line parameters, but requires all paths
     * given as separate arguments.
     * 
     * @param vcfin
     * @param dbfile
     * @param outfile
     * @param resetquality 
     */
    public BamfoAnnotateVariants(String vcfin, String dbfile, String outfile, boolean resetquality, PrintStream outlog) {
        this.vcffile = vcfin;
        this.dbfile = dbfile;
        this.outfile = outfile;
        this.resetquality = resetquality;
        this.outlog = outlog;
        isReady = true;
    }

    /**
     * go from an array of tokens into a single string with tabs in between
     *
     * @param tokens
     * @return
     */
    private String stitchTokens(String[] tokens) {
        if (tokens == null || tokens.length < 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(tokens[0]);
        for (int i = 1; i < tokens.length; i++) {
            sb.append("\t").append(tokens[i]);
        }
        return sb.toString();
    }

    /**
     * from a vcf file, make a hashmap that associates a chromosome name string
     * with an integer showing the order in which it appears in the file.
     *
     * @param vcffile
     * @return
     * @throws IOException
     */
    private HashMap<String, Integer> getChrOrder(String vcffile) throws IOException {

        HashMap<String, Integer> ans = new HashMap<>(128, 0.75f);
        BufferedReader reader = BufferedReaderMaker.makeBufferedReader(vcffile);

        // skip the header rows
        String s = reader.readLine();
        while (s != null && s.startsWith("#")) {
            s = reader.readLine();
        }

        // make sure the first item is inserted
        int chromnumber = 0;
        String oldchr = null;
        if (s != null) {
            String[] tokens = s.split("\t");
            oldchr = tokens[0];
            ans.put(oldchr, chromnumber);
            chromnumber++;
        }

        // go through all the remaining lines
        while ((s = reader.readLine()) != null) {
            String[] tokens = s.split("\t");
            if (!oldchr.equals(tokens[0])) {
                oldchr = tokens[0];
                ans.put(oldchr, chromnumber);
                chromnumber++;
            }
        }

        reader.close();

        return ans;
    }

    /**
     * skips over the database header. copies the vcf header to the
     * outputstream. Upon exit, both readers and set so that the next readLine()
     * gives the first vcf entry.
     *
     * @param vcfreader
     * @param dbreader
     * @param outstream
     * @throws IOException
     */
    private void processVcfHeaders(BufferedReader vcfreader, BufferedReader dbreader, OutputStream outstream) throws IOException {

        // create builders for headers from both files
        StringBuilder vcfbuilder = new StringBuilder(65536);

        ArrayList<String> dbINFOrows = new ArrayList<>(64);

        String dbString = dbreader.readLine();
        while (dbString != null && !dbString.startsWith("#CHROM")) {
            if (dbString.startsWith("##INFO")) {
                dbINFOrows.add(dbString);
            }
            dbString = dbreader.readLine();
        }

        // read the headers from the vcf file        
        // but do not copy the ##INFO rows
        String vcfString = vcfreader.readLine();
        while (vcfString != null && !vcfString.startsWith("#CHROM")) {
            if (!vcfString.startsWith("##INFO")) {
                vcfbuilder.append(vcfString).append("\n");
            }
            vcfString = vcfreader.readLine();
        }

        // append the INFO rows from the database
        for (int i = 0; i < dbINFOrows.size(); i++) {
            vcfbuilder.append(dbINFOrows.get(i)).append("\n");
        }

        // also append the CHROM line to the vcf builder        
        vcfbuilder.append(vcfString).append("\n");

        // output the vcf header to the outputstream
        outstream.write(vcfbuilder.toString().getBytes());

    }

    private void annotateVariants(BufferedReader vcfreader, HashMap<String, Integer> vcfchrorder, BufferedReader dbreader, OutputStream outstream) throws IOException {

        // process the headers, i.e. skip the header lines
        processVcfHeaders(vcfreader, dbreader, outstream);

        // the output will be buffered in a builder before outputing
        StringBuilder vcfbuilder = new StringBuilder(65536);

        // read the first entries from the vcf and database into vcfString and dbString 
        // (this works because processVcfHeaders() exits giving readers in this state)
        String vcfString = vcfreader.readLine();
        String dbString = dbreader.readLine();

        // these will determine whether at each iteration whether to read/process
        // lines from each of the vcf files
        boolean newFromVcf = true, newFromDB = true;

        String[] vcftokens = null;
        String[] dbtokens = null;

        // current positions in each of the db and vcf files
        int vcfposition = 0, dbposition = 0;

        while (vcfString != null && dbString != null) {

            // split the strings into tokens if necessary
            if (newFromVcf) {
                vcftokens = vcfString.split("\t");
                vcfposition = Integer.parseInt(vcftokens[1]);
                newFromVcf = false;
                vcftokens[2] = ".";
                vcftokens[7] = ".";
                if (resetquality) {
                    vcftokens[5] = ".";
                }
            }
            if (newFromDB) {
                dbtokens = dbString.split("\t");
                dbposition = Integer.parseInt(dbtokens[1]);
                newFromDB = false;
            }

            // check if the variants are equivalent
            if (vcftokens[0].equals(dbtokens[0])) {
                // chromosomes match, check positions
                if (vcfposition == dbposition) {
                    // check if the genotype is actually equivalent
                    if (vcftokens[3].equals(dbtokens[3]) && vcftokens[4].equals(dbtokens[4])) {
                        vcftokens[2] = dbtokens[2];
                    } else {
                        // position is in database, but exact bases are not
                        vcftokens[2] = "[" + dbtokens[2] + "]";
                    }

                    // replace the quality and INFO field in the file with that from the database
                    vcftokens[7] = dbtokens[7];
                    if (resetquality) {
                        vcftokens[5] = dbtokens[5];
                    }

                    // advance both along the vcf and database files
                    newFromVcf = true;
                    newFromDB = true;

                } else if (vcfposition < dbposition) {
                    // vcfposition needs to catch up, so advance in the vcf
                    newFromVcf = true;
                } else {
                    // vcfposition>dbposition, so advance in the database
                    newFromDB = true;
                }
            } else {
                // chromosomes do not match, 
                // does it mean to advance along the vcf or db?
                Integer vcfchrindex = vcfchrorder.get(vcftokens[0]);
                Integer dbchrindex = vcfchrorder.get(dbtokens[0]);
                if (dbchrindex == null) {
                    // the chromosome in the DB is not represented at all, so skip those lines in the DB
                    newFromDB = true;
                } else {
                    // skip either along the vcf or along the DB
                    if (dbchrindex < vcfchrindex) {
                        newFromDB = true;
                    } else {
                        newFromVcf = true;
                    }
                }
            }

            // get new lines from one or both of vcf files
            if (newFromVcf) {
                vcfbuilder.append(stitchTokens(vcftokens)).append("\n");
                vcfString = vcfreader.readLine();
            }
            if (newFromDB) {
                dbString = dbreader.readLine();
            }

            // perhaps save portion to disk
            if (vcfbuilder.length() > 6400) {
                outstream.write(vcfbuilder.toString().getBytes());
                vcfbuilder = new StringBuilder(65536);
            }

        }

        // if vcfString is not null, it means the variant is not in the database and the database is finished
        // copy the rest of the variants to the output
        while (vcfString != null) {
            String[] tokens = vcfString.split("\t");
            tokens[2] = ".";
            tokens[7] = ".";
            if (resetquality) {
                tokens[5] = ".";
            }
            vcfbuilder.append(stitchTokens(tokens)).append("\n");

            if (vcfbuilder.length() > 6400) {
                outstream.write(vcfbuilder.toString().getBytes());
                vcfbuilder = new StringBuilder(65536);
            }
            vcfString = vcfreader.readLine();
        }


        // copy the last of the StringBuilder to output
        if (vcfbuilder.length() > 0) {
            outstream.write(vcfbuilder.toString().getBytes());
        }

    }

    @Override
    public void run() {
        if (!isReady) {
            return;
        }

        // get the order of the chromosomes in the vcf
        HashMap<String, Integer> vcfchrorder;
        try {
            vcfchrorder = getChrOrder(vcffile);
        } catch (Exception ex) {
            outlog.println("Error extracting chromsome orders: " + ex.getMessage());
            return;
        }


        // create the input and output streams 
        BufferedReader vcfreader, dbreader;
        OutputStream outstream;
        try {
            // create the readers for the input vcf and the database
            vcfreader = BufferedReaderMaker.makeBufferedReader(vcffile);
            dbreader = BufferedReaderMaker.makeBufferedReader(dbfile);
            outstream = OutputStreamMaker.makeOutputStream(outfile);
        } catch (Exception ex) {
            outlog.println("Error creating input/output streams");
            return;
        }

        try {
            annotateVariants(vcfreader, vcfchrorder, dbreader, outstream);
        } catch (Exception ex) {
            outlog.println("Error annotating variants: " + ex.getMessage());
        }

        // close all the streams
        try {
            vcfreader.close();
            dbreader.close();
            outstream.close();
        } catch (Exception ex) {
            outlog.println("Error closing input/output streams: " + ex.getMessage());
        }
    }
}
