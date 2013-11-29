package nz.co.christensen.taptorotate;

import android.content.*;
import android.preference.PreferenceManager;

public class BootCompletedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean("enable_service", true) && prefs.getBoolean("start_on_boot", true))
			context.startService(new Intent(context.getApplicationContext(), RotationService.class));
	}

}
