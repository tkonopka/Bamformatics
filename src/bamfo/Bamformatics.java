/*
 * Copyright 2012-2014 Tomasz Konopka.
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
package bamfo;

import javax.swing.UIManager;

/**
 * Gateway to multiple tools for processing bam files
 *
 * @author tkonopka
 */
public class Bamformatics {

    final static String version = "0.2.3";

    public static String getVersion() {
        return version;
    }

    public static void printHelp() {
        System.out.println("Bamformatics is a suite of tools for manipulating alignment and associated files");
        System.out.println("\nAuthor: Tomasz Konopka (bamformatics@gmail.com)\n");
        System.out.println("Usage: java -jar Bamformatics.jar TYPE [options]\n");
        System.out.println("   annotatevariants   - add ID codes to a vcf file");
        System.out.println("   callvariants       - produce variant calls");
        System.out.println("   defaults           - set default parameters");
        System.out.println("   errors             - estimate substitution error rates");
        System.out.println("   filtervariants     - filter variant calls");
        System.out.println("   find               - look for indels and other properties in bam");
        System.out.println("   gui                - graphical user interface");
        System.out.println("   noquals            - remove base qualities");
        System.out.println("   split              - split a bam file into two using ids");
        System.out.println("   stats              - collect various statistics about alignment file");
        System.out.println("   tracks             - compute genotype-able coverage (and other) tracks");
        System.out.println("   variantdetails     - create custom tables from several vcfs and bams");
        System.out.println("   version            - display the current version");
        System.out.println();
    }

    public static void printExperimental() {        
        System.out.println("Bamformatics is a suite of tools for manipulating alignment and associated files");
        System.out.println("\nAuthor: Tomasz Konopka (bamformatics@gmail.be)\n");
        System.out.println("Usage of experimental tools: java -jar Bamformatics.jar TYPE [options]\n");
        System.out.println("   entropy            - compute alignment entropy tracks");
        //System.out.println("   gui                - graphical user interface");
        System.out.println("   psl2bam            - create bam files from blat psl");
        System.out.println();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        // if necessary, print the Help menu for all triage functions        
        if (args.length == 0) {
            printHelp();
            return;
        }

        String tooltype = args[0].toLowerCase();
        // extract the parameters passed on to the triage subprograms
        String[] newargs;
        if (args.length == 1) {
            newargs = null;
        } else {
            newargs = new String[args.length - 1];
            System.arraycopy(args, 1, newargs, 0, args.length-1);            
        }

        // the tooltype determine what kind of task is submitted to the executor
        // (could be a switch statement here if I switch to Java 7...)
        if (tooltype.equals("tracks")) {
            new bamfo.tracks.BamfoTracks(newargs, System.out).run();
        } else if (tooltype.equals("callvariants")) {
            new bamfo.call.BamfoVcf(newargs, System.out).run();
        } else if (tooltype.equals("filtervariants")) {
            new bamfo.call.BamfoVcfFilter(newargs).run();
        } else if (tooltype.equals("multivcf") || tooltype.equals("variantdetails")) {
            new bamfo.call.BamfoVcfDetail(newargs).run();
        } else if (tooltype.equals("annotatevariants")) {
            new bamfo.call.BamfoAnnotateVariants(newargs).run();
        } else if (tooltype.equals("errors")) {
            new bamfo.stats.BamfoErrors(newargs).run();
        } else if (tooltype.equals("defaults")) {
            new bamfo.utils.BamfoDefaults(newargs).run();
        } else if (tooltype.equals("split")) {
            new bamfo.rebam.BamfoSplit(newargs).run();
        } else if (tooltype.equals("stats")) {
            new bamfo.stats.BamfoStats(newargs).run();
        } else if (tooltype.equals("find")) {
            new bamfo.rebam.BamfoFind(newargs).run();
        } else if (tooltype.equals("version")) {
            System.out.println("Bamformatics v" + getVersion());
            // here define more experimental tools
        } else if (tooltype.equals("psl2bam")) {
            new bamfo.psl.BamfoPsl2Bam(newargs).run();
        } else if (tooltype.equals("entropy")) {
            new bamfo.tracks.BamfoEntropy(newargs).run();
        } else if (tooltype.equals("noquals")) {
            new bamfo.rebam.BamfoNoQuals(newargs).run();
        } else if (tooltype.equals("experimental")) {
            printExperimental();
        } else if (tooltype.equals("gui")) {
            try {
                // Set System L&F
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException ex) {
                java.util.logging.Logger.getLogger(Bamformatics.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (InstantiationException ex) {
                java.util.logging.Logger.getLogger(Bamformatics.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                java.util.logging.Logger.getLogger(Bamformatics.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(Bamformatics.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }


            //Create and display the GUI
            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {                    
                    new bamfo.gui.BamfoGUI().setVisible(true);                    
                }
            });
        } else {
            System.out.println("Unrecognized Bamformatics command " + args[0]);
        }

    }
}
