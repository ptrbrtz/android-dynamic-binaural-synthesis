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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.graphics.Canvas;
import android.graphics.Paint;
import net.ptrbrtz.adbs.android.utils.WaveFileInfo;

public class SoundSource extends Entity {
	protected static final String TAG = "SoundSource";
	protected static final float SOURCE_RADIUS = 8f;
	protected static final float SOURCE_HALO_RADIUS = SourcesView.SOURCE_SELECT_RADIUS;
	
	// static fields for drawing
	protected static Paint paint = null;
	
	protected String name;
	protected String audioFileName;
	protected float volume;
	protected float level;
	protected float normalizedLevel; // [-60dB, 12dB] -> [0.0, 1.0] 
	protected boolean muted;
	
	private float[] sourceCircleRadiusCache = {0.0f, 0.0f};	// one key/value pair
	private static final double LOG2 = Math.log(2.0);
	
	private FileInputStream audioFileStream;
	private WaveFileInfo audioFileInfo;
	private AudioFileBuffer audioFileBuffer;

	public SoundSource() {
		super();
		setName("<unnamed>");
		setAudioFileName(null);
		setVolume(0.0f);
		setMuted(false);
	}

	public SoundSource(float posX, float posY, String name, String audioFile, float volume, boolean muted) {
		super(posX, posY);
		setName(name);
		setAudioFileName(audioFile);
		setVolume(volume);
		setMuted(muted);
	}
	
	public static Paint getPaint() {
		return paint;
	}

	public static void setPaint(Paint paint) {
		SoundSource.paint = paint;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (name.length() > 14)
			this.name = name.substring(0, 14) + "...";
		else this.name = name;
	}

	public String getAudioFileName() {
		return audioFileName;
	}

	public void setAudioFileName(String audioFileName) {
		this.audioFileName = audioFileName;
	}

	public float getVolume() {
		return volume;
	}

	public void setVolume(float volume) {
		this.volume = volume;
	}

	public float getLevel() {
		return level;
	}

	public void setLevel(float level) {
		this.level = level;
		this.normalizedLevel = (level + 60.0f) / 72.0f; // 12dB headroom
		if (this.normalizedLevel < 0.0f) this.normalizedLevel = 0.0f;
		else if (this.normalizedLevel > 1.0f) this.normalizedLevel = 1.0f;
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	@Override
	public void draw(Canvas canvas, float inverseScaling, float rotation) {
	
		// save current transformation matrix
		canvas.save();
		
		// translate and scale
		canvas.translate(position[0], position[1]);
		canvas.scale(inverseScaling, inverseScaling);

		// draw halo
		SoundSource.paint.setStrokeWidth(2.0f * GlobalData.pixelScaling);
		SoundSource.paint.setARGB(100, 0, 0, 255);
		if (selected) {
			SoundSource.paint.setStyle(Paint.Style.FILL);
		} else {
			SoundSource.paint.setStyle(Paint.Style.STROKE);
		}
		canvas.drawCircle(0.0f, 0.0f, SOURCE_HALO_RADIUS * GlobalData.pixelScaling, SoundSource.paint);
		
		// calculate source circle radius
		if (sourceCircleRadiusCache[0] != inverseScaling) {
			// log scaling, base 2
			float scaling = (float) (Math.log(1.0f / inverseScaling) / LOG2 - 0.5) / 5.0f;
			// limit value to range
			if (scaling > 2.0) scaling = 2.0f;
			if (scaling < 0.2) scaling = 0.2f;
			// save to cache
			sourceCircleRadiusCache[0] = inverseScaling;
			sourceCircleRadiusCache[1] = SOURCE_RADIUS * scaling;
		}
		
		// draw source circle
		if (selected) {
			SoundSource.paint.setARGB(80, 0, 0, 0);
		} else {
			SoundSource.paint.setARGB(100, 0, 0, 0);
		}		
		if (isMuted()) {
			SoundSource.paint.setStyle(Paint.Style.STROKE);
		} else {
			SoundSource.paint.setStyle(Paint.Style.FILL);
		}
		SoundSource.paint.setStrokeWidth(1.5f * GlobalData.pixelScaling);
		canvas.drawCircle(0.0f, 0.0f, sourceCircleRadiusCache[1] * GlobalData.pixelScaling, SoundSource.paint);
		
		// draw text
		canvas.rotate(rotation); // rotate to show text correctly
		if (selected) {
			SoundSource.paint.setARGB(255, 50, 50, 50);
		} else {
			SoundSource.paint.setARGB(255, 127, 127, 127);
		}
		SoundSource.paint.setTextAlign(Paint.Align.CENTER);
		SoundSource.paint.setStrokeWidth(0.0f);
		SoundSource.paint.setStyle(Paint.Style.FILL);
		SoundSource.paint.setTextSize(9.0f * GlobalData.pixelScaling);
		canvas.drawText(name, 0.0f, sourceCircleRadiusCache[1] * GlobalData.pixelScaling + 6.0f * GlobalData.pixelScaling + SoundSource.paint.getTextSize(), SoundSource.paint);

		// restore saved transformation matrix
		canvas.restore();
	}

	public void setupIO(byte[] buffer, int bufferOffset) throws FileNotFoundException, IOException {
		// open file stream
		audioFileStream = new FileInputStream(getAudioFileName());
		
		// read file info
		audioFileInfo = new WaveFileInfo(audioFileStream);
		
		// set up buffer and fill it
		audioFileBuffer = new AudioFileBuffer(buffer, bufferOffset, audioFileStream, audioFileInfo.getFirstAudioByteIndex());
		audioFileBuffer.setLoop(true);
		audioFileBuffer.bufferNextChunk();
	}
	
	public void shutdownIO() {
		if (audioFileBuffer != null) {
			audioFileBuffer.dispose();
			audioFileBuffer = null;
		}
		
		if (audioFileStream != null) {
			try {
				audioFileStream.close();
			} catch (IOException e) {}
			audioFileStream = null;
		}
	}

	public FileInputStream getAudioFileStream() {
		return audioFileStream;
	}

	public WaveFileInfo getAudioFileInfo() {
		return audioFileInfo;
	}

	public AudioFileBuffer getAudioFileBuffer() {
		return audioFileBuffer;
	}

	@Override
	protected void finalize() throws Throwable {
		shutdownIO();
		super.finalize();
	}
}
