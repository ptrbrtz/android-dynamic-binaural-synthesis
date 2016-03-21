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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;
import net.ptrbrtz.adbs.android.utils.MathUtils;

public class ExponentialSeekBar extends SeekBar {
	private float expBase = 6;	// more is more
	private float expMin = 0.0f;
	private float expMax = 1.0f;
	private int decimalPlaces = -1; // default no rounding

	public ExponentialSeekBar(Context context) {
		super(context);
	}

	public ExponentialSeekBar(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ExponentialSeekBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	public void setExpBase(float base) {
		expBase = base;
	}
	
	public float getExpBase() {
		return expBase;
	}
	
	public float getExpMin() {
		return expMin;
	}
	
	public void setExpMin(float expMin) {
		this.expMin = expMin;
	}
	
	public float getExpMax() {
		return expMax;
	}
	
	public void setExpMax(float expMax) {
		this.expMax = expMax;
	}
	
	public void setExpValue(float val) {
		setProgress(Math.round(MathUtils.mapExpToLin(val, expBase, 0, getMax(), getExpMin(), getExpMax())));
	}
	
	public float getExpValue() {
		float val = MathUtils.mapLinToExp((float) getProgress(), expBase, 0, getMax(), getExpMin(), getExpMax());
		if (decimalPlaces >= 0)
			val = MathUtils.roundTo(val, decimalPlaces);
		return val;
	}

	public int getDecimalPlaces() {
		return decimalPlaces;
	}

	public void setDecimalPlaces(int decimalPlaces) {
		this.decimalPlaces = decimalPlaces;
	}
}
