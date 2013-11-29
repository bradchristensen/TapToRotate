package nz.co.christensen.taptorotate;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.*;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;

public class SettingsActivity extends PreferenceActivity {

	protected SharedPreferences mPrefs;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.preferences, menu);
		return true;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		FragmentManager mFragmentManager = getFragmentManager();
		FragmentTransaction mFragmentTransaction = mFragmentManager.beginTransaction();
		PrefsFragment mPrefsFragment = new PrefsFragment();
		mFragmentTransaction.replace(android.R.id.content, mPrefsFragment);
		mFragmentTransaction.commit();
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (mPrefs.getBoolean("enable_service", true))
			startService(new Intent(getApplicationContext(), RotationService.class));
		
	}

	public static class PrefsFragment extends PreferenceFragment {

		private Context mContext = null;
		protected SharedPreferences mPrefs;
		protected PreferenceGroup mDebugViews;
		
		@Override
		public void onAttach(Activity context) {
			super.onAttach(context);
			
			mContext = context;
			mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			final PrefsFragment fragment = this;
			
			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preferences);
			
			mDebugViews = (PreferenceGroup) findPreference("category_debug");
			
			boolean debug = mPrefs.getBoolean("enable_debug", false);
			if (!debug) {
				getPreferenceScreen().removePreference(mDebugViews);
			}
			
			final Preference prefEnableService = findPreference("enable_service");
			prefEnableService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean val = (Boolean) newValue;
					if (val) {
						mContext.startService(new Intent(mContext.getApplicationContext(), RotationService.class));
					} else {
						mContext.stopService(new Intent(mContext.getApplicationContext(), RotationService.class));
					}
					return true;
				}
				
			});
			
			Preference.OnPreferenceChangeListener forceListener = new Preference.OnPreferenceChangeListener() {
				
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean val = (Boolean) newValue;
					if (mPrefs.getBoolean("enable_service", true)) {
						Intent intent = new Intent(mContext.getApplicationContext(), RotationService.class);
						
						mContext.stopService(intent);
						
						intent.putExtra("PREFERENCE_KEY", preference.getKey());
						intent.putExtra("PREFERENCE_VALUE", val);
						
						mContext.startService(intent);
					}
					return true;
				}
			};
			
			final Preference prefForceFixed = findPreference("force_rotation_fixed");
			final Preference prefForceFree = findPreference("force_rotation_free");
			
			prefForceFixed.setOnPreferenceChangeListener(forceListener);
			prefForceFree.setOnPreferenceChangeListener(forceListener);
			
			final Preference prefEnableDebug = findPreference("enable_debug");
			prefEnableDebug.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean val = (Boolean) newValue;
					
					if (val) {
						fragment.getPreferenceScreen().addItemFromInflater(mDebugViews);
						mDebugViews = (PreferenceGroup) fragment.findPreference("category_debug");
					} else {
						fragment.getPreferenceScreen().removePreference(mDebugViews);
					}
					
					Intent intent = new Intent(mContext.getApplicationContext(), RotationService.class);
					
					mContext.stopService(intent);
					
					intent.putExtra("PREFERENCE_KEY", preference.getKey());
					intent.putExtra("PREFERENCE_VALUE", val);
					
					mContext.startService(intent);
					
					return true;
				}
			});
			
			LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(mContext);
			lbm.registerReceiver(new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					if (mPrefs.getBoolean("enable_debug", false)) {
						final Preference debugAzimuth = mDebugViews.findPreference("debug_azimuth");
						final Preference debugPitch = mDebugViews.findPreference("debug_pitch");
						final Preference debugRoll = mDebugViews.findPreference("debug_roll");
						
						debugAzimuth.setSummary("" + intent.getDoubleExtra("AZIMUTH", 0) + "°");
						debugPitch.setSummary("" + intent.getDoubleExtra("PITCH", 0) + "°");
						debugRoll.setSummary("" + intent.getDoubleExtra("ROLL", 0) + "°");
					}
				}
				
			}, new IntentFilter("nz.co.christensen.taptorotate.debug"));
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	

}
