/*
 * Copyright 2012 Tomasz Konopka, Maxime Tarabichi.
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import jsequtils.sequence.FastaReader;
import net.sf.samtools.Cigar;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMFileReader;

/**
 * Some static functions used here and there within this project. It is not a
 * very elegant solution - needs some fixing later.
 *
 *
 * @author tkonopka
 */
public class BamfoCommon {

    // numeric codes for bases
    public final static int codeA = 0;
    public final static int codeT = 1;
    public final static int codeC = 2;
    public final static int codeG = 3;
    public final static int codeN = 4;
    public final static int codeClip = 5;
    public final static int codeIns = 6;
    public final static int codeDel = 7;
    public final static int numcodes = 8;
    //public final static int posIns = -2;
    //public final static int posClip = -1;
    public final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    //static String specialread = "HWI-BRUNOP16X_0001:4:43:8344:161211#0";
    /**
     * The converse of basesTozeroToFour
     *
     * @param code
     * @return
     */
    public static char ZeroToFourToBase(int code) {
        switch (code) {
            case 0:
                return 'A';
            case 1:
                return 'T';
            case 2:
                return 'C';
            case 3:
                return 'G';
            default:
                return 'N';
        }
    }

    /**
     * The converse of basesTozeroToFour
     *
     * @param code
     * @return
     */
    public static byte ZeroToFourToByte(int code) {
        switch (code) {
            case 0:
                return 'A';
            case 1:
                return 'T';
            case 2:
                return 'C';
            case 3:
                return 'G';
            default:
                return 'N';
        }
    }

    /**
     * converts one base to an integer
     *
     * @param base
     * @return
     */
    public static int basesToZeroToFour(byte base) {
        switch (base) {
            case 'A':
                return codeA;
            case 'T':
                return codeT;
            case 'C':
                return codeC;
            case 'G':
                return codeG;
            default:
                return codeN;
        }
    }

    public static int basesToZeroToFourComplement(byte base) {
        switch (base) {
            case 'A':
                return codeT;
            case 'T':
                return codeA;
            case 'C':
                return codeG;
            case 'G':
                return codeC;
            default:
                return codeN;
        }
    }

    public static int basesToZeroToFour(char base) {
        switch (base) {
            case 'A':
                return codeA;
            case 'T':
                return codeT;
            case 'C':
                return codeC;
            case 'G':
                return codeG;
            default:
                return codeN;
        }
    }

    /**
     * converts an array of bytes to integer codes from 0 to 4 for ATCGN (N or
     * other)
     *
     * @param bases
     * @return
     */
    public static int[] basesToZeroToFour(byte[] bases) {
        int baseslength = bases.length;
        int[] ans = new int[baseslength];
        for (int i = 0; i < baseslength; i++) {
            ans[i] = basesToZeroToFour(bases[i]);
        }
        return ans;
    }
    
    public static byte[] getSequenceBase1(byte[] sequence, int start, int end) {
        byte[] newseq = new byte[end - start + 1];
        System.arraycopy(sequence, start - 1, newseq, 0, end - start + 1);
        return newseq;
    }

    /**
     * Find an appropriate anchor for the the indel, accounting for small
     * repeats.
     *
     * @param genomereader
     *
     * a reader that makes the genome sequence available
     *
     * @param indelstart
     *
     * the positions where the indel starts e.g. if indel is defined as AGT -> A
     * in vcf, indelstart is position of the A e.g. if indel is defined as A - >
     * AGGTC, indelstart is position of the A
     *
     * @param indel
     *
     * the sequence of the insertion or deletion. e.g. if indel is defined as
     * AGT -> A, the indel sequence is GT e.g. if indel is defined as A ->
     * AGGTC, the indel sequence in GGTC
     *
     *
     * @return
     *
     * an integer, the 1-based position of a suitable anchor for the indel
     *
     */
    public static int getAnchorPosition(FastaReader genomereader, int indelstart, byte[] indel) {

        // by default, the anchor will be the indelstart;
        int anchor = indelstart;

        // start by looking at the last base in the indel
        int indellen = indel.length;
        int inindel = indellen;

        boolean found = false;
        while (!found && anchor > 1) {
            inindel--;
            if (inindel < 0) {
                inindel = indellen - 1;
            }
            byte indelbase = indel[inindel];
            byte genomebase = genomereader.getBaseAtPositionBase1(anchor);
            if (genomebase != indelbase) {
                found = true;
            } else {
                anchor--;
            }
        }

        return anchor;
    }

    /**
     * Find an appropriate anchor for the the indel, accounting for small
     * repeats.
     *
     * @param genomereader
     *
     * a reader that makes the genome sequence available
     *
     * @param indelstart
     *
     * the positions where the indel starts e.g. if indel is defined as AGT -> A
     * in vcf, indelstart is position of the A e.g. if indel is defined as A - >
     * AGGTC, indelstart is position of the A
     *
     * @param indel
     *
     * the sequence of the insertion or deletion. e.g. if indel is defined as
     * AGT -> A, the indel sequence is GT e.g. if indel is defined as A ->
     * AGGTC, the indel sequence in GGTC
     *
     *
     * @return
     *
     * an integer, the 1-based position of a suitable anchor for the indel
     *
     */
    public static int getAnchorPosition(byte[] sequence, int indelstart, byte[] indel) {

        // by default, the anchor will be the indelstart;
        int anchor = indelstart;

        // start by looking at the last base in the indel
        int indellen = indel.length;
        int inindel = indellen;

        boolean found = false;
        while (!found && anchor > 1) {
            inindel--;
            if (inindel < 0) {
                inindel = indellen - 1;
            }
            byte indelbase = indel[inindel];
            byte genomebase = sequence[anchor - 1];
            if (genomebase != indelbase) {
                found = true;
            } else {
                anchor--;
            }
        }

        return anchor;
    }

    /**
     *
     * @param recordcigar
     * @return
     *
     * true if the record contains an insertion or a deletion
     */
    public static boolean containsIndelDeprecated(Cigar recordcigar) {
        ArrayList<CigarElement> recordce = new ArrayList(recordcigar.getCigarElements());
        for (int i = 0; i < recordce.size(); i++) {
            CigarElement ce = recordce.get(i);
            if (ce.getOperator() == CigarOperator.D || ce.getOperator() == CigarOperator.I) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param recordcigar
     * @return
     *
     * true if the record contains an insertion or a deletion
     */
    public static boolean containsIndelDeprecated(ArrayList<CigarElement> cigarelements) {
        for (int i = 0; i < cigarelements.size(); i++) {
            CigarElement ce = cigarelements.get(i);
            if (ce.getOperator() == CigarOperator.D || ce.getOperator() == CigarOperator.I) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param recordcigar
     * @return
     *
     * true if the record contains an insertion
     */
    public static boolean containsInsertion(Cigar recordcigar) {
        ArrayList<CigarElement> recordce = new ArrayList(recordcigar.getCigarElements());
        for (int i = 0; i < recordce.size(); i++) {
            CigarElement ce = recordce.get(i);
            if (ce.getOperator() == CigarOperator.I) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param recordcigar
     * @return
     *
     * true if the record contains a deletion
     */
    public static boolean containsDeletion(Cigar recordcigar) {
        ArrayList<CigarElement> recordce = new ArrayList(recordcigar.getCigarElements());
        for (int i = 0; i < recordce.size(); i++) {
            CigarElement ce = recordce.get(i);
            if (ce.getOperator() == CigarOperator.D) {
                return true;
            }
        }
        return false;
    }

    /**
     * returns the number of bases that have position declared as posClip (-1)
     *
     * @param pos
     * @return
     */
    public static int countClippedBases(Cigar recordcigar) {
        int count = 0;
        ArrayList<CigarElement> recordce = new ArrayList(recordcigar.getCigarElements());
        for (int i = 0; i < recordce.size(); i++) {
            CigarElement ce = recordce.get(i);
            if (ce.getOperator() == CigarOperator.S) {
                count += ce.getLength();
            }
        }
        return count;
    }

    /**
     *
     * get the median value of a list of numbers.
     *
     * Warning! This function will sort the numbers so the array passed in the argument will change.
     *
     * @param numbers
     *
     * numbers whose median should be computed
     *
     * @return
     *
     * the median value of the list of numbers, or NaN if empty
     */
    public static double getMedian(int[] numbers) {
        // deal with easy cases
        if (numbers == null || numbers.length == 0) {
            return Double.NaN;
        }

        // for the median, numbers should be sorted. Sort them in place here
        Arrays.sort(numbers);

        int size = numbers.length;
        int mid = size / 2;
        if (size % 2 == 0) {
            // is even, median must be averaged over two items
            double a = (double) numbers[mid];
            double b = (double) numbers[mid - 1];
            return (a + b) / 2;
        } else {
            // qsize is odd, median is easy to get at qmid
            return (double) numbers[size / 2];
        }
    }
    
    public static void updateValidationStringency(SAMFileReader sreader, String validate) {
        if (validate.equalsIgnoreCase("LENIENT")) {
            sreader.setValidationStringency(SAMFileReader.ValidationStringency.LENIENT);            
        } else if (validate.equalsIgnoreCase("SILENT")) {
            sreader.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);            
        } else if (validate.equalsIgnoreCase("STRICT")) {
            sreader.setValidationStringency(SAMFileReader.ValidationStringency.STRICT);
        }
    }    
}
