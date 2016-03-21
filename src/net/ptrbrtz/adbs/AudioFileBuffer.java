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
import java.io.IOException;

import android.util.Log;

/**
 * Class that reads and buffers data from an audio file.
 * Buffering takes place in background using extra thread.
 *  
 * @author Peter Bartz
 */
public class AudioFileBuffer extends Thread {
	private static final String TAG = "AudioFileBuffer";
	
	public static final int STATUS_OK_RUNNING = 0;
	public static final int STATUS_OK_END_OF_STREAM = 1;
	public static final int STATUS_OK_QUIT = 2;
	public static final int STATUS_ERROR_QUIT = 3;
	
	private byte[] buffer;
	private int bufferOffset;
	private FileInputStream inStream;
	private int streamStart;
	private int totalNumBytesRead;
	private boolean bufferReady = false;
	private boolean loop;
	
	private boolean quit = false;
	private int status;
	
	/**
	 * Initializes this audio file buffer and starts buffering first chunk of data
	 * @param buffer
	 * @param bufferOffset
	 * @param inStream
	 * @param streamLength
	 * @throws IOException 
	 */
	public AudioFileBuffer(byte[] buffer, int bufferOffset, FileInputStream inStream, int streamStart) throws IOException {
		this.buffer = buffer;
		this.bufferOffset = bufferOffset;
		this.loop = false;
		setStream(inStream, streamStart);
		clearBuffer(0);
		inStream.getChannel().position(streamStart);
		this.start();
	}
	
	/**
	 * Resets input stream, does not start buffering automatically
	 * @param inStream
	 * @param streamLength
	 */
	public synchronized void setStream(FileInputStream inStream, int streamStart) {
		this.inStream = inStream;
		this.streamStart = streamStart;
		totalNumBytesRead = 0;
		status = STATUS_OK_RUNNING;
	}
	
	public synchronized void rewind() throws IOException {
		totalNumBytesRead = 0;
		status = STATUS_OK_RUNNING;
		clearBuffer(0);
		inStream.getChannel().position(streamStart);
	}
	
	public void setLoop(boolean loop) {
		this.loop = loop;
	}
	
	public boolean getLoop() {
		return loop;
	}
	
	/**
	 * Wakes up underlying thread to load next chunk in background
	 */
	public synchronized void bufferNextChunk() {
		bufferReady = false;
		this.notify();
	}
	
	/**
	 * Returns one of the STATUS_ constants
	 */
	public synchronized int getStatus() {
		return status;
	}
	
	public void clearBuffer(int offset) {
		for (int i = offset; i < buffer.length; i++) {
			buffer[i] = 0;
		}
	}

	/**
	 * Stops underlying thread responsible for filling buffer in background
	 */
	public synchronized void dispose() {
		quit = true;
		this.notify();
	}
	
	/**
	 * Should be used instead of holding reference to buffer, as it will
	 * block if buffer not fully filled again
	 * 
	 * @return Buffer
	 */
	public synchronized byte[] getBuffer() {
		while (!bufferReady) {
			try {
				this.wait();
			} catch (InterruptedException e) {
				Log.e(TAG, "InterruptedException");
				status = STATUS_ERROR_QUIT;
				break;

			}
		}
		return buffer;
	}
	
	@Override
	public synchronized void run() {
		int read, numRead;
		int bytesToRead;
		
		status = STATUS_OK_RUNNING;
		
		while (true) {
			// stop thread?
			if (quit) break;
			
			try {
				// buffer one chunk of data
				if (status == STATUS_OK_END_OF_STREAM) {
					clearBuffer(bufferOffset);
				} else {
					// determine how many bytes to read
					bytesToRead = buffer.length - bufferOffset;
					
					// read data from stream
					numRead = 0;
					while (numRead < bytesToRead) {
						read = inStream.read(buffer, bufferOffset + numRead, bytesToRead - numRead);
						
						// end of stream reached? loop?
						if (read == -1) {
							if (!loop) {
								status = STATUS_OK_END_OF_STREAM;
								
								// fill remaining buffer with zeros
								clearBuffer(bufferOffset + numRead);
								break;
							} else { // loop
								inStream.getChannel().position(streamStart);
							}
						} else  {
							numRead += read;
						}
					}
					totalNumBytesRead += numRead;
				}

				// wait until someone asks us to buffer more data
				bufferReady = true;
				this.notify();	// wake up possibly waiting consumer thread
				this.wait();
			} catch (InterruptedException e) {
				Log.e(TAG, "InterruptedException");
				status = STATUS_ERROR_QUIT;
				break;
			} catch (IOException e) {
				Log.e(TAG, "IOException");
				status = STATUS_ERROR_QUIT;
				break;
			}
		}
		
		status = STATUS_OK_QUIT;
	}

	@Override
	protected void finalize() throws Throwable {
		dispose();
		super.finalize();
	}
}
