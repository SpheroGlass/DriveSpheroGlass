package com.spheroglass;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.sensor.DeviceSensorsData;
import orbotix.sphero.ConnectionListener;
import orbotix.sphero.DiscoveryListener;
import orbotix.sphero.PersistentOptionFlags;
import orbotix.sphero.SensorControl;
import orbotix.sphero.SensorFlag;
import orbotix.sphero.SensorListener;
import orbotix.sphero.Sphero;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.google.glass.widget.SliderView;

public class SpheroGlass extends Activity {

	protected static final String TAG = "SpheroGlass";

	private Sphero mRobot;
	private SensorListener sensorListener;

	private float speed = 0f;
	private int direction = 0;
	private float turn = 0;
	
	private SensorManager mSensorManager;
	private float mAccel;
	private float mAccelCurrent;
	private float mAccelLast;
	private final SensorEventListener mSensorListener = getSensorListener();

	private Timer loopCommandsTimer;
	boolean disconnecting = false;

	private SliderView mIndeterm;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.menu);

		initState();
	}

	private void initState() {
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccel = 0.00f;
		mAccelCurrent = SensorManager.GRAVITY_EARTH;
		mAccelLast = SensorManager.GRAVITY_EARTH;
		speed = 0f;
		direction = 0;
		turn = 0;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();

		connectToSphero();
		initSensors();
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		disconnectFromSphero();
		stopSensors();
	}

	private void initSensors() {
		mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
	}

	private void stopSensors() {
		mSensorManager.unregisterListener(mSensorListener);
	}

	private void connectToSphero() {
		
		mIndeterm = (SliderView) findViewById(R.id.indeterm_slider);
		mIndeterm.startIndeterminate();
		
		RobotProvider.getDefaultProvider().removeConnectionListeners();
		RobotProvider.getDefaultProvider().addConnectionListener(new ConnectionListener() {
			@Override
			public void onConnected(Robot robot) {
				mIndeterm.stopIndeterminate();
				mRobot = (Sphero) robot;
				SpheroGlass.this.connected();
				updateConnectionStatus(R.string.connection_ok);
			}

			@Override
			public void onConnectionFailed(Robot sphero) {
				mIndeterm.stopIndeterminate();
				if(!disconnecting) {
					Log.d(TAG, "Connection Failed: " + sphero);
					updateConnectionStatus(R.string.connection_failed);
				}
			}

			@Override
			public void onDisconnected(Robot robot) {
				Log.d(TAG, "Disconnected: " + robot);
				updateConnectionStatus(R.string.connection_disconnected);
				mRobot = null;
			}
		});

		RobotProvider.getDefaultProvider().addDiscoveryListener(new DiscoveryListener() {
			@Override
			public void onBluetoothDisabled() {
				Log.d(TAG, "Bluetooth Disabled");
				updateConnectionStatus(R.string.bluetooth_disabled);
			}

			@Override
			public void discoveryComplete(List<Sphero> spheros) {
				Log.d(TAG, "Found " + spheros.size() + " robots");
			}

			@Override
			public void onFound(List<Sphero> sphero) {
				Log.d(TAG, "Found: " + sphero);
				RobotProvider.getDefaultProvider().connect(sphero.iterator().next());
			}
		});

		boolean success = RobotProvider.getDefaultProvider().startDiscovery(this);
		if (!success) {
			Log.d(TAG, "Unable To start Discovery!");
		}
	}

	private void disconnectFromSphero() {
		Log.d(TAG, "Disconnecting (1): " + Thread.currentThread().getName());
		disconnecting = true;
		loopCommandsStop();
		Log.d(TAG, "Disconnecting (2): " + Thread.currentThread().getName());
		
		if (mRobot != null) {
//			final SensorControl control = mRobot.getSensorControl();
//			control.removeSensorListener(sensorListener);

//			BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
			mRobot.disconnect();
		}
//		RobotProvider.getDefaultProvider().removeDiscoveryListeners();
//		RobotProvider.getDefaultProvider().removeAllControls();
//		RobotProvider.getDefaultProvider().shutdown();
	}
	
	private void connected() {
		Log.d(TAG, "Connected On Thread: " + Thread.currentThread().getName());
		Log.d(TAG, "Connected: " + mRobot);

		final SensorControl control = mRobot.getSensorControl();
		sensorListener = new SensorListener() {
			@Override
			public void sensorUpdated(DeviceSensorsData sensorDataArray) {
				Log.d(TAG, sensorDataArray.toString());
			}
		};
		control.addSensorListener(sensorListener, SensorFlag.ACCELEROMETER_NORMALIZED, SensorFlag.GYRO_NORMALIZED);

		control.setRate(1);
		mRobot.enableStabilization(true);
		mRobot.setBackLEDBrightness(.99f);
		mRobot.setColor(100, 0, 0);

		boolean preventSleepInCharger = mRobot.getConfiguration().isPersistentFlagEnabled(PersistentOptionFlags.PreventSleepInCharger);
		Log.d(TAG, "Prevent Sleep in charger = " + preventSleepInCharger);
		Log.d(TAG, "VectorDrive = " + mRobot.getConfiguration().isPersistentFlagEnabled(PersistentOptionFlags.EnableVectorDrive));

		mRobot.getConfiguration().setPersistentFlag(PersistentOptionFlags.PreventSleepInCharger, false);
		mRobot.getConfiguration().setPersistentFlag(PersistentOptionFlags.EnableVectorDrive, true);

		Log.d(TAG, "VectorDrive = " + mRobot.getConfiguration().isPersistentFlagEnabled(PersistentOptionFlags.EnableVectorDrive));
		Log.v(TAG, mRobot.getConfiguration().toString());

		loopCommandsStart();
	}

	private void loopCommandsStart() {
		TimerTask timerTask = new TimerTask() {

			@Override
			public void run() {
				Sphero robot = mRobot;
				if(robot != null && mRobot.isConnected()) {
					if(Math.abs(turn)>1.5) {
						direction += 5 * (turn > 0 ? 1 : -1);
					}
					if(Math.abs(speed)>0.1) {
						if(speed>0) {
							robot.drive(normalizeDirection(direction+180), normalizeSpeed(speed));
						} else {
							robot.drive(normalizeDirection(direction), normalizeSpeed(-speed));
						}
					} else {
						robot.drive(normalizeDirection(direction), 0);
					}
				}
			}

			private int normalizeDirection(int direction) {
				return ((direction%360)+360)%360;
			}
			
			private float normalizeSpeed(float speed) {
				return Math.min(speed * 2, 1);
			}
		};
		loopCommandsTimer = new Timer();
		loopCommandsTimer.schedule(timerTask, 0, 100);
	}
	
	private void loopCommandsStop() {
		if(loopCommandsTimer != null) {
			loopCommandsTimer.cancel();
			loopCommandsTimer = null;
		}
	}

	private SensorEventListener getSensorListener() {
		return new SensorEventListener() {

			public void onSensorChanged(SensorEvent se) {
				float x = se.values[0];
				float y = se.values[1];
				float z = se.values[2];
				mAccelLast = mAccelCurrent;
				mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
				float delta = mAccelCurrent - mAccelLast;
				mAccel = mAccel * 0.9f + delta;
				
				speed = (float) (-z / SensorManager.GRAVITY_EARTH);
				turn = -x;
			}

			public void onAccuracyChanged(Sensor sensor, int accuracy) {
			}
		};
	}
	
	private void updateConnectionStatus(final int stringId) {
		
		this.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				final TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
				connectionStatus.setText(stringId);
				connectionStatus.setVisibility(View.VISIBLE);
				
				View slider = findViewById(R.id.indeterm_slider);
				slider.setVisibility(View.INVISIBLE);
				
				final Handler handler = new Handler();
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						connectionStatus.setVisibility(View.INVISIBLE);
					}
				}, 2000);
				
//				new Timer().schedule(new TimerTask() {
//					@Override
//					public void run() {
//						connectionStatus.setVisibility(View.INVISIBLE);
//					}
//				}, 2000);
			}
		});
		
	}
}
