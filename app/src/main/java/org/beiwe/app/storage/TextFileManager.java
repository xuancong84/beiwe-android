package org.beiwe.app.storage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.beiwe.app.BuildConfig;
import org.beiwe.app.CrashHandler;
import org.beiwe.app.listeners.*;
import org.beiwe.app.survey.AudioRecorderActivity;
import org.beiwe.app.survey.AudioRecorderEnhancedActivity;
import org.beiwe.app.survey.SurveyAnswersRecorder;
import org.beiwe.app.survey.SurveyTimingsRecorder;
import org.beiwe.app.ui.DebugInterfaceActivity;

import android.content.Context;
import android.util.Log;

/**The (Text)FileManager.
 * The FileManager is implemented as a Singleton.  More accurately the static object contains several
 * singletons, static instances of FileManager Objects.  Before using the FileManager the app must
 * provide it with a Context and call the Start() function.  Failure to do so causes the app to crash.
 * This Is Intentional.  The Point of the app is to record data.
 * The Reason for this construction is to construct a file write system where there is only ever a
 * single pointer to each file type, and that these files are never overwritten, written to asynchronously,
 * or left accidentally empty.
 * The files handled here are the GPSFile, accelFile, powerStateLog, audioSurveyInfo, callLog, textsLog, surveyTimings,
 * currentDailyQuestions, currentWeeklyQuestions, deviceData, and debugLogFile.
 * On construction you provide a boolean flag ("persistent").  Persistent files do not get overwritten on application start.
 * To access a file use the following construction: TextFileManager.getXXXFile()
 * @author Eli */
public class TextFileManager { 

	//Delimiter and newline strings
	public static final String DELIMITER = ",";
	
	//Static instances of the individual FileManager objects.
	private static TextFileManager GPSFile;
	private static TextFileManager accelFile;
	private static TextFileManager accessibilityLog;
	private static TextFileManager ambientLightFile;
	private static TextFileManager ambientTemperatureFile;
	private static TextFileManager gyroFile;
	private static TextFileManager stepsFile;
	private static TextFileManager magnetoFile;
	private static TextFileManager powerStateLog;
	private static TextFileManager callLog;
	private static TextFileManager tapsLog;
	private static TextFileManager textsLog;
	private static TextFileManager usageFile;
	private static TextFileManager bluetoothLog;
	private static TextFileManager wifiLog;
	private static TextFileManager debugLogFile;

	private static TextFileManager surveyTimings;
	private static TextFileManager surveyAnswers;

	private static TextFileManager keyFile;
	
	//"global" static variables
	public static int write_buffer_size = 0;
	private static Context appContext;
	private static int GETTER_TIMEOUT = 50; //value is in milliseconds
	private static String getter_error = "Tried to access %s before calling TextFileManager.start().";
	private static String broken_getter_error = "Tried to access %s before calling TextFileManager.start(), but the timeout failed.";
	private static void throwGetterError(String sourceName) { throw new NullPointerException( String.format(getter_error, sourceName) ); }
	private static void throwTimeoutBrokeGetterError(String sourceName) { throw new NullPointerException( String.format(broken_getter_error, sourceName) ); }
	
	//public static getters.
	// These are all simple and nearly identical, so they are squished into one-liners.
	// checkAvailableWithTimeout throws an error and the app restarts if the TextFile is unavailable.
	public static TextFileManager getAccelFile() { checkAvailableWithTimeout(AccelerometerListener.name); return accelFile; }
	public static TextFileManager getAccessibilityLogFile() { checkAvailableWithTimeout(AccessibilityListener.name); return accessibilityLog; }
	public static TextFileManager getAmbientLightFile() { checkAvailableWithTimeout(AmbientLightListener.name); return ambientLightFile; }
	public static TextFileManager getAmbientTemperatureFile() { checkAvailableWithTimeout(AmbientTemperatureListener.name); return ambientTemperatureFile; }
	public static TextFileManager getGyroFile() { checkAvailableWithTimeout(GyroscopeListener.name); return gyroFile; }
	public static TextFileManager getMagnetoFile() { checkAvailableWithTimeout(MagnetoListener.name); return magnetoFile; }
	public static TextFileManager getStepsFile() { checkAvailableWithTimeout(StepsListener.name); return stepsFile; }
	public static TextFileManager getGPSFile() { checkAvailableWithTimeout(GPSListener.name); return GPSFile; }
	public static TextFileManager getPowerStateFile() { checkAvailableWithTimeout(PowerStateListener.name); return powerStateLog; }
	public static TextFileManager getCallLogFile() { checkAvailableWithTimeout(CallLogger.name); return callLog; }
	public static TextFileManager getTextsLogFile() { checkAvailableWithTimeout(SmsSentLogger.name); return textsLog; }
	public static TextFileManager getTapsLogFile() { checkAvailableWithTimeout(TapsListener.name); return tapsLog; }
	public static TextFileManager getBluetoothLogFile() { checkAvailableWithTimeout(BluetoothListener.name); return bluetoothLog; }
	public static TextFileManager getWifiLogFile() { checkAvailableWithTimeout(WifiListener.name); return wifiLog; }
	public static TextFileManager getUsageFile() { checkAvailableWithTimeout(UsageListener.name); return usageFile; }
	public static TextFileManager getSurveyTimingsFile() { checkAvailableWithTimeout(SurveyTimingsRecorder.name); return surveyTimings; }
	public static TextFileManager getSurveyAnswersFile() { checkAvailableWithTimeout(SurveyAnswersRecorder.name); return surveyAnswers; }
	//(the persistent files)
	public static TextFileManager getDebugLogFile() { checkAvailableWithTimeout(DebugInterfaceActivity.LogFile.name); return debugLogFile; }
	public static TextFileManager getKeyFile() { checkAvailableWithTimeout("keyFile"); return keyFile; }
	
	/** Checks the availability of a given TextFile, returns true if available, false otherwise. */
	private static Boolean checkTextFileAvailable(String thing) {
		//the check for availability is whether the appropriate variable is allocated
		switch (thing){
			case AccelerometerListener.name: return (accelFile != null);
			case AccessibilityListener.name: return (accessibilityLog != null);
			case AmbientLightListener.name: return (ambientLightFile != null);
			case AmbientTemperatureListener.name: return (ambientTemperatureFile != null);
			case GyroscopeListener.name: return (gyroFile != null);
			case MagnetoListener.name: return (magnetoFile != null);
			case StepsListener.name: return (stepsFile != null);
			case GPSListener.name: return (GPSFile != null);
			case PowerStateListener.name: return (powerStateLog != null);
			case CallLogger.name: return (callLog != null);
			case SmsSentLogger.name: return (textsLog != null);
			case TapsListener.name: return (tapsLog != null);
			case BluetoothListener.name: return (bluetoothLog != null);
			case UsageListener.name: return (usageFile != null);
			case WifiListener.name: return (wifiLog != null);
			case SurveyTimingsRecorder.name: return (surveyTimings != null);
			case SurveyAnswersRecorder.name: return (surveyAnswers != null);
			case DebugInterfaceActivity.LogFile.name: return (debugLogFile != null);
			case "keyFile": return (keyFile != null);
			default: throw new NullPointerException(String.format("invalid key %s provided for checking available text file.", thing));
		}
	}
	
	/** We check for the availability of the given TextFile, if it fails to exist we wait GETTER_TIMEOUT milliseconds and then try again. 
	 * On a regular case error we throw the getter error, if the sleep operation breaks we throw the broken timeout error. */
	private static void checkAvailableWithTimeout(String textFile) {
		if ( !checkTextFileAvailable(textFile) ) {
			try {
				// The Background Service should be getting restarted as we speak
				for(int x = 0; x < 40; x++) {
					// previously, flat 75 ms. Now 50ms over 40 iterations. If this still fails then we have bigger problems
					Thread.sleep(GETTER_TIMEOUT);

					// From the documentation
					// No response to an input event (such as key press or screen touch events) within 5 seconds.
					// A BroadcastReceiver hasn't finished executing within 10 seconds.
					// https://developer.android.com/training/articles/perf-anr
					// As of: 2018-04-25
					if ( checkTextFileAvailable(textFile) ) return;
				}
				throwGetterError(textFile);
			}
			catch (InterruptedException e) { throwTimeoutBrokeGetterError(textFile); }
		}
	}
	
	//and (finally) the non-static object instance variables
	public String name = null;
	public String fileName = null;
	private String header = null;
	private String buffer = "";
	private Boolean persistent = null;
	private Boolean encrypted = null;
	private Boolean isDummy = true;
	private byte[] AESKey = null;
	
	/*###############################################################################
	########################### Class Initialization ################################
	###############################################################################*/
	
	/**Starts the TextFileManager
	 * This must be called before code attempts to access files using getXXXFile().
	 * Initializes all TextFileManager object instances.  Initialization is idempotent.
	 * @param appContext a Context, provided by the app. */
	public static synchronized void initialize(Context appContext){
		//the key file for encryption (it is persistent and never written to)
		keyFile = new TextFileManager(appContext, "keyFile", "", true, true, false, false);
		// Persistent files (old, no longer used, but this is an example of a persistent file (one that does not get abandoned at shut-down/initialization) )
//		currentDailyQuestions = new TextFileManager(appContext, "currentDailyQuestionsFile.json", EMPTY_HEADER, true, true, false);
//		currentWeeklyQuestions = new TextFileManager(appContext, "currentWeeklyQuestionsFile.json", EMPTY_HEADER, true, true, false);
		// The debug file is no longer persistent, so that we can upload it to the server associated with a user, otherwise it has the name "logfile.txt" and fails to upload.
		debugLogFile = new TextFileManager(appContext, DebugInterfaceActivity.LogFile.name, DebugInterfaceActivity.LogFile.header, false, false, true, false);
		// Regularly/periodically-created files
		GPSFile = new TextFileManager(appContext, GPSListener.name, GPSListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.GPS));
		accelFile = new TextFileManager(appContext, AccelerometerListener.name, AccelerometerListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.ACCELEROMETER));
		accessibilityLog = new TextFileManager(appContext, AccessibilityListener.name, AccessibilityListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.ACCESSIBILITY));
		ambientLightFile = new TextFileManager(appContext, AmbientLightListener.name, AmbientLightListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.AMBIENTLIGHT));
		ambientTemperatureFile = new TextFileManager(appContext, AmbientTemperatureListener.name, AmbientTemperatureListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.AMBIENTTEMPERATURE));
		gyroFile = new TextFileManager(appContext, GyroscopeListener.name, GyroscopeListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.GYROSCOPE));
		magnetoFile = new TextFileManager(appContext, MagnetoListener.name, MagnetoListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.MAGNETOMETER));
		stepsFile = new TextFileManager(appContext, StepsListener.name, StepsListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.STEPS));
		tapsLog = new TextFileManager(appContext, TapsListener.name, TapsListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.TAPS));
		textsLog = new TextFileManager(appContext, SmsSentLogger.name, SmsSentLogger.header, false, false, true, !PersistentData.getEnabled(PersistentData.TEXTS));
		callLog = new TextFileManager(appContext, CallLogger.name, CallLogger.header, false, false, true, !PersistentData.getEnabled(PersistentData.CALLS));
		powerStateLog = new TextFileManager(appContext, PowerStateListener.name, PowerStateListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.POWER_STATE));
		usageFile = new TextFileManager(appContext, UsageListener.name, UsageListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.USAGE));
		bluetoothLog = new TextFileManager(appContext, BluetoothListener.name, BluetoothListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.BLUETOOTH));
		// Files created on specific events/written to in one go.
		surveyTimings = new TextFileManager(appContext, SurveyTimingsRecorder.name, SurveyTimingsRecorder.header, false, false, true, false);
		surveyAnswers = new TextFileManager(appContext, SurveyAnswersRecorder.name, SurveyAnswersRecorder.header, false, false, true, false);
		wifiLog = new TextFileManager(appContext, WifiListener.name, WifiListener.header, false, false, true, !PersistentData.getEnabled(PersistentData.WIFI));
	}
	
	/*###############################################################################
	################## Instance Construction and Initialization #####################
	###############################################################################*/
	
	/** This class has a PRIVATE constructor.  The constructor is only ever called 
	 * internally, via the static initialize() function, it creatse the "FileHandlers" used throughout the codebase. 
	 * @param appContext A Context.
	 * @param name The file's name.
	 * @param header The first line of the file.  Leave empty if you don't want a header, remember to include a new line at the end of the header.
	 * @param persistent Set this to true for a persistent file.  Persistent files are not currently encryptable.
	 * @param openOnInstantiation This boolean value dictates whether the file should be opened, mostly this is used in conjunction persistent files so that they can be read from.
	 * @param encrypted Set this to True if the file will have encrypted writes. */
	private TextFileManager(Context appContext, String name, String header, Boolean persistent, Boolean openOnInstantiation, Boolean encrypted, Boolean isDummy ){
		TextFileManager.appContext = appContext;
		if ( persistent && encrypted ) { throw new NullPointerException("Persistent files do not support encryption."); }
		this.name = name;
		this.header = header;
		this.persistent = persistent;
		this.encrypted = encrypted;
		this.isDummy = isDummy;
		//if (isDummy) { Log.e("TextFileManager", "creating dummy handle for " + this.name); }
		if (openOnInstantiation) { this.newFile(); } //immediately creating a file on instantiation was a common code pattern.
	}
	
	/** Makes a new file.
	 * Persistent files do not get a time stamp.
	 * Encrypted files get a key and have the key encrypted using RSA and written as the first line of the file.
	 * If a file has a header it is written as the second line.
	 * Fails when files are not allowed to be written to. (the rule is no encrypted writes until registraction is complete.
	 * @return A boolean value of whether a new file has been created.*/
	public synchronized boolean newFile(){
		if ( this==null || this.isDummy ) { return false; }

		// flush buffer if it is non-empty
		if ( !buffer.isEmpty() ){
			writeEncrypted(null );	 // force a flush
			buffer = "";
		}

		// handle the naming cases for persistent vs. non-persistent files
		if ( this.persistent ) { this.fileName = this.name; }
		else { // if user has not registered, stop non-persistent file generation
			if ( !PersistentData.isRegistered() ) { return false; }
			this.fileName = PersistentData.getPatientID() + "_" + this.name + "_" + System.currentTimeMillis() + ".csv";
		}

		try {
			// write the key to the file (if it has one)
			if (this.encrypted) {
				this.AESKey = EncryptionEngine.newAESKey();
				this.unsafeWritePlaintext(EncryptionEngine.encryptRSA(this.AESKey));
			}
			// write the csv header, if the file has a header
			if (header != null && header.length() > 0) {
				// We will not call writeEncrypted here because we need to handle the specific case of the new file not being created properly.
				this.unsafeWritePlaintext(EncryptionEngine.encryptAES(header, this.AESKey));
			}
		} catch (FileNotFoundException e) {
			Log.e("TextFileManager", "could not find file to write to, " + this.fileName);
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext);
			this.fileName = null;  // Set filename null so that the system tries to create the file again later
			return false;
		} catch (IOException e) {
			if(e.getMessage().toLowerCase().contains("enospc")) { // If the device is out of storage, alert the user
				Log.e("ENOSPC", "Out of storage space");
			}
			Log.e("TextFileManager", "error in the write operation: " + e.getMessage() );
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext);
			this.fileName = null;
			return false;
		} catch (InvalidKeyException e) {
			Log.e("TextFileManager", "encrypted write operation without an AES key: " + this.name + ", " + this.fileName);
			CrashHandler.writeCrashlog(e, appContext);
			this.fileName = null;
			return false;
		} catch (InvalidKeySpecException e) { //this occurs when an encrypted write operation occurs without an RSA key file, we eat this error because it only happens during registration/initial config.
			Log.e("TextFileManager", "EncryptionEngine.AES_TOO_EARLY_ERROR: " + this.name + ", " + header);
			e.printStackTrace();
			this.fileName = null;
			return false;
		}
		return true;
	}
	
	/** If it's a SurveyAnswers or SurveyTimings file, we want to append the
	 * Survey ID so that the file name reads like this:
	 * [USERID]_SurveyAnswers[SURVEYID]_[TIMESTAMP].csv
	 * @param surveyId */
	// does not require dummy check, just setting attributes on the in-memory variable
	public synchronized void newFile(String surveyId) {
		String nameHolder = this.name;
		this.name += surveyId;
		newFile(); //We do not care about return value, it is only used for handling encrypted files.
		this.name = nameHolder;
	}
	
	/*###############################################################################
	########################## Read and Write Operations ############################
	###############################################################################*/
	
	/** Takes a string. writes that to the file, adds a new line to the string.
	 * Prints a stacktrace on a write error, but does not crash. If there is no
	 * file, a new file will be created.
	 * @param data any unicode valid string*/
	private synchronized void unsafeWritePlaintext(String data) throws FileNotFoundException, IOException{
		FileOutputStream outStream;
		//write the output, we always want mode append
		outStream = appContext.openFileOutput(this.fileName, Context.MODE_APPEND);
		outStream.write( ( data ).getBytes() );
		outStream.write( "\n".getBytes() );
		outStream.flush();
		outStream.close();
	}

	public synchronized void safeWritePlaintext(String data) {
		if (this.isDummy) { return; }
		if (fileName == null) this.newFile();
		try {
			unsafeWritePlaintext(data);
		} catch (FileNotFoundException e) {
			Log.e("TextFileManager", "could not find file to write to, " + this.fileName);
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext);
		} catch (IOException e) {
			if(e.getMessage().toLowerCase().contains("enospc")) { // If the device is out of storage, alert the user
				Log.e("ENOSPC", "Out of storage space");
			}
			Log.e("TextFileManager", "error in the write operation: " + e.getMessage() );
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext);
		}
	}

	/**Encrypts string data and writes it to a file.
	 * @param data any unicode valid string (set to null to force flush) */
	public synchronized void writeEncrypted( String data ) {
		if ( this == null ) return;
		if ( data != null ) DebugInterfaceActivity.smartLog( name, data );
		if ( this.isDummy ) return;
		if ( !this.encrypted ) throw new NullPointerException( this.name + "is not supposed to have encrypted writes!" );

		if ( data == null ){	// if data is null, force flush
			if ( buffer.isEmpty() )
				return;
			data = buffer;
			buffer = "";
		} else if ( write_buffer_size > 0 ) {	// use buffered write to save phone CPU
			if( buffer.isEmpty() )
				buffer = data;
			else
				buffer += ("\n" + data);
			if( buffer.length() >= write_buffer_size ){
				data = buffer;
				buffer = "";
			} else return;
		}

		if ( fileName == null && !this.newFile() ) //when newFile fails we are not allowed to write to files.
			return;

		try {
			this.safeWritePlaintext( EncryptionEngine.encryptAES( data, this.AESKey ) );
		} catch (InvalidKeyException e) {
			Log.e("TextFileManager", "encrypted write operation without an AES key: " + this.name + ", " + this.fileName);
			CrashHandler.writeCrashlog(e, appContext);
		} catch (InvalidKeySpecException e) { //this occurs when an encrypted write operation occurs without an RSA key file, we eat this error because it only happens during registration/initial config.
			Log.e("TextFileManager", "EncryptionEngine.AES_TOO_EARLY_ERROR: " + this.name + ", " + data);
			e.printStackTrace(); }
	}
	
	/**@return A string of the file contents. */
	public synchronized String read() {
		if (this.isDummy) { return this.name + " is a dummy file."; }
		BufferedInputStream bufferedInputStream;
		StringBuffer stringBuffer = new StringBuffer();
		int data;
		try {  // Read through the (buffered) input stream, append to a stringbuffer.  Catch exceptions
			bufferedInputStream = new BufferedInputStream( appContext.openFileInput(fileName) );
			try { while( (data = bufferedInputStream.read()) != -1)
				stringBuffer.append((char)data); }
			catch (IOException e) {
				Log.e("Upload", "read error in " + this.fileName);
				e.printStackTrace();
				CrashHandler.writeCrashlog(e, appContext); }
			bufferedInputStream.close(); }
		catch (FileNotFoundException e) {
			Log.e("TextFileManager", "file " + this.fileName + " does not exist");
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext); }
		catch (IOException e){
			Log.e("DataFileManager", "could not close " + this.fileName);
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext);}
		
		return stringBuffer.toString();
	}
	
	
	/*###############################################################################
	#################### Miscellaneous Utility Functions ############################
	###############################################################################*/
	
	/** Delete the reference to the file so that it can be uploaded */
	public synchronized void closeFile() { this.fileName = null; }
	
	/** Deletes a file in the safest possible way, based on the file type (persistent-nonpersistent). */
	public synchronized void deleteSafely() {
		if (this.isDummy) { return; }
		String oldFileName = this.fileName;
		// For files that are persistant we have to do a slightly unsafe deletion, for everything else
		// we allocate the new file and then delete the old file.
		
		if ( this.persistent ) { //delete then create (unsafe, potential threading issues)
			TextFileManager.delete(oldFileName);
			this.newFile(); }
		else { 					//create then delete
//			this.newFile();
			TextFileManager.delete(oldFileName); }
	}
	
	/** Deletes a file.  Exists to make file deletion thread-safe.
	 * @param fileName */
	public static synchronized void delete(String fileName){
		try { appContext.deleteFile(fileName); }
		catch (Exception e) {
			Log.e("TextFileManager", "cannot delete file " + fileName );
			e.printStackTrace();
			CrashHandler.writeCrashlog(e, appContext); }
	}
	
	/** Make new files for all the non-persistent files. */
	public static synchronized void makeNewFilesForEverything() {
//		Log.d("TextFileManager.java", "makeNewFilesForEverything() called");
		GPSFile.newFile();
		accelFile.newFile();
		accessibilityLog.newFile();
		ambientLightFile.newFile();
		ambientTemperatureFile.newFile();
		gyroFile.newFile();
		magnetoFile.newFile();
		stepsFile.newFile();
		powerStateLog.newFile();
		callLog.newFile();
		tapsLog.newFile();
		textsLog.newFile();
		usageFile.newFile();
		bluetoothLog.newFile();
		debugLogFile.newFile();
	}
	
	/** Very simple function, exists to make any function that needs to grab all extant files thread-safe.
	 * DO NOT USE THIS FUNCTION, USE getAllFilesSafely() INSTEAD.
	 * @return a string array of all files in the app's file directory. */
	public static synchronized String[] getAllFiles() { return appContext.getFilesDir().list(); }
		
	/** Returns all data that are not currently in use
	 * @return String[] a list of file names */
	public static synchronized ArrayList<String> getAllUploadableFiles() {
		Set<String> files = new HashSet<String>();
		Collections.addAll(files, getAllFiles());
		
		// These files should never be uploaded
		files.remove(TextFileManager.getKeyFile().fileName);
		files.remove(AudioRecorderActivity.unencryptedTempAudioFileName);
		files.remove(AudioRecorderEnhancedActivity.unencryptedRawAudioFileName);
		files.remove(AudioRecorderEnhancedActivity.unencryptedTempAudioFileName); //should be identical to regular audiorecording file, but keep in case it changes.
		
		// These files are currently being written to, so they shouldn't be uploaded now
		files.remove(TextFileManager.getGPSFile().fileName);
		files.remove(TextFileManager.getAccelFile().fileName);
		files.remove(TextFileManager.getAccessibilityLogFile().fileName);
		files.remove(TextFileManager.getAmbientLightFile().fileName);
		files.remove(TextFileManager.getAmbientTemperatureFile().fileName);
		files.remove(TextFileManager.getGyroFile().fileName);
		files.remove(TextFileManager.getMagnetoFile().fileName);
		files.remove(TextFileManager.getStepsFile().fileName);
		files.remove(TextFileManager.getPowerStateFile().fileName);
		files.remove(TextFileManager.getCallLogFile().fileName);
		files.remove(TextFileManager.getTapsLogFile().fileName);
		files.remove(TextFileManager.getTextsLogFile().fileName);
		files.remove(TextFileManager.getUsageFile().fileName);
		files.remove(TextFileManager.getDebugLogFile().fileName);
		files.remove(TextFileManager.getBluetoothLogFile().fileName);

		// These files are only occasionally open, but they may be currently open. If they are, don't upload them
		files.remove(TextFileManager.getSurveyAnswersFile().fileName);
		files.remove(TextFileManager.getSurveyTimingsFile().fileName);
		files.remove(TextFileManager.getWifiLogFile().fileName);

		return new ArrayList <String> (files);
	}
	
	/*###############################################################################
	######################## DEBUG STUFF ############################################
	###############################################################################*/
	
	/** Returns a list of file names, all files in that list are retired and will not be written to again.
	 * @return a string array of files*/
	public static synchronized String[] getAllFilesSafely() {
		String[] file_list = getAllFiles();
		makeNewFilesForEverything();
		return file_list;
	}
		
	/**For Debug Only.  Deletes all files, creates new ones. */
	public static synchronized void deleteEverything() {
		//Get complete list of all files, then make new files, then delete all files from the old files list.
		Set<String> files = new HashSet<String>();
		Collections.addAll(files, getAllFilesSafely());
		
		//Need to do this crap or else we end up deleting the persistent files repeatedly
		files.remove(TextFileManager.getDebugLogFile().fileName);
		TextFileManager.getDebugLogFile().deleteSafely();
		files.remove(TextFileManager.getKeyFile().fileName);
		
		//and delete things
		for (String file_name : files) {
//			Log.i("deleting file", file_name);
			try { appContext.deleteFile(file_name); }
			catch (Exception e) {
				Log.e("TextFileManager", "could not delete file " + file_name);
				e.printStackTrace(); }
		}
	}

	// add files into zips, avoid huge zip files
	public static synchronized ArrayList<String> compress_and_delete( ArrayList<String> nonzip_list ){
		ArrayList<String> ret = new ArrayList<String>();
		ZipOutputStream zip = null;
		FileOutputStream fileWriter = null;
		long tms = System.currentTimeMillis();
		if(!nonzip_list.isEmpty()) try {
			int n_current_bytes = 0, n_proc_files = 0;
			String zip_filename = "", last_filename = nonzip_list.get(nonzip_list.size()-1);
			ArrayList<String> delete_list = new ArrayList<String>();
			for ( String file_name : nonzip_list ) {
				if (fileWriter == null){
					zip_filename = (++tms) + ".zip";
					fileWriter = appContext.openFileOutput(zip_filename, Context.MODE_PRIVATE);
					zip = new ZipOutputStream(fileWriter);
					n_current_bytes = 0;
				}
				n_current_bytes += addFileToZip(file_name, zip);
				zip.flush();
				fileWriter.flush();
				delete_list.add(file_name);
				if ( n_current_bytes > 1048576 || file_name.equals(last_filename) ) {
					zip.close();
					fileWriter.close();
					fileWriter = null;
					n_proc_files += delete_list.size();
					for(String fn : delete_list)
						delete(fn);
					delete_list.clear();
					ret.add(zip_filename);
					PersistentData.setMainUploadInfo( "Compressing files ..."
							+ "\nDate: " + Calendar.getInstance().getTime().toString()
							+ "\nProcessed files: " + n_proc_files + "/" + nonzip_list.size()
							+ "\nOutput files: " + ret.size());
				}
			}
		} catch ( Exception e ) {
			DebugInterfaceActivity.smartLog(DebugInterfaceActivity.LogFile.name,tms + " : " + e.toString());
		} finally {
			if( fileWriter != null ) try{
				zip.close();
				fileWriter.close();
			} catch ( Exception e1 ) {
				DebugInterfaceActivity.smartLog(DebugInterfaceActivity.LogFile.name,tms + " : " + e1.toString());
			}
		}
		return ret;
	}

	// add an individual file to zip
	public static synchronized int addFileToZip(String filename, ZipOutputStream zip) throws Exception {
		int len, n_total=0;
		byte[] buf = new byte[4096];
		FileInputStream in = null;
		try {
			in = appContext.openFileInput(filename);
			zip.putNextEntry(new ZipEntry(filename));
			while ((len = in.read(buf)) > 0) {
				zip.write(buf, 0, len);
				n_total += len;
			}
		}	finally {
			if( in != null )
				in.close();
		}
		return n_total;
	}

	public static String CS2S(CharSequence seq){ return (seq==null?"":(String)seq); }
	public static String CS2S(String seq){ return (seq==null?"":seq); }
	public static String CS2S(List<CharSequence> seq){
		return (seq==null?"":seq.toString());
	}
}
