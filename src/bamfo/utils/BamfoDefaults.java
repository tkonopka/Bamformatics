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
package bamfo.utils;

import bamfo.Bamformatics;
import java.io.File;
import java.util.prefs.Preferences;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Subprogram that allows the user to set/reset/see defaults values for the
 * calling parameters.
 *
 * @author tkonopka
 */
public class BamfoDefaults implements Runnable {

    // bam2x default values for all settings ('factory defaults\)
    final static double DEFAULT_MINSCORE = 18;
    final static double DEFAULT_MINALLELIC = 0.05;
    final static int DEFAULT_MINDEPTH = 3;
    final static int DEFAULT_MINFROMEND = 5;
    final static int DEFAULT_MINFROMSTART = 5;
    final static int DEFAULT_MINMAPQUAL = 7;
    final static String DEFAULT_MINBASEQUAL = "*";
    final static double DEFAULT_STRANDBIAS = 0.005;
    final static boolean DEFAULT_TRIMPOLYEDGE = true;
    final static boolean DEFAULT_TRIMBTAIL = true;
    final static boolean DEFAULT_NREF = false;
    final static String DEFAULT_GENOME = "NA";
    final static String DEFAULT_VALIDATE = "STRICT";
    // make sure to create a genotypesettings object with all options
    final static String[] settingtypes = {"minbasequal", "minmapqual",
        "minscore", "minallelic", "mindepth", "minfromstart", "minfromend",
        "strandbias", "trim", "trimQB", "NRef", "genome", "validate"};
    private BamfoSettings genotypesettings = new BamfoSettings(settingtypes);
    private Preferences prefs = getPreferences();
    // some options obtained from the command line
    private boolean show = false;
    private boolean reset = false;
    private String genomefile = prefs.get("genome", DEFAULT_GENOME);

    private void printDefaultsHelp() {
        System.out.println("bam2x defaults: set default values for calling parameters");
        System.out.println();
        System.out.println("General options:");
        System.out.println("  --reset                  - reset all values to bam2x defaults");
        System.out.println("  --show                   - print all current default values");
        System.out.println();
        System.out.println(genotypesettings.printHelp());

    }

    private boolean parseDefaultsParameters(String[] args) {

        OptionParser prs = new OptionParser();

        // two options to show and reset the default parameters
        prs.accepts("reset");
        prs.accepts("show");

        // record default genome
        prs.accepts("genome").withRequiredArg().ofType(String.class);

        // some options for genotyping
        genotypesettings.addOptionsToOptionParser(prs);

        // now use OptionSet to parse the command line
        OptionSet options;
        try {
            options = prs.parse(args);
        } catch (Exception ex) {
            System.out.println("Error parsing command line parameters\n" + ex.getMessage());
            return false;
        }

        if (options.has("show")) {
            show = true;
            return true;
        }

        // if output is not set, will use stdout
        if (options.has("reset")) {
            reset = true;
            return true;
        }


        // get the options for genotyping
        if (!genotypesettings.getOptionValues(options)) {
            return false;
        }

        // read options that 
        if (options.has("genome")) {
            try {
                genomefile = (String) options.valueOf("genome");
            } catch (Exception ex) {
                System.out.println("Error parsing option genome: " + ex.getMessage());
                return false;
            }
        } else {
            // if not present, load from default
            genomefile = prefs.get("genome", DEFAULT_GENOME);
        }

        return true;
    }

    public static Preferences getPreferences() {
        return Preferences.userNodeForPackage(Bamformatics.class);
    }

    private void resetSettings() {
        // set defaults for the parameters involved in calling
        prefs.put("minbasequal", DEFAULT_MINBASEQUAL);
        prefs.putBoolean("trim", DEFAULT_TRIMPOLYEDGE);
        prefs.putBoolean("trimQB", DEFAULT_TRIMBTAIL);
        prefs.putBoolean("NRef", DEFAULT_NREF);
        prefs.putDouble("minscore", DEFAULT_MINSCORE);
        prefs.putDouble("minallelic", DEFAULT_MINALLELIC);
        prefs.putDouble("strandbias", DEFAULT_STRANDBIAS);
        prefs.putInt("mindepth", DEFAULT_MINDEPTH);
        prefs.putInt("minfromstart", DEFAULT_MINFROMSTART);
        prefs.putInt("minfromend", DEFAULT_MINFROMEND);
        prefs.putInt("minmapqual", DEFAULT_MINMAPQUAL);

        // defaults for other parameters
        prefs.put("genome", DEFAULT_GENOME);
        prefs.put("validate", DEFAULT_VALIDATE);
    }

    /**
     * Here the values of the options from the command line are saved into user
     * preferences.
     *
     */
    private void setDefaults() {

        // set calling options
        if (genotypesettings.getMinallelic() != prefs.getDouble("minallelic", DEFAULT_MINALLELIC)) {
            prefs.putDouble("minallelic", genotypesettings.getMinallelic());
        }
        if (genotypesettings.getMinscore() != prefs.getDouble("minscore", DEFAULT_MINSCORE)) {
            prefs.putDouble("minscore", genotypesettings.getMinscore());
        }
        if (genotypesettings.getStrandbias() != prefs.getDouble("strandbias", DEFAULT_STRANDBIAS)) {
            prefs.putDouble("strandbias", genotypesettings.getStrandbias());
        }
        if (genotypesettings.getMinfromstart() != prefs.getInt("minfromstart", DEFAULT_MINFROMSTART)) {
            prefs.putInt("minfromstart", genotypesettings.getMinfromstart());
        }
        if (genotypesettings.getMinfromend() != prefs.getInt("minfromend", DEFAULT_MINFROMEND)) {
            prefs.putInt("minfromend", genotypesettings.getMinfromend());
        }
        if (genotypesettings.getMindepth() != prefs.getInt("mindepth", DEFAULT_MINDEPTH)) {
            prefs.putInt("mindepth", genotypesettings.getMindepth());
        }
        if (genotypesettings.getMinmapqual() != prefs.getInt("minmapqual", DEFAULT_MINMAPQUAL)) {
            prefs.putInt("minmapqual", genotypesettings.getMinmapqual());
        }
        if (genotypesettings.isTrimpolyedge() != prefs.getBoolean("trim", DEFAULT_TRIMPOLYEDGE)) {
            prefs.putBoolean("trim", genotypesettings.isTrimpolyedge());
        }
        if (genotypesettings.isTrimBtail() != prefs.getBoolean("trimQB", DEFAULT_TRIMBTAIL)) {
            prefs.putBoolean("trimQB", genotypesettings.isTrimBtail());
        }
        if (genotypesettings.isNRef() != prefs.getBoolean("NRef", DEFAULT_NREF)) {
            prefs.putBoolean("NRef", genotypesettings.isNRef());
        }
        if (!genotypesettings.getValidate().equalsIgnoreCase(prefs.get("validate", DEFAULT_VALIDATE))) {
            prefs.put("validate", genotypesettings.getValidate());
        }


        // for the byte it's a bit more complex
        byte bq = genotypesettings.getMinbasequal();
        byte[] bqa = new byte[1];
        bqa[0] = bq;
        String bqs = new String(bqa);
        if (!bqs.equals(prefs.get("minbasequal", DEFAULT_MINBASEQUAL))) {
            prefs.put("minbasequal", bqs);
        }

        // perhaps set the default genome file
        if (!genomefile.equals(DEFAULT_GENOME)) {
            File genomefile2 = new File(genomefile);
            if (genomefile2.canRead()) {
                genomefile = genomefile2.getAbsolutePath();
                prefs.put("genome", genomefile);
            } else {
                System.out.println("Cannot read specified genome file");
            }
        }

    }

    /**
     *
     * @param args
     */
    public BamfoDefaults(String[] args) {

        // parse the input
        if (args == null || args.length == 0) {
            printDefaultsHelp();
            return;
        }

        // parse the parameters, exit if not successful
        if (!parseDefaultsParameters(args)) {
            return;
        }


        if (reset) {
            resetSettings();
        }

        if (show) {
            printAllDefaults();
        }

        if (reset || show) {
            return;
        }

        // if reached here, use the remaining options to set defaults
        setDefaults();

    }

    /**
     * prints all the default settings in a common format
     * ##bamformatics.option=value
     */
    private void printAllDefaults() {
        System.out.print(genotypesettings.printAllOptions());
    }

    /**
     * Running this does not do anything. All work is in the constructor. (Maybe
     * this tool should not be a runnable)
     */
    @Override
    public void run() {
        // running this utility doesn't do anything. All is done in constructor.
    }
}
