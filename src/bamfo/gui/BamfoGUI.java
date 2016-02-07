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

import bamfo.Bamformatics;
import bamfo.gui.configurations.CallDialog;
import bamfo.gui.configurations.FilterDialog;
import bamfo.gui.decorations.DecoratedImageIcon;
import bamfo.gui.filetree.BamfoTreeModel;
import bamfo.gui.filetree.BamfoTreeNode;
import bamfo.gui.filetree.BamfoTreePanel;
import bamfo.gui.scripts.ScriptDialog;
import bamfo.gui.utils.BamfoIconMaker;
import bamfo.gui.utils.BamfoMiscFile;
import bamfo.gui.utils.BamfoRunnableTask;
import bamfo.gui.viewers.BamfoScratchViewer;
import bamfo.gui.viewers.BamfoTabComponent;
import bamfo.gui.viewers.BamfoTableViewer;
import bamfo.gui.viewers.BamfoTaskViewer;
import bamfo.gui.viewers.BamfoViewer;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import jsequtils.file.FileExtensionGetter;
import org.apache.commons.io.FileUtils;

/**
 *
 * The main window of the Bamformatics GUI.
 *
 * @author tomasz
 */
public class BamfoGUI extends javax.swing.JFrame {

    private final static String version = Bamformatics.getVersion();
    // the GUISettings object will hold much of the science-related options
    private BamfoGUISettings guiSettings;
    // the executor will handle multiple jobs running at the same time.
    private ExecutorService taskExecutor;
    // Dialog windows for custom work:
    // the dialogs are lazy-initialized at run-time
    private FilterDialog filterDialog = null;
    private CallDialog callDialog = null;
    private ScriptDialog scriptDialog = null;
    // filesTreePanel and scriptsTreePanel are the components displaying trees.
    private BamfoTreePanel filesTreePanel;
    private BamfoTreePanel scriptsTreePanel;
    // activeTreePanel will be a reference to the first two.
    // will be reset repeatedly to help object figure out which event triggered something.    
    private BamfoTreePanel activeTreePanel;
    // the refreshTimer will keep the panels in proper condition
    private Timer refreshTimer;
    // copytemp will be a temporary storage of file identifiers that can be copy/pasted.
    private final ArrayList<BamfoTreeNode> copyTemp = new ArrayList<>();

    /**
     * Class deals with how popups appear in relation to items in the file
     * trees.
     */
    private class BamfoTreeSelectionListener implements TreeSelectionListener, MouseListener {

        private final BamfoTreePanel thisTreePanel;

        public BamfoTreeSelectionListener(BamfoTreePanel panel) {
            this.thisTreePanel = panel;
        }

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            activeTreePanel = thisTreePanel;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            activeTreePanel = thisTreePanel;
        }

        /**
         * This function customizes and displays popup menus when different
         * files in the proejctsTree are pressed.
         *
         *
         *
         * @param e
         */
        @Override
        public void mousePressed(MouseEvent e) {
            activeTreePanel = thisTreePanel;

            if (e.getButton() == MouseEvent.BUTTON3) {
                BamfoTreeNode selectednode = thisTreePanel.getLastSelectedPathComponent();
                if (selectednode == null) {
                    return;
                }

                if (selectednode.getParent() == thisTreePanel.getRoot()) {
                    popupDirectory();
                    leafCloseProject.setVisible(true);
                    projectsPopup.show(e.getComponent(), e.getX(), e.getY());
                } else {
                    leafCloseProject.setVisible(false);
                    popupDirectory(false);
                    if (selectednode.isLeaf()) {
                        // check if can be executed, i.e. is a script
                        File nodefile = selectednode.getNodeFile();
                        if (nodefile.isDirectory()) {
                            popupDirectory();
                        } else {
                            // use file extension to hint at some operations
                            String[] nodeNameExt = FileExtensionGetter.getExtensionSplit(nodefile);
                            if (nodeNameExt[1].endsWith("bam")) {
                                popupCall();
                            } else if ((nodeNameExt[1].endsWith("vcf"))) {
                                popupFilter();
                            } else {
                                popupViewonly();
                            }
                            // add executable options if necessary
                            if (nodefile.canExecute()) {
                                popupScript(true);
                            }
                            popupExecutable(nodefile.canExecute());
                            popupCopy(true);
                            popupPaste(false);
                        }

                    } else {
                        // must be a directory if it has children
                        popupDirectory();
                    }
                    projectsPopup.show(e.getComponent(), e.getX(), e.getY());
                }
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                if (e.getClickCount() == 2) {
                    // perform the default action, which is view
                    leafViewActionPerformed(null);
                }
            }

        }

        @Override
        public void mouseReleased(MouseEvent e) {
            activeTreePanel = thisTreePanel;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }

        private void popupView(boolean show) {
            leafView.setVisible(show);
            sepAfterView.setVisible(show);
        }

        private void popupCopy(boolean show) {
            leafFileCopy.setVisible(show);
        }

        private void popupPaste(boolean show) {
            leafFilePaste.setVisible(show);
        }

        private void popupPasteEnable(boolean enable) {
            leafFilePaste.setEnabled(enable);
        }

        private void popupViewonly() {
            popupView(true);
            leafNewFolder.setVisible(false);
            leafFilterDefault.setVisible(false);
            leafFilterGeneral.setVisible(false);
            leafCallDefault.setVisible(false);
            leafCallGeneral.setVisible(false);
            sepAfterVariants.setVisible(false);
            popupScript(false);
        }

        private void popupCall(boolean show) {
            leafCallDefault.setVisible(show);
            leafCallGeneral.setVisible(show);

            if (!show) {
                return;
            }

            if (callDialog == null) {
                leafCallDefault.setEnabled(false);
            } else {
                String lastconfig = callDialog.getCurrentConfig();
                if (lastconfig == null) {
                    leafCallDefault.setEnabled(false);
                    leafCallDefault.setText("Call [default]");
                } else {
                    leafCallDefault.setEnabled(true);
                    leafCallDefault.setText("Call [" + lastconfig + "]");
                }
            }

            sepAfterVariants.setVisible(true);
        }

        private void popupCall() {
            leafView.setVisible(true);
            leafNewFolder.setVisible(false);
            popupCall(true);
            popupScript(false);
            leafFilterDefault.setVisible(false);
            leafFilterGeneral.setVisible(false);
        }

        private void popupFilter(boolean show) {
            leafFilterDefault.setVisible(show);
            leafFilterGeneral.setVisible(show);

            if (!show) {
                return;
            }

            if (filterDialog == null) {
                leafFilterDefault.setEnabled(false);
            } else {
                String lastconfig = filterDialog.getCurrentConfig();
                leafFilterDefault.setEnabled(true);
                if (lastconfig == null) {
                    leafFilterDefault.setText("Filter [default]");
                } else {
                    leafFilterDefault.setText("Filter [" + lastconfig + "]");
                }
            }
        }

        private void popupFilter() {
            leafView.setVisible(true);
            sepAfterView.setVisible(true);
            leafNewFolder.setVisible(false);
            popupScript(false);
            popupFilter(true);
            leafCallDefault.setVisible(false);
            leafCallGeneral.setVisible(false);
            sepAfterVariants.setVisible(true);
        }

        private void popupScript(boolean show) {
            leafExecuteGeneral.setVisible(show);
            sepAfterExec.setVisible(show);
        }

        private void popupDirectory(boolean show) {
            leafNewFolder.setVisible(show);
            leafRefresh.setVisible(show);
        }

        private void popupDirectory() {
            popupDirectory(true);
            popupView(false);
            popupFilter(false);
            popupCall(false);
            popupScript(false);
            //popupExecutable(false);
            leafExecutable.setVisible(false);
            popupCopy(true);
            popupPaste(true);
            popupPasteEnable(!copyTemp.isEmpty());
        }

        private void popupExecutable(boolean exec) {
            leafExecutable.setSelected(exec);
            leafExecutable.setVisible(true);
        }
    }

    /**
     * Creates new form BamfoGUI
     */
    public BamfoGUI() {

        // initComponents is the function created via the NetBeans IDE        
        initComponents();
        setTitle("Bamformatics - Loading...");

        dataFileTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        scriptFileTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // read the user directory and obtain the bare minimum settings
        File userdir = getUserHomeDir();
        if (userdir == null) {
            System.exit(0);
        }
        guiSettings = new BamfoGUISettings(userdir);
        // make some minor/quick changes to the appearance of the form
        editWindow();
                
        // make more changes to the window in a separate thread?
        // do this from another thread so that user can start using the 
        // GUI as it is before it is fully loaded.                
        new LoadDetailsInBackground().execute();                
        
        setTitle("Bamformatics");
    }

    /**
     * this class initializes the custom look of the GUI.
     */
    class LoadDetailsInBackground extends javax.swing.SwingWorker {

        @Override
        protected Object doInBackground() throws Exception {
                        
            // complete the load of the user settings (open projects, call/filter configurations, etc.)
            taskExecutor = Executors.newFixedThreadPool(guiSettings.getNumThreads());

            guiSettings.completeLoad();            
            
            // set up the main components
            // the file panels on the left hand side
            BamfoTreeModel btm = guiSettings.getProjectsTreeModel();
            // perhaps show only the scripts panel, perhaps keep both
            if (guiSettings.getSeparateDataScripts() == 0) {
                scriptsTreePanel = new BamfoTreePanel(btm, "All files", BamfoTreeNode.NODECLASS_DATAEXEC, null);
                filesTreePanel = new BamfoTreePanel(btm, "None", BamfoTreeNode.NODECLASS_NONE, null);
                filesTreePanel.setVisible(false);
                activeTreePanel = scriptsTreePanel;
            } else {
                filesTreePanel = new BamfoTreePanel(btm, "Data", BamfoTreeNode.NODECLASS_DATAONLY, null);
                scriptsTreePanel = new BamfoTreePanel(btm, "Executables", BamfoTreeNode.NODECLASS_EXECONLY, null);
                activeTreePanel = filesTreePanel;
            }

            // edit the components (add listeners, tune appearance, etc.)
            editComponents();
            dataFileTree.setCursor(Cursor.getDefaultCursor());
            scriptFileTree.setCursor(Cursor.getDefaultCursor());

            Object temp = guiSettings.get("horizontalsplit");
            if (temp != null) {
                mainSplitPane.setDividerLocation((Integer) temp);
            }

            temp = guiSettings.get("verticalsplit");
            if (temp != null) {
                filesSplitPane.setDividerLocation((Integer) temp);
            }

            // load the files that were open last time            
            reopenFiles();

            // create a timer that will occasionally look for new files in the disk                    
            refreshTimer = new Timer(1000 * guiSettings.getFolderTimer(), new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // remember current selection in tree                    
                    TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
                    BamfoTreeNode[] selectednodes = null;
                    if (selected != null) {
                        selectednodes = new BamfoTreeNode[selected.length];
                        for (int i = 0; i < selected.length; i++) {
                            selectednodes[i] = (BamfoTreeNode) selected[i].getLastPathComponent();
                        }
                    }
                    guiSettings.saveMap("scriptstree", scriptsTreePanel.getExpanded());
                    guiSettings.saveMap("filestree", filesTreePanel.getExpanded());
                    guiSettings.updateAllProjects();
                    filesTreePanel.refresh();
                    scriptsTreePanel.refresh();
                    // restore selection in tree
                    if (selectednodes != null) {                        
                    }                    
                }
            });
            refreshTimer.start();

            filesTreePanel.refresh();
            scriptsTreePanel.refresh();
                                              
            // Here the exit command is useful for profiling startup of the GUI.
            //System.exit(0);
            return 1;
        }
    }

    /**
     * Adjusts the look of the GUI window (size of window, size of panels)
     */
    private void editWindow() {
        // restore the look of the window

        Object temp = guiSettings.get("desktopbounds");
        if (temp == null) {
            // preset size is not set. Place a frame in the middle of the screen.
            Dimension dimen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            this.setBounds(new java.awt.Rectangle(dimen.width / 8, dimen.height / 8,
                    dimen.width * 3 / 4, dimen.height * 3 / 4));
        } else {
            java.awt.Rectangle newbounds = new java.awt.Rectangle();
            newbounds.x = (Integer) guiSettings.get("desktopbounds.x");
            newbounds.y = (Integer) guiSettings.get("desktopbounds.y");
            newbounds.width = (Integer) guiSettings.get("desktopbounds.width");
            newbounds.height = (Integer) guiSettings.get("desktopbounds.height");
            this.setBounds(newbounds);
        }

        temp = guiSettings.get("horizontalsplit");
        if (temp != null) {
            mainSplitPane.setDividerLocation((Integer) temp);
        }

        temp = guiSettings.get("verticalsplit");
        if (temp != null) {
            filesSplitPane.setDividerLocation((Integer) temp);
        }

    }

    private void editComponents() {

        // modify icons in the menu
        DecoratedImageIcon decicon = new DecoratedImageIcon(BamfoIconMaker.getNotepadIcon());
        decicon.setDecoration(DecoratedImageIcon.Decoration.plus, DecoratedImageIcon.BOTTOMLEFT);
        menuFileNewScratch.setIcon(decicon);

        DecoratedImageIcon newscripticon = new DecoratedImageIcon(BamfoIconMaker.getScriptIcon());
        newscripticon.setDecoration(DecoratedImageIcon.Decoration.plus, DecoratedImageIcon.BOTTOMLEFT);
        menuFileNewScript.setIcon(newscripticon);

        // load the expanded states for the tree panels
        filesTreePanel.setExpanded(guiSettings.loadBooleanMap("filestree"));
        scriptsTreePanel.setExpanded(guiSettings.loadBooleanMap("scriptstree"));

        JTree scriptsTree = scriptsTreePanel.getTree();
        BamfoTreeSelectionListener ssl = new BamfoTreeSelectionListener(scriptsTreePanel);
        scriptsTree.addTreeSelectionListener(ssl);
        scriptsTree.addMouseListener(ssl);

        filesSplitPane.setLeftComponent(filesTreePanel);
        filesSplitPane.setRightComponent(scriptsTreePanel);

        // perhaps show only the scripts panel, perhaps keep both
        if (guiSettings.getSeparateDataScripts() == 0) {
            mainSplitPane.setLeftComponent(scriptsTreePanel);
            filesTreePanel.setVisible(false);
        } else {
            // set up mouse listeners on the files tree, 
            // which will be visible in this run
            JTree filesTree = filesTreePanel.getTree();
            BamfoTreeSelectionListener fsl = new BamfoTreeSelectionListener(filesTreePanel);
            filesTree.addTreeSelectionListener(fsl);
            filesTree.addMouseListener(fsl);
        }
    }

    /**
     * Open all files that were saved as open at the time of last exit.
     *
     */
    private void reopenFiles() {
        // open files in the tab menus
        HashMap<String, Object> opentabs = guiSettings.loadMap("opentabs");
        for (int i = 0; i < opentabs.size(); i++) {
            Object opentabname = opentabs.get("tab." + i);
            if (opentabname == null) {
                fileViewTab.addTab("Scratch", new BamfoScratchViewer());
                fileViewTab.setTabComponentAt(i, new BamfoTabComponent(fileViewTab));
                fileViewTab.setToolTipTextAt(i, null);
            } else {
                File f = new File(opentabname.toString());
                fileViewTab.addTab(f.getName(), new BamfoTableViewer(f, fileViewTab, guiSettings.getNumDataRowsLoad()));
                fileViewTab.setTabComponentAt(i, new BamfoTabComponent(fileViewTab));
                fileViewTab.setToolTipTextAt(i, f.getAbsolutePath());
            }
        }
        // give focus to the item that was selected before
        Object temp = guiSettings.get("selectedtab");
        if (temp != null) {
            int selectedtab = (Integer) temp;
            if (selectedtab > -1 && selectedtab < fileViewTab.getTabCount()) {
                fileViewTab.setSelectedIndex(selectedtab);
            }
        }

    }

    /**
     *
     * @return
     *
     * the user's home directory. This directory is read from the application's
     * preferences, or set up via a dialog.
     *
     */
    private File getUserHomeDir() {

        // check if user directory is set. If it is set and readable, 
        Preferences hereprefs = Preferences.userNodeForPackage(BamfoGUI.class);
        String savedHomeDirString = hereprefs.get("homedir", null);
        if (savedHomeDirString != null) {
            File saveHomeDir = new File(savedHomeDirString);
            if (saveHomeDir.isDirectory() && saveHomeDir.canRead() && saveHomeDir.canWrite()) {
                return saveHomeDir;
            }
        }

        // if reached here, try to make a user directory        
        JOptionPane.showMessageDialog(rootPane,
                "It seems that you are using Bamformatics for the first time.\n\n"
                + "Please set a home directory.\n\n"
                + "Bamformatics will use this home directory to store settings "
                + "and temporary files.\n", "Home directory",
                JOptionPane.INFORMATION_MESSAGE);

        File newHomeDir = BamfoMiscFile.chooseDir();
        if (newHomeDir == null) {
            JOptionPane.showMessageDialog(rootPane, "Bamformatics will now exit.\n"
                    + "Please start again to set up a home directory.", "Home directory",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }

        // save the chosen user directory
        hereprefs.put("homedir", newHomeDir.getAbsolutePath());

        JOptionPane.showMessageDialog(rootPane, "The home directory has been set.\n"
                + "You can customize Bamformatics further by going to Tools:Options", "Home directory",
                JOptionPane.INFORMATION_MESSAGE);

        // and continue with the rest of the application
        return newHomeDir;
    }

    /**
     * called before the options dialog is opened.
     */
    private void reloadOptions() {
        optionsThreadSpinner.setValue(guiSettings.getNumThreads());
        dataRowsSpinner.setValue(guiSettings.getNumDataRowsLoad());
        optionsHomeDirTextField.setText(guiSettings.getUserdir().getPath());
        separateRadio.setSelected(guiSettings.getSeparateDataScripts() == 1);
        togetherRadio.setSelected(guiSettings.getSeparateDataScripts() == 0);
        optionsFolderTimerSpinner.setValue(guiSettings.getFolderTimer());
        optionsScriptTimerSpinner.setValue(guiSettings.getScriptTimer());
        omitExtTextField.setText(guiSettings.getOmitExtensions());
    }

    /**
     * Turn the list of open tabs into a hashmap. This is somewhat inefficient,
     * an array would be sufficient, but the hashmap is later directly passed on
     * to a save-to-disk function
     *
     * @return
     */
    private HashMap<String, String> getOpenTabs() {
        int numtabs = fileViewTab.getTabCount();
        HashMap<String, String> ans = new HashMap<>(numtabs * 2);
        int j = 0;
        for (int i = 0; i < numtabs; i++) {
            if (fileViewTab.getComponentAt(i) instanceof BamfoTableViewer) {
                ans.put("tab." + j, fileViewTab.getToolTipTextAt(i));
                j++;
            } else if (fileViewTab.getComponentAt(i) instanceof BamfoScratchViewer) {
                ans.put("tab." + j, null);
                j++;
            }
        }
        return ans;
    }

    /**
     * Called when the application exits. Saves the look into the settings.
     */
    private void saveGUIgraphics() {
        // make sure the window bounds and other graphics are saved        
        java.awt.Rectangle boundsRect = this.getBounds();
        guiSettings.set("desktopbounds", Boolean.TRUE);
        guiSettings.set("desktopbounds.x", new Integer(boundsRect.x));
        guiSettings.set("desktopbounds.y", new Integer(boundsRect.y));
        guiSettings.set("desktopbounds.width", new Integer(boundsRect.width));
        guiSettings.set("desktopbounds.height", new Integer(boundsRect.height));
        guiSettings.set("horizontalsplit", new Integer(mainSplitPane.getDividerLocation()));
        guiSettings.set("verticalsplit", new Integer(filesSplitPane.getDividerLocation()));
        guiSettings.set("selectedtab", new Integer(fileViewTab.getSelectedIndex()));
        guiSettings.save();
        guiSettings.saveMap("filestree", filesTreePanel.getExpanded());
        guiSettings.saveMap("scriptstree", scriptsTreePanel.getExpanded());
        guiSettings.saveMap("opentabs", getOpenTabs());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        optionsDialog = new javax.swing.JDialog();
        jLabel1 = new javax.swing.JLabel();
        OptionsCancelButton = new javax.swing.JButton();
        OptionsOkButton = new javax.swing.JButton();
        optionsHomeDirTextField = new javax.swing.JTextField();
        OptionsHomeDirBrowseButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        optionsThreadSpinner = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        optionsFolderTimerSpinner = new javax.swing.JSpinner();
        optionsScriptTimerSpinner = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        separateRadio = new javax.swing.JRadioButton();
        togetherRadio = new javax.swing.JRadioButton();
        jLabel8 = new javax.swing.JLabel();
        dataRowsSpinner = new javax.swing.JSpinner();
        jLabel9 = new javax.swing.JLabel();
        omitExtTextField = new javax.swing.JTextField();
        projectsPopup = new javax.swing.JPopupMenu();
        leafNewFolder = new javax.swing.JMenuItem();
        leafRefresh = new javax.swing.JMenuItem();
        leafCloseProject = new javax.swing.JMenuItem();
        leafView = new javax.swing.JMenuItem();
        sepAfterView = new javax.swing.JPopupMenu.Separator();
        leafCallDefault = new javax.swing.JMenuItem();
        leafCallGeneral = new javax.swing.JMenuItem();
        leafFilterDefault = new javax.swing.JMenuItem();
        leafFilterGeneral = new javax.swing.JMenuItem();
        sepAfterVariants = new javax.swing.JPopupMenu.Separator();
        leafExecuteGeneral = new javax.swing.JMenuItem();
        sepAfterExec = new javax.swing.JPopupMenu.Separator();
        leafFileCopy = new javax.swing.JMenuItem();
        leafFilePaste = new javax.swing.JMenuItem();
        leafRename = new javax.swing.JMenuItem();
        leafDelete = new javax.swing.JMenuItem();
        leafExecutable = new javax.swing.JCheckBoxMenuItem();
        togetherseparateGroup = new javax.swing.ButtonGroup();
        mainSplitPane = new javax.swing.JSplitPane();
        filesSplitPane = new javax.swing.JSplitPane();
        projectsScrollPane = new javax.swing.JScrollPane();
        dataFileTree = new javax.swing.JTree();
        jScrollPane1 = new javax.swing.JScrollPane();
        scriptFileTree = new javax.swing.JTree();
        fileViewTab = new javax.swing.JTabbedPane();
        jMenuBar1 = new javax.swing.JMenuBar();
        menuFile = new javax.swing.JMenu();
        menuFileNewScratch = new javax.swing.JMenuItem();
        menuFileNewScript = new javax.swing.JMenuItem();
        menuFileOpen = new javax.swing.JMenuItem();
        menuFileClose = new javax.swing.JMenuItem();
        menuFileImport = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        menuFileSave = new javax.swing.JMenuItem();
        menuFileSaveAs = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        menuFileQuit = new javax.swing.JMenuItem();
        menuView = new javax.swing.JMenu();
        menuViewPrevious = new javax.swing.JMenuItem();
        menuViewNext = new javax.swing.JMenuItem();
        menuViewClose = new javax.swing.JMenuItem();
        menuTools = new javax.swing.JMenu();
        menuToolsCalling = new javax.swing.JMenuItem();
        menuToolsFiltering = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        menuToolsOptions = new javax.swing.JMenuItem();
        menuHelp = new javax.swing.JMenu();
        menuHelpAbout = new javax.swing.JMenuItem();

        optionsDialog.setTitle("Options");
        optionsDialog.setMinimumSize(new java.awt.Dimension(600, 442));
        optionsDialog.setPreferredSize(new java.awt.Dimension(539, 464));
        optionsDialog.setResizable(false);

        jLabel1.setText("Home Directory:");

        OptionsCancelButton.setText("Cancel");
        OptionsCancelButton.setMaximumSize(new java.awt.Dimension(90, 27));
        OptionsCancelButton.setMinimumSize(new java.awt.Dimension(90, 27));
        OptionsCancelButton.setPreferredSize(new java.awt.Dimension(90, 27));
        OptionsCancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OptionsCancelButtonActionPerformed(evt);
            }
        });

        OptionsOkButton.setText("OK");
        OptionsOkButton.setMaximumSize(new java.awt.Dimension(90, 27));
        OptionsOkButton.setMinimumSize(new java.awt.Dimension(90, 27));
        OptionsOkButton.setPreferredSize(new java.awt.Dimension(90, 27));
        OptionsOkButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OptionsOkButtonActionPerformed(evt);
            }
        });

        optionsHomeDirTextField.setEditable(false);

        OptionsHomeDirBrowseButton.setText("...");
        OptionsHomeDirBrowseButton.setMaximumSize(new java.awt.Dimension(24, 27));
        OptionsHomeDirBrowseButton.setMinimumSize(new java.awt.Dimension(24, 27));
        OptionsHomeDirBrowseButton.setPreferredSize(new java.awt.Dimension(24, 27));
        OptionsHomeDirBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OptionsHomeDirBrowseButtonActionPerformed(evt);
            }
        });

        jLabel2.setText("Threads:");

        optionsThreadSpinner.setModel(new javax.swing.SpinnerNumberModel(4, 0, 256, 1));
        optionsThreadSpinner.setMinimumSize(new java.awt.Dimension(70, 27));
        optionsThreadSpinner.setPreferredSize(new java.awt.Dimension(70, 27));

        jLabel3.setText("Folder refresh interval:");

        jLabel4.setText("Script refresh interval:");

        optionsFolderTimerSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(120), Integer.valueOf(0), null, Integer.valueOf(10)));

        optionsScriptTimerSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 0, 30, 1));

        jLabel5.setText("seconds");

        jLabel6.setText("seconds");

        jLabel7.setText("Data and script files:");

        togetherseparateGroup.add(separateRadio);
        separateRadio.setText("separate");
        separateRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                separateRadioActionPerformed(evt);
            }
        });

        togetherseparateGroup.add(togetherRadio);
        togetherRadio.setText("together");

        jLabel8.setText("Data rows loading:");

        dataRowsSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(250000), Integer.valueOf(0), null, Integer.valueOf(100)));

        jLabel9.setText("Omit extensions:");

        javax.swing.GroupLayout optionsDialogLayout = new javax.swing.GroupLayout(optionsDialog.getContentPane());
        optionsDialog.getContentPane().setLayout(optionsDialogLayout);
        optionsDialogLayout.setHorizontalGroup(
            optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(optionsDialogLayout.createSequentialGroup()
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel9))
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addGroup(optionsDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel2)
                                    .addGap(116, 116, 116)
                                    .addComponent(optionsThreadSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(optionsDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel3)
                                    .addGap(18, 18, 18)
                                    .addComponent(optionsFolderTimerSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(3, 3, 3)
                                    .addComponent(jLabel5))
                                .addGroup(optionsDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel4)
                                    .addGap(23, 23, 23)
                                    .addComponent(optionsScriptTimerSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(12, 12, 12)
                                    .addComponent(jLabel6))
                                .addGroup(optionsDialogLayout.createSequentialGroup()
                                    .addComponent(jLabel1)
                                    .addGap(63, 63, 63)
                                    .addComponent(optionsHomeDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 309, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(OptionsHomeDirBrowseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(optionsDialogLayout.createSequentialGroup()
                                    .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel7)
                                        .addComponent(jLabel8))
                                    .addGap(37, 37, 37)
                                    .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(optionsDialogLayout.createSequentialGroup()
                                            .addComponent(separateRadio)
                                            .addGap(18, 18, 18)
                                            .addComponent(togetherRadio))
                                        .addComponent(dataRowsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(omitExtTextField))))
                            .addGroup(optionsDialogLayout.createSequentialGroup()
                                .addComponent(OptionsOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(OptionsCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        optionsDialogLayout.setVerticalGroup(
            optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(optionsDialogLayout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addComponent(jLabel1))
                    .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(optionsHomeDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(OptionsHomeDirBrowseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(6, 6, 6)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addComponent(jLabel2))
                    .addComponent(optionsThreadSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(27, 27, 27)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(optionsFolderTimerSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel5))))
                .addGap(12, 12, 12)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(optionsScriptTimerSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4)
                            .addComponent(jLabel6))))
                .addGap(18, 18, 18)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(optionsDialogLayout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addComponent(jLabel7))
                    .addComponent(separateRadio)
                    .addComponent(togetherRadio))
                .addGap(18, 18, 18)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(dataRowsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(omitExtTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 66, Short.MAX_VALUE)
                .addGroup(optionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(OptionsCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(OptionsOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(21, 21, 21))
        );

        leafNewFolder.setText("New Folder...");
        leafNewFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafNewFolderActionPerformed(evt);
            }
        });
        projectsPopup.add(leafNewFolder);

        leafRefresh.setText("Refresh");
        leafRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafRefreshActionPerformed(evt);
            }
        });
        projectsPopup.add(leafRefresh);

        leafCloseProject.setText("Close");
        leafCloseProject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafCloseProjectActionPerformed(evt);
            }
        });
        projectsPopup.add(leafCloseProject);

        leafView.setText("View");
        leafView.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafViewActionPerformed(evt);
            }
        });
        projectsPopup.add(leafView);

        sepAfterView.setPreferredSize(new java.awt.Dimension(2, 1));
        projectsPopup.add(sepAfterView);

        leafCallDefault.setText("Call Variants [default]");
        leafCallDefault.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafCallDefaultActionPerformed(evt);
            }
        });
        projectsPopup.add(leafCallDefault);

        leafCallGeneral.setText("Call variants...");
        leafCallGeneral.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafCallGeneralActionPerformed(evt);
            }
        });
        projectsPopup.add(leafCallGeneral);

        leafFilterDefault.setText("Filter [default]");
        leafFilterDefault.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafFilterDefaultActionPerformed(evt);
            }
        });
        projectsPopup.add(leafFilterDefault);

        leafFilterGeneral.setText("Filter...");
        leafFilterGeneral.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafFilterGeneralActionPerformed(evt);
            }
        });
        projectsPopup.add(leafFilterGeneral);

        sepAfterVariants.setPreferredSize(new java.awt.Dimension(2, 1));
        projectsPopup.add(sepAfterVariants);

        leafExecuteGeneral.setText("Execute...");
        leafExecuteGeneral.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafExecuteGeneralActionPerformed(evt);
            }
        });
        projectsPopup.add(leafExecuteGeneral);

        sepAfterExec.setPreferredSize(new java.awt.Dimension(2, 1));
        projectsPopup.add(sepAfterExec);

        leafFileCopy.setText("Copy");
        leafFileCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafFileCopyActionPerformed(evt);
            }
        });
        projectsPopup.add(leafFileCopy);

        leafFilePaste.setText("Paste");
        leafFilePaste.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafFilePasteActionPerformed(evt);
            }
        });
        projectsPopup.add(leafFilePaste);

        leafRename.setText("Rename");
        leafRename.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafRenameActionPerformed(evt);
            }
        });
        projectsPopup.add(leafRename);

        leafDelete.setText("Delete");
        leafDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafDeleteActionPerformed(evt);
            }
        });
        projectsPopup.add(leafDelete);

        leafExecutable.setSelected(true);
        leafExecutable.setText("Executable");
        leafExecutable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leafExecutableActionPerformed(evt);
            }
        });
        projectsPopup.add(leafExecutable);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Bamformatics");
        setMinimumSize(new java.awt.Dimension(640, 480));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                GUICloseListener(evt);
            }
        });
        getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.Y_AXIS));

        mainSplitPane.setBorder(null);
        mainSplitPane.setDividerLocation(240);
        mainSplitPane.setDividerSize(5);

        filesSplitPane.setDividerLocation(250);
        filesSplitPane.setDividerSize(5);
        filesSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        projectsScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.SystemColor.activeCaptionBorder));

        dataFileTree.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("Loading...");
        dataFileTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        dataFileTree.setRowHeight(20);
        projectsScrollPane.setViewportView(dataFileTree);

        filesSplitPane.setLeftComponent(projectsScrollPane);

        treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("Loading...");
        scriptFileTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jScrollPane1.setViewportView(scriptFileTree);

        filesSplitPane.setRightComponent(jScrollPane1);

        mainSplitPane.setLeftComponent(filesSplitPane);

        fileViewTab.setBackground(new java.awt.Color(204, 204, 204));
        mainSplitPane.setRightComponent(fileViewTab);

        getContentPane().add(mainSplitPane);

        menuFile.setMnemonic('F');
        menuFile.setText("File");
        menuFile.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                menuFileMenuSelected(evt);
            }
        });

        menuFileNewScratch.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        menuFileNewScratch.setMnemonic('N');
        menuFileNewScratch.setText("New scratch area");
        menuFileNewScratch.setToolTipText("");
        menuFileNewScratch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileNewScratchActionPerformed(evt);
            }
        });
        menuFile.add(menuFileNewScratch);

        menuFileNewScript.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        menuFileNewScript.setText("New script");
        menuFileNewScript.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileNewScriptActionPerformed(evt);
            }
        });
        menuFile.add(menuFileNewScript);

        menuFileOpen.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        menuFileOpen.setText("Open File...");
        menuFileOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileOpenActionPerformed(evt);
            }
        });
        menuFile.add(menuFileOpen);

        menuFileClose.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_MASK));
        menuFileClose.setText("Close File");
        menuFileClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileCloseActionPerformed(evt);
            }
        });
        menuFile.add(menuFileClose);

        menuFileImport.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, java.awt.event.InputEvent.CTRL_MASK));
        menuFileImport.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/folder-horizontal-open.png"))); // NOI18N
        menuFileImport.setText("Import Folder...");
        menuFileImport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileImportProjectActionPerformed(evt);
            }
        });
        menuFile.add(menuFileImport);

        jSeparator3.setBorder(null);
        jSeparator3.setPreferredSize(new java.awt.Dimension(0, 1));
        menuFile.add(jSeparator3);

        menuFileSave.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        menuFileSave.setIcon(new javax.swing.ImageIcon("/scripts/java/Bamformatics/src/bamfo/gui/icons/disk-black.png")); // NOI18N
        menuFileSave.setText("Save");
        menuFileSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileSaveActionPerformed(evt);
            }
        });
        menuFile.add(menuFileSave);

        menuFileSaveAs.setText("Save as...");
        menuFileSaveAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileSaveAsActionPerformed(evt);
            }
        });
        menuFile.add(menuFileSaveAs);

        jSeparator4.setBorder(null);
        jSeparator4.setPreferredSize(new java.awt.Dimension(0, 1));
        menuFile.add(jSeparator4);

        menuFileQuit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK));
        menuFileQuit.setText("Quit");
        menuFileQuit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileQuitActionPerformed(evt);
            }
        });
        menuFile.add(menuFileQuit);

        jMenuBar1.add(menuFile);

        menuView.setMnemonic('V');
        menuView.setText("View");

        menuViewPrevious.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, java.awt.event.InputEvent.ALT_MASK));
        menuViewPrevious.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/arrow-180-medium.png"))); // NOI18N
        menuViewPrevious.setText("Previous view");
        menuViewPrevious.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuViewPreviousActionPerformed(evt);
            }
        });
        menuView.add(menuViewPrevious);

        menuViewNext.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.InputEvent.ALT_MASK));
        menuViewNext.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/arrow-000-medium.png"))); // NOI18N
        menuViewNext.setText("Next view");
        menuViewNext.setToolTipText("");
        menuViewNext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuViewNextActionPerformed(evt);
            }
        });
        menuView.add(menuViewNext);

        menuViewClose.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.ALT_MASK));
        menuViewClose.setText("Close view");
        menuViewClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuViewCloseActionPerformed(evt);
            }
        });
        menuView.add(menuViewClose);

        jMenuBar1.add(menuView);

        menuTools.setMnemonic('T');
        menuTools.setText("Tools");

        menuToolsCalling.setMnemonic('c');
        menuToolsCalling.setText("Variant calling...");
        menuToolsCalling.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuToolsCallingActionPerformed(evt);
            }
        });
        menuTools.add(menuToolsCalling);

        menuToolsFiltering.setMnemonic('f');
        menuToolsFiltering.setText("Variant filtering...");
        menuToolsFiltering.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuToolsFilteringActionPerformed(evt);
            }
        });
        menuTools.add(menuToolsFiltering);

        jSeparator2.setPreferredSize(new java.awt.Dimension(2, 1));
        menuTools.add(jSeparator2);

        menuToolsOptions.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/gear.png"))); // NOI18N
        menuToolsOptions.setMnemonic('O');
        menuToolsOptions.setText("Options...");
        menuToolsOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuToolsOptionsActionPerformed(evt);
            }
        });
        menuTools.add(menuToolsOptions);

        jMenuBar1.add(menuTools);

        menuHelp.setMnemonic('H');
        menuHelp.setText("Help");

        menuHelpAbout.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, java.awt.event.InputEvent.CTRL_MASK));
        menuHelpAbout.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/information.png"))); // NOI18N
        menuHelpAbout.setMnemonic('A');
        menuHelpAbout.setText("About");
        menuHelpAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuHelpAboutActionPerformed(evt);
            }
        });
        menuHelp.add(menuHelpAbout);

        jMenuBar1.add(menuHelp);

        setJMenuBar(jMenuBar1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void menuFileQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileQuitActionPerformed
        GUICloseListener(null);
    }//GEN-LAST:event_menuFileQuitActionPerformed

    private void menuFileImportProjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileImportProjectActionPerformed
        File newproject = BamfoMiscFile.chooseDir();
        // perhaps the user pressed cancel
        if (newproject == null) {
            return;
        }
        // otherwise load the project
        super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        // perhaps the directory is already included        
        if (guiSettings.addProject(newproject)) {
            filesTreePanel.refresh();
            scriptsTreePanel.refresh();
        }
        super.setCursor(Cursor.getDefaultCursor());
    }//GEN-LAST:event_menuFileImportProjectActionPerformed

    /**
     * deletes a file or a folder. If it is a folder, all files with the
     * directory will be deleted recursively.
     *
     * @param f
     */
    private void deleteFileOrDir(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirectory()) {
                    deleteFileOrDir(children[i]);
                }
                children[i].delete();
            }
        }
        f.delete();
    }

    /**
     * creates a viewer panel with a given name. The viewer is just for looks.
     * It does not have a runnable task associated with it. This will have to be
     * set later.
     *
     * Note: This is a public method that allows dialog boxes and other classes
     * to add viewers to the BamfoGUI.
     *
     * @param taskname
     *
     * @return
     *
     * a new BamfoTaskViewer that has been added to the BamfoGUI tabbed panel.
     *
     */
    public synchronized BamfoTaskViewer openTaskViewer(File f, String type, String prefix) {
        int numtabsnow = fileViewTab.getTabCount();

        // if got here, need to create a new viewer for an executable task
        BamfoTaskViewer taskviewer = new BamfoTaskViewer(taskExecutor, fileViewTab, prefix);
        String taskname = prefix + f.getName() + " [" + type + "]";
        fileViewTab.addTab(taskname, taskviewer);
        fileViewTab.setTabComponentAt(numtabsnow, new BamfoTabComponent(fileViewTab));
        fileViewTab.setToolTipTextAt(numtabsnow, f.getAbsolutePath());
        fileViewTab.setSelectedIndex(numtabsnow);

        return taskviewer;
    }

    /**
     * creates a viewer for the given file. This creates a tab in the RHS tab
     * pane and makes it show up.
     *
     * @param f
     */
    private void openFileViewer(File f) {

        if (f == null) {
            return;
        }
        // check if contents is text based, signal problems otherwise
        if (!BamfoTableViewer.canReadFile(f)) {
            JOptionPane.showMessageDialog(this,
                    "File does not seem to contain text or alignment data.", "File View Aborted",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int numtabsnow = fileViewTab.getTabCount();
        // checks if the file is not already open
        // if it is, don't open it again, but make it pop up, 
        String fpath = f.getAbsolutePath();
        for (int i = 0; i < numtabsnow; i++) {
            BamfoViewer tabviewer = (BamfoViewer) fileViewTab.getComponentAt(i);
            if (fpath.equals(tabviewer.getViewerDescriptor())) {
                fileViewTab.setSelectedIndex(i);
                return;
            }
        }
        // if got here, need to create a new viewer
        fileViewTab.addTab(f.getName(), new BamfoTableViewer(f, fileViewTab, guiSettings.getNumDataRowsLoad()));
        fileViewTab.setTabComponentAt(numtabsnow, new BamfoTabComponent(fileViewTab));
        fileViewTab.setToolTipTextAt(numtabsnow, fpath);
        fileViewTab.setSelectedIndex(numtabsnow);
    }

    private void showFilterDialog() {
        filterDialog.setVisible(true);
    }

    private void showCallDialog() {
        callDialog.setVisible(true);
    }

    /**
     * This function closes the application. It waits for the executor to
     * shutdown and saves graphical settings before exit.
     *
     * @param evt
     */
    private void GUICloseListener(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_GUICloseListener
        // shutdown all the services and exit
        // the first few lines may give an exception if something was 
        // not properly initialized, but in most cases should not cause trouble.
        try {
            taskExecutor.shutdown();
            refreshTimer.stop();
            saveGUIgraphics();
            guiSettings.save();
        } catch (Exception ex) {
            System.exit(1);
        }
        try {
            taskExecutor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            System.out.println("Exception while waiting for executor shutdown: " + ex.getMessage());
        }

        System.exit(0);
    }//GEN-LAST:event_GUICloseListener

    private void leafFilterDefaultOnSelected(TreePath[] selected) {
        // apply filter on each of the selected files
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            if (node.getNodeFile().isFile()) {
                BamfoTaskViewer taskviewer = this.openTaskViewer(node.getNodeFile(), "Bamfo", "Filtering ");
                // create a runnable task that will perform the filtering                
                BamfoRunnableTask brun = filterDialog.makeRunnable(node.getNodeFile(), taskviewer.getOutputArea());
                // create another task that will perform some GUI cleanup                
                Runnable refreshrunnable = this.makeRefreshRunnable(node.getNodeFile());
                // the task should not be null, but it is, avoid executing it                
                if (brun != null) {
                    taskviewer.setTask(brun);
                    taskviewer.setFinishTask(refreshrunnable);
                    taskviewer.executeTask();
                    taskviewer.setTaskStatus("Filtering");
                }
            }
        }
    }

    private void leafCallDefaultOnSelected(TreePath[] selected) {
        // call on each of the selected files
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            if (node.getNodeFile().isFile()) {
                //String taskname = "Call [" + node.getNodeFile().getName() + "]";
                BamfoTaskViewer taskviewer = this.openTaskViewer(node.getNodeFile(), "Bamfo", "Calling ");
                // create a runnable task that will perform the filtering
                BamfoRunnableTask brun = callDialog.makeRunnable(node.getNodeFile(), taskviewer.getOutputArea());
                // create another task that will perform some GUI cleanup
                Runnable refreshrunnable = this.makeRefreshRunnable(node.getNodeFile());
                // the task should not be null, but it is, avoid executing it
                if (brun != null) {
                    taskviewer.setTask(brun);
                    taskviewer.setFinishTask(refreshrunnable);
                    taskviewer.executeTask();
                    taskviewer.setTaskStatus("Calling");
                }
            }
        }
    }

    private void menuFileNewScratchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileNewScratchActionPerformed
        int numtabs = fileViewTab.getTabCount();
        fileViewTab.addTab("Scratch", new BamfoScratchViewer());
        fileViewTab.setTabComponentAt(numtabs, new BamfoTabComponent(fileViewTab));
        fileViewTab.setToolTipTextAt(numtabs, null);
        fileViewTab.setSelectedIndex(numtabs);
    }//GEN-LAST:event_menuFileNewScratchActionPerformed

    private void menuFileOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileOpenActionPerformed
        // allow the user to choose a file. Then open it.
        File f = BamfoMiscFile.chooseFile();
        openFileViewer(f);
    }//GEN-LAST:event_menuFileOpenActionPerformed

    private void separateRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_separateRadioActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_separateRadioActionPerformed

    private void OptionsHomeDirBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OptionsHomeDirBrowseButtonActionPerformed
        File newHomeDir = BamfoMiscFile.chooseDir();
        if (newHomeDir != null) {
            optionsHomeDirTextField.setText(newHomeDir.getAbsolutePath());
        }
    }//GEN-LAST:event_OptionsHomeDirBrowseButtonActionPerformed

    private void OptionsOkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OptionsOkButtonActionPerformed

        // figure out if the settings need to be updated and saved
        boolean changed = false;
        // figure out if changes will take place only after reload
        boolean afterreload = false;

        // check threads
        if ((int) (Integer) optionsThreadSpinner.getValue() != guiSettings.getNumThreads()) {
            guiSettings.setNumthreads((int) (Integer) optionsThreadSpinner.getValue());
            changed = true;
            afterreload = true;
        }

        // check home directory
        File newHomeDir = new File(optionsHomeDirTextField.getText());
        if (!guiSettings.getUserdir().equals(newHomeDir)) {
            guiSettings.setUserDir(newHomeDir);
            // save the user directory into the registry or other central place
            Preferences hereprefs = Preferences.userNodeForPackage(BamfoGUI.class);
            hereprefs.put("homedir", newHomeDir.getAbsolutePath());
            changed = true;
            afterreload = true;
        }

        // check the timers
        int newFolderTime = (int) (Integer) optionsFolderTimerSpinner.getValue();
        if (newFolderTime != guiSettings.getFolderTimer()) {
            guiSettings.setFolderTimer(newFolderTime);
            refreshTimer.setDelay(1000 * newFolderTime);
            changed = true;
        }
        int newScriptTime = (int) (Integer) optionsScriptTimerSpinner.getValue();
        if (newScriptTime != guiSettings.getScriptTimer()) {
            guiSettings.setScriptTimer(newScriptTime);
            changed = true;
        }

        if (separateRadio.isSelected() != (guiSettings.getSeparateDataScripts() == 1)) {
            guiSettings.setSeparateDataScripts(separateRadio.isSelected());
            afterreload = true;
            changed = true;
        }

        // check the data rows spinner
        int newDataRows = (int) (Integer) dataRowsSpinner.getValue();
        if (newDataRows != guiSettings.getNumDataRowsLoad()) {
            guiSettings.setNumDataRowsLoad(newDataRows);
            changed = true;
        }

        // check the extensions
        String newOmitExt = omitExtTextField.getText();
        if (!newOmitExt.equals(guiSettings.getOmitExtensions())) {
            guiSettings.setOmitExtensions(newOmitExt);
            afterreload = true;
            changed = true;
        }
        
        // save the settings to disk
        if (changed) {
            guiSettings.save();

            if (afterreload) {
                JOptionPane.showMessageDialog(new JPanel(),
                        "Changes will take affect only after reload", "Reload",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }

        optionsDialog.setVisible(false);
    }//GEN-LAST:event_OptionsOkButtonActionPerformed

    /**
     * Called when user presses cancel... makes the dialog disappear and reloads
     * the settings.
     * 
     * @param evt 
     */
    private void OptionsCancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OptionsCancelButtonActionPerformed
        optionsDialog.setVisible(false); 
        reloadOptions();
    }//GEN-LAST:event_OptionsCancelButtonActionPerformed

    private void menuFileMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_menuFileMenuSelected
        // checks if the currently selected tab can be saved
        BamfoViewer bv = (BamfoViewer) fileViewTab.getSelectedComponent();
        if (bv == null) {
            menuFileSave.setEnabled(false);
            menuFileSaveAs.setEnabled(false);
            return;
        }
        menuFileSave.setEnabled(bv.canSave());
        menuFileSaveAs.setEnabled(bv.canSaveAs());

    }//GEN-LAST:event_menuFileMenuSelected

    private void menuFileSaveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileSaveAsActionPerformed
        // get the active component
        BamfoViewer bv = (BamfoViewer) fileViewTab.getSelectedComponent();
        if (bv == null) {
            return;
        }
        // first get a file name
        File savefile = BamfoMiscFile.chooseFile((new File(bv.getViewerDescriptor())).getParentFile());
        if (savefile == null) {
            return;
        }
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        bv.saveAs(savefile);

        filesTreePanel.rePopulate(savefile.getParent());
        scriptsTreePanel.rePopulate(savefile.getParent());
        this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_menuFileSaveAsActionPerformed

    private void menuFileSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileSaveActionPerformed
        // get the active component
        BamfoViewer bv = (BamfoViewer) fileViewTab.getSelectedComponent();
        if (bv == null) {
            return;
        }
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        bv.save();
        this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_menuFileSaveActionPerformed

    /**
     * Shows the scripts dialog box without setting any defaults. If the dialog
     * has already been created. This just brings it up to the front.
     *
     * @param evt
     */
    private void menuFileNewScriptActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileNewScriptActionPerformed
        if (scriptDialog == null) {
            scriptDialog = new ScriptDialog(this, false, null);
        }
        scriptDialog.setVisible(true);
    }//GEN-LAST:event_menuFileNewScriptActionPerformed

    /**
     * close the currently selected tab in the file view tab
     *
     * @param evt
     */
    private void menuFileCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileCloseActionPerformed
        // avoid removing if the file view tab is already empty
        if (fileViewTab.getTabCount() < 1) {
            return;
        }
        int nowtab = fileViewTab.getSelectedIndex();
        if (nowtab >= 0) {
            fileViewTab.remove(nowtab);
        }
        System.gc();
    }//GEN-LAST:event_menuFileCloseActionPerformed

    private void menuHelpAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuHelpAboutActionPerformed
        // display a short message with information
        JOptionPane.showMessageDialog(rootPane,
                "Bamformatics " + version + ".\n\n"
                + "www.sourceforge.net/projects/bamformatics/\n\n",
                "About Bamformatics",
                JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_menuHelpAboutActionPerformed

    private void menuViewPreviousActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuViewPreviousActionPerformed
        switchView(-1);
    }//GEN-LAST:event_menuViewPreviousActionPerformed

    private void menuViewNextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuViewNextActionPerformed
        switchView(+1);
    }//GEN-LAST:event_menuViewNextActionPerformed

    private void menuViewCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuViewCloseActionPerformed
        int numselected = fileViewTab.getSelectedIndex();
        if (numselected >= 0) {
            fileViewTab.remove(numselected);
        }
    }//GEN-LAST:event_menuViewCloseActionPerformed

    private void menuToolsCallingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuToolsCallingActionPerformed
        // create the calling dialog if it doesn't already exist
        if (callDialog == null) {
            callDialog = new CallDialog(this, guiSettings.getCallConfigurationList());
        }

        // show the dialog
        callDialog.setVisible(true);

        // don't do anything at the end. This function acts from the tools menu and
        // is meant to create/edit fitering configurations, not to apply them
        // onto any particular files
    }//GEN-LAST:event_menuToolsCallingActionPerformed

    private void menuToolsFilteringActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuToolsFilteringActionPerformed
        // create the filtering dialog if it doesn't already exist
        if (filterDialog == null) {
            filterDialog = new FilterDialog(this, guiSettings.getFilterConfigurationList());
        }

        // show the dialog
        filterDialog.setVisible(true);

        // don't do anything at the end. This function acts from the tools menu and
        // is meant to create/edit fitering configurations, not to apply them
        // onto any particular files
    }//GEN-LAST:event_menuToolsFilteringActionPerformed

    private void menuToolsOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuToolsOptionsActionPerformed
        optionsDialog.setLocationRelativeTo(this);
        optionsDialog.setModal(true);
        reloadOptions();
        optionsDialog.setVisible(true);
    }//GEN-LAST:event_menuToolsOptionsActionPerformed

    private void leafNewFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafNewFolderActionPerformed

        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();

        // if reached here, really try to rename        
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            File nodefile = node.getNodeFile();

            if (nodefile.isDirectory()) {
                // ask the user for a new name
                String newname = (String) JOptionPane.showInputDialog(rootPane,
                        "New Folder under " + nodefile.getName() + "",
                        "New Folder",
                        JOptionPane.QUESTION_MESSAGE,
                        null, null,
                        "NewFolder");

                if (newname != null) {
                    File newfolder = new File(nodefile.getAbsolutePath(), newname);
                    try {
                        newfolder.mkdirs();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(rootPane,
                                "Failed to create folder " + newfolder.getAbsolutePath()
                                + "\n\n" + ex.getMessage(), "Exception",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    scriptsTreePanel.rePopulate(nodefile.getAbsolutePath());
                    filesTreePanel.rePopulate(nodefile.getAbsolutePath());
                }
            }
        }
    }//GEN-LAST:event_leafNewFolderActionPerformed

    private void leafRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafRefreshActionPerformed
        BamfoTreeNode selectednode = activeTreePanel.getLastSelectedPathComponent();
        if (selectednode == null) {
            return;
        }
        activeTreePanel.rePopulate(selectednode.getNodeFile().getAbsolutePath());
    }//GEN-LAST:event_leafRefreshActionPerformed

    private void leafCloseProjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafCloseProjectActionPerformed

        // first get the tree component that is selected
        BamfoTreeNode node = (BamfoTreeNode) activeTreePanel.getLastSelectedPathComponent();
        File dir = node.getNodeFile();

        // try to remove the node also from the panels
        guiSettings.closeProject(dir);
        filesTreePanel.refresh();
        scriptsTreePanel.refresh();
    }//GEN-LAST:event_leafCloseProjectActionPerformed

    private void leafViewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafViewActionPerformed
        // find which item or items in the projects Tree are selected                
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected == null) {
            return;
        }
        super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        // open them all!
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            if (node.isFile()) {
                openFileViewer(node.getNodeFile());
            }
        }
        super.setCursor(Cursor.getDefaultCursor());
    }//GEN-LAST:event_leafViewActionPerformed

    private void leafCallDefaultActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafCallDefaultActionPerformed
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected != null) {
            leafCallDefaultOnSelected(selected);
        }
    }//GEN-LAST:event_leafCallDefaultActionPerformed

    private void leafCallGeneralActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafCallGeneralActionPerformed
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected == null) {
            return;
        }

        if (callDialog == null) {
            callDialog = new CallDialog(this, guiSettings.getCallConfigurationList());
        }

        callDialog.setVisible(true);
        if (!callDialog.isOk()) {
            return;
        }

        // the rest is the same as if the default call was pressed
        this.leafCallDefaultOnSelected(selected);
    }//GEN-LAST:event_leafCallGeneralActionPerformed

    private void leafFilterDefaultActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafFilterDefaultActionPerformed
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected == null) {
            return;
        }
        leafFilterDefaultOnSelected(selected);
    }//GEN-LAST:event_leafFilterDefaultActionPerformed

    private void leafFilterGeneralActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafFilterGeneralActionPerformed
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected == null) {
            return;
        }

        if (filterDialog == null) {
            filterDialog = new FilterDialog(this, guiSettings.getFilterConfigurationList());
        }

        showFilterDialog();
        if (!filterDialog.isOk()) {
            return;
        }

        // from here, need to apply the filtering to the selected files
        // this is implemented via the "Default" filter approach
        leafFilterDefaultOnSelected(selected);
    }//GEN-LAST:event_leafFilterGeneralActionPerformed

    private void leafExecuteGeneralActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafExecuteGeneralActionPerformed
        TreePath selected = scriptsTreePanel.getTree().getLeadSelectionPath();
        if (selected == null) {
            return;
        }

        BamfoTreeNode node = (BamfoTreeNode) selected.getLastPathComponent();
        if (node.getNodeFile().isFile()) {
            File scriptfile = node.getNodeFile();
            if (scriptDialog == null) {
                scriptDialog = new ScriptDialog(this, false, scriptfile);
            } else {
                scriptDialog.setScript(scriptfile);
            }
            scriptDialog.setVisible(true);
        }
    }//GEN-LAST:event_leafExecuteGeneralActionPerformed

    private void leafFileCopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafFileCopyActionPerformed
        // replace the contents of the temporary store with the new selection
        copyTemp.clear();

        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected.length < 1) {
            return;
        }

        // if reached here, really remember the nodes to copy
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            BamfoTreeNode newnode = new BamfoTreeNode(node);
            newnode.populate(guiSettings.getOmitExtensionsArray());
            copyTemp.add(newnode);
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(newnode.getNodeFile().getAbsolutePath());
        }
        // put the names of the files in the clipboard
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
    }//GEN-LAST:event_leafFileCopyActionPerformed

    private void leafFilePasteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafFilePasteActionPerformed
        StringBuilder message = new StringBuilder();
        int cts = copyTemp.size();
        message.append("Really copy ").append(cts).append(" file");
        if (cts > 1) {
            message.append("s");
        }
        message.append("\n\n");
        for (int i = 0; i < 3 && i < cts; i++) {
            message.append(copyTemp.get(i).getNodeFile().getAbsolutePath()).append("\n");
        }
        message.append("\nto directory\n\n");

        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected.length < 1) {
            return;
        }

        // if reached here, really change the executable status of items        
        BamfoTreeNode targetnode = (BamfoTreeNode) selected[0].getLastPathComponent();
        File target = targetnode.getNodeFile();

        message.append(target.getAbsolutePath()).append("\n");

        int confirm = JOptionPane.showConfirmDialog((Component) null, message.toString(),
                "Copy Files", JOptionPane.OK_CANCEL_OPTION);

        // abort if user aborts
        if (confirm == JOptionPane.CANCEL_OPTION) {
            return;
        }

        for (int i = 0; i < cts; i++) {
            // deal with case of over-writing            
            try {
                BamfoFileCopy(copyTemp.get(i), targetnode.getNodeFile());
            } catch (Exception ex) {
                System.out.println("Something went wrong during file copy: "
                        + copyTemp.get(i).getNodeFile().getAbsolutePath());
                System.out.println(ex.getMessage());
            }
        }

        // update the tree model and refresh the tree        
        filesTreePanel.rePopulate(targetnode.getNodeFile().getParent());
        scriptsTreePanel.rePopulate(targetnode.getNodeFile().getParent());
    }//GEN-LAST:event_leafFilePasteActionPerformed

    private void leafRenameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafRenameActionPerformed
        // when files get renames, the trees will have to be reloaded. 
        // The hashmap will keep track of which directories should be reloaded.
        HashMap<String, Boolean> updateProjects = new HashMap<>();
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        // if reached here, really try to rename        
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            File nodefile = node.getNodeFile();

            // ask the user for a new name
            String newname = (String) JOptionPane.showInputDialog(rootPane,
                    "Rename " + nodefile.getName() + " to:",
                    "Rename",
                    JOptionPane.QUESTION_MESSAGE,
                    null, null,
                    nodefile.getName());

            if (newname != null) {
                nodefile.renameTo(new File(nodefile.getParent(), newname));
                updateProjects.put(nodefile.getParent(), true);
            }

        }

        // after renaming, iterate the hashmap to update the projects
        // update means remove and add
        if (!updateProjects.isEmpty()) {
            for (String key : updateProjects.keySet()) {
                filesTreePanel.rePopulate(key);
                scriptsTreePanel.rePopulate(key);
            }
            filesTreePanel.refresh();
            scriptsTreePanel.refresh();
        }
    }//GEN-LAST:event_leafRenameActionPerformed

    private void leafDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafDeleteActionPerformed
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected == null) {
            return;
        }
        int numselected = selected.length;
        if (numselected < 1) {
            return;
        }

        // ask the user to make sure files should be deleted
        String message = numselected + " selected file";
        if (numselected > 1) {
            message += "s";
        }
        message += " will be deleted!";
        int confirm = JOptionPane.showConfirmDialog((Component) null, message,
                "Delete", JOptionPane.OK_CANCEL_OPTION);

        // abort if user aborts
        if (confirm == JOptionPane.CANCEL_OPTION) {
            return;
        }

        // if reached here, really delete the files
        // delete the files from the base model stored in GUISettings. Then filter
        // the panels again to make the changes apparent in the panels
        for (int i = 0; i < numselected; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            // delete from the tree model                   
            File nodeparent = node.getNodeFile().getParentFile();
            deleteFileOrDir(node.getNodeFile());
            filesTreePanel.rePopulate(nodeparent.getAbsolutePath());
            scriptsTreePanel.rePopulate(nodeparent.getAbsolutePath());
        }
    }//GEN-LAST:event_leafDeleteActionPerformed

    private void leafExecutableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leafExecutableActionPerformed
        TreePath[] selected = activeTreePanel.getTree().getSelectionPaths();
        if (selected.length < 1) {
            return;
        }

        // if reached here, really change the executable status of items
        for (int i = 0; i < selected.length; i++) {
            BamfoTreeNode node = (BamfoTreeNode) selected[i].getLastPathComponent();
            File nodefile = node.getNodeFile();
            if (nodefile.isFile()) {
                try {
                    nodefile.setExecutable(!nodefile.canExecute());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "An error occurred while changing permission for file:\n" + nodefile.getAbsolutePath(), "Permission Change Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        // make sure the changes become visible immediately
        filesTreePanel.refresh();
        scriptsTreePanel.refresh();
    }//GEN-LAST:event_leafExecutableActionPerformed

    /**
     * custom code used in most (all?) mouse listeners for the popup menus
     *
     * @param evt
     * @return
     *
     * true if event is a single left mouse click. As a side effect, the
     * function also hides the popup menu.
     *
     *
     */
    private boolean acceptMouseClickFromPopup(MouseEvent evt) {
        projectsPopup.setVisible(false);
        if (evt == null) {
            return true;
        }
        if (evt.getButton() == MouseEvent.BUTTON1 && evt.getClickCount() <= 1) {
            return true;
        }
        return false;
    }

    /**
     * Copies the files represented by source into target. Work recursively on
     * directories. Uses Common-IO library.
     *
     * @param source
     * @param target
     */
    private void BamfoFileCopy(BamfoTreeNode source, File targetdir) throws IOException {

        if (source == null) {
            return;
        }

        File sourcefile = source.getNodeFile();
        File destinationfile = new File(targetdir, sourcefile.getName());

        // avoid over-writing a file with itself.
        if (sourcefile.getAbsolutePath().equals(destinationfile.getAbsolutePath())) {
            return;
        }

        // ask before over-writing
        if (destinationfile.exists()) {
            String newname = (String) JOptionPane.showInputDialog(rootPane,
                    "Destination file " + sourcefile.getName() + " already exists. Rename new file as:",
                    "Rename or overwrite",
                    JOptionPane.QUESTION_MESSAGE,
                    null, null,
                    sourcefile.getName());

            if (newname == null) {
                return;
            }
            destinationfile = new File(targetdir, newname);
        }

        if (sourcefile.isDirectory()) {
            FileUtils.copyDirectory(sourcefile, destinationfile, true);
        } else {
            FileUtils.copyFile(sourcefile, destinationfile, true);
        }
    }

    /**
     * Toggle the selected tab in the tabbed pane.
     *
     * @param n
     *
     * Use +-1 only. Other values not tested.
     *
     */
    private void switchView(int n) {

        int nowselected = fileViewTab.getSelectedIndex();
        // ignore requests when there are no tabs selected
        if (nowselected < 0) {
            return;
        }
        int numtabs = fileViewTab.getTabCount();

        // change the selected tab. But be careful to rotate first to last, etc.
        nowselected += n;
        if (nowselected >= numtabs) {
            nowselected = 0;
        }
        if (nowselected < 0) {
            nowselected = numtabs - 1;
        }
        // actually change the focus of the tabbed pane
        fileViewTab.setSelectedIndex(nowselected);
    }

    /**
     * Consider that a task is expected to generate a file outputfile. At the
     * end of the task, the panels should be updated. This function makes a
     * runnable that can do that.
     *
     *
     * @param outputfile
     * @return
     */
    private Runnable makeRefreshRunnable(final File outputfile) {
        return new Runnable() {
            @Override
            public void run() {
                filesTreePanel.rePopulate(outputfile.getParent());
                scriptsTreePanel.rePopulate(outputfile.getParent());
                filesTreePanel.refresh();
                scriptsTreePanel.refresh();
            }
        };
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        if (args == null || args.length == 0) {
            return;
        }

        try {
            // Set System L&F
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            //UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(BamfoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(BamfoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(BamfoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(BamfoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        /*
         * Create tand display the form
         */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new BamfoGUI().setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton OptionsCancelButton;
    private javax.swing.JButton OptionsHomeDirBrowseButton;
    private javax.swing.JButton OptionsOkButton;
    private javax.swing.JTree dataFileTree;
    private javax.swing.JSpinner dataRowsSpinner;
    private javax.swing.JTabbedPane fileViewTab;
    private javax.swing.JSplitPane filesSplitPane;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JMenuItem leafCallDefault;
    private javax.swing.JMenuItem leafCallGeneral;
    private javax.swing.JMenuItem leafCloseProject;
    private javax.swing.JMenuItem leafDelete;
    private javax.swing.JCheckBoxMenuItem leafExecutable;
    private javax.swing.JMenuItem leafExecuteGeneral;
    private javax.swing.JMenuItem leafFileCopy;
    private javax.swing.JMenuItem leafFilePaste;
    private javax.swing.JMenuItem leafFilterDefault;
    private javax.swing.JMenuItem leafFilterGeneral;
    private javax.swing.JMenuItem leafNewFolder;
    private javax.swing.JMenuItem leafRefresh;
    private javax.swing.JMenuItem leafRename;
    private javax.swing.JMenuItem leafView;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JMenu menuFile;
    private javax.swing.JMenuItem menuFileClose;
    private javax.swing.JMenuItem menuFileImport;
    private javax.swing.JMenuItem menuFileNewScratch;
    private javax.swing.JMenuItem menuFileNewScript;
    private javax.swing.JMenuItem menuFileOpen;
    private javax.swing.JMenuItem menuFileQuit;
    private javax.swing.JMenuItem menuFileSave;
    private javax.swing.JMenuItem menuFileSaveAs;
    private javax.swing.JMenu menuHelp;
    private javax.swing.JMenuItem menuHelpAbout;
    private javax.swing.JMenu menuTools;
    private javax.swing.JMenuItem menuToolsCalling;
    private javax.swing.JMenuItem menuToolsFiltering;
    private javax.swing.JMenuItem menuToolsOptions;
    private javax.swing.JMenu menuView;
    private javax.swing.JMenuItem menuViewClose;
    private javax.swing.JMenuItem menuViewNext;
    private javax.swing.JMenuItem menuViewPrevious;
    private javax.swing.JTextField omitExtTextField;
    private javax.swing.JDialog optionsDialog;
    private javax.swing.JSpinner optionsFolderTimerSpinner;
    private javax.swing.JTextField optionsHomeDirTextField;
    private javax.swing.JSpinner optionsScriptTimerSpinner;
    private javax.swing.JSpinner optionsThreadSpinner;
    private javax.swing.JPopupMenu projectsPopup;
    private javax.swing.JScrollPane projectsScrollPane;
    private javax.swing.JTree scriptFileTree;
    private javax.swing.JPopupMenu.Separator sepAfterExec;
    private javax.swing.JPopupMenu.Separator sepAfterVariants;
    private javax.swing.JPopupMenu.Separator sepAfterView;
    private javax.swing.JRadioButton separateRadio;
    private javax.swing.JRadioButton togetherRadio;
    private javax.swing.ButtonGroup togetherseparateGroup;
    // End of variables declaration//GEN-END:variables
}
