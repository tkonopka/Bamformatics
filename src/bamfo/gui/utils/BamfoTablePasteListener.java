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
package bamfo.gui.utils;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JTable;

/**
 * Custom generic class that allows user to paste contents from the clipboard into a
 * table. Also, some other keyboard shortcuts on table operations are allowed, e.g.
 * delete contents of the selected table cells.
 *
 *
 * @author tomasz
 */
public class BamfoTablePasteListener implements KeyListener {

    private final JTable table;

    public BamfoTablePasteListener(JTable table) {
        this.table = table;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // deal with Ctrl-V paste events
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
            paste();
            return;
        }
        // deal with removal of information from table
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
            deleteCells();
            return;
        }


    }

    private void stopEditing() {
        if (table.isEditing()) {
            table.editCellAt(-1, -1);
        }
    }

    /**
     * go through the selected cells in the table and removes them
     */
    private void deleteCells() {

        stopEditing();

        int startcol = table.getSelectedColumn();
        int startrow = table.getSelectedRow();

        int selColCount = table.getSelectedColumnCount();
        int selRowCount = table.getSelectedRowCount();

        // loop through all the selected items and delete them
        for (int j = 0; j < selColCount; j++) {
            Class columnclass = table.getColumnClass(startcol+j);
            if (columnclass == String.class) {
                for (int i = 0; i < selRowCount; i++) {
                    table.setValueAt("", startrow+i, startcol+j);
                }
            } else {
                for (int i = 0; i < selRowCount; i++) {
                    table.setValueAt(null, startrow+i, startcol+j);
                }
            } 
        }
        
        table.repaint();
    }

    private void paste() {
        // start pasting into the table 
        Transferable fromclipboard = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        // only do something if the contents of the clipboard is a string.
        if (!fromclipboard.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return;
        }
        try {
            // find where in the table to start pasting
            int startcol = table.getSelectedColumn();
            int startrow = table.getSelectedRow();
            int tableRowCount = table.getRowCount();
            int tableColCount = table.getColumnCount();

            // convert the contents of the clipboard into an array with one string per line
            String clipboardString = fromclipboard.getTransferData(DataFlavor.stringFlavor).toString();
            String[] cliprows = clipboardString.split("\n");

            // stop editing the table...
            stopEditing();

            // copy the contents of the cells into the table
            // ignore over-flowing items
            for (int i = 0; i < cliprows.length && startrow + i < tableRowCount; i++) {
                String[] nowcells = cliprows[i].split("\t");
                for (int j = 0; j < nowcells.length && startcol + j < tableColCount; j++) {
                    table.setValueAt(nowcells[j], startrow + i, startcol + j);
                }
            }

        } catch (Exception ex) {
            System.out.println("Something went wrong when pasting from clipboard");
            System.out.println(ex.getMessage());
        }
        // repaint the table to show the new contents
        table.repaint();
    }
}
