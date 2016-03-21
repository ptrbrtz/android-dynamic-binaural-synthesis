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

public class MathUtils {
	public static boolean isPowerOfTwo(int x) {
		return x != 0 && (x & (x - 1)) == 0;
	}
	
	// not the fastest way to do it, but good enough
	public static int getNextPowerOfTwo(int i) {
		int result = 1;
		while (result < i) result <<= 1;
		return result;
	}
	
	public static int getNextPowerOfTwo(int i, int base) {
		int result = base;
		while (result < i) result <<= 1;
		return result;
	}

	public static float roundTo(float val, int decimalPlaces) {
		float m = (float) Math.pow(10.0f, (float) decimalPlaces);
		return (float) Math.round(val * m) / m;
	}
	
	public static float mapLinToExp(float linVal, float expBase, float linMin, float linMax, float expMin, float expMax) {
		linVal = (linVal - linMin) / (linMax - linMin); // normalize

		float expVal = (float) (Math.pow(expBase, linVal) - 1.0f) / (expBase - 1.0f);
		return expVal * (expMax - expMin) + expMin; // de-normalize
	}

	public static float mapExpToLin(float expVal, float expBase, float linMin, float linMax, float expMin, float expMax) {
		expVal = (expVal - expMin) / (expMax - expMin); // normalize

		float linVal = (float) (Math.log(expVal * (expBase - 1.0f) + 1.0f) / Math.log(expBase));
		return linVal * (linMax - linMin) + linMin; // de-normalize
	}
}
