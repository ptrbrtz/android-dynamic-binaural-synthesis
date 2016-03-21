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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.os.Process;
import android.util.FloatMath;
import android.util.Log;
import net.ptrbrtz.adbs.android.utils.MathUtils;
import net.ptrbrtz.adbs.android.utils.WaveFileInfo;

public class AudioScene {
	private static final String TAG = "AudioScene";
	private ArrayList<SoundSource> soundSources;
	private ArrayList<SoundSource> selectedSoundSources;
	private Listener listener = null;
	private float volume;	// in %
	private AtomicBoolean volumeFlag = new AtomicBoolean(false);
	private TransportState transportState;
	private AtomicBoolean transportStateFlag = new AtomicBoolean(false);
	
	private SuperpoweredAudioIO superpoweredAudioIO;
	private AudioThread audioThread;
	private int sampleRate;
	private int bitsPerSample;
	private float[][][] hrirs = null;
	private InternalAudioBufferSettings internalBufSettings = new InternalAudioBufferSettings();

	private String sceneDescriptionFilePath;
	private String sceneDescriptionFileName;
	
	// static fields for drawing
	private static Paint paint = null;
	
	public enum TransportState {
		PLAYING, PAUSED
	}
	
	public static class AudioBufferSettings {
		// float values are in ms, not samples
		public float crossfadeOverlapSize;
		public float processingBlockSize;
		public int ringBufSizeMultiplier;
	}

	public static class InternalAudioBufferSettings {
		public int monoAudioOutBlockSizeInBytes;
		public int monoAudioOutBlockSizeInSamples;
		public int minMonoFftBlockSizeInSamples;
		public int monoFftBlockSizeInBytes;
		public int monoFftBlockSizeInSamples;
		public int monoFftPaddingInBytes;
		public int monoFftPaddingInSamples;
		public int monoNumFilterCoeffFrames;
		public int monoFilterOverlapInBytes;
		public int monoFilterOverlapInSamples;
		public int monoCrossfadeOverlapInBytes;
		public int monoCrossfadeOverlapInSamples;
	}
	
	public AudioScene() {
		soundSources = new ArrayList<SoundSource>();
		selectedSoundSources = new ArrayList<SoundSource>();
		setVolume(50.0f);
		setTransportState(TransportState.PAUSED);
		listener = new Listener();
	}
	
	public static Paint getPaint() {
		return paint;
	}

	public static void setPaint(Paint paint) {
		AudioScene.paint = paint;
	}

	public float getVolume() {
		return volume;
	}

	public void setVolume(float volume) {
		this.volume = volume;
		volumeFlag.set(true);
	}

	public int getSampleRate() {
		return sampleRate;
	}
	
	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
	}
	
	public int getBitsPerSample() {
		return bitsPerSample;
	}
	
	public void setBitsPerSample(int bitsPerSample) {
		this.bitsPerSample = bitsPerSample;
	}
	
	public TransportState getTransportState() {
		return transportState;
	}
	
	public void setTransportState(TransportState state) {
		this.transportState = state;
		transportStateFlag.set(true);
	}
	
	public void rewind() {
		if (audioThread != null) {
			audioThread.rewind();
		}
	}
	
	public void pause() {
		if (audioThread != null) {
			audioThread.pause();
			setTransportState(TransportState.PAUSED);
		}
	}
	
	public void play() {
		if (audioThread != null) {
			audioThread.play();
			setTransportState(TransportState.PLAYING);
		}
	}
	
	public boolean getAndClearVolumeFlag() {
		return volumeFlag.getAndSet(false);
	}
	
	public boolean getAndClearTransportStateFlag() {
		return transportStateFlag.getAndSet(false);
	}
	
	public void addSoundSource(SoundSource s) {
		soundSources.add(s);
	}
	
	public boolean removeSoundSource(SoundSource s) {
		return soundSources.remove(s);
	}
	
	public SoundSource getSoundSource(int index) {
		try {
			return soundSources.get(index);
		} catch (IndexOutOfBoundsException e) {}
		return null;
	}

	public void draw(Canvas canvas, float inverseScaling, float inverseRotation) {
		// draw reference
		listener.draw(canvas, inverseScaling, 0.0f);
		
		// draw sound sources
		int numSources = soundSources.size();
		for (int i = 0; i < numSources; i++) {
			soundSources.get(i).draw(canvas, inverseScaling, listener.getAzimuth());
		}
	}

	// position must be given in sound source coordinates 
	public SoundSource getNearestSoundSource(float[] pos) {
		float dX, dY, distance, bestDistance;
		SoundSource nearestSource = null;
		
		// find nearest sound source
		bestDistance = Float.POSITIVE_INFINITY;
		int numSources = soundSources.size();
		SoundSource s = null;
		for (int i = 0; i < numSources; i++) {
			s = soundSources.get(i);
			dX = pos[0] - s.getX();
			dY = pos[1] - s.getY();
			distance = FloatMath.sqrt(dX*dX + dY*dY);
			if(distance < bestDistance) {
				bestDistance = distance;
				nearestSource = s;
			}
		}
		
		return nearestSource;
	}
	
	public void selectSoundSource(SoundSource source) {
		// check if sound source already contained
		if (selectedSoundSources.contains(source))
			return;
		
		// add new source to list of selected source
		selectedSoundSources.add(source);
		
		// set selected-flag in source itself
		source.setSelected(true);
	}
	
	public void deselectSoundSource(SoundSource source) {
		// deselect source
		selectedSoundSources.remove(source);
		source.setSelected(false);
	}
	
	public void selectAllSoundSources() {
		int numSources = soundSources.size();
		for (int i = 0; i < numSources; i++) {
			soundSources.get(i).setSelected(true);
		}
		selectedSoundSources.addAll(soundSources);
	}
	
	public void deselectAllSoundSources() {
		int numSources = soundSources.size();
		for (int i = 0; i < numSources; i++) {
			soundSources.get(i).setSelected(false);
		}
		selectedSoundSources.clear();
	}
	
	public ArrayList<SoundSource> getSelectedSoundSources() {
		return selectedSoundSources;
	}

	public Listener getListener() {
		return listener;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	public String getSceneDescriptionFilePath() {
		return sceneDescriptionFilePath;
	}

	public void setSceneDescriptionFilePath(String sceneDescriptionFilePath) {
		this.sceneDescriptionFilePath = sceneDescriptionFilePath;
	}

	public String getSceneDescriptionFileName() {
		return sceneDescriptionFileName;
	}

	public void setSceneDescriptionFileName(String sceneDescriptionFileName) {
		this.sceneDescriptionFileName = sceneDescriptionFileName;
	}

	public void reset() {
		shutdownIO();
		soundSources.clear();
		selectedSoundSources.clear();
	}
	
	public int getNumSoundSources() {
		return soundSources.size();
	}
	
	public static float samplesToMillis(float numSamples, int sampleRate) {
		return numSamples / (float)sampleRate * 1000.0f; 
	}

	public static int samplesToMillis(int numSamples, int sampleRate) {
		return Math.round(samplesToMillis((float)numSamples, sampleRate)); 
	}

	public static float millisToSamples(float millis, int sampleRate) {
		return millis * (float)sampleRate / 1000.0f; 
	}
	
	public static int millisToSamples(int millis, int sampleRate) {
		return Math.round(millisToSamples((float)millis, sampleRate));
	}

	private static int cachedNativeBufferSize = 0;
	public static int getNativeBufferSize(Context context) {
		if (cachedNativeBufferSize == 0) {
			AudioManager audioManager = (AudioManager) context.getSystemService(GlobalData.applicationContext.AUDIO_SERVICE);
			cachedNativeBufferSize = Integer.valueOf(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
		}
		
		return cachedNativeBufferSize;
	}
	
	private static int cachedNativeSamplerate = 0;
	public static int getNativeSamplerate(Context context) {
		if (cachedNativeSamplerate == 0) { 
			AudioManager audioManager = (AudioManager) context.getSystemService(GlobalData.applicationContext.AUDIO_SERVICE);
			cachedNativeSamplerate = Integer.valueOf(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
		}
		
		return cachedNativeSamplerate;
	}

	public void getInternalAudioBufferSettings(AudioBufferSettings in, int filterLength, InternalAudioBufferSettings out) {
		out.monoAudioOutBlockSizeInSamples = Math.round(millisToSamples(in.processingBlockSize, sampleRate));
		out.monoAudioOutBlockSizeInBytes = out.monoAudioOutBlockSizeInSamples * 2;

		// calculate filter overlap
		out.monoNumFilterCoeffFrames =  filterLength;
		out.monoFilterOverlapInSamples =out. monoNumFilterCoeffFrames - 1;	// for FIR filter
		out.monoFilterOverlapInBytes = out.monoFilterOverlapInSamples * 2;	// samples are in short, one short has two bytes
		
		// crossfade block overlap
		out.monoCrossfadeOverlapInSamples = Math.round(millisToSamples(in.crossfadeOverlapSize, sampleRate));
		out.monoCrossfadeOverlapInBytes = out.monoCrossfadeOverlapInSamples * 2;
		
		// calculate fft block size
		out.minMonoFftBlockSizeInSamples = out.monoAudioOutBlockSizeInSamples + (out.monoFilterOverlapInSamples + out.monoCrossfadeOverlapInSamples);
		// find next bigger number consisting of prime factors 2 and up to two 3s or up to two 5s
		out.monoFftBlockSizeInSamples = Math.min(MathUtils.getNextPowerOfTwo(out.minMonoFftBlockSizeInSamples),
				MathUtils.getNextPowerOfTwo(out.minMonoFftBlockSizeInSamples, 3));
		out.monoFftBlockSizeInSamples = Math.min(out.monoFftBlockSizeInSamples,
				MathUtils.getNextPowerOfTwo(out.minMonoFftBlockSizeInSamples, 3*3));
		out.monoFftBlockSizeInSamples = Math.min(out.monoFftBlockSizeInSamples,
				MathUtils.getNextPowerOfTwo(out.minMonoFftBlockSizeInSamples, 5));
		out.monoFftBlockSizeInSamples = Math.min(out.monoFftBlockSizeInSamples,
				MathUtils.getNextPowerOfTwo(out.minMonoFftBlockSizeInSamples, 5*5));
		out.monoFftBlockSizeInBytes = out.monoFftBlockSizeInSamples * 2;
		out.monoFftPaddingInSamples = out.monoFftBlockSizeInSamples - out.minMonoFftBlockSizeInSamples;
		out.monoFftPaddingInBytes = out.monoFftPaddingInSamples * 2;
	}
	
	public void setupIO(AudioBufferSettings bufferSettings) throws Exception {
		// set audio format
		if (bitsPerSample != 16) {
			throw new Exception("Only 16bit pcm wav files are supported currently");
		}
		
		// check sample rate
		if (sampleRate != GlobalData.DEFAULT_SAMPLERATE) {
			throw new Exception("Only "+ GlobalData.DEFAULT_SAMPLERATE + " Hz audio scene sample rate is supported currently");
		}
		
		// init HRIR stuff
		this.hrirs = GlobalData.hrirs;
		if (hrirs == null) {
			throw new Exception("No HRIRs loaded");
		} else if (hrirs.length != 2 || hrirs[0].length != 360) {
			throw new Exception("Number of HRIR pairs does not equal 360");
		}
		
		getInternalAudioBufferSettings(bufferSettings, hrirs[0][0].length, internalBufSettings);
		superpoweredAudioIO = new SuperpoweredAudioIO();
		if (!superpoweredAudioIO.setup(internalBufSettings.monoAudioOutBlockSizeInSamples, getNativeBufferSize(GlobalData.applicationContext),
				sampleRate, getNativeSamplerate(GlobalData.applicationContext), bufferSettings.ringBufSizeMultiplier))
			throw new Exception("Could not setup() superpoweredAudioIO");

		// debug output
		if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
			Log.d(TAG, "***************************************");
			Log.d(TAG, "nativeMonoBufSizeInSamples = " + getNativeBufferSize(GlobalData.applicationContext));
			Log.d(TAG, "nativeSamplerate = " + getNativeSamplerate(GlobalData.applicationContext));
			Log.d(TAG, "monoAudioOutBlockSizeInSamples = " + internalBufSettings.monoAudioOutBlockSizeInSamples);
			Log.d(TAG, "monoFilterOverlapInSamples = " + internalBufSettings.monoFilterOverlapInSamples);
			Log.d(TAG, "monoCrossfadeOverlapInSamples = " + internalBufSettings.monoCrossfadeOverlapInSamples);
			Log.d(TAG, "minMonoFftBlockSizeInSamples = " + internalBufSettings.minMonoFftBlockSizeInSamples);
			Log.d(TAG, "monoFftBlockSizeInSamples = " + internalBufSettings.monoFftBlockSizeInSamples);
			Log.d(TAG, "monoFftPaddingInSamples = " + internalBufSettings.monoFftPaddingInSamples);
			Log.d(TAG, "monoRingBufferSizeInSamples = " + getNativeBufferSize(GlobalData.applicationContext) * bufferSettings.ringBufSizeMultiplier
					+ " (multiplier = " + bufferSettings.ringBufSizeMultiplier + ")");
			Log.d(TAG, "***************************************");
		}
		
		// setup sound sources
		int numSources = soundSources.size();
		for (int i = 0; i < numSources; i++) {
			soundSources.get(i).setupIO(new byte[internalBufSettings.monoFftBlockSizeInBytes - internalBufSettings.monoFftPaddingInBytes],
					internalBufSettings.monoFilterOverlapInBytes + internalBufSettings.monoCrossfadeOverlapInBytes);
			
			// check audio file properties
			WaveFileInfo wfi = soundSources.get(i).getAudioFileInfo();
			if (wfi.getFormat() != WaveFileInfo.FORMAT_PCM) {
				throw new Exception("Only 16bit pcm wav files are supported currently, no u-law/a-law/etc.");
			}
			if (wfi.getSampleRate() != sampleRate) {
				throw new Exception("Sample rate of audio file '" + soundSources.get(i).getAudioFileName() + "' does not match scene sample rate of " + this.sampleRate + "Hz (Real-time resampling is not supported yet)");
			}
			if (wfi.getBitsPerSample() != bitsPerSample) {
				throw new Exception("Bit depth of audio file '" + soundSources.get(i).getAudioFileName() + "' does not match scene bit depth of " + this.bitsPerSample + " bits/sample (Real-time conversion is not supported yet)");
			}
		}
		
		// init native fir filter
		if (FirFilter.kissfftCreate(hrirs[0], hrirs[1], internalBufSettings.monoFftBlockSizeInSamples,
				internalBufSettings.monoCrossfadeOverlapInSamples, internalBufSettings.monoFftPaddingInSamples) == -1) {
			throw new Exception("Could not init FFT.");
		}
		
		// create and start audio thread
		audioThread = new AudioThread();
		audioThread.start();
	}
	
	public void shutdownIO() {
		// stop audio thread, audio track will be stopped when thread exits
		if (audioThread != null) {
			audioThread.quit();
			try {
				audioThread.join();
			} catch (InterruptedException e) {}
		}
		
		// shutdown sound sources
		int numSources = soundSources.size();
		for (int i = 0; i < numSources; i++) {
			soundSources.get(i).shutdownIO();
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		shutdownIO();
		super.finalize();
	}
	
	private class AudioThread extends Thread {
		private static final String TAG = "AudioThread";

		private Object transportStateMonitor = new Object();
		private volatile boolean paused = false;
		private volatile boolean pauseRequest = false;
		private volatile boolean resumeRequest = false;
		private volatile boolean quitRequest = false;
		private volatile boolean rewindRequest = false;

		private boolean doPause = false;
		private boolean doResume = false;
		private boolean doQuit = false;
		private boolean doRewind = false;
		private boolean doFade = false;
		private boolean doFirCrossfade = true;
		private int fadeDirection = 1;
		
		public void pause() {
			synchronized (transportStateMonitor) {
				pauseRequest = true;
			}
		}
		
		public void play() {
			synchronized (transportStateMonitor) {
				pauseRequest = false;
				if (paused)	{
					resumeRequest = true;
					transportStateMonitor.notify(); // wake up
				}
			}
		}

		public void rewind() {
			synchronized (transportStateMonitor) {
				rewindRequest = true;
			}
		}

		public void quit() {
			synchronized (transportStateMonitor) {
				quitRequest = true;
				if (paused) transportStateMonitor.notify();
			}
		}

		// produce some cpu load, used for benchmarking
		private void benchmarkCpuLoad() {
			for (int k = 0;  k < 100000; k++) {
				int j=k;
				double d = Math.sqrt(k);
				k = (int) d - j;
				k = j;
			}
		}
		
		/**
		 * Returns angle with respect to x-axis in counterclockwise sense in radians
		 * Uses x-is-north/y-is-east coordinate system
		 * (1/0) is 0°
		 * (0/-1) is 90°
		 * (-1/0) is 180°
		 * (0/1) is 270°
		 * 
		 * @param x x component of 2D vector
		 * @return Angle in radians
		 */
		public  float angleX2Rad(float x, float y) {
			float angle;
			
			if (x < 0.0f) {
				angle = (float) (Math.PI - Math.atan(y/x));
			} else if (x > 0.0f ) {
				if (y > 0.0f) {
					angle = (float) (2.0 * Math.PI - Math.atan(y/x));
				} else {
					angle = (float) (-Math.atan(y/x));
				}
			} else {
				if (y > 0.0f) {
					angle = (float) (1.5 * Math.PI);
				} else if (y < 0.0f) {
					angle = (float) (0.5 * Math.PI);
				} else {
					angle = 0.0f;
				}
			}
			
			return angle;
		}

		/**
		 * Returns angle with respect x-axis in counterclockwise sense in degrees
		 * Uses x-is-north/y-is-east coordinate system
		 * (1/0) is 0
		 * (0/-1) is Pi/2
		 * (-1/0) is Pi
		 * (0/1) is 3Pi/2
		 * 
		 * @param x x component of 2D vector
		 * @return Angle in degrees
		 */
		public float angleX2Deg(float x, float y) {
			return (float) (angleX2Rad(x, y) * 180.0 / Math.PI);
		}

		@Override
		public void run() {
			// set up high thread priority
			// (note: messes up nanoTime with traceview when not doing full trace, but just sampling)
			try {
				Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
			} catch(Exception e) {
				Log.e(TAG, "Could not set process priority: " + e.getMessage());
			}
			
			// set volume and start playing audio track
			setTransportState(TransportState.PLAYING);
			
			// some variables
			SoundSource soundSource;
			int angle;
			float sampleScaling;
			float defaultSampleScaling;
			float sceneSampleScaling;
			float distance;
			boolean allEndOfStream;
			boolean sourceMuted;
			float sourceXY[] = new float[2];
			int bufferStatus;
			int numSources = soundSources.size();
			
			// for benchmarking and latency testing
			boolean localLatencyTestRunning = false;
			int numSkips = 000; // = 10s @ 5ms loops
			int maxNumRuns = 1000;
			
//			RuntimeStatsBenchmark renderBlockBenchmark = new RuntimeStatsBenchmark(TAG, "RENDER BLOCK", numSkips, maxNumRuns);
			RuntimeStatsBenchmark renderBlockBenchmark = new RuntimeStatsBenchmark(TAG, "RENDER BLOCK", numSkips, maxNumRuns, true, true);
			RuntimeStatsBenchmark wholeLoopBenchmark = new RuntimeStatsBenchmark(TAG, "WHOLE LOOP", numSkips, maxNumRuns, false, false);
			RuntimeStatsBenchmark renderSourceBenchmark = new RuntimeStatsBenchmark(TAG, "RENDER SOURCE", numSkips * numSources, maxNumRuns * numSources);
//			RuntimeStatsBenchmark zeroBenchmark = new RuntimeStatsBenchmark(TAG, "ZERO", numSkips, maxNumRuns, false, false);
//			RuntimeStatsBenchmark metaZeroBenchmark = new RuntimeStatsBenchmark(TAG, "META-ZERO", numSkips, maxNumRuns);
//			RuntimeStatsBenchmark writeToTrackBenchmark = new RuntimeStatsBenchmark(TAG, "WRITE", numSkips, maxNumRuns);
//			RuntimeStatsBenchmark waitFileBenchmark = new RuntimeStatsBenchmark(TAG, "WAIT FILE", numSkips * numSources, maxNumRuns * numSources);
//			RuntimeStatsBenchmark gcBenchmark = new RuntimeStatsBenchmark(TAG, "GC", numSkips, maxNumRuns);
//			RuntimeStatsBenchmark cpuLoadBenchmark = new RuntimeStatsBenchmark(TAG, "CPU LOAD", numSkips, maxNumRuns, true, true);
//			RuntimeStatsBenchmark sleepBenchmark = new RuntimeStatsBenchmark(TAG, "SLEEP", numSkips, maxNumRuns);
//			RuntimeStatsBenchmark bufSizeBenchmark = new RuntimeStatsBenchmark(TAG, "BUF SIZE", numSkips, maxNumRuns);
//			RuntimeStatsBenchmark tempBenchmark = new RuntimeStatsBenchmark(TAG, "TEMP", numSkips, maxNumRuns);
			// this one is just used as convenient store-data-and-mail-it container
			RuntimeStatsBenchmark latencyData = new RuntimeStatsBenchmark(TAG, "latency data", 0, 20, true, false);
			
			// buffers
			byte[] input;
			byte[] outgoingData = new byte[internalBufSettings.monoAudioOutBlockSizeInBytes * 2];	// *2 because output is stereo
			
			// default attenuation depends on number of sound sources
			defaultSampleScaling = 2.0f;
			if (numSources > 1) {
//				defaultSampleScaling /= (float) numSources * 0.5f;	// TODO is 0.5 good weighting? 
			}
			
			// init scene volume
			sceneSampleScaling = volume / 100.0f;
			
			// try to invoke gc before starting playback
			System.gc();
			
			// start audio io
			if (!superpoweredAudioIO.start())
				Log.e(TAG, "Could not start() superpoweredAudiIO");

			// play loop
			// NOTE:
			// All unsynchronized reading/writing of sound source properties and other shared variables in this
			// loop can be done without synchronizing (between this thread and the UI-thread), because:
			// a) content of soundSources ArrayList is not altered while this AudioThread is running
			// b) contents of soundSource and sub-objects accessed here are either also not altered while this 
			//    AudioThread is running - or they are accessed in an atomic way
			while (!doQuit) {
				if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
					wholeLoopBenchmark.startTimer();
					renderBlockBenchmark.startTimer();
				}
				
				// update output volume?
				if (getAndClearVolumeFlag()) {
					sceneSampleScaling = volume / 100.0f; // atomic read of volume
				}

				FirFilter.beginRenderingBlock();

				// process all sound sources
				allEndOfStream = true;
				for (int i = 0; i < numSources; i++) {
					soundSource = soundSources.get(i);
					
					// get buffered samples. this blocks if background buffering not ready yet.
					// no locking required, since next buffer refill will explicitly be triggered by this thread
					/**if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) waitFileBenchmark.startTimer();**/
					input = soundSource.getAudioFileBuffer().getBuffer();
					/**if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) waitFileBenchmark.stopTimer();**/
					
					// do stuff that would normally require synchronization, because UI thread accesses this too
					// in this case, fields are atomically read and cached, so no locks are required
					sourceMuted = soundSource.isMuted();
					soundSource.getXY(sourceXY);	// TODO this is just "nearly atomic", since x and y could be "out of sync"
					
					// do not render source if muted  :)
					if (!sourceMuted) {
						// calculate angle/hrtf index of sound source
						angle = Math.round(angleX2Deg(-sourceXY[1], sourceXY[0]) + listener.azimuth);
						angle %= 360;
						if (angle < 0) angle += 360;
						
						// calculate distance of sound source (listener is at 0/0 always)
						distance = (float) Math.sqrt(sourceXY[0] * sourceXY[0] + sourceXY[1] * sourceXY[1]);
						
						// calculate sound source attenuation
						// TODO different scaling for near field?
						if (distance < 0.2f) {
							distance = 0.2f;
						}
						sampleScaling = defaultSampleScaling * sceneSampleScaling / (float) Math.sqrt(distance);
						
						// do filtering
						if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) renderSourceBenchmark.startTimer();
						if (FirFilter.kissfftFilter(input, angle, sampleScaling) == -1) {
							// tell sources mover activity an error occured and quit this audio thread
							GlobalData.playActivityMsgHandler.sendEmptyMessage(PlayActivity.AUDIOTHREAD_FIR_FILTER_ERROR_MSG);
							quit();
						}
						if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) renderSourceBenchmark.stopTimer();
					}
					
					// benchmarking: defined cpu load (without any i/o)
					/**if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
						for (int k = 0; k < 10; k++) {
							cpuLoadBenchmark.startTimer();
							benchmarkCpuLoad();
							cpuLoadBenchmark.stopTimer();
						}
					}**/

					// check buffer status
					bufferStatus = soundSource.getAudioFileBuffer().getStatus();
					if (bufferStatus == AudioFileBuffer.STATUS_ERROR_QUIT) { // buffer encountered an error and quit thread
						// tell sources mover activity an error occured and quit this audio thread
						GlobalData.playActivityMsgHandler.sendEmptyMessage(PlayActivity.AUDIOTHREAD_AUDIO_FILE_BUFFERING_ERROR_MSG);
						quit();
					} else if (bufferStatus != AudioFileBuffer.STATUS_OK_END_OF_STREAM) { 
						allEndOfStream = false; // not all sources have finished playing yet (only if not looping)
					}
					
					// overlap-save + cross-fade overlap save
					System.arraycopy(input, internalBufSettings.monoAudioOutBlockSizeInBytes, input, 0,
							internalBufSettings.monoFilterOverlapInBytes + internalBufSettings.monoCrossfadeOverlapInBytes);
					
					// buffer next chunk of audio file in background
					soundSources.get(i).getAudioFileBuffer().bufferNextChunk();
				} // end sound sources loop

				// benchmarks
				/**if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
					// to test if cpu-time timer is working as expected 
					sleepBenchmark.startTimer();
					SystemClock.sleep(100);
					sleepBenchmark.stopTimer();
				}**/
				/**if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
					// test benchmarking overhead
					metaZeroBenchmark.startTimer();
					zeroBenchmark.startTimer();
					zeroBenchmark.stopTimer();
					metaZeroBenchmark.stopTimer();
				}**/

				// finish filtered audio block
				FirFilter.endRenderingBlock(doFirCrossfade, outgoingData);
				doFirCrossfade = true; // reset flag

				// quit(), rewind(), play() and pause() can not interfere with this block
				synchronized (transportStateMonitor) {
					// check if we have to do a rewind/play/pause/fade
					if (resumeRequest) {	// resuming from pause -> exclusive audio block
						resumeRequest = false;
						doResume = true;
						doFade = true;
						fadeDirection = 1;
					} else {	// normal operation -> other requests
						if (pauseRequest) {
							pauseRequest = false;
							doPause = true;
							doFade = true;
							fadeDirection = -1;
						}
						if (rewindRequest) {
							rewindRequest = false;
							doFade = true;
							fadeDirection = -1;
							doRewind = true;
						}
						if (quitRequest) {
							quitRequest = false;
							doFade = true;
							fadeDirection = -1;
							doPause = false;
							doRewind = false;
							doQuit = true;
						}
					}
					if (doFade) {
						superpoweredAudioIO.linearFade(outgoingData, internalBufSettings.monoAudioOutBlockSizeInSamples, fadeDirection);
						doFade = false;
					}
					if (doResume) {
						// start audio output again
						superpoweredAudioIO.play(); // TODO fill some buffers with zeros? remove old buffers?
						doResume = false;
					}
					if (allEndOfStream) { // did all audio streams end?
						doRewind = true; // no need to fade here: normal wrap-around, user's audio file
					}
					
					// benchmark: latency tests
					if (GlobalData.DO_OUTPUT_LATENCY_TEST || GlobalData.DO_TOTAL_SYSTEM_LATENCY_TEST) {
						Arrays.fill(outgoingData, (byte)0);
						if (GlobalData.latencyTestTriggered) {
							outgoingData[0] = Byte.MAX_VALUE;
							outgoingData[1] = Byte.MAX_VALUE;
							outgoingData[2] = Byte.MAX_VALUE;
							outgoingData[3] = Byte.MAX_VALUE;
							Log.d(TAG, "Outputting latency test sample");
							GlobalData.latencyTestTriggered = false;
							localLatencyTestRunning = true;
						}
					}
					// benchmark: render whole block
					if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
						renderBlockBenchmark.stopTimer();
						/**writeToTrackBenchmark.startTimer();**/
					}
	
					// blocking audio write (already faded if needed)
					try {
						if (!superpoweredAudioIO.write(outgoingData)) {
							Log.d(TAG, "Error during write()");
							doQuit = true;	// TODO send error message via handler? shutdown io?
						}
					} catch (InterruptedException e) {
						Log.e(TAG, "InterruptedException during write()");
					}
					
					// benchmark: output latency test
					if (GlobalData.DO_OUTPUT_LATENCY_TEST) {
						if (localLatencyTestRunning) {
							latencyData.timeSamples[(int) latencyData.numRuns++] = (float) (System.nanoTime()-GlobalData.olTestAccelTimestamp) / 1000000.0f;
							localLatencyTestRunning = false;
							
							if (latencyData.numRuns == latencyData.maxNumRuns) { // collected everything?
								String result = latencyData.getSamplesCsv();
								RuntimeStatsBenchmark.sendTextPerMail(GlobalData.playActivity, GlobalData.TEST_RESULTS_MAIL_ADDRESSES, "OUTPUT LATENCY TEST", result);
								doQuit = true;
							}
						}
					}
					// benchmark: system latency test
					if (GlobalData.DO_TOTAL_SYSTEM_LATENCY_TEST) {
						if (localLatencyTestRunning) {
							latencyData.timeSamples[(int) latencyData.numRuns++] = (float) (System.nanoTime()-GlobalData.tslTestBluetoothTimestamp) / 1000000.0f;
							localLatencyTestRunning = false;
							
							if (latencyData.numRuns == latencyData.maxNumRuns) { // collected everything?
								String result = latencyData.getSamplesCsv();
								RuntimeStatsBenchmark.sendTextPerMail(GlobalData.playActivity, GlobalData.TEST_RESULTS_MAIL_ADDRESSES, "SYSTEM LATENCY TEST", result);
								doQuit = true;
							}
						}
					}

					// flush if upcoming quit or pause
					if (doQuit || doPause) {
						superpoweredAudioIO.flushAndPause();
					}
					if (doPause && !doQuit) {
						doPause = false;
						paused = true;
						try {
							transportStateMonitor.wait();
						} catch (InterruptedException e) {
							Log.e(TAG, "pause wait() was interrupted, quitting. (" + e.getMessage() + ")");
							doQuit = true;
						}
						paused = false;
						if (quitRequest || doQuit) break; // leave while() loop directly
						if (rewindRequest) {
							rewindRequest = false;
							doRewind = true;
						}
					}
					if (doRewind) {
						doRewind = false;
						// loop over all sound sources
						for (int i = 0; i < numSources; i++) {
							soundSource = soundSources.get(i);
							
							// rewind stream
							try {
								// rewind buffer and buffer first chunk of audio data
								soundSource.getAudioFileBuffer().rewind();
								soundSource.getAudioFileBuffer().bufferNextChunk();
							} catch (IOException e) {
								// tell sources mover activity an error occured and quit this audio thread
								GlobalData.playActivityMsgHandler.sendEmptyMessage(PlayActivity.AUDIOTHREAD_AUDIO_FILE_BUFFERING_ERROR_MSG);
								quit();
							}
							
							// prevent next fir filter crossfade
							doFirCrossfade = false;
						}
					}

					// benchmarks
					if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) {
						// gc benchmark
						/**if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) gcBenchmark.startTimer();
						System.gc();
						if (GlobalData.DO_AUDIO_ENGINE_BENCHMARK) gcBenchmark.stopTimer();**/

						// benchmark: periodically log measurements
						if (renderBlockBenchmark.numRuns % 5000 == 0 && renderBlockBenchmark.numRuns > 0) {
							Log.d(TAG, "##################");
//							wholeLoopBenchmark.logStats();
//							renderSourceBenchmark.logStats();
							renderBlockBenchmark.logStats();
//							zeroBenchmark.logStats();
//							metaZeroBenchmark.logStats();
//							bufSizeBenchmark.logStats();
//							waitFileBenchmark.logStats();
//							writeToTrackBenchmark.logStats();
//							gcBenchmark.logStats();
//							tempBenchmark.logStats();
//							cpuLoadBenchmark.logStats();
//							sleepBenchmark.logStats();
							float missedBlocks = Math.round(
									((float) (renderBlockBenchmark.lastStartTimestamp - renderBlockBenchmark.firstStartTimestamp)
											/ 1e6f / samplesToMillis((float)internalBufSettings.monoAudioOutBlockSizeInSamples,
													GlobalData.DEFAULT_SAMPLERATE)) - (float) (renderBlockBenchmark.numRuns - 1)
									);
							Log.d(TAG, "missed output blocks: " + missedBlocks);
						}
	
						wholeLoopBenchmark.stopTimer();
						/**
						byte[] b1 = new byte[50000];
						try {
							while (!cpuLoadBenchmark.finished) {
								cpuLoadBenchmark.startTimer();
								benchmarkCpuLoad();
								benchmarkCpuLoad();
								cpuLoadBenchmark.stopTimer();
								Thread.sleep(0, 500000);
							}
						} catch (InterruptedException e) {
						}
						cpuLoadBenchmark.logStats();
						String r = cpuLoadBenchmark.getStats();
						r += cpuLoadBenchmark.getSamplesCsv();
						r += "---------------------\n";
						RuntimeStatsBenchmark.sendTextPerMail(GlobalData.playActivity, GlobalData.TEST_RESULTS_MAIL_ADDRESSES, cpuLoadBenchmark.id, r);
						doQuit = true;
						**/
						
						// benchmark: mail results when finished
						if (renderBlockBenchmark.finished) {
							Log.d(TAG, "##################");
//							wholeLoopBenchmark.logStats();
//							renderSourceBenchmark.logStats();
							renderBlockBenchmark.logStats();
							String result = renderBlockBenchmark.getStats();
//							result += wholeLoopBenchmark.getStats();
							result += renderBlockBenchmark.getSamplesCsv();
							result += "---------------------\n";
//							result += wholeLoopBenchmark.getSamplesCsv();
							RuntimeStatsBenchmark.sendTextPerMail(GlobalData.playActivity, GlobalData.TEST_RESULTS_MAIL_ADDRESSES, renderBlockBenchmark.id, result);
							doQuit = true;
						}
					}
				} // synchronized (transportStateMonitor)
			} // end "while (!quit)" loop

			// stop and shutdown audio
			superpoweredAudioIO.pause();
			superpoweredAudioIO.shutdown();
			FirFilter.kissfftDestroy();
		}

	}
}
