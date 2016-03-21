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

package net.ptrbrtz.adbs;

import android.util.Log;

public class FirFilter {
	private static final String TAG = "FirFilter";

	// load shared lib
	static {
		try {
			System.loadLibrary("firfilter");
			Log.d(TAG, "library loaded...");
		} catch (Exception e) {
			Log.e(TAG, "could not load library...");
		}
	}
	
	// native methods
	public static native int kissfftCreate(float[][] hrirsL, float[][] hrirsR, int fftSize, int crossfadeSize, int paddingSize);
	public static native int kissfftDestroy();
	public static native int beginRenderingBlock();
	public static native int endRenderingBlock(boolean crossfade, byte[] audioOutput);
	public static native int kissfftFilter(byte[] audioInput, int hrtfIndex, float sampleScaling);
	public static native long jniNanoTime();
}
