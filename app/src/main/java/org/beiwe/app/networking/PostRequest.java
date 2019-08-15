package org.beiwe.app.networking;

import android.content.Context;
import android.os.StrictMode;
import android.util.Log;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.BuildConfig;
import org.beiwe.app.CrashHandler;
import org.beiwe.app.DeviceInfo;
import org.beiwe.app.R;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.SetDeviceSettings;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.DebugInterfaceActivity;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

/**
 * PostRequest is our class for handling all HTTP operations we need; they are all in the form of HTTP post requests.
 * All HTTP connections are HTTPS, and automatically include a password and identifying information.
 *
 * @author Josh, Eli, Dor
 */

//TODO: Low priority. Eli. clean this up and update docs. It does not adequately state that it puts into any request automatic security parameters, and it is not obvious why some of the functions exist (minimal http thing)
public class PostRequest {
	private static Context appContext;

	/**
	 * Uploads must be initialized with an appContext before they can access the wifi state or upload a _file_.
	 */
	private PostRequest(Context applicationContext) {
		appContext = applicationContext;
	}

	/**
	 * Simply runs the constructor, using the applcationContext to grab variables.  Idempotent.
	 */
	public static void initialize(Context applicationContext) {
		new PostRequest(applicationContext);
	}

	private static final Object FILE_UPLOAD_LOCK = new Object() {
	}; //Our lock for file uploading

	/*##################################################################################
	 ##################### Publicly Accessible Functions ###############################
	 #################################################################################*/


	/**
	 * For use with Async tasks.
	 * This opens a connection with the server, sends the HTTP parameters, then receives a response code, and returns it.
	 *
	 * @param parameters HTTP parameters
	 * @return serverResponseCode
	 */
	public static int httpRegister(String parameters, String url) {
		try {
			return doRegisterRequest(parameters, new URL(url));
		} catch (MalformedURLException e) {
			Log.e("PostRequestFileUpload", "malformed URL");
			e.printStackTrace();
			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			Log.e("PostRequest", "Network error: " + e.getMessage());
			return 502;
		}
	}

	/**
	 * For use with Async tasks.
	 * This opens a connection with the server, sends the HTTP parameters, then receives a response code, and returns it.
	 * This function exists to resend registration data if we are using non anonymized hashing
	 *
	 * @param parameters HTTP parameters
	 * @return serverResponseCode
	 */
	public static int httpRegisterAgain(String parameters, String url) {
		try {
			return doRegisterRequestSimple(parameters, new URL(url));
		} catch (MalformedURLException e) {
			Log.e("PostRequestFileUpload", "malformed URL");
			e.printStackTrace();
			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			Log.e("PostRequest", "Network error: " + e.getMessage());
			return 502;
		}
	}

	/**
	 * For use with Async tasks.
	 * Makes an HTTP post request with the provided URL and parameters, returns the server's response code from that request
	 *
	 * @param parameters HTTP parameters
	 * @return an int of the server's response code from the HTTP request
	 */
	public static int httpRequestcode(String parameters, String url, String newPassword) {
		try {
			return doPostRequestGetResponseCode(parameters, new URL(url), newPassword);
		} catch (MalformedURLException e) {
			Log.e("PosteRequestFileUpload", "malformed URL");
			e.printStackTrace();
			return 0;
		} catch (IOException e) {
			Log.e("PostRequest", "Unable to establish network connection");
			return 502;
		}
	}

	/**
	 * For use with Async tasks.
	 * Makes an HTTP post request with the provided URL and parameters, returns a string of the server's entire response.
	 *
	 * @param parameters HTTP parameters
	 * @param urlString  a string containing a url
	 * @return a string of the contents of the return from an HTML request.
	 */
	//TODO: Eli. low priority. investigate the android studio warning about making this a package local function
	public static String httpRequestString(String parameters, String urlString) {
		try {
			return doPostRequestGetResponseString(parameters, urlString);
		} catch (IOException e) {
			Log.e("PostRequest error", "Download File failed with exception: " + e);
			e.printStackTrace();
			throw new NullPointerException("Download File failed.");
		}
	}
	
	/*##################################################################################
	 ################################ Common Code ######################################
	 #################################################################################*/

	/**
	 * Creates an HTTP connection with minimal settings.  Some network funcitonality
	 * requires this minimal object.
	 *
	 * @param url a URL object
	 * @return a new HttpsURLConnection with minimal settings applied
	 * @throws IOException This function can throw 2 kinds of IO exceptions: IOExeptions and ProtocolException
	 */
	private static HttpsURLConnection minimalHTTP(URL url) throws IOException {
		SSLContext context = null;
		try {
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);

			// add SSL certificate
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			String crt = BackgroundService.appContext.getString(R.string.ssl_crt).replace(" ", "\n");
			crt = crt.replace("-BEGIN\n", "-BEGIN ").replace("-END\n", "-END ");
			InputStream caInput = new ByteArrayInputStream(crt.getBytes());
			Certificate ca;
			try {
				ca = cf.generateCertificate(caInput);
				System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
			} finally {
				caInput.close();
			}

			// Create a KeyStore containing our trusted CAs
			String keyStoreType = KeyStore.getDefaultType();
			KeyStore keyStore = KeyStore.getInstance(keyStoreType);
			keyStore.load(null, null);
			keyStore.setCertificateEntry("ca", ca);

			// Create a TrustManager that trusts the CAs in our KeyStore
			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
			tmf.init(keyStore);

			// Create an SSLContext that uses our TrustManager
			context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
		}catch (Exception e) {
			Log.e("ssl","Error: failed to add SSL certificate!");
			return null;
		}

		// Set hostname verifier
		HostnameVerifier hostnameVerifier = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Create a new HttpsURLConnection and set its parameters
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setSSLSocketFactory(context.getSocketFactory());
		connection.setHostnameVerifier(hostnameVerifier);
		connection.setUseCaches(false);
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setRequestProperty("Cache-Control", "no-cache");
		connection.setConnectTimeout(60000);
		connection.setReadTimeout(100000);
		return connection;
	}


	/**
	 * For use with functionality that requires additional parameters be added to an HTTP operation.
	 *
	 * @param parameters a string that has been created using the makeParameters function
	 * @param url        a URL object
	 * @return a new HttpsURLConnection with common settings
	 */
	private static HttpsURLConnection setupHTTP(String parameters, URL url, String newPassword) throws IOException {
		HttpsURLConnection connection = minimalHTTP(url);

		DataOutputStream request = new DataOutputStream(connection.getOutputStream());
		request.write(securityParameters(newPassword).getBytes());
		request.write(parameters.getBytes());
		request.flush();
		request.close();

		return connection;
	}

	/**
	 * Reads in the response data from an HttpsURLConnection, returns it as a String.
	 *
	 * @param connection an HttpsURLConnection
	 * @return a String containing return data
	 * @throws IOException
	 */
	private static String readResponse(HttpsURLConnection connection) throws IOException {
		Integer responseCode = connection.getResponseCode();
		if (responseCode == 200) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new DataInputStream(connection.getInputStream())));
			String line;
			StringBuilder response = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			return response.toString();
		}
		return responseCode.toString();
	}


	/*##################################################################################
	 ####################### Actual Post Request Functions #############################
	 #################################################################################*/

	private static String doPostRequestGetResponseString(String parameters, String urlString) throws IOException {
		HttpsURLConnection connection = setupHTTP(parameters, new URL(urlString), null);
		connection.connect();
		String data = readResponse(connection);
		connection.disconnect();
		return data;
	}


	private static int doPostRequestGetResponseCode(String parameters, URL url, String newPassword) throws IOException {
		HttpsURLConnection connection = setupHTTP(parameters, url, newPassword);
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}


	private static int doRegisterRequest(String parameters, URL url) throws IOException {
		HttpsURLConnection connection = setupHTTP(parameters, url, null);
		int response = connection.getResponseCode();
		if (response == 200) {
			String responseBody = readResponse(connection);
			try {
				JSONObject responseJSON = new JSONObject(responseBody);
				String key = responseJSON.getString("client_public_key");
				writeKey(key, response);
				JSONObject deviceSettings = responseJSON.getJSONObject("device_settings");
				SetDeviceSettings.writeDeviceSettings(deviceSettings);
			} catch (JSONException e) {
				CrashHandler.writeCrashlog(e, appContext);
			}
		}
		connection.disconnect();
		return response;
	}

	private static int doUpdateSettings() throws IOException {
		String parameters = PostRequest.makeParameter("patient_id", PersistentData.getPatientID());
		URL url = new URL(PersistentData.getServerUrl()+"/update_setting");
		HttpsURLConnection connection = setupHTTP(parameters, url, null);
		int response = connection.getResponseCode();
		if (response == 200) {
			String responseBody = readResponse(connection);
			try {
				JSONObject responseJSON = new JSONObject(responseBody);
				JSONObject deviceSettings = responseJSON.getJSONObject("device_settings");
				SetDeviceSettings.writeDeviceSettings(deviceSettings);
				BackgroundService.localHandle.doSetup();
			} catch (JSONException e) {
				CrashHandler.writeCrashlog(e, appContext);
			}
		}
		connection.disconnect();
		return response;
	}

	// Simple registration that does not parse response data.
	// This is used for resubmitting non anonymized identifier data during registration
	private static int doRegisterRequestSimple(String parameters, URL url) throws IOException {
		HttpsURLConnection connection = setupHTTP(parameters, url, null);
		int response = connection.getResponseCode();
		String responseBody = readResponse(connection);
		connection.disconnect();
		return response;
	}

	private static int writeKey(String key, int httpResponse) {
		if (!key.startsWith("MIIBI")) {
			Log.e("PostRequest - register", " Received an invalid encryption key from server: " + key);
			return 2;
		}
		// Log.d( "PostRequest", "Received a key: " + key );
		TextFileManager.getKeyFile().deleteSafely();
		TextFileManager.getKeyFile().safeWritePlaintext(key);
		return httpResponse;
	}


	/**
	 * Constructs and sends a multipart HTTP POST request with a file attached.
	 * This function uses minimalHTTP() directly because it needs to add a header (?) to the
	 * HttpsURLConnection object before it writes a file to it.
	 * This function had performance issues with large files, these have been resolved by conversion
	 * to use buffered file reads and http/tcp stream writes.
	 *
	 * @param file      the File to be uploaded
	 * @param uploadUrl the destination URL that receives the upload
	 * @return HTTP Response code as int
	 * @throws IOException
	 */
	private static int doFileUpload(File file, URL uploadUrl, long stopTime) throws IOException {
		if (file.length() > 1024 * 1024 * 10)
			DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name, "upload big file: length = " + file.length());

		HttpsURLConnection connection = minimalHTTP(uploadUrl);
		BufferedOutputStream request = new BufferedOutputStream(connection.getOutputStream(), 65536);
		BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file), 65536);

		request.write(securityParameters(null).getBytes());
		request.write(makeParameter("file_name", file.getName()).getBytes());
		request.write("file=".getBytes());
//		long start = System.currentTimeMillis();
		// Read in data from the file, and pour it into the POST request stream
		int data;
		int i = 0;
		while ((data = inputStream.read()) != -1) {
			request.write((char) data);
			i++;
			//This check has been profiled, it causes no slowdown in upload speeds, and vastly improves upload behavior.
			if (i % 65536 == 0 && stopTime < System.currentTimeMillis()) {
				connection.disconnect();
				return -1;
			}
		}
//		long stop = System.currentTimeMillis();
//		if (file.length() >  1024*1024*10) {
//			Log.w("upload", "speed: " + (file.length() / ((stop - start) / 1000)) / 1024 + "KBps");
//		}
		inputStream.close();
		request.write("".getBytes());
		request.flush();
		request.close();

		// Get HTTP Response. Pretty sure this blocks, nothing can really be done about that.
		int response = connection.getResponseCode();
		connection.disconnect();
		if (BuildConfig.APP_IS_DEV) {
			DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name,
					"uploading finished attempt to upload " + file.getName() + "; received code " + response);
		}
		return response;
	}


	//#######################################################################################
	//################################## File Upload ########################################
	//#######################################################################################


	/**
	 * Uploads all available files on a separate thread.
	 */
	public static void uploadAllFiles() {
		// determine if you are allowed to upload over WiFi or cellular data, return if not.
		if (!NetworkUtility.canUpload(appContext))
			return;

		DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name,"DOING UPLOAD STUFF" );
		// Run the HTTP POST on a separate thread
		Thread uploaderThread = new Thread(new Runnable() {
			@Override
			public void run() {
				doUploadAllFiles();
			}
		}, "uploader_thread");
		uploaderThread.start();
	}

	/**
	 * Uploads all files to the Beiwe server.
	 * Files get deleted as soon as a 200 OK code in received from the server.
	 */
	private static void doUploadAllFiles() {
		synchronized (FILE_UPLOAD_LOCK) {
			//long stopTime = System.currentTimeMillis() + PersistentData.getUploadDataFilesFrequencyMilliseconds();
			long stopTime = System.currentTimeMillis() + 1000 * 60 * 60; //One hour to upload files
			String[] files = TextFileManager.getAllUploadableFiles();
			DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name,"uploading " + files.length + " files");
			File file = null;
			URL uploadUrl = null; //set up url, write a crash log and fail gracefully if this ever breaks.
			try {
				uploadUrl = new URL(addWebsitePrefix(appContext.getResources().getString(R.string.data_upload_url)));
			} catch (MalformedURLException e) {
				CrashHandler.writeCrashlog(e, appContext);
				return;
			}

			for (String fileName : TextFileManager.getAllUploadableFiles()) {
				try {
					file = new File(appContext.getFilesDir() + "/" + fileName);
					DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name,"uploading " + file.getName());
					if (PostRequest.doFileUpload(file, uploadUrl, stopTime) == 200) {
						TextFileManager.delete(fileName);
					}
				} catch (IOException e) {
					DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name, "PostRequest.java: Failed to upload file " + fileName + ". Raised exception: " + e.getCause());
				}

				if (stopTime < System.currentTimeMillis()) {
					String msg = "UPLOAD STUFF, " + System.currentTimeMillis() + ", upload time limit of 1 hr reached, shutting down upload.";
					DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name, msg );
					TextFileManager.getDebugLogFile().writeEncrypted( msg );
					CrashHandler.writeCrashlog( new Exception("Upload took longer than 1 hour" ), appContext);
					return;
				}
			}
			DebugInterfaceActivity.smartLog( DebugInterfaceActivity.UploadFiles.name,"UPLOAD STUFF: DONE WITH UPLOAD");
		}
	}


	//#######################################################################################
	//############################### UTILITY FUNCTIONS #####################################
	//#######################################################################################

	public static String makeParameter(String key, String value) {
		return key + "=" + value + "&";
	}

	/**
	 * Create the 3 standard security parameters for POST request authentication.
	 *
	 * @param newPassword If this is a Forgot Password request, pass in a newPassword string from
	 *                    a text input field instead of from the device storage.
	 * @return a String of the securityParameters to append to the POST request
	 */
	public static String securityParameters(String newPassword) {
		String patientId = PersistentData.getPatientID();
		String deviceId = DeviceInfo.getAndroidID();
		String password = PersistentData.getPassword();
		if (newPassword != null) password = newPassword;

		return makeParameter("patient_id", patientId) +
				makeParameter("password", password) +
				makeParameter("device_id", deviceId);
	}

	public static String addWebsitePrefix(String URL) {
		String serverUrl = PersistentData.getServerUrl();
		if ((BuildConfig.CUSTOMIZABLE_SERVER_URL) && (serverUrl != null)) {
			return serverUrl + URL;
		} else {
			// If serverUrl == null, this should be an old version of the app that didn't let the
			// user specify the URL during registration, so assume the URL is either
			// studies.beiwe.org or staging.beiwe.org.
			return appContext.getResources().getString(R.string.default_website) + URL;
		}
	}

}