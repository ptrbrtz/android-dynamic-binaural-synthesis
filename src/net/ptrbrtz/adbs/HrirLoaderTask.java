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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.os.AsyncTask;

public class HrirLoaderTask extends AsyncTask<String, Integer, float[][][]> {
	private static final String TAG = "HrirLoaderTask";
	
//	private void readFileAsByteArray(String fileName, byte[] array) throws IOException {
//		FileInputStream fin = new FileInputStream(fileName);
//		if (fin.getChannel().size() != array.length) {
//			throw new IOException("Unexpected file size");
//		}
//		DataInputStream din = new DataInputStream(fin);
//		din.readFully(array);
//	}
	
//	private short[] packHrtfCoeffs(ByteBuffer leftBuffer, ByteBuffer rightBuffer) {
//		// rewind buffer positions
//		leftBuffer.rewind();
//		rightBuffer.rewind();
//		
//		// convert to 16 bit fixedpoint interleaved array
//		short[] returnArray = new short[leftBuffer.capacity() / 2];	// div 4 mul 2 = div 2
//		for (int i = 0; i < returnArray.length; i += 2) {
//			returnArray[i] = (short) (leftBuffer.getFloat() * (1 << 14));
//			returnArray[i+1] = (short) (rightBuffer.getFloat() * (1 << 14));
//		}
//		
//		return returnArray;
//	}
	
//	private short[] flipChannels(short[] interleavedArray) {
//		short[] returnArray = new short[interleavedArray.length];
//		for (int i = 0; i < returnArray.length; i += 2) {
//			returnArray[i] = interleavedArray[i+1];
//			returnArray[i+1] = interleavedArray[i];
//		}
//		
//		return returnArray;
//	}
	
	private float convertToLittleEndianFloat(int intBits) {
		intBits = (0x000000ff & (intBits >> 24)) | (0x0000ff00 & (intBits >> 8)) | (0x00ff0000 & (intBits << 8))
				| (0xff000000 & (intBits << 24));
		return Float.intBitsToFloat(intBits);
	}
	
	private float[][][] readHrtfsBin(String hrirFileName) throws FileNotFoundException, IOException {
		// get filter length and create arrays/buffers
		File f = new File(hrirFileName);
		int numHrirCoeffs = (int) (f.length() / 4 / 720); // div 4 for float, div 720 for one pair of HRIRs per degree
		float[][][] hrirs = new float[2][360][numHrirCoeffs]; // dimensions: left/right, angle, coefficient
		ByteBuffer hrirPairByteBuf = ByteBuffer.allocate(numHrirCoeffs * 4 * 2);
		hrirPairByteBuf.order(ByteOrder.LITTLE_ENDIAN); // the reason we're doing this: fast reading of little-endian floats

		FileInputStream fin = new FileInputStream(hrirFileName);
		DataInputStream din = new DataInputStream(fin);

		// read and reorder HRIR coefficients
		for (int a = 0; a < 360; a++) {
			hrirPairByteBuf.rewind();
			din.readFully(hrirPairByteBuf.array());
			for (int i = 0; i < numHrirCoeffs; i++) {
				hrirs[0][a][i] = hrirPairByteBuf.getFloat();
			}
			for (int i = 0; i < numHrirCoeffs; i++) {
				hrirs[1][a][i] = hrirPairByteBuf.getFloat();
			}
			publishProgress(a);
		}
		
		din.close();
		return hrirs;
	}
	
//	private Vector<short[]> readHrtfsBin(String fileNamePrefixLeft, String fileNamePrefixRight, String fileNamePostfix) throws FileNotFoundException, IOException {
//		Vector<short[]> hrirs = new Vector<short[]>(360);
//		hrirs.setSize(360);
//		
//		// get filter length and create temp arrays/buffers
//		File f = new File(fileNamePrefixLeft + "0" + fileNamePostfix);
//		int fileSize = (int) f.length();
//		byte[] leftArray = new byte[fileSize];
//		byte[] rightArray = new byte[fileSize];
//		ByteBuffer leftBuffer = ByteBuffer.wrap(leftArray);
//		ByteBuffer rightBuffer = ByteBuffer.wrap(rightArray);
//		leftBuffer.order(ByteOrder.LITTLE_ENDIAN);
//		rightBuffer.order(ByteOrder.LITTLE_ENDIAN);
//		short[] hrtf;
//		
//		// read HRTFs (assuming they are symmetrical, for faster loading process)
//		for (int i = -180; i <= 0; i++) {
//			// for (int i = -180; i < 180; i++) {
//			readFileAsByteArray(fileNamePrefixLeft + i + fileNamePostfix, leftArray);
//			readFileAsByteArray(fileNamePrefixRight + i + fileNamePostfix, rightArray);
//			hrtf = packHrtfCoeffs(leftBuffer, rightBuffer);
//			hrirs.set(i + 180, hrtf);
//			if (i != -180) {
//				hrirs.set(360 - (i + 180), flipChannels(hrtf));
//			}
//			publishProgress((i + 181) * 2);
//		}
//		
//		return hrirs;
//	}

	@Override
	protected float[][][] doInBackground(String... params) {
		try {
			return readHrtfsBin(params[0]);
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException e) {
			return null;
		} catch (Exception e) {
			return null;
		}
	}
}
