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
 * A generic object that holds an object and a counter. 
 * 
 * @author tkonopka
 */
public class IntObjectPair {
    final int count;
    final Object obj;
    
    public IntObjectPair(int count, Object obj) {
        this.count = count;
        this.obj = obj;
    }
}


/**
 * a comparator of two IntObjectPair objects. The comparison is done only on the counter field. 
 * 
 * @author tkonopka
 */
class IntObjectPairComparator implements Comparator {

    @Override
    public int compare(Object o1, Object o2) {        
        int c1 = ((IntObjectPair) o1).count;
        int c2 = ((IntObjectPair) o2).count;
        if (c1 < c2) {
            return -1;
        } else if (c1 > c2) {
            return 1;
        } else {
            return 0;
        }
    }
}
