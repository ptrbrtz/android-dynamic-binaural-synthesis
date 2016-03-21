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

public abstract class Entity {
	private static final String TAG = "Entity";
	
	protected float[] position = {0.0f, 0.0f};
	protected float[] transformedPosition = {0.0f, 0.0f};
	protected float[] savedPosition = {0.0f, 0.0f};
	protected boolean selected;
	protected float azimuth;
	protected boolean positionFlag = true;
	protected boolean azimuthFlag = true;
	protected boolean selectedFlag = true;
	
	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public float getX() {
		return position[0];
	}

	public void setX(float x) {
		this.position[0] = x;
		positionFlag = true;
	}

	public float getY() {
		return position[1];
	}
	
	public void setY(float y) {
		this.position[1] = y;
		positionFlag = true;
	}
	
	public void getXY(float[] pos) {
		pos[0] = getX();
		pos[1] = getY();
	}
	
	public void setXY(float[] position) {
		setX(position[0]);		
		setY(position[1]);		
	}

	public void setXY(float x, float y) {
		setX(x);		
		setY(y);		
	}

	public void savePosition(float x, float y) {
		savedPosition[0] = x;
		savedPosition[1] = y;
	}
	
	public void savePosition(float[] pos) {
		savedPosition[0] = pos[0];
		savedPosition[1] = pos[1];
	}
	
	public float getSavedX() {
		return savedPosition[0];
	}
	
	public float getSavedY() {
		return savedPosition[1];
	}
	
	public float getAzimuth() {
		return azimuth;
	}

	public void setAzimuth(float azimuth) {
		this.azimuth = azimuth;
		azimuthFlag = true;
	}

	public boolean getAndClearPositionFlag() {
		boolean returnValue = positionFlag;
		positionFlag = false;
		return returnValue;
	}

	public void setPositionFlag(boolean positionFlag) {
		this.positionFlag = positionFlag;
	}

	public boolean getAndClearAzimuthFlag() {
		boolean returnValue = azimuthFlag;
		azimuthFlag = false;
		return returnValue;
	}

	public void setAzimuthFlag(boolean azimuthFlag) {
		this.azimuthFlag = azimuthFlag;
	}

	public boolean getAndClearSelectedFlag() {
		boolean returnValue = selectedFlag;
		selectedFlag = false;
		return returnValue;
	}

	public void setSelectedFlag(boolean selectedFlag) {
		this.selectedFlag = selectedFlag;
	}

	public Entity(float posX, float posY) {
		initEntity();		
		setX(posX);
		setY(posY);
	}

	public Entity() {
		initEntity();
	}
	
	private void initEntity() {
		setSelected(false);
		setX(0.0f);
		setY(0.0f);
		azimuth = 0.0f;
	}
	
	public abstract void draw(Canvas canvas, float inverseScaling, float rotation);
}
