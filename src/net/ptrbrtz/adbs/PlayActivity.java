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
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import net.ptrbrtz.adbs.AudioScene.TransportState;
import net.ptrbrtz.adbs.android.utils.DialogHelper;
import net.ptrbrtz.adbs.android.utils.MathUtils;

public class PlayActivity extends Activity implements SensorEventListener {
	private static final String TAG = "PlayActivity";
	
	// message ids
	private static final int TIMED_INVALIDATE_MSG = 1;
	public static final int AUDIOTHREAD_FIR_FILTER_ERROR_MSG = 2;
	public static final int AUDIOTHREAD_AUDIO_FILE_BUFFERING_ERROR_MSG = 3;
		
	private ImageButton zoomOutButton;
	private ImageButton zoomInButton;
	private ImageButton settingsButton;
	private ImageButton exitTransportButton;
	private ImageButton exitOptionsButton;
	private ImageButton playButton;
	private ImageButton pauseButton;
	private ImageButton rewindButton;
	private Button optionsButton;
	private Button loadSceneButton;
	private Button loadHrirsButton;
	private Button audioSettingsButton;
	private Button helpButton;
	private TextView volumeTextView;
	private CustomSeekBar volumeSeekBar;
	private RelativeLayout transportLayout;
	private RelativeLayout optionsLayout;
	private RelativeLayout audioSettingsLayout;
	private RelativeLayout helpLayout;
	private TextView helpTextView;
	private LinearLayout buttonsLayout;
	private SourcesView sourcesView;
	private TextView processingSamplerateTextView;
	private TextView processingBlockSizeTextView;
	private TextView ringBufMultiplierTextView;
	private TextView ringBufSizeTextView;
	private TextView crossFadeOverlapSizeTextView;
	private TextView currentHrirLengthTextView;
	private TextView openSLBlockSizeTextView;
	private TextView openSLSamplerateTextView;
	private TextView minFftBlockSizeTextView;
	private TextView actualFftBlockSizeTextView;
	private ExponentialSeekBar processingBlockSizeSeekBar;
	private ExponentialSeekBar ringBufSizeSeekBar;
	private ExponentialSeekBar crossfadeOverlapSizeSeekBar;
	private Button audioSettingsOkButton;
	private Button audioSettingsCancelButton;
	
	private SensorManager sensorManager;
	private boolean draw;
	private boolean useRazor;
	private boolean initOrientation = true;
	private float currentOrientation;
	private AudioScene.AudioBufferSettings audioBufferSettings = new AudioScene.AudioBufferSettings();
	private AudioScene.InternalAudioBufferSettings internalAudioBufferSettings =
			new AudioScene.InternalAudioBufferSettings();

	private AudioSceneLoader audioSceneLoader = new AudioSceneLoader();
	
	private Dialog loadSceneDialog;
	private Dialog loadHrirsDialog;
	private SharedPreferences prefs;
	
	// message handler to receive messages from other threads
    private Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
			switch (msg.what) {
			case TIMED_INVALIDATE_MSG: // draw next frame
				if (draw)
					this.sendEmptyMessageDelayed(TIMED_INVALIDATE_MSG, 40); // 25 fps
				
				// filter orientation and set rotation
				if (useRazor)
					//sourcesView.setCurrentRotation(-phoneOrientationLPFilter.filter(currentOrientation) /*+ initialOrientation*/);
					sourcesView.setCurrentRotation(-currentOrientation /*+ initialOrientation*/);

				// update ui elements
				// have to update volume seekbar?
				// getAndClearVolumeFlag() not working, cause AudioThread is also reading the volume. hmmmm... 
				int newVolume = Math.round(GlobalData.audioScene.getVolume());
				if (volumeSeekBar.getVisualProgress() != newVolume) {
					// set seek bar
					volumeSeekBar.setVisualProgress(newVolume);
					// show current value in text view
					volumeTextView.setText(newVolume + " %");
				}
				
				// have to update transport state?
				if (GlobalData.audioScene.getAndClearTransportStateFlag()) {
					TransportState ts = GlobalData.audioScene.getTransportState(); // cached atomic read
					if (ts == TransportState.PLAYING) {
						pauseButton.setVisibility(View.VISIBLE);
						playButton.setVisibility(View.GONE);
					} else if (ts == TransportState.PAUSED) {
						playButton.setVisibility(View.VISIBLE);
						pauseButton.setVisibility(View.GONE);
					}
				}
				
				// request redraw of sources view
				sourcesView.invalidate();
				break;
			case AUDIOTHREAD_FIR_FILTER_ERROR_MSG: // error in fir filter
				// show dialog
				DialogHelper.showOneButtonDialog(PlayActivity.this, "Error", "Sorry, an error occured while filtering audio. This should not happen. Please report.", "Quit", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						PlayActivity.this.finish();
					}
				});
				break;
			case AUDIOTHREAD_AUDIO_FILE_BUFFERING_ERROR_MSG: // error while buffering audio file
				// show dialog
				DialogHelper.showOneButtonDialog(PlayActivity.this, "Error", "Sorry, an error occured while buffering audio from files.", "Quit", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						PlayActivity.this.finish();
					}
				});
				break;
			default:
				super.handleMessage(msg);
        	}
        }
    };
    
	private class SourcesMoverHrirLoaderTask extends HrirLoaderTask {
		private String hrirPath;
    	private ProgressDialog progressDialog;
    	
    	public SourcesMoverHrirLoaderTask(String hrirPath) {
    		this.hrirPath = hrirPath;
    		this.execute(hrirPath + GlobalData.HRIRS_FILENAME);
    	}
    	
		@Override
		protected void onPreExecute() {
			// shutdown IO
			GlobalData.audioScene.shutdownIO();
			
			// stop drawing scene (does not go well with progress dialog, loading would be super slow.)
			stopDrawing();
			
			// show progress dialog
			progressDialog = DialogHelper.showProgressBarDialog(PlayActivity.this, "Please wait", "Loading HRIRs...", 360);
		}

		@Override
		protected void onPostExecute(float[][][] result) {
			progressDialog.dismiss();
			
			// start drawing scene again
			startDrawing();
			
			if (result == null) {
				// something went wrong
				DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not load HRIRs", "An error occured while loading HRIRs from '" + hrirPath + "'. Please try to load another set", "OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						loadHrirsDialog.show();
					}
				});
			} else {
				GlobalData.hrirs = result;
				
				// HRIRs loaded, save path to preferences
				prefs.edit().putString(GlobalData.PREFS_LAST_USED_HRIRS_PATH, hrirPath).commit();
				
				// restart IO
				try {
					collectAudioBufferSettings();
					GlobalData.audioScene.setupIO(audioBufferSettings);
				} catch (Exception e) {
					GlobalData.audioScene.reset();
					// display dialog
					DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not re-init scene", "An error occured while re-initializing audio scene (" +
							e.getMessage() + "). Please try to load scene again.", "OK");
				}
			}
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			progressDialog.setProgress(values[0]);
		}
	}

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        
		// publish references
		GlobalData.playActivityMsgHandler = msgHandler;
		GlobalData.playActivity = this;
		
		// get device dependent pixel scaling
		DisplayMetrics m = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(m);
		GlobalData.pixelScaling = m.density;
        
        // hide window title and status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // keep screen on, no sleeping please
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // set content
        setContentView(R.layout.play_activity);
        
	    // get preferences
	    prefs = getSharedPreferences(GlobalData.GLOBAL_PREFS_NAME, MODE_PRIVATE);

        // get sensor manager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // get references to views
       	sourcesView = (SourcesView) findViewById(R.id.sources_mover_view);
        zoomOutButton = (ImageButton) findViewById(R.id.zoomout_button);
        zoomInButton = (ImageButton) findViewById(R.id.zoomin_button);
        settingsButton = (ImageButton) findViewById(R.id.settings_button);
        exitTransportButton = (ImageButton) findViewById(R.id.exit_transport_button);
        exitOptionsButton = (ImageButton) findViewById(R.id.exit_options_button);
        playButton = (ImageButton) findViewById(R.id.play_button);
        pauseButton = (ImageButton) findViewById(R.id.pause_button);
        rewindButton = (ImageButton) findViewById(R.id.rewind_button);
        optionsButton = (Button) findViewById(R.id.options_button);
        loadSceneButton = (Button) findViewById(R.id.load_scene_button);
        loadHrirsButton = (Button) findViewById(R.id.load_hrirs_button);
        audioSettingsButton = (Button) findViewById(R.id.audio_settings_button);
        helpButton = (Button) findViewById(R.id.help_button);
        transportLayout = (RelativeLayout) findViewById(R.id.transport_layout);
        helpLayout = (RelativeLayout) findViewById(R.id.help_layout);
        helpTextView = (TextView) findViewById(R.id.help_textview);
        optionsLayout = (RelativeLayout) findViewById(R.id.options_layout);
        audioSettingsLayout = (RelativeLayout) findViewById(R.id.audio_settings_layout);
        buttonsLayout = (LinearLayout) findViewById(R.id.buttons_layout);
        volumeSeekBar = (CustomSeekBar) findViewById(R.id.volume_seekbar);
        volumeTextView = (TextView) findViewById(R.id.volume_textview);

    	processingSamplerateTextView = (TextView) findViewById(R.id.processing_samplerate_tv);
    	processingBlockSizeTextView = (TextView) findViewById(R.id.processing_block_size_tv);
    	ringBufMultiplierTextView = (TextView) findViewById(R.id.ring_buf_multiplier_tv);
    	ringBufSizeTextView = (TextView) findViewById(R.id.ring_buf_size_tv);
    	crossFadeOverlapSizeTextView = (TextView) findViewById(R.id.crossfade_overlap_size_tv);
    	currentHrirLengthTextView = (TextView) findViewById(R.id.hrir_length_tv);
    	openSLBlockSizeTextView = (TextView) findViewById(R.id.opensl_block_size_tv);
    	openSLSamplerateTextView = (TextView) findViewById(R.id.opensl_samplerate_tv);
    	minFftBlockSizeTextView = (TextView) findViewById(R.id.min_fft_block_size_tv);
    	actualFftBlockSizeTextView = (TextView) findViewById(R.id.actual_fft_block_size_tv);
        audioSettingsOkButton = (Button) findViewById(R.id.audio_settings_ok_button);
        audioSettingsCancelButton = (Button) findViewById(R.id.audio_settings_cancel_button);
    	
    	// load audio settings and setup views
    	processingBlockSizeSeekBar = (ExponentialSeekBar) findViewById(R.id.processing_block_size_seekbar);
    	processingBlockSizeSeekBar.setDecimalPlaces(1);
    	processingBlockSizeSeekBar.setExpMin(0.1f);
    	processingBlockSizeSeekBar.setExpMax(20.0f);
    	processingBlockSizeSeekBar.setExpValue(prefs.getFloat(GlobalData.PREFS_LAST_USED_PROCESSING_BLOCK_SIZE, GlobalData.DEFAULT_PROCESSING_BLOCK_SIZE));
    	ringBufSizeSeekBar = (ExponentialSeekBar) findViewById(R.id.ring_buf_size_seekbar);
    	ringBufSizeSeekBar.setDecimalPlaces(0);
    	ringBufSizeSeekBar.setExpMin(1.0f);
    	ringBufSizeSeekBar.setExpMax(10.0f);
    	ringBufSizeSeekBar.setExpValue(prefs.getInt(GlobalData.PREFS_LAST_USED_RING_BUFFER_MULTIPLIER, GlobalData.DEFAULT_RING_BUFFER_MULTIPLIER));
    	crossfadeOverlapSizeSeekBar = (ExponentialSeekBar) findViewById(R.id.crossfade_overlap_size_seekbar);
    	crossfadeOverlapSizeSeekBar.setDecimalPlaces(1);
    	crossfadeOverlapSizeSeekBar.setExpMin(1.0f);
    	crossfadeOverlapSizeSeekBar.setExpMax(25.0f);
    	crossfadeOverlapSizeSeekBar.setExpValue(prefs.getFloat(GlobalData.PREFS_LAST_USED_CROSSFADE_OVERLAP_SIZE, GlobalData.DEFAULT_CROSSFADE_OVERLAP_SIZE));
    	updateAudioSettingsTextViews();
		processingSamplerateTextView.setText(String.valueOf(GlobalData.DEFAULT_SAMPLERATE) + " Hz");
		openSLBlockSizeTextView.setText(String.valueOf(AudioScene.getNativeBufferSize(this)) + " Samples");
		openSLSamplerateTextView.setText(String.valueOf(AudioScene.getNativeSamplerate(this)) + " Hz");

    	// set help text (can't do html in xml)
    	helpTextView.setText(Html.fromHtml(
    			"<br/>" +
				"<h1>HOW TO USE</h1>" +
    			"<b>TAP source</b> to <i>(de)select</i><br/>" +
    			"<b>TAP background</b> to <i>(de)select all</i><br/><br/>" +
    			"<b>DOUBLE-TAP source(s)</b> to <i>mute</i><br/>" +
    			"<b>DRAG source(s)</b> to <i>move</i><br/><br/>" +
    			"<b>PINCH</b> to <i>zoom</i><br/>" +
    			"<b>DOUBLE-TAP background</b> to <i>auto-zoom</i><br/><br/>" +
    			"Use the <b>VOLUME BUTTONS</b> of your device to adjust volume. If you hear distortions, lower the Scene Mix Level in the App.<br/><br/>" +
    			"_____<br/><br/><br/>" +
    			"<h1>ABOUT</h1>" +
    			"(c) 2016 Peter Bartz<br/>" +
    			"Written as a part of my diploma thesis at Technische Universitaet Berlin and Universitaet Rostock.<br/>" +
    			"<br/>" +
    			"With thanks to Sascha Spors, Alexander Raake, Hagen Wierstorf and Matthias Geier.<br/><br/>" +
    			"_____<br/><br/><br/>" +
    			"<h1>LICENSE AND CREDITS</h1>" +
    			"This software is licensed under the MIT License.<br/>" +
    			"https://github.com/ptrbrtz/android-dynamic-binaural-synthesis<br/>" +
    			"<br/>" +
    			"This software uses the KISS FFT library by Mark Borgerding.<br/>" +
    			"https://sourceforge.net/projects/kissfft/<br/>" +
    			"<br/>" +
    			"This app has been Superpowered.<br/>" +
    			"http://superpowered.com/<br/>" +
    			"<br/>" +
    			"License information about the audio material and HRIR data used can be found " +
    			"in the respective sub-directories of the folder <br><b>AndroidDynamicBinauralSynthesis</b><br/> on the SD-card.<br/>" +
    			"<br/><br/>"
    	));
	    
	    sourcesView.setCurrentRotation(0.0f);
	    GlobalData.audioScene.getListener().setAzimuth(0.0f);

        // set ui actions
        zoomOutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sourcesView.zoomView(1.0f/1.3f);
			}
		});
        
        zoomInButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sourcesView.zoomView(1.3f);
			}
		});
        
        settingsButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
    			buttonsLayout.setVisibility(View.INVISIBLE);
        		transportLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        helpLayout.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		helpLayout.setVisibility(View.INVISIBLE);
        		optionsLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        exitTransportButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		transportLayout.setVisibility(View.INVISIBLE);
        		buttonsLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        exitOptionsButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		optionsLayout.setVisibility(View.INVISIBLE);
        		transportLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        playButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		GlobalData.audioScene.play();
        	}
        });
        
        pauseButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		GlobalData.audioScene.pause();
        	}
        });
        
        rewindButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		GlobalData.audioScene.rewind();
        	}
        });
        
        optionsButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		transportLayout.setVisibility(View.INVISIBLE);
        		optionsLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        loadSceneButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		loadSceneDialog.show();
        	}
        });
        
        loadHrirsButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		loadHrirsDialog.show();
        	}
        });
        
        audioSettingsButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		updateAudioSettingsTextViews();	// hrir length could have changed
        		optionsLayout.setVisibility(View.INVISIBLE);
        		audioSettingsLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        helpButton.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		optionsLayout.setVisibility(View.INVISIBLE);
        		helpLayout.setVisibility(View.VISIBLE);
        	}
        });
        
        transportLayout.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		exitTransportButton.performClick();
        	}
        });
        
        optionsLayout.setOnClickListener(new View.OnClickListener() {
        	@Override
        	public void onClick(View v) {
       			exitOptionsButton.performClick();
        	}
        });        
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				// this is a workaround: when reading touch input, 100 is never reached, 99 is max
				if (fromUser && progress == 99) progress = 100;
				
				GlobalData.audioScene.setVolume(progress);
				// set seek bar
				volumeSeekBar.setVisualProgress(progress);
				// show current value in text view
				volumeTextView.setText(progress + " %");
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// nothing
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// nothing
			}
        });
        
        // BUILD DIALOGS
        // scene loader dialog
		//loadSceneDialog = new Dialog(this, android.R.style.Theme_Light);
		loadSceneDialog = new Dialog(this);
		loadSceneDialog.setContentView(R.layout.pick_scene_dialog);
		loadSceneDialog.setTitle("Pick a scene to be loaded...");

		// find views
		final RadioGroup pickSceneRadioGroup = (RadioGroup) loadSceneDialog.findViewById(R.id.pick_scene_radiogroup);
		Button okButton = (Button) loadSceneDialog.findViewById(R.id.load_scene_ok_button);
		Button cancelButton = (Button) loadSceneDialog.findViewById(R.id.load_scene_cancel_button);

		// find available audio scene description files
		File scenesPath = new File(GlobalData.AUDIOSCENES_PATH);
		String[] sceneDescriptionFiles = scenesPath.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				if (filename.toLowerCase().endsWith(GlobalData.AUDIOSCENES_SUFFIX_LOWERCASE) &&	// skip non-scene files
						!filename.startsWith("."))	// skip hidden files too
					return true;
				else return false;
			}
		});
		
		// get last used scene file name from prefs
    	String lastUsedSceneFileName = GlobalData.AUDIOSCENES_PATH + GlobalData.DEFAULT_AUDIOSCENE_NAME;
    	lastUsedSceneFileName = prefs.getString(GlobalData.PREFS_LAST_USED_AUDIOSCENE, lastUsedSceneFileName);
    	
		// add to radiogroup
		boolean firstRadioButton = true;
		for (String s : sceneDescriptionFiles) {
			RadioButton rb = new RadioButton(this);
			rb.setText(" " + s.substring(0, s.length() - 4));
			rb.setTag(GlobalData.AUDIOSCENES_PATH + s);
			rb.setTextColor(0xFFDDDDDD);
			pickSceneRadioGroup.addView(rb);
			if (firstRadioButton) {
				rb.setChecked(true);
				firstRadioButton = false;
			} else if (rb.getTag().equals(lastUsedSceneFileName)) {
	        	// select if this is the scene that was last used
	        	rb.setChecked(true);
	        }
		}
		
		// set listeners
		okButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String fileName;
				int checkedRadioButtonId = pickSceneRadioGroup.getCheckedRadioButtonId();
				
				// any button checked?
				if (checkedRadioButtonId == -1) return;
				
	    		// load scene
				RadioButton rb = (RadioButton) loadSceneDialog.findViewById(checkedRadioButtonId);
				fileName = (String) rb.getTag();
				try {
					loadScene(fileName, GlobalData.audioScene);
				} catch (Exception e) {	// scene will already be reset here
					// display dialog
					DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not load scene", "An error occured while loading audio scene '" + fileName + "' (" +
							e.getMessage() + "). Please check file(s) and try again, or try to load another scene.", "OK");
					return;
				}
				
				// init audio io
				try {
					initAudioIO(GlobalData.audioScene);
				} catch (Exception e) {	// scene will already be reset here
					// display dialog
					DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not init audio I/O", "An error occured while setting up audio I/O '" + fileName + "' (" +
							e.getMessage() + "). Please try different audio settings, or try to restart app.", "OK");
					return;
				}
				
				// scene loaded, save name to preferences
				prefs.edit().putString(GlobalData.PREFS_LAST_USED_AUDIOSCENE, fileName).commit();
				
				// back to standard screen
				loadSceneDialog.dismiss();
				transportLayout.setVisibility(View.INVISIBLE);
				optionsLayout.setVisibility(View.INVISIBLE);
				audioSettingsLayout.setVisibility(View.INVISIBLE);
				buttonsLayout.setVisibility(View.VISIBLE);
				
				sourcesView.transformToFitScene(false);
			}
		});
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				loadSceneDialog.dismiss();
			}
		});

		// HRIRs loader dialog
		loadHrirsDialog = new Dialog(this);
		
		loadHrirsDialog.setContentView(R.layout.pick_hrtfs_dialog);
		loadHrirsDialog.setTitle("Pick a set of HRIRs to be loaded...");
		
		// find views
		final RadioGroup pickHrirsRadioGroup = (RadioGroup) loadHrirsDialog.findViewById(R.id.pick_hrtfs_radiogroup);
		okButton = (Button) loadHrirsDialog.findViewById(R.id.load_hrtfs_ok_button);
		cancelButton = (Button) loadHrirsDialog.findViewById(R.id.load_hrtfs_cancel_button);
		
		// find available hrirs
		File hrirsPath = new File(GlobalData.HRIRS_PATH);
		File[] hrirFolders = hrirsPath.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				if (pathname.isDirectory()) return true;
				else return false;
			}
		});
		
		// get last used hrir name from prefs
		String lastUsedHrirsName = GlobalData.HRIRS_PATH + GlobalData.DEFAULT_HRIRS_NAME;
		lastUsedHrirsName = prefs.getString(GlobalData.PREFS_LAST_USED_HRIRS_PATH, lastUsedHrirsName);
		
		// add to radiogroup
		firstRadioButton = true;
		for (File f : hrirFolders) {
			RadioButton rb = new RadioButton(this);
			rb.setText(" " + f.getName());
			rb.setTag(f.toString() + File.separator);
			rb.setTextColor(0xFFDDDDDD);
			pickHrirsRadioGroup.addView(rb);
			if (firstRadioButton) {
				rb.setChecked(true);
				firstRadioButton = false;
			} else if (rb.getTag().equals(lastUsedHrirsName)) {
				// select if this is the HRIRs set that was last used
				rb.setChecked(true);
			}
		}
		
		// set listeners
		okButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String hrirsName;
				int checkedRadioButtonId = pickHrirsRadioGroup.getCheckedRadioButtonId();
				
				// any button checked?
				if (checkedRadioButtonId == -1) return;
				
				// load HRIRs
				RadioButton rb = (RadioButton) loadHrirsDialog.findViewById(checkedRadioButtonId);
				hrirsName = (String) rb.getTag();
			    new SourcesMoverHrirLoaderTask(hrirsName);
				
				// back to standard screen
				loadHrirsDialog.dismiss();
				transportLayout.setVisibility(View.INVISIBLE);
				optionsLayout.setVisibility(View.INVISIBLE);
				audioSettingsLayout.setVisibility(View.INVISIBLE);
				buttonsLayout.setVisibility(View.VISIBLE);
			}
		});
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				loadHrirsDialog.dismiss();
			}
		});
		
		// audio settings stuff
		processingBlockSizeSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// nothing
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// nothing
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				updateAudioSettingsTextViews();
			}
		});
		ringBufSizeSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// nothing
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// nothing
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				updateAudioSettingsTextViews();
			}
		});
		crossfadeOverlapSizeSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			private static final float MAX_CROSSFADE_SIZE = 25.0f;
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// nothing
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// nothing
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				updateAudioSettingsTextViews();
			}
		});
		audioSettingsOkButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// shutdown audio IO
				GlobalData.audioScene.shutdownIO();

				// restart audio IO
				try {
					collectAudioBufferSettings();
					GlobalData.audioScene.setupIO(audioBufferSettings);
				} catch (Exception e) {
					GlobalData.audioScene.reset();
					// display dialog
					DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not re-init scene", "An error occured while re-initializing audio scene (" +
							e.getMessage() + "). Please try to load scene again.", "OK");
					return;
				}
				
				// save to prefs
				Editor e = prefs.edit();
				e.putFloat(GlobalData.PREFS_LAST_USED_CROSSFADE_OVERLAP_SIZE, audioBufferSettings.crossfadeOverlapSize);
				e.putFloat(GlobalData.PREFS_LAST_USED_PROCESSING_BLOCK_SIZE, audioBufferSettings.processingBlockSize);
				e.putInt(GlobalData.PREFS_LAST_USED_RING_BUFFER_MULTIPLIER, audioBufferSettings.ringBufSizeMultiplier);
				e.commit();
			}
		});
		audioSettingsCancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// reset sliders to actual unchanged values
				processingBlockSizeSeekBar.setExpValue(prefs.getFloat(GlobalData.PREFS_LAST_USED_PROCESSING_BLOCK_SIZE, GlobalData.DEFAULT_PROCESSING_BLOCK_SIZE));
				ringBufSizeSeekBar.setExpValue(prefs.getInt(GlobalData.PREFS_LAST_USED_RING_BUFFER_MULTIPLIER, GlobalData.DEFAULT_RING_BUFFER_MULTIPLIER));
				crossfadeOverlapSizeSeekBar.setExpValue(prefs.getFloat(GlobalData.PREFS_LAST_USED_CROSSFADE_OVERLAP_SIZE, GlobalData.DEFAULT_CROSSFADE_OVERLAP_SIZE));

				// skip previous screens, back to play screen
				audioSettingsLayout.setVisibility(View.INVISIBLE);
        		/**optionsLayout.setVisibility(View.VISIBLE);**/
        		buttonsLayout.setVisibility(View.VISIBLE);
			}
		});
		
        // init volume
        volumeSeekBar.setProgress(50);
        
        // set up stuff for drawing entities
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        SoundSource.setPaint(paint);

        paint = new Paint(paint);
        paint.setAntiAlias(true);
        Listener.setPaint(paint);

        paint = new Paint(paint);
        paint.setAntiAlias(true);
        paint.setARGB(255, 0, 0, 0);
        paint.setStrokeWidth(3);
        AudioScene.setPaint(paint);

        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording(0, 0);
        Listener.paint.setARGB(255, 50, 50, 50);
        Listener.paint.setStrokeWidth(3.0f * GlobalData.pixelScaling);
        canvas.drawLine(0.0f, 12.0f * GlobalData.pixelScaling, 0.0f, -13.0f * GlobalData.pixelScaling, Listener.paint);
        Listener.paint.setStrokeWidth(2.0f * GlobalData.pixelScaling);
        canvas.drawLine(0.0f, -15.0f * GlobalData.pixelScaling, 10.0f * GlobalData.pixelScaling, 1.0f * GlobalData.pixelScaling, Listener.paint);
        canvas.drawLine(0.0f, -15.0f * GlobalData.pixelScaling, -10.0f * GlobalData.pixelScaling, 1.0f * GlobalData.pixelScaling, Listener.paint);
        picture.endRecording();
        Listener.arrowPicture = picture;
        
        // setup Razor AHRS if used
        useRazor = this.getIntent().getBooleanExtra("UseRazor", false);
        if (useRazor) {
        	// nothing (yet)
        }
	}
    
    private void updateAudioSettingsTextViews() {
		processingBlockSizeTextView.setText(String.valueOf(processingBlockSizeSeekBar.getExpValue()) + " ms");
		ringBufMultiplierTextView.setText(String.valueOf((int) ringBufSizeSeekBar.getExpValue()));
		ringBufSizeTextView.setText(String.valueOf(MathUtils.roundTo(ringBufSizeSeekBar.getExpValue()	* 
				AudioScene.samplesToMillis((float) AudioScene.getNativeBufferSize(this), AudioScene.getNativeSamplerate(this)), 1))
				+ " ms");
		crossFadeOverlapSizeTextView.setText(String.valueOf(crossfadeOverlapSizeSeekBar.getExpValue()) + " ms");
		currentHrirLengthTextView.setText(String.valueOf(MathUtils.roundTo((float) AudioScene.samplesToMillis(
				GlobalData.hrirs[0][0].length, GlobalData.DEFAULT_SAMPLERATE), 2))  + " ms");

		// "dry run" to compute fft buffer sizes
		collectAudioBufferSettings();
		GlobalData.audioScene.getInternalAudioBufferSettings(audioBufferSettings, GlobalData.hrirs[0][0].length,
				internalAudioBufferSettings);
		minFftBlockSizeTextView.setText(String.valueOf(internalAudioBufferSettings.minMonoFftBlockSizeInSamples) + " Samples");
		actualFftBlockSizeTextView.setText(String.valueOf(internalAudioBufferSettings.monoFftBlockSizeInSamples) + " Samples");
    }
    
    private void collectAudioBufferSettings() {
    	audioBufferSettings.processingBlockSize = processingBlockSizeSeekBar.getExpValue();
    	audioBufferSettings.ringBufSizeMultiplier = (int) ringBufSizeSeekBar.getExpValue();
    	audioBufferSettings.crossfadeOverlapSize = crossfadeOverlapSizeSeekBar.getExpValue();
    }
    
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	Log.d(TAG, "onActivityResult");
    }
    
    @Override
    protected void onDestroy() {
    	Log.d(TAG, "onDestroy");
    	super.onDestroy();
    	
   		// disconnect from shake
    	if (useRazor) {
	   		GlobalData.razor.asyncDisconnect();
    	}

    	// make sure, we're finishing
    	if (!isFinishing()) {
    		finish();
    	}
    }
    
    @Override
    protected void onPause() {
    	Log.d(TAG, "onPause");
    	super.onPause();
    	
    	// stop periodical drawing
    	stopDrawing();
    	
    	// stop receiving sensor data
    	sensorManager.unregisterListener(this);
   		
		// stop audio IO
		GlobalData.audioScene.shutdownIO();
		
		// finish whole activity, so we go back to connect screen
		finish();
    }
    
    @Override
    protected void onRestart() {
    	super.onRestart();
    	Log.d(TAG, "onRestart");
    }
    
    @Override
    protected void onResume() {
    	Log.d(TAG, "onResume");
    	super.onResume();
    	
		// load last used scene
    	String fileName = GlobalData.AUDIOSCENES_PATH + GlobalData.DEFAULT_AUDIOSCENE_NAME;
    	fileName = prefs.getString(GlobalData.PREFS_LAST_USED_AUDIOSCENE, fileName);
		try {
			loadScene(fileName, GlobalData.audioScene);
		} catch (Exception e) {	// scene will already be reset here
			// display dialog
			loadSceneDialog.show();
			DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not load scene", "An error occured while loading audio scene '" + fileName + "' (" +
					e.getMessage() + "). Please check file(s) and try again, or try to load another scene.", "OK");
		}
		
		// init audio io
		try {
			initAudioIO(GlobalData.audioScene);
		} catch (Exception e) {	// scene will already be reset here
			// display dialog
			DialogHelper.showOneButtonDialog(PlayActivity.this, "Could not init audio I/O", "An error occured while setting up audio I/O '" + fileName + "' (" +
					e.getMessage() + "). Please try different audio settings, or try to restart app.", "OK");
		}
		
		// start periodical repainting every 40ms
		startDrawing();
		
    	// start sensing orientation
   		List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
   		sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_GAME);
   		
   		//Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
   		//sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
   		
		if (GlobalData.DO_OUTPUT_LATENCY_TEST) {
			List<Sensor> accelerometers = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
			sensorManager.registerListener(this, accelerometers.get(0), SensorManager.SENSOR_DELAY_FASTEST);
		}
    }
    
    @Override
    protected void onStart() {
    	super.onStart();
    	Log.d(TAG, "onStart");
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	Log.d(TAG, "onStop");
    }

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		Log.d(TAG, "onRestoreInstanceState");
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.d(TAG, "onSaveInstanceState");
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// catch "back"-button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (transportLayout.getVisibility() == View.VISIBLE) { // go back from transport controls?
				exitTransportButton.performClick();
				return true;
			} else if (optionsLayout.getVisibility() == View.VISIBLE) { // go back from options screen?
				exitOptionsButton.performClick();
				return true;
			} else if (audioSettingsLayout.getVisibility() == View.VISIBLE) { // go back from audio settings?
				audioSettingsLayout.setVisibility(View.INVISIBLE);
				optionsLayout.setVisibility(View.VISIBLE);
				return true;
			} else if (helpLayout.getVisibility() == View.VISIBLE) { // go back from help screen?
				helpLayout.setVisibility(View.INVISIBLE);
				optionsLayout.setVisibility(View.VISIBLE);
				return true;
			}
		}
		
		return super.onKeyDown(keyCode, event);
	}
    
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
			// remember initial orientation
			if (initOrientation) {
				/*initialOrientation = event.values[0];
				phoneOrientationLPFilter.init(initialOrientation);
				*/
				//phoneOrientationLPFilter.init(event.values[0]);
				initOrientation = false;
			}
				
			// set new orientation if it differs from old by certain threshold
			//if (Math.abs(event.values[0] - currentOrientation) >= PHONE_ORIENTATION_CHANGE_THRESHOLD)
				currentOrientation = event.values[0];
		}

		// testing TYPE__ROTATION_VECTOR
		// (TYPE_ORIENTATION is deprecated, but this is much more jumpy on Nexus 5)
		/*if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			float[] orientation = new float[3];
			float[] rMat = new float[9];
			private float mAzimuth = 0; // degrees
			// calculate the rotation matrix
			SensorManager.getRotationMatrixFromVector(rMat, event.values);
			// get the azimuth value (orientation[0]) in degree
			mAzimuth = (float) ((Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0]) + 360.0f) % 360.0f);
			// currentOrientation = mAzimuth;
		}*/
		
		if (GlobalData.DO_OUTPUT_LATENCY_TEST) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				// start test if accelerometer sensed a tap and prevent double triggers within 200ms
				if ((Math.abs(event.values[2]) > 12.0) && (event.timestamp - GlobalData.olTestAccelTimestamp > 2e8)) {
					/*// test timers
					Log.d(TAG, "timestamp                    : " + Math.round(event.timestamp / 1e6));
					Log.d(TAG, "System.nanoTime()            : " + Math.round(System.nanoTime() / 1e6));
					Log.d(TAG, "SystemClock.elapsedRealtime(): " + SystemClock.elapsedRealtime());
					//Log.d(TAG, "SystemClock.elapsedRealtimeNa: " + SystemClock.elapsedRealtimeNanos());
					Log.d(TAG, "SystemClock.uptimeMillis()   : " + SystemClock.uptimeMillis());
					Log.d(TAG, "System.currentTimeMillis()   : " + System.currentTimeMillis());*/
					Log.d(TAG, "Accel event receive delay check: " + Math.round((double)(System.nanoTime() - event.timestamp) / 1e6d) + " ms");
					GlobalData.olTestAccelTimestamp = event.timestamp;
					GlobalData.latencyTestTriggered = true;
				}
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// nothing
	}

	private void startDrawing() {
    	draw = true;
    	msgHandler.sendEmptyMessage(TIMED_INVALIDATE_MSG);
	}
	
	private void stopDrawing() {
		draw = false;
	}
	
	private void loadScene(String fileName, AudioScene audioScene) throws Exception {
		// load scene
		try {
			if (!audioSceneLoader.loadScene(fileName, audioScene)) {
				audioScene.reset();
				throw new Exception("Error while parsing file");
			}
		} catch (FileNotFoundException e) {
			audioScene.reset();
			throw new Exception("File not found");
		} catch (Exception e) {
			audioScene.reset();
			throw new Exception("Unknown error");
		}
	}
	
	private void initAudioIO(AudioScene audioScene) throws Exception {
		// init audio IO
		try {
			collectAudioBufferSettings();
			audioScene.setupIO(audioBufferSettings);
		} catch (Exception e) {
			audioScene.reset();
			throw e;
		}
	}
}