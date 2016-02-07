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
package bamfo.utils;

/**
 * A object/class for computing fisher test probabilities. Works only on 2x2 tables.
 * 
 * The Fisher test involves calculating factors like x!. For x between 0 and 12, this
 * class computes these values exactly. For larger values, the Stirling approximation to second is used.
 * 
 * 
 * @author tkonopka
 */
public class BamfoFisherTest {

    private final double[] stirling;
    private final static int precompsize = 256;

    public BamfoFisherTest() {
        // fill in the stirling approximations for the first few numbers
        stirling = new double[precompsize];
        stirling[0] = 0.0;

        // use exact formula up to 10
        int pow = 1;
        for (int i = 1; i < 13; i++) {
            pow = pow * i;
            stirling[i] = Math.log(pow);
        }
        // use approximate formula beyond that
        for (int i = 13; i < precompsize; i++) {
            stirling[i] = StirlingApprox(i);
        }

    }

    private void print(int a, int b, int c, int d) {
        System.out.println("looking:\t" + a + "\t" + b + "\t" + c + "\t" + d);
    }

    /**
     *
     * performs a fisher test.
     *
     * @param a
     * @param b
     * @param c
     * @param d
     * @return
     *
     * returns the log of the p-value.
     *
     */
    public double test(int a, int b, int c, int d) {
       
        double initp = pval(a, b, c, d);

        // get probabilities associated with shifted tables

        // probabilities for tables with smaller "a"
        double lessap = 0.0;
        int admin = Math.min(a, d);
        for (int i = 1; i <= admin; i++) {
            double tempp = pval(a - i, b + i, c + i, d - i);
            if (tempp <= initp) {
                lessap += tempp;
            }
        }

        // probabilities for tables with larger "a"
        double moreap = 0.0;
        int bcmin = Math.min(b, c);
        for (int i = 1; i <= bcmin; i++) {            
            double tempp = pval(a + i, b - i, c - i, d + i);
            if (tempp <= initp) {
                moreap += tempp;
            }
        }

        return Math.min(1.0, (initp + lessap + moreap));
    }

    /**
     *
     * @param a
     * @param b
     * @param c
     * @param d
     * @return
     *
     * the probability associated with an instanciation of a table
     *
     */
    private double pval(int a, int b, int c, int d) {
        // get the total number of occurances in the table
        int nn = a + b + c + d;

        // compute the pvalue
        if (nn < precompsize) {
            // the individual numbers must all be less than precompsize so do the test using
            // the array
            return Math.exp(stirling[a + b] + stirling[c + d] + stirling[a + c] + stirling[b + d]
                    - stirling[a] - stirling[b] - stirling[c] - stirling[d] - stirling[nn]);

        } else {
            // some numbers are not in array. Use a general method.
            return Math.exp(getStirling(a + b) + getStirling(c + d) + getStirling(a + c) + getStirling(b + d)
                    - getStirling(a) - getStirling(b) - getStirling(c) - getStirling(d) - getStirling(nn));

        }
    }

    /**
     * get Stirling approximation to ln x!. Uses precomputed table when
     * possible, but uses StirlingApprox otherwise
     *
     * @param x
     * @return
     */
    private double getStirling(int x) {
        if (x < precompsize) {
            return stirling[x];
        } else {
            return StirlingApprox((double) x);
        }
    }

    /**
     * gives the Stirling approximation to ln x! up to ln(x) order.
     *
     * @param x
     * @return
     */
    private double StirlingApprox(double x) {
        return (x * Math.log(x)) - x + (0.5 * Math.log(2 * Math.PI * x));
    }
}
