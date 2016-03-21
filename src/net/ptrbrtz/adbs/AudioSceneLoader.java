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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;

public class AudioSceneLoader {
	private static final String TAG = "AudioSceneLoader";

	public boolean loadScene(String fileName, AudioScene outputAudioScene) throws FileNotFoundException {
		// open input stream
		InputStream inputStream = new FileInputStream(fileName);
		
		try {
			// create sax parser
			SAXParserFactory spf = SAXParserFactory.newInstance(); 
			SAXParser sp = spf.newSAXParser(); 

			// get an xml reader 
			XMLReader xr = sp.getXMLReader();

			// set up xml input source
			InputSource inputSource = new InputSource(inputStream);

			// create handler for scene description and assign it
			SceneDescrXMLHandler sceneDescrXMLHandler = new SceneDescrXMLHandler(outputAudioScene); 
			xr.setContentHandler(sceneDescrXMLHandler);

			// basic scene setup
			outputAudioScene.reset();
			if (outputAudioScene.getListener() == null) // make sure we have a listener
				outputAudioScene.setListener(new Listener());
			
			// save scene description file name and path
			File file = new File(fileName);
			outputAudioScene.setSceneDescriptionFilePath(file.getParent() + File.separator);
			outputAudioScene.setSceneDescriptionFileName(file.getName()); 
			
			// parse scene description
			xr.parse(inputSource);
			
		} catch (Exception e) {
			Log.d(TAG, "Exception " + e.toString() + ": " + e.getMessage());
			outputAudioScene.reset();
			return false;
		}
		
		return true;
	}
	
	private class SceneXMLHandler extends DefaultHandler {
		protected static final String NAME = "name";
		protected static final String X = "x";
		protected static final String Y = "y";
		protected static final String POSITION = "position";
		protected static final String SOURCE = "source";
		protected static final String REFERENCE = "reference";
		protected static final String SCENE_SETUP = "scene_setup";
		protected static final String MUTE = "mute";
		protected static final String VOLUME = "volume";
		protected static final String LEVEL = "level";
		protected static final String AZIMUTH = "azimuth";
		protected static final String ORIENTATION = "orientation";
		protected static final String FILE = "file";
		protected static final String TRUE = "true";
		protected static final String FALSE = "false";
		protected static final String SAMPLE_RATE = "sample_rate";
		protected static final String BITS_PER_SAMPLE = "bits_per_sample";
		
		protected AudioScene audioScene;
		
		protected SoundSource soundSource;
		protected boolean inSourceTag;
		protected boolean inReferenceTag;
		protected boolean inVolumeTag;
		protected boolean inSampleRateTag;
		protected boolean inBitsPerSampleTag;
		protected boolean inFileTag;
		
		public SceneXMLHandler(AudioScene audioScene) {
			this.audioScene = audioScene;
		}
		
		protected void setSoundSourceAttributes(SoundSource soundSource, Attributes attributes) {
			// get attributes
			String sName = attributes.getValue(NAME);
			String sMuted = attributes.getValue(MUTE);
			String sVolume = attributes.getValue(VOLUME);
			String sLevel = attributes.getValue(LEVEL);
			
			// set attributes in soundSource
			if (sName != null) soundSource.setName(sName);
			if (sMuted != null) {
				if (sMuted.equals(TRUE)) {
					soundSource.setMuted(true);
				} else {
					soundSource.setMuted(false);
				}
			}
			try {
				soundSource.setVolume(Float.parseFloat(sVolume));
			} catch (Exception e) {} // string -> float conversion exception
			if (sLevel != null) {
				try {
					soundSource.setLevel(Float.parseFloat(sLevel));
				} catch (Exception e) {} // string -> float conversion exception
			}
		}
		
		protected float parseFloat(String s, float defaultValue) {
			float f = defaultValue;
			try {
				f = Float.parseFloat(s);
			} catch (Exception e) {} // string -> float conversion exception
			return f;
		}
		
		protected int parseInt(String s, int defaultValue) {
			int i = defaultValue;
			try {
				i = Integer.parseInt(s);
			} catch (Exception e) {} // string -> float conversion exception
			return i;
		}
		
		protected void setEntityPosition(Entity entity, Attributes attributes) {
			// get attributes
			String pX = attributes.getValue(X);
			String pY = attributes.getValue(Y);
			
			// set position of entity
			try {
				float x, y;
				x = Float.parseFloat(pX);
				y = -Float.parseFloat(pY); // convert to "right-handed" screen coordinates
				entity.setXY(x, y);
			} catch (Exception e) {} // string -> float conversion exception
		}
		
		protected void setEntityOrientation(Entity entity, Attributes attributes) {
			// get attributes
			String azimuth = attributes.getValue(AZIMUTH);
			
			// set orientation of entity
			try {
				entity.setAzimuth(Float.parseFloat(azimuth));
			} catch (Exception e) {} // string -> float conversion exception
		}
	}
	
	private class SceneDescrXMLHandler extends SceneXMLHandler {
		private boolean parsingSceneSetup;

		public SceneDescrXMLHandler(AudioScene audioScene) {
			super(audioScene);
		}

		@Override
		public void startDocument() throws SAXException {
			parsingSceneSetup = false;
			inSourceTag = false;
			inReferenceTag = false;
			inFileTag = false;
			soundSource = null;
		}
		
		@Override
		public void endDocument() throws SAXException {
			// nothing here
		}
		
		@Override
		public void startElement(String uri, String localName, String name,
				Attributes attributes) throws SAXException {
			if (!parsingSceneSetup) { 
				if (localName.equals(SCENE_SETUP)) { 
					parsingSceneSetup = true;
					Log.d(TAG, "creating new audio scene");
				}
				return;
			} else {
				if (inSourceTag) {
					if (localName.equals(POSITION)) {
						setEntityPosition(soundSource, attributes);
						return;
					} else if (localName.equals(ORIENTATION)) {
						setEntityOrientation(soundSource, attributes);
						return;
					} else if (localName.equals(FILE)) {
						inFileTag = true;
						return;
					}
				} else if (inReferenceTag) {
					// listener not allowed to have specific position and orientation
				} else if (localName.equals(SOURCE)) { 
					inSourceTag = true;
					soundSource = new SoundSource();
					setSoundSourceAttributes(soundSource, attributes);
					return;
				} else if (localName.equals(REFERENCE)) {
					inReferenceTag = true;
					// any direct reference attribs?
					audioScene.setListener(new Listener());
					return;
				} else if (localName.equals(BITS_PER_SAMPLE)) {
					inBitsPerSampleTag = true;
					return;
				} else if (localName.equals(SAMPLE_RATE)) {
					inSampleRateTag = true;
					return;
				} else if (localName.equals(VOLUME)) {
					inVolumeTag = true;
					return;
				}
			}
			
			Log.d(TAG, "start unhandled element: '" + localName + "'");
		}
		
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (parsingSceneSetup) {
				if (inVolumeTag) {
					audioScene.setVolume(parseFloat(String.valueOf(ch, start, length), audioScene.getVolume()));
				} else if (inSampleRateTag) {
					audioScene.setSampleRate(parseInt(String.valueOf(ch, start, length), audioScene.getSampleRate()));
				} else if (inBitsPerSampleTag) {
					audioScene.setBitsPerSample(parseInt(String.valueOf(ch, start, length), audioScene.getBitsPerSample()));
				} else if (inFileTag) {
					soundSource.setAudioFileName(audioScene.getSceneDescriptionFilePath() + String.valueOf(ch, start, length));
				}
			}
		}

		@Override
		public void endElement(String uri, String localName, String name)
		throws SAXException {
			if (!parsingSceneSetup) return;
			
			if (localName.equals(SOURCE)) {
				audioScene.addSoundSource(soundSource);
				soundSource = null;
				inSourceTag = false;
				return;
			} else if (localName.equals(REFERENCE)) {
				inReferenceTag = false;
				return;
			} else if (localName.equals(SAMPLE_RATE)) {
				inSampleRateTag = false;
				return;
			} else if (localName.equals(BITS_PER_SAMPLE)) {
				inBitsPerSampleTag = false;
				return;
			} else if (localName.equals(VOLUME)) {
				inVolumeTag = false;
				return;
			} else if (localName.equals(FILE)) {
				inFileTag = false;
				return;
			}
		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			Log.d(TAG, "error");
			throw e;
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			Log.d(TAG, "fatal error");
			throw e;
		}
		
		@Override
		public void warning(SAXParseException e) throws SAXException {
			Log.d(TAG, "warning");
		}
	}
}