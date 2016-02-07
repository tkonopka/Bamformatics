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

import bamfo.gui.BamfoGUI;
import bamfo.gui.decorations.DecoratedImageIcon;
import java.io.File;
import javax.swing.ImageIcon;
import jsequtils.file.FileExtensionGetter;

/**
 * A way to associate icons with files of certain types, e.g. paper icon for
 * documents, scroll icon for scripts, etc.
 *
 *
 *
 * @author tomasz
 */
public class BamfoIconMaker {

    private static final ImageIcon docIcon = getIcon("document.png");
    private static final ImageIcon scriptIcon = getIcon("script.png");
    private static final ImageIcon indexIcon = getIcon("document-attribute-i.png");
    private static final ImageIcon docTableIcon = getIcon("document-table.png");
    private static final ImageIcon docPdfIcon = getIcon("document-pdf.png");
    private static final ImageIcon docTextIcon = getIcon("document-text.png");
    private static final ImageIcon imageIcon = getIcon("document-image.png");
    //private static final ImageIcon transparentIcon = getIcon("transparent.png");
    private static final ImageIcon closedIcon = getIcon("folder-horizontal.png");
    private static final ImageIcon openIcon = getIcon("folder-horizontal-open.png");
    private static final ImageIcon crossIcon = getIcon("cross-small-gray2.png");
    private static final ImageIcon notepadIcon = getIcon("notebook.png");
    private static final ImageIcon boxIcon = getIcon("box.png");
    private static final ImageIcon woodenBoxIcon = getIcon("wooden-box.png");
    private static final ImageIcon folderStandIcon = getIcon("folder-stand.png");

    /**
     * This is not really a file icon, but it retrieves a very particular icon
     * (the cross for closing files) from the same location on disk as all the
     * other icons.
     *
     * @return
     */
    public static ImageIcon getCrossIcon() {
        return crossIcon;
    }

    /**
     * Like makeIconForFile, but assumes the file is a directory
     *
     * @param f
     * @return
     */
    public static DecoratedImageIcon makeIconForDir(File f) {
        // the default icon will be a document icon
        DecoratedImageIcon decoratedIcon = null;

        if (!f.exists()) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getClosedIcon());
            decoratedIcon.setDecoration(DecoratedImageIcon.Decoration.prohibition, DecoratedImageIcon.BOTTOMRIGHT);
            return decoratedIcon;
        }

        // not a file, so a directory            
        if (!f.canExecute()) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getClosedIcon());
            decoratedIcon.setDecoration(DecoratedImageIcon.Decoration.prohibition, DecoratedImageIcon.BOTTOMRIGHT);
            return decoratedIcon;
        }

        return decoratedIcon;
    }
    
    /**
     *
     * @param f
     * @return
     */
    public static DecoratedImageIcon makeIconForFile(File f, String filename) {
        // the default icon will be a document icon
        DecoratedImageIcon decoratedIcon = null;

        if (!f.exists()) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getDocIcon());
            decoratedIcon.setDecoration(DecoratedImageIcon.Decoration.prohibition, DecoratedImageIcon.BOTTOMRIGHT);
            return decoratedIcon;
        }

        if (f.canExecute()) {
            return new DecoratedImageIcon(BamfoIconMaker.getScriptIcon());
        }

        // split the file name into parts and display icons as function of filename
        String[] detail = FileExtensionGetter.getExtensionSplit(filename);

        // look at primary extension
        if (detail[1].equals("bai") || detail[1].equals("idx") || detail[1].equals("fai")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getIndexIcon());
        } else if (detail[1].equals("csv") || detail[1].equals("tsv")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getDocTableIcon());
        } else if (detail[1].equals("vcf") || detail[1].equals("bed")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getDocTableIcon());
        } else if (detail[1].equals("log") || detail[1].equals("out") || detail[1].equals("txt")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getDocTextIcon());
        } else if (detail[1].equals("pdf")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getDocPdfIcon());
        } else if (detail[1].equals("bash") || detail[1].equals("sh") || detail[1].equals("bat")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getScriptIcon());
            // a script may be executable or not. Signal if not
            // an executable script would have returned previously. 
            // If reached here it is not executable so needs to be flagged
            decoratedIcon.setDecoration(DecoratedImageIcon.Decoration.exclamation_yellow,
                    DecoratedImageIcon.BOTTOMRIGHT);
        } else if (detail[1].equals("png") || detail[1].equals("jpg") || detail[1].equals("eps")) {
            decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getImageIcon());
        }                

        return decoratedIcon;
    }

    /**
     * This is not used?
     *
     * @param f
     * @param expanded
     * @return
     */
    public static DecoratedImageIcon makeIconForFile(File f, boolean expanded) {

        DecoratedImageIcon decoratedIcon = makeIconForFile(f, f.getName());
        if (f.isFile() && !f.isDirectory() && !f.canExecute()) {
            return decoratedIcon;
        } else {
            if (expanded) {
                decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getOpenIcon());
            } else {
                decoratedIcon = new DecoratedImageIcon(BamfoIconMaker.getClosedIcon());
            }
        }
        return decoratedIcon;
    }

    /**
     * Shortcut to get an icon with a given name from the Bamfo.icons resource
     *
     * @param name
     *
     * file name of icon
     *
     * @return
     *
     * the image of the icon
     */
    private static ImageIcon getIcon(String name) {
        return new ImageIcon(BamfoGUI.class.getResource("icons/" + name));
    }

    public static ImageIcon getDocIcon() {
        return docIcon;
    }

    public static ImageIcon getScriptIcon() {
        return scriptIcon;
    }

    public static ImageIcon getIndexIcon() {
        return indexIcon;
    }

    public static ImageIcon getDocTableIcon() {
        return docTableIcon;
    }

    public static ImageIcon getDocPdfIcon() {
        return docPdfIcon;
    }

    public static ImageIcon getDocTextIcon() {
        return docTextIcon;
    }

    public static ImageIcon getImageIcon() {
        return imageIcon;
    }

    public static ImageIcon getClosedIcon() {
        return closedIcon;
    }

    public static ImageIcon getOpenIcon() {
        return openIcon;
    }

    public static ImageIcon getNotepadIcon() {
        return notepadIcon;
    }
    public static ImageIcon getBoxIcon() {
        return boxIcon;
    }
    public static ImageIcon getWoodenBoxIcon() {
        return woodenBoxIcon;
    }
    public static ImageIcon getFolderStandIcon() {
        return folderStandIcon;
    }
    
}
