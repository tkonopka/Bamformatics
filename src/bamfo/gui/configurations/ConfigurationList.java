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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A class that holds and manipulates a set/array of configurations.
 * The configurations are stored in an array. The array can be sorted by configuration 
 * name, but is not guaranteed/assumed that it always is.
 * 
 * The class also keeps track of the configuration that was last used.
 * 
 * 
 * @author tomasz
 */
abstract class ConfigurationList {

    ArrayList<Configuration> configs = new ArrayList<Configuration>(4);
    String lastused = null;
    ConfigurationComparator cc = new ConfigurationComparator();       
    
    /**
     * checks if the list has a configuration with given name.
     * 
     * @param name
     * 
     * configuration name to search for
     * 
     * @return 
     * 
     * a non-negative integer if the configuration name is present. 
     * -1 if the configuration name is not present in the list.
     * 
     */
    public int indexOf(String name) {
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getName().equals(name)) {         
                return i;
            }
        }
        return -1;
    }
       
    /**
     * similar to indexOf, but returns a boolean value only
     * 
     * @param name
     * @return 
     */
    public boolean has(String name) {
       if (indexOf(name)<0) {
           return false;
       } 
       return true;
    }
    
    public String getLastConfigurationName() {
        return lastused;
    }

    /**
     * retrieve the name of a configuration stored at some index in the array.
     *
     * @param index
     * @return
     */
    public String getName(int index) {
        return configs.get(index).getName();
    }

    /**
     * sorts the array by name of the configuration
     * 
     */
    public void sort() {
        Collections.sort(configs, cc);
    }
    
    /**
     * retrieve a configuration from its name (Currently, this is a linear
     * search). Using this function updates the "lastused" field.
     *
     * @param name
     *
     * @return
     * 
     * the configuration if it is present. null if the name is not present.
     *    *
     */
    public Configuration get(String name) {
        int index = this.indexOf(name);
        if (index<0) {
            lastused = null;
            return null;
        }
        
        lastused = name;
        return configs.get(index);        
    }

    /**
     * This is a combination of add and set for an arraylist.
     *
     * If the configuration name is not present yet, it is added (at the end of the array).
     *
     * If it already exists, then the old configuration is replaced by the new
     * one.
     *
     * Note: at the end, the array need not be sorted.
     * 
     * @param c
     */
    public void set(Configuration c) {
        int addindex = indexOf(c.getName());
        if (addindex < 0) {
            configs.add(c);
        } else {
            configs.set(addindex, c);
        }
        lastused = c.getName();
    }

    /**
     * Get rid of the named configuration from the arraylist. Also, update the 
     * lastused configuration field to avoid referring to a non-existent configuration.
     * 
     * @param name 
     */
    public void remove(String name) {
        int index = indexOf(name);
        if (index < 0) {
            return;
        }
        configs.remove(index);
        if (name.equals(lastused)) {
            lastused = null;
        }
    }

    public int size() {
        return configs.size();
    }
    
    /**
     * Compares configurations just by their name
     */
    class ConfigurationComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            Configuration f1 = (Configuration) o1;
            Configuration f2 = (Configuration) o2;
            return (f1.getName().compareTo(f2.getName()));
        }
    }
}
