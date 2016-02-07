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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * The class can be an element in a tree because it extends
 * DefaultMutableTreeNode.
 *
 * The node value in this class has to be a File object. The class thus provides
 * some shortcuts to get File attributes connected with the the tree nodes.
 *
 *
 * @author tomasz
 */
public class BamfoTreeNode extends DefaultMutableTreeNode {

    // these are used in selection of files to display in the panels
    public static int NODECLASS_NONE = -1;
    public static int NODECLASS_DATAONLY = 0;
    public static int NODECLASS_EXECONLY = 1;
    public static int NODECLASS_DATAEXEC = 2;
    private boolean onBlacklist = false;

    /**
     * Generic constructor for a directory of file. Also suitable constructor
     * for root node.
     *
     * @param f
     *
     *
     * @param allowsChildren
     *
     * set this to true to force showing
     *
     */
    public BamfoTreeNode(File f, boolean allowsChildren, String[] omitExts) {
        super(f, allowsChildren);
        if (allowsChildren) {
            this.populate(omitExts);
        }
    }

    private BamfoTreeNode(File f, boolean allowsChildren, boolean populate, String[] omitExts) {
        super(f, allowsChildren);
        if (allowsChildren && populate) {
            this.populate(omitExts);
        }
    }

    /**
     * A copy constructor that records the file name but does not automatically
     * populate the node with children based on lookups in the file system.
     *
     *
     * @param node
     *
     * node to be copied, (cannot have a null file?)
     *
     */
    public BamfoTreeNode(BamfoTreeNode node) {
        super(((File) node.userObject).getAbsoluteFile(), node.allowsChildren);
        this.onBlacklist = node.onBlacklist;
    }

    /**
     * A function that can be used to recursively copy a subset of a tree.
     *
     * @param node
     * @param pattern
     * @param nodeclass
     *
     * Only copies files that have a certain class (data, executable, either, or
     * none)
     *
     * @return
     */
    public static BamfoTreeNode copy(final BamfoTreeNode node, final Pattern pattern, final int nodeclass) {

        // deal with leaf node case. This is the exit point of recursion.
        if (node.isLeaf()) {
            File nodefile = node.getNodeFile();
            if (nodefile == null) {
                return null;
            }
            // always keep directories. Check if executable status matches        
            if (node.allowsChildren || (nodeclass == BamfoTreeNode.NODECLASS_DATAEXEC)
                    || ((nodeclass == BamfoTreeNode.NODECLASS_EXECONLY) && nodefile.canExecute())
                    || ((nodeclass == BamfoTreeNode.NODECLASS_DATAONLY) && !nodefile.canExecute())) {
                // decide whether to copy the node or not based on filename
                Matcher m = pattern.matcher(nodefile.getAbsolutePath());
                if (m.find()) {
                    return new BamfoTreeNode(node);
                }
            }
            return null;
        }

        // for non-leaf node, use recursion to process each child
        // create a temporary node. The if null is for the case of the root node
        BamfoTreeNode newnode;
        if (node.userObject != null) {
            newnode = new BamfoTreeNode(node);
        } else {
            newnode = new BamfoTreeNode((File) null, true, null);
        }

        int numchildren = node.getChildCount();
        for (int i = 0; i < numchildren; i++) {
            BamfoTreeNode childcopy = copy((BamfoTreeNode) node.getChildAt(i), pattern, nodeclass);
            if (childcopy != null) {
                newnode.add(childcopy);
            }
        }

        // check if any of the "node" children were added to the copy.
        // If they have, some children matched the pattern. 
        // If they haven't, none of the children match the pattern, -> return a signal
        if (newnode.getChildCount() > 0) {
            return newnode;
        }

        // last chance, perhaps this node itself satisfies the condition
        if (node.userObject == null) {
            return newnode;
        }
        Matcher m = pattern.matcher(newnode.getNodeFile().getAbsolutePath());
        if (m.find()) {
            return newnode;
        }
        return null;
    }

    /**
     *
     * @return
     *
     * the File reference for this node.
     *
     */
    public File getNodeFile() {
        if (userObject == null) {
            return null;
        }
        return (File) userObject;
    }

    /**
     *
     * @return
     *
     * the getName() information about the unerlying File.
     */
    public String getName() {
        if (userObject == null) {
            return null;
        }
        return ((File) userObject).getName();
    }

    /**
     *
     * @return
     *
     * the isFile() information about the underlying node
     *
     */
    public boolean isFile() {
        if (userObject == null) {
            return false;
        }
        return ((File) userObject).isFile();
    }

    /**
     *
     * This function is used by the tree to set labels near the icons.
     *
     * @return
     *
     * The name of this file.
     *
     */
    @Override
    public String toString() {

        // maybe return an empty string. 
        if (userObject == null) {
            return "";
        } else {
            return this.getName();
        }
    }

    public String getTreeString(int level) {
        StringBuilder sb = new StringBuilder();
        String spacer = new String(new char[level + 1]).replace("\0", " ");
        sb.append(spacer).append(this.getName()).append("\n");
        int numchildren = this.getChildCount();
        for (int i = 0; i < numchildren; i++) {
            BamfoTreeNode thischild = (BamfoTreeNode) this.getChildAt(i);
            sb.append(thischild.getTreeString(level + 1));
        }

        return sb.toString();
    }

    /**
     * Looks through the file system and add children to the current node.
     */
    public final void populate(String[] omitExts) {

        File dir = (File) userObject;
        if (dir == null) {
            return;
        }

        if (!dir.isDirectory()) {
            this.setAllowsChildren(false);
            return;
        }

        if (!dir.canExecute()) {
            this.setAllowsChildren(false);
            return;
        }

        //clear all existing information and reload files                
        this.removeAllChildren();

        // get contents of directory and save it into sub-directories and sub-files
        // Perhaps this could be done with the FileUtils library?                
        String[] indir = dir.list();
        int indirlen = indir.length;
        if (indirlen == 0) {
            return;
        }
        // sort the array so that it files/directories appear sorted alphabetically.
        Arrays.sort(indir);

        // temporary objects that will separate files/folders
        ArrayList<File> subdirs = new ArrayList<File>();
        ArrayList<File> subfiles = new ArrayList<File>();

        int omitLen = 0;
        if (omitExts != null) {
            omitLen = omitExts.length;
        }
        
        // look at all the candidates and place them into one of the subdirs/subfiles arrays
        for (int i = 0; i < indirlen; i++) {
            File tmpfile = new File(dir, indir[i]);
            if (!tmpfile.isHidden()) {
                if (tmpfile.isDirectory()) {
                    subdirs.add(tmpfile);
                } else if (tmpfile.isFile()) {
                    // among files, perhaps omit certain files because of extensions                    
                    if (omitLen == 0) {
                        subfiles.add(tmpfile);
                    } else {
                        boolean nowomit = false;
                        String nowname = tmpfile.getName();
                        for (int k = 0; k < omitLen & !nowomit; k++) {
                            if (nowname.endsWith(omitExts[k])) {
                                nowomit = true;
                            }
                        }
                        if (!nowomit) {
                            subfiles.add(tmpfile);
                        }
                    }
                }
            }
        }

        // add subdirectories into the tree
        int subdirlen = subdirs.size();
        for (int i = 0; i < subdirlen; i++) {
            this.add(new BamfoTreeNode(subdirs.get(i), true, omitExts));
        }
        // then add subfiles
        int subfilelen = subfiles.size();
        for (int i = 0; i < subfilelen; i++) {
            this.add(new BamfoTreeNode(subfiles.get(i), false, omitExts));
        }

    }

    /**
     *
     * @param node
     *
     *
     * @return
     *
     * returns the number of leaf nodes under this node
     */
    public static int getAllChildrenCount(BamfoTreeNode node) {
        if (!node.allowsChildren) {
            return 1;
        } else {
            int numchildren = node.getChildCount();
            int ans = 1; // includes oneself as a file?
            for (int i = 0; i < numchildren; i++) {
                ans += getAllChildrenCount((BamfoTreeNode) node.getChildAt(i));
            }
            return ans;
        }
    }

    /**
     *
     * recursive function looking for a node that describes a path with
     * specified absolute path.
     *
     * @param findAbsolutePath
     * @return
     *
     * a node that describes the desired path.
     *
     */
    BamfoTreeNode findNodeFromPath(String findAbsolutePath) {

        // consider this node as a candidate if it describes a file        
        File nowfile = (File) userObject;
        if (nowfile != null) {
            String herepath = nowfile.getAbsolutePath();
            if (!findAbsolutePath.startsWith(herepath)) {
                return null;
            }
            if (findAbsolutePath.equals(herepath)) {
                return this;
            }
        }
        // look through the children
        for (int i = 0; i < this.getChildCount(); i++) {
            BamfoTreeNode answer = ((BamfoTreeNode) this.getChildAt(i)).findNodeFromPath(findAbsolutePath);
            if (answer != null) {
                return answer;
            }
        }
        // if reached here, give up 
        return null;
    }

    public boolean isOnBlacklist() {
        return onBlacklist;
    }

    public void setOnBlacklist(boolean onBlacklist) {
        this.onBlacklist = onBlacklist;
    }
}
