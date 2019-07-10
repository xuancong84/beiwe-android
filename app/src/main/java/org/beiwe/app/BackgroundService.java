package org.beiwe.app;

import java.util.Calendar;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.beiwe.app.listeners.*;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.networking.SurveyDownloader;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.survey.SurveyScheduler;
import org.beiwe.app.ui.user.LoginActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.dsn.InvalidDsnException;

public class BackgroundService extends Service {
	private Context appContext;
	public AccelerometerListener accelerometerListener;
	public AmbientLightListener ambientLightListener;
	public BluetoothListener bluetoothListener;
	public GPSListener gpsListener;
	public GyroscopeListener gyroscopeListener;
	public PowerStateListener powerStateListener;
	public WifiListener wifiListener;
	public UsageListener usageListener;
	public SmsSentLogger smsSentLogger;
	public MMSSentLogger mmsSentLogger;
	public CallLogger callLogger;
	public TapsListener tapsListener;
//	public AccessibilityListener accessibilityListener;

	public static Timer timer;
	
	//localHandle is how static functions access the currently instantiated background service.
	//It is to be used ONLY to register new surveys with the running background service, because
	//that code needs to be able to update the IntentFilters associated with timerReceiver.
	//This is Really Hacky and terrible style, but it is okay because the scheduling code can only ever
	//begin to run with an already fully instantiated background service.
	public static BackgroundService localHandle;
	public static boolean isTapAdded = false, finalSetupDone = false;
	public static Activity activity = null;
	public static ActivityManager activityManager = null;
	public static UsageStatsManager usageStatsManager = null;
	public static ApplicationInfo appInfo = null;
	public static AppOpsManager opsManager = null;
	public static AccessibilityManager accessibilityManager = null;

	@Override
	/** onCreate is essentially the constructor for the service, initialize variables here. */
	public void onCreate() {
		appContext = this.getApplicationContext();
		activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
		accessibilityManager = (AccessibilityManager)getSystemService(Context.ACCESSIBILITY_SERVICE);
		try {
			appInfo = getPackageManager().getApplicationInfo(getPackageName(), 0);
		} catch (Exception e) {
			appInfo = null;
		}
		opsManager = (AppOpsManager) appContext.getSystemService( Context.APP_OPS_SERVICE );

		try {
			String sentryDsn = BuildConfig.SENTRY_DSN;
			Sentry.init(sentryDsn, new AndroidSentryClientFactory(appContext));
		} catch (InvalidDsnException ie) {
			Sentry.init(new AndroidSentryClientFactory(appContext));
		}

		Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(appContext));
		PersistentData.initialize( appContext );
		TextFileManager.initialize( appContext );
		PostRequest.initialize( appContext );
		localHandle = this;  //yes yes, hacky, I know.
		registerTimers(appContext);

		doSetup();
	}

	public String getForegroundAppName() {
		String topPackageName = null;
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
			ActivityManager.RunningTaskInfo foregroundTaskInfo = activityManager.getRunningTasks(1).get(0);
			topPackageName = foregroundTaskInfo.topActivity.getPackageName();
		} else {
			long time = System.currentTimeMillis();
			List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000*1000, time);
			if (stats != null) {
				SortedMap<Long, UsageStats> runningTask = new TreeMap<Long,UsageStats>();
				for (UsageStats usageStats : stats) {
					runningTask.put(usageStats.getLastTimeUsed(), usageStats);
				}
				topPackageName = runningTask.isEmpty()?"None":runningTask.get(runningTask.lastKey()).getPackageName();
			}
		}
		return topPackageName;
	}

	public void doSetup() {		// now support updating sensor settings
		TextFileManager.write_buffer_size = PersistentData.getInteger(PersistentData.WRITE_BUFFER_SIZE, 0);

		if ( PersistentData.getEnabled(PersistentData.POWER_STATE) && powerStateListener==null )
			powerStateListener = newPowerStateListener();
		if ( PersistentData.getEnabled(PersistentData.GPS) && gpsListener==null )
			gpsListener = new GPSListener(appContext); // Permissions are checked in the broadcast receiver
		if ( PersistentData.getEnabled(PersistentData.WIFI) && wifiListener==null )
			wifiListener = WifiListener.initialize( appContext );
		if ( PersistentData.getEnabled(PersistentData.ACCELEROMETER) && accelerometerListener==null )
			accelerometerListener = new AccelerometerListener( appContext );
		if ( PersistentData.getEnabled(PersistentData.AMBIENTLIGHT) && ambientLightListener==null )
			ambientLightListener = new AmbientLightListener( appContext );
		if ( PersistentData.getEnabled(PersistentData.GYROSCOPE) && gyroscopeListener==null )
			gyroscopeListener = new GyroscopeListener( appContext );
		if ( PersistentData.getEnabled(PersistentData.TAPS) && !isTapAdded )
			tapsListener = new TapsListener( this );
		if ( PersistentData.getEnabled(PersistentData.USAGE) && usageListener==null )
			usageListener = new UsageListener( this );

		//Bluetooth, wifi, gps, calls, and texts need permissions
		if( PersistentData.getEnabled(PersistentData.BLUETOOTH) && bluetoothListener==null ) {
			if ( PermissionHandler.confirmBluetooth(appContext) )
				startBluetooth();
		}

		if( PersistentData.getEnabled(PersistentData.TEXTS) ){
			if (!PermissionHandler.confirmTexts(appContext))
				sendBroadcast(Timer.checkForSMSEnabled);
			else{
				if(smsSentLogger==null)
					smsSentLogger = startSmsSentLogger();
				if(mmsSentLogger==null)
					mmsSentLogger = startMmsSentLogger();
			}
		}

		if( PersistentData.getEnabled(PersistentData.CALLS) && callLogger==null ) {
			if (PermissionHandler.confirmCalls(appContext))
				callLogger = startCallLogger();
			else
				sendBroadcast(Timer.checkForCallsEnabled);
		}

		//Only do the following if the device is registered
		if ( PersistentData.isRegistered() ) {
			DeviceInfo.initialize( appContext ); //if at registration this has already been initialized. (we don't care.)			
			startTimers();
		}
	}
	
	/** Stops the BackgroundService instance. */
	public void stop() { if (BuildConfig.APP_IS_BETA) { this.stopSelf(); } }
	
	/*#############################################################################
	#########################         Starters              #######################
	#############################################################################*/
	
	/** Initializes the Bluetooth listener 
	 * Note: Bluetooth has several checks to make sure that it actually exists on the device with the capabilities we need.
	 * Checking for Bluetooth LE is necessary because it is an optional extension to Bluetooth 4.0. */
	public void startBluetooth(){
		//Note: the Bluetooth listener is a BroadcastReceiver, which means it must have a 0-argument constructor in order for android can instantiate it on broadcast receipts.
		//The following check must be made, but it requires a Context that we cannot pass into the BluetoothListener, so we do the check in the BackgroundService.
		if ( appContext.getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) && PersistentData.getEnabled(PersistentData.BLUETOOTH) ) {
			this.bluetoothListener = new BluetoothListener();
			if ( this.bluetoothListener.isBluetoothEnabled() ) {
//				Log.i("Background Service", "success, actually doing bluetooth things.");
				registerReceiver(this.bluetoothListener, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED") );
			} else {
				//TODO: Low priority. Eli. Track down why this error log pops up, cleanup.  -- the above check should be for the (new) doesBluetoothCapabilityExist function instead of isBluetoothEnabled
				Log.e("Background Service", "bluetooth Failure. Should not have gotten this far.");
				TextFileManager.getDebugLogFile().writeEncrypted("bluetooth Failure, device should not have gotten to this line of code");
			}
		} else {
			if (PersistentData.getEnabled(PersistentData.BLUETOOTH)) {
				TextFileManager.getDebugLogFile().writeEncrypted("Device does not support bluetooth LE, bluetooth features disabled.");
				Log.w("BackgroundService bluetooth init", "Device does not support bluetooth LE, bluetooth features disabled."); }
			// else { Log.d("BackgroundService bluetooth init", "Bluetooth not enabled for study."); }
			this.bluetoothListener = null;
		}
	}
	
	/** Initializes the sms logger. */
	public SmsSentLogger startSmsSentLogger() {
		SmsSentLogger smsSentLogger = new SmsSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, smsSentLogger);
		return smsSentLogger;
	}
	
	public MMSSentLogger startMmsSentLogger(){
		MMSSentLogger mmsMonitor = new MMSSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://mms/"), true, mmsMonitor);
		return mmsMonitor;
	}

	/** Initializes the call logger. */
	private CallLogger startCallLogger() {
		CallLogger callLogger = new CallLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://call_log/calls/"), true, callLogger);
		return callLogger;
	}
	
	/** Initializes the PowerStateListener. 
	 * The PowerStateListener requires the ACTION_SCREEN_OFF and ACTION_SCREEN_ON intents
	 * be registered programatically. They do not work if registered in the app's manifest.
	 * Same for the ACTION_POWER_SAVE_MODE_CHANGED and ACTION_DEVICE_IDLE_MODE_CHANGED filters,
	 * though they are for monitoring deeper power state changes in 5.0 and 6.0, respectively. */
	@SuppressLint("InlinedApi")
	private PowerStateListener newPowerStateListener() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
		}
		if (android.os.Build.VERSION.SDK_INT >= 23) {
			filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
		}
		PowerStateListener ret = new PowerStateListener();
		registerReceiver(ret, filter);
		PowerStateListener.start(appContext);
		return ret;
	}
	
	
	/** create timers that will trigger events throughout the program, and
	 * register the custom Intents with the controlMessageReceiver. */
	@SuppressWarnings("static-access")
	public static void registerTimers(Context appContext) {
		localHandle.timer = new Timer(localHandle);
		IntentFilter filter = new IntentFilter();
		filter.addAction( appContext.getString( R.string.turn_accelerometer_off ) );
		filter.addAction( appContext.getString( R.string.turn_accelerometer_on ) );
		filter.addAction( appContext.getString( R.string.turn_ambientlight_on ) );
		filter.addAction( appContext.getString( R.string.turn_bluetooth_off ) );
		filter.addAction( appContext.getString( R.string.turn_bluetooth_on ) );
		filter.addAction( appContext.getString( R.string.turn_gps_off ) );
		filter.addAction( appContext.getString( R.string.turn_gps_on ) );
		filter.addAction( appContext.getString( R.string.turn_gyroscope_off ) );
		filter.addAction( appContext.getString( R.string.turn_gyroscope_on ) );
		filter.addAction( appContext.getString( R.string.signout_intent ) );
		filter.addAction( appContext.getString( R.string.voice_recording ) );
		filter.addAction( appContext.getString( R.string.update_usage ) );
		filter.addAction( appContext.getString( R.string.run_wifi_log ) );
		filter.addAction( appContext.getString( R.string.upload_data_files_intent ) );
		filter.addAction( appContext.getString( R.string.create_new_data_files_intent ) );
		filter.addAction( appContext.getString( R.string.check_for_new_surveys_intent ) );
		filter.addAction( appContext.getString( R.string.check_for_sms_enabled ) );
		filter.addAction( appContext.getString( R.string.check_for_calls_enabled ) );
		filter.addAction( ConnectivityManager.CONNECTIVITY_ACTION );
		filter.addAction( "crashBeiwe" );
		filter.addAction( "enterANR" );
		List<String> surveyIds = PersistentData.getSurveyIds();
		for (String surveyId : surveyIds) { filter.addAction(surveyId); }
		appContext.registerReceiver(localHandle.timerReceiver, filter);
	}
	
	/*#############################################################################
	####################            Timer Logic             #######################
	#############################################################################*/
	
	public void startTimers() {
		Long now = System.currentTimeMillis();
		Log.i("BackgroundService", "running startTimer logic.");
		if (PersistentData.getEnabled(PersistentData.ACCELEROMETER)) {  //if accelerometer data recording is enabled and...
			if(PersistentData.getMostRecentAlarmTime( getString(R.string.turn_accelerometer_on )) < now || //the most recent accelerometer alarm time is in the past, or...
					!timer.alarmIsSet(Timer.accelerometerOnIntent) ) { //there is no scheduled accelerometer-on timer.
				sendBroadcast(Timer.accelerometerOnIntent); // start accelerometer timers (immediately runs accelerometer recording session).
				//note: when there is no accelerometer-off timer that means we are in-between scans.  This state is fine, so we don't check for it.
			}
			else if(timer.alarmIsSet(Timer.accelerometerOffIntent)
					&& PersistentData.getMostRecentAlarmTime(getString( R.string.turn_accelerometer_on )) - PersistentData.getAccelerometerOffDurationMilliseconds() + 1000 > now ) {
				accelerometerListener.turn_on();
			}
		}

		if (PersistentData.getEnabled(PersistentData.GYROSCOPE)) {
			if(PersistentData.getMostRecentAlarmTime( getString(R.string.turn_gyroscope_on )) < now || //the most recent accelerometer alarm time is in the past, or...
					!timer.alarmIsSet(Timer.gyroscopeOnIntent) ) {
				sendBroadcast(Timer.gyroscopeOnIntent);
			}
			else if(timer.alarmIsSet(Timer.gyroscopeOffIntent)
					&& PersistentData.getMostRecentAlarmTime(getString( R.string.turn_gyroscope_on )) - PersistentData.getGyroOffDurationMilliseconds() + 1000 > now ) {
				gyroscopeListener.turn_on();
			}
		}

		if (PersistentData.getEnabled(PersistentData.AMBIENTLIGHT)) {  //if ambient light data recording is enabled and...
			if(PersistentData.getMostRecentAlarmTime( getString(R.string.turn_ambientlight_on )) < now || //the most recent accelerometer alarm time is in the past, or...
					!timer.alarmIsSet(Timer.ambientLightIntent) ) { //there is no scheduled accelerometer-on timer.
				sendBroadcast(Timer.ambientLightIntent); // start accelerometer timers (immediately runs accelerometer recording session).
				//note: when there is no off timer that means we are in-between scans.  This state is fine, so we don't check for it.
			}
		}

		if (PersistentData.getEnabled(PersistentData.USAGE)) {  //if usage recording is enabled and...
			if(PersistentData.getMostRecentAlarmTime( getString(R.string.update_usage )) < now || //the most recent usage update time is in the past, or...
					!timer.alarmIsSet(Timer.usageIntent) ) { //there is no scheduled usage update timer.
				sendBroadcast(Timer.usageIntent); // start usage timers (immediately update usage).
				//note: when there is no off timer that means we are in-between scans.  This state is fine, so we don't check for it.
			}
		}

		if ( PersistentData.getMostRecentAlarmTime(getString( R.string.turn_gps_on )) < now || !timer.alarmIsSet(Timer.gpsOnIntent) ) {
			sendBroadcast( Timer.gpsOnIntent ); }
		else if(PersistentData.getEnabled(PersistentData.GPS) && timer.alarmIsSet(Timer.gpsOffIntent)
				&& PersistentData.getMostRecentAlarmTime(getString( R.string.turn_gps_on )) - PersistentData.getGpsOffDurationMilliseconds() + 1000 > now ) {
			gpsListener.turn_on();
		}
		
		if ( PersistentData.getMostRecentAlarmTime( getString(R.string.run_wifi_log)) < now || //the most recent wifi log time is in the past or
				!timer.alarmIsSet(Timer.wifiLogIntent) ) {
			sendBroadcast( Timer.wifiLogIntent ); }
		
		//if Bluetooth recording is enabled and there is no scheduled next-bluetooth-enable event, set up the next Bluetooth-on alarm.
		//(Bluetooth needs to run at absolute points in time, it should not be started if a scheduled event is missed.)
		if ( PermissionHandler.confirmBluetooth(appContext) && !timer.alarmIsSet(Timer.bluetoothOnIntent)) {
			timer.setupExactSingleAbsoluteTimeAlarm(PersistentData.getBluetoothTotalDurationMilliseconds(), PersistentData.getBluetoothGlobalOffsetMilliseconds(), Timer.bluetoothOnIntent); }
		
		// Functionality timers. We don't need aggressive checking for if these timers have been missed, as long as they run eventually it is fine.
		if (!timer.alarmIsSet(Timer.uploadDatafilesIntent)) { timer.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequencyMilliseconds(), Timer.uploadDatafilesIntent); }
		if (!timer.alarmIsSet(Timer.createNewDataFilesIntent)) { timer.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequencyMilliseconds(), Timer.createNewDataFilesIntent); }
		if (!timer.alarmIsSet(Timer.checkForNewSurveysIntent)) { timer.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequencyMilliseconds(), Timer.checkForNewSurveysIntent); }

		//checks for the current expected state for survey notifications,
		for (String surveyId : PersistentData.getSurveyIds() ){
			if ( PersistentData.getSurveyNotificationState(surveyId) || PersistentData.getMostRecentSurveyAlarmTime(surveyId) < now ) {
				//if survey notification should be active or the most recent alarm time is in the past, trigger the notification.
				SurveyNotifications.displaySurveyNotification(appContext, surveyId); } }
		
		//checks that surveys are actually scheduled, if a survey is not scheduled, schedule it!
		for (String surveyId : PersistentData.getSurveyIds() ) {
			if ( !timer.alarmIsSet( new Intent(surveyId) ) ) { SurveyScheduler.scheduleSurvey(surveyId); } }

		Intent restartServiceIntent = new Intent( getApplicationContext(), BackgroundService.class);
		restartServiceIntent.setPackage( getPackageName() );
		PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, 0 );
		AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService( Context.ALARM_SERVICE );
		alarmService.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000 * 60 * 2, 1000 * 60 * 2, restartServicePendingIntent);
	}
	
	/**Refreshes the logout timer.
	 * This function has a THEORETICAL race condition, where the BackgroundService is not fully instantiated by a session activity,
	 * in this case we log an error to the debug log, print the error, and then wait for it to crash.  In testing on a (much) older
	 * version of the app we would occasionally see the error message, but we have never (august 10 2015) actually seen the app crash
	 * inside this code. */
	public static void startAutomaticLogoutCountdownTimer(){
		long tm = PersistentData.getMillisecondsBeforeAutoLogout();
		if ( tm == 0 ) return;
		if ( timer == null ) {
			Log.e("bacgroundService", "timer is null, BackgroundService may be about to crash, the Timer was null when the BackgroundService was supposed to be fully instantiated.");
			TextFileManager.getDebugLogFile().writeEncrypted("our not-quite-race-condition encountered, Timer was null when the BackgroundService was supposed to be fully instantiated");
			return;
		}
		timer.setupExactSingleAlarm(tm, Timer.signoutIntent);
		PersistentData.loginOrRefreshLogin();
	}
	
	/** cancels the signout timer */
	public static void clearAutomaticLogoutCountdownTimer() { timer.cancelAlarm(Timer.signoutIntent); }
	
	/** The Timer requires the BackgroundService in order to create alarms, hook into that functionality here. */
	public static void setSurveyAlarm(String surveyId, Calendar alarmTime) { timer.startSurveyAlarm(surveyId, alarmTime); }

	public static void cancelSurveyAlarm(String surveyId) { timer.cancelAlarm(new Intent(surveyId)); }
	
	/**The timerReceiver is an Android BroadcastReceiver that listens for our timer events to trigger,
	 * and then runs the appropriate code for that trigger. 
	 * Note: every condition has a return statement at the end; this is because the trigger survey notification
	 * action requires a fairly expensive dive into PersistantData JSON unpacking.*/
	private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context appContext, Intent intent) {
			if( BuildConfig.APP_IS_DEV )
				Log.d("BackgroundService - timers","Received broadcast: " + intent.toString() );
			TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " Received Broadcast: " + intent.toString() );
			String broadcastAction = intent.getAction();

			/** For GPS and Accelerometer the failure modes are:
			 * 1. If a recording event is triggered and followed by Doze being enabled then Beiwe will record until the Doze period ends.
			 * 2. If, after Doze ends, the timers trigger out of order Beiwe ceaces to record and triggers a new recording event in the future. */

			/** Disable active sensor */
			if (broadcastAction.equals( appContext.getString(R.string.turn_accelerometer_off) ) ) {
				accelerometerListener.turn_off();
				return; }
			if (broadcastAction.equals( appContext.getString(R.string.turn_gps_off) ) ) {
				if ( PermissionHandler.checkGpsPermissions(appContext) ) { gpsListener.turn_off(); }
				return; }
			if (broadcastAction.equals( appContext.getString(R.string.turn_gyroscope_off) ) ) {
				gyroscopeListener.turn_off();
				return; }

			/** Enable active sensors, reset timers. */
			//Accelerometer. We automatically have permissions required for accelerometer.
			if (broadcastAction.equals( appContext.getString(R.string.turn_accelerometer_on) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.ACCELEROMETER) ) { Log.e("BackgroundService Listener", "invalid Accelerometer on received"); return; }
				accelerometerListener.turn_on();
				//start both the sensor-off-action timer, and the next sensor-on-timer.
				long off_duration = PersistentData.getAccelerometerOffDurationMilliseconds();
				if(off_duration>0)
					timer.setupExactSingleAlarm(PersistentData.getAccelerometerOnDurationMilliseconds(), Timer.accelerometerOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(off_duration + PersistentData.getAccelerometerOnDurationMilliseconds(), Timer.accelerometerOnIntent);
				//record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash. 
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_accelerometer_on), alarmTime );
				return; }

			//AmbientLight. We automatically have permissions required for ambient light sensor.
			if (broadcastAction.equals( appContext.getString(R.string.turn_ambientlight_on) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.AMBIENTLIGHT) ) { Log.e("BackgroundService Listener", "invalid AmbientLight on received"); return; }
				ambientLightListener.turn_on();
				//start the next sensor-on-timer.
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getAmbientLightIntervalMilliseconds(), Timer.ambientLightIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_ambientlight_on), alarmTime );
				return; }

			//Gyroscope. Almost identical logic to accelerometer above.
			if (broadcastAction.equals( appContext.getString(R.string.turn_gyroscope_on) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.GYROSCOPE) ) { Log.e("BackgroundService Listener", "invalid Gyroscope on received"); return; }
				gyroscopeListener.turn_on();
				long off_duration = PersistentData.getGyroOffDurationMilliseconds();
				if(off_duration>0)
					timer.setupExactSingleAlarm(PersistentData.getGyroOnDurationMilliseconds(), Timer.gyroscopeOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(off_duration + PersistentData.getGyroOnDurationMilliseconds(), Timer.gyroscopeOnIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gyroscope_on), alarmTime );
				return; }

			//GPS. Almost identical logic to accelerometer above.
			if (broadcastAction.equals( appContext.getString(R.string.turn_gps_on) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.GPS) ) { Log.e("BackgroundService Listener", "invalid GPS on received"); return; }
				gpsListener.turn_on();
				long off_duration = PersistentData.getGpsOffDurationMilliseconds();
				if(off_duration>0)
					timer.setupExactSingleAlarm(PersistentData.getGpsOnDurationMilliseconds(), Timer.gpsOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(off_duration + PersistentData.getGpsOnDurationMilliseconds(), Timer.gpsOnIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gps_on), alarmTime );
				return; }

			//run a wifi scan.  Most similar to GPS, but without an off-timer.
			if (broadcastAction.equals( appContext.getString(R.string.run_wifi_log) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.WIFI) ) { Log.e("BackgroundService Listener", "invalid WiFi scan received"); return; }
				if ( PermissionHandler.checkWifiPermissions(appContext) ) { WifiListener.scanWifi(); }
				else { TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " user has not provided permission for Wifi."); }
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getWifiLogFrequencyMilliseconds(), Timer.wifiLogIntent);
				PersistentData.setMostRecentAlarmTime( getString(R.string.run_wifi_log), alarmTime );
				return; }

			//Usage update. We automatically have permissions required for usage.
			if (broadcastAction.equals( appContext.getString(R.string.update_usage) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.USAGE) ) { Log.e("BackgroundService Listener", "invalid Update Usage on received"); return; }
				usageListener.updateUsage();
				//start the next sensor-on-timer.
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getUsageUpdateIntervalMilliseconds(), Timer.usageIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.update_usage), alarmTime );
				return; }

			/** Bluetooth timers are unlike GPS and Accelerometer because it uses an absolute-point-in-time as a trigger, and therefore we don't need to store most-recent-timer state.
			 * The Bluetooth-on action sets the corresponding Bluetooth-off timer, the Bluetooth-off action sets the next Bluetooth-on timer.*/
			if (broadcastAction.equals( appContext.getString(R.string.turn_bluetooth_on) ) ) {
				if ( !PersistentData.getEnabled(PersistentData.BLUETOOTH) ) { Log.e("BackgroundService Listener", "invalid Bluetooth on received"); return; }
				if ( PermissionHandler.checkBluetoothPermissions(appContext) ) {
					if (bluetoothListener != null) bluetoothListener.enableBLEScan(); }
				else { TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " user has not provided permission for Bluetooth."); }
				timer.setupExactSingleAlarm(PersistentData.getBluetoothOnDurationMilliseconds(), Timer.bluetoothOffIntent);
				return;
			}
			if (broadcastAction.equals( appContext.getString(R.string.turn_bluetooth_off) ) ) {
				if ( PermissionHandler.checkBluetoothPermissions(appContext) ) {
					if ( bluetoothListener != null) bluetoothListener.disableBLEScan(); }
				timer.setupExactSingleAbsoluteTimeAlarm(PersistentData.getBluetoothTotalDurationMilliseconds(), PersistentData.getBluetoothGlobalOffsetMilliseconds(), Timer.bluetoothOnIntent);
				return;
			}
			
			//starts a data upload attempt.
			if (broadcastAction.equals( appContext.getString(R.string.upload_data_files_intent) ) ) {
				PostRequest.uploadAllFiles();
				timer.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequencyMilliseconds(), Timer.uploadDatafilesIntent);
				return;
			}
			//creates new data files
			if (broadcastAction.equals( appContext.getString(R.string.create_new_data_files_intent) ) ) {
				TextFileManager.makeNewFilesForEverything();
				timer.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequencyMilliseconds(), Timer.createNewDataFilesIntent);
				return;
			}
			//Downloads the most recent survey questions and schedules the surveys.
			if (broadcastAction.equals( appContext.getString(R.string.check_for_new_surveys_intent))) {
				SurveyDownloader.downloadSurveys( getApplicationContext() );
				timer.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequencyMilliseconds(), Timer.checkForNewSurveysIntent);
				return;
			}
			// Signs out the user. (does not set up a timer, that is handled in activity and sign-in logic) 
			if (broadcastAction.equals( appContext.getString(R.string.signout_intent) ) ) {
				PersistentData.logout();
				Intent loginPage = new Intent(appContext, LoginActivity.class);
				loginPage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				appContext.startActivity(loginPage);
				return;
			}

			if (broadcastAction.equals( appContext.getString(R.string.check_for_sms_enabled) ) ) {
				if ( PermissionHandler.confirmTexts(appContext) ) { startSmsSentLogger(); startMmsSentLogger(); }
				else if (PersistentData.getEnabled(PersistentData.TEXTS) ) { timer.setupExactSingleAlarm(30000L, Timer.checkForSMSEnabled); }
			}
			if (broadcastAction.equals( appContext.getString(R.string.check_for_calls_enabled) ) ) {
				if ( PermissionHandler.confirmCalls(appContext) ) { startCallLogger(); }
				else if (PersistentData.getEnabled(PersistentData.CALLS) ) { timer.setupExactSingleAlarm(30000L, Timer.checkForCallsEnabled); }
			}
			//checks if the action is the id of a survey (expensive), if so pop up the notification for that survey, schedule the next alarm
			if ( PersistentData.getSurveyIds().contains( broadcastAction ) ) {
//				Log.i("BACKGROUND SERVICE", "new notification: " + broadcastAction);
				SurveyNotifications.displaySurveyNotification(appContext, broadcastAction);
				SurveyScheduler.scheduleSurvey(broadcastAction);
				return;
			}

			if ( PersistentData.isRegistered() && broadcastAction.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
				if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					PostRequest.uploadAllFiles();
					return;
				}
			}

			//this is a special action that will only run if the app device is in debug mode.
			if (broadcastAction.equals("crashBeiwe") && BuildConfig.APP_IS_BETA)
				throw new NullPointerException("beeeeeoooop.");
			//this is a special action that will only run if the app device is in debug mode.
			if (broadcastAction.equals("enterANR") && BuildConfig.APP_IS_BETA) {
				try {
					Thread.sleep(100000);
				}
				catch(InterruptedException ie) {
					ie.printStackTrace();
				}
			}
		}
	};
		
	/*##########################################################################################
	############## code related to onStartCommand and binding to an activity ###################
	##########################################################################################*/
	@Override
	public IBinder onBind(Intent arg0) { return new BackgroundServiceBinder(); }
	
	/**A public "Binder" class for Activities to access.
	 * Provides a (safe) handle to the background Service using the onStartCommand code
	 * used in every RunningBackgroundServiceActivity */
	public class BackgroundServiceBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }
	
	/*##############################################################################
	########################## Android Service Lifecycle ###########################
	##############################################################################*/
	
	/** The BackgroundService is meant to be all the time, so we return START_STICKY */
	@Override public int onStartCommand(Intent intent, int flags, int startId){ //Log.d("BackroundService onStartCommand", "started with flag " + flags );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"started with flag " + flags);
		return START_STICKY;
		//we are testing out this restarting behavior for the service.  It is entirely unclear that this will have any observable effect.
		//return START_REDELIVER_INTENT;
	}
	//(the rest of these are identical, so I have compactified it)
	@Override public void onTaskRemoved(Intent rootIntent) { //Log.d("BackroundService onTaskRemoved", "onTaskRemoved called with intent: " + rootIntent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onTaskRemoved called with intent: " + rootIntent.toString());
		restartService(); }
	@Override public boolean onUnbind(Intent intent) { //Log.d("BackroundService onUnbind", "onUnbind called with intent: " + intent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onUnbind called with intent: " + intent.toString());
		restartService();
		return super.onUnbind(intent); }
	@Override public void onDestroy() { //Log.w("BackgroundService", "BackgroundService was destroyed.");
		//note: this does not run when the service is killed in a task manager, OR when the stopService() function is called from debugActivity.
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"BackgroundService was destroyed.");
		restartService();
		super.onDestroy(); }
	@Override public void onLowMemory() { //Log.w("BackroundService onLowMemory", "Low memory conditions encountered");
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onLowMemory called.");
		restartService(); }
	
	/** Sets a timer that starts the service if it is not running in ten seconds. */
	private void restartService(){
		//how does this even...  Whatever, 10 seconds later the background service will start.
		Intent restartServiceIntent = new Intent( getApplicationContext(), this.getClass() );
	    restartServiceIntent.setPackage( getPackageName() );
	    PendingIntent restartServicePendingIntent = PendingIntent.getService( getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT );
	    AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService( Context.ALARM_SERVICE );
	    alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, restartServicePendingIntent);
	}
	
	public void crashBackgroundService() { if (BuildConfig.APP_IS_BETA) {
		throw new NullPointerException("stop poking me!"); } }
}