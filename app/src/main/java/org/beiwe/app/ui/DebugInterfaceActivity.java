package org.beiwe.app.ui;

import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.BuildConfig;
import org.beiwe.app.CrashHandler;
import org.beiwe.app.PermissionHandler;
import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.Timer;
import org.beiwe.app.listeners.AccelerometerListener;
import org.beiwe.app.listeners.AccessibilityListener;
import org.beiwe.app.listeners.AmbientLightListener;
import org.beiwe.app.listeners.AmbientTemperatureListener;
import org.beiwe.app.listeners.BluetoothListener;
import org.beiwe.app.listeners.GPSListener;
import org.beiwe.app.listeners.GyroscopeListener;
import org.beiwe.app.listeners.TapsListener;
import org.beiwe.app.listeners.UsageListener;
import org.beiwe.app.listeners.WifiListener;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.networking.SurveyDownloader;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.survey.JsonSkipLogic;
import org.beiwe.app.ui.qrcode.BarcodeCaptureActivity;
import org.beiwe.app.ui.user.MainMenuActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.renderscript.ScriptC;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.support.v7.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


public class DebugInterfaceActivity extends SessionActivity {
	//extends a session activity.
	Context appContext;
	private static TextView logcat_view;
	private static TextView debug_intro_text;
	private static Button debug_stop_button;
	private static Button toggle_gesture_button;
	private static ScrollView logcat_scroll;
	private static String logcat_text = "";

	public class LogFile {
		public static final String name = "logFile";
		public static final String header = "DEBUG LOG FILE";
	}

	public class ListFile {
		public static final String name = "listFile";
		public static final String header = "LIST ALL FILES";
	}

	public class ListFeature {
		public static final String name = "listFeature";
		public static final String header = "LIST ALL FEATURES";
	}

	public class ListPermission {
		public static final String name = "permissions";
		public static final String header = "LIST ALL PERMISSIONS";
	}

	public class ScanQR {
		public static final String name = "scanQR";
		public static final String header = "QR code raw text";
	}

	public class UploadFiles {
		public static final String name = "uploadFiles";
		public static final String header = "File upload log";
	}

	private static boolean atBottom = true;
	private static boolean isActive = false;

	public void setConsoleMode(String new_feature){
		show_feature = new_feature;
		boolean active = !new_feature.isEmpty();
		logcat_view.setText(active?logcat_text:"Beiwe debug console");
		debug_stop_button.setVisibility(active?View.VISIBLE:View.GONE);
		debug_intro_text.setVisibility(active?View.GONE:View.VISIBLE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		isActive = true;
		setConsoleMode(show_feature); // activity has restarted, restore the original display content
	}

	@Override
	protected void onStop(){
		super.onStop();
		isActive = false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_debug_interface);
		appContext = getApplicationContext();
		debug_intro_text = findViewById(R.id.debugIntro);
		debug_stop_button = findViewById(R.id.buttonStopConsole);
		toggle_gesture_button = findViewById(R.id.buttonToggleGesture);
		logcat_view = findViewById(R.id.logcat_view);
		logcat_scroll = findViewById(R.id.logcat_scroll);
		logcat_scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
			@Override
			public void onScrollChange(View view, int i, int i1, int i2, int i3) {
				int diff = (logcat_view.getBottom() - (logcat_scroll.getHeight() + logcat_scroll.getScrollY()));
				atBottom = (diff == 0);	// if diff is zero, then the bottom has been reached
			}
		});

		// set long click listener
		Object longClickButtons[][] = {
				{ BluetoothListener.class, R.id.buttonStartBluetooth, R.id.buttonStopBluetooth },
				{ GPSListener.class, R.id.buttonEnableGPS, R.id.buttonDisableGPS },
				{ AccelerometerListener.class, R.id.buttonEnableAccelerometer, R.id.buttonDisableAccelerometer },
				{ AccessibilityListener.class, R.id.buttonEnableAccessibility, R.id.buttonDisableAccessibility },
				{ AmbientLightListener.class, R.id.buttonEnableAmbientLight },
				{ AmbientTemperatureListener.class, R.id.buttonEnableAmbientTemperature },
				{ GyroscopeListener.class, R.id.buttonEnableGyroscope, R.id.buttonDisableGyroscope },
				{ TapsListener.class, R.id.buttonEnableTaps, R.id.buttonDisableTaps },
				{ UsageListener.class, R.id.buttonUpdateUsage },
				{ WifiListener.class, R.id.buttonWifiScan },
				{ ScanQR.class, R.id.buttonTestQRscan},
				{ LogFile.class, R.id.buttonPrintInternalLog, R.id.buttonClearInternalLog },
				{ ListFile.class, R.id.buttonListFiles },
				{ ListFeature.class, R.id.buttonFeaturesEnabled },
				{ ListPermission.class, R.id.buttonFeaturesPermissable },
				{ UploadFiles.class, R.id.buttonUpload },
		};
		for( Object longClickButton[] : longClickButtons )
			for( int x=1; x<longClickButton.length; ++x ) try {
				Class cls = (Class)longClickButton[0];
				final String name, header;
				name = (String) (cls.getField("name").get(null));
				header = (String) (cls.getField("header").get(null));
				Button button = findViewById((int)longClickButton[x]);
				button.setText("*"+button.getText());
				button.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View view) {
						try {
							logcat_text = name+": "+header;
							setConsoleMode(name);
							Toast.makeText(appContext,"Output console shows "+name, Toast.LENGTH_SHORT).show();
						} catch (Exception e) {}
						return true;
					}
				});
			} catch ( Exception e ) { }
	}

	public static boolean unlocked = BuildConfig.APP_IS_DEV;
	public static String show_feature = "";
	public static void smartLog( String tag, String data ){
		if( BuildConfig.APP_IS_DEV )
			Log.i( tag, data );
		if( unlocked && tag.equals(show_feature) ){
			logcat_text += "\n"+data;
			while ( logcat_text.length() > 1000000 ) {	// limit text view buffer
				int p = logcat_text.indexOf('\n');
				logcat_text = (p<0?"":logcat_text.substring(p+1));
			}
			if(isActive)
				try {
					logcat_view.setText(logcat_text);
					if (atBottom)	// previously it is at bottom
						logcat_scroll.scrollTo(0, logcat_view.getBottom());
				} catch (Exception e) { }
		}
	}

	//Intent triggers caught in BackgroundService
	public void accelerometerOn (View view) { appContext.sendBroadcast( Timer.accelerometerOnIntent ); }
	public void accelerometerOff (View view) { appContext.sendBroadcast( Timer.accelerometerOffIntent ); }
	public void accessibilityOn (View view) { AccessibilityListener.listen = true; }
	public void accessibilityOff (View view) { AccessibilityListener.listen = false; }
	public void gyroscopeOn (View view) { appContext.sendBroadcast( Timer.gyroscopeOnIntent ); }
	public void gyroscopeOff (View view) { appContext.sendBroadcast( Timer.gyroscopeOffIntent ); }
	public void ambientLightOn (View view) { appContext.sendBroadcast( Timer.ambientLightIntent); }
	public void ambientTemperatureOn (View view) { appContext.sendBroadcast( Timer.ambientTemperatureIntent); }
	public void gpsOn (View view) { appContext.sendBroadcast( Timer.gpsOnIntent ); }
	public void gpsOff (View view) { appContext.sendBroadcast( Timer.gpsOffIntent ); }
	public void tapsOn (View view) { backgroundService.tapsListener.addView(); }
	public void tapsOff (View view) { backgroundService.tapsListener.removeView(); }
	public void scanWifi (View view) { appContext.sendBroadcast( Timer.wifiLogIntent ); }
	public void usageUpdate (View view) { appContext.sendBroadcast( Timer.usageIntent ); }
	public void bluetoothButtonStart (View view) { appContext.sendBroadcast(Timer.bluetoothOnIntent); }
	public void bluetoothButtonStop (View view) { appContext.sendBroadcast(Timer.bluetoothOffIntent); }
	public void stopConsole (View view) { logcat_text = ""; setConsoleMode(""); }
	public void testScanQR (View view){
		BarcodeCaptureActivity.checkQR = new Callable<Boolean>() { public Boolean call() { return true; } };
		startActivityForResult( new Intent( appContext, BarcodeCaptureActivity.class ), BARCODE_READER_REQUEST_CODE );
	}

	public void toggleGestureMode (View view) {
		AccessibilityListener.mSelf.toggleGestureMode(view);
		toggle_gesture_button.setText(AccessibilityListener.isGestureMode?
				"Turn Off Gesture Mode (Accessibility)":"Turn On Gesture Mode (Accessibility)");
	}

	private final static int BARCODE_READER_REQUEST_CODE = 2600;
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch ( requestCode ) {
			case BARCODE_READER_REQUEST_CODE:
				smartLog(ScanQR.name, BarcodeCaptureActivity.scan_result);
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	//raw debugging info
	public void printInternalLog(View view) {
		smartLog( LogFile.name, TextFileManager.getDebugLogFile().read() );
	}
	public void testEncrypt (View view) {
		Log.i("Debug..", TextFileManager.getKeyFile().read());
		String data = TextFileManager.getKeyFile().read();
		Log.i("reading keyFile:", data );
		try { EncryptionEngine.readKey(); }
		catch (InvalidKeySpecException e) {
			Log.e("DebugInterfaceActivity", "this is only partially implemented, unknown behavior");
			e.printStackTrace();
			throw new NullPointerException("some form of encryption error, type 1");
		}
		String encrypted;
		try { encrypted = EncryptionEngine.encryptRSA("ThIs Is a TeSt".getBytes() ).toString(); }
		catch (InvalidKeySpecException e) {
			Log.e("DebugInterfaceActivity", "this is only partially implemented, unknown behavior");
			e.printStackTrace();
			throw new NullPointerException("some form of encryption error, type 2");
		}
		Log.i("test encrypt - length:", "" + encrypted.length() );
		Log.i("test encrypt - output:", encrypted );
		Log.i("test hash:", EncryptionEngine.safeHash( encrypted ) );
		Log.i("test hash:", EncryptionEngine.hashMAC( encrypted ) );
	}

	public void getAlarmStates(View view) {
		List<String> ids = PersistentData.getSurveyIds();
		for (String surveyId : ids){
			Log.i("most recent alarm state", "survey id: " + surveyId + ", " +PersistentData.getMostRecentSurveyAlarmTime(surveyId) + ", " + PersistentData.getSurveyNotificationState(surveyId)) ;
		}
	}
	
	public void getEnabledFeatures(View view) {
		for(String feature : PersistentData.feature_list)
				smartLog( ListFeature.name,feature+" is "+(PersistentData.getEnabled(feature)?"Enabled":"Disabled") );
	}
	
	public void getPermissableFeatures(View view) {
		smartLog(ListPermission.name, PermissionHandler.checkAccessFineLocation(appContext)?"AccessFineLocation enabled.":"AccessFineLocation disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessNetworkState(appContext)?"AccessNetworkState enabled.":"AccessNetworkState disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessWifiState(appContext)?"AccessWifiState enabled.":"AccessWifiState disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessBluetooth(appContext)?"Bluetooth enabled.":"Bluetooth disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessBluetoothAdmin(appContext)?"BluetoothAdmin enabled.":"BluetoothAdmin disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessCallPhone(appContext)?"CallPhone enabled.":"CallPhone disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessReadCallLog(appContext)?"ReadCallLog enabled.":"ReadCallLog disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessReadContacts(appContext)?"ReadContacts enabled.":"ReadContacts disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessReadPhoneState(appContext)?"ReadPhoneState enabled.":"ReadPhoneState disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessReadSms(appContext)?"ReadSms enabled.":"ReadSms disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessReceiveMms(appContext)?"ReceiveMms enabled.":"ReceiveMms disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessReceiveSms(appContext)?"ReceiveSms enabled.":"ReceiveSms disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessRecordAudio(appContext)?"RecordAudio enabled.":"RecordAudio disabled.");
		smartLog(ListPermission.name, PermissionHandler.checkAccessUsagePermission(appContext)?"Package Usage enabled.":"Package Usage disabled.");
		smartLog(ListPermission.name, AccessibilityListener.isEnabled(appContext)?"AccessibilityService enabled.":"AccessibilityService disabled.");
	}
	
	public void clearInternalLog(View view) { TextFileManager.getDebugLogFile().deleteSafely(); }
	public void getKeyFile(View view) { Log.i("DEBUG", "key file data: " + TextFileManager.getKeyFile().read()); }
	
	
	//network operations
	public void uploadDataFiles(View view) { PostRequest.uploadAllFiles(); }
	public void runSurveyDownload(View view) { SurveyDownloader.downloadSurveys(appContext); }
	public void buttonTimer(View view) { backgroundService.startTimers(); }	
	
	
	//file operations
	public void makeNewFiles(View view) { TextFileManager.makeNewFilesForEverything(); }
	public void deleteEverything(View view) {
		Log.i("Delete Everything button pressed", "poke.");
		String[] files = TextFileManager.getAllFiles();
		Arrays.sort(files);
		for( String file : files ) { Log.i( "files...", file); }
		TextFileManager.deleteEverything(); }
	public void listFiles(View view){
		smartLog( ListFile.name,"UPLOADABLE FILES" );
		String[] files = TextFileManager.getAllUploadableFiles();
		Arrays.sort(files);
		for( String file : files ) { smartLog( ListFile.name, file ); }
		smartLog( ListFile.name,"ALL FILES" );
		files = TextFileManager.getAllFiles();
		Arrays.sort(files);
		for( String file : files ) { smartLog( ListFile.name, file ); }
	}

	//ui operations
	public void loadMainMenu(View view) { startActivity(new Intent(appContext, MainMenuActivity.class) ); }
	public void popSurveyNotifications(View view) {
		for ( String surveyId : PersistentData.getSurveyIds() )
			SurveyNotifications.displaySurveyNotification(appContext, surveyId);
	}
	
	//crash operations (No, really, we actually need this.)
	public void crashUi(View view) { throw new NullPointerException("oops, you bwoke it."); }
	public void crashBackground(View view) { BackgroundService.timer.setupExactSingleAlarm((long) 0, new Intent("crashBeiwe")); }
	public void crashBackgroundInFive(View view) { BackgroundService.timer.setupExactSingleAlarm((long) 5000, new Intent("crashBeiwe")); }
	public void enterANRUI(View view) { try { Thread.sleep(100000); } catch(InterruptedException ie) {	ie.printStackTrace(); } }
	public void enterANRBackground(View view) { BackgroundService.timer.setupExactSingleAlarm((long) 0, new Intent("enterANR")); }
	public void stopBackgroundService(View view) { backgroundService.stop(); }
	public void testManualErrorReport(View view) {
		try{ throw new NullPointerException("this is a test null pointer exception from the debug interface"); }
		catch (Exception e) { CrashHandler.writeCrashlog(e, getApplicationContext()); }
	}

	//runs tests on the json logic parser
	public void testJsonLogicParser(View view) {
		String JsonQuestionsListString = "[{\"question_text\": \"In the last 7 days, how OFTEN did you EAT BROCCOLI?\", \"question_type\": \"radio_button\", \"answers\": [{\"text\": \"Never\"}, {\"text\": \"Rarely\"}, {\"text\": \"Occasionally\"}, {\"text\": \"Frequently\"}, {\"text\": \"Almost Constantly\"}], \"question_id\": \"6695d6c4-916b-4225-8688-89b6089a24d1\"}, {\"display_if\": {\">\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 0]}, \"question_text\": \"In the last 7 days, what was the SEVERITY of your CRAVING FOR BROCCOLI?\", \"question_type\": \"radio_button\", \"answers\": [{\"text\": \"None\"}, {\"text\": \"Mild\"}, {\"text\": \"Moderate\"}, {\"text\": \"Severe\"}, {\"text\": \"Very Severe\"}], \"question_id\": \"41d54793-dc4d-48d9-f370-4329a7bc6960\"}, {\"display_if\": {\"and\": [{\">\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 0]}, {\">\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 0]}]}, \"question_text\": \"In the last 7 days, how much did your CRAVING FOR BROCCOLI INTERFERE with your usual or daily activities, (e.g. eating cauliflower)?\", \"question_type\": \"radio_button\", \"answers\": [{\"text\": \"Not at all\"}, {\"text\": \"A little bit\"}, {\"text\": \"Somewhat\"}, {\"text\": \"Quite a bit\"}, {\"text\": \"Very much\"}], \"question_id\": \"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\"}, {\"display_if\": {\"or\": [{\"and\": [{\"<=\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 3]}, {\"==\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 2]}, {\"<\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 3]}]}, {\"and\": [{\"<=\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 3]}, {\"<\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 3]}, {\"==\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 2]}]}, {\"and\": [{\"==\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 4]}, {\"<=\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 1]}, {\"<=\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 1]}]}]}, \"question_text\": \"While broccoli is a nutritious and healthful food, it's important to recognize that craving too much broccoli can have adverse consequences on your health.  If in a single day you find yourself eating broccoli steamed, stir-fried, and raw with a 'vegetable dip', you may be a broccoli addict.  This is an additional paragraph (following a double newline) warning you about the dangers of broccoli consumption.\", \"question_type\": \"info_text_box\", \"question_id\": \"9d7f737d-ef55-4231-e901-b3b68ca74190\"}, {\"display_if\": {\"or\": [{\"and\": [{\"==\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 4]}, {\"or\": [{\">=\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 2]}, {\">=\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 2]}]}]}, {\"or\": [{\">=\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 3]}, {\">=\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 3]}]}]}, \"question_text\": \"OK, it sounds like your broccoli habit is getting out of hand.  Please call your clinician immediately.\", \"question_type\": \"info_text_box\", \"question_id\": \"59f05c45-df67-40ed-a299-8796118ad173\"}, {\"question_text\": \"How many pounds of broccoli per day could a woodchuck chuck if a woodchuck could chuck broccoli?\", \"text_field_type\": \"NUMERIC\", \"question_type\": \"free_response\", \"question_id\": \"9745551b-a0f8-4eec-9205-9e0154637513\"}, {\"display_if\": {\"<\": [\"9745551b-a0f8-4eec-9205-9e0154637513\", 10]}, \"question_text\": \"That seems a little low.\", \"question_type\": \"info_text_box\", \"question_id\": \"cedef218-e1ec-46d3-d8be-e30cb0b2d3aa\"}, {\"display_if\": {\"==\": [\"9745551b-a0f8-4eec-9205-9e0154637513\", 10]}, \"question_text\": \"That sounds about right.\", \"question_type\": \"info_text_box\", \"question_id\": \"64a2a19b-c3d0-4d6e-9c0d-06089fd00424\"}, {\"display_if\": {\">\": [\"9745551b-a0f8-4eec-9205-9e0154637513\", 10]}, \"question_text\": \"What?! No way- that's way too high!\", \"question_type\": \"info_text_box\", \"question_id\": \"166d74ea-af32-487c-96d6-da8d63cfd368\"}, {\"max\": \"5\", \"question_id\": \"059e2f4a-562a-498e-d5f3-f59a2b2a5a5b\", \"question_text\": \"On a scale of 1 (awful) to 5 (delicious) stars, how would you rate your dinner at Chez Broccoli Restaurant?\", \"question_type\": \"slider\", \"min\": \"1\"}, {\"display_if\": {\">=\": [\"059e2f4a-562a-498e-d5f3-f59a2b2a5a5b\", 4]}, \"question_text\": \"Wow, you are a true broccoli fan.\", \"question_type\": \"info_text_box\", \"question_id\": \"6dd9b20b-9dfc-4ec9-cd29-1b82b330b463\"}, {\"question_text\": \"THE END. This survey is over.\", \"question_type\": \"info_text_box\", \"question_id\": \"ec0173c9-ac8d-449d-d11d-1d8e596b4ec9\"}]";
		JsonSkipLogic steve;
		JSONArray questions;
		Boolean runDisplayLogic = true;
		try {
			questions = new JSONArray(JsonQuestionsListString);
			steve = new JsonSkipLogic(questions, runDisplayLogic, getApplicationContext());
		} catch (JSONException e) {
			Log.e("Debug", "it dun gon wronge.");
			e.printStackTrace();
			throw new NullPointerException("it done gon wronge");
		}
		int i = 0;
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
		steve.getNextQuestion();
		Log.v("debug", "" + i); i++;
	}

	private static View s_view;
	public void resetAPP(View view){
		s_view = view;

		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if( which == DialogInterface.BUTTON_POSITIVE ) RESET(getApplicationContext());
			}
		};

		new AlertDialog.Builder(RunningBackgroundServiceActivity.mSelf)
				.setTitle("Warning")
				.setMessage("This will delete all files, unregister any study, reset and quit the APP. Are you sure?")
				.setPositiveButton("OK", dialogClickListener)
				.setNegativeButton("Cancel", dialogClickListener).show();
	}

	public static void RESET(Context context){
		if(AccessibilityListener.isEnabled(context))
			AccessibilityListener.mSelf.disableSelf();
		PersistentData.resetAPP(DebugInterfaceActivity.s_view);
		RunningBackgroundServiceActivity.mSelf.moveTaskToBack(true);
		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(0);
	}
}
