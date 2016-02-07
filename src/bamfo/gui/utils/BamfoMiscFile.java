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

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JPanel;

/**
 *
 * @author tomasz
 */
public class BamfoMiscFile {

    private static File superChooseFileOrDir(boolean choosefile, boolean choosedir, File currentdir) {

        // create the file chooser
        JPanel panel = new JPanel();
        JFileChooser mychooser = new JFileChooser();

        // customize the look
        if (choosedir) {
            if (choosefile) {
                mychooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            } else {
                mychooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            }
        } else {
            mychooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        }
        if (currentdir != null) {
            mychooser.setCurrentDirectory(currentdir);
        }

        int answer = mychooser.showOpenDialog(panel);
        if (answer == JFileChooser.APPROVE_OPTION) {
            return mychooser.getSelectedFile();
        } else {
            return null;
        }

    }

    /**
     * Generic function to ask the user for a file. Use a dialog box.
     *
     * @return
     */
    public static File chooseFile() {
        return superChooseFileOrDir(true, false, null);
    }

    /**
     * Generic function to ask the user for a directory. Use a dialog box.
     *
     * @return
     */
    public static File chooseDir() {
        return superChooseFileOrDir(false, true, null);
    }

    public static File chooseDir(File currentdir) {
        return superChooseFileOrDir(false, true, currentdir);
    }
    
    public static File chooseFileOrDir() {
        return superChooseFileOrDir(true, true, null);
    }

    public static File chooseFileOrDir(File currentdir) {
        return superChooseFileOrDir(true, true, currentdir);
    }

    public static File chooseFile(File currentdir) {
        return superChooseFileOrDir(true, false, currentdir);
    }
}
