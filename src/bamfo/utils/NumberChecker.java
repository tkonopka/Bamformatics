package bamfo.utils;

import java.util.regex.Pattern;

/**
 *
 * This is based on code from the Double javadoc, with some extension.
 *
 *
 *
 */
public class NumberChecker {

    private final static String Digits = "(\\p{Digit}+)";
    private final static String HexDigits = "(\\p{XDigit}+)";
    // an exponent is 'e' or 'E' followed by an optionally
    // signed decimal integer.
    private final static String Exp = "[eE][+-]?" + Digits;
    // fpRegex is a regex for a floating point number
    private final static String fpRegex =
            ("[\\x00-\\x20]*" + // Optional leading "whitespace"
            "[+-]?(" + // Optional sign character
            "NaN|" + // "NaN" string
            "Infinity|"
            + // "Infinity" string
            // A decimal floating-point string representing a finite positive
            // number without a leading sign has at most five basic pieces:
            // Digits . Digits ExponentPart FloatTypeSuffix
            //
            // Since this method allows integer-only strings as input
            // in addition to strings of floating-point literals, the
            // two sub-patterns below are simplifications of the grammar
            // productions from section 3.10.2 of
            // The Java™ Language Specification.
            // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
            "(((" + Digits + "(\\.)?(" + Digits + "?)(" + Exp + ")?)|"
            + // . Digits ExponentPart_opt FloatTypeSuffix_opt
            "(\\.(" + Digits + ")(" + Exp + ")?)|"
            + // Hexadecimal strings
            "(("
            + // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
            "(0[xX]" + HexDigits + "(\\.)?)|"
            + // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
            "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")"
            + ")[pP][+-]?" + Digits + "))"
            + "[fFdD]?))"
            + "[\\x00-\\x20]*");// Optional trailing "whitespace"
    // intRgex is a modification of fpRegex. It should match
    // integers, numbers without a decimal point
    private final static String intRegex =
            ("[\\x00-\\x20]*" + // Optional leading "whitespace"
            "[+-]?(" + // Optional sign character
            "NaN|" + // "NaN" string
            "Infinity|"
            + // "Infinity" string
            // A decimal floating-point string representing a finite positive
            // number without a leading sign has at most five basic pieces:
            // Digits . Digits ExponentPart FloatTypeSuffix
            //
            // Since this method allows integer-only strings as input
            // in addition to strings of floating-point literals, the
            // two sub-patterns below are simplifications of the grammar
            // productions from section 3.10.2 of
            // The Java™ Language Specification.
            // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
            "(((" + Digits + "(" + Exp + ")?)|"
            + // Hexadecimal strings
            "((" + // 0[xX] HexDigits 
            "(0[xX]" + HexDigits + ")"
            + ")[pP][+-]?" + Digits + "))"
            + "[fFdD]?))"
            + "[\\x00-\\x20]*");// Optional trailing "whitespace"

    /**
     * This function is almost straight from the javadoc.
     *
     * @param s
     * @return
     */
    public static boolean isDouble(String s) {
        if (s == null) {
            return false;
        }        
        return Pattern.matches(fpRegex, s);
    }

    public static boolean isInteger(String s) {
        if (s == null) {
            return false;
        }
        if (!Pattern.matches(intRegex, s)) {
            return false;
        } else {
            // I noticed the integer pattern does not pick up some combinations
            // that are output by my code in R, so check for these specially here
            if (s.startsWith("0E")|| s.startsWith("0e") || 
                    s.startsWith("-0E") || s.startsWith("-0e")){
                return false;
            } else {                
                return true;
            }
        }        
    }
}
