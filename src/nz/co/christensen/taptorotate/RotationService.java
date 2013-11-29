package nz.co.christensen.taptorotate;

import com.larvalabs.svgandroid.*;

import android.app.Service;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.hardware.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.*;
import android.provider.Settings.SettingNotFoundException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.*;
import android.view.View.DragShadowBuilder;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.RelativeLayout.LayoutParams;

public class RotationService extends Service implements SensorEventListener {

	public final static String TAG = "RotationService";
	
	public static double THRESHOLD_ROLL = 23.5;
	public static double THRESHOLD_PITCH = 70;
	
	private boolean mDebug = false;
	
	private boolean mEnabled = false;
	
	protected LocalBroadcastManager mDebugBroadcast;
	private RelativeLayout mWindow;
	private WindowManager mWindowManager;
	private SensorManager mSensorManager;
	private LinearLayout mOrientationChanger;
	private FadingImageView mImageView;
	private FadingImageView mImageViewDrag;
	private RelativeLayout mDropzones;
	private int mDeviceRotation = 0;
	private Thread mButtonTimeoutThread = null;
	private boolean mForcedRotation = false;
	
	private Drawable mIconFree;
	private Drawable mIconPortrait;
	private Drawable mIconLandscape;
	
	private boolean attachedToWindow = false;
	
	protected SharedPreferences mPrefs;
	
	private Sensor mSensorAccelerometer;
	private Sensor mSensorGeomagnetic;
	
	private static final WindowManager.LayoutParams mWindowParams =
			new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				0,
				PixelFormat.TRANSLUCENT
			);
	
	private static final WindowManager.LayoutParams mWindowButtonParams =
			new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_PHONE,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT
			);
	
	private static final WindowManager.LayoutParams mOrientationLayout =
			new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_PHONE,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.RGBA_8888
			);
	
	private static final RelativeLayout.LayoutParams mDropzoneParams =
			new RelativeLayout.LayoutParams(
					LayoutParams.MATCH_PARENT,
					LayoutParams.MATCH_PARENT
			);
	
	private void enableForcedRotation() {
		if (!mForcedRotation) {
			mForcedRotation = true;
			
			mWindowManager.addView(mOrientationChanger, mOrientationLayout);
		}
	}
	
	private void disableForcedRotation() {
		if (mForcedRotation) {
			mForcedRotation = false;
			
			mWindowManager.removeView(mOrientationChanger);
		}
	}
	
	@Override
	public void onCreate() {
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		mDebug = mPrefs.getBoolean("enable_debug", false);
		mDebugBroadcast = LocalBroadcastManager.getInstance(this);
		
		Resources r = getResources();
		mIconFree = SVGParser.getSVGFromResource(r, R.raw.icon_free).createPictureDrawable();
		mIconPortrait = SVGParser.getSVGFromResource(r, R.raw.icon_portrait).createPictureDrawable();
		mIconLandscape = SVGParser.getSVGFromResource(r, R.raw.icon_landscape).createPictureDrawable();
		
		mWindow = new RelativeLayout(this) {
			
			
			@Override
			protected void onAttachedToWindow() {
				super.onAttachedToWindow();

				attachedToWindow = true;
				Log.e(TAG, "Attached to window");
				
				final DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(mImageView);
				mImageView.setVisibility(View.GONE);
				mImageViewDrag.startDrag(null, shadowBuilder, mImageViewDrag, 0);
			}
			
			@Override
			protected void onDetachedFromWindow() {
				super.onDetachedFromWindow();
				
				attachedToWindow = false;
				Log.e(TAG, "Detached from window");
			}
		};
		mWindow.setLayoutParams(mWindowParams);
		
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		mDropzones = (RelativeLayout) inflater.inflate(R.layout.dropzones, mWindow);
		
		mOrientationLayout.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
		mOrientationChanger = new LinearLayout(this);
		mOrientationChanger.setClickable(false);
		mOrientationChanger.setFocusable(false);
		mOrientationChanger.setFocusableInTouchMode(false);
		mOrientationChanger.setLongClickable(false);
		
		final int padding = getResources().getDimensionPixelSize(R.dimen.button_padding);
		
		mImageViewDrag = (FadingImageView) mDropzones.findViewById(R.id.button);
		mImageView = new FadingImageView(this);
		mImageViewDrag.setBackgroundResource(R.drawable.button_background);
		mImageView.setBackgroundResource(R.drawable.button_background);
		mImageView.setPadding(padding, padding, padding, padding);
		mImageView.setLongClickable(true);
		
		try {
			if (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 0) {
				int rotation = Settings.System.getInt(getContentResolver(), Settings.System.USER_ROTATION);
				mImageView.setImageDrawable(rotation == 0 || rotation == 2 ? mIconPortrait : mIconLandscape);
				if (mPrefs.getBoolean("force_rotation_fixed", true))
					enableForcedRotation();
			} else {
				mImageView.setImageDrawable(mIconFree);
				if (mPrefs.getBoolean("force_rotation_free", false))
					enableForcedRotation();
			}
		} catch (SettingNotFoundException e) {
			e.printStackTrace();
		}
		
		final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		
		mImageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPrefs.getBoolean("haptic_click", true))
					vibrator.vibrate(25);
				
 				Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
 				
 				if (mPrefs.getBoolean("force_rotation_fixed", true))
					enableForcedRotation();
 				else
 					disableForcedRotation();
 				
				int rotation;
				switch (mDeviceRotation) {
				case 90:
					rotation = 3;
					break;
				case 180:
					rotation = 2;
					break;
				case 270:
					rotation = 1;
					break;
				default:
					rotation = 0;
				}
				Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, rotation);
				
				mImageView.setImageDrawable(rotation == 0 || rotation == 2 ? mIconPortrait : mIconLandscape);
				
				hideRotateButton();
			}
		});
		mImageView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if (mPrefs.getBoolean("haptic_longclick", true))
					vibrator.vibrate(100);
				
				Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
				
				if (mPrefs.getBoolean("force_rotation_free", false))
					enableForcedRotation();
				else
					disableForcedRotation();
				
				mImageView.setImageDrawable(mIconFree);
				
				hideRotateButton();
				
				return true;
			}
		});
		
		mImageView.setOnTouchListener(new View.OnTouchListener() {
			
			private float startX = 0;
			private float startY = 0;
			
			private Thread longClickWaitThread = null;
			private Object longClickWaitLock = new Object();
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					startX = event.getX();
					startY = event.getY();
					synchronized (longClickWaitLock) {
						if (longClickWaitThread != null)
							longClickWaitThread.interrupt();
						
						longClickWaitThread = new Thread(new Runnable() {

							@Override
							public void run() {
								try {
									Thread.sleep(ViewConfiguration.getLongPressTimeout());
								} catch (InterruptedException e) {
									return;
								}
								
								final Thread currentThread = Thread.currentThread();
								
								synchronized (longClickWaitLock) {
									mImageView.post(new Runnable() {

										@Override
										public void run() {
											if (currentThread.isInterrupted())
												return;
											
											mImageView.performLongClick();
										}
										
									});
								}
							}
							
						});
						longClickWaitThread.start();
					}
				} else if (event.getAction() == MotionEvent.ACTION_MOVE &&
						(event.getX() > startX + THRESHOLD_ROLL || event.getX() < startX - THRESHOLD_ROLL
						|| event.getY() > startY + THRESHOLD_ROLL || event.getY() < startY - THRESHOLD_ROLL)) {
					/*if (v.getVisibility() == View.VISIBLE) {
						synchronized (longClickWaitLock) {
							if (longClickWaitThread != null)
								longClickWaitThread.interrupt();
						}
						synchronized (mButtonTimeoutThreadLock) {
							if (mButtonTimeoutThread == null)
								return false;
							
							mButtonTimeoutThread.interrupt();
							mButtonTimeoutThread = null;

							mWindowManager.addView(mWindow, mWindowParams);
						}
						
						
					}*/
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					if (v.getVisibility() == View.VISIBLE) {
						synchronized (longClickWaitLock) {
							if (longClickWaitThread != null)
								longClickWaitThread.interrupt();
							v.performClick();
						}
					}
				}
				return true;
			}
		});
		
		final View.OnDragListener dragListener = new View.OnDragListener() {
			
			@Override
			public boolean onDrag(View v, final DragEvent event) {
				Log.e(TAG, "Dragged");
				
				int resId;
				switch (event.getAction()) {
				case DragEvent.ACTION_DRAG_STARTED:
					break;
				case DragEvent.ACTION_DRAG_ENTERED:
					switch (v.getId()) {
					case R.id.dropzone_left:
						resId = R.drawable.dropzone_left_hover;
						break;
					case R.id.dropzone_right:
						resId = R.drawable.dropzone_right_hover;
						break;
					case R.id.dropzone_top:
						resId = R.drawable.dropzone_top_hover;
						break;
					case R.id.dropzone_bottom:
						resId = R.drawable.dropzone_bottom_hover;
						break;
					default:
						return true;
					}
					v.setBackgroundResource(resId);
					break;
				case DragEvent.ACTION_DRAG_EXITED:
					switch (v.getId()) {
					case R.id.dropzone_left:
						resId = R.drawable.dropzone_left;
						break;
					case R.id.dropzone_right:
						resId = R.drawable.dropzone_right;
						break;
					case R.id.dropzone_top:
						resId = R.drawable.dropzone_top;
						break;
					case R.id.dropzone_bottom:
						resId = R.drawable.dropzone_bottom;
						break;
					default:
						return true;
					}
					v.setBackgroundResource(resId);
					break;
				case DragEvent.ACTION_DROP:
					hideRotateButton();
					break;
				case DragEvent.ACTION_DRAG_ENDED:
					mWindow.post(new Runnable() {
						@Override
						public void run() {
							if (mImageView.getVisibility() != View.VISIBLE) {
								mWindow.removeView(mDropzones);
								mWindow.removeView(mImageView);
								mWindowManager.removeView(mWindow);
								mImageView.setVisibility(View.VISIBLE);
								if (event.getResult())
									showRotateButton(false);
							}
						}
					});
					break;
				default:
					break;
				}
				return true;
			}
		};
		
		mDropzones.findViewById(R.id.dropzone_left).setOnDragListener(dragListener);
		mDropzones.findViewById(R.id.dropzone_right).setOnDragListener(dragListener);
		mDropzones.findViewById(R.id.dropzone_top).setOnDragListener(dragListener);
		mDropzones.findViewById(R.id.dropzone_bottom).setOnDragListener(dragListener);
		
		mWindowButtonParams.gravity = Gravity.CENTER;
		mWindowButtonParams.width = getResources().getDimensionPixelSize(R.dimen.button_size);
		mWindowButtonParams.height = mWindowButtonParams.width;
		
		mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorGeomagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (mEnabled) {
			Log.i(TAG, "Service start requested but service was already running");
			return START_STICKY;
		}
		
		if (intent != null) {
			if (intent.hasExtra("PREFERENCE_KEY") && intent.hasExtra("PREFERENCE_VALUE")) {
				if (intent.getStringExtra("PREFERENCE_KEY").equals("force_rotation_fixed")) {
					if (intent.getBooleanExtra("PREFERENCE_VALUE", true))
						enableForcedRotation();
					else
						disableForcedRotation();
				} else if (intent.getStringExtra("PREFERENCE_KEY").equals("force_rotation_free")) {
					if (intent.getBooleanExtra("PREFERENCE_VALUE", false))
						enableForcedRotation();
					else
						disableForcedRotation();
				} else if (intent.getStringExtra("PREFERENCE_KEY").equals("enable_debug")) {
					mDebug = intent.getBooleanExtra("PREFERENCE_VALUE", false);
				}
			}
		}
		
		enableSensors();
		
		registerReceiver(mIntentReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		registerReceiver(mIntentReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
		
		Log.i(TAG, "Starting service (command received)");
		
		return START_STICKY;
	}
	
	BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				enableSensors();
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				disableSensors();
			}
		}
		
	};
	
	protected void enableSensors() {
		mEnabled = true;
		
		mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mSensorGeomagnetic, SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	protected void disableSensors() {
		mEnabled = false;
		
		mSensorManager.unregisterListener(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public class LocalBinder extends Binder {
		RotationService getService() {
			return RotationService.this;
		}
	}

	private static final int matrix_size = 16;
	float[] valGeomagnetic = new float[matrix_size];
	float[] valAcceleration = new float[matrix_size];
	float[] valR = new float[matrix_size];
	float[] outR = new float[matrix_size];
	float[] I = new float[matrix_size];
	float[] values = new float[3];

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_MAGNETIC_FIELD:
			valGeomagnetic = event.values.clone();
			break;
		case Sensor.TYPE_ACCELEROMETER:
			valAcceleration = event.values.clone();
			break;
		}

		if (valGeomagnetic != null && valAcceleration != null) {
			
			if (!mEnabled)
				Log.e(TAG, "Service still receiving events while screen turned off");
				
			SensorManager.getRotationMatrix(valR, null, valAcceleration, valGeomagnetic);

			// Correct if screen is in Landscape
			SensorManager.remapCoordinateSystem(valR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);

			SensorManager.getOrientation(outR, values);

			double azimuth = (float) Math.round((Math.toDegrees(values[0])) * 2) / 2;
			azimuth = (azimuth + 360) % 360;
			double pitch = Math.toDegrees(values[1]);
			double roll = Math.toDegrees(values[2]);
			
			if (mDebug) {
				Intent debugIntent = new Intent("nz.co.christensen.taptorotate.debug");
				debugIntent.putExtra("AZIMUTH", azimuth);
				debugIntent.putExtra("PITCH", pitch);
				debugIntent.putExtra("ROLL", roll);
				mDebugBroadcast.sendBroadcast(debugIntent);
			}

			final int screenRotation = mWindowManager.getDefaultDisplay().getRotation();

			if (pitch < THRESHOLD_PITCH && pitch > -THRESHOLD_PITCH) {
				if (roll > 0 - THRESHOLD_ROLL && roll < 0 + THRESHOLD_ROLL && mDeviceRotation != 0) {
					mDeviceRotation = 0;
					if (screenRotation != Surface.ROTATION_0)
						showRotateButton();
				} else if (roll > 90 - THRESHOLD_ROLL && roll < 90 + THRESHOLD_ROLL && mDeviceRotation != 90) {
					mDeviceRotation = 90;
					if (screenRotation != Surface.ROTATION_270)
						showRotateButton();
				} else if ((roll > 180 - THRESHOLD_ROLL || roll < -180 + THRESHOLD_ROLL) && mDeviceRotation != 180) {
					mDeviceRotation = 180;
					if (screenRotation != Surface.ROTATION_180)
						showRotateButton();
				} else if (roll > -90 - THRESHOLD_ROLL && roll < -90 + THRESHOLD_ROLL && mDeviceRotation != 270) {
					mDeviceRotation = 270;
					if (screenRotation != Surface.ROTATION_90)
						showRotateButton();
				}
			}

		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	
	private final Object mButtonTimeoutThreadLock = new Object();
	
	private void showRotateButton() {
		showRotateButton(true);
	}
	
	private void showRotateButton(boolean addWindow) {
		if (mImageView.getVisibility() == View.VISIBLE) {
			synchronized (mButtonTimeoutThreadLock) {
				if (mButtonTimeoutThread != null) {
					mButtonTimeoutThread.interrupt();
					mButtonTimeoutThread = null;
				} else if (addWindow) {
					mWindowManager.addView(mImageView, mWindowButtonParams);
				}
			}
			
			mButtonTimeoutThread = new Thread(new Runnable() {
	
				@Override
				public void run() {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						return;
					}
					
					synchronized (mButtonTimeoutThreadLock) {
						if (Thread.interrupted())
							return;
						
						Log.e(TAG, "Removing view from timeout");
						mWindowManager.removeView(mImageView);
						mButtonTimeoutThread = null;
					}
				}
				
			});
			mButtonTimeoutThread.start();
		}
	}
	
	protected void hideRotateButton() {
		synchronized (mButtonTimeoutThreadLock) {
			if (mButtonTimeoutThread != null) {
				mButtonTimeoutThread.interrupt();
				mButtonTimeoutThread = null;
				if (mImageView.getVisibility() == View.VISIBLE)
					mWindowManager.removeView(mImageView);
			}
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		disableSensors();
		
		unregisterReceiver(mIntentReceiver);
		
		hideRotateButton();
		if (mOrientationChanger != null && mForcedRotation)
			mWindowManager.removeView(mOrientationChanger);
		
		Log.i(TAG, "Stopping service (destroyed)");
	}
	

}
