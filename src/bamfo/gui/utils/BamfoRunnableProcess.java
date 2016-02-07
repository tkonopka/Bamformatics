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
import java.util.Iterator;
import java.util.List;
import javax.swing.JTextArea;

/**
 * A means to make a "Process" into a runnable object (so that it can be
 * given to an executore service). The class provides a means to destroy the
 * underlying process without necessarily interrupting the runnable.
 *
 * The destroyProcess functionality simplifies implementations of run() because
 * run() will not have to check for the thread interruption flags.
 *
 * @author tomasz
 */
public abstract class BamfoRunnableProcess extends BamfoRunnableTask {
    
    protected Process process = null;
    protected final ProcessBuilder pb;
    

    /**
     * creates a new runnable process. The constructor does not start the
     * process described by the builder. The run() method should do that.
     *
     * @param pb
     */
    public BamfoRunnableProcess(ProcessBuilder pb, JTextArea outarea) {
        super(outarea);
        this.pb = pb;
        
        // set the descriptor string for this task
        File scriptfile = new File(pb.command().get(0));        
        this.shortname = scriptfile.getName();        
        this.executable = scriptfile.getAbsolutePath();
        this.arguments = getProcessArguments();
        this.directory = pb.directory().getAbsolutePath();
    }
    
    /**
     * 
     * @return 
     * 
     * the arguments passed onto the process, one argument per line.
     * 
     */
    private String getProcessArguments() {
        // fetch the command from the process builder
        List<String> thiscommand = pb.command();
        Iterator<String> it = thiscommand.iterator();

        StringBuilder sb = new StringBuilder();

        // for the arguments, skip the first item
        if (it.hasNext()) {
            it.next();
        }

        // return the remainging arguments, one item on a line
        while (it.hasNext()) {
            sb.append(it.next()).append("\n");
        }
        return sb.toString();
    }

    /**
     * very important method. It destroys the process if it is running. This
     * should effectively stop the run() method.
     *
     */
    @Override
    public void stopTask() {
        if (process != null) {
            process.destroy();
        }
    }
    
    /**
     * This method should start the process and deal with the input/output. The
     * method should end with process.waitFor() in order to finish when the
     * process either runs to completion or is terminated via destroyProcess().
     *
     */
    @Override
    public abstract void run();
}
