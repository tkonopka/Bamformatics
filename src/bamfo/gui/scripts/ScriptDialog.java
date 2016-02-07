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
package bamfo.gui.scripts;

import bamfo.gui.BamfoGUI;
import bamfo.gui.utils.BamfoMiscFile;
import bamfo.gui.utils.BamfoRunnableProcess;
import bamfo.gui.utils.BamfoRunnableTask;
import bamfo.gui.utils.BamfoTablePasteListener;
import bamfo.gui.viewers.BamfoTaskViewer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author tomasz
 */
public class ScriptDialog extends javax.swing.JDialog {

    private final BamfoGUI parentBamfoGUI;

    /**
     *
     * @return
     */
    synchronized File getCurrentDir() {
        try {
            File curdir = new File((String) dirTable.getValueAt(0, 0));
            if (curdir.exists()) {
                return curdir;
            } else {
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * function called on dirTable and scriptTable to make them look like
     * one-lines with a "..." browse button
     *
     * @param thetable
     */
    private void editTable(JTable thetable, boolean dironly) {
        TableColumnModel atm = (TableColumnModel) thetable.getColumnModel();
        atm.getColumn(0).setPreferredWidth(128);
        atm.getColumn(1).setPreferredWidth(22);
        atm.getColumn(1).setMaxWidth(22);
        thetable.getTableHeader().setVisible(false);
        thetable.getTableHeader().setPreferredSize(new Dimension(-1, 0));

        atm.getColumn(1).setCellRenderer(new BrowseButtonRenderer());
        thetable.addMouseListener(new BrowseButtonClickListener(this, thetable, 1, 0, dironly));
        thetable.setRowHeight(20);
        thetable.addKeyListener(new BamfoTablePasteListener(thetable));
    }

    /**
     * Creates new form ScriptDialog
     *
     */
    public ScriptDialog(BamfoGUI parent, boolean modal, File scriptfile) {
        super(parent, modal);
        this.parentBamfoGUI = parent;
        initComponents();
        this.setLocationRelativeTo(null);

        // modify the tables to make it look better
        editTable(dirTable, true);
        dirTable.setPreferredSize(new Dimension(400, 18));
        editTable(scriptTable, false);
        scriptTable.setPreferredSize(new Dimension(400, 18));
        setScript(scriptfile);

        // repeat for the arguments table
        editTable(argumentsTable, false);
    
        // make sure all the values in the first column are not null
        clearArguments();
    }

    public final synchronized void setScript(File scriptfile) {
        if (scriptfile != null) {
            scriptTable.setValueAt(scriptfile.getAbsolutePath(), 0, 0);
        } else {
            scriptTable.setValueAt("", 0, 0);
        }
    }

    /**
     * replaces all values in the table by the empty string ""
     */
    public final void clearArguments() {
        // clear the arguments table
        int rowsintable = argumentsTable.getRowCount();
        DefaultTableModel tabmodel = (DefaultTableModel) argumentsTable.getModel();
        for (int i = 0; i < rowsintable; i++) {
            tabmodel.setValueAt("", i, 0);
        }

        // clear the current directory
        tabmodel = (DefaultTableModel) dirTable.getModel();
        rowsintable = dirTable.getRowCount();
        for (int i = 0; i < rowsintable; i++) {
            tabmodel.setValueAt("", i, 0);
        }

    }

    /**
     * figures out how many of the rows in the table are non-trivial.
     *
     * @return
     *
     */
    private int getNumArgs() {
        DefaultTableModel tabmodel = (DefaultTableModel) argumentsTable.getModel();
        // start at end and look toward beginning        
        for (int i = tabmodel.getRowCount() - 1; i >= 0; i--) {
            String thisarg = (String) tabmodel.getValueAt(i, 0);
            if (!thisarg.isEmpty()) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Adds values of cells from table into the array list.
     *
     * @param thiscommand
     */
    private void addArgs(ArrayList<String> thiscommand) {
        int numargs = getNumArgs();
        DefaultTableModel tabmodel = (DefaultTableModel) argumentsTable.getModel();
        for (int i = 0; i < numargs; i++) {
            String thisarg = (String) tabmodel.getValueAt(i, 0);
            if (thisarg.isEmpty()) {
                thiscommand.add("\"\"");
            } else {
                thiscommand.add(thisarg);
            }
        }
    }

    private File getCurDir(File f) {
        // check for the current directory        
        String curdirString = (String) dirTable.getModel().getValueAt(0, 0);
        if (curdirString.isEmpty()) {
            return f.getParentFile();
        } else {
            try {
                return new File(curdirString);
            } catch (Exception ex) {
                return null;
            }
        }

    }

    /**
     * uses the set directory and arguments to create a runnable process.
     *
     * @param f
     *
     * the executable script file
     *
     * @return
     *
     * a runnable object that will execute the script as an OS process.
     *
     *
     */
    public BamfoRunnableProcess makeRunnable(final File f, final JTextArea outarea) {

        // get the current directory for the process
        File curdir = getCurDir(f);
        if (curdir == null) {
            return null;
        }

        // figure out how many rows have non-empty arguments
        // and make a list with all the arguments
        int numargs = getNumArgs();
        ArrayList<String> thiscommand = new ArrayList<String>(numargs + 1);
        thiscommand.add(f.getAbsolutePath());
        addArgs(thiscommand);

        // create an OS process
        final ProcessBuilder pb = new ProcessBuilder(thiscommand);
        pb.directory(curdir);
        pb.redirectErrorStream(true);

        // return a runnable that executes this processs
        return new ScriptRunnableProcess(pb, outarea);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        scriptOkButton = new javax.swing.JButton();
        scriptCancelButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        dirTable = new javax.swing.JTable();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        argumentsTable = new javax.swing.JTable();
        jLabel3 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        scriptTable = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Script Execution");
        setMinimumSize(new java.awt.Dimension(393, 381));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
        });

        scriptOkButton.setText("Ok");
        scriptOkButton.setMaximumSize(new java.awt.Dimension(90, 27));
        scriptOkButton.setMinimumSize(new java.awt.Dimension(90, 27));
        scriptOkButton.setPreferredSize(new java.awt.Dimension(90, 26));
        scriptOkButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scriptOkButtonActionPerformed(evt);
            }
        });

        scriptCancelButton.setText("Cancel");
        scriptCancelButton.setMaximumSize(new java.awt.Dimension(90, 27));
        scriptCancelButton.setMinimumSize(new java.awt.Dimension(90, 27));
        scriptCancelButton.setPreferredSize(new java.awt.Dimension(90, 26));
        scriptCancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scriptCancelButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Parameters:");

        dirTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null}
            },
            new String [] {
                "Title 1", "Title 2"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Object.class
            };
            boolean[] canEdit = new boolean [] {
                true, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        dirTable.setMaximumSize(new java.awt.Dimension(400, 18));
        dirTable.setMinimumSize(new java.awt.Dimension(400, 18));
        dirTable.setPreferredSize(new java.awt.Dimension(400, 18));
        dirTable.setShowHorizontalLines(false);
        dirTable.setShowVerticalLines(false);
        dirTable.getTableHeader().setResizingAllowed(false);
        dirTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(dirTable);

        jLabel2.setText("Directory:");

        argumentsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Title 1", "Title 2"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Object.class
            };
            boolean[] canEdit = new boolean [] {
                true, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        argumentsTable.setShowVerticalLines(false);
        argumentsTable.getTableHeader().setResizingAllowed(false);
        argumentsTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane2.setViewportView(argumentsTable);

        jLabel3.setText("Script:");

        scriptTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null}
            },
            new String [] {
                "Title 1", "Title 2"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Object.class
            };
            boolean[] canEdit = new boolean [] {
                true, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scriptTable.setMaximumSize(new java.awt.Dimension(400, 18));
        scriptTable.setMinimumSize(new java.awt.Dimension(400, 18));
        scriptTable.setPreferredSize(new java.awt.Dimension(400, 18));
        scriptTable.setShowVerticalLines(false);
        scriptTable.getTableHeader().setResizingAllowed(false);
        scriptTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane3.setViewportView(scriptTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(scriptOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scriptCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3)
                            .addComponent(jLabel1))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 205, Short.MAX_VALUE)
                .addGap(24, 24, 24)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(scriptCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(scriptOkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void stopEditingTable(JTable thetable) {
        if (thetable.isEditing()) {
            thetable.editCellAt(-1, -1);
        }
    }

    private void scriptOkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptOkButtonActionPerformed

        // this is a hack. It asks the table to start editing a cell that doesn't
        // exist. This fails, but it forces the table to stop editing the 
        // original cell.
        stopEditingTable(argumentsTable);
        stopEditingTable(scriptTable);
        stopEditingTable(dirTable);

        // find out whether the script can be executed
        File scriptfile = new File((String) scriptTable.getValueAt(0, 0));
        if (!scriptfile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Script file does not exist:\n" + scriptfile.getAbsolutePath(), "Non-existent file",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!scriptfile.canExecute()) {
            JOptionPane.showMessageDialog(this,
                    "Cannot execute script file:\n" + scriptfile.getAbsolutePath(), "Non-executable file",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // if reached here, the script should be executed
        BamfoTaskViewer taskviewer = parentBamfoGUI.openTaskViewer(scriptfile, "Script","Executing ");
        BamfoRunnableTask brun = makeRunnable(scriptfile, taskviewer.getOutputArea());
        taskviewer.setTask(brun);
        taskviewer.executeTask();
        taskviewer.setTaskStatus("Running");

        // go back to the main form
        this.setVisible(false);
    }//GEN-LAST:event_scriptOkButtonActionPerformed

    private void scriptCancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptCancelButtonActionPerformed
        // go back to the main form
        this.setVisible(false);
    }//GEN-LAST:event_scriptCancelButtonActionPerformed

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
    }//GEN-LAST:event_formWindowActivated
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable argumentsTable;
    private javax.swing.JTable dirTable;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton scriptCancelButton;
    private javax.swing.JButton scriptOkButton;
    private javax.swing.JTable scriptTable;
    // End of variables declaration//GEN-END:variables
}

/**
 * makes cells in a table appear like a Button with text "..."
 *
 * @author tomasz
 */
class BrowseButtonRenderer extends JButton implements TableCellRenderer {

    public BrowseButtonRenderer() {
        this.setText("...");
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return this;
    }
}

/**
 * Class that checks for clicks on the columns with the browse buttons. When
 * there is a click, a dialog appears to select files. If OK is selected, the
 * file/dir name is written into the path column in the table.
 *
 * @author tomasz
 */
class BrowseButtonClickListener implements MouseListener {

    private final int browsecolumn;
    private final int pathcolumn;
    private final JTable table;
    private final ScriptDialog dialog;
    private final boolean dironly;

    public BrowseButtonClickListener(ScriptDialog dialog, JTable table, int browsecolumn, int pathcolumn, boolean dironly) {
        this.dialog = dialog;
        this.table = table;
        this.browsecolumn = browsecolumn;
        this.pathcolumn = pathcolumn;
        this.dironly = dironly;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
            int rownumber = table.rowAtPoint(e.getPoint());
            int colnumber = table.columnAtPoint(e.getPoint());
            if (colnumber == browsecolumn) {
                File curdir = dialog.getCurrentDir();
                // let the user browse for a file
                File myfile;
                if (dironly) {
                    myfile = BamfoMiscFile.chooseDir(curdir);
                } else {
                    myfile = BamfoMiscFile.chooseFileOrDir(curdir);
                }
                if (myfile != null) {
                    DefaultTableModel mymodel = (DefaultTableModel) table.getModel();
                    mymodel.setValueAt(myfile.getAbsolutePath(), rownumber, pathcolumn);
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
