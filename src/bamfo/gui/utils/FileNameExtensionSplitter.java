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

/**
 *
 * @author tomasz
 */
public class FileNameExtensionSplitter {
            
    /**
     * splits file name into three parts: name, extension, and another extension
     * if the file is compressed.
     *
     *
     * @param name
     *
     * the name of the file
     *
     * @return
     *
     * Some examples:
     *
     * hello.txt.gz -> (hello) (txt) (gz) 
     * bye.log -> (bye) (log)
     * something.very.complicated -> (something.very) (complicated) 
     * archive.zip -> (archive) () (zip)
     *
     */
    private static String[] split(String name) {
        String[] ans = {"", "", ""};

        // first check for compression extension
        if (name.endsWith(".zip") || name.endsWith(".bz2")) {
            ans[2] = name.substring(name.length() - 3, name.length());
            name = name.substring(0, name.length() - 4);
        } else if (name.endsWith(".gz")) {
            ans[2] = "gz";
            name = name.substring(0, name.length() - 3);
        }

        // find conventional extension
        int lastdot = name.lastIndexOf(".");

        // give up if there is no extension
        if (lastdot < 0) {
            ans[0] = name;
            return ans;
        }

        // there is an extension, so get it
        ans[0] = name.substring(0, lastdot);
        ans[1] = name.substring(lastdot + 1, name.length());

        return ans;
    }

}
