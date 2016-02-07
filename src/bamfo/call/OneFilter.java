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

import bamfo.utils.NumberChecker;
import bamfo.utils.bed.BedRegions;
import java.io.File;
import java.io.IOException;
import jsequtils.variants.VcfEntry;

/**
 * An object describing a type of filtering criteria that should be applied.
 * Class is used within the BamfoVcfFilter utility.
 *
 * @author tkonopka
 */
public class OneFilter {

    // constants for defining relations, e.g. >, >=, etc.    
    public static enum Relation {

        Greater, GreaterOrEqual, Equal, Less, LessOrEqual
    }
    // name of the filter
    private String filtername;
    // configuration for a by-region filter
    private File bedfile = null;
    private String bedfilepath = null;
    private BedRegions regions = null;
    // configuration for a by-key filter
    private String key = null;
    private Relation keyRelation = Relation.Equal;
    private String keyThresholdString = null;
    private Double keyThresholdDouble = null;

    /**
     *
     * @return
     *
     * a new filter that holds the same settings as "this"
     *
     * The "BedRegions" object is not copied.
     *
     */
    public OneFilter copy() {
        OneFilter copy = new OneFilter(this.filtername);
        if (bedfile != null) {
            copy.bedfile = bedfile.getAbsoluteFile();
            copy.bedfilepath = copy.bedfile.getAbsolutePath();
        }
        if (key != null) {
            copy.key = this.key;
        }
        if (keyRelation != null) {
            copy.keyRelation = this.keyRelation;
        }
        if (keyThresholdString != null) {
            copy.keyThresholdString = this.keyThresholdString;
        }
        if (keyThresholdDouble != null) {
            copy.keyThresholdDouble = new Double(this.keyThresholdDouble);
        }
        return copy;
    }

    /**
     * A basic constructor that sets the name of the filter. A filter
     * constructed in this way will always accept when filterVcfEntry is called.
     *
     * @param name
     */
    public OneFilter(String name) {
        this.filtername = name;
    }

    /**
     * A deep-copy constructor. For by-region constructors, this will re-read
     * the bed-file defining the regions.
     *
     * @param filter
     */
    public OneFilter(OneFilter filter) {

        this.filtername = filter.filtername;
        this.bedfilepath = filter.bedfilepath;
        this.key = filter.key;
        this.keyRelation = filter.keyRelation;
        this.keyThresholdString = filter.keyThresholdString;
        if (filter.keyThresholdDouble == null) {
            this.keyThresholdDouble = null;
        } else {
            this.keyThresholdDouble = new Double(filter.keyThresholdDouble.doubleValue());
        }

        if (filter.bedfile != null) {
            this.bedfile = filter.bedfile.getAbsoluteFile();
            try {
                this.regions = new BedRegions(this.bedfile);
            } catch (IOException ex) {
                this.regions = null;
            }
        } else {
            this.bedfile = null;
            this.regions = null;
        }
    }

    /**
     * constructor for a by-regions filter
     *
     * @param bedfile
     */
    public OneFilter(String filtername, File bedfile) throws IOException {
        this.filtername = filtername;
        this.bedfile = bedfile.getAbsoluteFile();
        // load the regions into memory
        this.regions = new BedRegions(bedfile);
        this.bedfilepath = bedfile.getAbsolutePath();
    }

    /**
     * constructor for a by-key filter
     *
     * @param key
     * @param keyRelation
     * @param keyThreshold
     */
    public OneFilter(String filtername, String key, Relation keyRelation, String keyThreshold) {
        this.filtername = filtername;
        this.key = key;
        this.keyRelation = keyRelation;
        this.keyThresholdString = keyThreshold;
        this.keyThresholdDouble = null;
    }

    /**
     * constructor for a filter by key and Double
     *
     * @param key
     * @param keyRelation
     * @param keyThreshold
     */
    public OneFilter(String filtername, String key, Relation keyRelation, double keyThreshold) {
        this.filtername = filtername;
        this.key = key;
        this.keyRelation = keyRelation;
        this.keyThresholdDouble = new Double(keyThreshold);
        this.keyThresholdString = null;
    }

    /**
     * check if the filter is well formed. Strategy is to check for null values.
     * If the variables are set, the filter is valid.
     *
     * @return
     */
    public boolean isValid() {
        if (this.bedfile == null) {
            // a by key filter should have key, relation, and threshold defined
            if (this.key == null || this.keyRelation == null) {
                return false;
            }
            if (this.keyThresholdDouble == null && this.keyThresholdString == null) {
                return false;
            }
        } else {
            // a bed fileter should have a bedregions not null
            if (this.regions == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies the filter to one row in a VCF table. At completion, the variant
     * in the argument will have its "filter" field modified.
     *
     *
     * @param entry
     *
     * Variant to be filtered
     *
     */
    public void filterVcfEntry(VcfEntry entry) {

        // by default, assume the entry passes the filter, 
        // i.e. the filter does not hit the entry
        boolean entryhit = false;

        // perform different actions depending on whether filter is 
        // by region or by key/threshold
        if (bedfile == null) {
            if (key != null) {
                // here use key and threshold

                // find the key and value pair in the entry
                String[] format = entry.getFormat().split(":");
                String[] genotype = entry.getGenotype().split(":");

                // find which of the format items corresponds to the key by which filtering is to be done
                int keyindex = findKeyInArray(format, key);

                // assume by default that the entry passes (i.e. the filter is not a hit)
                // it will pass if the format field does not have the key            
                if (keyindex >= 0) {
                    entryhit = checkEntryForKey(genotype[keyindex]);
                }
            }

        } else {
            // by bed region
            // check if the position is in declared region                        
            entryhit = regions.containsBase0(entry.getChr(), entry.getPosition());
        }

        // update the entry filter field if necessary            
        entry.setFilter(modifyFilterStatus(entry.getFilter(), !entryhit));

    }

    /**
     * Create comment lines for the filter.
     *
     * @return
     *
     * a message with leading ## that is suitable to add onto a header of a vcf
     * file
     *
     */
    public String getFilterHeaderLines() {

        // check for not well-constructed filters.
        if (bedfile == null && key == null) {
            return "";
        }

        // prepare a message for the header
        StringBuilder filtermessage = new StringBuilder(1024);

        // message is different depending on whether filtering by bed or by a key/threshold relation
        if (bedfile == null) {
            filtermessage.append("##FILTER=<ID=").append(filtername).append(",Description=\"Filter by threshold, ").append(key);
            switch (keyRelation) {

                case Greater:
                    filtermessage.append(" >");
                    break;
                case GreaterOrEqual:
                    filtermessage.append(" >=");
                    break;
                case Equal:
                    filtermessage.append(" =");
                    break;
                case Less:
                    filtermessage.append(" <");
                    break;
                case LessOrEqual:
                    filtermessage.append(" <=");
                    break;
                default:
                    filtermessage.append(" ");
            }
            if (keyThresholdDouble == null) {
                filtermessage.append(" ").append(keyThresholdString).append(" (string)");
            } else {
                filtermessage.append(" ").append(keyThresholdDouble).append(" (double)");
            }
            filtermessage.append("\">\n");

        } else {
            filtermessage.append("##FILTER=<ID=").append(filtername).append(",Description=\"Filter by bed file ");
            filtermessage.append(bedfilepath).append("\">\n");
        }

        // write message and the header to the output
        return filtermessage.toString();
    }

    /**
     *
     * @param value
     *
     * a portion of the vcfEntry e.g. from a last column 0|0:128:hello
     *
     * value should be the string "128" or "hello" depending on which key will
     * be compared.
     *
     * @return
     *
     * true or false depending on how the value compares to the keyThreshold.
     *
     */
    private boolean checkEntryForKey(String value) {

        if (keyThresholdDouble == null && keyThresholdString == null) {
            return false;
        }

        if (keyThresholdDouble == null) {
            // compare by strings               
            switch (keyRelation) {
                case Greater:
                    return keyThresholdString.compareTo(value) < 0;
                case GreaterOrEqual:
                    return keyThresholdString.compareTo(value) <= 0;
                case Equal:
                    return keyThresholdString.compareTo(value) == 0;
                case Less:
                    return keyThresholdString.compareTo(value) > 0;
                case LessOrEqual:
                    return keyThresholdString.compareTo(value) >= 0;
                default:
                    return false;
            }

        } else {
            // compare numerically
            double valuedouble;

            if (NumberChecker.isDouble(value)) {
                valuedouble = Double.valueOf(value);
            } else {
                // the string may represent infinity as infinity signs rather than Inf
                if (value.equals("∞")) {
                    valuedouble = Double.POSITIVE_INFINITY;
                } else if (value.equals("-∞")) {
                    valuedouble = Double.NEGATIVE_INFINITY;
                } else {
                    // if here, cannot understand the string as any number
                    // so automatically report the default decision.
                    return false;
                }
            }

            switch (keyRelation) {
                case Greater:
                    return keyThresholdDouble < valuedouble;
                case GreaterOrEqual:
                    return keyThresholdDouble <= valuedouble;
                case Equal:
                    return keyThresholdDouble == valuedouble;
                case Less:
                    return keyThresholdDouble > valuedouble;
                case LessOrEqual:
                    return keyThresholdDouble >= valuedouble;
                default:
                    return false;
            }
        }

    }

    /**
     * identifies the location of a key in an array by simple linear search.
     *
     * @param array
     *
     * an array, typically will be unsorted.
     *
     * @param key
     *
     * the item to look for
     *
     * @return
     *
     * the index in the array where the key is location, or -1 if key is not
     * present.
     *
     */
    private int findKeyInArray(String[] array, String key) {

        for (int i = 0; i < array.length; i++) {
            if (key.equals(array[i])) {
                return i;
            }
        }

        // if got here without return, the key is not in the array
        return -1;
    }

    /**
     * generates a string that can be put into a vcf filter column.
     *
     *
     * @param oldstatus
     *
     * previous status of the filter column
     *
     * @param filterhit
     *
     * if the current filter is passed or not.
     *
     * @return
     *
     * if pass is true, the output is the same as oldstatus or PASS. if pass is
     * false, the output is an update of the oldstatus with the current filter
     * name
     *
     *
     */
    private String modifyFilterStatus(String oldstatus, boolean pass) {
        if (pass) {
            if (oldstatus.equals(".")) {
                return "PASS";
            } else {
                return oldstatus;
            }
        } else {
            if (oldstatus.equals(".") || oldstatus.equals("PASS")) {
                return filtername;
            } else {
                return oldstatus + ";" + filtername;
            }
        }
    }

    public String getFiltername() {
        return filtername;
    }

    public File getBedfile() {
        return bedfile;
    }

    public String getKey() {
        return key;
    }

    public Relation getKeyRelation() {
        return keyRelation;
    }

    public String getKeyThresholdString() {
        return keyThresholdString;
    }

    public Double getKeyThresholdDouble() {
        return keyThresholdDouble;
    }

    public void setBedfile(File bedfile) {
        this.bedfile = bedfile;
        if (bedfile == null) {
            this.bedfilepath = "";
        } else {
            this.bedfilepath = bedfile.getAbsolutePath();
        }
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setKeyRelation(Relation keyRelation) {
        this.keyRelation = keyRelation;
    }

    public void setKeyThresholdString(String keyThresholdString) {
        this.keyThresholdString = keyThresholdString;
    }

    public void setKeyThresholdDouble(Double keyThresholdDouble) {
        this.keyThresholdDouble = keyThresholdDouble;
    }

    public void setFiltername(String filtername) {
        this.filtername = filtername;
    }
}
