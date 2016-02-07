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
import javax.swing.tree.DefaultTreeModel;

/**
 *
 * @author tomasz
 */
public class BamfoTreeModel extends DefaultTreeModel {

    /**
     * Creates a new model. Elements of the model will be BamfoTreeNode-s, i.e.
     * essentially File-s.
     *
     * The root element of the model will always be a node where the file is
     * null. Note, the node itself is not null, but the file object in this node
     * is null.
     *
     */
    public BamfoTreeModel() {
        super(new BamfoTreeNode((File) null, true, null), true);
    }

    /**
     * Another constructor that uses a given node as its root.
     *
     * @param node
     */
    public BamfoTreeModel(BamfoTreeNode node) {        
        super(node, true);
    }

    /**
     * 
     * @return 
     * 
     * an array with all directories watched in this tree model
     * 
     */
    public File[] getAllProjects() {
        int numprojects = super.root.getChildCount();
       if (numprojects==0) {
           return null;
       } else {
           File[] ans = new File[numprojects];
           for (int i=0; i<ans.length; i++) {
               ans[i]=((BamfoTreeNode)super.root.getChildAt(i)).getNodeFile();
           }
           return ans;
       }
    }
    
    /**
     * Deletes/removes the file described by the node from the tree model
     * 
     * @param node 
     */
    public void delete(BamfoTreeNode node) {
        if (node.getNodeFile() != null) {
            deleteFile((BamfoTreeNode) this.root, node.getNodeFile());
        }
    }

    private void deleteFile(BamfoTreeNode traverse, File f) {
        if (traverse.getNodeFile() != null) {            
            if (traverse.getNodeFile().getAbsolutePath().equals(f.getAbsolutePath())) {
                this.removeNodeFromParent(traverse);
                return;
            }
        }

        int numchildren = traverse.getChildCount();
        for (int i = numchildren - 1; i >= 0; i--) {
            deleteFile((BamfoTreeNode) traverse.getChildAt(i), f);
        }
    }

    public boolean addDirAtRoot(File dir, String[] omitExts) {

        String dirabsolute = dir.getAbsolutePath()+File.separator;
        BamfoTreeNode bamforoot = (BamfoTreeNode) this.root;        
        
        // compare the new path to those already in the tree model
        // in the loop either give up if new dir is subdir of an existing one
        // or remove all items that are subdirs.
        int numprojects = this.getChildCount(root);        
        for (int i = numprojects - 1; i >= 0; i--) {
            BamfoTreeNode thischild = (BamfoTreeNode) this.getChild(root, i);
            String thispath = thischild.getNodeFile().getAbsolutePath()+File.separator;
            if (dirabsolute.startsWith(thispath)) {
                return false;
            }
            if (thispath.startsWith(dirabsolute)) {
                // have to remove this item
                this.removeNodeFromParent(thischild);
            }
        }

        // if reached here, the item should be inserted
        // create a new node (will automatically populate itself with files)
        BamfoTreeNode newchild = new BamfoTreeNode(dir, dir.isDirectory(), omitExts);        

        numprojects = this.getChildCount(root);        
        String dirname = dir.getName();

        for (int i = 0; i < numprojects; i++) {
            BamfoTreeNode thischild = (BamfoTreeNode) this.getChild(root, i);
            String childname = thischild.getName();
            int compresult = childname.compareTo(dirname);
            if (compresult > 0) {
                // item i is lexicographically greater than dirname
                // just add the dir at this location and exit                
                this.insertNodeInto(newchild, bamforoot, i);
                return true;
            } else if (compresult == 0) {                
                String childabsolute = thischild.getNodeFile().getAbsolutePath();
                if (childabsolute.compareTo(dirabsolute) > 0) {                    
                    this.insertNodeInto(newchild, bamforoot, i);
                    return true;
                }
            } // there is another possibilty, that compresult<0. Then ignore and look at next item.
        }

        // if reached here, the new child is last in the list        
        this.insertNodeInto(newchild, bamforoot, numprojects);

        return true;
    }

    @Override
    public String toString() {
        return ((BamfoTreeNode) root).getTreeString(0);
    }
    
    /**
     * get the node attached to the root from which the node in the argument descends
     * 
     * @param node
     * @return 
     */
    public static BamfoTreeNode getFirstParent(BamfoTreeNode node) {
        if (node ==null ){
            return null;
        }        
        if (((BamfoTreeNode)node.getParent()).getNodeFile()==null) {
            return node;
        }
        return getFirstParent((BamfoTreeNode)node.getParent());
    }
    
    /**
     * calls analogous function on the root BamfoTreeNode of the model.
     * 
     * @param absolutePath
     * @return 
     */
    public BamfoTreeNode findNodeFromPath(String absolutePath) {        
        return(((BamfoTreeNode) super.root).findNodeFromPath(absolutePath));
    }
}
