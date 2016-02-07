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
package bamfo.gui.configurations;

import bamfo.call.OneFilter;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;

/**
 * Warning: this only deals with keyThresholdStrings, not with doubles
 *
 *
 * @author tomasz
 */
public final class FilterConfiguration extends Configuration {

    private final ArrayList<OneFilter> filters = new ArrayList<OneFilter>();

    /**
     * Constructor that creates an empty configuration without any filters.
     *
     * @param name
     */
    public FilterConfiguration(String name) {
        setName(name);
        setSuffix(".filtered." + name);
    }

    /**
     * Loads a filter configuration from a file.
     *
     * @param f
     *
     */
    public FilterConfiguration(File f) throws IOException {
        BufferedReader br = BufferedReaderMaker.makeBufferedReader(f);
        String s = br.readLine();
        setName(s);
        s = br.readLine();
        setSuffix(s);
        while ((s = br.readLine()) != null) {            
            OneFilter temp = parseFilterFromStringCode(s);
            if (temp != null) {                
                filters.add(temp);
            }
        }
        br.close();
    }

    private OneFilter parseFilterFromStringCode(String s) {
        String[] tokens = s.split("\t");
        if (tokens.length < 2) {
            return null;
        }

        if (tokens[1].equals("bed")) {
            try {
                return new OneFilter(tokens[0], new File(tokens[2]));
            } catch (IOException ex) {
                return null;
            }
        } else {
            return new OneFilter(tokens[0],
                    tokens[2],
                    OneFilter.Relation.valueOf(tokens[3]),
                    tokens[4]);
        }
    }

    /**
     * Writes a filter configuration into a file
     *
     * @param f
     */
    public void saveConfiguration(File f) {

        // get the textual representation of the filter configuration
        String filterString = this.toString();

        // write it to file
        try {
            OutputStream os = OutputStreamMaker.makeOutputStream(f);
            os.write(filterString.getBytes());
            os.close();
        } catch (Exception ex) {
            System.out.println("Error saving configuration");
        }

    }

    /**
     * 
     * @return 
     * 
     * A string that should describe all aspects of the filter configuration
     * and that should allow recreation of the filter configuration object 
     * 
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();        
        sb.append(getName()).append("\n").append(getSuffix()).append("\n");
        for (int i = 0; i < filters.size(); i++) {            
            if (filters.get(i).isValid() || filters.get(i).getBedfile() != null) {                
                sb.append(getFilterString(filters.get(i)));
            } 
        }
        return sb.toString();
    }

    /**
     * Convert a filter configuration into a string.
     *
     * @param filter
     * @return
     */
    private String getFilterString(OneFilter filter) {

        StringBuilder sb = new StringBuilder(1024);
        sb.append(filter.getFiltername()).append("\t");

        if (filter.getBedfile() == null) {
            sb.append("key\t");
            if (filter.getKey() == null || filter.getKey().isEmpty()) {
                sb.append("NA").append("\t");
            } else {
                sb.append(filter.getKey()).append("\t");
            }
            sb.append(filter.getKeyRelation()).append("\t");
            if (filter.getKeyThresholdString().isEmpty()) {
                sb.append("NA");
            } else {
                sb.append(filter.getKeyThresholdString());
            }
        } else {
            sb.append("bed\t").append(filter.getBedfile().getAbsolutePath());
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Adds a filter into the configuration.
     *
     * Note: the object is assigned, not a defensive copy.
     *
     * @param index
     * @param filter
     *
     */
    public void addFilter(int index, OneFilter filter) {
        filters.add(index, filter);
    }

    /**
     *
     * @return
     *
     * a list of filters belong to this configuration.
     *
     * Note: This is a defensive copy of the private configuration. For bed
     * files, the returned filters will actually contain the bed regions loaded
     * into memory! This is suitable if the filters are actually to be used to
     * process a file, but may not be what is desired when just displaying the
     * properties of the filters in GUI.
     *
     */
    public ArrayList<OneFilter> getFilterList() {
        int numcount = filters.size();
        ArrayList<OneFilter> answer = new ArrayList<OneFilter>(numcount + 1);
        for (int i = 0; i < numcount; i++) {
            OneFilter temp = filters.get(i);
            answer.add(new OneFilter(filters.get(i)));
        }
        return answer;
    }

    public OneFilter getFilterAt(int index) {
        return filters.get(index);
    }

    public int getNumFilters() {
        return filters.size();
    }

    /**
     * clears the filter list entirely.
     *
     */
    public void clear() {
        filters.clear();
    }
}
