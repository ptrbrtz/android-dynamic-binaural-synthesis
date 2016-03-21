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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;

public class Listener extends Entity {
	private static final String TAG = "Reference";
	
	// static fields for drawing
	protected static Paint paint = null;
	protected static Picture arrowPicture = null;
	
	public static Paint getPaint() {
		return paint;
	}

	public static void setPaint(Paint paint) {
		Listener.paint = paint;
	}

	@Override
	public void draw(Canvas canvas, float inverseScaling, float rotation) {
		// save current transformation matrix
		canvas.save();
		
		// rotate and scale
		canvas.rotate(azimuth);
		canvas.scale(inverseScaling, inverseScaling);
		
		// draw reference
		arrowPicture.draw(canvas);
		
		// restore matrix
		canvas.restore();
	}
}
