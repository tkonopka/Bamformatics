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
 * List that stores configuration
 *
 *
 * @author tomasz
 */
public class CallConfigurationList extends ConfigurationList {

    /**
     * Basic constructor. Does not do anything as the ConfigurationList
     * constructor does everything.
     */
    public CallConfigurationList() {
    }

    /**
     * Reference copy of the list.
     *
     * @param fcl
     */
    public CallConfigurationList(CallConfigurationList ccl) {
        this.configs = ccl.configs;
        this.lastused = ccl.lastused;
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
    @Override
    public CallConfiguration get(String name) {
        Configuration ans = super.get(name);
        if (ans == null) {
            return null;
        } else {
            return (CallConfiguration) ans;
        }
    }
}
