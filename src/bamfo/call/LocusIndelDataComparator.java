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

import java.util.Comparator;

/**
 * Compares two indels as recorded in LocusIndelData objects.
 *
 * This assumes: the anchors of the two indels should be the same.
 *
 *
 *
 * @author tkonopka
 */
final class LocusIndelDataComparator implements Comparator {

    /**
     * Criteria for equality: the indel length should be the same and the
     * anchor+indel sequence should be approximately equal e.g. on reference
     * sequence ACCG, indel AC->AC[CT] and ACC->ACC[TC] are reported equivalent)
     *
     * Among "equal" indels sequences
     *
     *
     * @param s1
     * @param s2
     * @return
     */
    public int indelSequenceCompare(byte[] s1, byte[] s2) {

        int s1len = s1.length;
        int s2len = s2.length;
        int sminlen = Math.min(s1len, s2len);

        for (int i = 0; i < sminlen; i++) {
            if (s1[i] < s2[i]) {
                return -1;
            } else if (s1[i] > s2[i]) {
                return 1;
            }
        }

        // reached here, the sequences are indel-equal
        return 0;
    }

    /**
     * Similar indels are those that have equal indel sequences
     *
     * @param o1
     * @param o2
     * @return
     *
     * true if: two indels are both insertion or both deletions NAD
     *          two indels are of same length AND
     *          two sequences sequences are equal or similar
     */
    public boolean similar(LocusIndelData lid1, LocusIndelData lid2) {

        if (lid1.insertion == lid2.insertion && lid1.indellen == lid2.indellen && indelSequenceCompare(lid1.sequence, lid2.sequence) == 0) {
            return true;
        }
        return false;

    }

    @Override
    public int compare(Object o1, Object o2) {

        LocusIndelData lid1 = (LocusIndelData) o1;
        LocusIndelData lid2 = (LocusIndelData) o2;

        // by convention, deletions will be before insertions
        if (!lid1.insertion && lid2.insertion) {
            return -1;
        } else if (lid1.insertion && !lid2.insertion) {
            return 1;
        }
        
        // among equal sequences, "smaller" indels are those  with long indel sequences                
        if (lid1.indellen > lid2.indellen) {
            return -1;
        } else if (lid1.indellen < lid2.indellen) {
            return 1;
        }

        // compare the sequences
        int sequencecompare = indelSequenceCompare(lid1.sequence, lid2.sequence);
        if (sequencecompare != 0) {
            return sequencecompare;
        }
        
        // prefer indels that start closer to the anchor
        if (lid1.indelstart < lid2.indelstart) {
            return -1;
        } else if (lid1.indelstart > lid2.indelstart) {
            return 1;
        }

        // if reached here, they are really by all criteria.
        return 0;
    }
}
