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
import java.util.Set;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import de.tuberlin.qu.razorahrs.RazorAHRS;
import de.tuberlin.qu.razorahrs.RazorListener;
import net.ptrbrtz.adbs.android.utils.DialogHelper;

public class HeadtrackerConnectActivity extends Activity {
	protected static final String TAG = "HeadtrackerConnectActivity";
	
	private static final int REQUEST_ENABLE_BT_ID = 1;

	private SharedPreferences prefs;
	private BluetoothAdapter bluetoothAdapter;
	
	private RadioGroup razorListRadioGroup;
	private Button connectButton;
	private Button withoutRazorButton;
	private Button resetPrefsButton;
	private Button enableBluetoothButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		
		// check if low latency audio is available
		if (!getPackageManager().hasSystemFeature("android.hardware.audio.low_latency")) {
		    Log.d(TAG, "Device does not support low latency audio");
		    Toast.makeText(this, "Attention: this device does not support low latency audio. The App might not work properly.", Toast.LENGTH_LONG).show();
		}

		// save application context
		GlobalData.applicationContext = getApplicationContext();

		// set content view
		setContentView(R.layout.connect_activity);
		
	    // get preferences
	    prefs = getSharedPreferences(GlobalData.GLOBAL_PREFS_NAME, MODE_PRIVATE);

		// get component references
		connectButton = (Button) findViewById(R.id.connect_button);
		withoutRazorButton = (Button) findViewById(R.id.withoutrazor_button);
		resetPrefsButton = (Button) findViewById(R.id.reset_prefs_button);
		razorListRadioGroup = (RadioGroup) findViewById(R.id.razor_radiogroup);
		enableBluetoothButton = (Button) findViewById(R.id.enable_bt_button);
		
				
		// get bluetooth adapter
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {	// ooops
			// show dialog
			DialogHelper.showOneButtonDialog(HeadtrackerConnectActivity.this, "Could not initialize Bluetooth", "Please check if Bluetooth is set up properly on your device and run app again.", "Quit", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// quit
					HeadtrackerConnectActivity.this.finish();
				}
			});
			return;
		}
		
		enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT_ID);
			}
		});
		
	    // connect button click handler
	    connectButton.setOnClickListener(new View.OnClickListener() {
	    	ProgressDialog progressDialog;
	    	
			public void onClick(View view) {
				disableOkButton();

				// show progress dialog
				progressDialog = DialogHelper.showProgressDialogWithButton(HeadtrackerConnectActivity.this,
						"Please wait", "Connecting...", DialogInterface.BUTTON_NEGATIVE, "Cancel",
						new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						GlobalData.razor.asyncDisconnect();
						enableOkButton();
					};
				});

	    		// get selected bluetooth device
	    		RadioButton rb = (RadioButton) findViewById(razorListRadioGroup.getCheckedRadioButtonId());
	    		BluetoothDevice razorDevice = (BluetoothDevice) rb.getTag();

	    		// Create new razor instance and set listener
	    		GlobalData.razor = new RazorAHRS(razorDevice, new RazorListener() {
	    			@Override
	    			public void onConnectAttempt(int attempt, int maxAttempts) {
	    				// Toast.makeText(HeadtrackerConnectActivity.this, "Please wait, connecting...\n\nAttempt " + attempt + " of " + maxAttempts + "...", Toast.LENGTH_SHORT).show();
	    			}
	    			
	    			@Override
	    			public void onConnectOk() {
	    				// start mover activity
	    	    		Intent intent = new Intent(HeadtrackerConnectActivity.this, PlayActivity.class);
	    	    		intent.putExtra("UseRazor", true);
	    				startActivity(intent);
	    				
	    				enableOkButton();
	    				progressDialog.dismiss();
	    			}
	    			
	    			public void onConnectFail(Exception e) {
	    				enableOkButton();
	    				progressDialog.dismiss();
	    				DialogHelper.showOneButtonDialog(HeadtrackerConnectActivity.this, "Could not connect", "Please make sure your Razor AHRS is turned on.", "OK");
	    			}

					@Override
					public void onAnglesUpdate(float yaw, float pitch, float roll/**, long timestamp*/) {
						// NOTE: timestamp is only used in a specially modified version of the library used
						// for latency testing. The modified sources can be found in the RazorAHRSLatencyTestVersion
						// folder.
						/**if (GlobalData.DO_TOTAL_SYSTEM_LATENCY_TEST) {
							// start test if we received a yaw != 0, but prevent double triggers within 200ms
							if ((yaw != 0.0f) && (timestamp - GlobalData.tslTestBluetoothTimestamp > 2e8)) {
								Log.d(TAG, "Bluetooth event receive delay check: " + Math.round((double)(System.nanoTime() - timestamp) / 1e6d) + " ms");
								GlobalData.tslTestBluetoothTimestamp = timestamp;
								GlobalData.latencyTestTriggered = true;
							}
							yaw = 0.0f;
						}*/
						GlobalData.audioScene.getListener().setAzimuth(yaw);
					}

					@Override
					public void onIOExceptionAndDisconnect(IOException e) {
						// TODO we should run something like this in PlayActivity:
						// show dialog and go back to connect screen
						/*DialogHelper.showOneButtonDialog(this, "Razor AHRS error", "An error occured in Bluetooth communication with Razor AHRS. Please try to connect again.", "OK", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								PlayActivity.this.finish();
							}
						});*/
						Log.d("RazorListener", "onIOExceptionAndDisconnect: " + e.getMessage());
					}

					@Override
					public void onSensorsUpdate(float accX, float accY, float accZ, float magX, float magY, float magZ,
							float gyrX, float gyrY, float gyrZ/**, long timestamp*/) {
						// nothing						
					}
	    		});
	    		
	    		// Connect asynchronously
	    		GlobalData.razor.asyncConnect(5);	// 5 connect attempts

	    		// save shake device name to preferences
	    		prefs.edit().putString(GlobalData.PREFS_LAST_USED_RAZOR_DEVICE_NAME, razorDevice.getName()).commit();
	    	}
	    });
	    
	    // without shake button click handler
	    resetPrefsButton.setOnClickListener(new View.OnClickListener() {
	    	public void onClick(View view) {
	    		prefs.edit().clear().commit();
	    		Toast.makeText(getApplicationContext(), "All settings reset.", Toast.LENGTH_LONG).show();
	    		HeadtrackerConnectActivity.this.recreate();
	    	}
	    });
	    
	    // without shake button click handler
	    withoutRazorButton.setOnClickListener(new View.OnClickListener() {
	    	public void onClick(View view) {
	    		// start mover activity
	    		Intent intent = new Intent(HeadtrackerConnectActivity.this, PlayActivity.class);
	    		intent.putExtra("UseRazor", false);
	    		startActivity(intent);
	    	}
	    });
	    
	    // load last used HRIRs
	    String hrirsPath = prefs.getString(GlobalData.PREFS_LAST_USED_HRIRS_PATH, GlobalData.HRIRS_PATH + GlobalData.DEFAULT_HRIRS_NAME);
	    new ConnectorHrirLoaderTask(hrirsPath);
	}
	
	private class ConnectorHrirLoaderTask extends HrirLoaderTask {
		private String hrirPath;
    	private ProgressDialog progressDialog;
    	
    	public ConnectorHrirLoaderTask(String hrirPath) {
    		this.hrirPath = hrirPath;
    		this.execute(hrirPath + GlobalData.HRIRS_FILENAME);
    	}
    	
		@Override
		protected void onPreExecute() {
			progressDialog = DialogHelper.showProgressBarDialog(HeadtrackerConnectActivity.this, "Please wait", "Loading HRIRs...", 360);
		}

		@Override
		protected void onPostExecute(float[][][] result) {
			GlobalData.hrirs = result;
			progressDialog.dismiss();
			
			if (result == null) {
				// something went wrong
				DialogHelper.showTwoButtonDialog(HeadtrackerConnectActivity.this, "Could not load HRIRs", "An error occured while loading HRIRs from '" + hrirPath + "'. Please check HRIR files and run app again, or try to load default HRIRs.", "Quit", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// quit
						HeadtrackerConnectActivity.this.finish();
					}
				}, "Load Default HRIRs", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// load default HRIRs
						new ConnectorHrirLoaderTask(GlobalData.HRIRS_PATH + GlobalData.DEFAULT_HRIRS_NAME);
					}
				});
			} else {
				// HRIRs loaded, save path to preferences
				prefs.edit().putString(GlobalData.PREFS_LAST_USED_HRIRS_PATH, hrirPath).commit();
			}
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			progressDialog.setProgress(values[0]);
		}
    }

	private void enableOkButton() {
		// enable ok button
		connectButton.setEnabled(true);
		connectButton.setText("Connect");
	}

	private void disableOkButton() {
		// disable ok button and set text
		connectButton.setEnabled(false);
		connectButton.setText("Connecting...");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT_ID) {
			Log.d(TAG, "GOT RESULT CODE: REQUEST_ENABLE_BT_ID");
			// restart this activity
			startActivity(new Intent(HeadtrackerConnectActivity.this, HeadtrackerConnectActivity.class));
			finish();
		} else {
			super.onActivityResult(requestCode, resultCode, data);
			Log.d(TAG, "onActivityResult, result code: " + resultCode);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
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
		
		if (bluetoothAdapter.isEnabled()) {
			// get list of paired bluetooth devices
			Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
			
			// add to radio group
			razorListRadioGroup.removeAllViews();
			String lastUsedRazorDeviceName = prefs.getString(GlobalData.PREFS_LAST_USED_RAZOR_DEVICE_NAME, "----");
			RadioButton firstRadioButton = null;
		    for (BluetoothDevice device : pairedDevices) {
		        RadioButton rb = new RadioButton(this);
		        rb.setText(" " + device.getName());
		        rb.setTag(device);
		        razorListRadioGroup.addView(rb);
		        if (firstRadioButton == null) {
		        	firstRadioButton = rb;
		        	firstRadioButton.setChecked(true);
		        	enableOkButton();
		        } else if (device.getName().equals(lastUsedRazorDeviceName)) {
		        	// select if this is the device that was used on last connect
		        	rb.setChecked(true);
		        }
		    }
		    
		    // check if any paired devices found
		    if (firstRadioButton == null) {
		    	TextView tv = new TextView(this);
		    	tv.setText("No paired Bluetooth devices found.");
		    	razorListRadioGroup.addView(tv);
		    }
		    
		    razorListRadioGroup.setVisibility(View.VISIBLE);
		    enableBluetoothButton.setVisibility(View.GONE);
		} else {
		    razorListRadioGroup.setVisibility(View.GONE);
		    enableBluetoothButton.setVisibility(View.VISIBLE);
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
}
