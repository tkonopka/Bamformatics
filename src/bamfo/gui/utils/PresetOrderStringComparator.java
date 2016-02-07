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
package bamfo.gui.utils;

import java.util.*;

/**
 *
 * A comparator that can determine ordering of strings using some predefined
 * order. Useful when comparing names of chromosomes. The constructor can take a
 * list of chromosomes names as defined in the genome. Chromosomes names can
 * then be sorted into the same order as in the genome.
 *
 * @author tomasz
 */
public class PresetOrderStringComparator implements Comparator {

    // the preset order of strings will be stored in a hashmap for quick lookup
    private final HashMap<String, Integer> codes;

    /**
     * 
     * @param presets 
     * 
     * names of strings for which ordering should be remembered. 
     * 
     */
    public PresetOrderStringComparator(List<String> presets) {
        // create a hashmap slightly larger than the preset string set
        this.codes = new HashMap<String, Integer>(2 + (int) (presets.size() * 1.5));
        
        // fill the codes array with integer
        Iterator<String> chrit = presets.iterator();
        int index = 0;
        while (chrit.hasNext()) {
            String chrname = chrit.next();
            codes.put(chrname, index);
            index++;
        }
    }

    /**
     * 
     * @param presets 
     * 
     * names of strings for which ordering should be remembered. 
     * 
     */
    public PresetOrderStringComparator(String[] presets) {
        this(Arrays.asList(presets));
    }

    @Override
    public int compare(Object o1, Object o2) {

        // get the Strings and codes
        String s1 = (String) o1;
        String s2 = (String) o2;
        int c1 = Integer.MAX_VALUE, c2 = Integer.MAX_VALUE;
        if (codes.containsKey(s1)) {
            c1 = (int)codes.get(s1);
        }
        if (codes.containsKey(s2)) {
            c2 = (int)codes.get(s2);
        }

        // if codes are not defined in this genome, return lexicographical order
        // otherwise, return preset order
        // (Automatically, strings defined in the constructor appear before
        // strings that have not been defined)
        if (c1 == Integer.MAX_VALUE && c2 == Integer.MAX_VALUE) {
            return s1.compareTo(s2);
        } else {
            return c1 - c2;
        }

    }
}