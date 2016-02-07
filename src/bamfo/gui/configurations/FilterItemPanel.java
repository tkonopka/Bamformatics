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
import bamfo.gui.utils.BamfoMiscFile;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.EventObject;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * Warning: The Panel only deals with string in the key threshold box.
 *
 *
 * @author tomasz
 */
class FilterItemPanel extends javax.swing.JPanel
        implements TableCellRenderer, TableCellEditor {

    volatile OneFilter oneFilter;

    /**
     * Creates new form FilterItemPanel
     */
    public FilterItemPanel() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content oneFilter this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        BedOrKeyGroup = new javax.swing.ButtonGroup();
        filterNameTextfield = new javax.swing.JTextField();
        nameLabel = new javax.swing.JLabel();
        bedRadio = new javax.swing.JRadioButton();
        keyRadio = new javax.swing.JRadioButton();
        bedTextField = new javax.swing.JTextField();
        keyTextField = new javax.swing.JTextField();
        relationCombobox = new javax.swing.JComboBox();
        bedBrowseButton = new javax.swing.JButton();
        thresholdTextField = new javax.swing.JTextField();

        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
        setMaximumSize(new java.awt.Dimension(20, 80));
        setMinimumSize(new java.awt.Dimension(20, 80));
        setPreferredSize(new java.awt.Dimension(20, 80));

        filterNameTextfield.setPreferredSize(new java.awt.Dimension(10, 24));

        nameLabel.setText("Name:");

        bedRadio.setBackground(new java.awt.Color(255, 255, 255));
        BedOrKeyGroup.add(bedRadio);
        bedRadio.setText("Region");
        bedRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bedRadioActionPerformed(evt);
            }
        });

        keyRadio.setBackground(new java.awt.Color(255, 255, 255));
        BedOrKeyGroup.add(keyRadio);
        keyRadio.setText("Key");
        keyRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keyRadioActionPerformed(evt);
            }
        });

        bedTextField.setEditable(false);
        bedTextField.setPreferredSize(new java.awt.Dimension(10, 24));

        keyTextField.setPreferredSize(new java.awt.Dimension(10, 24));

        relationCombobox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "<", "<=", "=", ">", ">=" }));
        relationCombobox.setPreferredSize(new java.awt.Dimension(53, 24));
        relationCombobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keyRelationHandler(evt);
            }
        });

        bedBrowseButton.setText("...");
        bedBrowseButton.setPreferredSize(new java.awt.Dimension(24, 24));
        bedBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bedBrowseButtonActionPerformed(evt);
            }
        });

        thresholdTextField.setPreferredSize(new java.awt.Dimension(10, 24));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(nameLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(filterNameTextfield, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bedRadio)
                    .addComponent(keyRadio))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(keyTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(relationCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(thresholdTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 123, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(bedTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bedBrowseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bedRadio)
                            .addComponent(bedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bedBrowseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(keyRadio)
                            .addComponent(keyTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(relationCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(thresholdTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(28, 28, 28)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(filterNameTextfield, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(nameLabel))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bedRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bedRadioActionPerformed
        selectBed(bedRadio.isSelected());
    }//GEN-LAST:event_bedRadioActionPerformed

    private void keyRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keyRadioActionPerformed
        selectBed(bedRadio.isSelected());
    }//GEN-LAST:event_keyRadioActionPerformed

    private void keyRelationHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keyRelationHandler
        //updateThisFilter();
    }//GEN-LAST:event_keyRelationHandler

    private void bedBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bedBrowseButtonActionPerformed

        // try to figure out current bed file directory
        File nowdir = null;
        try {
            nowdir = new File(bedTextField.getText()).getParentFile();            
        } catch (Exception ex) {            
            nowdir = null;
        }

        File newbed;
        if (nowdir == null) {
            newbed = BamfoMiscFile.chooseFile();
        } else {
            newbed = BamfoMiscFile.chooseFile(nowdir);
        }
        
        if (newbed != null) {
            bedTextField.setText(newbed.getAbsolutePath());
            updateThisFilter();            
        }
    }//GEN-LAST:event_bedBrowseButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup BedOrKeyGroup;
    private javax.swing.JButton bedBrowseButton;
    private javax.swing.JRadioButton bedRadio;
    private javax.swing.JTextField bedTextField;
    private javax.swing.JTextField filterNameTextfield;
    private javax.swing.JRadioButton keyRadio;
    private javax.swing.JTextField keyTextField;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JComboBox relationCombobox;
    private javax.swing.JTextField thresholdTextField;
    // End of variables declaration//GEN-END:variables

    void selectBed(boolean bed) {
        bedRadio.setSelected(bed);
        bedTextField.setEnabled(bed);
        bedBrowseButton.setEnabled(bed);
        keyRadio.setSelected(!bed);
        keyTextField.setEnabled(!bed);
        relationCombobox.setEnabled(!bed);
        thresholdTextField.setEnabled(!bed);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        oneFilter = (OneFilter) value;
        filterNameTextfield.setText(oneFilter.getFiltername());
        if (oneFilter.getBedfile() == null) {
            selectBed(false);
            setBedpath("");
            setKey(oneFilter.getKey());
            setThreshold(oneFilter.getKeyThresholdString());
            setRelation(oneFilter.getKeyRelation());
        } else {
            selectBed(true);
            setBedpath(oneFilter.getBedfile().getAbsolutePath());
        }
        return this;
    }

    // below are functions that will be used to interface between the form/panel
    // and renderers and editors
    void setFilterName(String filtername) {
        filterNameTextfield.setText(filtername);
    }

    String getFilterName() {
        return filterNameTextfield.getText();
    }

    String getKey() {
        return keyTextField.getText();
    }

    void setKey(String key) {
        if (key == null) {
            keyTextField.setText("");
        } else {
            keyTextField.setText(key);
        }
    }

    String getThreshold() {
        return thresholdTextField.getText();
    }

    void setThreshold(String threshold) {
        if (threshold != null) {
            thresholdTextField.setText(threshold);
        } else {
            thresholdTextField.setText("");
        }
    }

    OneFilter.Relation getRelation() {
        String relationString = (String) relationCombobox.getSelectedItem();
        if (relationString.equalsIgnoreCase(">")) {
            return OneFilter.Relation.Greater;
        } else if (relationString.equalsIgnoreCase(">=")) {
            return OneFilter.Relation.GreaterOrEqual;
        }
        if (relationString.equalsIgnoreCase("=")) {
            return OneFilter.Relation.Equal;
        }
        if (relationString.equalsIgnoreCase("<")) {
            return OneFilter.Relation.Less;
        }
        if (relationString.equalsIgnoreCase("<=")) {
            return OneFilter.Relation.LessOrEqual;
        } else {
            return null;
        }

    }

    void setRelation(OneFilter.Relation relation) {
        if (relation == null) {
            return;
        }
        String relationString = "";
        switch (relation) {
            case Greater:
                relationString = ">";
                break;
            case GreaterOrEqual:
                relationString = ">=";
                break;
            case Less:
                relationString = "<";
                break;
            case LessOrEqual:
                relationString = "<=";
                break;
            case Equal:
                relationString = "=";
                break;
            default:
                System.out.println("setRelation in default?");
                break;
        }
        relationCombobox.setSelectedItem(relationString);
    }

    void setBedpath(File bedpath) {
        if (bedpath == null) {
            bedTextField.setText("");
        } else {
            bedTextField.setText(bedpath.getAbsolutePath());
        }
    }

    void setBedpath(String bedpath) {
        if (bedpath == null) {
            bedTextField.setText("");
        } else {
            bedTextField.setText(bedpath);
        }
    }

    String getBedpath() {
        return bedTextField.getText();
    }

    void setBedRadio(boolean bybed) {
        bedRadio.setSelected(bybed);
        keyRadio.setSelected(!bybed);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        oneFilter = (OneFilter) value;
        selectBed(oneFilter.getBedfile() != null);
        setFilterName(oneFilter.getFiltername());
        setKey(oneFilter.getKey());
        setThreshold(oneFilter.getKeyThresholdString());
        setBedpath(oneFilter.getBedfile());
        // explicitly set the radio button        
        setRelation(oneFilter.getKeyRelation());
        return this;
    }

    @Override
    public Object getCellEditorValue() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
        return true;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return true;
    }

    /**
     * Function that uses values in the GUI components to change the information
     * saved in the OneFilter that they represent.
     *
     */
    private synchronized void updateThisFilter() {
        oneFilter.setFiltername(filterNameTextfield.getText());
        String temp;
        if (bedRadio.isSelected()) {
            temp = bedTextField.getText();
            if (temp.length() > 0) {
                oneFilter.setBedfile(new File(temp));
            }
            oneFilter.setKey(null);
            oneFilter.setKeyThresholdString(null);
            oneFilter.setKeyRelation(null);
        } else {
            oneFilter.setBedfile(null);
            temp = keyTextField.getText();
            if (temp.length() > 2) {
                temp = temp.substring(0, 2);
            }
            oneFilter.setKey(temp);

            temp = thresholdTextField.getText();
            oneFilter.setKeyThresholdString(temp);
            oneFilter.setKeyThresholdDouble(null);

            oneFilter.setKeyRelation(getRelation());
        }
    }

    @Override
    public boolean stopCellEditing() {
        updateThisFilter();
        return true;
    }

    @Override
    public void cancelCellEditing() {
    }

    @Override
    public void addCellEditorListener(CellEditorListener l) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }
}