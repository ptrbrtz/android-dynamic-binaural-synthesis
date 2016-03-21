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

/**
 * Right now functionality is limited to stereo 16-bit pcm output,
 * because that's all we need. But it could become more flexible.
 * Written audio will be put to a ring buffer/buffer queue on the
 * native side . Samplerate conversion happens (if needed) 
 * before writing to the ring buffer.
 * The ring buffer has to be big enough to hold at least one output
 * block (multiplier >= 1).
 * 
 * A stereo pair of samples is referred to as having "length of 1".  
 */
public class SuperpoweredAudioIO {
	private static final String TAG = "SuperpoweredAudioIO";

	public int internalSamplerate;
	public int outputSamplerate;
	public int internalBufSizeInFrames;
	public int outputBufSizeInFrames;
	public int ringBufferSizeMultiplier;
	public float samplerateScalingFactor;
	public boolean resample;
	public int maxInternalResampledBufSizeInFrames;
	public byte[] internalResampledBuf;
	public boolean initialized = false;
	
	private byte[] outBuf;
	int outBufMarkInFrames;

	// TODO more error checks for arguments
	public boolean setup(int internalBufSizeInFrames, int outputBufSizeInFrames, int internalSamplerate, int outputSamplerate,
			int ringBufferSizeMultiplier) throws InterruptedException {
		this.internalSamplerate = internalSamplerate;
		this.outputSamplerate = outputSamplerate;
		this.internalBufSizeInFrames = internalBufSizeInFrames;
		this.outputBufSizeInFrames = outputBufSizeInFrames;
		this.ringBufferSizeMultiplier = ringBufferSizeMultiplier;
		
		// check size of ring buffer
		if (ringBufferSizeMultiplier < 1) {
			Log.d(TAG, "ringBufferSizeMultiplier has to be >= 1");
			return false;
		}
		
		samplerateScalingFactor = (float) outputSamplerate / (float) internalSamplerate;
		maxInternalResampledBufSizeInFrames = (int) Math.ceil((float) internalBufSizeInFrames
				* samplerateScalingFactor * 1.1f);	// add 10%, just to be sure there is enough room
		resample = (internalSamplerate != outputSamplerate);
		
		outBuf = new byte[outputBufSizeInFrames * 4];
		outBufMarkInFrames = 0;
		internalResampledBuf = new byte[maxInternalResampledBufSizeInFrames * 4];
		
		return true;
	}
	
	// does start playing automatically
	public boolean start() {
		// init resampler and audio output
		initialized = setupSuperpowered(internalBufSizeInFrames, maxInternalResampledBufSizeInFrames,
				outputBufSizeInFrames, internalSamplerate, outputSamplerate, ringBufferSizeMultiplier);
		
		return initialized;
	}
	
	public void shutdown() {
		if (!initialized) {
			Log.d(TAG, "can't shutdown() - not initialized");
			return;
		}
		
		shutdownSuperpowered();
		initialized = false;
	}
	
	// possibly blocking, if all buffers are full
	public boolean write(byte[] audioData) throws InterruptedException {
		if (!initialized) {
			Log.d(TAG, "can't write() - not initialized");
			return false;
		}
		
		// check buffer size
		if (audioData.length < internalBufSizeInFrames * 4) {
			Log.d(TAG, "received too short buffer in write()");
			return false;
		}

		// resample
		byte [] finalBuf = audioData;
		int numOutputFrames = internalBufSizeInFrames;
		if (resample) {
			finalBuf = internalResampledBuf;
			numOutputFrames = resample(audioData, internalResampledBuf);
			if (numOutputFrames > maxInternalResampledBufSizeInFrames) {
				Log.d(TAG, "resampled buffer length bigger than expected: got " + numOutputFrames + ", expected maximal " + maxInternalResampledBufSizeInFrames);
				return false;
			}
		}

		// enqueue
		enqueueSamples(finalBuf, numOutputFrames);	// send to jni, might block

		return true;
	}
	
	public void play() {
		if (!initialized) {
			Log.d(TAG, "can't play() - not initialized");
			return;
		}
		
		nativePlay(true);
	}
	
	// pause as soon as possible, not playing pending buffers
	public void pause() {
		if (!initialized) {
			Log.d(TAG, "can't pause() - not initialized");
			return;
		}
		
		nativePause();
	}
	
	// pause after finish playing pending buffers
	public void flushAndPause() {
		if (!initialized) {
			Log.d(TAG, "can't pause() - not initialized");
			return;
		}
		
		nativeFlushAndPause();
	}
	
	// native methods
	private native boolean setupSuperpowered(int internalBufSizeInFrames, int resampledInternalBufSizeInFrames, 
			int outputBufSizeInFrames, int internalSamplerate, int outputSamplerate, int ringBufferSizeMultiplier);
	private native void shutdownSuperpowered(); 
	private native int resample(byte[] inputData, byte[] outputData);
	private native void nativePlay(boolean resetRingBuf);
	private native void nativePause();
	private native void nativeFlushAndPause();
	public native void linearFade(byte[] buf, int length, int sign);
	private native void enqueueSamples(byte[] buf, int numFrames);

	// load shared lib
	static {
		try {
			System.loadLibrary("stlport_shared");	// C++ shared lib, load first
			System.loadLibrary("superpoweredAudioIO");
			Log.d(TAG, "library loaded...");
		} catch (Exception e) {
			Log.e(TAG, "could not load library...");
		}
	}
}
