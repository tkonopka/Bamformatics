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
package bamfo.gui.viewers;

import bamfo.gui.components.BamfoRegexPanel;
import bamfo.gui.utils.BamfoTablePasteListener;
import bamfo.utils.BamfoCommon;
import bamfo.utils.NumberChecker;
import bamfo.utils.TextFileChecker;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.FileExtensionGetter;
import jsequtils.file.OutputStreamMaker;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMRecord;

/**
 *
 * @author tomasz
 */
public class BamfoTableViewer extends BamfoViewer {

    private ArrayList<String> headerlines = new ArrayList<>(16);
    private Object[][] data;
    private String[] colnames;
    private Class[] colclasses;
    private boolean[] colvisible;
    private boolean[] rowvisible;
    private File file;
    private final JTabbedPane pane;
    private final CardLayout headerdataCL;
    private volatile boolean dataloaded = false;
    private BamfoRegexPanel headerRegexPanel;
    private BamfoRegexPanel dataRegexPanel;
    private JComboBox dataRegexSubsetCombo;
    // experimental
    private String lastDataPatternString = ".";
    private int lastDataPatternSubset = -1;
    private String lastHeaderPatternString = ".";

    /**
     * Use file extension to set some reasonable settings for headers and column
     * names
     *
     * @param file
     */
    private void setDefaultSettings(File file) {
        String[] filenamesplit = FileExtensionGetter.getExtensionSplit(file);
        filenamesplit[1] = filenamesplit[1].toLowerCase();
        if (filenamesplit[1].equals("vcf")) {
            hasColnamesCheckBox.setSelected(true);
            colnamesLastHeaderRow.setEnabled(true);
            colnamesLastHeaderRow.setSelected(true);
            return;
        } else {
            colnamesLastHeaderRow.setEnabled(true);
            colnamesLastHeaderRow.setSelected(false);
        }
        if (filenamesplit[1].equals("bed")
                || filenamesplit[1].equals("txt")) {
            colnamesLastHeaderRow.setEnabled(false);
            hasColnamesCheckBox.setSelected(false);
        }
        if (filenamesplit[1].equals("") || filenamesplit[1].equals("r")
                || filenamesplit[1].equals("fastq")
                || filenamesplit[1].equals("fa")
                || filenamesplit[1].equals("log")
                || filenamesplit[1].equals("html")
                || filenamesplit[1].equals("htm")
                || filenamesplit[1].equals("php")) {
            colnamesLastHeaderRow.setEnabled(false);
            hasColnamesCheckBox.setSelected(false);
            headerDefTextField.setText("");
        }
        if (filenamesplit[1].equals("sam")) {
            colnamesLastHeaderRow.setEnabled(false);
            hasColnamesCheckBox.setSelected(false);
            headerDefTextField.setText("@");
        }
        // for certain file types, set the separator character
        // so that it will be determined when data is actually read
        if (filenamesplit[1].equals("csv")
                || filenamesplit[1].equals("txt")) {
            dataSepTextField.setText("\\n");
        }
    }

    /**
     *
     * @param file
     *
     * file to load
     *
     * @param pane
     *
     * this is a means for the viewer to communicate with its parent container
     *
     * @param numrows
     *
     * maximum number of rows to load into memory (can be changed later)
     *
     */
    public BamfoTableViewer(File file, final JTabbedPane pane, int numrows) {
        initComponents();
        postInitComponents();

        // set some default values for the settings
        setDefaultSettings(file);
        maxRowSpinner.setValue(numrows);

        commentLabel.setVisible(false);
        headerdataCL = (CardLayout) headerDataCards.getLayout();

        viewHeaderButton.setSelected(true);

        // customize the table so that it displays and sort items correctly
        //dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        dataTable.setDefaultRenderer(String.class, new BamfoTableCellRenderer());
        dataTable.setDefaultRenderer(Integer.class, new BamfoTableCellRenderer());
        dataTable.setDefaultRenderer(Double.class, new BamfoTableCellRenderer());
        dataTable.addKeyListener(new BamfoTablePasteListener(dataTable));

        // remember the file given in constructor        
        this.file = file;
        this.pane = pane;

        // by default, set the buttons to disabled. They will be activated
        // when the data is actually loaded.
        viewHeaderButton.setEnabled(false);
        viewDataButton.setEnabled(false);

        // by default, load the header and display the header panel        
        reloadButtonActionPerformed(null);
        if (viewHeaderButton.isEnabled()) {
            viewHeaderButtonActionPerformed(null);
        }

        // but if the header is empty, switch to the data view
        if (headerlines.isEmpty() && viewDataButton.isEnabled()) {
            viewDataButton.setSelected(true);
            viewDataButtonActionPerformed(null);
        }

        canSaveAs = true;
        savefile = file;

        columnChoiceTable.addKeyListener(
                new BamfoTableToggleListener(columnChoiceTable, 0));

    }

    /**
     * before initializing a table viewer, an external utility can check if this
     * class will be able to process the file.
     *
     * @param f
     *
     * @return
     *
     * true if file appears to have text data or if extension is "bam"
     *
     */
    public static boolean canReadFile(File f) {
        if (TextFileChecker.isTextFile(f)) {
            return true;
        }
        if (isBamOrSamFile(f)) {
            return true;
        }
        return false;
    }

    /**
     * checks extension of a file and compares with "bam"
     *
     * @param f
     * @return
     */
    private static boolean isBamOrSamFile(File f) {
        String fextension = FileExtensionGetter.getExtension(f);
        if (fextension.equals("bam") || fextension.endsWith("sam")) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isBamFile(File f) {
        String fextension = FileExtensionGetter.getExtension(f);
        if (fextension.equals("bam")) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isSamFile(File f) {
        String fextension = FileExtensionGetter.getExtension(f);
        if (fextension.equals("sam")) {
            return true;
        } else {
            return false;
        }
    }

    private void loadBamHeader() {
        headerlines.clear();
        // open SAM and copy header into the headerlines array
        SAMFileReader inputSam = new SAMFileReader(file);
        BamfoCommon.updateValidationStringency(inputSam, "SILENT");
        String textHeader = inputSam.getFileHeader().getTextHeader();
        String[] tHtokens = textHeader.split("\n");
        headerlines.addAll(Arrays.asList(tHtokens));

        headerArea.setText(textHeader);
        numHeadRowsLabel.setText("" + headerlines.size());

        inputSam.close();
        // force refiltering of the the Header
        this.lastHeaderPatternString = ".";
        filterHeader();
    }

    private void loadHeader() throws FileNotFoundException, IOException {

        headerlines.clear();
        BufferedReader br = BufferedReaderMaker.makeBufferedReader(file);
        // read the comment lines                
        String s;
        StringBuilder sb = new StringBuilder(1024);
        String headerDef = headerDefTextField.getText();
        while ((s = br.readLine()) != null && s.startsWith(headerDef)) {
            headerlines.add(s);
            sb.append(s).append("\n");
        }
        br.close();

        headerArea.setText(sb.toString());
        numHeadRowsLabel.setText("" + headerlines.size());

        // force refiltering of the the Header
        this.lastHeaderPatternString = ".";
        filterHeader();
    }

    /**
     * Helper function for loadData. When a column starts as being a number but
     * turns out not be a number, this function can backtrack on a column and
     * change object to string. The function should return nothing, but will
     * change the objarray.
     *
     * @param objarray
     *
     * a list of object array representing items in the table.
     *
     * @param colindex
     *
     * column to downgrade
     *
     *
     */
    private void downgradeColumnToString(ArrayList<Object[]> objarray, int colindex) {
        int oasize = objarray.size();
        for (int i = 0; i < oasize; i++) {
            Object[] temp = objarray.get(i);
            temp[colindex] = temp[colindex].toString();
        }
    }

    /**
     * Similar to downgradeColumnToString, but in this case the operation is an
     * upgrade. Use it to upgrade integers to doubles.
     *
     * @param objarray
     * @param colindex
     */
    private void upgradeColumnToDouble(ArrayList<Object[]> objarray, int colindex) {
        int oasize = objarray.size();
        for (int i = 0; i < oasize; i++) {
            Object[] temp = objarray.get(i);
            temp[colindex] = new Double(temp[colindex].toString());
        }
    }

    /**
     * Uses the existing column names and some of the data items to set
     * tentative widths for the data table.
     */
    private void adjustColWidths() {

        int numcols = this.colnames.length;
        if (numcols < 1) {
            return;
        }

        int[] colwidths = new int[numcols];

        // first set the widths based on column names
        for (int i = 0; i < numcols; i++) {
            colwidths[i] = 12 + (colnames[i].length() * 10);
        }

        // then peek into the data items
        int peakdepth = 10;
        int datarows = this.data.length;

        for (int i = 0; i < numcols; i++) {
            int maxchars = 0;
            for (int j = 0; j < Math.min(peakdepth, datarows); j++) {
                maxchars = Math.max(maxchars, data[j][i].toString().length());
            }
            colwidths[i] = Math.max(colwidths[i], 12 + (maxchars * 10));
            colwidths[i] = Math.min(500, colwidths[i]);
        }

        TableColumnModel tcm = dataTable.getColumnModel();
        for (int i = 0; i < numcols; i++) {
            tcm.getColumn(i).setPreferredWidth(colwidths[i]);
        }
    }

    /**
     * Scans the data in the input file and counts commas and tabs. Returns the
     * most common character, which is probably the character that separates
     * columns.
     *
     * @param numlines
     *
     * Instead of scanning the whole file, the scan can look at only a subset of
     * lines.
     *
     * @return
     *
     * either a comma or a tab, whichever is more common.
     *
     * @throws IOException
     */
    private String guessSeparatorCharacter(int numlines) throws IOException {
        BufferedReader br = BufferedReaderMaker.makeBufferedReader(file);

        // skip the header, if it exists
        String headerdef = headerDefTextField.getText();
        String s = br.readLine();
        while (s != null && s.startsWith(headerdef)) {
            s = br.readLine();
        }

        int commacount = 0, tabcount = 0;
        int nowline = 0;
        while (s != null && nowline < numlines) {
            // scan the line and count commas and tabs
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == ',') {
                    commacount++;
                } else if (s.charAt(i) == '\t') {
                    tabcount++;
                }
            }

            s = br.readLine();
            nowline++;
        }
        br.close();

        // decide which character is more represented
        if (commacount > tabcount) {
            dataSepTextField.setText(",");
            return ",";
        } else {
            dataSepTextField.setText("\\t");
            return "\t";
        }
    }

    private void loadBamData() {

        // set up the reader
        SAMFileReader inputSam = new SAMFileReader(file);
        BamfoCommon.updateValidationStringency(inputSam, "SILENT");

        int numcols = 12;

        colvisible = new boolean[numcols];
        setAllTrue(colvisible);

        // label the columns 
        colnames = new String[numcols];
        colnames[0] = "readname";
        colnames[1] = "flag";
        colnames[2] = "chr";
        colnames[3] = "position";
        colnames[4] = "mapqual";
        colnames[5] = "cigar";
        colnames[6] = "mate.chr";
        colnames[7] = "mate.position";
        colnames[8] = "template.length";
        colnames[9] = "sequence";
        colnames[10] = "quality";
        colnames[11] = "tags";

        // record the classes of the data
        colclasses = new Class[numcols];
        colclasses[0] = String.class;
        colclasses[1] = Integer.class;
        colclasses[2] = String.class;
        colclasses[3] = Integer.class;
        colclasses[4] = Integer.class;
        colclasses[5] = String.class;
        colclasses[6] = String.class;
        colclasses[7] = Integer.class;
        colclasses[8] = Integer.class;
        colclasses[9] = String.class;
        colclasses[10] = String.class;
        colclasses[11] = String.class;

        // read the data                
        ArrayList<Object[]> tempdata = new ArrayList<Object[]>(32);
        int maxrows = (int) (Integer) maxRowSpinner.getValue();

        for (final SAMRecord record : inputSam) {
            String[] recordstring = record.getSAMString().split("\t");

            Object[] temprow = new Object[numcols];
            temprow[0] = recordstring[0];
            temprow[1] = record.getFlags();
            temprow[2] = recordstring[2];
            temprow[3] = record.getAlignmentStart();
            temprow[4] = record.getMappingQuality();
            temprow[5] = record.getCigarString();
            temprow[6] = record.getMateReferenceName();
            temprow[7] = record.getMateAlignmentStart();
            temprow[8] = recordstring[8];
            temprow[9] = record.getReadString();
            temprow[10] = record.getBaseQualityString();

            // record the tags by parsing the sam record string            
            StringBuilder sb = new StringBuilder();
            for (int i = 11; i < recordstring.length; i++) {
                if (i > 11) {
                    sb.append("\t");
                }
                sb.append(recordstring[i]);
            }
            temprow[11] = sb.toString();

            tempdata.add(temprow);
            if (tempdata.size() >= maxrows) {
                break;
            }
        }

        inputSam.close();

        if (!tempdata.isEmpty()) {
            int tdatasize = tempdata.size();
            data = new Object[tdatasize][numcols];
            for (int i = 0; i < tdatasize; i++) {
                Object[] temp = (Object[]) tempdata.get(i);
                System.arraycopy(temp, 0, data[i], 0, temp.length);
            }
        } else {
            data = new String[1][numcols];
            for (int j = 0; j < numcols; j++) {
                data[0][j] = "NA";
            }
        }
        rowvisible = new boolean[data.length];
        setAllTrue(rowvisible);

        ViewerTableModel tm = new ViewerTableModel(data, colnames, colclasses);

        dataTable.setModel(tm);
        dataTable.setAutoCreateRowSorter(true);
        numDataRowsLabel.setText("" + dataTable.getModel().getRowCount());
        numDataColsLabel.setText("" + dataTable.getModel().getColumnCount());

        // write a comment if there is more data but it has not been loaded yet
        if (tempdata.size() == maxrows) {
            JOptionPane.showMessageDialog(this,
                    "Loaded only first " + maxrows + " data rows, but data file contains more.\n"
                    + "To see more data from the file, change settings and reload the file.", "Warning",
                    JOptionPane.WARNING_MESSAGE);
            commentLabel.setText("File contains more data than shown in the viewer!");
            commentLabel.setVisible(true);
        }

        // force refiltering of the data
        lastDataPatternString = ".";
        filterData();
    }

    /**
     *
     */
    private void addSubsetColumns() {
        ItemListener subsetListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    filterHeaderOrData(1);
                }
            }
        };

        dataRegexSubsetCombo.removeAllItems();
        dataRegexSubsetCombo.addItem(makeObj("[all columns]"));
        if (colnames != null) {
            for (int i = 0; i < colnames.length; i++) {
                dataRegexSubsetCombo.addItem(makeObj(colnames[i]));
            }
        }
        dataRegexSubsetCombo.setSelectedIndex(0);
        lastDataPatternSubset = 0;
        dataRegexSubsetCombo.addItemListener(subsetListener);
    }

    /**
     * reads data from a file, stores it in memory and displays it in the table.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void loadData() throws FileNotFoundException, IOException {

        // find the column separator character
        String colsep = dataSepTextField.getText();
        if (colsep.equals("\\n")) {
            guessSeparatorCharacter(10);
            colsep = dataSepTextField.getText();
        }

        BufferedReader br = BufferedReaderMaker.makeBufferedReader(file);
        // read the column names                
        int numcols;

        // first skip header rows
        String s = br.readLine();
        String sbefore = s;
        String headerdef = headerDefTextField.getText();
        while (s != null && s.startsWith(headerdef)) {
            sbefore = s;
            s = br.readLine();
        }

        // now, s contains the first non-header line        
        // parse the header line
        if (s != null && hasColnamesCheckBox.isSelected()) {
            if (colnamesLastHeaderRow.isSelected()) {
                sbefore = sbefore.substring(headerdef.length());
                colnames = sbefore.split(colsep);
                numcols = colnames.length;
            } else {
                // treat this line as a header line, parse it and move to the next one
                colnames = s.split(colsep);
                numcols = colnames.length;
                s = br.readLine();
            }
        } else {
            if (s != null) {
                String[] temp = s.split(colsep);
                numcols = temp.length;
                colnames = new String[numcols];
                for (int i = 0; i < numcols; i++) {
                    colnames[i] = "V" + i;
                }
            } else {
                // there is no data?
                data = new Object[0][0];
                colnames = new String[0];
                colvisible = new boolean[0];
                rowvisible = new boolean[0];
                numcols = 0;
                colclasses = new Class[0];
                ViewerTableModel tm = new ViewerTableModel(data, colnames, colclasses);
                dataTable.setModel(tm);
                dataTable.setAutoCreateRowSorter(true);
                numDataRowsLabel.setText("0");
                super.setCursor(Cursor.getDefaultCursor());
                return;
            }
        }
        colvisible = new boolean[numcols];
        setAllTrue(colvisible);

        // add column names into regex combo box
        addSubsetColumns();

        // record the classes of the data
        colclasses = new Class[numcols];
        for (int i = 0; i < numcols; i++) {
            colclasses[i] = String.class;
        }

        // read the data        
        // try the first row to get classes of the columns
        if (s != null) {
            String[] temp = s.split(colsep, numcols);
            for (int i = 0; i < temp.length; i++) {
                if (temp[i].equalsIgnoreCase("NA") || temp[i].equalsIgnoreCase("NaN")
                        || temp[i].equalsIgnoreCase("Inf") || temp[i].equalsIgnoreCase("-Inf")) {
                    colclasses[i] = Double.class;
                } else {
                    if (NumberChecker.isDouble(temp[i])) {
                        colclasses[i] = Double.class;
                    }
                    if (NumberChecker.isInteger(temp[i])) {
                        colclasses[i] = Integer.class;
                    }
                }
            }
        }

        ArrayList<Object[]> tempdata = new ArrayList<Object[]>(32);
        int maxrows = (int) (Integer) maxRowSpinner.getValue();
        if (s != null) {
            do {
                // separate the data into columns
                String[] ssplit = s.split(colsep, numcols);
                Object[] temprow = new Object[numcols];

                // convert the strings into other objects if necessary
                for (int i = 0; i < ssplit.length; i++) {

                    temprow[i] = ssplit[i];
                    String tempstr = (String) temprow[i];
                    if (colclasses[i] == Integer.class) {
                        if (!NumberChecker.isInteger(tempstr)) {
                            // perhaps upgrade to Double, or downgrade to String
                            if (NumberChecker.isDouble(tempstr)) {
                                upgradeColumnToDouble(tempdata, i);
                                temprow[i] = new Double(tempstr);
                                colclasses[i] = Double.class;
                            } else if (tempstr.equalsIgnoreCase("NA") || tempstr.equalsIgnoreCase("NaN")) {
                                upgradeColumnToDouble(tempdata, i);
                                temprow[i] = new Double(Double.NaN);
                                colclasses[i] = Double.class;
                            } else if (tempstr.equalsIgnoreCase("Inf")) {
                                upgradeColumnToDouble(tempdata, i);
                                temprow[i] = new Double(Double.POSITIVE_INFINITY);
                                colclasses[i] = Double.class;
                            } else if (tempstr.equalsIgnoreCase("-Inf")) {
                                upgradeColumnToDouble(tempdata, i);
                                temprow[i] = new Double(Double.NEGATIVE_INFINITY);
                                colclasses[i] = Double.class;
                            } else {
                                // failed attempts to keep the column as a number, 
                                // so fall back onto string instead
                                downgradeColumnToString(tempdata, i);
                                colclasses[i] = String.class;
                            }
                        } else {
                            temprow[i] = new Integer(((String) temprow[i]).trim());
                        }
                    } else if (colclasses[i] == Double.class) {
                        if (!NumberChecker.isDouble(tempstr)) {
                            if (tempstr.equalsIgnoreCase("NA") || tempstr.equalsIgnoreCase("NaN")) {
                                temprow[i] = new Double(Double.NaN);
                            } else if (tempstr.equalsIgnoreCase("Inf")) {
                                temprow[i] = new Double(Double.POSITIVE_INFINITY);
                            } else if (tempstr.equalsIgnoreCase("-Inf")) {
                                temprow[i] = new Double(Double.NEGATIVE_INFINITY);
                            } else {
                                downgradeColumnToString(tempdata, i);
                                colclasses[i] = String.class;
                            }
                        } else {
                            temprow[i] = new Double(((String) temprow[i]).trim());
                        }
                    }
                }
                // if the row does not have enough items to fill the row, padd with ""
                for (int j = ssplit.length; j < numcols; j++) {
                    temprow[j] = "";
                }

                // add the array of custom objects into the array
                // and move onto the next line
                tempdata.add(temprow);
            } while ((s = br.readLine()) != null && tempdata.size() < maxrows);
        }
        br.close();

        if (!tempdata.isEmpty()) {
            int tdatasize = tempdata.size();
            data = new Object[tdatasize][numcols];
            for (int i = 0; i < tdatasize; i++) {
                Object[] temp = (Object[]) tempdata.get(i);
                System.arraycopy(temp, 0, data[i], 0, temp.length);
            }
        } else {
            data = new String[1][numcols];
            for (int j = 0; j < numcols; j++) {
                data[0][j] = "NA";
            }
        }
        ViewerTableModel tm = new ViewerTableModel(data, colnames, colclasses);

        rowvisible = new boolean[data.length];
        setAllTrue(rowvisible);

        dataTable.setModel(tm);
        dataTable.setAutoCreateRowSorter(true);
        numDataRowsLabel.setText("" + dataTable.getModel().getRowCount());
        numDataColsLabel.setText("" + dataTable.getModel().getColumnCount());

        // write a comment if there is more data but it has not been loaded yet
        if (s != null) {
            JOptionPane.showMessageDialog(this,
                    "Loaded only first " + maxrows + " data rows, but data file contains more.\n"
                    + "To see more data from the file, change settings and reload the file.", "Warning",
                    JOptionPane.WARNING_MESSAGE);
            commentLabel.setText("File contains more data than shown in the viewer!");
            commentLabel.setVisible(true);
        }

        // force refiltering of the data
        lastDataPatternString = ".";
        filterData();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        HeaderDataButtonGroup = new javax.swing.ButtonGroup();
        tableConfigDialog = new javax.swing.JDialog();
        columnsCancelButton = new javax.swing.JButton();
        columnsAllButton = new javax.swing.JButton();
        columnsNoneButton = new javax.swing.JButton();
        columnsOKButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        columnChoiceTable = new javax.swing.JTable();
        cardsToolbar = new javax.swing.JToolBar();
        viewSettingsButton = new javax.swing.JToggleButton();
        viewHeaderButton = new javax.swing.JToggleButton();
        viewDataButton = new javax.swing.JToggleButton();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        reloadButton = new javax.swing.JButton();
        tableColumnsButton = new javax.swing.JButton();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        headerDataCards = new javax.swing.JPanel();
        settingsPanel = new javax.swing.JPanel();
        hasColnamesCheckBox = new javax.swing.JCheckBox();
        colnamesLastHeaderRow = new javax.swing.JCheckBox();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        headerDefTextField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        dataSepTextField = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        maxRowSpinner = new javax.swing.JSpinner();
        dataScrollPane = new javax.swing.JScrollPane();
        dataTable = new javax.swing.JTable();
        headerScrollPane = new javax.swing.JScrollPane();
        headerArea = new javax.swing.JTextArea();
        bottomToolbar = new javax.swing.JToolBar();
        jLabel1 = new javax.swing.JLabel();
        numHeadRowsLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        jLabel8 = new javax.swing.JLabel();
        numDataColsLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        jLabel4 = new javax.swing.JLabel();
        numDataRowsLabel = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JToolBar.Separator();
        commentLabel = new javax.swing.JLabel();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        tableConfigDialog.setTitle("View table columns");
        tableConfigDialog.setMinimumSize(new java.awt.Dimension(260, 400));
        tableConfigDialog.setResizable(false);

        columnsCancelButton.setText("Cancel");
        columnsCancelButton.setMaximumSize(new java.awt.Dimension(90, 27));
        columnsCancelButton.setMinimumSize(new java.awt.Dimension(90, 27));
        columnsCancelButton.setPreferredSize(new java.awt.Dimension(90, 27));
        columnsCancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                columnsCancelButtonActionPerformed(evt);
            }
        });

        columnsAllButton.setText("All");
        columnsAllButton.setMaximumSize(new java.awt.Dimension(74, 27));
        columnsAllButton.setMinimumSize(new java.awt.Dimension(74, 27));
        columnsAllButton.setPreferredSize(new java.awt.Dimension(74, 27));
        columnsAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                columnsAllButtonActionPerformed(evt);
            }
        });

        columnsNoneButton.setText("None");
        columnsNoneButton.setMaximumSize(new java.awt.Dimension(74, 27));
        columnsNoneButton.setMinimumSize(new java.awt.Dimension(74, 27));
        columnsNoneButton.setOpaque(true);
        columnsNoneButton.setPreferredSize(new java.awt.Dimension(74, 27));
        columnsNoneButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                columnsNoneButtonActionPerformed(evt);
            }
        });

        columnsOKButton.setText("OK");
        columnsOKButton.setMaximumSize(new java.awt.Dimension(90, 27));
        columnsOKButton.setMinimumSize(new java.awt.Dimension(90, 27));
        columnsOKButton.setPreferredSize(new java.awt.Dimension(90, 27));
        columnsOKButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                columnsOKButtonActionPerformed(evt);
            }
        });

        columnChoiceTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                { new Boolean(true), "A0"},
                { new Boolean(true), "D3"},
                { new Boolean(true), "Z8"},
                { new Boolean(true), "B7"}
            },
            new String [] {
                "View", "Column"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class
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
        columnChoiceTable.setIntercellSpacing(new java.awt.Dimension(0, 0));
        columnChoiceTable.setShowHorizontalLines(false);
        columnChoiceTable.setShowVerticalLines(false);
        columnChoiceTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(columnChoiceTable);

        javax.swing.GroupLayout tableConfigDialogLayout = new javax.swing.GroupLayout(tableConfigDialog.getContentPane());
        tableConfigDialog.getContentPane().setLayout(tableConfigDialogLayout);
        tableConfigDialogLayout.setHorizontalGroup(
            tableConfigDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tableConfigDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tableConfigDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tableConfigDialogLayout.createSequentialGroup()
                        .addGroup(tableConfigDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addGroup(tableConfigDialogLayout.createSequentialGroup()
                                .addGap(0, 87, Short.MAX_VALUE)
                                .addComponent(columnsOKButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(columnsCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap())
                    .addGroup(tableConfigDialogLayout.createSequentialGroup()
                        .addComponent(columnsAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(columnsNoneButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        tableConfigDialogLayout.setVerticalGroup(
            tableConfigDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tableConfigDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tableConfigDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(columnsAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(columnsNoneButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 260, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 32, Short.MAX_VALUE)
                .addGroup(tableConfigDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(columnsOKButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(columnsCancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        cardsToolbar.setBorder(null);
        cardsToolbar.setFloatable(false);
        cardsToolbar.setRollover(true);
        cardsToolbar.setFont(cardsToolbar.getFont());
        cardsToolbar.setMaximumSize(new java.awt.Dimension(32767, 30));
        cardsToolbar.setMinimumSize(new java.awt.Dimension(183, 30));
        cardsToolbar.setPreferredSize(new java.awt.Dimension(148, 30));

        HeaderDataButtonGroup.add(viewSettingsButton);
        viewSettingsButton.setText("Settings");
        viewSettingsButton.setFocusable(false);
        viewSettingsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        viewSettingsButton.setMaximumSize(new java.awt.Dimension(80, 25));
        viewSettingsButton.setMinimumSize(new java.awt.Dimension(80, 25));
        viewSettingsButton.setPreferredSize(new java.awt.Dimension(80, 25));
        viewSettingsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        viewSettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewSettingsButtonActionPerformed(evt);
            }
        });
        cardsToolbar.add(viewSettingsButton);

        HeaderDataButtonGroup.add(viewHeaderButton);
        viewHeaderButton.setText("Header");
        viewHeaderButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        viewHeaderButton.setFocusable(false);
        viewHeaderButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        viewHeaderButton.setMaximumSize(new java.awt.Dimension(80, 25));
        viewHeaderButton.setMinimumSize(new java.awt.Dimension(80, 25));
        viewHeaderButton.setPreferredSize(new java.awt.Dimension(80, 25));
        viewHeaderButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        viewHeaderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewHeaderButtonActionPerformed(evt);
            }
        });
        cardsToolbar.add(viewHeaderButton);

        HeaderDataButtonGroup.add(viewDataButton);
        viewDataButton.setText("Data");
        viewDataButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        viewDataButton.setFocusable(false);
        viewDataButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        viewDataButton.setMaximumSize(new java.awt.Dimension(80, 25));
        viewDataButton.setMinimumSize(new java.awt.Dimension(80, 25));
        viewDataButton.setPreferredSize(new java.awt.Dimension(80, 25));
        viewDataButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        viewDataButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewDataButtonActionPerformed(evt);
            }
        });
        cardsToolbar.add(viewDataButton);
        cardsToolbar.add(jSeparator2);

        reloadButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/arrow-circle-double.png"))); // NOI18N
        reloadButton.setFocusable(false);
        reloadButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        reloadButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        reloadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadButtonActionPerformed(evt);
            }
        });
        cardsToolbar.add(reloadButton);

        tableColumnsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/table-select-column.png"))); // NOI18N
        tableColumnsButton.setFocusable(false);
        tableColumnsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tableColumnsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tableColumnsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tableColumnsButtonActionPerformed(evt);
            }
        });
        cardsToolbar.add(tableColumnsButton);
        cardsToolbar.add(filler1);

        add(cardsToolbar);

        headerDataCards.setLayout(new java.awt.CardLayout());

        settingsPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        hasColnamesCheckBox.setSelected(true);
        hasColnamesCheckBox.setText("Data has column names");
        hasColnamesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hasColnamesCheckBoxActionPerformed(evt);
            }
        });

        colnamesLastHeaderRow.setText("Column names in last header row");
        colnamesLastHeaderRow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                colnamesLastHeaderRowActionPerformed(evt);
            }
        });

        jLabel2.setText("Header");

        jLabel3.setText("Data");

        jLabel5.setText("Header identifier:");

        headerDefTextField.setText("#");

        jLabel6.setText("Column separator:");

        dataSepTextField.setText("\\t");

        jLabel7.setText("Maximum rows:");

        maxRowSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(250000), Integer.valueOf(0), null, Integer.valueOf(1000)));

        javax.swing.GroupLayout settingsPanelLayout = new javax.swing.GroupLayout(settingsPanel);
        settingsPanel.setLayout(settingsPanelLayout);
        settingsPanelLayout.setHorizontalGroup(
            settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jLabel2))
                .addGap(93, 93, 93)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(colnamesLastHeaderRow)
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel6)
                            .addComponent(jLabel7))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dataSepTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(maxRowSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addGroup(settingsPanelLayout.createSequentialGroup()
                            .addComponent(jLabel5)
                            .addGap(18, 18, 18)
                            .addComponent(headerDefTextField))
                        .addComponent(hasColnamesCheckBox)))
                .addContainerGap(486, Short.MAX_VALUE))
        );
        settingsPanelLayout.setVerticalGroup(
            settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jLabel5)
                    .addComponent(headerDefTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(60, 60, 60)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hasColnamesCheckBox)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(colnamesLastHeaderRow)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(dataSepTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(maxRowSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(169, Short.MAX_VALUE))
        );

        headerDataCards.add(settingsPanel, "settingscard");

        dataScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        dataScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        dataScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        dataTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        dataTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        dataTable.setColumnSelectionAllowed(true);
        dataTable.setGridColor(new java.awt.Color(204, 204, 204));
        dataTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        dataTable.getTableHeader().setReorderingAllowed(false);
        dataScrollPane.setViewportView(dataTable);

        headerDataCards.add(dataScrollPane, "datacard");

        headerScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        headerArea.setColumns(20);
        headerArea.setRows(5);
        headerArea.setTabSize(2);
        headerArea.setBorder(null);
        headerArea.setMargin(new java.awt.Insets(2, 2, 2, 2));
        headerScrollPane.setViewportView(headerArea);

        headerDataCards.add(headerScrollPane, "headercard");

        add(headerDataCards);

        bottomToolbar.setFloatable(false);
        bottomToolbar.setRollover(true);
        bottomToolbar.setMaximumSize(new java.awt.Dimension(32767, 27));
        bottomToolbar.setMinimumSize(new java.awt.Dimension(100, 27));
        bottomToolbar.setPreferredSize(new java.awt.Dimension(200, 27));

        jLabel1.setText(" Header rows:");
        jLabel1.setMaximumSize(new java.awt.Dimension(100, 14));
        jLabel1.setMinimumSize(new java.awt.Dimension(100, 14));
        jLabel1.setPreferredSize(new java.awt.Dimension(100, 14));
        bottomToolbar.add(jLabel1);

        numHeadRowsLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        numHeadRowsLabel.setFocusable(false);
        numHeadRowsLabel.setMaximumSize(new java.awt.Dimension(80, 14));
        numHeadRowsLabel.setMinimumSize(new java.awt.Dimension(80, 14));
        numHeadRowsLabel.setPreferredSize(new java.awt.Dimension(80, 14));
        bottomToolbar.add(numHeadRowsLabel);
        bottomToolbar.add(jSeparator1);

        jLabel8.setText(" Data cols:");
        jLabel8.setMaximumSize(new java.awt.Dimension(100, 14));
        jLabel8.setMinimumSize(new java.awt.Dimension(100, 14));
        jLabel8.setPreferredSize(new java.awt.Dimension(100, 14));
        bottomToolbar.add(jLabel8);

        numDataColsLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        numDataColsLabel.setMaximumSize(new java.awt.Dimension(80, 14));
        numDataColsLabel.setMinimumSize(new java.awt.Dimension(80, 14));
        numDataColsLabel.setPreferredSize(new java.awt.Dimension(80, 14));
        bottomToolbar.add(numDataColsLabel);
        bottomToolbar.add(jSeparator3);

        jLabel4.setText(" Data rows:");
        jLabel4.setMaximumSize(new java.awt.Dimension(100, 14));
        jLabel4.setMinimumSize(new java.awt.Dimension(100, 14));
        jLabel4.setPreferredSize(new java.awt.Dimension(100, 14));
        bottomToolbar.add(jLabel4);

        numDataRowsLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        numDataRowsLabel.setMaximumSize(new java.awt.Dimension(80, 14));
        numDataRowsLabel.setMinimumSize(new java.awt.Dimension(80, 14));
        numDataRowsLabel.setPreferredSize(new java.awt.Dimension(80, 14));
        bottomToolbar.add(numDataRowsLabel);
        bottomToolbar.add(jSeparator4);

        commentLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/exclamation.png"))); // NOI18N
        commentLabel.setText("comment");
        bottomToolbar.add(commentLabel);
        bottomToolbar.add(filler2);

        add(bottomToolbar);
    }// </editor-fold>//GEN-END:initComponents

    private void viewDataButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewDataButtonActionPerformed
        headerdataCL.show(headerDataCards, "datacard");
        headerRegexPanel.setVisible(false);
        dataRegexPanel.setVisible(true);
        dataRegexSubsetCombo.setVisible(true);
        reloadButton.setVisible(false);
        tableColumnsButton.setVisible(true);

        if (!dataloaded) {
            super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                if (isBamOrSamFile(file)) {
                    loadBamData();
                } else {
                    loadData();
                }
                adjustColWidths();
            } catch (Exception ex) {
                // ignore the exceptions here, set some things disabled              
                signalDataLoadError(ex);
            }
            super.setCursor(Cursor.getDefaultCursor());
            dataloaded = true;
        }
    }//GEN-LAST:event_viewDataButtonActionPerformed

    private void viewHeaderButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewHeaderButtonActionPerformed
        headerdataCL.show(headerDataCards, "headercard");
        headerRegexPanel.setVisible(true);
        dataRegexPanel.setVisible(false);
        dataRegexSubsetCombo.setVisible(false);
        reloadButton.setVisible(false);
        tableColumnsButton.setVisible(false);
    }//GEN-LAST:event_viewHeaderButtonActionPerformed

    private void viewSettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewSettingsButtonActionPerformed
        headerdataCL.show(headerDataCards, "settingscard");
        headerRegexPanel.setVisible(false);
        dataRegexPanel.setVisible(false);
        dataRegexSubsetCombo.setVisible(false);
        reloadButton.setVisible(true);
        tableColumnsButton.setVisible(false);
    }//GEN-LAST:event_viewSettingsButtonActionPerformed

    private void colnamesLastHeaderRowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_colnamesLastHeaderRowActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_colnamesLastHeaderRowActionPerformed

    private void hasColnamesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hasColnamesCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_hasColnamesCheckBoxActionPerformed

    /**
     * display a message that data was not loaded properly. Disables some
     * visuals to avoid user looking at missing data.
     *
     */
    private void signalDataLoadError(Exception ex) {
        JOptionPane.showMessageDialog(this,
                "An error occurred while loading data from file:\n" + this.file.getAbsolutePath() + "\n" + ex.getMessage(), "File View Aborted",
                JOptionPane.ERROR_MESSAGE);
        viewHeaderButton.setEnabled(false);
        viewDataButton.setEnabled(false);
        viewSettingsButtonActionPerformed(null);
    }

    private void reloadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadButtonActionPerformed

        // indicate in the GUI that data is not loaded
        numHeadRowsLabel.setText("");
        numDataRowsLabel.setText("");
        commentLabel.setVisible(false);

        // make appear as if data is not loaded. Will force reload
        // when the data panel is clicked
        dataloaded = false;
        data = null;
        colnames = null;
        colclasses = null;
        headerlines.clear();
        headerArea.setText(null);

        // read the header information from the input file
        try {
            if (isBamOrSamFile(file)) {
                loadBamHeader();
            } else {
                loadHeader();
            }
        } catch (Exception ex) {
            // ignore the exception here. Header and Data cards will be disabled
            signalDataLoadError(ex);
            return;
        }

        viewHeaderButton.setEnabled(true);
        viewDataButton.setEnabled(true);
    }//GEN-LAST:event_reloadButtonActionPerformed

    private void tableColumnsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tableColumnsButtonActionPerformed

        // avoid doing anything if the loaded table does not have any columns
        int numcols = colnames.length;
        if (numcols == 0) {
            return;
        }
        tableConfigDialog.setLocationRelativeTo(this);
        tableConfigDialog.setModal(true);

        // make an object holding the currently visible columns
        Set<String> nowVisibleColumns = new HashSet<>();
        ViewerTableModel vtm = (ViewerTableModel) dataTable.getModel();
        for (int i = 0; i < vtm.getColumnCount(); i++) {
            nowVisibleColumns.add(vtm.getColumnName(i));
        }

        // make an object holding all column names, with currently
        // displayed status in first column
        Object[][] cco = new Object[numcols][2];
        for (int i = 0; i < numcols; i++) {
            cco[i][0] = nowVisibleColumns.contains(colnames[i]);
            cco[i][1] = colnames[i];
        }

        DefaultTableModel cctm = (DefaultTableModel) columnChoiceTable.getModel();
        cctm.setDataVector(cco, new String[]{"View", "Column"});
        columnChoiceTable.setAutoCreateRowSorter(true);

        TableColumnModel cctcm = columnChoiceTable.getColumnModel();
        cctcm.getColumn(0).setResizable(false);        
        cctcm.getColumn(0).setMaxWidth(48);
        
        // finally, make the dialog visible
        tableConfigDialog.setVisible(true);
    }//GEN-LAST:event_tableColumnsButtonActionPerformed

    /**
     * performed when user gives up on editing the viewable columns. (Does
     * nothing - the table will be re-generated when user decides to edit
     * again).
     *
     * @param evt
     */
    private void columnsCancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_columnsCancelButtonActionPerformed
        tableConfigDialog.setVisible(false);
    }//GEN-LAST:event_columnsCancelButtonActionPerformed

    /**
     * Changes all tick boxes in the column selection table.
     *
     * @param evt
     */
    private void columnsAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_columnsAllButtonActionPerformed
        TableModel cctm = columnChoiceTable.getModel();
        int cctmrows = cctm.getRowCount();
        for (int i = 0; i < cctmrows; i++) {
            cctm.setValueAt(true, i, 0);
        }
    }//GEN-LAST:event_columnsAllButtonActionPerformed

    private void columnsNoneButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_columnsNoneButtonActionPerformed
        TableModel cctm = columnChoiceTable.getModel();
        int cctmrows = cctm.getRowCount();
        for (int i = 0; i < cctmrows; i++) {
            cctm.setValueAt(false, i, 0);
        }
    }//GEN-LAST:event_columnsNoneButtonActionPerformed

    private void setAllTrue(boolean[] aa) {
        int aalen = aa.length;
        for (int i = 0; i < aalen; i++) {
            aa[i] = true;
        }
    }

    private static int sumBooleanArray(boolean[] aa) {
        int cc = 0;
        int aalen = aa.length;
        for (int i = 0; i < aalen; i++) {
            if (aa[i]) {
                cc++;
            }
        }
        return cc;
    }

    private String[] subsetArray(String[] ss, boolean[] subset) {
        int numsubset = sumBooleanArray(subset);
        String[] ans = new String[numsubset];
        int cc = 0;
        for (int i = 0; i < ss.length; i++) {
            if (subset[i]) {
                ans[cc] = ss[i];
                cc++;
            }
        }
        return ans;
    }

    /**
     * Activated when user decides to change the
     *
     * @param evt
     */
    private void columnsOKButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_columnsOKButtonActionPerformed
        // find out which columns should be visible
        int numcols = colnames.length;
        boolean[] newvisible = new boolean[numcols];
        int visiblecount = 0;
        boolean different = false;
        TableModel ctm = columnChoiceTable.getModel();
        for (int i = 0; i < ctm.getRowCount(); i++) {
            if ((Boolean) ctm.getValueAt(i, 0)) {
                newvisible[i] = true;
                visiblecount++;
            }
            if (newvisible[i] != colvisible[i]) {
                different = true;
            }
        }

        // do not allow the user to deselect all columns
        if (visiblecount == 0) {
            JOptionPane.showMessageDialog(new JPanel(),
                    "Select at least one column.", "Column selection",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (different) {
            // here reset the visible columns 
            System.arraycopy(newvisible, 0, colvisible, 0, numcols);
            Object[][] tempdata = subsetDataArray(data, rowvisible, colvisible);
            resetDataInTable(tempdata);
            numDataColsLabel.setText("" + visiblecount);
        }

        tableConfigDialog.setVisible(false);
    }//GEN-LAST:event_columnsOKButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup HeaderDataButtonGroup;
    private javax.swing.JToolBar bottomToolbar;
    private javax.swing.JToolBar cardsToolbar;
    private javax.swing.JCheckBox colnamesLastHeaderRow;
    private javax.swing.JTable columnChoiceTable;
    private javax.swing.JButton columnsAllButton;
    private javax.swing.JButton columnsCancelButton;
    private javax.swing.JButton columnsNoneButton;
    private javax.swing.JButton columnsOKButton;
    private javax.swing.JLabel commentLabel;
    private javax.swing.JScrollPane dataScrollPane;
    private javax.swing.JTextField dataSepTextField;
    private javax.swing.JTable dataTable;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JCheckBox hasColnamesCheckBox;
    private javax.swing.JTextArea headerArea;
    private javax.swing.JPanel headerDataCards;
    private javax.swing.JTextField headerDefTextField;
    private javax.swing.JScrollPane headerScrollPane;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar.Separator jSeparator4;
    private javax.swing.JSpinner maxRowSpinner;
    private javax.swing.JLabel numDataColsLabel;
    private javax.swing.JLabel numDataRowsLabel;
    private javax.swing.JLabel numHeadRowsLabel;
    private javax.swing.JButton reloadButton;
    private javax.swing.JPanel settingsPanel;
    private javax.swing.JButton tableColumnsButton;
    private javax.swing.JDialog tableConfigDialog;
    private javax.swing.JToggleButton viewDataButton;
    private javax.swing.JToggleButton viewHeaderButton;
    private javax.swing.JToggleButton viewSettingsButton;
    // End of variables declaration//GEN-END:variables

    // function used when creating entries in the data regex combo box
    private Object makeObj(final String item) {
        return new Object() {
            public String toString() {
                return item;
            }
        };
    }

    private void postInitComponents() {

        DocumentListener headerListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterHeaderOrData(0);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterHeaderOrData(0);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterHeaderOrData(0);
            }
        };
        DocumentListener dataListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterHeaderOrData(1);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterHeaderOrData(1);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterHeaderOrData(1);
            }
        };

        headerRegexPanel = new BamfoRegexPanel(headerListener);
        dataRegexPanel = new BamfoRegexPanel(dataListener);
        cardsToolbar.add(headerRegexPanel);
        cardsToolbar.add(dataRegexPanel);

        dataRegexSubsetCombo = new JComboBox();
        dataRegexSubsetCombo.setMinimumSize(new java.awt.Dimension(120, 24));
        dataRegexSubsetCombo.setPreferredSize(new java.awt.Dimension(120, 24));
        cardsToolbar.add(dataRegexSubsetCombo);
    }

    /**
     * This is a wrapper for filterHeader() and filterData(), but allows to
     * toggle the cursor once for all methods
     *
     * @param headdata
     */
    private void filterHeaderOrData(int headdata) {
        //long t1 = System.currentTimeMillis();
        super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (headdata == 0) {
            filterHeader();
            if (this.lastHeaderPatternString.equals(".")) {
                this.headerArea.setEditable(true);
            } else {
                this.headerArea.setEditable(false);
            }
        } else {
            filterData();
            if (lastDataPatternString.equals(".")) {
                ((ViewerTableModel) dataTable.getModel()).setEditable(true);
            } else {
                ((ViewerTableModel) dataTable.getModel()).setEditable(false);
            }
        }
        super.setCursor(Cursor.getDefaultCursor());
        //long t2 = System.currentTimeMillis();
        //System.out.println("filter time: " + (t2 - t1) + "ms");
    }

    /**
     *
     * @param oldp
     * @param newp
     * @return
     *
     * true if oldp and newp differ by one alphanumeric character at either the
     * beginning or the end of the string.
     *
     */
    private boolean isSimplePatternExtension(String oldp, String newp) {
        int oldlen = oldp.length();
        int newlen = newp.length();
        if (1 + oldlen == newlen) {
            char newchar = '.';
            if (newp.startsWith(oldp)) {
                newchar = newp.charAt(newlen - 1);
            } else if (newp.endsWith(oldp)) {
                newchar = newp.charAt(0);
            }
            if ((newchar >= 'a' && newchar <= 'z')
                    || (newchar >= 'A' && newchar <= 'Z')
                    || (newchar >= '0' && newchar <= '9')) {
                return true;
            }
        }
        return false;
    }

    private void filterHeader() {

        // if previous pattern was ".", remember the last header.
        if (lastHeaderPatternString != null && lastHeaderPatternString.equals(".")) {
            String headerAreaText = headerArea.getText();
            headerlines.clear();
            if (!headerAreaText.isEmpty()) {
                String[] newheaderlines = headerArea.getText().split("\n");
                headerlines.addAll(Arrays.asList(newheaderlines));
            }
        }

        // create a pattern from the header regex panel and use it to filter                
        Pattern pattern = headerRegexPanel.getUniversalPattern();
        int numlines = 0;

        if (pattern == null) {
            return;
        }

        // check if pattern is a simple extension of previous pattern
        String patternstring = pattern.pattern();
        boolean pextension = isSimplePatternExtension(lastHeaderPatternString, patternstring);

        StringBuilder sb = new StringBuilder(1024);

        // check for the trivial case
        if (headerRegexPanel.isTrivial()) {
            for (int i = 0; i < headerlines.size(); i++) {
                sb.append(headerlines.get(i)).append("\n");
            }
            numlines = headerlines.size();
        } else {
            Matcher matcher;
            if (pextension) {
                String[] tokens = headerArea.getText().split("\n");
                for (int i = 0; i < tokens.length; i++) {
                    String hline = tokens[i];
                    matcher = pattern.matcher(hline);
                    if (matcher.find()) {
                        sb.append(hline).append("\n");
                        numlines++;
                    }
                }
                tokens = null;
            } else {
                // in nontrivial cases actually go through all the original lines one by one                
                for (int i = 0; i < headerlines.size(); i++) {
                    String hline = headerlines.get(i);
                    matcher = pattern.matcher(hline);
                    if (matcher.find()) {
                        sb.append(hline).append("\n");
                        numlines++;
                    }
                }
            }
        }
        headerArea.setText(sb.toString());
        numHeadRowsLabel.setText("" + numlines);
        lastHeaderPatternString = pattern.pattern();
        if (sb.length() > 1000000) {
            sb = null;
            System.gc();
        }
    }

    private int getIndexOfColByName(int index, String name) {
        for (int i = index; i < dataTable.getColumnCount(); i++) {
            if (dataTable.getColumnName(i).equals(name)) {
                return i;
            }
        }
        // if reached here, the name is not found
        return -1;
    }

    /**
     * This changes the appearance of the data table upon filtering. Makes sure
     * that, e.g., column order and widths stay the same before and after the
     * filtering operation.
     *
     *
     * @param newdata
     *
     * new data to be displayed in the table.
     *
     * @param colnames
     *
     * names of columns.
     *
     */
    @Deprecated
    private void resetDataInTableOld(Object[][] newdata, String[] colnames) {
        TableColumnModel tcm = dataTable.getColumnModel();

        // remember old column widths
        int numcols = colnames.length;
        int[] colwidths = new int[numcols];
        for (int i = 0; i < numcols; i++) {
            colwidths[i] = tcm.getColumn(i).getWidth();
        }
        // remember old column order
        String[] beforecolnames = new String[numcols];
        for (int kk = 0; kk < numcols; kk++) {
            beforecolnames[kk] = dataTable.getColumnName(kk);
        }

        // reset the data
        ((ViewerTableModel) dataTable.getModel()).setDataVector(newdata, colnames);

        // restore old column order -> This is quadratic in number of columns!
        for (int kk = 0; kk < numcols; kk++) {
            int nowindex = getIndexOfColByName(kk, beforecolnames[kk]);
            dataTable.moveColumn(nowindex, kk);
        }

        // make the column widths the same as before
        tcm = dataTable.getColumnModel();
        for (int i = 0; i < numcols; i++) {
            tcm.getColumn(i).setPreferredWidth(colwidths[i]);
            tcm.getColumn(i).setWidth(colwidths[i]);
        }
    }

    private void resetDataInTable(Object[][] newdata) {
        TableColumnModel tcm = dataTable.getColumnModel();
        int numcols = tcm.getColumnCount();

        // remember old column widths  
        HashMap<String, Integer> colwidth = new HashMap<>(numcols * 2);
        for (int i = 0; i < numcols; i++) {
            colwidth.put(dataTable.getColumnName(i), tcm.getColumn(i).getWidth());
        }

        // reset the data
        String[] visiblecolnames = subsetArray(colnames, colvisible);
        ViewerTableModel dtvm = ((ViewerTableModel) dataTable.getModel());
        dtvm.setDataVector(newdata, visiblecolnames);

        tcm = dataTable.getColumnModel();
        int cc = 0;
        for (int i = 0; i < colnames.length; i++) {
            if (colvisible[i]) {
                dtvm.setColumnClass(cc, colclasses[i]);
                Integer oldwidth = colwidth.get(colnames[i]);
                if (oldwidth != null) {
                    tcm.getColumn(cc).setWidth((int) oldwidth);
                    tcm.getColumn(cc).setPreferredWidth((int) oldwidth);
                }
                cc++;
            }
        }
    }

    /**
     * find which column matches the selection in the subset combo box
     *
     * @return
     */
    private int findSubsetColumnId() {
        if (dataRegexSubsetCombo.getSelectedIndex() == 0) {
            return -1;
        }
        String colname = dataRegexSubsetCombo.getSelectedItem().toString();
        for (int i = 0; i < colnames.length; i++) {
            if (colnames[i].equals(colname)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * get a list of row ids that fit regex pattern
     *
     * @param nowdata
     * @param pattern
     * @return
     */
    private ArrayList<Integer> getRegexHitsFromArray(Object[][] nowdata,
            int columnindex, Pattern pattern) {

        int nowrows = nowdata.length;
        int numcols = nowdata[0].length;

        Matcher matcher;
        ArrayList<Integer> hitrows = new ArrayList<Integer>(2 + nowrows / 4);

        if (columnindex < 0) {
            for (int i = 0; i < nowrows; i++) {
                boolean hit = false;
                for (int j = 0; j < numcols && !hit; j++) {
                    if (data[i][j] != null) {
                        String item = (data[i][j]).toString();
                        matcher = pattern.matcher(item);
                        if (matcher.find()) {
                            hit = true;
                            hitrows.add(i);
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i < nowrows; i++) {
                if (data[i][columnindex] != null) {
                    String item = (data[i][columnindex]).toString();
                    matcher = pattern.matcher(item);
                    if (matcher.find()) {
                        hitrows.add(i);
                    }
                }
            }
        }

        return hitrows;
    }

    private boolean[] getBooleanRegexHitsFromArray(Object[][] nowdata,
            int columnindex, Pattern pattern) {

        int nowrows = nowdata.length;
        int numcols = nowdata[0].length;
        boolean[] hits = new boolean[nowrows];

        Matcher matcher;

        if (columnindex < 0) {
            for (int i = 0; i < nowrows; i++) {
                //if (rowvisible[i]) {
                boolean hit = false;
                for (int j = 0; j < numcols && !hit; j++) {
                    if (data[i][j] != null) {
                        String item = (data[i][j]).toString();
                        matcher = pattern.matcher(item);
                        if (matcher.find()) {
                            hit = true;
                            hits[i] = true;
                        }
                    }
                }
                //}
            }
        } else {
            for (int i = 0; i < nowrows; i++) {
                //if (rowvisible[i]) {
                if (data[i][columnindex] != null) {
                    String item = (data[i][columnindex]).toString();
                    matcher = pattern.matcher(item);
                    if (matcher.find()) {
                        hits[i] = true;
                    }
                }
                //}
            }
        }

        return hits;
    }

    /**
     * get a list of row ids that fit a regex pattern
     *
     */
    private ArrayList<Integer> getRegexHitsFromModel(ViewerTableModel nowmodel,
            int columnindex, Pattern pattern) {

        int nowrows = nowmodel.getRowCount();
        int numcols = nowmodel.getColumnCount();
        Matcher matcher;
        ArrayList<Integer> hitrows = new ArrayList<Integer>(2 + nowrows / 4);

        if (columnindex < 0) {
            // search for matches in all the columns
            for (int i = 0; i < nowrows; i++) {
                boolean hit = false;
                for (int j = 0; j < numcols && !hit; j++) {
                    Object nowval = nowmodel.getValueAt(i, j);
                    if (nowval != null) {
                        String item = (nowval).toString();
                        matcher = pattern.matcher(item);
                        if (matcher.find()) {
                            hit = true;
                            hitrows.add(i);
                        }
                    }
                }
            }
        } else {
            // search for matches only in one column
            for (int i = 0; i < nowrows; i++) {
                Object nowval = nowmodel.getValueAt(i, columnindex);
                if (nowval != null) {
                    String item = (nowval).toString();
                    matcher = pattern.matcher(item);
                    if (matcher.find()) {
                        hitrows.add(i);
                    }
                }
            }
        }

        return hitrows;
    }

    /**
     * Function called to scan through whole data to find rows that match a
     * desired pattern.
     */
    @Deprecated
    private void filterDataOld() {

        // create a pattern from the data panel
        Pattern pattern = dataRegexPanel.getUniversalPattern();
        int nowsubset = dataRegexSubsetCombo.getSelectedIndex();
        // if there is a problem (e.g. not well-formed regex) don't do anything
        // if pattern and subset category is the same as before, also don't do anything
        if (pattern == null
                || (pattern.pattern().equals(lastDataPatternString)
                && nowsubset == lastDataPatternSubset)) {
            return;
        }

        // if previous pattern was ".", remember the data from the table
        // allows changes in the table to get remembered during filtering
        if (lastDataPatternString != null && lastDataPatternString.equals(".")) {
            // if this is just about changing the subset, don't do anything
            if (nowsubset != lastDataPatternSubset) {
                // procedure was triggered by subset change, but regex says to show
                // everything, so no need to redraw anything
                lastDataPatternSubset = nowsubset;
                return;
            }
            // if reached here, the filter procedure was triggered by a change in the 
            // regex pattern
            int numrows = dataTable.getModel().getRowCount();
            int numcols = dataTable.getModel().getColumnCount();
            for (int i = 0; i < numrows; i++) {
                for (int j = 0; j < numcols; j++) {
                    data[i][j] = dataTable.getValueAt(i, j);
                }

            }
            for (int j = 0; j < numcols; j++) {
                colnames[j] = dataTable.getColumnName(j);
            }
        }

        int numcols = colnames.length;
        Object[][] tempdata = null;

        // for all-inclusive patterns, use the raw data
        // without making a defensive copy
        if (pattern.pattern().isEmpty() || pattern.pattern().equals(".")) {
            resetDataInTableOld(data, colnames);
            numDataRowsLabel.setText("" + dataTable.getModel().getRowCount());
            lastDataPatternString = ".";
            System.gc();
            return;
        }

        boolean pextension = isSimplePatternExtension(lastDataPatternString, pattern.pattern());

        // if reached here, use pattern matching
        ArrayList<Integer> hitrows = new ArrayList<Integer>();
        int whichcolumn = findSubsetColumnId();


        // check row by row if something matches the filter
        if (pextension) {
            ViewerTableModel nowmodel = (ViewerTableModel) dataTable.getModel();
            // find the row ids that match the pattern
            hitrows = getRegexHitsFromModel(nowmodel, whichcolumn, pattern);
            // make copy of only the hits row -> tempdata
            tempdata = new Object[hitrows.size()][numcols];
            for (int i = 0; i < hitrows.size(); i++) {
                int nowrow = hitrows.get(i);
                for (int j = 0; j < numcols; j++) {
                    tempdata[i][j] = nowmodel.getValueAt(nowrow, j);
                }
            }
        } else {
            // find row ids that match the pattern
            hitrows = getRegexHitsFromArray(data, whichcolumn, pattern);
            // now display only the hit rows
            tempdata = new Object[hitrows.size()][numcols];
            for (int i = 0; i < hitrows.size(); i++) {
                System.arraycopy(data[hitrows.get(i)], 0, tempdata[i], 0, numcols);
            }
        }

        resetDataInTableOld(tempdata, colnames);
        numDataRowsLabel.setText("" + dataTable.getModel().getRowCount());
        this.lastDataPatternString = pattern.pattern();

        if (tempdata != null && tempdata.length > 1024) {
            tempdata = null;
            System.gc();
        }

    }

    /**
     * Function invoked to save changes in table by user into the data[][]
     * array.
     *
     */
    private void saveTableUpdate() {
        int numviscols = sumBooleanArray(colvisible);
        int numcols = colnames.length;

        int[] ddcindex = new int[numviscols];
        int nowcol = 0;
        for (int j = 0; j < numcols; j++) {
            if (colvisible[j]) {
                ddcindex[nowcol] = j;
                nowcol++;
            }
        }

        int numrows = rowvisible.length;
        for (int i = 0; i < numrows; i++) {
            for (int j = 0; j < numviscols; j++) {
                data[i][ddcindex[j]] = dataTable.getValueAt(i, j);
            }
        }
    }

    /**
     * Make a subset of the original data
     *
     * @param dd
     *
     * input 2D array
     *
     * @param rows
     *
     * array - rows set to true are copied into output
     *
     * @param cols
     *
     * array - col set to true are copied into output
     *
     * @return
     *
     * another 2D array which will be a subset of dd, with select rows and
     * columns.
     *
     */
    private Object[][] subsetDataArray(Object[][] dd, boolean[] rows, boolean[] cols) {
        int numansrows = sumBooleanArray(rows);
        int numanscols = sumBooleanArray(cols);
        int numrows = dd.length;
        int numcols = dd[0].length;

        Object[][] ans = new Object[numansrows][numanscols];

        if (numanscols == cols.length) {
            // if copy all the columns, simplify the calculation using System.arraycopy
            int nowrow = 0;
            for (int i = 0; i < numrows; i++) {
                if (rows[i]) {
                    System.arraycopy(data[i], 0, ans[nowrow], 0, numcols);
                    nowrow++;
                }
            }

        } else {
            // if leave some columns out, need to copy elements manually. 
            // First make a map between indexes in colnames and indexes in the ans array            
            int[] ddcindex = new int[numanscols];
            int nowcol = 0;
            for (int j = 0; j < numcols; j++) {
                if (cols[j]) {
                    ddcindex[nowcol] = j;
                    nowcol++;
                }
            }

            // now copy one element at a time
            int nowrow = 0;
            for (int i = 0; i < numrows; i++) {
                if (rows[i]) {
                    for (int j = 0; j < numanscols; j++) {
                        ans[nowrow][j] = dd[i][ddcindex[j]];
                    }
                    nowrow++;
                }
            }
        }

        return ans;
    }

    private void filterData() {

        // create a pattern from the data panel
        Pattern pattern = dataRegexPanel.getUniversalPattern();
        int nowsubset = dataRegexSubsetCombo.getSelectedIndex();
        // if there is a problem (e.g. not well-formed regex) don't do anything
        // if pattern and subset category is the same as before, also don't do anything
        if (pattern == null
                || (pattern.pattern().equals(lastDataPatternString)
                && nowsubset == lastDataPatternSubset)) {
            return;
        }

        // if previous pattern was ".", remember the data from the table
        // allows changes in the table to get remembered during filtering
        if (lastDataPatternString != null && lastDataPatternString.equals(".")) {
            // if this is just about changing the subset, don't do anything
            if (nowsubset != lastDataPatternSubset) {
                // procedure was triggered by subset change, but regex says to show
                // everything, so no need to redraw anything
                lastDataPatternSubset = nowsubset;
                return;
            }

            saveTableUpdate();
        }

        // for all-inclusive patterns, use the raw data
        // without making a defensive copy
        if (pattern.pattern().isEmpty() || pattern.pattern().equals(".")) {
            // make a copy of the data      
            rowvisible = new boolean[data.length];
            setAllTrue(rowvisible);
            Object[][] tempdata = subsetDataArray(data, rowvisible, colvisible);
            resetDataInTable(tempdata);
            numDataRowsLabel.setText("" + tempdata.length);
            lastDataPatternString = ".";
            System.gc();
            return;
        }

        boolean pextension = isSimplePatternExtension(lastDataPatternString, pattern.pattern());

        // if reached here, use pattern matching        
        int whichcolumn = findSubsetColumnId();

        // check row by row if something matches the filter
        if (!pextension) {
            setAllTrue(rowvisible);
        }

        // find row ids that match the pattern and display subset of rows
        rowvisible = getBooleanRegexHitsFromArray(data, whichcolumn, pattern);
        Object[][] tempdata = subsetDataArray(data, rowvisible, colvisible);
        resetDataInTable(tempdata);
        numDataRowsLabel.setText("" + tempdata.length);
        this.lastDataPatternString = pattern.pattern();

        if (tempdata != null && tempdata.length > 1024) {
            tempdata = null;
            System.gc();
        }
    }

    /**
     * The descriptor for a standard viewer is the path for the file.
     *
     * @return
     */
    @Override
    public String getViewerDescriptor() {
        return file.getAbsolutePath();
    }

    private String getColNamesString(String sep, boolean rearrange) {
        StringBuilder sb = new StringBuilder();
        ViewerTableModel tm = (ViewerTableModel) this.dataTable.getModel();

        if (tm.getColumnCount() == 0) {
            return null;
        }

        if (hasColnamesCheckBox.isSelected()) {
            if (colnamesLastHeaderRow.isSelected()) {
                return null;
            }
            if (rearrange) {
                sb.append(tm.getColumnName(dataTable.convertColumnIndexToModel(0)));
                for (int i = 1; i < tm.getColumnCount(); i++) {
                    sb.append(sep).append(tm.getColumnName(dataTable.convertColumnIndexToModel(i)));
                }
            } else {
                sb.append(tm.getColumnName(0));
                for (int i = 1; i < tm.getColumnCount(); i++) {
                    sb.append(sep).append(tm.getColumnName(i));
                }
            }
            sb.append("\n");
        } else {
            return null;
        }

        return sb.toString();
    }

    @Override
    public boolean canSaveAs() {
        //if (!dataloaded || isBamFile(file)) {
        if (!dataloaded) {
            return false;
        }
        return canSaveAs;
    }

    /**
     * Similar to saveAsText, but this one write the contents into a sam format
     *
     * @param newfile
     * @param confirm
     *
     */
    private void saveAsSam(File newfile, int confirm) {

        try {
            OutputStream os = OutputStreamMaker.makeOutputStream(newfile);
            if (confirm == 1) {
                // save entire table                
                for (int i = 0; i < headerlines.size(); i++) {
                    os.write((headerlines.get(i) + "\n").getBytes());
                }
                for (int i = 0; i < data.length; i++) {
                    StringBuilder sb = new StringBuilder();
                    Object[] temprow = data[i];
                    sb.append(temprow[0]);
                    for (int j = 0; j < temprow.length; j++) {
                        if (temprow[j] == null) {
                            sb.append("\t");
                        } else {
                            sb.append("\t").append(temprow[j]);
                        }
                    }
                    if (!sb.toString().endsWith("\n")) {
                        sb.append("\n");
                    }
                    os.write(sb.toString().getBytes());
                }
            } else {
                // save just the current view
                String headerText = headerArea.getText();
                if (!headerText.isEmpty()) {
                    os.write(headerText.getBytes());
                    if (!headerText.endsWith("\n")) {
                        os.write("\n".getBytes());
                    }
                }
                ViewerTableModel tm = (ViewerTableModel) dataTable.getModel();
                for (int i = 0; i < tm.getRowCount(); i++) {
                    int imodel = dataTable.convertRowIndexToModel(i);
                    StringBuilder sb = new StringBuilder();
                    // for Sam/Bam output, maybe re-order rows but never columns
                    sb.append(tm.getValueAt(imodel, 0));
                    for (int j = 1; j < tm.getColumnCount(); j++) {
                        if (tm.getValueAt(imodel, j) == null) {
                            sb.append("\t");
                        } else {
                            sb.append("\t").append(tm.getValueAt(imodel, j));
                        }
                    }
                    if (!sb.toString().endsWith("\n")) {
                        sb.append("\n");
                    }
                    os.write(sb.toString().getBytes());
                }
            }
            os.close();
        } catch (Exception ex) {
            System.out.println("Error while saving sam file: " + ex.getMessage());
        }
    }

    /**
     *
     * Similar to saveAsText, but this one writes contents of this viewer into a
     * bam format
     *
     */
    private void saveAsBam(File newfile, int confirm) {

        // save first into a temporary sam file        
        File tempSAMfile = new File(newfile.getAbsolutePath() + ".temp.sam");
        while (tempSAMfile.exists()) {
            tempSAMfile = new File(tempSAMfile.getAbsolutePath() + ".temp.sam");
        }

        saveAsSam(tempSAMfile, confirm);

        // now read from the sam file and copy into the bam file
        SAMFileReader inputSam = new SAMFileReader(tempSAMfile);
        BamfoCommon.updateValidationStringency(inputSam, "SILENT");

        SAMFileWriter outputSam;
        SAMFileHeader outheader = inputSam.getFileHeader().clone();
        outputSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(
                outheader, true, newfile);

        for (final SAMRecord record : inputSam) {
            outputSam.addAlignment(record);
        }

        inputSam.close();
        outputSam.close();

        // clean up the temporary sam file
        tempSAMfile.delete();
    }

    /**
     *
     * @param newfile
     *
     * destination file
     *
     * @param confirm
     *
     * set this to 1 to save all the loaded data set this to 2 to save only
     * currently filtered data
     *
     */
    private void saveAsText(File newfile, int confirm) {

        // if got here, either save current view or entire table
        try {
            String sep = dataSepTextField.getText();
            if (sep.equals("\\t")) {
                sep = "\t";
            }

            OutputStream os = OutputStreamMaker.makeOutputStream(newfile);
            if (confirm == 1) {
                // save entire table
                String herecolnames = getColNamesString(sep, false);

                for (int i = 0; i < headerlines.size(); i++) {
                    os.write((headerlines.get(i) + "\n").getBytes());
                }
                if (herecolnames != null) {
                    os.write(herecolnames.getBytes());
                }
                for (int i = 0; i < data.length; i++) {
                    StringBuilder sb = new StringBuilder();
                    Object[] temprow = data[i];
                    sb.append(temprow[0]);
                    for (int j = 0; j < temprow.length; j++) {
                        if (temprow[j] == null) {
                            sb.append(sep);
                        } else {
                            sb.append(sep).append(temprow[j]);
                        }
                    }
                    if (!sb.toString().endsWith("\n")) {
                        sb.append("\n");
                    }
                    os.write(sb.toString().getBytes());
                }
            } else {
                // save just the current view
                String headerText = headerArea.getText();
                if (!headerText.isEmpty()) {
                    os.write(headerText.getBytes());
                    if (!headerText.endsWith("\n")) {
                        os.write("\n".getBytes());
                    }
                }
                String herecolnames = getColNamesString(sep, true);
                if (herecolnames != null) {
                    os.write(herecolnames.getBytes());
                }
                ViewerTableModel tm = (ViewerTableModel) dataTable.getModel();
                for (int i = 0; i < tm.getRowCount(); i++) {
                    int imodel = dataTable.convertRowIndexToModel(i);
                    StringBuilder sb = new StringBuilder();
                    sb.append(tm.getValueAt(imodel, dataTable.convertColumnIndexToModel(0)));
                    for (int j = 1; j < tm.getColumnCount(); j++) {
                        int jmodel = dataTable.convertColumnIndexToModel(j);
                        if (tm.getValueAt(imodel, jmodel) == null) {
                            sb.append(sep);
                        } else {
                            sb.append(sep).append(tm.getValueAt(imodel, jmodel));
                        }
                    }
                    if (!sb.toString().endsWith("\n")) {
                        sb.append("\n");
                    }
                    os.write(sb.toString().getBytes());
                }
            }
            os.close();
        } catch (Exception ex) {
            System.out.println("Error saving file here: " + ex.getMessage());
        }
    }

    /**
     * saves the contents of the viewer into a text file
     *
     * @param newfile
     */
    @Override
    public void saveAs(File newfile) {

        if (newfile == null) {
            return;
        }

        if (!canSaveAs()) {
            JOptionPane.showMessageDialog(this,
                    "Cannot save the file at this time.\n"
                    + "Load both header and data and try again.", "Load data before saving",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // check that the new file is not already open
        String newfilepath = newfile.getAbsolutePath();
        int numTabsInPane = pane.getTabCount();
        if (!file.getAbsolutePath().equals(newfile.getAbsolutePath())) {
            for (int i = 0; i < numTabsInPane; i++) {
                BamfoViewer tabviewer = (BamfoViewer) pane.getComponentAt(i);
                if (newfilepath.equals(tabviewer.getViewerDescriptor())) {
                    JOptionPane.showMessageDialog(this,
                            "File " + newfile.getAbsolutePath() + " is already open.\n"
                            + "Close the open file before overwriting it.", "Save Aborted",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }

        if (!overwrite(newfile)) {
            return;
        }

        // check what to save if panels are masking some data
        int confirm = 2;
        if (!headerRegexPanel.isTrivial() || !dataRegexPanel.isTrivial()) {
            // ask if need to change all table or just current view        
            String[] options = new String[3];
            options[2] = "Current view";
            options[1] = "Original table";
            options[0] = "Cancel";
            confirm = JOptionPane.showOptionDialog((Component) null,
                    "Current view uses regex filters.\nDo you want to save the current view, or the entire table?",
                    "Save table", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);
            if (confirm == 0) {
                return;
            }
        }

        if (isBamFile(newfile)) {
            saveAsBam(newfile, confirm);
        } else if (isSamFile(newfile)) {
            saveAsSam(newfile, confirm);
        } else {
            saveAsText(newfile, confirm);
        }

        String oldpath = file.getAbsolutePath();
        // rename the tab in the tabbed pane.
        for (int i = 0; i < numTabsInPane; i++) {
            BamfoViewer tabviewer = (BamfoViewer) pane.getComponentAt(i);
            if (oldpath.equals(tabviewer.getViewerDescriptor())) {
                file = newfile.getAbsoluteFile();
                pane.setTitleAt(i, file.getName());
                pane.setToolTipTextAt(i, newfilepath);
                return;
            }
        }
    }
}

class ViewerTableModel extends DefaultTableModel {

    private final Class[] columnclasses;
    private boolean isEditable = true;

    @Override
    public boolean isCellEditable(int row, int column) {
        return isEditable;
    }

    public void setEditable(boolean isEditable) {
        this.isEditable = isEditable;
    }

    /**
     * Creates a table model with some data and column names. All columns are
     * set to Object class.
     *
     * @param data
     * @param colnames
     */
    ViewerTableModel(Object[][] data, String[] colnames, Class[] columnclasses) {
        super(data, colnames);
        this.columnclasses = new Class[columnclasses.length];
        for (int i = 0; i < columnclasses.length; i++) {
            this.columnclasses[i] = columnclasses[i];
        }
    }

    public void setColumnClass(int index, Class c) {
        if (index < columnclasses.length) {
            columnclasses[index] = c;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex < columnclasses.length) {
            return columnclasses[columnIndex];
        } else {
            return Object.class;
        }
    }
}

class BamfoTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        // let the default renderer do something
        JLabel supercomp = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        // change the border to add a little padding
        supercomp.setBorder(new EmptyBorder(1, 4, 1, 4));

        return supercomp;
    }
}

/**
 * Implements a column that allows user to select rows of a table, 
 * then use the space bar or the return key to toggle a boolean value
 * stored in one of the columns. (Used in columnChoiceTable)
 * 
 * @author Tomasz Konopka
 */
class BamfoTableToggleListener implements KeyListener {

    private final JTable table;
    private final int togglecolumn;

    public BamfoTableToggleListener(JTable tab, int togglecolumn) {
        this.table = tab;
        this.togglecolumn = togglecolumn;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        
        // only react to presses of a spacebar or a return key
        char echar = e.getKeyChar();
        if (echar == ' ' || echar == '\n' || echar == '\r') {
            // find current selection
            int[] selectedrows = table.getSelectedRows();
            if (selectedrows == null || selectedrows.length == 0) {
                return;
            }
            // figure out if all selected rows have the same selection status
            boolean allselected = true;
            for (int i = 0; i < selectedrows.length; i++) {
                if (!(Boolean) table.getValueAt(selectedrows[i], togglecolumn)) {
                    allselected = false;
                }
            }            
            // if rows are selected, unselect them
            // if rows are not selected, select them
            for (int i = 0; i < selectedrows.length; i++) {
                table.setValueAt(!allselected, selectedrows[i], togglecolumn);
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // nothing
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // nothing
    }
}