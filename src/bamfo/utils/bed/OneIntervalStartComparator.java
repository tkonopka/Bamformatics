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
 * Compares two intervals based only on their start position
 */
class OneIntervalStartComparator implements Comparator {

    @Override
    public int compare(Object o1, Object o2) {
        OneInterval oi1 = (OneInterval) o1;
        OneInterval oi2 = (OneInterval) o2;
        if (oi1.getStart() < oi2.getStart()) {
            return -1;
        } else if (oi1.getStart() > oi2.getStart()) {
            return 1;
        } else {
            return 0;
        }
    }
}