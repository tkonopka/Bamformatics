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

import java.awt.Component;
import java.io.File;
import javax.swing.JOptionPane;

/**
 * This interface makes sure that all viewers can identify what file they are
 * currently watching.
 *
 *
 * @author tomasz
 */
public abstract class BamfoViewer extends javax.swing.JPanel {

    boolean canSave = false;
    boolean canSaveAs = false;
    // once a file is save once, the destination file can be recorded here.
    // when this is not null, the save() option will be activated.
    File savefile = null;    

    /**
     * This should output an identifier of the file/object that is being
     * displayed in the viewer.
     *
     * @return
     */
    public abstract String getViewerDescriptor();

    public boolean canSave() {   
        return (savefile != null && canSaveAs());
    }

    public boolean canSaveAs() {
        return canSaveAs;
    }

    boolean overwrite(File ff) {
        // if file does not exist, that's the same as overwriting a file
        if (!ff.exists()) {
            return true;
        }

        int confirm = JOptionPane.showConfirmDialog((Component) null,
                "File " + ff.getName() + " already exists. Overwrite it?",
                "Overwrite", JOptionPane.YES_NO_OPTION);

        // abort if user aborts
        if (confirm == JOptionPane.YES_OPTION) {
            return true;
        } else {
            return false;
        }

    }

    public void save() {
        if (savefile != null) {
            saveAs(savefile);
        }
    }

    /**
     * Each implementation of the 
     * @param newfile 
     */
    public abstract void saveAs(File newfile);
        
}
