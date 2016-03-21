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

import java.io.IOException;
import java.io.InputStream;

/**
 * Infos taken from:
 * https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
 * http://codeguru.earthweb.net/cpp/g-m/multimedia/audio/print.php/c8935
 * http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html
 * http://www.digitizationguidelines.gov/audio-visual/documents/AVPS_Audio_Metadata_Overview_090612.pdf
 */
public class WaveFileInfo {
    private static final String TAG = "WaveFileInfo";
    
    public static final short FORMAT_PCM = 1;
    public static final short FORMAT_ALAW = 6;
    public static final short FORMAT_ULAW = 7;
    
    private short format;
    private short numChannels;
    private int sampleRate;
    private short bitsPerSample;
    private int firstAudioByteIndex;
    private int numAudioBytes;
    
    public WaveFileInfo() {
    }
    
    public WaveFileInfo(InputStream in) throws IOException {
    	readInfos(in);
    }
    
    public short getFormat() {
        return format;
    }
    
    public short getNumChannels() {
        return numChannels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public short getBitsPerSample() {
        return bitsPerSample;
    }

    public int getFirstAudioByteIndex() {
        return firstAudioByteIndex;
    }
    
    public int getNumAudioBytes() {
    	return numAudioBytes;
    }
    
    public void readInfos(InputStream in) throws IOException {
    	String chunkId;
    	int filePos = 0;
    	
        // read "RIFF"
        readString(in, "RIFF");
        // read size
        readInt(in);
        
        // read "WAVE"
        readString(in, "WAVE");

        filePos += 12;
        	
        // find format chunk and read it
        while (true) {
        	filePos += 8;
			chunkId = readString(in, 4);
			
			if (chunkId == null) {
				throw new IOException("could not find format chunk");
			}
			
			if (!chunkId.equals("fmt ")) {
				// this is not fmt chunk, skip it
				int chunkSize = readInt(in);
				in.skip(chunkSize);
				filePos += chunkSize;
				continue;
			}
			
			// fmt chunk found
        	int fmtChunkSize = readInt(in);
        	filePos += fmtChunkSize;

        	// read fmt infos
            format = readShort(in);
            numChannels = readShort(in);
            sampleRate = readInt(in);
            int byteRate = readInt(in);
            short blockAlign = readShort(in);
            bitsPerSample = readShort(in);
            
            // check consistency
            if (byteRate != numChannels * sampleRate * bitsPerSample / 8) {
                throw new IOException("fmt.ByteRate field inconsistent");
            }
            if (blockAlign != numChannels * bitsPerSample / 8) {
                throw new IOException("fmt.BlockAlign field inconsistent");
            }
            
            // skip additional fmt stuff
            in.skip(fmtChunkSize - 16); 
            break;
        }

        // find data chunk and read size
        while (true) {
        	filePos += 8; 
			chunkId = readString(in, 4);
			
			if (chunkId == null) {
				throw new IOException("could not find data chunk");
			}
			
			if (!chunkId.equals("data")) {
				// this is not data chunk, skip it
				int chunkSize = readInt(in);
				in.skip(chunkSize);
				filePos += chunkSize;
				continue;
			}
			
			// data chunk found
        	numAudioBytes = readInt(in);
        	firstAudioByteIndex = filePos;
        	break;
        }
    }

    private static String readString(InputStream in, int length) throws IOException {
    	String str = "";
    	int c;
    	
        for (int i = 0; i < length; i++) {
        	c = in.read();
        	if (c == -1) return null;
        	else str += (char) c;
        }
        
        return str;
    }

    private static void readString(InputStream in, String str) throws IOException {
    	for (int i = 0; i < str.length(); i++) {
    		if (str.charAt(i) != in.read()) throw new IOException(str + " was expected, but not found");
    	}
    }
    
    private static int readInt(InputStream in) throws IOException {
        return in.read() | (in.read() << 8) | (in.read() << 16) | (in.read() << 24);
    }

    private static short readShort(InputStream in) throws IOException {
        return (short)(in.read() | (in.read() << 8));
    }

    @Override
    public String toString() {
        return String.format(
                "WaveHeader format=%d numChannels=%d sampleRate=%d bitsPerSample=%d numBytes=%d",
                format, numChannels, sampleRate, bitsPerSample, numAudioBytes);
    }

}