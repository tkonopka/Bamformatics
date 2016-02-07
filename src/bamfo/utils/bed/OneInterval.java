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
package bamfo.utils.bed;

import java.util.Comparator;

/**
 *
 * @author tkonopka
 */
class OneInterval {

    private final int start, end;

    public int getEnd() {
        return end;
    }

    public int getStart() {
        return start;
    }

    public OneInterval(int start, int end) {
        this.start = Math.min(start, end);
        this.end = Math.max(start, end);
    }

    @Override
    public String toString() {
        return (start + "\t" + end);
    }
       
    /**
     * check if there is an overlaps between two intervals.
     * This function assumes that the intervals are well formed, i.e. created via the constructor
     * and that the start value is smaller or equal to the end value.
     * 
     * @param interval
     * @return 
     */
    public boolean overlaps(OneInterval interval) {

        // check for simple overlaps, e.g. 
        //     aaaaaaaaaaaaaa
        //             bbbbbbbbbbbbbbb
        if (start >= interval.start && start <= interval.end) {
            return true;
        }
        if (end >= interval.start && end <= interval.end) {
            return true;
        }

        // check for non overlap
        //  aaaaaaaaaaaa
        //                 bbbbbbbbbbbbbb
        if (start >= interval.end) {
            return false;
        }

        if (end <= interval.start) {
            return false;
        }

        return true;
                
    }

    public static OneInterval merge(OneInterval a, OneInterval b) {
        return new OneInterval(Math.min(a.start, b.start), Math.max(a.end, b.end));
    }
}
