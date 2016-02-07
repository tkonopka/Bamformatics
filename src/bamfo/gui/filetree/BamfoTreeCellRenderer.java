package bamfo.gui.filetree;

import bamfo.gui.decorations.DecoratedImageIcon;
import bamfo.gui.utils.BamfoIconMaker;
import java.awt.Component;
import java.io.File;
import java.text.DecimalFormat;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 *
 * This cell renderer displays some custom icons in a tree based on common
 * extensions/permissions.
 *
 * Displays file sizes when pointer hovers over a tree node.
 *
 * @author tomasz
 */
public class BamfoTreeCellRenderer extends DefaultTreeCellRenderer {

    private final DecimalFormat sizeformat = new DecimalFormat("0.0");

    /**
     * Sets up a rendered with a default icons for the nodes. Some of the other
     * cells' features are also modified: border.
     */
    public BamfoTreeCellRenderer() {
        setLeafIcon(BamfoIconMaker.getDocIcon());
        setClosedIcon(BamfoIconMaker.getClosedIcon());
        setOpenIcon(BamfoIconMaker.getOpenIcon());
        // make some space around the label so that decorated icons don't get trimmed
        this.setBorder(new EmptyBorder(1, 2, 1, 1));
    }

    private String getFileSize(long size) {

        double dblsize;
        if (size > 1073741824) {
            dblsize = size / 1073741824.0;
            return (sizeformat.format(dblsize) + "GB");
        } else if (size > 1048576) {
            dblsize = size / 1048576.0;
            return (sizeformat.format(dblsize) + "MB");
        } else if (size > 1024) {
            dblsize = size / 1024.0;
            return (sizeformat.format(dblsize) + "KB");
        } else {
            return (size + "B");
        }
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
            Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        // get the component that would be drawn by default
        Component ret = super.getTreeCellRendererComponent(tree, value,
                selected, expanded, leaf, row, hasFocus);

        // the node values in the ProjectsTree are files        
        BamfoTreeNode node = (BamfoTreeNode) value;
        File f = node.getNodeFile();

        // The root node has a null file. Avoid exception with this if.
        if (f == null) {
            return ret;
        }

        // set a tooltip that includes the size of the file and its full path
        if (f.canRead()) {
            String nowtooltip = "";
            if (f.isFile()) {
                nowtooltip = getFileSize(f.length()) + "    ";
            }
            super.setToolTipText(nowtooltip + f.getAbsolutePath());
        } else {
            super.setToolTipText("");
        }

        // the component given by the DefaultTreeCellRenderer is a label        
        JLabel celllabel = (JLabel) ret;
        //BamfoTreeCellLabel celllabel = new BamfoTreeCellLabel(f, (JLabel) ret);
        // Edit the label slightly with an icon
        DecoratedImageIcon decoratedIcon;
        if (node.isOnBlacklist()) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getFolderStandIcon());
        } else {
            if (node.getAllowsChildren()) {
                decoratedIcon = BamfoIconMaker.makeIconForDir(f);
            } else {
                decoratedIcon = BamfoIconMaker.makeIconForFile(f, node.getName());
            }
        }
        if (decoratedIcon != null) {
            celllabel.setIcon(decoratedIcon);
        }
        return celllabel;
    }

    /**
     * This is used in the tree to display files. It is just like a label, but
     * generates its tooltip only when requested.
     *
     * Using this may not affect memory use, since the component will store a
     * file object as opposed to a sting for the label and a tooltip, but it
     * will not need to look up the size of the file unless a tooltip is asked
     * for.
     *
     * @author Tomasz Konopka
     */
    class BamfoTreeCellLabel extends JLabel {

        File cellfile;

        public BamfoTreeCellLabel(File file, JLabel label) {
            super(label.getIcon(), label.getHorizontalAlignment());
            this.cellfile = file;
        }

        @Override
        public String getText() {
            if (cellfile == null) {
                return "";
            }
            return cellfile.getName();
        }

        /**
         *
         * Computes a tooltip for the component.
         *
         * @return
         *
         * for directories, just the absolute file path for file, the file size
         * and the absolute file path
         *
         */
        @Override
        public String getToolTipText() {
            if (cellfile.canRead()) {
                String nowtooltip = "";
                if (cellfile.isFile()) {
                    nowtooltip = getFileSize(cellfile.length()) + "    ";
                }
                return (nowtooltip + cellfile.getAbsolutePath());
            } else {
                return ("");
            }
        }
    }
}
