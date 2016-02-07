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
package bamfo.tracks;

import bamfo.utils.BamfoCommon;
import bamfo.utils.BamfoSettings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsequtils.file.OutputStreamMaker;
import jsequtils.file.RleWriter;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMSequenceRecord;

/**
 *
 * @author tkonopka
 */
abstract class TracksCompute implements Runnable {
    
    final BamfoSettings settings;
    static final int TRACK_COVERAGE = 0;
    static final int TRACK_READSTART = 1;
    static final int TRACK_READEND = 2;
    static final int TRACK_READSTARTEND = 3;
    static final int TRACK_MEDMAPQUAL = 4;
    final File bamfile;    
    final File outdir;   
    
    public TracksCompute(BamfoSettings settings, File bamfile, File outdir) {
        this.settings = settings;
        this.bamfile = bamfile;
        this.outdir = outdir;
    }
    
    void outputRleTrack(int[] track, String nowRefName, File outdir) throws FileNotFoundException, IOException {
        OutputStream os = OutputStreamMaker.makeOutputStream(outdir.getCanonicalPath(), nowRefName + ".txt.gz");
        RleWriter.write(os, track, true);
        os.close();
    }
       
    void outputRleTrack(double[] track, String nowRefName, File outdir) throws FileNotFoundException, IOException {
        OutputStream os = OutputStreamMaker.makeOutputStream(outdir.getCanonicalPath(), nowRefName + ".txt.gz");
        RleWriter.write(os, track, true);
        os.close();
    }
    
    
    void saveNotPresentChromosomes(boolean[] chroms, SAMFileHeader samHeader, File outdir, double defaultvalue) throws FileNotFoundException, IOException {        
        // at the end, check that all chromosomes have been output
        // if not, coverage must have been zero
        for (int i = 0; i < chroms.length; i++) {
            if (!chroms[i]) {
                // create dummy array and output it
                SAMSequenceRecord ssr = samHeader.getSequence(i);
                int chromlen = ssr.getSequenceLength();
                double[] blank = new double[chromlen];
                
                // fill the blank with the default value
                if (defaultvalue!=0) {
                    for (int j=0; j<chromlen; j++) {
                        blank[j]=defaultvalue;
                    }
                }
                
                String nowRefName = ssr.getSequenceName();
                outputRleTrack(blank, nowRefName, outdir);                
            }
        }
    }
    
    abstract void computeTrack(SAMFileReader inputSam) throws FileNotFoundException, IOException;
    
    /**
     * After the utility is initialized, it has to be "executed" by invoking this method.
     * If initialization failed, this method does not do anything.
     * 
     */
    @Override
    public void run() {
        // start processing, open the SAM file and start computing
        SAMFileReader inputSam = new SAMFileReader(bamfile);
        BamfoCommon.updateValidationStringency(inputSam, settings.getValidate());
        
        try {
            computeTrack(inputSam);
        } catch (IOException ex) {
            System.out.println("computing error");
            Logger.getLogger(TracksCoverage.class.getName()).log(Level.SEVERE, null, ex);
        }

        // close input SAM file
        inputSam.close();
    }
            
}
