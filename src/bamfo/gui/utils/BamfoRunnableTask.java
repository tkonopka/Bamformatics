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

import java.io.IOException;
import javax.swing.JTextArea;

/**
 * A wrapper for a runnable
 *
 * @author tomasz
 */
public abstract class BamfoRunnableTask implements Runnable {

    // the strings are descriptors for the task
    protected String executable ="";
    protected String arguments="";
    protected String directory="";
    protected String shortname = "task";
    // exitval will be a status indicator
    protected int exitval = -1;
    // the output of the task will be passed on to the appendable object
    protected final JTextArea outarea;

    /**
     * output from this runnable task will be displayed via the text area
     * object.
     *
     * @param appendable
     */
    public BamfoRunnableTask(JTextArea outarea) {
        this.outarea = outarea;
    }

    /**
     * get a string representing the executable for this runnable.
     *
     * @return
     */
    public String getExecutable() {
        return executable;
    }

    /**
     * get a summary of the arguments passed to this runnable.
     *
     * @return
     */
    public String getArguments() {
        return arguments;
    }

    public String getShortname() {
        return shortname;
    }

    public String getDirectory() {
        return directory;
    }
            
    /**
     * pass the string to the output area.
     *
     * @param s
     *
     */
    protected void processOutput(String s) throws IOException {        
        synchronized (outarea) {
            outarea.append(s+"\n");
            outarea.repaint();
        }
    }

    /**
     *
     * check how the process is doing. Note, this function returns the value
     * stored in a flag. It does not look at the process itself. Thus, the run()
     * method that invokes the process should set the flag!
     *
     * @return
     *
     * 0 if the process exited normally. 1 or other positive value if process
     * was terminated or exited with an error. -1 if the process is still
     * running.
     *
     */
    public int getExitval() {
        return exitval;
    }

    /**
     *
     * sets the flag returned by getExitval()
     *
     * @param exitval
     */
    protected void setExitval(int exitval) {
        this.exitval = exitval;
    }
    
    @Override
    public abstract void run();
    
    /**
     * This should be the means to stop/interrupt the task.
     * 
     */
    public abstract void stopTask();
}
