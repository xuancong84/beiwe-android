package org.beiwe.app.storage;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.beiwe.app.BuildConfig;
import org.beiwe.app.JSONUtils;
import org.beiwe.app.R;
import org.beiwe.app.ui.user.MainMenuActivity;
import org.json.JSONArray;
import org.json.JSONException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**A class for managing patient login sessions.
 * Uses SharedPreferences in order to save username-password combinations.
 * @author Dor Samet, Eli Jones, Josh Zagorsky, Wang Xuancong */
public class PersistentData {
	public static String NULL_ID = "NULLID";
	private static final long MAX_LONG = 9223372036854775807L;

	private static boolean isInitialized = false;
	private static Context appContext;

	public static SharedPreferences pref;
	public static Editor editor;
	public static boolean isUnregisterDebugMode = false;

	/**  Editor key-strings */
	private static final String PREF_NAME = "BeiwePref";
	private static final String SERVER_URL_KEY = "serverUrl";
	private static final String KEY_ID = "uid";
	private static final String KEY_PASSWORD = "password";
	private static final String IS_REGISTERED = "IsRegistered";
	private static final String LOGIN_EXPIRATION = "loginExpirationTimestamp";
	public static final String PCP_PHONE_NUMBER = "primary_care";
	public static final String PASSWORD_RESET_NUMBER_KEY = "reset_number";
	public static final String PHONE_NUMBER_LENGTH = "phone_number_length";
	public static final String USE_GPS_FUZZING = "use_gps_fuzzing";
	public static final String USE_ANONYMIZED_HASHING = "use_anonymized_hashing";
	public static final String WRITE_BUFFER_SIZE = "write_buffer_size";
	public static final String USE_COMPRESSION = "use_compression";

	public static final String ACCELEROMETER = "accelerometer";
	public static final String ACCESSIBILITY = "accessibility";
	public static final String AMBIENTLIGHT = "ambientlight";
	public static final String AMBIENTTEMPERATURE = "ambienttemperature";
	public static final String GYROSCOPE = "gyro";
	public static final String GPS = "gps";
	public static final String MAGNETOMETER = "magnetometer";
	public static final String CALLS = "calls";
	public static final String TEXTS = "texts";
	public static final String TAPS = "taps";
	public static final String USAGE = "usage";
	public static final String WIFI = "wifi";
	public static final String BLUETOOTH = "bluetooth";
	public static final String POWER_STATE = "power_state";
	public static final String ALLOW_UPLOAD_OVER_CELLULAR_DATA = "allow_upload_over_cellular_data";

	public static String [] feature_list = {
			ACCELEROMETER,
			ACCESSIBILITY,
			AMBIENTLIGHT,
			AMBIENTTEMPERATURE,
			GYROSCOPE,
			GPS,
			MAGNETOMETER,
			CALLS,
			TEXTS,
			TAPS,
			WIFI,
			USAGE,
			BLUETOOTH,
			POWER_STATE,
			ALLOW_UPLOAD_OVER_CELLULAR_DATA,
			USE_COMPRESSION
	};

	private static final String ACCELEROMETER_OFF_DURATION_SECONDS = "accelerometer_off_duration_seconds";
	private static final String ACCELEROMETER_ON_DURATION_SECONDS = "accelerometer_on_duration_seconds";
	private static final String AMBIENTLIGHT_INTERVAL_SECONDS = "ambientlight_interval_seconds";
	private static final String AMBIENTTEMPERATURE_INTERVAL_SECONDS = "ambienttemperature_interval_seconds";
	private static final String BLUETOOTH_ON_DURATION_SECONDS = "bluetooth_on_duration_seconds";
	private static final String BLUETOOTH_TOTAL_DURATION_SECONDS = "bluetooth_total_duration_seconds";
	private static final String BLUETOOTH_GLOBAL_OFFSET_SECONDS = "bluetooth_global_offset_seconds";
	private static final String CHECK_FOR_NEW_SURVEYS_FREQUENCY_SECONDS = "check_for_new_surveys_frequency_seconds";
	private static final String CREATE_NEW_DATA_FILES_FREQUENCY_SECONDS = "create_new_data_files_frequency_seconds";
	private static final String GPS_OFF_DURATION_SECONDS = "gps_off_duration_seconds";
	private static final String GPS_ON_DURATION_SECONDS = "gps_on_duration_seconds";
	private static final String GYRO_OFF_DURATION_SECONDS = "gyro_off_duration_seconds";
	private static final String GYRO_ON_DURATION_SECONDS = "gyro_on_duration_seconds";
	private static final String MAGNETOMETER_OFF_DURATION_SECONDS = "magnetometer_off_duration_seconds";
	private static final String MAGNETOMETER_ON_DURATION_SECONDS = "magnetometer_on_duration_seconds";
	private static final String SECONDS_BEFORE_AUTO_LOGOUT = "seconds_before_auto_logout";
	private static final String USAGE_UPDATE_INTERVAL_SECONDS = "usage_update_interval_seconds";
	private static final String UPLOAD_DATA_FILES_FREQUENCY_SECONDS = "upload_data_files_frequency_seconds";
	private static final String VOICE_RECORDING_MAX_TIME_LENGTH_SECONDS = "voice_recording_max_time_length_seconds";
	private static final String WIFI_LOG_FREQUENCY_SECONDS = "wifi_log_frequency_seconds";

	public static String [] time_param_list = {
			ACCELEROMETER_OFF_DURATION_SECONDS,
			ACCELEROMETER_ON_DURATION_SECONDS,
			AMBIENTLIGHT_INTERVAL_SECONDS,
			AMBIENTTEMPERATURE_INTERVAL_SECONDS,
			BLUETOOTH_ON_DURATION_SECONDS,
			BLUETOOTH_TOTAL_DURATION_SECONDS,
			BLUETOOTH_GLOBAL_OFFSET_SECONDS,
			CHECK_FOR_NEW_SURVEYS_FREQUENCY_SECONDS,
			CREATE_NEW_DATA_FILES_FREQUENCY_SECONDS,
			GPS_OFF_DURATION_SECONDS,
			GPS_ON_DURATION_SECONDS,
			GYRO_OFF_DURATION_SECONDS,
			GYRO_ON_DURATION_SECONDS,
			MAGNETOMETER_OFF_DURATION_SECONDS,
			MAGNETOMETER_ON_DURATION_SECONDS,
			SECONDS_BEFORE_AUTO_LOGOUT,
			USAGE_UPDATE_INTERVAL_SECONDS,
			UPLOAD_DATA_FILES_FREQUENCY_SECONDS,
			VOICE_RECORDING_MAX_TIME_LENGTH_SECONDS,
			WIFI_LOG_FREQUENCY_SECONDS,
	};

	private static final String SURVEY_IDS = "survey_ids";
//	private static final String SURVEY_QUESTION_IDS = "question_ids";

	/*#####################################################################################
	################################### Initializing ######################################
	#####################################################################################*/

	/**The publicly accessible initializing function for the LoginManager, initializes the internal variables.
	 * @param context */
	public static void initialize( Context context ) {
		if ( isInitialized ) { return; }
		appContext = context;
		pref = appContext.getSharedPreferences( PREF_NAME, Context.MODE_PRIVATE ); //sets Shared Preferences private mode
		editor = pref.edit();
		editor.commit();
		isInitialized = true;
	}

	public static void resetAPP(View view){
		TextFileManager.deleteEverything();
		appContext.deleteSharedPreferences(PREF_NAME);
		isInitialized = false;
	}

	/*#####################################################################################
	##################################### User State ######################################
	#####################################################################################*/

	/** Quick check for login. **/
	public static boolean isLoggedIn(){
		if ( pref == null ) return false;
		if ( getLong( SECONDS_BEFORE_AUTO_LOGOUT, 0 ) == 0 ) return true;
		// If the current time is earlier than the expiration time, return TRUE; else FALSE
		return (System.currentTimeMillis() < pref.getLong(LOGIN_EXPIRATION, 0)); }

	/** Set the login session to expire a fixed amount of time in the future */
	public static void loginOrRefreshLogin() {
		editor.putLong(LOGIN_EXPIRATION, System.currentTimeMillis() + getMillisecondsBeforeAutoLogout());
		editor.commit(); }

	/** Set the login session to "expired" */
	public static void logout() {
		editor.putLong(LOGIN_EXPIRATION, 0);
		editor.commit(); }

	/**Getter for the IS_REGISTERED value. */
	public static boolean isRegistered() {
		if (pref == null) return false;
		return pref.getBoolean(IS_REGISTERED, false); }

	/**Setter for the IS_REGISTERED value.
	 * @param value */
	public static void setRegistered(boolean value) { 
		editor.putBoolean(IS_REGISTERED, value);
		editor.commit(); }

	/*######################################################################################
	##################################### Passwords ########################################
	######################################################################################*/

	/**Checks that an input matches valid password requirements. (this only checks length)
	 * Throws up an alert notifying the user if the password is not valid.
	 * @param password
	 * @return true or false based on password requirements.*/
	public static boolean passwordMeetsRequirements(String password) {
		return (password.length() >= minPasswordLength());
	}

	public static int minPasswordLength() {
		if (BuildConfig.APP_IS_BETA) {
			return 1;
		} else {
			return 6;
		}
	}

 	/**Takes an input string and returns a boolean value stating whether the input matches the current password.
	 * @param input
	 * @param input
	 * @return */
	public static boolean checkPassword(String input){ return ( getPassword().equals( EncryptionEngine.safeHash(input) ) ); }

	/**Sets a password to a hash of the provided value.
	 * @param password */
	public static void setPassword(String password) {
		editor.putString(KEY_PASSWORD, EncryptionEngine.safeHash(password) );
		editor.commit();
	}

	
	public static boolean getEnabled(String feature){
		return isUnregisterDebugMode ? true : pref.getBoolean(feature, false);
	}
	public static void setEnabled(String feature, boolean enabled){
		if( !isUnregisterDebugMode ) {
			editor.putBoolean(feature, enabled);
			editor.commit();
		}
	}

	public static boolean getBoolean(String feature){ return pref.getBoolean(feature, false); }
	public static void setBoolean(String feature, boolean enabled){
		editor.putBoolean(feature, enabled);
		editor.commit();
	}

	/*#####################################################################################
	################################## Timer Settings #####################################
	#####################################################################################*/

	// Default timings (only used if app doesn't download custom timings)
	private static final long DEFAULT_ACCELEROMETER_OFF_DURATION = 10 * 60;
	private static final long DEFAULT_ACCELEROMETER_ON_DURATION = 10 * 60;
	private static final long DEFAULT_AMBIENTLIGHT_INTERVAL = 60;
	private static final long DEFAULT_AMBIENTTEMPERATURE_INTERVAL = 60;
	private static final long DEFAULT_BLUETOOTH_ON_DURATION = 1 * 60;
	private static final long DEFAULT_BLUETOOTH_TOTAL_DURATION = 5 * 60;
	private static final long DEFAULT_BLUETOOTH_GLOBAL_OFFSET = 0 * 60;
	private static final long DEFAULT_CHECK_FOR_NEW_SURVEYS_PERIOD = 24 * 60 * 60;
	private static final long DEFAULT_CREATE_NEW_DATA_FILES_PERIOD = 15 * 60;
	private static final long DEFAULT_GPS_OFF_DURATION = 5 * 60;
	private static final long DEFAULT_GPS_ON_DURATION = 5 * 60;
	private static final long DEFAULT_GYRO_OFF_DURATION = 10 * 60;
	private static final long DEFAULT_GYRO_ON_DURATION = 10 * 60;
	private static final long DEFAULT_MAGNETOMETER_OFF_DURATION = 10 * 60;
	private static final long DEFAULT_MAGNETOMETER_ON_DURATION = 10 * 60;
	private static final long DEFAULT_USAGE_UPDATE_INTERVAL_SECONDS = 30 * 60;
	private static final long DEFAULT_SECONDS_BEFORE_AUTO_LOGOUT = 5 * 60;
	private static final long DEFAULT_UPLOAD_DATA_FILES_PERIOD = 60;
	private static final long DEFAULT_VOICE_RECORDING_MAX_TIME_LENGTH = 4 * 60;
	private static final long DEFAULT_WIFI_LOG_FREQUENCY = 5 * 60;
	
	public static long getAccelerometerOffDurationMilliseconds() { return 1000L * pref.getLong(ACCELEROMETER_OFF_DURATION_SECONDS, DEFAULT_ACCELEROMETER_OFF_DURATION); }
	public static long getAccelerometerOnDurationMilliseconds() { return 1000L * pref.getLong(ACCELEROMETER_ON_DURATION_SECONDS, DEFAULT_ACCELEROMETER_ON_DURATION); }
	public static long getAmbientLightIntervalMilliseconds() { return 1000L * pref.getLong(AMBIENTLIGHT_INTERVAL_SECONDS, DEFAULT_AMBIENTLIGHT_INTERVAL); }
	public static long getAmbientTemperatureIntervalMilliseconds() { return 1000L * pref.getLong(AMBIENTTEMPERATURE_INTERVAL_SECONDS, DEFAULT_AMBIENTTEMPERATURE_INTERVAL); }
	public static long getBluetoothOnDurationMilliseconds() { return 1000L * pref.getLong(BLUETOOTH_ON_DURATION_SECONDS, DEFAULT_BLUETOOTH_ON_DURATION); }
	public static long getBluetoothTotalDurationMilliseconds() { return 1000L * pref.getLong(BLUETOOTH_TOTAL_DURATION_SECONDS, DEFAULT_BLUETOOTH_TOTAL_DURATION); }
	public static long getBluetoothGlobalOffsetMilliseconds() { return 1000L * pref.getLong(BLUETOOTH_GLOBAL_OFFSET_SECONDS, DEFAULT_BLUETOOTH_GLOBAL_OFFSET); }
	public static long getCheckForNewSurveysFrequencyMilliseconds() { return 1000L * pref.getLong(CHECK_FOR_NEW_SURVEYS_FREQUENCY_SECONDS, DEFAULT_CHECK_FOR_NEW_SURVEYS_PERIOD); }
	public static long getCreateNewDataFilesFrequencyMilliseconds() { return 1000L * pref.getLong(CREATE_NEW_DATA_FILES_FREQUENCY_SECONDS, DEFAULT_CREATE_NEW_DATA_FILES_PERIOD); }
	public static long getGpsOffDurationMilliseconds() { return 1000L * pref.getLong(GPS_OFF_DURATION_SECONDS, DEFAULT_GPS_OFF_DURATION); }
	public static long getGpsOnDurationMilliseconds() { return 1000L * pref.getLong(GPS_ON_DURATION_SECONDS, DEFAULT_GPS_ON_DURATION); }
	public static long getGyroOffDurationMilliseconds() { return 1000L * pref.getLong(GYRO_OFF_DURATION_SECONDS, DEFAULT_GYRO_OFF_DURATION); }
	public static long getGyroOnDurationMilliseconds() { return 1000L * pref.getLong(GYRO_ON_DURATION_SECONDS, DEFAULT_GYRO_ON_DURATION); }
	public static long getMagnetometerOffDurationMilliseconds() { return 1000L * pref.getLong(MAGNETOMETER_OFF_DURATION_SECONDS, DEFAULT_MAGNETOMETER_OFF_DURATION); }
	public static long getMagnetometerOnDurationMilliseconds() { return 1000L * pref.getLong(MAGNETOMETER_ON_DURATION_SECONDS, DEFAULT_MAGNETOMETER_ON_DURATION); }
	public static long getMillisecondsBeforeAutoLogout() { return 1000L * pref.getLong(SECONDS_BEFORE_AUTO_LOGOUT, DEFAULT_SECONDS_BEFORE_AUTO_LOGOUT); }
	public static long getUsageUpdateIntervalMilliseconds() { return 1000L * pref.getLong(USAGE_UPDATE_INTERVAL_SECONDS, DEFAULT_USAGE_UPDATE_INTERVAL_SECONDS); }
	public static long getUploadDataFilesFrequencyMilliseconds() { return 1000L * pref.getLong(UPLOAD_DATA_FILES_FREQUENCY_SECONDS, DEFAULT_UPLOAD_DATA_FILES_PERIOD); }
	public static long getVoiceRecordingMaxTimeLengthMilliseconds() { return 1000L * pref.getLong(VOICE_RECORDING_MAX_TIME_LENGTH_SECONDS, DEFAULT_VOICE_RECORDING_MAX_TIME_LENGTH); }
	public static long getWifiLogFrequencyMilliseconds() { return 1000L * pref.getLong(WIFI_LOG_FREQUENCY_SECONDS, DEFAULT_WIFI_LOG_FREQUENCY); }

	public static String getString(String feature){ return pref.getString(feature,""); }
	public static void setString(String feature, String value){
		editor.putString(feature, value);
		editor.commit();
	}

	public static int getInteger(String feature, int default_v){ return pref.getInt( feature, default_v ); }
	public static void setInteger(String feature, int value){
		editor.putInt(feature, value);
		editor.commit();
	}

	public static long getLong(String feature, long default_v){ return pref.getLong( feature, default_v ); }
	public static void setLong(String feature, long value){
		editor.putLong(feature, value);
		editor.commit();
	}

	//accelerometer, bluetooth, new surveys, create data files, gps, logout,upload, wifilog (not voice recording, that doesn't apply
	public static void setMostRecentAlarmTime(String identifier, long time) {
		editor.putLong(identifier + "-prior_alarm", time);
		editor.commit(); }
	public static long getMostRecentAlarmTime(String identifier) { return pref.getLong( identifier + "-prior_alarm", 0); }
	//we want default to be 0 so that checks "is this value less than the current expected value" (eg "did this timer event pass already")

	/*###########################################################################################
	################################### Text Strings ############################################
	###########################################################################################*/

	public static final String ABOUT_PAGE_TEXT_KEY = "about_page_text";
	public static final String CALL_CLINICIAN_BUTTON_TEXT_KEY = "call_clinician_button_text";
	public static final String CONSENT_FORM_TEXT_KEY = "consent_form_text";
	public static final String SURVEY_SUBMIT_SUCCESS_TOAST_TEXT_KEY = "survey_submit_success_toast_text";
	public static final String MAIN_UPLOAD_INFO_TEXT_KEY = "main_upload_info_text";
	public static final String LASY_UPLOAD_TIME_KEY = "last_upload_time";
	
	public static String getAboutPageText() {
		String defaultText = appContext.getString(R.string.default_about_page_text);
		return pref.getString(ABOUT_PAGE_TEXT_KEY, defaultText); }
	public static String getCallClinicianButtonText() {
		String defaultText = appContext.getString(R.string.default_call_clinician_text);
		return pref.getString(CALL_CLINICIAN_BUTTON_TEXT_KEY, defaultText); }
	public static String getConsentFormText() {
		String defaultText = appContext.getString(R.string.default_consent_form_text);
		return pref.getString(CONSENT_FORM_TEXT_KEY, defaultText); }
	public static String getSurveySubmitSuccessToastText() {
		String defaultText = appContext.getString(R.string.default_survey_submit_success_message);
		return pref.getString(SURVEY_SUBMIT_SUCCESS_TOAST_TEXT_KEY, defaultText); }

	public static String getMainUploadInfo() {
		return pref.getString(MAIN_UPLOAD_INFO_TEXT_KEY, ""); }
	public static void setMainUploadInfo(String text) {
		editor.putString(MAIN_UPLOAD_INFO_TEXT_KEY, text);
		editor.commit();
		try {
			MainMenuActivity.mSelf.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					((TextView)MainMenuActivity.mSelf.findViewById(R.id.main_last_upload)).setText(text);
				}
			});
		} catch (Exception e){}
	}

	/*###########################################################################################
	################################### User Credentials ########################################
	###########################################################################################*/

	public static void setServerUrl(String serverUrl) {
		if (!serverUrl.startsWith("http"))
			serverUrl = "https://" + serverUrl;
		editor.putString(SERVER_URL_KEY, serverUrl);
		editor.commit();
	}
	public static String getServerUrl() { return pref.getString(SERVER_URL_KEY, null); }

	public static void setLoginCredentials( String userID, String password ) {
		if (editor == null) Log.e("LoginManager.java", "editor is null in setLoginCredentials()");
		editor.putString(KEY_ID, userID);
		setPassword(password);
		editor.commit(); }

	public static String getPassword() { return pref.getString( KEY_PASSWORD, null ); }
	public static String getPatientID() { return pref.getString(KEY_ID, NULL_ID); }

	/*###########################################################################################
	###################################### Survey Info ##########################################
	###########################################################################################*/
	
	public static List<String> getSurveyIds() { return JSONUtils.jsonArrayToStringList(getSurveyIdsJsonArray()); }
	public static List<String> getSurveyQuestionMemory(String surveyId) { return JSONUtils.jsonArrayToStringList(getSurveyQuestionMemoryJsonArray(surveyId)); }
	public static String getSurveyTimes(String surveyId){ return pref.getString(surveyId + "-times", null); }
	public static String getSurveyContent(String surveyId){ return pref.getString(surveyId + "-content", null); }
	public static String getSurveyType(String surveyId){ return pref.getString(surveyId + "-type", null); }
	public static String getSurveySettings(String surveyId){ return pref.getString(surveyId + "-settings", null); }
	public static Boolean getSurveyNotificationState( String surveyId) { return pref.getBoolean(surveyId + "-notificationState", false ); }
	public static long getMostRecentSurveyAlarmTime(String surveyId) { return pref.getLong( surveyId + "-prior_alarm", MAX_LONG); }
	
	public static void createSurveyData(String surveyId, String content, String timings, String type, String settings){
		setSurveyContent(surveyId,  content);
		setSurveyTimes(surveyId, timings);
		setSurveyType(surveyId, type);
		setSurveySettings(surveyId, settings);
	}
	//individual setters
	public static void setSurveyContent(String surveyId, String content){
		editor.putString(surveyId + "-content", content);
		editor.commit(); }
	public static void setSurveyTimes(String surveyId, String times){
		editor.putString(surveyId + "-times", times);
		editor.commit(); }
	public static void setSurveyType(String surveyId, String type){
		editor.putString(surveyId + "-type", type);
		editor.commit(); }
	public static void setSurveySettings(String surveyId, String settings){
//		Log.d("presistent data", "setting survey settings: " + settings);
		editor.putString(surveyId + "-settings", settings);
		editor.commit();
	}
	
	//survey state storage
	public static void setSurveyNotificationState(String surveyId, Boolean bool ) {
		editor.putBoolean(surveyId + "-notificationState", bool );
		editor.commit(); }
	public static void setMostRecentSurveyAlarmTime(String surveyId, long time) {
		editor.putLong(surveyId + "-prior_alarm", time);
		editor.commit(); }
	
	
	public static void deleteSurvey(String surveyId) {
		editor.remove(surveyId + "-content");
		editor.remove(surveyId + "-times");
		editor.remove(surveyId + "-type");
		editor.remove(surveyId + "-notificationState");
		editor.remove(surveyId + "-settings");
		editor.remove(surveyId + "-questionIds");
		editor.commit();
		removeSurveyId(surveyId);
	}
	
	//array style storage and removal for surveyIds and questionIds	
	private static JSONArray getSurveyIdsJsonArray() {
		String jsonString = pref.getString(SURVEY_IDS, "0");
		// Log.d("persistant data", "getting ids: " + jsonString);
		if (jsonString == "0") { return new JSONArray(); } //return empty if the list is empty
		try { return new JSONArray(jsonString); }
		catch (JSONException e) { throw new NullPointerException("getSurveyIds failed, json string was: " + jsonString ); }
	}
		
	public static void addSurveyId(String surveyId) {
		List<String> list = JSONUtils.jsonArrayToStringList( getSurveyIdsJsonArray() );
		if ( !list.contains(surveyId) ) {
			list.add(surveyId);
			editor.putString(SURVEY_IDS, new JSONArray(list).toString() );
			editor.commit();
		}
		else { throw new NullPointerException("duplicate survey id added: " + surveyId); } //we ensure uniqueness in the downloader, this should be unreachable.
	}
	
	private static void removeSurveyId(String surveyId) {
		List<String> list = JSONUtils.jsonArrayToStringList( getSurveyIdsJsonArray() );
		if ( list.contains(surveyId) ) {
			list.remove(surveyId);
			editor.putString(SURVEY_IDS, new JSONArray(list).toString() );
			editor.commit();
		}
		else { throw new NullPointerException("survey id does not exist: " + surveyId); } //we ensure uniqueness in the downloader, this should be unreachable.
	}
	

	private static JSONArray getSurveyQuestionMemoryJsonArray( String surveyId ) {
		String jsonString = pref.getString(surveyId + "-questionIds", "0");
		if (jsonString == "0") { return new JSONArray(); } //return empty if the list is empty
		try { return new JSONArray(jsonString); }
		catch (JSONException e) { throw new NullPointerException("getSurveyIds failed, json string was: " + jsonString ); }
	}
		
	public static void addSurveyQuestionMemory(String surveyId, String questionId) {
		List<String> list = getSurveyQuestionMemory(surveyId);
		// Log.d("persistent data", "adding questionId: " + questionId);
		if ( !list.contains(questionId) ) {
			list.add(questionId);
			editor.putString(surveyId + "-questionIds", new JSONArray(list).toString() );
			editor.commit();
		}
		else { throw new NullPointerException("duplicate question id added: " + questionId); } //we ensure uniqueness in the downloader, this should be unreachable.
	}
	
	public static void clearSurveyQuestionMemory(String surveyId) {
		editor.putString(surveyId + "-questionIds", new JSONArray().toString() );
		editor.commit();
	}

	/*###########################################################################################
	###################################### Encryption ###########################################
	###########################################################################################*/

	private static final String HASH_SALT_KEY = "hash_salt_key";
	private static final String HASH_ITERATIONS_KEY = "hash_iterations_key";
	private static final String USE_ANONYMIZED_HASHING_KEY = "use_anonymized_hashing";

	// Get salt for pbkdf2 hashing
	public static byte[] getHashSalt() {
		String saltString = pref.getString(HASH_SALT_KEY, null);
		if(saltString == null) { // create salt if it does not exist
			saltString = new String(SecureRandom.getSeed(64));
			editor.putString(HASH_SALT_KEY, saltString);
			editor.commit();
		}
		return saltString.getBytes();
	}

	// Get iterations for pbkdf2 hashing
	public static int getHashIterations() {
		int iterations = pref.getInt(HASH_ITERATIONS_KEY, 0);
		if(iterations == 0) { // create iterations if it does not exist
			// create random iteration count from 100 to 1000
			iterations = 100 + new Random().nextInt(900);
			editor.putInt(HASH_ITERATIONS_KEY, iterations);
			editor.commit();
		}
		return iterations;
	}

	/*###########################################################################################
	###################################### FUZZY GPS ############################################
	###########################################################################################*/

	private static final String LATITUDE_OFFSET_KEY = "latitude_offset_key";
	private static final String LONGITUDE_OFFSET_KEY = "longitude_offset_key";

	public static double getLatitudeOffset() {
		if(!getBoolean(USE_GPS_FUZZING)) return 0;
		float latitudeOffset = pref.getFloat(LATITUDE_OFFSET_KEY, 0.0f);
		if( latitudeOffset == 0.0f ) { //create latitude offset if it does not exist
			latitudeOffset = (float)(Math.random()*1000.0-500.0); // create random latitude offset between (-1, -.2) or (.2, 1)
			editor.putFloat(LATITUDE_OFFSET_KEY, latitudeOffset);
			editor.commit();
		}
		return latitudeOffset;
	}

	public static float getLongitudeOffset() {
		if(!getBoolean(USE_GPS_FUZZING)) return 0;
		float longitudeOffset = pref.getFloat(LONGITUDE_OFFSET_KEY, 0.0f);
		if( longitudeOffset == 0.0f ) { //create longitude offset if it does not exist
			longitudeOffset = (float)(Math.random()*1000.0-500.0); // create random longitude offset between (-180, -10) or (10, 180)
			editor.putFloat(LONGITUDE_OFFSET_KEY, longitudeOffset);
			editor.commit();
		}
		return longitudeOffset;
	}
}
