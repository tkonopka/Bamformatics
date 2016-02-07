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
package bamfo.utils;

import java.util.Date;

/**
 * conversion between two date objects and a string.
 *
 *
 * @author tomasz
 */
public class DateInterval {

    /**
     * conversion between two dates into a string representing duration.
     *
     * @param d1
     * @param d2
     * @return
     */
    public static String timeInterval(Date d1, Date d2) {
        long t1 = d1.getTime();
        long t2 = d2.getTime();

        // convert the times into second rather than millis
        int temp = (int) ((t2 - t1) / 1000);

        // get the days, hours, etc.
        int[] ans = new int[4];
        ans[0] = (temp / (3600 * 24));
        temp = temp - (ans[0] * 3600 * 24);
        ans[1] = (temp / (3600));
        temp = temp - (ans[1] * 3600);
        ans[2] = (temp / (60));
        temp = temp - ans[2] * (60);
        ans[3] = temp;

        // create a compact representation of the result
        int maxval = 3;
        for (int i = 0; i < 4; i++) {
            if (ans[i] > 0) {
                maxval = i;
                break;
            }
        }

        StringBuilder timestring = new StringBuilder(32);
        for (int i = maxval; i < 4; i++) {
            timestring.append(ans[i]);
            switch (i) {
                case 0:
                    timestring.append("d ");
                    break;
                case 1:
                    timestring.append("h ");
                    break;
                case 2:
                    timestring.append("m ");
                    break;
                case 3:
                    timestring.append("s ");
                    break;
                default:
                    break;
            }
        }

        return timestring.toString();
    }
}
