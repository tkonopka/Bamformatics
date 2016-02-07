/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bamfo.psl;

import java.io.File;

/**
 *
 * @author tkonopka
 */
public class Psl2multibamSettings {
    File genomefile = null;
    String readgroup = "blat";
    boolean help = false;
    
    File psl1 = null;
    File psl2 = null;
    
    File fastq1 = null;
    File fastq2 = null;
    
    String outprefix= "";
    
    int maxsubopt = Integer.MAX_VALUE;
    int maxinsertsize = Integer.MAX_VALUE;
    
}
