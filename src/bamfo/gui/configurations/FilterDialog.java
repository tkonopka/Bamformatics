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

import bamfo.call.BamfoVcfFilter;
import bamfo.call.OneFilter;
import bamfo.gui.utils.BamfoRunnableTask;
import bamfo.utils.NumberChecker;
import java.awt.Dimension;
import java.io.File;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import jsequtils.file.FileExtensionGetter;

/**
 * A dialog that allows a user to see/change/create/remove configurations for
 * variant filtering.
 *
 * It has FilterConfigurationList that stores all available configurations. It
 * also has a separate filterconfiguration object that holds the config that is
 * being edited/changed. This config is separate from the list until it is
 * "saved."
 *
 *
 *
 * @author tomasz
 */
public class FilterDialog extends javax.swing.JDialog {

    FilterTableModel filterTableModel = new FilterTableModel();
    private boolean ok = false;
    private final FilterConfigurationList filterConfigurations;
    private String currentConfig = null;

    /**
     * Creates new form FilterDialog
     */
    public FilterDialog(java.awt.Frame parent, FilterConfigurationList filterConfigurations) {
        super(parent, true);
        initComponents();
        this.setLocationRelativeTo(null);

        // make the table editable through special looking panels
        filterTable.setDefaultRenderer(OneFilter.class, new FilterItemPanel());
        filterTable.setDefaultEditor(OneFilter.class, new FilterItemPanel());

        filterTable.setRowHeight(80);
        filterTable.getTableHeader().setVisible(false);
        filterTable.getTableHeader().setPreferredSize(new Dimension(-1, 0));
        filterTable.setModel(filterTableModel);

        this.filterConfigurations = filterConfigurations;

        // fill the combo box with configurations passed in the constructor        
        makeComboList();
        // set the default configuration to the one used last
        configCombo.setSelectedItem((String) filterConfigurations.getLastConfigurationName());
    }

    /**
     * check if the user has changed a configuration in the GUI.
     *
     * @return
     *
     * true if the config saved in the list is different from the one displayed
     * in the table.
     *
     * false if they are the same, i.e. nothing has been changed.
     *
     */
    private boolean configChanged() {
        // after a removal, fc may be null. Report that as it hasn't changed.
        if (currentConfig == null) {         
            return false;
        }        

        FilterConfiguration fc = filterConfigurations.get(currentConfig);

        // check the suffix
        if (!fc.getSuffix().equals(outSuffixTextfield.getText())) {      
            return true;
        }        

        // check that the number of filters is the same              
        int rowcount = filterTableModel.getRowCount();
        if (fc.getNumFilters() != rowcount) {         
            return true;
        }        

        // then check the individual filters
        for (int i = 0; i < rowcount; i++) {
            OneFilter guifilter = (OneFilter) filterTableModel.getValueAt(i, 0);
            OneFilter savedfilter = fc.getFilterAt(i);            
            if (!equalFilters(guifilter, savedfilter)) {                
                return true;
            }
        }

        return false;
    }

    /**
     *
     * @param f1
     * @param f2
     * @return
     *
     * true if the two filters have the same settings
     *
     */
    private boolean equalFilters(OneFilter f1, OneFilter f2) {

        if (!equalsNull(f1.getFiltername(), f2.getFiltername())) {
            return false;
        }
        if (!equalsNull(f1.getKey(), f2.getKey())) {
            return false;
        }
        if (!equalsNull(f1.getKeyThresholdString(), f2.getKeyThresholdString())) {
            return false;
        }
        if (f1.getKeyRelation() != f2.getKeyRelation()) {
            return false;
        }
        if (!equalsNull(f1.getBedfile(), f2.getBedfile())) {
            return false;
        }
        if (!equalsNull(f1.getKeyThresholdDouble(), f2.getKeyThresholdDouble())) {
            return false;
        }
        return true;
    }

    /**
     * A wrapper for String.equals that does not break down when either string
     * is null.
     *
     * @param s1
     * @param s2
     * @return
     */
    private boolean equalsNull(String s1, String s2) {
        if (s1 == null) {
            if (s2 == null) {
                return true;
            } else {
                return false;
            }
        } else {
            if (s2 == null) {
                return false;
            } else {
                return (s1.equals(s2));
            }
        }
    }

    /**
     * comparison of two files but their paths. This function does not break
     * down when the files are null.
     *
     * @param f1
     * @param f2
     * @return
     */
    private boolean equalsNull(File f1, File f2) {
        if (f1 == null) {
            if (f2 == null) {
                return true;
            } else {
                return false;
            }
        } else {
            if (f2 == null) {
                return false;
            } else {
                return equalsNull(f1.getAbsolutePath(), f2.getAbsolutePath());
            }
        }
    }

    /**
     *
     * @param d1
     * @param d2
     * @return
     */
    private boolean equalsNull(Double d1, Double d2) {
        if (d1 == null) {
            if (d2 == null) {
                return true;
            } else {
                return false;
            }
        } else {
            if (d2 == null) {
                return false;
            } else {
                return d1.equals(d2);
            }
        }
    }

    private void selectConfig(String name) {

        // remember the name of new configuration in a class variable
        currentConfig = name;
        filterTableModel.removeAll();

        if (name != null) {
            // deal with aesthetics            
            filterTable.setEnabled(true);
            outSuffixTextfield.setEnabled(true);
            addFilterButton.setEnabled(true);
            removeFilterButton.setEnabled(true);
            applyButton.setEnabled(true);
            // add the filters that are selected
            FilterConfiguration hereconfig = filterConfigurations.get(name);
            for (int i = 0; i < hereconfig.getNumFilters(); i++) {
                filterTableModel.addElement(hereconfig.getFilterAt(i).copy());
            }
            outSuffixTextfield.setText(hereconfig.getSuffix());
        } else {
            // deal with aesthetics
            filterTable.setEnabled(false);
            outSuffixTextfield.setEnabled(false);
            addFilterButton.setEnabled(false);
            removeFilterButton.setEnabled(false);
            applyButton.setEnabled(false);
            outSuffixTextfield.setText("");
        }

    }

    /**
     * Use this to query whether user pressed ok or cancel the last time the
     * user saw the dialog.
     *
     * @return
     */
    public boolean isOk() {
        return ok;
    }

    public String getCurrentConfig() {
        return currentConfig;
    }

    /**
     * create a runnable object based on the selected configuration.
     *
     * @param f
     * @return
     */
    public BamfoRunnableTask makeRunnable(File f, JTextArea outarea) {
        String last = filterConfigurations.getLastConfigurationName();
        FilterConfiguration fc = filterConfigurations.get(last);

        // if there was no last configuration, abort
        if (fc == null) {
            return null;
        }

        // make a defensive copy of the suffix and filter list
        String nowsuffix = fc.getSuffix();
        ArrayList<OneFilter> shallowcopy = fc.getFilterList();
        ArrayList<OneFilter> deepcopy = new ArrayList<OneFilter>(1 + shallowcopy.size());
        for (int i = 0; i < shallowcopy.size(); i++) {
            //System.out.println("making copy of " + i);
            OneFilter temp = shallowcopy.get(i);
            if (NumberChecker.isDouble(temp.getKeyThresholdString())) {
                String s = temp.getKeyThresholdString();
                temp.setKeyThresholdString(null);
                temp.setKeyThresholdDouble(new Double(Double.parseDouble(s)));
            }
            OneFilter tempcopy = new OneFilter(temp);
            if (tempcopy.isValid()) {
                deepcopy.add(tempcopy);
            }
        }

        //System.out.println("got deep copy");

        File fdir = f.getParentFile();
        //String[] fsplit = FileNameExtensionSplitter.split(f.getName());
        String[] fsplit = FileExtensionGetter.getExtensionSplit(f);

        String outtemp = fsplit[0] + nowsuffix;
        if (!fsplit[1].isEmpty()) {
            outtemp += "." + fsplit[1];
        }
        if (!fsplit[2].isEmpty()) {
            outtemp += "." + fsplit[2];
        }
        String output = new File(fdir, outtemp).getAbsolutePath();

        // create the stream where the task will output messages
        PipedOutputStream os = new PipedOutputStream();
        PrintStream pos = new PrintStream(os);
        
        return new VariantsRunnableTask(new BamfoVcfFilter(f, output, deepcopy, pos),
                os, outarea, "java -jar bamformatics.jar filtervariants",
                getCommandLineArguments(f, output, deepcopy));
    }

    /**
     * creates a string for a command line version of this task
     *
     * @param f
     *
     * input path
     *
     * @param output
     *
     * output path
     *
     * @param filters
     *
     * the filters that are to be applied
     *
     * @return
     */
    private String getCommandLineArguments(File f, String output, ArrayList<OneFilter> filters) {
        StringBuilder fakecmd = new StringBuilder();
        fakecmd.append("--vcf ").append(f.getAbsolutePath()).append("\n");
        fakecmd.append("--output ").append(output).append("\n");

        // get all the key/threshold filters
        for (int i = 0; i < filters.size(); i++) {
            OneFilter temp = filters.get(i);
            if (temp.getBedfile() == null) {
                fakecmd.append("--filter ").append(temp.getFiltername());
                fakecmd.append(" --key \"").append(temp.getKey());
                OneFilter.Relation temprelation = temp.getKeyRelation();
                if (temprelation == OneFilter.Relation.Equal) {
                    fakecmd.append("=");
                } else if (temprelation == OneFilter.Relation.Greater) {
                    fakecmd.append(">");
                } else if (temprelation == OneFilter.Relation.GreaterOrEqual) {
                    fakecmd.append(">=");
                } else if (temprelation == OneFilter.Relation.Less) {
                    fakecmd.append("<");
                } else if (temprelation == OneFilter.Relation.LessOrEqual) {
                    fakecmd.append("<=");
                }
                if (temp.getKeyThresholdString() == null) {
                    fakecmd.append(temp.getKeyThresholdDouble().doubleValue());
                } else {
                    fakecmd.append(temp.getKeyThresholdString());
                }
                fakecmd.append("\"");
                fakecmd.append("\n");
            }
        }

        // get all the bed file filters
        for (int i = 0; i < filters.size(); i++) {
            OneFilter temp = filters.get(i);
            if (temp.getBedfile() != null) {
                fakecmd.append("--filter ").append(temp.getFiltername());
                fakecmd.append(" --bed ").append(temp.getBedfile().getAbsolutePath());
                fakecmd.append("\n");
            }

        }


        return fakecmd.toString();
    }

    /**
     * This is a hack that makes sure the filter table stops editing a
     * configuration. Useful when switching between configurations or when
     * pressing OK before final changes are made.
     *
     */
    private void forceEditStop() {
        if (filterTable.isEditing()) {
            filterTable.editCellAt(-1, -1);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        outSuffixTextfield = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        deleteConfigButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        newConfigButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        addFilterButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        removeFilterButton = new javax.swing.JButton();
        configCombo = new javax.swing.JComboBox();
        filterOkButton = new javax.swing.JButton();
        filterCancelButton = new javax.swing.JButton();
        FilterScrollPane = new javax.swing.JScrollPane();
        filterTable = new javax.swing.JTable();
        applyButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Variant Filtering");
        setMinimumSize(new java.awt.Dimension(625, 494));
        setModal(true);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                windowActivatedListener(evt);
            }
        });

        jLabel3.setText("Output suffix:");

        deleteConfigButton.setText("Delete");
        deleteConfigButton.setMaximumSize(new java.awt.Dimension(90, 27));
        deleteConfigButton.setMinimumSize(new java.awt.Dimension(90, 27));
        deleteConfigButton.setPreferredSize(new java.awt.Dimension(90, 27));
        deleteConfigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteConfigButtonActionPerformed(evt);
            }
        });

        jLabel2.setText("Filters:");

        newConfigButton.setText("New...");
        newConfigButton.setMaximumSize(new java.awt.Dimension(90, 27));
        newConfigButton.setMinimumSize(new java.awt.Dimension(90, 27));
        newConfigButton.setPreferredSize(new java.awt.Dimension(90, 27));
        newConfigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newConfigButtonActionPerformed(evt);
            }
        });

        addFilterButton.setText("Add...");
        addFilterButton.setMaximumSize(new java.awt.Dimension(90, 27));
        addFilterButton.setMinimumSize(new java.awt.Dimension(90, 27));
        addFilterButton.setPreferredSize(new java.awt.Dimension(90, 27));
        addFilterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addFilterButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Configuration:");
        jLabel1.setMaximumSize(new java.awt.Dimension(120, 17));
        jLabel1.setMinimumSize(new java.awt.Dimension(120, 17));
        jLabel1.setPreferredSize(new java.awt.Dimension(120, 17));

        removeFilterButton.setText("Remove");
        removeFilterButton.setMaximumSize(new java.awt.Dimension(90, 27));
        removeFilterButton.setMinimumSize(new java.awt.Dimension(90, 27));
        removeFilterButton.setPreferredSize(new java.awt.Dimension(90, 27));
        removeFilterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeFilterButtonActionPerformed(evt);
            }
        });

        configCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changeConfigHandler(evt);
            }
        });

        filterOkButton.setText("Ok");
        filterOkButton.setMaximumSize(new java.awt.Dimension(90, 27));
        filterOkButton.setMinimumSize(new java.awt.Dimension(90, 27));
        filterOkButton.setPreferredSize(new java.awt.Dimension(90, 27));
        filterOkButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterOkButtonActionPerformed(evt);
            }
        });

        filterCancelButton.setText("Cancel");
        filterCancelButton.setMaximumSize(new java.awt.Dimension(90, 27));
        filterCancelButton.setMinimumSize(new java.awt.Dimension(90, 27));
        filterCancelButton.setPreferredSize(new java.awt.Dimension(90, 27));
        filterCancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterCancelButtonActionPerformed(evt);
            }
        });

        filterTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        filterTable.setRowHeight(80);
        FilterScrollPane.setViewportView(filterTable);

        applyButton.setText("Apply");
        applyButton.setMaximumSize(new java.awt.Dimension(90, 27));
        applyButton.setMinimumSize(new java.awt.Dimension(90, 27));
        applyButton.setPreferredSize(new java.awt.Dimension(90, 27));
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(applyButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(filterOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(outSuffixTextfield, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(configCombo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(newConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addComponent(FilterScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 505, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(filterCancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(deleteConfigButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(removeFilterButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(addFilterButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 595, Short.MAX_VALUE)
                    .addGap(18, 18, 18)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(newConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(deleteConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(configCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(29, 29, 29)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(outSuffixTextfield, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addGap(18, 18, 18)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addFilterButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeFilterButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 247, Short.MAX_VALUE))
                    .addComponent(FilterScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filterOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(filterCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(applyButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(49, 49, 49)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 12, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(433, Short.MAX_VALUE)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Deletes a configuration for the combo box and from the private array
     *
     *
     * @param evt
     */
    private void deleteConfigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteConfigButtonActionPerformed

        // get the name of the configuration in the combo box
        int selindex = configCombo.getSelectedIndex();
        if (selindex < 0) {
            return;
        }
        String configname = (String) configCombo.getItemAt(selindex);

        // allow the user to cancel the operation
        int makesure = JOptionPane.showConfirmDialog(this, "Really delete configuration " + configname + "?",
                "Delete", JOptionPane.OK_CANCEL_OPTION);
        if (makesure == JOptionPane.CANCEL_OPTION) {
            return;
        }

        // finally, remove the configuration for real
        configCombo.removeItemAt(selindex);
        filterConfigurations.remove(configname);
        if (configCombo.getComponentCount() > 0) {
            selectConfig((String) configCombo.getSelectedItem());
        } else {
            selectConfig(null);
        }
    }//GEN-LAST:event_deleteConfigButtonActionPerformed

    private void makeComboList() {
        configCombo.removeAllItems();
        for (int i = 0; i < filterConfigurations.size(); i++) {
            configCombo.addItem(filterConfigurations.getName(i));
        }
    }

    /**
     * Creates a new item in the combo box and the private array
     *
     * @param evt
     */
    private void newConfigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newConfigButtonActionPerformed

        String newconfig = (String) JOptionPane.showInputDialog(this, "New Configuration", "New Configuration");
        if (newconfig == null) {
            return;
        }

        // check that this configuration does not exist
        if (filterConfigurations.has(newconfig)) {
            selectConfig(newconfig);
            return;
        }

        // create a new configuration
        filterConfigurations.set(new FilterConfiguration(newconfig));
        configCombo.addItem(newconfig);
        selectConfig(newconfig);
        configCombo.setSelectedItem(newconfig);
    }//GEN-LAST:event_newConfigButtonActionPerformed

    private void addFilterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addFilterButtonActionPerformed
        filterTableModel.addElement();
        refreshFilterTable();
    }//GEN-LAST:event_addFilterButtonActionPerformed

    /**
     * this is a fudge function. It asks the model for all the elements in the
     * table. It causes all the elements to be redranw.
     */
    private void refreshFilterTable() {
        int nn = filterTableModel.getRowCount();
        for (int i = 0; i < nn; i++) {
            filterTableModel.getValueAt(i, 0);
        }
    }

    private void removeFilterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeFilterButtonActionPerformed

        int[] selectedrows = filterTable.getSelectedRows();
        if (selectedrows.length == 0) {
            return;
        }

        if (selectedrows[0] >= 0) {
            filterTableModel.removeElement(selectedrows[0]);
        }
    }//GEN-LAST:event_removeFilterButtonActionPerformed

    private void filterOkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterOkButtonActionPerformed

        // should save this configuration before exiting
        applyButtonActionPerformed(null);

        // make this configuration appear as the last used
        filterConfigurations.get(currentConfig);

        // make sure the decision is visible to the user on the main form
        ok = true;

        // go back to the main form
        this.setVisible(false);
    }//GEN-LAST:event_filterOkButtonActionPerformed

    private void filterCancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterCancelButtonActionPerformed
        // restore settings to the previous state (stored in the filterConfigurations Array)
        configCombo.setSelectedItem(currentConfig);

        // make sure the main form sees the cancel decision
        ok = false;

        // go back to the main form
        this.setVisible(false);
    }//GEN-LAST:event_filterCancelButtonActionPerformed

    private void windowActivatedListener(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_windowActivatedListener
        // by default set ok to false 
        ok = false;
    }//GEN-LAST:event_windowActivatedListener

    private void changeConfigHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeConfigHandler

        if (currentConfig != null && currentConfig.equals((String) configCombo.getSelectedItem())) {
            return;
        }

        if (configChanged()) {
            int makesure = JOptionPane.showConfirmDialog(this, "Configuration has changed. Do you want to save changes?",
                    "Changed", JOptionPane.YES_NO_CANCEL_OPTION);
            if (makesure == JOptionPane.YES_OPTION) {
                applyButtonActionPerformed(null);
            } else if (makesure == JOptionPane.CANCEL_OPTION) {
                configCombo.setSelectedItem(currentConfig);
                return;
            } // other option is to change without saving                        
        }
        selectConfig((String) configCombo.getSelectedItem());
    }//GEN-LAST:event_changeConfigHandler

    private void applyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
        
        forceEditStop();

        FilterConfiguration fc = filterConfigurations.get(currentConfig);
        fc.clear();
        ArrayList<OneFilter> filters = filterTableModel.filters;
        for (int i = 0; i < filters.size(); i++) {
            fc.addFilter(i, filters.get(i));
        }
        fc.setSuffix(outSuffixTextfield.getText());
    }//GEN-LAST:event_applyButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane FilterScrollPane;
    private javax.swing.JButton addFilterButton;
    private javax.swing.JButton applyButton;
    private javax.swing.JComboBox configCombo;
    private javax.swing.JButton deleteConfigButton;
    private javax.swing.JButton filterCancelButton;
    private javax.swing.JButton filterOkButton;
    private javax.swing.JTable filterTable;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton newConfigButton;
    private javax.swing.JTextField outSuffixTextfield;
    private javax.swing.JButton removeFilterButton;
    // End of variables declaration//GEN-END:variables
}

/**
 * My data structure used for the filter table/list. It stores filters in a
 * ArrayList.
 */
class FilterTableModel extends AbstractTableModel {

    ArrayList<OneFilter> filters = new ArrayList<OneFilter>(4);

    public void addElement() {
        int rowcount = this.getRowCount();
        filters.add(new OneFilter("filter" + rowcount));
        this.fireTableDataChanged();
        this.fireTableStructureChanged();
    }

    public void addElement(OneFilter filter) {
        filters.add(filter);
        this.fireTableDataChanged();
        this.fireTableStructureChanged();
    }

    /**
     * removes a single item from the table, given by the index.
     *
     * @param index
     */
    public void removeElement(int index) {
        if (index >= 0 && index < filters.size()) {
            filters.remove(index);
        }
        this.fireTableDataChanged();
        this.fireTableStructureChanged();
    }

    /**
     * removes all filters from the model.
     */
    public void removeAll() {
        filters.clear();
        this.fireTableDataChanged();
        this.fireTableStructureChanged();
    }

    @Override
    public int getRowCount() {
        return filters.size();
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return filters.get(rowIndex);
    }

    /*
     * This function is specified in an example. I had code it to return a
     * certain class.
     *
     */
    @Override
    public Class getColumnClass(int c) {
        return OneFilter.class;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return true;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        filters.set(row, (OneFilter) value);
        this.fireTableRowsUpdated(row, col);
    }
}
