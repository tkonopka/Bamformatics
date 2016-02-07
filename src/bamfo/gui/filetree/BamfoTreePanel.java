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
package bamfo.gui.filetree;

import bamfo.gui.components.BamfoRegexPanel;
import java.awt.Cursor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author tomasz
 */
public class BamfoTreePanel extends javax.swing.JPanel {

    // the base model will contain all nodes, but will never be visible
    // nor assigned to a tree
    private final BamfoTreeModel basemodel;
    // the shown model may contain all nodes or a subset of nodes (based on regex)
    // this model will be placed into the JTree
    private BamfoTreeModel shownmodel;
    private final BamfoRegexPanel treeRegexPanel;
    // the expanded hashmap will remember expanded state during filtering.
    private HashMap<String, Boolean> expanded = new HashMap<>(8);
    // panel will display only executables or only regular files
    private int nodeclass;
    // remember what the last pattern. This is useful when changing the filtering.
    private String lastPattern = ".";
    // panel can omit storing files with certain extensions
    private String[] omitExtensions;
    
    /**
     * Creates new form BamfoTreePanel
     *
     * @param btm
     *
     * model
     *
     * @param label
     *
     * Label to display as header of the panel
     *
     * @param nodeclass
     *
     * class of files to display. Use BamfoTreeNode static integers.
     *
     */
    public BamfoTreePanel(BamfoTreeModel btm, String label, int nodeclass, String[] omitExtensions) {
        initComponents();
        // set the private variables, which are directly related to the arguments
        panelLabel.setText(label);
        this.basemodel = btm;
        this.nodeclass = nodeclass;        
        if (omitExtensions==null) {
            this.omitExtensions = null;
        } else {            
            this.omitExtensions = Arrays.copyOf(omitExtensions, omitExtensions.length);            
        }

        // prepare and set the regex panel.
        DocumentListener filterListener = new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterTree();
            }
        };
        treeRegexPanel = new BamfoRegexPanel(filterListener);
        treeToolbar.add(treeRegexPanel);

        // the shown model will initially be the full model, but immediately filtered.
        this.shownmodel = btm;
        filterTree();

        // tweak the look and behavior of the tree in the panel
        tree.setRootVisible(false);
        tree.setModel(shownmodel);
        BamfoTreeCellRenderer rend = new BamfoTreeCellRenderer();
        tree.setCellRenderer(rend);
        tree.setDragEnabled(false);
        // allow the tree to display tooltips
        ToolTipManager.sharedInstance().registerComponent(tree);
    }

    public void setPanelLabel(String label) {
        panelLabel.setText(label);
    }

    // delete a node from the basemodel and then the shown model
    public void deleteNode(BamfoTreeNode node) {
        basemodel.delete(node);
        filterTree(true);
    }

    /**
     * This is forces the tree to be re-filtered, which means the model and tree
     * and recomputed and displayed anew.
     *
     */
    public void refresh() {
        super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        //System.out.println("refresh before: expanded has "+expanded.size());
        filterTree();
        //System.out.println("refresh after: expanded has "+expanded.size());
        super.setCursor(Cursor.getDefaultCursor());
    }

    public void printExpanded(String msg) {
        System.out.println(msg+" - expanded has "+expanded.size());
    }
    
    public void rePopulate(String fileAbsolutePath) {
        BamfoTreeNode dirnode = basemodel.findNodeFromPath(fileAbsolutePath);
        if (dirnode != null) {
            super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            dirnode.populate(omitExtensions);
            lastPattern = null;
        }
        filterTree(true);
        super.setCursor(Cursor.getDefaultCursor());
    }

    public void collapseAll() {
        expanded.clear();
        expandSomeDirs();
    }

    public BamfoTreeNode getLastSelectedPathComponent() {
        return (BamfoTreeNode) tree.getLastSelectedPathComponent();
    }

    public BamfoTreeNode getRoot() {
        return (BamfoTreeNode) shownmodel.getRoot();
    }

    /**
     * This method is bad because it makes the GUI hang if two calls are sent at
     * the same time...
     *
     * @param pattern
     */
    public synchronized void setFilter(String pattern) {
        treeRegexPanel.setPattern(pattern);
        this.refresh();
    }

    /**
     * This is the generic function to filter a tree.
     *
     * @param force
     *
     * set to true if the tree filter/copy should take place no matter what. Set
     * to false if speedup shortcuts can be taken
     *
     */
    private synchronized void filterTree(boolean force) {

        // create a pattern from the data panel
        Pattern pattern = treeRegexPanel.getUniversalPattern();

        // if the pattern is null, it means some error in the pattern compilation
        // just leave the whole setup as it is.
        if (pattern == null) {
            return;
        }

        // cheat with simple patterns, do not filter, just show the raw model.
        if (!force) {
            if (pattern.pattern().equals(".")) {
                tree.setModel(basemodel);
                expandSomeDirs();
                lastPattern = ".";
                return;
            }
        }

        // before using the filter, remember the expansion state.
        // Then create a new model and display it.        
        rememberExpandedState((TreeNode) tree.getModel().getRoot());

        BamfoTreeNode shownroot = BamfoTreeNode.copy(
                (BamfoTreeNode) basemodel.getRoot(),
                pattern, nodeclass);

        if (shownroot == null) {
            shownmodel = new BamfoTreeModel();
        } else {
            shownmodel = new BamfoTreeModel(shownroot);
        }
        tree.setModel(shownmodel);
        expandSomeDirs();
        lastPattern = pattern.pattern();
    }

    /**
     * Function allows to show sub-tree of the basemodel.
     */
    private synchronized void filterTree() {
        filterTree(false);
    }

    /**
     * This function can be used set the expanded state of the tree using a
     * hashmap made elsewhere.
     *
     *
     * @param map
     *
     *
     */
    public synchronized void setExpanded(HashMap<String, Boolean> map) {
        this.expanded = map;
        expandSomeDirs();
    }

    public HashMap<String, Boolean> getExpanded() {
        rememberExpandedState((BamfoTreeNode) tree.getModel().getRoot());
        return this.expanded;
    }

    /**
     * Looks at all the rows in the tree and expands nodes that have been saved
     * in the expanded map
     */
    private synchronized void expandSomeDirs() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath p = tree.getPathForRow(i);
            if (expanded.containsKey(p.toString())) {
                tree.expandPath(p);
            }
        }
    }

    public int getAllChildCount() {
        BamfoTreeNode rootnode = (BamfoTreeNode) shownmodel.getRoot();
        if (rootnode == null) {
            return 0;
        }
        return BamfoTreeNode.getAllChildrenCount(rootnode);
    }

    /**
     * Reads through the current model and puts all expanded treepaths into the
     * "expanded" hashmap.
     */
    private synchronized void rememberExpandedState(TreeNode node) {
        //System.out.println("remembering at "+node.toString());
        if (!node.getAllowsChildren()) {
            return;
        }
        if (node.isLeaf()) {
            return;
        }

        // if reached here, the node could be a directory and so need 
        // to change if it is expanded and thus if it needs to put into the hashmap
        TreePath p = new TreePath(shownmodel.getPathToRoot(node));
        if (tree.isExpanded(p)) {
            expanded.put(p.toString(), Boolean.TRUE);
            int numchildren = node.getChildCount();
            for (int i = 0; i < numchildren; i++) {
                rememberExpandedState(node.getChildAt(i));
            }

        } else {
            expanded.remove(p.toString());
        }
    }

    /**
     * provides external class direct access to the tree in this panel
     *
     *
     * @return
     *
     *
     */
    public JTree getTree() {
        return tree;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        treeToolbar = new javax.swing.JToolBar();
        panelLabel = new javax.swing.JLabel();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        jScrollPane1 = new javax.swing.JScrollPane();
        tree = new javax.swing.JTree();

        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 0, 0));
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        treeToolbar.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 4, 1, 1));
        treeToolbar.setFloatable(false);
        treeToolbar.setRollover(true);
        treeToolbar.setMaximumSize(new java.awt.Dimension(32767, 30));
        treeToolbar.setMinimumSize(new java.awt.Dimension(78, 30));
        treeToolbar.setPreferredSize(new java.awt.Dimension(78, 30));

        panelLabel.setText("Panel label");
        treeToolbar.add(panelLabel);
        treeToolbar.add(filler2);

        add(treeToolbar);

        jScrollPane1.setMinimumSize(new java.awt.Dimension(70, 60));

        tree.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root node");
        tree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jScrollPane1.setViewportView(tree);

        add(jScrollPane1);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.Box.Filler filler2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel panelLabel;
    private javax.swing.JTree tree;
    private javax.swing.JToolBar treeToolbar;
    // End of variables declaration//GEN-END:variables
}
