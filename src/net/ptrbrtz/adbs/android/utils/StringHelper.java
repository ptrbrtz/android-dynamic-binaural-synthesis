/*
Copyright (c) 2016 Peter Bartz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package net.ptrbrtz.adbs.android.utils;

/**
 * @author peter
 * 
 * Helper class to convert int/float numbers to string and append to a StringBuilder without allocating new objects.
 * Standard java conversion and appending methods allocate lots of objects, which causes Garbage Collector pauses. 
 */
public class StringHelper {
	private static final char[] DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private static final int DEFAULT_NUM_DEC_PLACES = 5;
	private static final char DEFAULT_PADDING_CHAR = '0';

	// Convert integer value to string and concatenate to given StringBuilder.
	public static void append(StringBuilder sb, int val, int padding, char paddingChar, int base) {
		// Minus sign
		if (val < 0) {
			sb.append('-');
			val = -val;
		}
		
		// Calculate length of string
		int length = 0;
		int lengthVal = val;
		do {
			lengthVal /= base;
			length++;
		} while (lengthVal > 0);

		// Output padding and make room
		int maxLength = Math.max(padding, length);
		for (int i = 0; i < maxLength; i++) {
			sb.append(paddingChar);
		}

		// We're writing backwards, one character at a time
		for (int i = sb.length()-1; length > 0; i--, length--) {
			sb.setCharAt(i, DIGITS[val % base]);
			val /= base;
		}
	}

	// Convert integer value to string and concatenate to given StringBuilder. Base 10 and given padding.
	public static void append(StringBuilder sb, int val, int padding, char paddingChar) {
		append(sb, val, padding, paddingChar, 10);
	}

	// Convert integer value to string and concatenate to given StringBuilder. Base 10 and given padding using zeros.
	public static void append(StringBuilder sb, int val, int padding) {
		append(sb, val, padding, DEFAULT_PADDING_CHAR, 10);
	}
	
	// Convert integer value to string and concatenate to given StringBuilder. Base 10 and no padding.
	public static void append(StringBuilder sb, int val) {
		append(sb, val, 0, DEFAULT_PADDING_CHAR, 10);
	}
	
	// Convert float value to string and concatenate to given StringBuilder.
	public static void append(StringBuilder sb, float val, int decimalPlaces, int padding, char paddingChar) {
		if (decimalPlaces == 0) {
			// Round and treat as int
			append(sb, Math.round(val), padding, paddingChar, 10);
		} else {
			int intPart = (int) val;

			// Cast to int and append
			append(sb, intPart, padding, paddingChar, 10);

			// Decimal point
			sb.append('.');

			// Calculate remainder
			float remainder = Math.abs(val - intPart);

			// Multiply, so we get an int
			remainder *= Math.pow(10.0, decimalPlaces);

			// Round last digit
			remainder += 0.5f;

			// Append as int
			append(sb, (int) remainder, decimalPlaces, '0', 10);
		}
	}
	
	// Convert float value to string and concatenate to given StringBuilder. 5 decimal places, no padding.
	public static void append(StringBuilder sb, float val) {
		append(sb, val, DEFAULT_NUM_DEC_PLACES, 0, DEFAULT_PADDING_CHAR);
	}

	// Convert float value to string and concatenate to given StringBuilder. No padding.
	public static void append(StringBuilder sb, float val, int decimalPlaces) {
		append(sb, val, decimalPlaces, 0, DEFAULT_PADDING_CHAR);
	}

	// Convert float value to string and concatenate to given StringBuilder. Padding with zeros
	public static void append(StringBuilder sb, float val, int decimalPlaces, int padding) {
		append(sb, val, decimalPlaces, padding, DEFAULT_PADDING_CHAR);
	}
}
