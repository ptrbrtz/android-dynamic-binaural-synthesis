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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.SeekBar;
import net.ptrbrtz.adbs.R;

public class CustomSeekBar extends SeekBar {
	private static Paint paint;
	
	private int visualProgress; 

	public CustomSeekBar(Context context) {
		super(context);
		
		if (paint == null) {
			paint = new Paint();
			paint.setColor(Color.WHITE);
		}
		
		visualProgress = getProgress();
	}

	public CustomSeekBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		if (paint == null) {
			paint = new Paint();
			paint.setColor(Color.WHITE);
		}
		
		visualProgress = getProgress();
	}

	public CustomSeekBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		if (paint == null) {
			paint = new Paint();
			paint.setColor(Color.WHITE);
		}
		
		visualProgress = getProgress();
	}

	public void setVisualProgress(int progress) {
		visualProgress = progress;
	}
	
	public int getVisualProgress() {
		return visualProgress;
	}
	
	@Override
	protected synchronized void onDraw(Canvas canvas) {
		int width, height;
		width = this.getWidth();
		height = this.getHeight();

		canvas.drawColor(getResources().getColor(R.drawable.col_btn_default));
		
		canvas.drawLine(0, 0, width, 0, paint);
		canvas.drawRect(0, 0, Math.round((float) this.visualProgress / (float) this.getMax() * (float) width), height, paint);
	}
}
