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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Picture;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import net.ptrbrtz.adbs.android.utils.StringHelper;

public class SourcesView extends View implements OnGestureListener, OnDoubleTapListener, OnScaleGestureListener {
	private static final String TAG = "SourcesView";

	// source won't be select if touch point outside this radius (in pixels)
	public static final float SOURCE_SELECT_RADIUS = 50f;
	// border when calculation "fit scene into screen"
	private static final float FIT_SCENE_PIXEL_BORDER = 50f;
	private static final float FIT_SCENE_PIXEL_BORDER_2 = 2.0f * FIT_SCENE_PIXEL_BORDER;

	// string builders to avoid object creation
	private StringBuilder scaleHalfString = new StringBuilder(100);
	private StringBuilder scaleEndString = new StringBuilder(100);
	
	public enum TransformationMode {
		TRANSLATE, ROTATE
	}

	private static Paint paint = null;
	private Picture sizeScalePicture = new Picture();
	private Matrix viewportTransformation;
	private Matrix inverseViewportTransformation;
	private float[] selectionOffset;
	private float[] touchPoint;
	private float[] firstScrollPoint = { 0.0f, 0.0f };
	private SoundSource lastTouchSoundSource;
	private float currentScaling = 0.5f;
	private float[] currentTranslation = { 0.0f, 50.0f };
	private boolean scrolling = false;
	private float currentRotation = 0.0f;
	private TransformationMode transformationMode = TransformationMode.TRANSLATE;
	protected TimedInterpolator scalingInterpolator;
	private GestureDetector gestureDetector;
	private ScaleGestureDetector scaleGestureDetector;
	private float initialViewportScaling;

	// flags
	private boolean viewportFlag = true;
	private boolean scalingFlag = true;

	public SourcesView(Context context) {
		super(context);
		init();
	}

	public SourcesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public SourcesView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		// make this view focusable
		setFocusable(true);

		// init fields
		viewportTransformation = new Matrix();
		inverseViewportTransformation = new Matrix();
		
		selectionOffset = new float[2];
		touchPoint = new float[2];
		
		scalingInterpolator = new TimedInterpolator();
		scalingInterpolator.setDuration(800);
		
		if (paint == null) {
			paint = new Paint();
			paint.setAntiAlias(false);
			paint.setStrokeWidth(1.0f * GlobalData.pixelScaling);
			paint.setTextAlign(Paint.Align.CENTER);
			paint.setTextSize(9.0f * GlobalData.pixelScaling);
		}
		
		// set up gesture detectors
		gestureDetector = new GestureDetector(getContext(), this);
		gestureDetector.setIsLongpressEnabled(false);
		gestureDetector.setOnDoubleTapListener(this);
		scaleGestureDetector = new ScaleGestureDetector(getContext(), this);

		// init viewport transformation matrix
		recalculateViewportTransformation();
	}

	public float getCurrentScaling() {
		return currentScaling;
	}

	public void setCurrentScaling(float currentScaling) {
		this.currentScaling = currentScaling;
		setViewportFlag(true);
		setScalingFlag(true);
	}

	public float getCurrentRotation() {
		return currentRotation;
	}

	public void setCurrentRotation(float currentRotation) {
		if (this.currentRotation != currentRotation) {
			this.currentRotation = currentRotation;
			setViewportFlag(true);
		}
	}

	public float[] getCurrentTranslation() {
		return currentTranslation;
	}

	public void setCurrentTranslation(float[] currentTranslation) {
		setCurrentTranslation(currentTranslation[0], currentTranslation[1]);
	}

	public void setCurrentTranslation(float currentTranslationX, float currentTranslationY) {
		this.currentTranslation[0] = currentTranslationX;
		this.currentTranslation[1] = currentTranslationY;
		setViewportFlag(true);
	}

	public boolean getAndClearViewportFlag() {
		boolean returnValue = viewportFlag;
		viewportFlag = false;
		return returnValue;
	}

	public void setViewportFlag(boolean viewportFlag) {
		this.viewportFlag = viewportFlag;
	}

	public boolean getAndClearScalingFlag() {
		boolean returnValue = scalingFlag;
		scalingFlag = false;
		return returnValue;
	}
	
	public void setScalingFlag(boolean scalingFlag) {
		this.scalingFlag = scalingFlag;
	}
	
	public void setTransformationMode(TransformationMode mode) {
		this.transformationMode = mode;
	}

	public void zoomView(float factor) {
		float newScaling;

		if (scalingInterpolator.isActive()) {
			newScaling = scalingInterpolator.getEndValue() * factor;
		} else {
			newScaling = currentScaling * factor;
		}

		scalingInterpolator.setStartEndValues(currentScaling, newScaling);
		scalingInterpolator.startInterpolating();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		setViewportFlag(true);
		transformToFitScene(false);
	}

	private void recalculateViewportTransformation() {
		recalculateViewportTransformation(viewportTransformation,
				currentRotation, currentScaling, currentTranslation);
		viewportTransformation.invert(inverseViewportTransformation);
	}

	private void recalculateViewportTransformation(Matrix dstMatrix,
			float centerRotation, float scaling, float[] translation) {
		dstMatrix.reset();
		// translate to center
		dstMatrix.preTranslate((float) getWidth() / 2.0f, (float) getHeight() / 2.0f);
		// translate
		dstMatrix.preTranslate(translation[0], translation[1]);
		// rotate around listener
		dstMatrix.preRotate(centerRotation);
		// scale system
		dstMatrix.preScale(scaling, scaling);
	}

	private void recalculateSizeScale() {
		float sizeInMeters;
		double upperBoundingPowOfTen;
		double scaleLengthInMeters;
		float scaleLengthInPixels;
		float scaleStartInPixels = 20.0f * GlobalData.pixelScaling;

		// get size of short display edge in meters
		sizeInMeters = inverseViewportTransformation.mapRadius(Math.min(getWidth(), 
				getHeight()) - scaleStartInPixels * 2.0f);

		// find upper bounding power of ten (0.01, 0.1, 1, 10, 100, etc.)
		if (sizeInMeters > 1.0) {
			upperBoundingPowOfTen = 10.0;
			while (true) {
				if (sizeInMeters / upperBoundingPowOfTen > 1.0f) {
					upperBoundingPowOfTen *= 10.0;
				} else {
					break;
				}
			}
		} else {
			upperBoundingPowOfTen = 1.0;
			while (true) {
				if (sizeInMeters / upperBoundingPowOfTen >= 0.1f) {
					break;
				} else {
					upperBoundingPowOfTen *= 0.1;
				}
			}
		}

		// map to subdivisions
		scaleLengthInMeters = sizeInMeters / upperBoundingPowOfTen;
		if (scaleLengthInMeters > 0.75)
			scaleLengthInMeters = 0.75;
		else if (scaleLengthInMeters > 0.5)
			scaleLengthInMeters = 0.5;
		else if (scaleLengthInMeters > 0.3)
			scaleLengthInMeters = 0.3;
		else if (scaleLengthInMeters > 0.2)
			scaleLengthInMeters = 0.2;
		else if (scaleLengthInMeters > 0.15)
			scaleLengthInMeters = 0.15;
		else
			scaleLengthInMeters = 0.1;
		scaleLengthInMeters *= upperBoundingPowOfTen;

		// get corresponding scale length in pixels
		scaleLengthInPixels = viewportTransformation.mapRadius((float) scaleLengthInMeters);

		// generate picture
		Canvas canvas = sizeScalePicture.beginRecording(0, 0);
		float y = 10.0f * GlobalData.pixelScaling;
		float xHalf = scaleStartInPixels + scaleLengthInPixels / 2.0f;
		float scaleEndInPixels = scaleStartInPixels + scaleLengthInPixels;

		SourcesView.paint.setARGB(255, 100, 100, 100);
		SourcesView.paint.setAntiAlias(true);
		canvas.drawLine(scaleStartInPixels, y, scaleEndInPixels, y, SourcesView.paint);
		canvas.drawLine(scaleStartInPixels, y, scaleStartInPixels, y + 5.0f * GlobalData.pixelScaling, SourcesView.paint);
		canvas.drawLine(xHalf, y, xHalf, y + 5.0f * GlobalData.pixelScaling, SourcesView.paint);
		canvas.drawLine(scaleEndInPixels, y, scaleEndInPixels, y + 5.0f * GlobalData.pixelScaling, SourcesView.paint);

		canvas.drawText("0m", scaleStartInPixels, y + 16.0f * GlobalData.pixelScaling, SourcesView.paint);

		// TODO switch between mm, cm, m, km...
		scaleHalfString.setLength(0);
		StringHelper.append(scaleHalfString, ((float) scaleLengthInMeters / 2.0f), 2);
		scaleHalfString.append("m");
		canvas.drawText(scaleHalfString, 0, scaleHalfString.length(), xHalf, y + 16.0f * GlobalData.pixelScaling, SourcesView.paint);
		
		scaleEndString.setLength(0);
		StringHelper.append(scaleEndString, (float) scaleLengthInMeters, 2);
		scaleEndString.append("m");
		canvas.drawText(scaleEndString, 0, scaleEndString.length(), scaleEndInPixels, y + 16.0f * GlobalData.pixelScaling, SourcesView.paint);
	}

	@Override
	public void onDraw(Canvas canvas) {
		// recalculate current viewport matrix if necessary
		if (scalingInterpolator.isActive()) { // zooming animation
			setCurrentScaling(scalingInterpolator.getCurrentValue());
			recalculateViewportTransformation();
			setViewportFlag(false);
		} else if (getAndClearViewportFlag()) {
			recalculateViewportTransformation();
		}
		if (getAndClearScalingFlag()) {
			recalculateSizeScale();
		}

		// clear background
		canvas.drawColor(getResources().getColor(R.drawable.col_background));

		// draw audio scene
		canvas.setMatrix(viewportTransformation);
		GlobalData.audioScene.draw(canvas, 1.0f / currentScaling, -currentRotation);

		// reset matrix
		canvas.setMatrix(null);

		// draw size scale
		sizeScalePicture.draw(canvas);
	}

	public void getSceneBounds(Matrix viewportTransformation, float[] minXY, float[] maxXY, float [] listenerPosition) {
		// get point from pool
		float[] point = GlobalData.pointPool.get();
		
		// map listener position to screen coordinate system
		listenerPosition[0] = 0.0f;
		listenerPosition[1] = 0.0f;
		viewportTransformation.mapPoints(listenerPosition);

		// set min/max to listener position
		minXY[0] = listenerPosition[0];
		minXY[1] = listenerPosition[1];
		maxXY[0] = listenerPosition[0];
		maxXY[1] = listenerPosition[1];
		
		int numSources = GlobalData.audioScene.getNumSoundSources();
		SoundSource s = null;
		for (int i = 0; i < numSources; i++) {
			s = GlobalData.audioScene.getSoundSource(i);
			point[0] = s.getX();
			point[1] = s.getY();

			// map to screen coordinate system
			viewportTransformation.mapPoints(point);

			// check if point lies outside current bounds
			if (point[0] < minXY[0])
				minXY[0] = point[0];
			if (point[0] > maxXY[0])
				maxXY[0] = point[0];
			if (point[1] < minXY[1])
				minXY[1] = point[1];
			if (point[1] > maxXY[1])
				maxXY[1] = point[1];
		}
		
		// put point back to pool
		GlobalData.pointPool.put(point);
	}

	public void transformToFitScene(boolean smooth) {
		float min[];
		float max[];
		float listenerPosition[];
		float scalingFactor = Float.POSITIVE_INFINITY;

		// get points from pool
		min = GlobalData.pointPool.get();
		max = GlobalData.pointPool.get();
		listenerPosition = GlobalData.pointPool.get();

		// get scene bounds in screen coordinates
		recalculateViewportTransformation();	// just to be sure we are up to date
		getSceneBounds(viewportTransformation, min, max, listenerPosition);

		min[0] = listenerPosition[0] - min[0];
		min[1] = listenerPosition[1] - min[1];
		max[0] = max[0] - listenerPosition[0];
		max[1] = max[1] - listenerPosition[1];

		// recalculate scaling
		if (min[0] > 0 || min[1] > 0 || max[0] > 0 || max[1] > 0) {
			// calculate for all four borders
			if (min[0] > 0) {
				scalingFactor = (listenerPosition[0] - FIT_SCENE_PIXEL_BORDER_2 * GlobalData.pixelScaling) / min[0];
			}
			
			if (min[1] > 0) {
				scalingFactor = Math.min(scalingFactor, (listenerPosition[1] - FIT_SCENE_PIXEL_BORDER_2 * GlobalData.pixelScaling) / min[1]);
			}
			
			if (max[0] > 0) {
				scalingFactor = Math.min(scalingFactor, ((float) getWidth() - FIT_SCENE_PIXEL_BORDER_2 * GlobalData.pixelScaling - listenerPosition[0]) / max[0]);
			}
			
			if (max[1] > 0) {
				scalingFactor = Math.min(scalingFactor, ((float) getHeight() - FIT_SCENE_PIXEL_BORDER_2 * GlobalData.pixelScaling - listenerPosition[1]) / max[1]);
			}
			
			// set best scaling
			if (smooth)
				zoomView(scalingFactor);
			else
				setCurrentScaling(currentScaling * scalingFactor);
		}
		
		// put points back to pool
		GlobalData.pointPool.put(min);
		GlobalData.pointPool.put(max);
		GlobalData.pointPool.put(listenerPosition);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// pass event to gesture detectors
		gestureDetector.onTouchEvent(event);
		scaleGestureDetector.onTouchEvent(event);
		return true;
	}

	@Override
	public boolean onDown(MotionEvent event) {
		scrolling = false;

		// determine transformed coordinate of touch point
		touchPoint[0] = event.getX();
		touchPoint[1] = event.getY();
		inverseViewportTransformation.mapPoints(touchPoint);

		// try to find nearest sound source
		lastTouchSoundSource = GlobalData.audioScene.getNearestSoundSource(touchPoint);
		if (lastTouchSoundSource != null) {
			// get distance (touch point to source) in pixels
			selectionOffset[0] = lastTouchSoundSource.getX();
			selectionOffset[1] = lastTouchSoundSource.getY();
			viewportTransformation.mapPoints(selectionOffset);
			selectionOffset[0] -= event.getX();
			selectionOffset[1] -= event.getY();
			float distance = FloatMath.sqrt(selectionOffset[0] * selectionOffset[0] + 
					selectionOffset[1] * selectionOffset[1]);

			// select source?
			if (distance > SOURCE_SELECT_RADIUS * GlobalData.pixelScaling) {
				lastTouchSoundSource = null;
			}
		}

		return true;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		// long press is disabled
	}

	@Override
	public boolean onScroll(MotionEvent firstEvent, MotionEvent thisEvent, float distanceX, float distanceY) {
		float point[] = GlobalData.pointPool.get();
		
		// transform sound source or surface?
		if (lastTouchSoundSource != null) { // transform sound source
			// is lastTouchSoundSource selected?
			if (!lastTouchSoundSource.isSelected()) {
				// select only this source
				GlobalData.audioScene.deselectAllSoundSources();
				GlobalData.audioScene.selectSoundSource(lastTouchSoundSource);
			}

			// save positions of sources if this is first scroll event
			if (!scrolling) {
				scrolling = true;
				firstScrollPoint[0] = firstEvent.getX();
				firstScrollPoint[1] = firstEvent.getY();
				ArrayList<SoundSource> selectedSources = GlobalData.audioScene.getSelectedSoundSources();
				int numSources = selectedSources.size();
				for (int i = 0; i < numSources; i++) { // loop through all currently selected sources
					selectedSources.get(i).getXY(point);
					viewportTransformation.mapPoints(point);
					selectedSources.get(i).savePosition(point);
				}
			}

			// translate or rotate?
			if (transformationMode == TransformationMode.TRANSLATE) { // translate
				ArrayList<SoundSource> selectedSources = GlobalData.audioScene.getSelectedSoundSources();
				int numSources = selectedSources.size();
				SoundSource soundSource;
				for (int i = 0; i < numSources; i++) { // loop through all currently selected sources
					soundSource = selectedSources.get(i);

					// transform screen coords into object coords, consider offset
					point[0] = soundSource.getSavedX() + thisEvent.getX() - firstScrollPoint[0];
					point[1] = soundSource.getSavedY() + thisEvent.getY() - firstScrollPoint[1];
					inverseViewportTransformation.mapPoints(point);

					// set new sound source position
					soundSource.setXY(point);
				}
			} else { // rotate
				// not implemented
			}
		} else { 
			// do something useful here?
		}

		GlobalData.pointPool.put(point);
		return true;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return true;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		if (lastTouchSoundSource == null) { // fit scene
			transformToFitScene(true);
		} else { // (un)mute sound sources
			if (!lastTouchSoundSource.isSelected()) {
				// (un)mute one source
				lastTouchSoundSource.setMuted(!lastTouchSoundSource.isMuted());
			} else {
				// (un)mute current selected group of sources
				ArrayList<SoundSource> selectedSources = GlobalData.audioScene.getSelectedSoundSources();
				int numSources = selectedSources.size();
				SoundSource soundSource;
				for (int i = 0; i < numSources; i++) { // loop through all currently selected sources
					soundSource = selectedSources.get(i);
					soundSource.setMuted(!soundSource.isMuted());
				}
			}
		}

		return true;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent e) {
		// nothing
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent event) {
		// (de)select one or all sound sources
		if (lastTouchSoundSource != null) {
			if (lastTouchSoundSource.isSelected()) {
				GlobalData.audioScene.deselectSoundSource(lastTouchSoundSource);
			} else {
				GlobalData.audioScene.selectSoundSource(lastTouchSoundSource);
			}
		} else {
			if (GlobalData.audioScene.getSelectedSoundSources().isEmpty()) {
				GlobalData.audioScene.selectAllSoundSources();
			} else {
				GlobalData.audioScene.deselectAllSoundSources();
			}
		}

		return true;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		// not used
		return false;
	}

	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		setCurrentScaling(initialViewportScaling * detector.getScaleFactor());
		recalculateViewportTransformation();
		return false;
	}

	@Override
	public boolean onScaleBegin(ScaleGestureDetector detector) {
		initialViewportScaling = getCurrentScaling();
		lastTouchSoundSource = null;
		return true;
	}

	@Override
	public void onScaleEnd(ScaleGestureDetector detector) {
	}
}
