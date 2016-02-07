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
package bamfo.gui;

import bamfo.gui.configurations.CallConfiguration;
import bamfo.gui.configurations.CallConfigurationList;
import bamfo.gui.configurations.FilterConfiguration;
import bamfo.gui.configurations.FilterConfigurationList;
import bamfo.gui.filetree.BamfoTreeModel;
import bamfo.gui.filetree.BamfoTreeNode;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;

/**
 * A class that keeps track (load, save, update) of user preferences in the GUI.
 * It is responsible for loading and saving these preferences to disk when the
 * GUI is started/closed.
 *
 *
 * @author tomasz
 */
class BamfoGUISettings {

    // the directory where user preferences will be stored. Must be specified in constructor
    private File userDir;
    // other settings that are set within the GUI, not within the Options dialog
    private final BamfoTreeModel projectsTreeModel;
    //generic store of settings
    private final HashMap<String, Object> settings;
    // defaultsettings will be like settings, but will change only 
    // items inaccessible at runtime with default values
    private final HashMap<String, Object> defaultsettings;
    private final FilterConfigurationList filterConfigurations;
    private final CallConfigurationList callConfigurations;

    /**
     * Constructor for the settings object.
     *
     * Note: the constructor loads bare minimum information from the user
     * directory. To load the rest, call the completeLoad() method
     *
     * @param userDir
     */
    public BamfoGUISettings(File userDir) {
        this.userDir = userDir;

        // initialize blank configurations objects
        projectsTreeModel = new BamfoTreeModel();
        filterConfigurations = new FilterConfigurationList();
        callConfigurations = new CallConfigurationList();

        // load the generic map with value/object pairs
        defaultsettings = makeDefaultSettings();
        HashMap<String, Object> tempmap = loadMap("settings");
        if (tempmap == null) {
            settings = new HashMap<>(8);
        } else {
            settings = tempmap;
        }
    }

    /**
     * Call this method after the constructor!
     */
    public void completeLoad() {
// load the tree model from disk or create a new one.        
        loadProjectsTreeModel();

        // load filter configurations
        loadFilterConfigurations();
        if (settings.containsKey("lastFilterConfig")) {
            filterConfigurations.get((String) settings.get("lastFilterConfig"));
        }

        // load call configurations
        loadCallConfigurations();
        if (settings.containsKey("lastCallConfig")) {
            callConfigurations.get((String) settings.get("lastCallConfig"));
        }
    }

    /**
     * create an object that is guaranteed to hold values for all the options.
     *
     * @return
     */
    private HashMap<String, Object> makeDefaultSettings() {
        HashMap<String, Object> ans = new HashMap<>();
        ans.put("numThreads", new Integer(4));
        ans.put("folderTimer", new Integer(120));
        ans.put("scriptTimer", new Integer(2));
        ans.put("numDataRowsLoad", new Integer(250000));
        ans.put("separateDataScripts", new Integer(0));
        ans.put("omitextensions", "");
        return ans;
    }

    public BamfoTreeModel getProjectsTreeModel() {
        return projectsTreeModel;
    }

    public synchronized int getNumProjects() {
        return projectsTreeModel.getChildCount(projectsTreeModel.getRoot());
    }

    /**
     * This is a hack, needs changing probably.
     *
     * It first removes the project and then adds it.
     *
     * @param dir
     * @return
     */
    public synchronized boolean updateProject(File dir) {
        closeProject(dir);
        addProject(dir);
        return true;
    }

    /**
     * updates all the projects, one by one
     */
    public synchronized void updateAllProjects() {
        BamfoTreeNode root = (BamfoTreeNode) projectsTreeModel.getRoot();
        // first get a list of all the projects
        ArrayList<File> allprojects = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            BamfoTreeNode node = (BamfoTreeNode) root.getChildAt(i);
            allprojects.add(node.getNodeFile().getAbsoluteFile());
        }

        // then force update each item in turn
        for (int i = 0; i < allprojects.size(); i++) {
            this.updateProject(allprojects.get(i));
        }
    }

    /**
     * checks if argument is already declared in the settings.
     *
     * If yes, ignores. If not, the directory is added.
     *
     * @param dir
     *
     * a directory with a project to be monitored.
     *
     * @return
     *
     */
    public synchronized boolean addProject(File dir) {
        if (!dir.canRead() || !dir.isDirectory()) {
            return false;
        }

        // try to insert the directory
        if (!projectsTreeModel.addDirAtRoot(dir, this.getOmitExtensionsArray())) {
            return false;
        }

        saveProjectsTreeModel();
        return true;
    }

    /**
     * Get rid of a directory from the list of watched folders.
     *
     * @param dir
     */
    public synchronized void closeProject(File dir) {

        String dirpath = dir.getAbsolutePath();

        BamfoTreeNode root = (BamfoTreeNode) projectsTreeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            BamfoTreeNode node = (BamfoTreeNode) root.getChildAt(i);
            if (node.getNodeFile().getAbsolutePath().equals(dirpath)) {
                projectsTreeModel.removeNodeFromParent(node);
            }
        }

        saveProjectsTreeModel();
    }

    /**
     *
     * @param f
     * @return
     *
     * the directory that is at the root of the projectsTreeModel for this f
     *
     */
    public synchronized File belongsToProject(File f) {
        BamfoTreeNode root = (BamfoTreeNode) projectsTreeModel.getRoot();
        String fpath = f.getAbsolutePath();
        for (int i = 0; i < root.getChildCount(); i++) {
            BamfoTreeNode node = (BamfoTreeNode) root.getChildAt(i);
            if (fpath.startsWith(node.getNodeFile().getAbsolutePath())) {
                return node.getNodeFile().getAbsoluteFile();
            }
        }
        return null;
    }

    /**
     * scans the selected projects and returns the index matching the argument
     *
     * @param dir
     * @return
     */
    public synchronized void save() {
        // save some objects: the projects tree model and settings
        saveProjectsTreeModel();
        settings.put("lastFilterConfig", filterConfigurations.getLastConfigurationName());
        settings.put("lastCallConfig", callConfigurations.getLastConfigurationName());
        saveMap("settings", settings);
        saveFilterConfigurations();
        saveCallConfigurations();
    }

    private void loadProjectsTreeModel() {

        // if model file does not exist, don't do anything
        File modelfile = new File(userDir, "projectsTreeModel.gz");
        if (!modelfile.exists() || !modelfile.canRead()) {
            return;
        }

        String[] omitExts = this.getOmitExtensionsArray();

        // add the directories stored in the file to the model
        BufferedReader br;
        try {
            br = BufferedReaderMaker.makeBufferedReader(modelfile);
            String s;
            while ((s = br.readLine()) != null) {
                if (s.length() > 0) {
                    projectsTreeModel.addDirAtRoot(new File(s), omitExts);
                }
            }
            br.close();
        } catch (IOException ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void saveProjectsTreeModel() {
        File[] projects = projectsTreeModel.getAllProjects();

        StringBuilder sb = new StringBuilder(1024);
        if (projects != null) {
            for (int i = 0; i < projects.length; i++) {
                sb.append(projects[i].getAbsolutePath()).append("\n");
            }
        }

        File modelfile = new File(userDir, "projectsTreeModel.gz");

        // then output the string into a file
        try {
            OutputStream os = OutputStreamMaker.makeOutputStream(modelfile);
            os.write(sb.toString().getBytes());
            os.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public synchronized File getUserdir() {
        return userDir;
    }

    /**
     * generic method to associate a value to a setting.
     *
     * @param label
     * @param value
     */
    private synchronized void setIntSetting(String label, int value) {
        settings.put(label, new Integer(value));
    }

    /**
     * generic lookup of a setting value, which returns a default if a
     * user-configurable is not found.
     *
     * @param label
     * @return
     */
    private synchronized int getIntSetting(String label) {
        Object temp = settings.get(label);
        if (temp == null) {
            return ((Integer) defaultsettings.get(label)).intValue();
        } else {
            return ((Integer) temp).intValue();
        }
    }

    private synchronized String getStringSetting(String label) {
        Object temp = settings.get(label);
        if (temp == null) {
            return ((String) defaultsettings.get(label));
        } else {
            return (String) temp;
        }
    }

    /**
     * looks up the number of threads allowed for use.
     *
     * @return
     */
    public synchronized int getNumThreads() {
        return getIntSetting("numThreads");
    }

    public synchronized void setNumthreads(int numthreads) {
        setIntSetting("numThreads", numthreads);
    }

    public synchronized int getNumDataRowsLoad() {
        return getIntSetting("numDataRowsLoad");
    }

    public synchronized void setNumDataRowsLoad(int numrows) {
        setIntSetting("numDataRowsLoad", numrows);
    }

    public synchronized int getFolderTimer() {
        return getIntSetting("folderTimer");
    }

    public synchronized void setFolderTimer(int seconds) {
        setIntSetting("folderTimer", seconds);
    }

    public synchronized int getScriptTimer() {
        return getIntSetting("scriptTimer");
    }

    public synchronized void setScriptTimer(int seconds) {
        setIntSetting("scriptTimer", seconds);
    }

    public synchronized int getSeparateDataScripts() {
        return (getIntSetting("separateDataScripts"));
    }

    public synchronized void setSeparateDataScripts(boolean separate) {
        if (separate) {
            setIntSetting("separateDataScripts", 1);
        } else {
            setIntSetting("separateDataScripts", 0);
        }
    }

    /**
     *
     * @return
     *
     * one string that is saved in the settings map
     */
    public synchronized String getOmitExtensions() {
        return (getStringSetting("omitextensions"));
    }

    /**
     *
     * @return
     *
     * an array of extensions from getOmitExtensions()
     *
     */
    public synchronized String[] getOmitExtensionsArray() {
        String ss = getOmitExtensions();
        if (ss.isEmpty()) {
            return null;
        } else {
            // get an array
            String[] ans = ss.split(",");
            // make sure that the array has elements with a "."
            for (int i = 0; i < ans.length; i++) {
                if (!ans[i].startsWith(".")) {
                    ans[i] = "." + ans[i];
                }
            }
            return ans;
        }
    }

    /**
     * records a set of extensions into the settings map. (
     *
     * @param exts
     *
     * will eventually be interpreted as a comma-separated list of extensions
     *
     */
    public synchronized void setOmitExtensions(String exts) {
        // first remove white spaces from exts        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < exts.length(); i++) {
            if (exts.charAt(i) != ' ') {
                sb.append(exts.charAt(i));
            }
        }
        settings.put("omitextensions", sb.toString());
    }

    /**
     * Generic method to get some value associated with a setting. Don't use
     * this.
     *
     * @param s
     * @return
     */
    public Object get(String s) {
        return settings.get(s.toLowerCase());
    }

    /**
     * This is somewhat problematic as it allows BamfoGUI to store whatever
     * without the Settings object being able to
     *
     * @param s
     * @param o
     */
    public void set(String s, Object o) {
        settings.put(s.toLowerCase(), o);
    }

    public synchronized void setUserDir(File userDir) {
        this.userDir = userDir;
    }

    private void loadCallConfigurations() {

        // get all filenames that are call configuration files        
        File[] configfiles = userDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".gz") && name.startsWith("callconfig.");
            }
        });

        // create one configuration that is called "default"        
        callConfigurations.set(new CallConfiguration("default"));
        callConfigurations.get("default");

        // load the configurations one by one
        for (int i = 0; i < configfiles.length; i++) {
            try {
                callConfigurations.set(new CallConfiguration(configfiles[i]));
            } catch (IOException ex) {
                System.out.println("Error loading configuration file " + configfiles[i].getAbsolutePath() + ": " + ex.getMessage());
            }
        }
        callConfigurations.sort();

    }

    private void loadFilterConfigurations() {
        // get all filenames that are filter configuration files        
        File[] configfiles = userDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".gz") && name.startsWith("filterconfig.");
            }
        });

        // load the configurations one by one
        for (int i = 0; i < configfiles.length; i++) {
            try {
                filterConfigurations.set(new FilterConfiguration(configfiles[i]));
            } catch (IOException ex) {
                System.out.println("Error loading file: " + configfiles[i].getAbsolutePath());
            }
        }

    }

    /**
     * saves several filtering configurations onto disk (one file per
     * configuration)
     *
     * @param configs
     */
    private void saveFilterConfigurations() {

        // first delete all currently available configurations
        String[] allfiles = userDir.list();
        for (int i = 0; i < allfiles.length; i++) {
            if (allfiles[i].endsWith(".gz") && allfiles[i].endsWith("filterconfig.")) {
                new File(userDir, allfiles[i]).delete();
            }
        }

        // then recreate necessary files
        for (int i = 0; i < filterConfigurations.size(); i++) {
            File temp = new File(userDir, "filterconfig." + filterConfigurations.getName(i) + ".gz");
            filterConfigurations.get(filterConfigurations.getName(i)).saveConfiguration(temp);
        }
    }

    /**
     * Similar to saveFilterConfigurations.
     *
     * It puts descriptions of configurations into a text file.
     *
     */
    private void saveCallConfigurations() {
        // first delete all currently available configurations
        String[] allfiles = userDir.list();
        for (int i = 0; i < allfiles.length; i++) {
            if (allfiles[i].endsWith(".gz") && allfiles[i].startsWith("callconfig.")) {
                new File(userDir, allfiles[i]).delete();
            }
        }

        // then recreate necessary files
        for (int i = 0; i < callConfigurations.size(); i++) {
            // save all configurations, but do not save the "Default" one
            if (!callConfigurations.getName(i).equals("default")) {
                File temp = new File(userDir, "callconfig." + callConfigurations.getName(i) + ".gz");
                callConfigurations.get(callConfigurations.getName(i)).saveConfiguration(temp);
            }
        }
    }

    /**
     * saves a hashmap into a text file.
     *
     * @param map
     *
     * will be cast to type <String, Object>, i.e. <String, Integer> or <String,
     * Boolean> are allowed
     *
     * @param label
     *
     * a string that will appear in the filename.
     *
     */
    public synchronized void saveMap(String label, HashMap map) {

        HashMap<String, Object> mm = (HashMap<String, Object>) map;
        // get the keys from the map and put them together into a string
        StringBuilder sb = new StringBuilder(1024);
        for (Map.Entry<String, Object> entry : mm.entrySet()) {
            Object nowval = entry.getValue();
            if (nowval == null) {
                sb.append(entry.getKey()).append("\t").append("null\tnull\n");
            } else {
                sb.append(entry.getKey()).append("\t").
                        append(nowval.getClass().getCanonicalName()).append("\t").
                        append(nowval.toString()).append("\n");
            }
        }

        File mapfile = new File(userDir, "map." + label + ".gz");

        // then output the string into a file
        try {
            OutputStream os = OutputStreamMaker.makeOutputStream(mapfile);
            os.write(sb.toString().getBytes());
            os.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * loads a general map (Integer,Double,String,Boolean) from file.
     *
     * @param label
     * @return
     */
    public final synchronized HashMap<String, Object> loadMap(String label) {
        // set up an empty map
        HashMap<String, Object> ans = new HashMap<String, Object>(8);

        // the map with given label should be save in a certain location
        File mapfile = new File(userDir, "map." + label + ".gz");

        // but if it doesn't exist, return an empty map.
        if (!mapfile.exists()) {
            return ans;
        }

        // if it exists, read it!
        try {
            BufferedReader br = BufferedReaderMaker.makeBufferedReader(mapfile);
            String s;
            while ((s = br.readLine()) != null) {
                String[] ssplit = s.split("\t");
                if (ssplit[1].equals("java.lang.Double")) {
                    ans.put(ssplit[0], new Double(Double.parseDouble(ssplit[2])));
                } else if (ssplit[1].equals("java.lang.Integer")) {
                    ans.put(ssplit[0], new Integer(Integer.parseInt(ssplit[2])));
                } else if (ssplit[1].equals("java.lang.Boolean")) {
                    ans.put(ssplit[0], Boolean.parseBoolean(ssplit[2]));
                } else if (ssplit[1].equals("null")) {
                    ans.put(ssplit[0], null);
                } else {
                    // other classes are stored as strings
                    if (ssplit.length > 2) {
                        ans.put(ssplit[0], ssplit[2]);
                    } else {
                        ans.put(ssplit[0], "");
                    }
                }
            }
            br.close();
        } catch (Exception ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ans;
    }

    /**
     * loads a boolean map from file.
     *
     * @param label
     * @return
     */
    public synchronized HashMap<String, Boolean> loadBooleanMap(String label) {
        // set up an empty map
        HashMap<String, Boolean> ans = new HashMap<String, Boolean>(16);

        // the map with given label should be save in a certain location
        File mapfile = new File(userDir, "map." + label + ".gz");

        // but if it doesn't exist, return an empty map.
        if (!mapfile.exists()) {
            return ans;
        }

        // if it exists, read it!
        try {
            BufferedReader br = BufferedReaderMaker.makeBufferedReader(mapfile);
            String s;
            while ((s = br.readLine()) != null) {
                String[] ssplit = s.split("\t");
                if (ssplit[1].equals("java.lang.Boolean")) {
                    ans.put(ssplit[0], Boolean.parseBoolean(ssplit[2]));
                } else {
                    // other classes are skipped in this method                    
                }
            }
            br.close();
        } catch (Exception ex) {
            Logger.getLogger(BamfoGUISettings.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ans;
    }

    public FilterConfigurationList getFilterConfigurationList() {
        return filterConfigurations;
    }

    public CallConfigurationList getCallConfigurationList() {
        return callConfigurations;
    }
}
