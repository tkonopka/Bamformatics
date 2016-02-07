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

import bamfo.call.BamfoAnnotateVariants;
import bamfo.call.BamfoVcf;
import bamfo.gui.utils.BamfoMiscFile;
import bamfo.gui.utils.BamfoRunnableTask;
import bamfo.utils.BamfoSettings;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.EventObject;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.CellEditorListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import jsequtils.file.FileExtensionGetter;

/**
 *
 * @author tomasz
 */
public class CallDialog extends javax.swing.JDialog {

// set row numbers for various options
    private final int ROW_GENOMEHEADER = 0;
    private final int ROW_GENOME = 1;
    private final int ROW_THRESHOLDS = 2;
    private final int ROW_BASEQUAL = 3;
    private final int ROW_MAPQUAL = 4;
    private final int ROW_DEPTH = 5;
    private final int ROW_PROPORTION = 6;
    private final int ROW_SCORE = 7;
    private final int ROW_STRANDBIAS = 8;
    private final int ROW_TRIMMING = 9;
    private final int ROW_READSTART = 10;
    private final int ROW_READEND = 11;
    private final int ROW_HOMPOL = 12;
    private final int ROW_BTAIL = 13;
    private final int ROW_NREF = 14;
    private final int ROW_ANNOTATIONS = 15;
    private final int ROW_ANNODB = 16;
    private final int ROW_REPLACEQUAL = 17;
    // other settings
    private boolean ok = false;
    private final CallConfigurationList callConfigurations;
    private String currentConfig = null;

    /**
     * class fills the table with labels and values, and specifies which
     * cells/rows are editable.
     */
    class CallSettingsTableModel extends DefaultTableModel {

        public CallSettingsTableModel() {
            // call the DefaultTableModel constructor with defaults
            super(new Object[][]{
                        {" Genome:", ""},
                        {"    genome:", ""},
                        {" Thresholds:", ""},
                        {"    base quality:", "+"},
                        {"    mapping quality:", "0"},
                        {"    depth:", "3"},
                        {"    proportion:", "0.05"},
                        {"    score:", "18"},
                        {"    strand bias:", "0.001"},
                        {" Trimming", ""},
                        {"    read start:", "5"},
                        {"    read end:", "5"},
                        {"    hom. pol. tails:", "1"},
                        {"    quality B tai:", "1"},
                        {"    Ns as Ref:", "0"},
                        {" Annotations", ""},
                        {"    database:", ""},
                        {"    replace qual:", "0"}
                    }, new String[]{"argument", "value"});
        }

        /**
         * all cells that are not labels are editable. The genome item will be
         * editable via the dialog box, not the renderer/editor.
         *
         * @param rowIndex
         * @param columnIndex
         * @return
         */
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (rowIndex == ROW_TRIMMING || rowIndex == ROW_GENOMEHEADER
                    || rowIndex == ROW_THRESHOLDS || rowIndex == ROW_ANNOTATIONS) {
                return false;
            }
            if (columnIndex == 0) {
                return false;
            }
            if (rowIndex == ROW_GENOME || rowIndex == ROW_ANNODB) {
                return false;
            }
            return true;
        }

        // below are a set of functions that 
        public String getGenome() {
            return (String) this.getValueAt(ROW_GENOME, 1);
        }

        public void setGenome(String genome) {
            this.setValueAt(genome, ROW_GENOME, 1);
        }

        public byte getBasequal() {
            Object temp = this.getValueAt(ROW_BASEQUAL, 1);
            if (temp.getClass() == String.class) {
                String s = (String) temp;
                return (byte) s.charAt(0);
            } else if (temp.getClass() == Character.class) {
                return (byte) ((Character) temp).charValue();
            } else {
                return 0;
            }
        }

        public void setBasequal(byte basequal) {
            String s = "" + (char) basequal;
            this.setValueAt(s, ROW_BASEQUAL, 1);
        }

        public int getMapqual() {
            String s = (String) this.getValueAt(ROW_MAPQUAL, 1);
            return Integer.parseInt(s);
        }

        public void setMapqual(int mapqual) {
            this.setValueAt("" + mapqual, ROW_MAPQUAL, 1);
        }

        public int getDepth() {
            String s = (String) this.getValueAt(ROW_DEPTH, 1);
            return Integer.parseInt(s);
        }

        public void setDepth(int depth) {
            this.setValueAt("" + depth, ROW_DEPTH, 1);
        }

        public double getProportion() {
            String s = (String) this.getValueAt(ROW_PROPORTION, 1);
            return Double.parseDouble(s);
        }

        public void setProportion(double proportion) {
            this.setValueAt("" + proportion, ROW_PROPORTION, 1);
        }

        public double getScore() {
            String s = (String) this.getValueAt(ROW_SCORE, 1);
            return Double.parseDouble(s);
        }

        public void setScore(double score) {
            this.setValueAt("" + score, ROW_SCORE, 1);
        }

        public double getStrandbias() {
            String s = (String) this.getValueAt(ROW_STRANDBIAS, 1);
            return Double.parseDouble(s);
        }

        public void setStrandbias(double strandbias) {
            this.setValueAt("" + strandbias, ROW_STRANDBIAS, 1);
        }

        public int getReadstart() {
            String s = (String) this.getValueAt(ROW_READSTART, 1);
            return Integer.parseInt(s);
        }

        public int getReadend() {
            String s = (String) this.getValueAt(ROW_READEND, 1);
            return Integer.parseInt(s);
        }

        public void setReadstart(int readstart) {
            this.setValueAt("" + readstart, ROW_READSTART, 1);
        }

        public void setReadend(int readend) {
            this.setValueAt("" + readend, ROW_READEND, 1);
        }

        public boolean getTrimpolyedge() {
            String s = (String) this.getValueAt(ROW_HOMPOL, 1);
            if (s.equals("1")) {
                return true;
            } else {
                return false;
            }
        }

        public boolean getTrimQB() {
            String s = (String) this.getValueAt(ROW_BTAIL, 1);
            if (s.equals("1")) {
                return true;
            } else {
                return false;
            }
        }

        public void setTrimpolyedge(boolean trim) {
            if (trim) {
                this.setValueAt("1", ROW_HOMPOL, 1);
            } else {
                this.setValueAt("0", ROW_HOMPOL, 1);
            }
        }

        public void setTrimQB(boolean trimQB) {
            if (trimQB) {
                this.setValueAt("1", ROW_BTAIL, 1);
            } else {
                this.setValueAt("0", ROW_BTAIL, 1);
            }
        }

        public boolean getNRef() {
            String s = (String) this.getValueAt(ROW_NREF, 1);
            if (s.equals("1")) {
                return true;
            } else {
                return false;
            }
        }
        
        public void setNRef(boolean NRef) {
            if (NRef) {
                this.setValueAt("1", ROW_NREF, 1);
            } else {
                this.setValueAt("0", ROW_NREF, 1);
            }
        }
        
        
        public String getAnnoDB() {
            return (String) this.getValueAt(ROW_ANNODB, 1);
        }

        public void setAnnoDB(String db) {
            this.setValueAt(db, ROW_ANNODB, 1);
        }

        public boolean getReplaceQual() {
            String s = (String) this.getValueAt(ROW_REPLACEQUAL, 1);
            if (s.equals("1")) {
                return true;
            } else {
                return false;
            }
        }

        public void setReplaceQual(boolean replace) {
            if (replace) {
                this.setValueAt("1", ROW_REPLACEQUAL, 1);
            } else {
                this.setValueAt("0", ROW_REPLACEQUAL, 1);
            }
        }
    }

    /**
     * Class that deals with how the user can interact with the table
     */
    class CallSettingsTableRendererEditor implements TableCellRenderer, TableCellEditor {

        // the components to display or use for editing
        JLabel rLabel = new JLabel();
        JSpinner byteSpinner = new JSpinner();
        JSpinner intSpinner = new JSpinner();
        JSpinner doubleSpinner = new JSpinner();
        JCheckBox boolCheck = new JCheckBox();
        private Object currentValue;
        private int currentRow = 0;
        // keep track of last item that was edited
        private int TYPE_BYTE = 0;
        private int TYPE_INT = 1;
        private int TYPE_DOUBLE = 2;
        private int TYPE_BOOLEAN = 3;
        int nowediting = -1;
        private final DefaultTableModel model;

        /**
         * constructor required to initialize the components (e.g. set min/max
         * for spinners)
         */
        public CallSettingsTableRendererEditor(DefaultTableModel model) {
            super();
            this.model = model;
            // initialize the spinners
            // one for bytes/characters, one for integers, one for doubles

            Character[] temp = new Character[94];
            for (int i = 0; i < 94; i++) {
                temp[i] = new Character((char) (i + 33));
            }

            byteSpinner.setModel(new SpinnerListModel(temp));
            intSpinner.setModel(new SpinnerNumberModel(Integer.valueOf(5), Integer.valueOf(0), null, Integer.valueOf(1)));
            doubleSpinner.setModel(new SpinnerNumberModel(Double.valueOf(18.0d), Double.valueOf(0.0d), null, Double.valueOf(0.1d)));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            String valstring = getValstring(value);
            rLabel.setText(valstring);

            if (row == ROW_GENOMEHEADER || row == ROW_THRESHOLDS || row == ROW_TRIMMING
                    || row == ROW_ANNOTATIONS) {
                rLabel.setOpaque(true);
                return rLabel;
            } else {
                rLabel.setOpaque(false);
            }

            if (column == 1) {

                if (row == ROW_BASEQUAL) {
                    byteSpinner.setValue((char) valstring.charAt(0));
                    return byteSpinner;
                } else if (row == ROW_MAPQUAL || row == ROW_READSTART
                        || row == ROW_READEND || row == ROW_DEPTH) {
                    intSpinner.setValue(Integer.parseInt(valstring));
                    return intSpinner;
                } else if (row == ROW_PROPORTION || row == ROW_SCORE || row == ROW_STRANDBIAS) {
                    doubleSpinner.setValue(Double.parseDouble(valstring));
                    return doubleSpinner;
                } else if (row == ROW_HOMPOL || row == ROW_BTAIL || row == ROW_REPLACEQUAL
                        || row==ROW_NREF) {
                    if (valstring.isEmpty() || valstring.equals("0")) {
                        boolCheck.setSelected(false);
                    } else {
                        boolCheck.setSelected(true);
                    }
                    return boolCheck;
                }

            }
            return rLabel;
        }

        /**
         * convert an object to a string (either empty or with something defined
         * by value)
         */
        private String getValstring(Object value) {
            if (value == null) {
                return "";
            } else {
                return (String) value;
            }
        }

        /**
         * This is very similar to getTableCellRendererComponent but this keeps
         * track of the original value and editing type for when editing
         * finishes or is canceled.
         *
         * @param table
         * @param value
         * @param isSelected
         * @param row
         * @param column
         * @return
         */
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentValue = value;
            currentRow = row;
            String valstring = getValstring(currentValue);

            if (column == 1) {

                if (row == ROW_BASEQUAL) {
                    nowediting = this.TYPE_BYTE;
                    byteSpinner.setValue((char) valstring.charAt(0));
                    return byteSpinner;
                } else if (row == ROW_MAPQUAL || row == ROW_READSTART
                        || row == ROW_READEND || row == ROW_DEPTH) {
                    nowediting = this.TYPE_INT;
                    intSpinner.setValue(Integer.parseInt(valstring));
                    return intSpinner;
                } else if (row == ROW_PROPORTION || row == ROW_SCORE || row == ROW_STRANDBIAS) {
                    nowediting = this.TYPE_DOUBLE;
                    doubleSpinner.setValue(Double.parseDouble(valstring));
                    return doubleSpinner;
                } else if (row == ROW_HOMPOL || row == ROW_BTAIL || row == ROW_REPLACEQUAL ||
                        row==ROW_NREF) {
                    nowediting = this.TYPE_BOOLEAN;
                    if (valstring.isEmpty() || valstring.equals("0")) {
                        boolCheck.setSelected(false);
                    } else {
                        boolCheck.setSelected(true);
                    }
                    return boolCheck;
                }

            }
            return new JLabel(valstring);
        }

        @Override
        public Object getCellEditorValue() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isCellEditable(EventObject anEvent) {
            JTable tt = (JTable) anEvent.getSource();
            if (((JTable) anEvent.getSource()).isEnabled()) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean shouldSelectCell(EventObject anEvent) {
            return true;
        }

        @Override
        public boolean stopCellEditing() {
            if (nowediting == this.TYPE_INT) {
                currentValue = ((Integer) intSpinner.getValue()).toString();
                model.setValueAt(currentValue, currentRow, 1);
            } else if (nowediting == this.TYPE_DOUBLE) {
                currentValue = ((Double) doubleSpinner.getValue()).toString();
                model.setValueAt(currentValue, currentRow, 1);
            } else if (nowediting == this.TYPE_BYTE) {
                currentValue = (byteSpinner.getValue());
                model.setValueAt(currentValue, currentRow, 1);
            } else if (nowediting == this.TYPE_BOOLEAN) {
                if (boolCheck.isSelected()) {
                    currentValue = "1";
                } else {
                    currentValue = "0";
                }
                model.setValueAt(currentValue, currentRow, 1);
            }
            return true;
        }

        @Override
        public void cancelCellEditing() {
        }

        @Override
        public void addCellEditorListener(CellEditorListener l) {
        }

        @Override
        public void removeCellEditorListener(CellEditorListener l) {
        }
    }

    public String getCurrentConfig() {
        return currentConfig;
    }

    public boolean isOk() {
        return ok;
    }

    /**
     * All this just so that when user click on the genome item, a file browser
     * pops up.
     *
     */
    class BrowseListener implements MouseListener {

        private final JTable table;
        private final int row;
        private final int column;

        public BrowseListener(JTable table, int row, int column) {
            this.table = table;
            this.row = row;
            this.column = column;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!table.isEnabled()) {
                return;
            }
            int rownumber = table.rowAtPoint(e.getPoint());
            int colnumber = table.columnAtPoint(e.getPoint());
            if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                if (colnumber == column && rownumber == row) {
                    File myfile = BamfoMiscFile.chooseFile();
                    if (myfile != null) {
                        DefaultTableModel model = (DefaultTableModel) table.getModel();
                        model.setValueAt(myfile.getAbsolutePath(), rownumber, colnumber);
                    }
                }
            }
            if (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() == 1) {
                if (colnumber == column && rownumber == row) {
                    int toclear = JOptionPane.showConfirmDialog(rootPane, "Do you want to clear the currently set value?", "Clear path", JOptionPane.YES_NO_OPTION);
                    if (toclear == JOptionPane.YES_OPTION) {
                        DefaultTableModel model = (DefaultTableModel) table.getModel();
                        model.setValueAt("", rownumber, colnumber);
                    }
                }
            }

        }

        @Override
        public void mousePressed(MouseEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void mouseExited(MouseEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    /**
     * Creates new form BamfoCallDialog
     */
    public CallDialog(java.awt.Frame parent, CallConfigurationList callConfigurations) {
        super(parent, true);
        initComponents();
        this.setLocationRelativeTo(null);

        this.callConfigurations = callConfigurations;

        // make the columns and header of the table look good
        CallSettingsTableModel model = new CallSettingsTableModel();
        callSettingsTable.setModel(model);
        callSettingsTable.setDefaultEditor(Object.class, new CallSettingsTableRendererEditor(model));
        callSettingsTable.setDefaultRenderer(Object.class, new CallSettingsTableRendererEditor(model));
        // mouse listeners so that the user can pick out files with a dialog
        callSettingsTable.addMouseListener(new BrowseListener(callSettingsTable, this.ROW_GENOME, 1));
        callSettingsTable.addMouseListener(new BrowseListener(callSettingsTable, this.ROW_ANNODB, 1));
        callSettingsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        callSettingsTable.getTableHeader().setVisible(false);
        callSettingsTable.getTableHeader().setPreferredSize(new Dimension(-1, 0));

        String temp = callConfigurations.getLastConfigurationName();

        // fill the combo box with names of calling configurations
        makeComboList();
        // set the default configuration to the one used last
        configCombo.setSelectedItem((String) callConfigurations.getLastConfigurationName());
    }

    private void makeComboList() {
        configCombo.removeAllItems();
        for (int i = 0; i < callConfigurations.size(); i++) {
            configCombo.addItem(callConfigurations.getName(i));
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

        jLabel1 = new javax.swing.JLabel();
        configCombo = new javax.swing.JComboBox();
        newConfigButton = new javax.swing.JButton();
        deleteConfigButton = new javax.swing.JButton();
        callOkButton = new javax.swing.JButton();
        callCancelButton = new javax.swing.JButton();
        applyButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        jLabel3 = new javax.swing.JLabel();
        outSuffixTextfield = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        callSettingsTable = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Variant Calling");
        setMinimumSize(new java.awt.Dimension(493, 582));

        jLabel1.setText("Configuration:");
        jLabel1.setMaximumSize(new java.awt.Dimension(120, 17));
        jLabel1.setMinimumSize(new java.awt.Dimension(120, 17));
        jLabel1.setPreferredSize(new java.awt.Dimension(120, 17));

        configCombo.setMinimumSize(new java.awt.Dimension(40, 26));
        configCombo.setPreferredSize(new java.awt.Dimension(40, 26));
        configCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configCombochangeConfigHandler(evt);
            }
        });

        newConfigButton.setText("New...");
        newConfigButton.setMaximumSize(new java.awt.Dimension(90, 27));
        newConfigButton.setMinimumSize(new java.awt.Dimension(90, 27));
        newConfigButton.setPreferredSize(new java.awt.Dimension(90, 26));
        newConfigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newConfigButtonActionPerformed(evt);
            }
        });

        deleteConfigButton.setText("Delete");
        deleteConfigButton.setMaximumSize(new java.awt.Dimension(90, 27));
        deleteConfigButton.setMinimumSize(new java.awt.Dimension(90, 27));
        deleteConfigButton.setPreferredSize(new java.awt.Dimension(90, 26));
        deleteConfigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteConfigButtonActionPerformed(evt);
            }
        });

        callOkButton.setText("Ok");
        callOkButton.setMaximumSize(new java.awt.Dimension(90, 27));
        callOkButton.setMinimumSize(new java.awt.Dimension(90, 27));
        callOkButton.setPreferredSize(new java.awt.Dimension(90, 26));
        callOkButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                callOkButtonActionPerformed(evt);
            }
        });

        callCancelButton.setText("Cancel");
        callCancelButton.setMaximumSize(new java.awt.Dimension(90, 27));
        callCancelButton.setMinimumSize(new java.awt.Dimension(90, 27));
        callCancelButton.setPreferredSize(new java.awt.Dimension(90, 26));
        callCancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                callCancelButtonActionPerformed(evt);
            }
        });

        applyButton.setText("Apply");
        applyButton.setMaximumSize(new java.awt.Dimension(90, 23));
        applyButton.setMinimumSize(new java.awt.Dimension(90, 23));
        applyButton.setPreferredSize(new java.awt.Dimension(90, 26));
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButtonActionPerformed(evt);
            }
        });

        jLabel3.setText("Output suffix:");
        jLabel3.setMaximumSize(new java.awt.Dimension(120, 17));
        jLabel3.setMinimumSize(new java.awt.Dimension(120, 17));
        jLabel3.setPreferredSize(new java.awt.Dimension(120, 17));

        outSuffixTextfield.setMinimumSize(new java.awt.Dimension(10, 26));

        callSettingsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {"Genome", ""},
                {"  genome", null},
                {"Thresholds:", null},
                {"  base quality:", null},
                {"  mapping quality:", null},
                {"  depth:", null},
                {"  proportion:", null},
                {"  score:", null},
                {"  strandbias", null},
                {"Trimming", null},
                {"  read start:", null},
                {"  read end::", null},
                {"  hom. pol. tails:", null},
                {"  quality B tai:", null},
                {"  Ns as ref", null},
                {"Annotations", null},
                {"  database:", null},
                {"  replacequal:", null}
            },
            new String [] {
                "Title 1", "Title 2"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        callSettingsTable.setMinimumSize(new java.awt.Dimension(30, 395));
        callSettingsTable.setPreferredSize(new java.awt.Dimension(150, 395));
        callSettingsTable.setRowHeight(22);
        callSettingsTable.setShowVerticalLines(false);
        jScrollPane1.setViewportView(callSettingsTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(outSuffixTextfield, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(applyButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(callOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(callCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(configCombo, 0, 145, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(newConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(configCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(deleteConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(newConfigButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 12, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(outSuffixTextfield, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 34, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(callCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(callOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(applyButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

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

        CallConfiguration cc = callConfigurations.get(currentConfig);
        BamfoSettings bs = cc.getSettings();
        CallSettingsTableModel model = (CallSettingsTableModel) callSettingsTable.getModel();

        // check the fieldds that are stored within the cc itself
        if (!cc.getSuffix().equals(outSuffixTextfield.getText())) {
            return true;
        }
        if (!cc.getAnnotationfilepath().equals(model.getAnnoDB())) {
            return true;
        }
        if (cc.isReplaceQual() != model.getReplaceQual()) {
            return true;
        }

        // check the fields that are stored withing the BamfoSettings        
        if (bs.isTrimBtail() != model.getTrimQB()
                || bs.isTrimpolyedge() != model.getTrimpolyedge()
                || bs.isNRef() != model.getNRef()) {
            return true;
        }        
        if (!bs.getGenome().equals(model.getGenome())) {
            return true;
        }
        if (bs.getMinallelic() != model.getProportion()
                || bs.getMinbasequal() != model.getBasequal()
                || bs.getMindepth() != model.getDepth()
                || bs.getMinfromend() != model.getReadend()
                || bs.getMinfromstart() != model.getReadstart()
                || bs.getMinmapqual() != model.getMapqual()
                || bs.getMinscore() != model.getScore()
                || bs.getStrandbias() != model.getStrandbias()) {
            return true;
        }

        // if all test have passed, configuration has not changed.
        return false;
    }

    private void configCombochangeConfigHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_configCombochangeConfigHandler

        callSettingsTable.getDefaultEditor(Object.class).stopCellEditing();
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

        String configname = (String) configCombo.getSelectedItem();
        if (configname != null && configname.equals("default")) {
            deleteConfigButton.setEnabled(false);
            applyButton.setEnabled(false);
        } else {
            deleteConfigButton.setEnabled(true);
            applyButton.setEnabled(true);
        }

    }//GEN-LAST:event_configCombochangeConfigHandler

    private void newConfigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newConfigButtonActionPerformed

        String newconfig = (String) JOptionPane.showInputDialog(this, "New Configuration", "New Configuration");
        if (newconfig == null) {
            return;
        }

        // check that this configuration does not exist
        if (callConfigurations.has(newconfig)) {
            selectConfig(newconfig);
            return;
        }

        // create a new configuration
        callConfigurations.set(new CallConfiguration(newconfig));
        configCombo.addItem(newconfig);
        selectConfig(newconfig);
        configCombo.setSelectedItem(newconfig);
    }//GEN-LAST:event_newConfigButtonActionPerformed

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
        callConfigurations.remove(configname);
        if (configCombo.getComponentCount() > 0) {
            selectConfig((String) configCombo.getSelectedItem());
        } else {
            selectConfig(null);
        }
    }//GEN-LAST:event_deleteConfigButtonActionPerformed

    private void callOkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_callOkButtonActionPerformed
        // should save this configuration before exiting
        applyButtonActionPerformed(null);

        // make sure the decision is visible to the user on the main form
        ok = true;

        // go back to the main form
        this.setVisible(false);
    }//GEN-LAST:event_callOkButtonActionPerformed

    private void callCancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_callCancelButtonActionPerformed
        configCombo.setSelectedItem(currentConfig);

        // make sure the main form sees the cancel decision
        ok = false;

        // go back to the main form
        this.setVisible(false);
    }//GEN-LAST:event_callCancelButtonActionPerformed

    /**
     * This is called in order to make sure the values in the viewer are saved
     * into the the configurations list
     *
     * @param evt
     */
    private void applyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
                
        callSettingsTable.getDefaultEditor(Object.class).stopCellEditing();
                
        CallConfiguration nowconfig = callConfigurations.get(currentConfig);
        BamfoSettings bsettings = nowconfig.getSettings();
        CallSettingsTableModel model = (CallSettingsTableModel) callSettingsTable.getModel();
        model.fireTableDataChanged();

        // get the items that go into the general configuration
        nowconfig.setSuffix(outSuffixTextfield.getText());
        nowconfig.setAnnotationfilepath(model.getAnnoDB());
        nowconfig.setReplaceQual(model.getReplaceQual());

        // get the items that go into the BamfoSettings object
        bsettings.setGenome(model.getGenome());
        bsettings.setMinallelic(model.getProportion());
        bsettings.setMinbasequal(model.getBasequal());
        bsettings.setMindepth(model.getDepth());
        bsettings.setMinfromend(model.getReadend());
        bsettings.setMinfromstart(model.getReadstart());
        bsettings.setMinmapqual(model.getMapqual());
        bsettings.setMinscore(model.getScore());
        bsettings.setStrandbias(model.getStrandbias());
        bsettings.setTrimBtail(model.getTrimQB());
        bsettings.setTrimpolyedge(model.getTrimpolyedge());
        bsettings.setNRef(model.getNRef());

    }//GEN-LAST:event_applyButtonActionPerformed

    private void refreshTable() {
        CallSettingsTableModel model = (CallSettingsTableModel) callSettingsTable.getModel();
        int nn = model.getRowCount();
        for (int i = 0; i < nn; i++) {
            model.getValueAt(0, 1);
        }
    }

    /**
     * loads and displays the configuration identified by the given name
     *
     * @param name
     */
    private void selectConfig(String name) {

        // remember the name of new configuration in a class variable

        currentConfig = name;
        callSettingsTable.setEnabled(true);
        callSettingsTable.getDefaultEditor(Object.class).stopCellEditing();
        //refreshTable();

        if (name != null) {
            // get the settings saved in the list
            CallConfiguration nowconfig = callConfigurations.get(name);
            BamfoSettings bsettings = nowconfig.getSettings();

            // display all the options in the table/textfield
            outSuffixTextfield.setText(nowconfig.getSuffix());
            //model.fireTableDataChanged();

            // There should be a way to update the existing model by
            // just replacing some values
            // However, I had trouble with updating the cell that was last being edited.            
            CallSettingsTableModel model = (CallSettingsTableModel) callSettingsTable.getModel();
            while (model.getRowCount() > 0) {
                model.removeRow(model.getRowCount() - 1);
            }

            model = new CallSettingsTableModel();
            callSettingsTable.setModel(model);
            callSettingsTable.setDefaultEditor(Object.class, new CallSettingsTableRendererEditor(model));
            callSettingsTable.setDefaultRenderer(Object.class, new CallSettingsTableRendererEditor(model));

            model.setGenome(bsettings.getGenome());
            model.setBasequal(bsettings.getMinbasequal());
            model.setMapqual(bsettings.getMinmapqual());
            model.setDepth(bsettings.getMindepth());
            model.setProportion(bsettings.getMinallelic());
            model.setScore(bsettings.getMinscore());
            model.setStrandbias(bsettings.getStrandbias());
            model.setReadstart(bsettings.getMinfromstart());
            model.setReadend(bsettings.getMinfromend());
            model.setTrimpolyedge(bsettings.isTrimpolyedge());
            model.setTrimQB(bsettings.isTrimBtail());
            model.setNRef(bsettings.isNRef());
            model.setAnnoDB(nowconfig.getAnnotationfilepath());
            model.setReplaceQual(nowconfig.isReplaceQual());
            model.fireTableDataChanged();

            // make the look enabled or disabled.
            if (name.equals("default")) {
                callSettingsTable.setEnabled(false);
                outSuffixTextfield.setEnabled(false);
                applyButton.setEnabled(false);
                deleteConfigButton.setEnabled(false);
            } else {
                callSettingsTable.setEnabled(true);
                outSuffixTextfield.setEnabled(true);
                applyButton.setEnabled(true);
                deleteConfigButton.setEnabled(true);
            }


        } else {
            // deal with aesthetics for default case
            System.out.println("is this every reached?");
            callSettingsTable.setEnabled(false);
        }

    }

    /**
     * create a runnable object based on the selected configuration.
     *
     * @param f
     * @return
     */
    public BamfoRunnableTask makeRunnable(final File f, JTextArea outarea) {
        String last = callConfigurations.getLastConfigurationName();
        final CallConfiguration cc = callConfigurations.get(last);

        // if there was no last configuration, return a runnable that does not do anything
        if (cc == null) {
            return null;
        }

        // create input/output paths
        File fdir = f.getParentFile();
        //String[] fsplit = FileNameExtensionSplitter.split(f.getName());
        String[] fsplit = FileExtensionGetter.getExtensionSplit(f);
        final String output = new File(fdir, fsplit[0] + cc.getSuffix()).getAbsolutePath();
        final String outputtemp = new File(fdir, fsplit[0] + cc.getSuffix() + ".temp.gz").getAbsolutePath();

        // create the stream where the task will output messages
        final PipedOutputStream os = new PipedOutputStream();
        final PrintStream pos = new PrintStream(os);

        // create a temporary runnable. This will either call or call+annotate
        Runnable temprunnable;
        if (cc.getAnnotationfilepath() == null || cc.getAnnotationfilepath().isEmpty()) {
            // create a new runnable BamfoVcf that just calls the variants
            temprunnable = new BamfoVcf(f.getAbsoluteFile(), output, new BamfoSettings(cc.getSettings()), pos);
        } else {
            // create a wrapper that calls the variants into a temporary file, then 
            // annotates the calls using a database
            temprunnable = new Runnable() {
                @Override
                public void run() {
                    // first make the calls
                    new BamfoVcf(f.getAbsoluteFile(), outputtemp, new BamfoSettings(cc.getSettings()), pos).run();
                    // then annotate the calls
                    new BamfoAnnotateVariants(outputtemp, cc.getAnnotationfilepath(), output, false, pos).run();
                    // and finally remove the temporary file
                    try {
                        (new File(outputtemp)).delete();
                    } catch (Exception ex) {
                        pos.println("Error deleting temporary file: " + ex.getMessage());
                    }
                }
            };
        }

        // now turn the runnable into a BamfoRunnable so that it can be monitored by the viewer                
        return new VariantsRunnableTask(temprunnable, os, outarea,
                "java -jar bamformatics.jar callvariants",
                getCommandLineArguments(f, output, new BamfoSettings(cc.getSettings())));

    }

    private String getCommandLineArguments(File f, String output, BamfoSettings settings) {
        StringBuilder sb = new StringBuilder();
        sb.append("--bam ").append(f.getAbsolutePath()).append("\n");
        sb.append("--output ").append(output).append("\n");
        sb.append("--genome ").append(settings.getGenome()).append("\n");
        sb.append("--minallelic ").append(settings.getMinallelic()).append("\n");
        sb.append("--minbasequal ").append((char) settings.getMinbasequal()).append("\n");
        sb.append("--mindepth ").append(settings.getMindepth()).append("\n");
        sb.append("--minfromend ").append(settings.getMinfromend()).append("\n");
        sb.append("--minfromstart ").append(settings.getMinfromstart()).append("\n");
        sb.append("--minmapqual ").append(settings.getMinmapqual()).append("\n");
        sb.append("--minscore ").append(settings.getMinscore()).append("\n");
        sb.append("--strandbias ").append(settings.getStrandbias()).append("\n");
        sb.append("--trimQB ").append(settings.isTrimBtail()).append("\n");        
        sb.append("--trim ").append(settings.isTrimpolyedge()).append("\n");
        sb.append("--NRef ").append(settings.isNRef()).append("\n");
        sb.append("--verbose ").append("\n");

        return sb.toString();
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton applyButton;
    private javax.swing.JButton callCancelButton;
    private javax.swing.JButton callOkButton;
    private javax.swing.JTable callSettingsTable;
    private javax.swing.JComboBox configCombo;
    private javax.swing.JButton deleteConfigButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton newConfigButton;
    private javax.swing.JTextField outSuffixTextfield;
    // End of variables declaration//GEN-END:variables
}
