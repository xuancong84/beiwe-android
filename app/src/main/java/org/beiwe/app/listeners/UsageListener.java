package org.beiwe.app.listeners;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.BuildConfig;
import org.beiwe.app.PermissionHandler;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;

import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public class UsageListener {
	public static String header = "timestamp,packageName,className,eventType";
	private static String last_usage_entry_name = "last_usage_timestamp";

	private Context appContext;

	/**Listens for ambient light sensor updates.  NOT activated on instantiation.
	 * Use the turn_on() function to log any ambient light sensor updates to the
	 * ambient light sensor log.
	 * @param applicationContext a Context from an activity or service. */
	public UsageListener(Context applicationContext) {
		this.appContext = applicationContext;
		last_package_name = last_class_name = "";
//		updateUsage();
	}

	public static String eventType2String( int eventType ){
		switch( eventType ){
			case UsageEvents.Event.ACTIVITY_PAUSED: return "ACTIVITY_PAUSED";
			case UsageEvents.Event.ACTIVITY_RESUMED: return "ACTIVITY_RESUMED";
			case UsageEvents.Event.ACTIVITY_STOPPED: return "ACTIVITY_STOPPED";
			case UsageEvents.Event.CONFIGURATION_CHANGE: return "CONFIGURATION_CHANGE";
			case UsageEvents.Event.DEVICE_SHUTDOWN: return "DEVICE_SHUTDOWN";
			case UsageEvents.Event.DEVICE_STARTUP: return "DEVICE_STARTUP";
			case UsageEvents.Event.FOREGROUND_SERVICE_START: return "FOREGROUND_SERVICE_START";
			case UsageEvents.Event.FOREGROUND_SERVICE_STOP: return "FOREGROUND_SERVICE_STOP";
			case UsageEvents.Event.KEYGUARD_HIDDEN: return "KEYGUARD_HIDDEN";
			case UsageEvents.Event.KEYGUARD_SHOWN: return "KEYGUARD_SHOWN";
			case UsageEvents.Event.NONE: return "NONE";
			case UsageEvents.Event.SCREEN_INTERACTIVE: return "SCREEN_INTERACTIVE";
			case UsageEvents.Event.SCREEN_NON_INTERACTIVE: return "SCREEN_NON_INTERACTIVE";
			case UsageEvents.Event.SHORTCUT_INVOCATION: return "SHORTCUT_INVOCATION";
			case UsageEvents.Event.STANDBY_BUCKET_CHANGED: return "STANDBY_BUCKET_CHANGED";
			case UsageEvents.Event.USER_INTERACTION: return "USER_INTERACTION";
			default: return "UNKNOWN";
		}
	}

	private static String last_package_name = "", last_class_name = "";
	public synchronized void updateUsage() {
		long curr_usage_timestamp = System.currentTimeMillis();
		if( PersistentData.pref.contains( last_usage_entry_name ) ) {
			long last_usage_timestamp = PersistentData.pref.getLong(last_usage_entry_name, curr_usage_timestamp);
			try {
				UsageEvents usageEvents = BackgroundService.usageStatsManager.queryEvents(last_usage_timestamp, curr_usage_timestamp);
				UsageEvents.Event usageEvent = new UsageEvents.Event();
				if( usageEvents.hasNextEvent() && PermissionHandler.checkAccessUsagePermission(appContext) ) {
					String data = "";
					while (usageEvents.getNextEvent(usageEvent)) {
						String package_name = TextFileManager.CS2S(usageEvent.getPackageName());
						String class_name = TextFileManager.CS2S(usageEvent.getClassName());
						data += "\n" + usageEvent.getTimeStamp()
								+ TextFileManager.DELIMITER + (package_name.equals(last_package_name)?"":package_name)
								+ TextFileManager.DELIMITER + (class_name.equals(last_class_name)?"":class_name)
								+ TextFileManager.DELIMITER + eventType2String(usageEvent.getEventType());
						last_package_name = package_name;
						last_class_name = class_name;
					}
					TextFileManager.getUsageFile().writeEncrypted(data.substring(1));
					PersistentData.editor.putLong(last_usage_entry_name, curr_usage_timestamp);
					PersistentData.editor.commit();
					if( BuildConfig.APP_IS_DEV )
						Log.i("AUsage", data.substring(1) );
				}
			} catch (Exception e) { }
		} else {	// last_usage is absent, 1st time call
			PersistentData.editor.putLong( last_usage_entry_name, curr_usage_timestamp );
			PersistentData.editor.commit();
		}
	}

}
