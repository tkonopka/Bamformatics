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
 * Structure describe one indel with a read.
 * 
 * Class is used with the BamfoVcf tool.
 *
 * @author tkonopka
 */
class LocusIndelData {

    // sequence of anchor plus indel
    final byte[] sequence;
    // keep track of anchor base 
    final byte anchorbase;
    // position in genome where one-letter anchor starts
    final int indelstart;
    final int indellen;
    // ofset of actual indel sequence from anchor sequence
    final int offset;
    final boolean minusstrand;
    // fromstart/fromend are distances of the indel from the read extremities
    final int fromstart;
    final int fromend;
    final int mapquality;
    final boolean insertion;

    public LocusIndelData(byte anchorbase, byte[] anchor, byte[] indel, int indelstart, 
            boolean minusstrand, int indexonread, int readlength, int mapquality,
            boolean insertion) {
        
        this.anchorbase = anchorbase;
        
        int al = anchor.length;
        int il = indel.length;
        sequence = new byte[al + il];

        System.arraycopy(anchor, 0, sequence, 0, al);
        System.arraycopy(indel, 0, sequence, al, il);
        this.offset = al;

        this.indelstart = indelstart;
        this.minusstrand = minusstrand;
        this.mapquality = mapquality;
        this.indellen = il;

        this.insertion = insertion;

        // convert the index on read and read length into distances from the read edges
        if (minusstrand) {
            this.fromend = indexonread;
            this.fromstart = readlength - indexonread - 1;
        } else {
            this.fromstart = indexonread;
            this.fromend = readlength - indexonread - 1;
        }
    }
    
    /**
     * get the genome base just before the indel
     * 
     * @return 
     */
    public byte getAnchorbase() {        
        return anchorbase;
    }
    
    /**
     * get the sequence of the indel
     * 
     * @return 
     */
    public byte[] getIndel() {
        byte[] indel = new byte[indellen];
        System.arraycopy(sequence, offset, indel, 0, indellen);
        return indel;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("indel:\t").append(new String(sequence)).append("\t").append(offset).append("\n");
        sb.append("minustrand:\t").append(minusstrand).append("\n");
        sb.append("insertions:\t").append(insertion).append("\n");
        sb.append("from:\t").append(fromstart).append("\t").append(fromend).append("\n");
        return sb.toString();
    }
    
    /**     
     * 
     * @return 
     * 
     * the mapping quality number associated with the read. 
     * Is also accessible directly, so is this necessary or should the variable be private?
     * 
     */
    public int getMapquality() {
        return mapquality;
    }
    
    public int getFromstart() {
        return fromstart;
    }

    public int getIndelLen() {
        return indellen;
    }
    
}


