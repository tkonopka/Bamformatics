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
package bamfo.gui.configurations;

import bamfo.gui.utils.BamfoRunnableTask;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextArea;

/**
 * A type of runnable task suitable for variant calling or filtering.
 *
 * @author tomasz
 */
public class VariantsRunnableTask extends BamfoRunnableTask {

    private final Runnable task;
    private BufferedReader logreader;
    private final boolean ok;
    private final PipedOutputStream outstream;    
    
    public VariantsRunnableTask(Runnable task, PipedOutputStream outstream, JTextArea area,
            String program, String arguments) {
        super(area);
        this.task = task;
        this.outstream = outstream;

        try {
            PipedInputStream is = new PipedInputStream(outstream);
            logreader = new BufferedReader(new InputStreamReader(is));
        } catch (Exception ex) {
            System.out.println("exception while creating stream: "+ex.getMessage());            
            ok = false;
            return;
        }

        this.shortname = program;
        this.executable = program;
        this.arguments = arguments;
        this.directory = "";
        ok = true;
    }

    @Override
    public void run() {
        // only run if it has been properly initialized
        if (!ok) {
            return;
        }
        
        // start listening for the output in a separate thread
        ExecutorService readlogexecutor = Executors.newSingleThreadExecutor();
        readlogexecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String s;
                    while ((s = logreader.readLine()) != null) {
                        processOutput(s);
                    }
                } catch (IOException ex) {
                    System.out.println("Error listening to output: " + ex.getMessage());
                }
            }
        });

        // execute the task        
        task.run();
                        
        try {
            // close the output stream. This will cause the readlogexecutor to exit.
            outstream.close();            
        } catch (IOException ex) {
            Logger.getLogger(VariantsRunnableTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // end the executor that listens to the output
        readlogexecutor.shutdown();
        try {
            readlogexecutor.awaitTermination(1000, TimeUnit.DAYS);            
        } catch (Exception ex) {
            System.out.println("exception while executing task: " + ex.getMessage());
            setExitval(1);
            return;
        }

        // finally, record that the process finished successfully
        setExitval(0);
    }

    /**
     * Unfortunately this does not do anything. Use the Future returned by submit(Runnable) 
     * to an executor to get access to the Thread of the runnable and interrupt the 
     * process like that.
     */
    @Override
    public void stopTask() {        
    }
}
