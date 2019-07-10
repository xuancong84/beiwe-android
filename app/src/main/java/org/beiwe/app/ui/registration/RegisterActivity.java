package org.beiwe.app.ui.registration;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;
import org.beiwe.app.BuildConfig;
import org.beiwe.app.DeviceInfo;
import org.beiwe.app.PermissionHandler;
import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.networking.HTTPUIAsync;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.qrcode.BarcodeCaptureActivity;
import org.beiwe.app.ui.utils.AlertsManager;
import org.json.JSONObject;
import java.util.concurrent.Callable;
import static org.beiwe.app.networking.PostRequest.addWebsitePrefix;

/**Activity used to log a user in to the application for the first time. This activity should only be called on ONCE,
 * as once the user is logged in, data is saved on the phone.
 * @author Dor Samet, Eli Jones, Josh Zagorsky */

@SuppressLint("ShowToast")
public class RegisterActivity extends RunningBackgroundServiceActivity {
	private EditText serverUrlInput;
	private EditText userIdInput;
	private EditText tempPasswordInput;
	private EditText newPasswordInput;
	private EditText confirmNewPasswordInput;

	private final static int PERMISSION_CALLBACK = 0; //This callback value can be anything, we are not really using it
	private final static int REQUEST_PERMISSIONS_IDENTIFIER = 1500;
	private final static int BARCODE_READER_REQUEST_CODE = 2500;
	private final static String DEFAULT_PASSWORD = "abcd1234";

	/** Users will go into this activity first to register information on the phone and on the server. */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_register);

		if (!BuildConfig.CUSTOMIZABLE_SERVER_URL) {
			TextView serverUrlCaption = (TextView) findViewById(R.id.serverUrlCaption);
			EditText serverUrlInput = (EditText) findViewById(R.id.serverUrlInput);
			serverUrlCaption.setVisibility(View.GONE);
			serverUrlInput.setVisibility(View.GONE);
		}

		serverUrlInput = (EditText) findViewById(R.id.serverUrlInput);
		userIdInput = (EditText) findViewById(R.id.registerUserIdInput);
		tempPasswordInput = (EditText) findViewById(R.id.registerTempPasswordInput);
		newPasswordInput = (EditText) findViewById(R.id.registerNewPasswordInput);
		confirmNewPasswordInput = (EditText) findViewById(R.id.registerConfirmNewPasswordInput);
//		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard(getApplicationContext());
//		textFieldKeyboard.makeKeyboardBehave(serverUrlInput);
//		textFieldKeyboard.makeKeyboardBehave(userIdInput);
//		textFieldKeyboard.makeKeyboardBehave(tempPasswordInput);
//		textFieldKeyboard.makeKeyboardBehave(newPasswordInput);
//		textFieldKeyboard.makeKeyboardBehave(confirmNewPasswordInput);

		newPasswordInput.setHint(String.format(getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()));
		confirmNewPasswordInput.setHint(String.format(getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()));
	}

	public synchronized void scanQrButtonPressed(View view) {
		BarcodeCaptureActivity.checkQR = new Callable<Boolean>() {
			public Boolean call() throws Exception {
				JSONObject jObject = new JSONObject(BarcodeCaptureActivity.scan_result);
				if ( jObject.has("url") && jObject.has("uid") && jObject.has("utp") )
					return true;
				throw new Exception("Invalid QR code!");
			}
		};
		Intent intent = new Intent( getApplicationContext(), BarcodeCaptureActivity.class );
		startActivityForResult( intent, BARCODE_READER_REQUEST_CODE );
	}


	/** Registration sequence begins here, called when the submit button is pressed. * @param view */
	public synchronized void registerButtonPressed(View view) {
		String serverUrl = serverUrlInput.getText().toString();
		String userID = userIdInput.getText().toString();
		String tempPassword = tempPasswordInput.getText().toString();
		String newPassword = newPasswordInput.getText().toString();
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();

		if(BuildConfig.APP_IS_DEV && userID.isEmpty()){
			if(Build.MANUFACTURER.equals("unknown")) {
				userID = "hywvod27";
				tempPassword = newPassword = confirmNewPassword = "abcd1234";
			}else{
				userID = "iawlsn1e";
				tempPassword = newPassword = confirmNewPassword = "abcd1234";
			}
		}

		Log.d("serverUrl:", serverUrl);

		if ((serverUrl.length() == 0) && (BuildConfig.CUSTOMIZABLE_SERVER_URL)) {
			// If the study URL is empty, alert the user
			AlertsManager.showAlert(getString(R.string.url_too_short), getString(R.string.couldnt_register), this);
		} else if (userID.length() == 0) {
			// If the user id length is too short, alert the user
			AlertsManager.showAlert(getString(R.string.invalid_user_id), getString(R.string.couldnt_register), this);
			return;
		} else if (tempPassword.length() < 1) {
			// If the temporary registration password isn't filled in
			AlertsManager.showAlert(getString(R.string.empty_temp_password), getString(R.string.couldnt_register), this);
		} else if (!PersistentData.passwordMeetsRequirements(newPassword)) {
			// If the new password has too few characters
			String alertMessage = String.format(getString(R.string.password_too_short), PersistentData.minPasswordLength());
			AlertsManager.showAlert(alertMessage, getString(R.string.couldnt_register), this);
			return;
		} else if (!newPassword.equals(confirmNewPassword)) {
			// If the new password doesn't match the confirm new password
			AlertsManager.showAlert(getString(R.string.password_mismatch), getString(R.string.couldnt_register), this);
			return;
		} else {
			if (BuildConfig.CUSTOMIZABLE_SERVER_URL) {
				PersistentData.setServerUrl(serverUrl);
			}
			PersistentData.setLoginCredentials(userID, tempPassword);
			// Log.d("RegisterActivity", "trying \"" + LoginManager.getPatientID() + "\" with password \"" + LoginManager.getPassword() + "\"" );
			tryToRegisterWithTheServer(this, addWebsitePrefix(getApplicationContext().getString(R.string.register_url)), newPassword);
		}
	}
	
	
	/**Implements the server request logic for user, device registration. 
	 * @param url the URL for device registration*/
	static private void tryToRegisterWithTheServer(final Activity currentActivity, final String url, final String newPassword) {
		new HTTPUIAsync(url, currentActivity ) {
			@Override
			protected Void doInBackground(Void... arg0) {
				DeviceInfo.initialize(currentActivity.getApplicationContext());
				// Always use anonymized hashing when first registering the phone.
				parameters = PostRequest.makeParameter("bluetooth_id", DeviceInfo.getBluetoothMAC() ) +
							PostRequest.makeParameter("new_password", newPassword) +
							PostRequest.makeParameter("phone_number", ((RegisterActivity) activity).getPhoneInfo() ) +
							PostRequest.makeParameter("device_id", DeviceInfo.getAndroidID() ) +
							PostRequest.makeParameter("device_os", "Android") +
							PostRequest.makeParameter("os_version", DeviceInfo.getAndroidVersion() ) +
							PostRequest.makeParameter("hardware_id", DeviceInfo.getHardwareId() ) +
							PostRequest.makeParameter("brand", DeviceInfo.getBrand() ) +
							PostRequest.makeParameter("manufacturer", DeviceInfo.getManufacturer() ) +
							PostRequest.makeParameter("model", DeviceInfo.getModel() ) +
							PostRequest.makeParameter("product", DeviceInfo.getProduct() ) +
							PostRequest.makeParameter("beiwe_version", DeviceInfo.getBeiweVersion() );
				responseCode = PostRequest.httpRegister(parameters, url);

				// If we are not using anonymized hashing, resubmit the phone identifying information
				if (responseCode == 200 && !PersistentData.getEnabled(PersistentData.USE_ANONYMIZED_HASHING)) { // This short circuits so if the initial register fails, it won't try here
					try {
						//Sleep for one second so the backend does not receive information with overlapping timestamps
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					int resp = PostRequest.httpRegisterAgain(parameters, url);
				}
				return null;
			}
		
			@Override
			protected void onPostExecute(Void arg) {
				super.onPostExecute(arg);
				if (responseCode == 200) {
					PersistentData.setPassword(newPassword);
					activity.startActivity(new Intent(activity.getApplicationContext(), PhoneNumberEntryActivity.class) );
					activity.finish();
				} else {
					AlertsManager.showAlert(responseCode, currentActivity.getString(R.string.couldnt_register), currentActivity);
				}
			}
		};
	}

	/**This is the function that requires SMS permissions.  We need to supply a (unique) identifier for phone numbers to the registration arguments.
	 * @return */
	@SuppressLint("MissingPermission")
	private String getPhoneInfo() {
		TelephonyManager phoneManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
		String phoneInfo = phoneManager.getLine1Number();
		String hashedInfo = EncryptionEngine.hashPhoneNumber(TextFileManager.CS2S(phoneInfo));
		return hashedInfo;
	}
	
	
	/*####################################################################
	###################### Permission Prompting ##########################
	####################################################################*/
	private static Boolean prePromptActive = false;
	private static Boolean postPromptActive = false;
	private static Boolean thisResumeCausedByFalseActivityReturn = false;
	private static Boolean aboutToResetFalseActivityReturn = false;
	private static Boolean activityNotVisible = false;

	private void goToSettings() {
		Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
		myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
		myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityForResult(myAppSettings, REQUEST_PERMISSIONS_IDENTIFIER);
	}

	
	@Override
	protected void onResume() {
		super.onResume();
		activityNotVisible = false;
		
		// This used to be in an else block, its idempotent and we appear to have been having problems with it not having been run.
		DeviceInfo.initialize(getApplicationContext());
		
		if (aboutToResetFalseActivityReturn) {
			aboutToResetFalseActivityReturn = false;
			thisResumeCausedByFalseActivityReturn = false;
			return;
		}
		if ( !PermissionHandler.checkAccessReadSms(getApplicationContext()) && !thisResumeCausedByFalseActivityReturn) {
			if (shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) ) {
				if (!prePromptActive && !postPromptActive ) { showPostPermissionAlert(this); } 
			}
			else if (!prePromptActive && !postPromptActive ) { showPrePermissionAlert(this); }
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		activityNotVisible = true;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch ( requestCode ) {
			case BARCODE_READER_REQUEST_CODE:
				try {
					String qrResult = data.getStringExtra(BarcodeCaptureActivity.BarcodeObject);
					JSONObject jObject = new JSONObject(qrResult);
					serverUrlInput.setText(jObject.getString("url"));
					userIdInput.setText(jObject.getString("uid"));
					tempPasswordInput.setText(jObject.getString("utp"));
					newPasswordInput.setText(DEFAULT_PASSWORD);
					confirmNewPasswordInput.setText(DEFAULT_PASSWORD);
					aboutToResetFalseActivityReturn = true;
					registerButtonPressed(null);
				} catch (Exception e) { }
				break;
			case REQUEST_PERMISSIONS_IDENTIFIER:
				aboutToResetFalseActivityReturn = true;
		}
	}

	@Override
	public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
		if ( activityNotVisible ) return; //this is identical logical progression to the way it works in SessionActivity.
		for ( int i = 0; i < grantResults.length; ++i ) {
			if ( permissions[i].equals( Manifest.permission.READ_SMS ) ) {
				if ( grantResults[i] == PermissionHandler.PERMISSION_GRANTED ) { break; }
				if ( shouldShowRequestPermissionRationale(permissions[i]) )
					showPostPermissionAlert(this ); //(shouldShow... "This method returns true if the app has requested this permission previously and the user denied the request.")
			}
		}
	}
	
	/* Message Popping */
	
	public static void showPrePermissionAlert(final Activity activity) {
		// Log.i("reg", "showPreAlert");
		if (prePromptActive) { return; }
		prePromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(R.string.permission_registration_read_sms_alert);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			activity.requestPermissions(new String[]{ Manifest.permission.READ_SMS }, PERMISSION_CALLBACK );
			prePromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) { } } ); //Okay button
		builder.create().show();
	}
	
	public static void showPostPermissionAlert(final RegisterActivity activity) {
		// Log.i("reg", "showPostAlert");
		if (postPromptActive) { return; }
		postPromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(R.string.permission_registration_actually_need_sms_alert);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			thisResumeCausedByFalseActivityReturn = true;
			activity.goToSettings();
			postPromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) {  } } ); //Okay button
		builder.create().show();
	}
}
