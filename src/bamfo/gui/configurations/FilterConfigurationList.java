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

/**
 * Class that stores multiple filter configurations in a list. It allows to
 * get/add/remove configurations by their name. It also stores and allows to
 * retrieve the last-used configuration.
 *
 *
 * @author tomasz
 */
public class FilterConfigurationList extends ConfigurationList {

    /**
     * Basic constructor. Does not do anything as everything is initialized
     * properly within the ConfigurationList class.
     * 
     */
    public FilterConfigurationList() {
        // everything is set up already 
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
     *   
     */    
    public FilterConfiguration get(String name) {
        Configuration ans = super.get(name);        
        
        if (ans==null) {
            return null;
        } else {
            return (FilterConfiguration) ans;
        }
        
        
    }
         
    /**
     * Reference copy of the list.
     *
     * @param fcl
     */
    public FilterConfigurationList(FilterConfigurationList fcl) {
        configs = fcl.configs;
        lastused = fcl.lastused;
    }

    public void print() {
        System.out.println("filter config list: " + configs.size());
        for (int i = 0; i < configs.size(); i++) {
            System.out.println(configs.get(i).getName() + " "
                    + ((FilterConfiguration) configs.get(i)).getNumFilters());
        }
    }
}
