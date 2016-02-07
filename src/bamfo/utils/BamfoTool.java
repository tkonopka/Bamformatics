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
package bamfo.utils;

import java.io.PrintStream;

/**
 * A generic wrapper for a tool in the Bamformatics Toolkit.
 * 
 * 
 * It provides some logging facilities. This is useful because the stream used 
 * for reporting message can be used in another class to observe progress.
 * 
 * It provides a boolean value that should be used in the run() method 
 * to check if everything has been properly initialized.
 * 
 * @author tomasz
 */
public abstract class BamfoTool implements Runnable {
    
    // boolean should be used before performing the run() method 
    // to check if intialization succeeded
    protected boolean isReady = false;
    // object prints out log messages in a unified format
    protected final BamfoLog bamfolog;
    // other messages can be sent here (can be a substituted for System.out)
    protected final PrintStream outputStream;
    
    public BamfoTool() {
        bamfolog = new BamfoLog();        
        outputStream = System.out;
    }
    
    public BamfoTool(PrintStream logstream) {
        bamfolog = new BamfoLog(logstream);
        outputStream = logstream;
    }
            
}
