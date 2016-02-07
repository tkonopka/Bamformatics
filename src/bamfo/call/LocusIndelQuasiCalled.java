/*
 * Copyright 2012 Tomasz Konopka.
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
package bamfo.call;

/**
 * A summary of evidence for a given indel. Contains the ref/alt descriptions, and the number of reads 
 * supporting the variant.
 * 
 * @author tkonopka
 */
class LocusIndelQuasiCalled {

    final int indelstart;
    final byte[] ref;
    final byte[] alt;
    // the number of reads with indels
    final int countplus;
    final int countminus;
    // the number of distinct start positions
    final int distinctstart;
    // median mapping quality of reads with indel
    final double medianMappingQuality;
    

    /**
     * summarize information from an LocusIndelData 
     * 
     * @param lid
     * @param count 
     */
    LocusIndelQuasiCalled(LocusIndelData lid, int countplus, int countminus, 
            int distinctstart, double medianMappingQuality) {
        this.indelstart = lid.indelstart;        
        this.distinctstart = distinctstart;
        this.medianMappingQuality = medianMappingQuality;
        this.countplus = countplus;
        this.countminus = countminus;
        if (lid.insertion) {
            ref = new byte[1];
            ref[0] = lid.getAnchorbase();            
            alt = new byte[1+lid.indellen];
            alt[0] = ref[0];
            System.arraycopy(lid.getIndel(), 0, alt, 1, lid.indellen);            
        } else {                        
            alt = new byte[1];
            alt[0]=lid.getAnchorbase();
            ref = new byte[1+lid.indellen];
            ref[0]=alt[0];
            System.arraycopy(lid.getIndel(),0,ref, 1, lid.indellen);
        }        
    }
            
}
