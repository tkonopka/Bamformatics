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

package bamfo.gui.permissions;

import java.io.File;
import java.util.BitSet;

/**
 * Just a container for nine boolean values, coded into a bitset.
 *  
 * This is not used in the GUI, after all.
 * 
 * @author tomasz
 */
public class BamfoPermissions {
   
    private final BitSet permissions = new BitSet(9);
        
    /**
     * Simple constructor that will have all permissions set to false.
     */
    public BamfoPermissions() {
        
    }
    
    /**
     * Constructor that will set permissions based on current settings for file f.
     * The file reference is not stored in the object. 
     * 
     * @param f 
     */
    public BamfoPermissions(File f) {
        
    }
    
    /**
     * applies the current set of permission onto file f.
     * 
     * @param f 
     */
    public void applyPermissions(File f) {
        
    }
    
    /**
     * record permission setting for user to read. Other set functions
     * work similarly.
     * 
     * @param value 
     */
    public void setUR(boolean value) {
        permissions.set(0, value);
    }    
    public void setUW(boolean value) {
        permissions.set(1, value);
    }
    public void setUX(boolean value) {
        permissions.set(2, value);
    }
    public void setGR(boolean value) {
        permissions.set(3, value);
    }    
    public void setGW(boolean value) {
        permissions.set(4, value);
    }
    public void setGX(boolean value) {
        permissions.set(5, value);
    }
    public void setOR(boolean value) {
        permissions.set(6, value);
    }    
    public void setOW(boolean value) {
        permissions.set(7, value);
    }
    public void setOX(boolean value) {
        permissions.set(8, value);
    }
    
    
    /**
     * allow external code to read permission for User.Read. Other get functions
     * return corresponding values for group/other and read/write/execute.
     * 
     * @return 
     */
    public boolean getUR() {
        return permissions.get(0);
    }
    public boolean getUW() {
        return permissions.get(1);
    }
    public boolean getUX() {
        return permissions.get(2);
    }
    public boolean getGR() {
        return permissions.get(3);
    }
    public boolean getGW() {
        return permissions.get(4);
    }
    public boolean getGX() {
        return permissions.get(5);
    }
    public boolean getOR() {
        return permissions.get(6);
    }
    public boolean getOW() {
        return permissions.get(7);
    }
    public boolean getOX() {
        return permissions.get(8);
    }
}
