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
package bamfo.tracks;

import bamfo.utils.BamfoSettings;
import java.io.File;
import java.io.PrintStream;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 *
 * @author tkonopka
 */
public class BamfoTracks implements Runnable {

    private static final int TRACK_COVERAGE = 0;
    private static final int TRACK_READSTART = 1;
    private static final int TRACK_READEND = 2;
    private static final int TRACK_READSTARTEND = 3;
    private static final int TRACK_MEDMAPQUAL = 4;
    private File bamfile;
    private File outdir;
    private String tracktypeString = "coverage";
    private int tracktype = 0;
    private final static String[] settingtypes = {"minbasequal", "mindepth", "minmapqual",
        "minfromstart", "minfromend", "trim", "trimQB", "NRef","validate"};
    private BamfoSettings settings = new BamfoSettings(settingtypes);
    private boolean isReady = false;

    private void printBamTracksHelp() {
        // print the basic required parameters
        System.out.println("Bamformatics tracks: compute the *genotype-able* tracks of an alignment\n");
        System.out.println("  --bam <File>          - alignment file");
        System.out.println("  --output <File>       - output directory");
        System.out.println("  --type <String>       - type of track (coverage, readS, readE, readSE)");
        System.out.println();

        // also print options for genotyping
        System.out.println(settings.printHelp());
    }

    private boolean parseBamTracksParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // bam - input bam file
        prs.accepts("bam").withRequiredArg().ofType(File.class);
        // output - output directory
        prs.accepts("output").withRequiredArg().ofType(File.class);
        // type - type of track to compute directory
        prs.accepts("type").withRequiredArg().ofType(String.class);

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
            bamfile = (File) options.valueOf("bam");
            if (!bamfile.canRead()) {
                System.out.println("bam file is not readable");
                return false;
            }
        } else {
            System.out.println("missing parameter bam");
            return false;
        }

        if (options.has("type")) {
            tracktypeString = (String) options.valueOf("type");
            if (tracktypeString.equalsIgnoreCase("coverage")) {
                tracktype = TRACK_COVERAGE;
            } else if (tracktypeString.equalsIgnoreCase("readS")) {
                tracktype = TRACK_READSTART;
            } else if (tracktypeString.equalsIgnoreCase("readE")) {
                tracktype = TRACK_READEND;
            } else if (tracktypeString.equalsIgnoreCase("readSE")) {
                tracktype = TRACK_READSTARTEND;
            } else if (tracktypeString.equalsIgnoreCase("medmapqual")) {
                tracktype = TRACK_MEDMAPQUAL;
            } else {
                System.out.println("unrecognized track type " + tracktype);
                return false;
            }
        } else {
            // when type is not specified, the default behavior will be to compute the 
            // coverage tracks
        }

        if (options.has("output")) {
            outdir = (File) options.valueOf("output");
            if (!outdir.exists()) {
                if (outdir.mkdirs()) {
                    // everything is ok
                } else {
                    System.out.println("Could not create output directory ");
                    return false;
                }
            } else {
                System.out.println("Directory already exists. Contents will be overwritten");
            }
        } else {
            System.out.println("missing required parameter output");
            return false;
        }

        // get the genotyping-style options 
        if (!settings.getOptionValues(options)) {
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
    public BamfoTracks(String[] args, PrintStream logstream) {

        if (args == null) {
            printBamTracksHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseBamTracksParameters(args)) {
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

        if (tracktype < TRACK_MEDMAPQUAL) {
            new TracksCoverage(settings, bamfile, outdir, tracktype).run();
        } else if (tracktype == TRACK_MEDMAPQUAL) {
            new TracksMedMapQual(settings, bamfile, outdir).run();
        }

    }
}
