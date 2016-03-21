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

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import de.tuberlin.qu.razorahrs.RazorAHRS;
import net.ptrbrtz.adbs.android.utils.ObjectPool;
import net.ptrbrtz.adbs.android.utils.ObjectPool.ObjectFactory;

public class GlobalData {
	// preferences stuff
	public static final String GLOBAL_PREFS_NAME = "prefs";
	public static final String PREFS_LAST_USED_HRIRS_PATH = "lastUsedHrirsPath";
	public static final String PREFS_LAST_USED_RAZOR_DEVICE_NAME = "lastUsedRazorDeviceName";
	public static final String PREFS_LAST_USED_AUDIOSCENE = "lastUsedAudioScene";
	public static final String PREFS_LAST_USED_CROSSFADE_OVERLAP_SIZE = "lastUsedOverlapCrossfadeSize";
	public static final String PREFS_LAST_USED_RING_BUFFER_MULTIPLIER = "lastUsedRingBufferMultiplier";
	public static final String PREFS_LAST_USED_PROCESSING_BLOCK_SIZE = "lastUsedProcessingBlockSize";

	public static final String HRIRS_PATH = Environment.getExternalStorageDirectory().getPath() + "/AndroidDynamicBinauralSynthesis/HRIRs/";
	public static final String HRIRS_FILENAME = "HRIRs.dat";
	public static final String DEFAULT_HRIRS_NAME = "Anechoic (KEMAR 128) [default]/";

	public static final String AUDIOSCENES_PATH = Environment.getExternalStorageDirectory().getPath() + "/AndroidDynamicBinauralSynthesis/Scenes/";
	public static final String AUDIOSCENES_SUFFIX_LOWERCASE = ".asd";
	public static final String DEFAULT_AUDIOSCENE_NAME = "Radarfilm - Housigedark.asd";
	
	public static final float DEFAULT_CROSSFADE_OVERLAP_SIZE = 5.0f; // crossfading of filtered signal (in ms)
	public static final int DEFAULT_RING_BUFFER_MULTIPLIER = 5;
	public static final float DEFAULT_PROCESSING_BLOCK_SIZE = 5.0f; // (in ms)
	public static final int DEFAULT_SAMPLERATE = 44100;	// fixed at the moment

	// audio engine benchmark
	public static final boolean DO_AUDIO_ENGINE_BENCHMARK = false;

	// output and system latency tests
	public static final boolean DO_OUTPUT_LATENCY_TEST = false;
	public static final boolean DO_TOTAL_SYSTEM_LATENCY_TEST = false;
	public static boolean latencyTestTriggered = false;
	public static long olTestAccelTimestamp = 0;
	public static long tslTestBluetoothTimestamp = 0;
	
	// test and benchmark results destination mail address 
	public static final String[] TEST_RESULTS_MAIL_ADDRESSES = new String[]{"peter-bartz@gmx.de"};

	// shared data
	public static final AudioScene audioScene = new AudioScene(); // this instance of AudioScene is always reused
	public static Handler playActivityMsgHandler = null;
	public static Activity playActivity = null;
	public static RazorAHRS razor = null;
	public static Context applicationContext = null;

	public static float[][][] hrirs = null;	// dimensions: left/right channels, angles, coefficients

	public static ObjectPool<float[]> pointPool = new ObjectPool<float[]>(new ObjectFactory<float[]>() {
		@Override
		public float[] newObject() {
			return new float[2];
		}
	});

	public static float pixelScaling; // device dependent
}
