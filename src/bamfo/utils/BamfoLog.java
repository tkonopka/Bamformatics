package bamfo.utils;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Basically, a wrapper for println. The logged string is written to a
 * previously specified logstream. The logged output also has some special
 * formating including the date.
 *
 *
 * @author tomasz
 */
public class BamfoLog {

    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    private final PrintStream logstream;
    private boolean verbose = false;

    public BamfoLog(PrintStream logstream) {
        this.logstream = logstream;
    }
       
    public BamfoLog() {
        this.logstream = System.out;    
    }
    
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
     
    /**
     * 
     * @param s 
     * 
     */
    public void log(String s) {
        if (verbose) {
            logstream.println("[B][" + sdf.format(new Date()) + "] " + s);
            logstream.flush();
        }
    }
    
    /**
     *
     * @param verbose
     * @param s
     */
    public void log(boolean verbose, String s) {
        if (verbose) {
            logstream.println("[B][" + sdf.format(new Date()) + "] " + s);
            logstream.flush();
        }
    }
}
