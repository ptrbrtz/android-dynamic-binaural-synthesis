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

package net.ptrbrtz.adbs.android.utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CPUInfo {
	private static Map<String, RandomAccessFile> openFiles = new HashMap<String, RandomAccessFile>(16);
	
	public static String getInfo() {
		try {
			Scanner s = new Scanner(new File("/proc/cpuinfo"));
			String result = s.useDelimiter("\\A").next();
			s.close();
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	private static int getValueFromFile(String fileName) {
		try {
			Scanner s = new Scanner(new File(fileName));
			int result = s.nextInt();
			s.close();
			return result;
		} catch (Exception e) {
			return -1;
		}
	}
	
	public static int getValueFromFileRepeatedly(String fileName) {
		try {
			RandomAccessFile raf = openFiles.get(fileName);
			if (raf == null) {
				raf = new RandomAccessFile(fileName, "r");
				openFiles.put(fileName, raf);
			}
			
			raf.seek(0);
			return Integer.valueOf(raf.readLine());
		} catch (Exception e) {
			return -1;
		}
	}
	
	public static int getCurrentFreq(int cpuNum, boolean repeatedly) {
		if (repeatedly)
			return getValueFromFileRepeatedly("/sys/devices/system/cpu/cpu" + cpuNum + "/cpufreq/scaling_cur_freq");
		else
			return getValueFromFile("/sys/devices/system/cpu/cpu" + cpuNum + "/cpufreq/scaling_cur_freq");
	}

	// only returns number of last used cpu of process main thread, not calling thread 
//	public static int getLastUsedCPUNum() {
//		try {
//			Scanner s = new Scanner(new File("/proc/self/stat"));
//			for (int i = 0; i < 38; i++) s.next();
//			int result = s.nextInt();
//			s.close();
//			return result;
//		} catch (Exception e) {
//			return -1;
//		}
//	}

	public static int getCurrentMaxFreq(int cpuNum, boolean repeatedly) {
		if (repeatedly)
			return getValueFromFileRepeatedly("/sys/devices/system/cpu/cpu" + cpuNum + "/cpufreq/scaling_max_freq");
		else
			return getValueFromFile("/sys/devices/system/cpu/cpu" + cpuNum + "/cpufreq/scaling_max_freq");
	}

	// workaround: sometimes scaling_max_freq is not readable for certain cores. empirically all cores
	// are limited to the same freq. so any will do.
	public static int getCurrentMaxFreqAny(int numCoresToTest, boolean repeatedly) {
		int freq;
		for (int i = 0; i < numCoresToTest; i++) {
			freq = getCurrentMaxFreq(i, repeatedly);
			if (freq != -1) return freq;
		}
		return -1;
	}
	
	public static int getMaxFreq(int cpuNum, boolean repeatedly) {
		if (repeatedly)
			return getValueFromFileRepeatedly("/sys/devices/system/cpu/cpu" + cpuNum + "/cpufreq/cpuinfo_max_freq");
		else
			return getValueFromFile("/sys/devices/system/cpu/cpu" + cpuNum + "/cpufreq/cpuinfo_max_freq");
	}
}
