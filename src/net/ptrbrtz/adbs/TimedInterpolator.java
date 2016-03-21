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

import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;

public class TimedInterpolator {
	private static DecelerateInterpolator adInterpolator = new DecelerateInterpolator();
	private float startValue;
	private float endValue;
	private float diffValue;
	private float startTime;
	private float endTime;
	private float duration; // in ms
	private boolean active = false;
	
	public void setStartEndValues(float start, float end) {
		this.startValue = start;
		this.endValue = end;
		this.diffValue = this.endValue - this.startValue;
	}
	
	public void setDuration(float dur) {
		this.duration = dur;
	}
	
	public float getStartValue() {
		return startValue;
	}

	public void setStartValue(float startValue) {
		this.startValue = startValue;
	}

	public float getEndValue() {
		return endValue;
	}

	public void setEndValue(float endValue) {
		this.endValue = endValue;
	}

	public float getDuration() {
		return duration;
	}
	
	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void startInterpolating() {
		startTime = SystemClock.uptimeMillis();
		endTime = startTime + duration;
		active = true;
	}
	
	public float getCurrentValue() {
		float result;
		float currentTime;
		
		// get current time
		currentTime = (float) SystemClock.uptimeMillis();
		
		// check if we stay active after this method returns
		if (currentTime > endTime) {
			active = false;
		}
		
		// calculate result
		result = startValue + adInterpolator.getInterpolation((currentTime - startTime) / duration) * diffValue;
		
		// check bounds of result
		if (startValue < endValue) {
			if (result < startValue) result = startValue;
			else if (result > endValue) result = endValue;
		} else {
			if (result > startValue) result = startValue;
			else if (result < endValue) result = endValue;
		}
		
		return result;
	}
}
