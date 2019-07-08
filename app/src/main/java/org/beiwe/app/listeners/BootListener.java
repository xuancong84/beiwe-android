package org.beiwe.app.listeners;

import org.beiwe.app.BackgroundService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**The BootListener is never actually instantiated elsewhere in the app.  It's job is to sit
 * and wait for either the boot broadcast or the SD (external) applications available.
 * @author Eli */
public class BootListener extends BroadcastReceiver {
	
	/** Checks whether the app is installed on the SD card; needs a context passed in 
	 *  Grab a pagkageManager (general info) -> get packageInfo (info about this package) ->
	 *  ApplicationInfo (information about this application instance).
	 *  http://stackoverflow.com/questions/5814474/how-can-i-find-out-if-my-app-is-installed-on-sd-card */
//	private Boolean checkForSDCardInstall(Context externalContext) throws NameNotFoundException{
//		PackageManager pkgManager = externalContext.getPackageManager();
//		try {
//			PackageInfo pkgInfo = pkgManager.getPackageInfo(externalContext.getPackageName(), 0);
//			ApplicationInfo appInfo = pkgInfo.applicationInfo;
//			//appInfo.flags is an int; docs say: "Flags associated with the application. Any combination of... [list_of_flags]."  
//			// the following line returns true if the app is installed on an SD card.  
//			return (appInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE; }
//		catch (NameNotFoundException e) {
//			Log.i("PowerStateListener", "something is broken in the check for installation on an SD card.");
//			throw e; }
//	}
	
	/** Does what it says, starts the background service running.
	 *  called when SDcard available and on device startup. */	
	public static void startBackgroundService(Context externalContext){
		Intent intent_to_start_background_service = new Intent(externalContext, BackgroundService.class);
		intent_to_start_background_service.addFlags(Intent.FLAG_FROM_BACKGROUND);
		externalContext.startService(intent_to_start_background_service);
	}
	
	@Override
	public void onReceive(Context externalContext, Intent intent) {
		// Start main service if not started by the accessibility service
		if( BackgroundService.localHandle == null )
			startBackgroundService(externalContext);
	}
}
