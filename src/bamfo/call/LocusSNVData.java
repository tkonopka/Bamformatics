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
 * Structure to hold a summary on a single base and its position within its read.
 * 
 * It is used within the Bamfo variant calling program.
 * 
 * 
 * @author tkonopka
 */
public class LocusSNVData {

    private byte base;
    final byte quality;
    final boolean minusstrand;
    private int fromstart;
    private int fromend;
    private boolean readhasindel;    
    private int mapquality;    
    private int NMtag;
    private int maxN;   

    /**
     * create an object with genotype evidence for a locus coming from one read
     *
     * @param base
     * @param quality
     * @param minusstrand
     * @param indexonread
     * @param readlength
     */
    public LocusSNVData(byte base, byte quality, boolean minusstrand,
            int indexonread, int readlength, int mapquality, boolean readhasindel, 
            int maxN, int NMtag) {
        this.base = base;
        this.quality = quality;
        this.minusstrand = minusstrand;
        this.readhasindel = readhasindel;
        this.mapquality = mapquality;
        this.maxN = maxN;
        this.NMtag = NMtag;

        // convert the index on read and read length into distances from the read edges
        if (minusstrand) {
            this.fromend = indexonread;
            this.fromstart = readlength - indexonread - 1;
        } else {
            this.fromstart = indexonread;
            this.fromend = readlength - indexonread - 1;
        }
    }

    public byte getBase() {
        return base;
    }

    public void setBase(byte base) {
        this.base = base;
    }

    public int getFromend() {
        return fromend;
    }

    public void setFromend(int fromend) {
        this.fromend = fromend;
    }

    public int getFromstart() {
        return fromstart;
    }

    public void setFromstart(int fromstart) {
        this.fromstart = fromstart;
    }
    
    public int getMapquality() {
        return mapquality;
    }

    public void setMapquality(int mapquality) {
        this.mapquality = mapquality;
    }
    
    public boolean isReadhasindel() {
        return readhasindel;
    }

    public void setReadhasindel(boolean readhasindel) {
        this.readhasindel = readhasindel;
    }
    
    public int getMaxN() {
        return maxN;
    }

    public void setMaxN(int maxN) {
        this.maxN = maxN;
    }

    public int getNMtag() {
        return this.NMtag;
    }
    
    public void setNMtag(int NMtag) {
        this.NMtag = NMtag;
    }
    
}
