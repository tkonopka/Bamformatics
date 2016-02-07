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
package bamfo.gui.scripts;

import bamfo.gui.utils.BamfoRunnableProcess;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.swing.JTextArea;

/**
 *
 * @author tomasz
 */
public class ScriptRunnableProcess extends BamfoRunnableProcess {

    /**
     * just calls the super constructor.
     *
     * @param pb
     */
    public ScriptRunnableProcess(ProcessBuilder pb, JTextArea outarea) {
        super(pb, outarea);
    }

    /**
     * executes the process. The output is dealt with using the functions
     * defined in BamfoRunnableProcess.
     *
     */
    @Override
    public void run() {
        try {
            // create and run the process
            process = this.pb.start();
            // monitor the output of the process
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String s;
            while ((s = br.readLine()) != null) {
                processOutput(s);
            }
            setExitval(process.waitFor());

        } catch (IOException ex) {
            System.out.println("IOexception in process: " + ex.getMessage());
        } catch (InterruptedException ex) {
            System.out.println("Interrupted: " + ex.getMessage());
        }
    }
}
