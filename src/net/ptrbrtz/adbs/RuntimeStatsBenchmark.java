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

import java.text.NumberFormat;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.util.Log;
import net.ptrbrtz.adbs.android.utils.CPUInfo;

public class RuntimeStatsBenchmark {
	public String logTag, id;
	public double minTime, maxTime, avgTime, sumSquaredTimes;
	public double minTimeNonSleep, maxTimeNonSleep, avgTimeNonSleep, sumSquaredTimesNonSleep;
	public long numRuns, preRuns;
	public long tStart, tStartNonSleep;
	public double tElapsed, tElapsedNonSleep;
	public long skipFirstRuns, maxNumRuns;
	public boolean collectTimeSamples, collectCpuStateSamples;
	public float[] timeSamples, timeSamplesNonSleep;
	public byte[] cpuActiveCoreSamples;
	public int[] cpuFreqSamples, cpuMaxFreqSamples;
	public boolean finished;
	public NumberFormat nf;
	BenchmarkCallback startTimerCallback = null, stopTimerCallback = null;
	long firstStartTimestamp, lastStartTimestamp;
	boolean firstStartTimestampSet;

	interface BenchmarkCallback {
		abstract boolean callback(RuntimeStatsBenchmark b);
	}
	
	RuntimeStatsBenchmark(String logTag, String id) {
		this(logTag, id, 0, 0);
	}
	
	RuntimeStatsBenchmark(String logTag, String id, long skipFirstRuns, long maxNumRuns) {
		this(logTag, id, skipFirstRuns, maxNumRuns, false);
	}

	// collect* only works with maxNumRuns > 0
	RuntimeStatsBenchmark(String logTag, String id, long skipFirstRuns, long maxNumRuns, boolean collectTimeSamples) {
		this(logTag, id, skipFirstRuns, maxNumRuns, false, false);
	}
	
	// collect* only works with maxNumRuns > 0
	RuntimeStatsBenchmark(String logTag, String id, long skipFirstRuns, long maxNumRuns, boolean collectTimeSamples,
			boolean collectCpuStateSamples) {
		this.nf = NumberFormat.getInstance(Locale.GERMANY);
		this.logTag = logTag;
		this.id = id;
		this.skipFirstRuns = skipFirstRuns;
		this.maxNumRuns = maxNumRuns;
		this.collectTimeSamples = (collectTimeSamples && maxNumRuns > 0);
		if (collectTimeSamples) {
			timeSamples = new float[(int) maxNumRuns];
			timeSamplesNonSleep = new float[(int) maxNumRuns];
		}
		this.collectCpuStateSamples = (collectCpuStateSamples && maxNumRuns > 0);
		if (collectCpuStateSamples) {
			cpuActiveCoreSamples = new byte[(int) maxNumRuns];
			cpuFreqSamples = new int[(int) maxNumRuns];
			cpuMaxFreqSamples = new int[(int) maxNumRuns];
		}
		reset();
	}
	
	public void reset() {
		minTime = Double.MAX_VALUE;
		maxTime = 0;
		avgTime = 0;
		sumSquaredTimes = 0;
		minTimeNonSleep = Double.MAX_VALUE;
		maxTimeNonSleep = 0;
		avgTimeNonSleep = 0;
		sumSquaredTimesNonSleep = 0;
		numRuns = 0;
		preRuns = skipFirstRuns;
		finished = false;
		firstStartTimestampSet = false;
	}
	
	public void setStartTimerCallback(BenchmarkCallback c) {
		this.startTimerCallback = c;
	}
	
	public void setStopTimerCallback(BenchmarkCallback c) {
		this.stopTimerCallback = c;
	}
	
	public void startTimer() {
		// call start callback and skip this measurement if it says so
		if (startTimerCallback != null)
			if(!startTimerCallback.callback(this) && preRuns <= 0)
				preRuns = 1;
		
		tStart = System.nanoTime();
		tStartNonSleep = Debug.threadCpuTimeNanos();		
	}
	
	public void stopTimer() {
		// stop watches first
		tElapsed = System.nanoTime() - tStart;
		tElapsedNonSleep = Debug.threadCpuTimeNanos() - tStartNonSleep;
		
		// return if still in pre-run phase
		if (preRuns > 0) {
			preRuns--;
			return;
		}
		
		// return if already collected all data
		if (maxNumRuns > 0 && numRuns >= maxNumRuns) {
			finished = true;
			return;
		}

		// call additional callback and skip this measurement if it says so
		if (stopTimerCallback != null)
			if (!stopTimerCallback.callback(this))
				return;
		
		// collect time samples (in ms)
		if (collectTimeSamples) {
			timeSamples[(int) numRuns] = (float) tElapsed / 1e6f;
			timeSamplesNonSleep[(int) numRuns] = (float) tElapsedNonSleep / 1e6f;
		}
		
		// collect cpu state samples
		if (collectCpuStateSamples) {
			int cpuNum = NativeCPUInfo.getCpuId();
			cpuActiveCoreSamples[(int) numRuns] = (byte) cpuNum;
			cpuFreqSamples[(int) numRuns] = CPUInfo.getCurrentFreq(cpuNum, true);
			// TODO change hardcoded 4 to actual number of present cores
			cpuMaxFreqSamples[(int) numRuns] = CPUInfo.getCurrentMaxFreqAny(4, true);
		}
		
		// update min/max values
		if (tElapsed > maxTime) maxTime = tElapsed;
		if (tElapsed < minTime) minTime = tElapsed;
		if (tElapsedNonSleep > maxTimeNonSleep) maxTimeNonSleep = tElapsedNonSleep;
		if (tElapsedNonSleep < minTimeNonSleep) minTimeNonSleep = tElapsedNonSleep;
		
		// update timestamps
		if (!firstStartTimestampSet) {
			firstStartTimestamp = tStart;
			firstStartTimestampSet = true;
		}
		lastStartTimestamp = tStart;

		// update average times and stddev helper variables
		// (using numerically stable online-algorithm according to Knuth)
		double delta;
		numRuns++;
		delta = tElapsed - avgTime;
		avgTime += delta / (double) numRuns;
		sumSquaredTimes += delta * (tElapsed - avgTime);
		delta = tElapsedNonSleep - avgTimeNonSleep;
		avgTimeNonSleep += delta / (double) numRuns;
		sumSquaredTimesNonSleep += delta * (tElapsedNonSleep - avgTimeNonSleep);
	}
	
	public String getStats() {
		String result = "";
		if (numRuns == 0) {
			result += "----------\n";
			result += id + " no data yet\n";
		} else {
			result += "----------\n";
			result += id + " measured " + nf.format(numRuns) + " runs (skip first runs: " + skipFirstRuns + ")\n";
			result += id + " longest run (ms) : " + nf.format(maxTime / 1e6) + " (" + nf.format(maxTimeNonSleep / 1e6) + " non-sleep)\n";
			result += id + " shortest run (ms): " + nf.format(minTime / 1e6) + " (" + nf.format(minTimeNonSleep / 1e6) + " non-sleep)\n";
			result += id + " avg run (ms)     : " + nf.format(avgTime / 1e6) + " (" + nf.format(avgTimeNonSleep / 1e6) + " non-sleep)\n";
			result += id + " stddev (ms)      : " + nf.format(Math.sqrt(sumSquaredTimes / (double) numRuns) / 1e6)
				+ " (" + nf.format(Math.sqrt(sumSquaredTimesNonSleep / (double) numRuns) / 1e6) + " non-sleep)\n";
			result += "\n";
		}
		return result;
	}
	
	// returns samples as csv formatted string (tab-separated)
	public String getSamplesCsv() {
		String result = "";
		
		if (maxNumRuns <= 0 || (!collectTimeSamples && !collectCpuStateSamples))
			return "NO SAMPLES TAKEN";
		
		// column names
		result += "Sample #\tReal-time (ms)\tCPU-time (ms)\tCPU Core #\tCPU Freq\tCPU Max Freq\n";
		
		// fill columns
		for (int i = 0; i < maxNumRuns; i++) {
			result += nf.format(i + 1);
			result += "\t";
			if (timeSamples != null) result += nf.format(timeSamples[i]);
			result += "\t";
			if (timeSamplesNonSleep != null) result += nf.format(timeSamplesNonSleep[i]);
			result += "\t";
			if (cpuActiveCoreSamples != null) result += nf.format(cpuActiveCoreSamples[i]);
			result += "\t";
			if (cpuFreqSamples != null) result += nf.format(cpuFreqSamples[i]);
			result += "\t";
			if (cpuMaxFreqSamples != null) result += nf.format(cpuMaxFreqSamples[i]);
			result += "\n";
		}
			
		return result;
	}

	public void logStats() {
//			if (numRuns == 0) {
//				Log.d(logTag, "----------");
//				Log.d(logTag, id + " no data yet");
//			} else {
//				Log.d(logTag, "----------");
//				Log.d(logTag, id + " measured " + numRuns + " runs (skip first runs: " + skipFirstRuns + ")");
//				Log.d(logTag, id + " longest run (ms) : " + maxTime / 1e6 + " (" + maxTimeNonSleep / 1e6 + " non-sleep)");
//				Log.d(logTag, id + " shortest run (ms): " + minTime / 1e6 + " (" + minTimeNonSleep / 1e6 + " non-sleep)");
//				Log.d(logTag, id + " avg run (ms)     : " + (avgTime / 1e6) + " (" + (avgTimeNonSleep / 1e6) + " non-sleep)");
//				Log.d(logTag, id + " stddev (ms)      : " + Math.sqrt(sumSquaredTimes / (double) numRuns / 1e6)
//					+ " (" + Math.sqrt(sumSquaredTimesNonSleep / (double) numRuns / 1e6) + " non-sleep)");
//			}
			Log.d(logTag, getStats());
	}
	
	public static void sendTextPerMail(Context sendingContext, String[] mailAddresses, String mailSubject, String mailContent) {
		// send results per mail
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, mailAddresses);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, mailSubject);
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, mailContent);
		sendingContext.startActivity(Intent.createChooser(emailIntent, "Send mail..."));
	}
}