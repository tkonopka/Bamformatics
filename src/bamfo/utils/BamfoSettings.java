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

import java.util.Arrays;
import java.util.prefs.Preferences;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * This is a class that holds settings
 *
 * @author tkonopka
 */
public class BamfoSettings {

    // some settings used in genotyping
    // the values shown here are values that should result in the 
    // corresponding options being 'turned off'
    private double minscore = -1;
    private double minallelic = -1;
    private int mindepth = -1;
    private int minfromend = -1;
    private int minfromstart = -1;
    private int minmapqual = -1;
    private byte minbasequal = -1;
    private double strandbias = -1;
    private boolean trimpolyedge = false;
    private boolean trimBtail = false;
    // NRef - if true N's in alignment are treated as reference bases
    private boolean NRef = false;
    private String validate = "STRICT";
    // other options for general use.
    // the default reference genome can be set here
    private String genome = null;
    // settingtypes will hold string denoting which options have been declared
    private String[] settingtypes;

    /**
     * a copy constructor. Creates a new settings object with same values as the
     * old object. (A deep copy).
     *
     * @param s
     */
    public BamfoSettings(BamfoSettings s) {
        this.minscore = s.minscore;
        this.minallelic = s.minallelic;
        this.mindepth = s.mindepth;
        this.minfromstart = s.minfromstart;
        this.minfromend = s.minfromend;
        this.minmapqual = s.minmapqual;
        this.minbasequal = s.minbasequal;
        this.strandbias = s.strandbias;
        this.trimpolyedge = s.trimpolyedge;
        this.trimBtail = s.trimBtail;
        this.NRef = s.NRef;
        this.genome = s.genome;
        this.validate = s.validate;
        this.settingtypes = new String[s.settingtypes.length];
        System.arraycopy(s.settingtypes, 0, this.settingtypes, 0, settingtypes.length);
    }

    public double getMinallelic() {
        return minallelic;
    }

    public byte getMinbasequal() {
        return minbasequal;
    }

    public int getMindepth() {
        return mindepth;
    }

    public int getMinfromend() {
        return minfromend;
    }

    public int getMinfromstart() {
        return minfromstart;
    }

    public int getMinmapqual() {
        return minmapqual;
    }

    public double getMinscore() {
        return minscore;
    }

    public double getStrandbias() {
        return strandbias;
    }

    public boolean isTrimBtail() {
        return trimBtail;
    }

    public boolean isTrimpolyedge() {
        return trimpolyedge;
    }

    public boolean isNRef() {
        return NRef;
    }

    public String getGenome() {
        return genome;
    }

    public String getValidate() {
        return validate;
    }

    public void setMinscore(double minscore) {
        this.minscore = minscore;
    }

    public void setMinallelic(double minallelic) {
        this.minallelic = minallelic;
    }

    public void setMindepth(int mindepth) {
        this.mindepth = mindepth;
    }

    public void setMinfromend(int minfromend) {
        this.minfromend = minfromend;
    }

    public void setMinfromstart(int minfromstart) {
        this.minfromstart = minfromstart;
    }

    public void setMinmapqual(int minmapqual) {
        this.minmapqual = minmapqual;
    }

    public void setMinbasequal(byte minbasequal) {
        this.minbasequal = minbasequal;
    }

    public void setStrandbias(double strandbias) {
        this.strandbias = strandbias;
    }

    public void setTrimpolyedge(boolean trimpolyedge) {
        this.trimpolyedge = trimpolyedge;
    }

    public void setNRef(boolean NRef) {
        this.NRef = NRef;
    }

    public void setTrimBtail(boolean trimBtail) {
        this.trimBtail = trimBtail;
    }

    public void setGenome(String genome) {
        this.genome = genome;
    }

    public void setValidate(String validate) {
        this.validate = validate;
    }

    /**
     * constructor without specifying which settings will be available. It
     * creates a a settings object with all possible settings.
     *
     */
    public BamfoSettings() {
        this(new String[]{"minscore", "minallelic", "mindepth",
                    "minfromstart", "minfromend", "minmapqual", "minbasequal",
                    "strandbias", "trim", "trimQB", "NRef", "genome", "validate"});
    }

    /**
     * constructor: needs to specify which settings will be useable within this
     * instantiation.
     *
     * @param settings
     */
    public BamfoSettings(String[] settings) {

        settingtypes = new String[settings.length];
        System.arraycopy(settings, 0, settingtypes, 0, settings.length);
        Arrays.sort(settingtypes);

        // for those items that are declared, extract some default values
        // either from user preferences or from bam2x defaults
        Preferences prefs = BamfoDefaults.getPreferences();
        if (this.has("minscore")) {
            minscore = prefs.getDouble("minscore", BamfoDefaults.DEFAULT_MINSCORE);
        }
        if (this.has("minallelic")) {
            minallelic = prefs.getDouble("minallelic", BamfoDefaults.DEFAULT_MINALLELIC);
        }
        if (this.has("mindepth")) {
            mindepth = prefs.getInt("mindepth", BamfoDefaults.DEFAULT_MINDEPTH);
        }
        if (this.has("minfromstart")) {
            minfromstart = prefs.getInt("minfromstart", BamfoDefaults.DEFAULT_MINFROMSTART);
        }
        if (this.has("minfromend")) {
            minfromend = prefs.getInt("minfromend", BamfoDefaults.DEFAULT_MINFROMEND);
        }
        if (this.has("minmapqual")) {
            minmapqual = prefs.getInt("minmapqual", BamfoDefaults.DEFAULT_MINMAPQUAL);
        }
        if (this.has("minbasequal")) {
            minbasequal = (byte) prefs.get("minbasequal", BamfoDefaults.DEFAULT_MINBASEQUAL).charAt(0);
        }
        if (this.has("strandbias")) {
            strandbias = prefs.getDouble("strandbias", BamfoDefaults.DEFAULT_STRANDBIAS);
        }
        if (this.has("trim")) {
            trimpolyedge = prefs.getBoolean("trim", BamfoDefaults.DEFAULT_TRIMPOLYEDGE);
        }
        if (this.has("trimQB")) {
            trimBtail = prefs.getBoolean("trimQB", BamfoDefaults.DEFAULT_TRIMBTAIL);
        }
        if (this.has("NRef")) {
            NRef = prefs.getBoolean("NRef", BamfoDefaults.DEFAULT_NREF);
        }
        if (this.has("genome")) {
            genome = prefs.get("genome", BamfoDefaults.DEFAULT_GENOME);
        }
        if (this.has("validate")) {
            validate = prefs.get("validate", BamfoDefaults.DEFAULT_VALIDATE);
        }
    }

    /**
     *
     * @param setting
     * @return
     *
     * true if the setting has been declared false otherwise
     */
    private boolean has(String setting) {
        if (Arrays.binarySearch(settingtypes, setting) >= 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * supplements an optionparser with options relating to walking/traversing
     * reads in a bam file in view of genotyping or collecting genotyping
     * information.
     *
     *
     * @param op
     */
    public void addOptionsToOptionParser(OptionParser op) {
        // options for computing coverage at a locus
        if (this.has("minscore")) {
            op.accepts("minscore").withRequiredArg().ofType(Double.class);
        }
        if (this.has("mindepth")) {
            op.accepts("mindepth").withRequiredArg().ofType(Integer.class);
        }
        if (this.has("minallelic")) {
            op.accepts("minallelic").withRequiredArg().ofType(Double.class);
        }
        if (this.has("minfromstart")) {
            op.accepts("minfromstart").withRequiredArg().ofType(Integer.class);
        }
        if (this.has("minfromend")) {
            op.accepts("minfromend").withRequiredArg().ofType(Integer.class);
        }
        if (this.has("minmapqual")) {
            op.accepts("minmapqual").withRequiredArg().ofType(Integer.class);
        }
        if (this.has("minbasequal")) {
            op.accepts("minbasequal").withRequiredArg().ofType(String.class);
        }
        if (this.has("strandbias")) {
            op.accepts("strandbias").withRequiredArg().ofType(Double.class);
        }
        if (this.has("trim")) {
            op.accepts("trim").withRequiredArg().ofType(Boolean.class);
        }
        if (this.has("trimQB")) {
            op.accepts("trimQB").withRequiredArg().ofType(Boolean.class);
        }
        if (this.has("NRef")) {
            op.accepts("NRef").withRequiredArg().ofType(Boolean.class);
        }
        if (this.has("genome")) {
            op.accepts("genome").withRequiredArg().ofType(String.class);
        }
        if (this.has("validate")) {
            op.accepts("validate").withRequiredArg().ofType(String.class);
        }
    }

    /**
     *
     * @return
     *
     * a string showing all the values of the paraters. One value per line.
     * Suitable to print in header of vcf file.
     *
     */
    public String printAllOptions() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("##bamformatics.minscore=").append(minscore).append("\n");
        sb.append("##bamformatics.minallelic=").append(minallelic).append("\n");
        sb.append("##bamformatics.mindepth=").append(mindepth).append("\n");
        sb.append("##bamformatics.minfromstart=").append(minfromstart).append("\n");
        sb.append("##bamformatics.minfromend=").append(minfromend).append("\n");
        sb.append("##bamformatics.minmapqual=").append(minmapqual).append("\n");
        if (minbasequal == 0) {
            sb.append("##bamformatics.minbasequal=NA\n");
        } else {
            sb.append("##bamformatics.minbasequal=").append((char) minbasequal).append("\n");
        }
        sb.append("##bamformatics.strandbias=").append(strandbias).append("\n");
        sb.append("##bamformatics.trim=").append(trimpolyedge).append("\n");
        sb.append("##bamformatics.trimQB=").append(trimBtail).append("\n");
        sb.append("##bamformatics.NRef=").append(NRef).append("\n");
        sb.append("##bamformatics.validate=").append(validate).append("\n");
        if (genome == null) {
            sb.append("##bamformatics.genome=").append("NA").append("\n");
        } else {
            sb.append("##bamformatics.genome=").append(genome).append("\n");
        }
        return sb.toString();
    }

    /**
     *
     * get values for the Settings from a joptsimple OptionSet, i.e. from
     * command line parameters
     *
     * @param os
     * @return
     *
     * true if parsing succeeded. false is an error occurred.
     *
     */
    public boolean getOptionValues(OptionSet os) {

        if (os.has("minmapqual")) {
            try {
                minmapqual = (Integer) os.valueOf("minmapqual");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter minmapqual: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("minbasequal")) {
            try {
                if (((String) os.valueOf("minbasequal")).equals("NA")) {
                    minbasequal = 0;
                } else {
                    minbasequal = ((String) os.valueOf("minbasequal")).getBytes()[0];
                }
            } catch (Exception ex) {
                System.out.println("Error parsing parameter minbasequal: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("trim")) {
            try {
                trimpolyedge = (Boolean) os.valueOf("trim");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter trim: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("trimQB")) {
            try {
                trimBtail = (Boolean) os.valueOf("trimQB");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter trimQB: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("NRef")) {
            try {
                NRef = (Boolean) os.valueOf("NRef");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter NRef: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("minscore")) {
            try {
                minscore = (Double) os.valueOf("minscore");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter minscore: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("mindepth")) {
            try {
                mindepth = (Integer) os.valueOf("mindepth");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter mindepth: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("minfromstart")) {
            try {
                minfromstart = (Integer) os.valueOf("minfromstart");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter minfromstart: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("minfromend")) {
            try {
                minfromend = (Integer) os.valueOf("minfromend");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter minfromend: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("minallelic")) {
            try {
                minallelic = (Double) os.valueOf("minallelic");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter minallelic: " + ex.getMessage());
                return false;
            }
            if (minallelic > 1 || minallelic < 0) {
                System.out.println("Error: minallelic must be in range [0,1]");
            }
        }

        if (os.has("strandbias")) {
            try {
                strandbias = (Double) os.valueOf("strandbias");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter strandbias: " + ex.getMessage());
                return false;
            }
        }

        if (os.has("genome")) {
            try {
                genome = (String) os.valueOf("genome");
            } catch (Exception ex) {
                System.out.println("Error parsing parameter genome: " + ex.getMessage());
                return false;
            }
        }
        if (os.has("validate")) {
            try {
                validate = (String) os.valueOf("validate");
                if (validate.equalsIgnoreCase("STRICT")
                        || validate.equalsIgnoreCase("LENIENT")
                        || validate.equalsIgnoreCase("SILENT")) {
                    validate = validate.toUpperCase();
                } else {
                    System.out.println("Parameter validate must be one of STRICT, LENIENT, or SILENT: ");
                    return false;
                }
            } catch (Exception ex) {
                System.out.println("Error parsing parameter validate: " + ex.getMessage());
                return false;
            }
        }

        return true;
    }

    public String printHelp() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Common options:\n");

        if (this.has("genome")) {
            sb.append("  --genome <String>        - reference genome\n");
        }
        if (this.has("minallelic")) {
            sb.append("  --minallelic <double>    - minimum allelic proportion for variant\n");
        }
        if (this.has("minbasequal")) {
            sb.append("  --minbasequal <byte>     - minimum base quality (set to NA to ignore base qualities)\n");
        }
        if (this.has("mindepth")) {
            sb.append("  --mindepth <int>         - minimum depth for variant call\n");
        }
        if (this.has("minfromend")) {
            sb.append("  --minfromend <int>       - minimum distance from 3prime end\n");
        }
        if (this.has("minfromstart")) {
            sb.append("  --minfromstart <int>     - minimum distance from 5prime end\n");
        }
        if (this.has("minmapqual")) {
            sb.append("  --minmapqual <int>       - minimum mapping quality\n");
        }
        if (this.has("minscore")) {
            sb.append("  --minscore <double>      - minimum quality score threshold\n");
        }
        if (this.has("NRef")) {
            sb.append("  --NRef <boolean>         - count Ns in read sequence as reference bases\n");
        }
        if (this.has("strandbias")) {
            sb.append("  --strandbias <double>    - minimum strand bias probability (fisher test)\n");
        }
        if (this.has("trim")) {
            sb.append("  --trim <boolean>         - trim leading/trailing homopolymers\n");
        }
        if (this.has("trimQB")) {
            sb.append("  --trimQB <boolean>       - trim leading/trailing quality B tails\n");
        }
        if (this.has("validate")) {
            sb.append("  --validate <String>      - validation stringency, STRICT, LENIENT or SILENT\n");
        }
        return sb.toString();
    }
}
