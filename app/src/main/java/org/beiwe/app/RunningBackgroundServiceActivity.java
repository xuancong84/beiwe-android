package org.beiwe.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Trace;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.beiwe.app.BackgroundService.BackgroundServiceBinder;
import org.beiwe.app.storage.PersistentData;
import static org.beiwe.app.storage.PersistentData.*;

import org.beiwe.app.ui.DebugInterfaceActivity;
import org.beiwe.app.ui.user.AboutActivityLoggedOut;
import org.beiwe.app.ui.user.MainMenuActivity;

import java.util.concurrent.Callable;

/**All Activities in the app extend this Activity.  It ensures that the app's key services (i.e.
 * BackgroundService, LoginManager, PostRequest, DeviceInfo, and WifiListener) are running before
 * the interface tries to interact with any of those.
 * 
 * Activities that require the user to be logged in (SurveyActivity, GraphActivity, 
 * AudioRecorderActivity, etc.) extend SessionActivity, which extends this.
 * Activities that do not require the user to be logged in (the login, registration, and password-
 * reset Activities) extend this activity directly.
 * Therefore all Activities have this Activity's functionality (binding the BackgroundService), but
 * the login-protected Activities have additional functionality that forces the user to log in. 
 * 
 * @author Eli Jones, Josh Zagorsky
 */
public class RunningBackgroundServiceActivity extends AppCompatActivity {
	/** The backgroundService variable is an Activity's connection to the ... BackgroundService.
	 * We ensure the BackgroundService is running in the onResume call, and functionality that
	 * relies on the BackgroundService is always tied to UI elements, reducing the chance of
	 * a null backgroundService variable to essentially zero. */
	protected BackgroundService backgroundService;
	protected static RunningBackgroundServiceActivity mSelf = null;
	protected static Menu s_menu = null;

	//an unused variable for tracking whether the background Service is connected, uncomment if we ever need that.
//	protected boolean isBound = false;
	
	/**The ServiceConnection Class is our trigger for events that rely on the BackgroundService */
	protected ServiceConnection backgroundServiceConnection = new ServiceConnection() {
	    @Override
	    public void onServiceConnected(ComponentName name, IBinder binder) {
	        // Log.d("ServiceConnection", "Background Service Connected");
	        BackgroundServiceBinder some_binder = (BackgroundServiceBinder) binder;
	        backgroundService = some_binder.getService();
	        doBackgroundDependentTasks();
//	        isBound = true;
	    }
	    
	    @Override
	    public void onServiceDisconnected(ComponentName name) {
	        Log.w("ServiceConnection", "Background Service Disconnected");
	        backgroundService = null;
//	        isBound = false;
	    }
	};
	
	@Override
	protected void onCreate(Bundle bundle){ 
		super.onCreate(bundle);
		mSelf = this;
		Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(getApplicationContext()));
		PersistentData.initialize(getApplicationContext());
	}
	
	/** Override this function to do tasks on creation, but only after the background Service has been initialized. */
	protected void doBackgroundDependentTasks() { }
	
	@Override
	/**On creation of RunningBackgroundServiceActivity we guarantee that the BackgroundService is
	 * actually running, we then bind to it so we can access program resources. */
	protected void onResume() {
		super.onResume();
		
		Intent startingIntent = new Intent(getApplicationContext(), BackgroundService.class);
		startingIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
		startService(startingIntent);
		bindService( startingIntent, backgroundServiceConnection, Context.BIND_AUTO_CREATE);
	}


	@Override
	/** disconnect BackgroundServiceConnection when the Activity closes, otherwise we have a
	 * memory leak warning (and probably an actual memory leak, too). */
	protected void onPause() {
		super.onPause();
		activityNotVisible = true;
		unbindService(backgroundServiceConnection);
	}
	
	
	/*####################################################################
	########################## Common UI #################################
	####################################################################*/
	
	@Override
	/** Common UI element, the menu button.*/
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; adds items to the action bar if it is present.
		s_menu = menu;
		getMenuInflater().inflate(R.menu.logged_out_menu, menu);
		menu.findItem(R.id.menu_call_clinician).setTitle(PersistentData.getCallClinicianButtonText());
		return true;
	}

	
	@Override
	/** Common UI element, items in menu.*/
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
		case R.id.menu_about:
			startActivity(new Intent(getApplicationContext(), AboutActivityLoggedOut.class));
			return true;
		case R.id.menu_call_clinician:
			callClinician(null);
			return true;
		case R.id.menu_call_research_assistant:
			callResearchAssistant(null);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	
	/** sends user to phone, calls the user's clinician. */
	@SuppressWarnings("MissingPermission")
	public void callClinician( View v ) {
		startPhoneCall(PersistentData.getString(PCP_PHONE_NUMBER));
	}
	
	/** sends user to phone, calls the study's research assistant. */
	@SuppressWarnings("MissingPermission")
	public void callResearchAssistant(View v) {
		startPhoneCall(PersistentData.getString(PASSWORD_RESET_NUMBER_KEY));
	}

	private void startPhoneCall(String phoneNumber) {
		Intent callIntent = new Intent(Intent.ACTION_CALL);
		callIntent.setData(Uri.parse("tel:" + phoneNumber));
		try {
			startActivity(callIntent);
		} catch (SecurityException e) {
			showAlertThatEnablesUserToGrantPermission(this,
					getString(R.string.cant_make_a_phone_call_permissions_alert),
					getString(R.string.cant_make_phone_call_alert_title),
					0);
		}
	}

	/*####################################################################
	###################### Permission Prompting ##########################
	####################################################################*/

	private static Boolean prePromptActive = false;
	private static Boolean postPromptActive = false;
	private static Boolean powerPromptActive = false;
	private static Boolean usagePromptActive = false;
	private static Boolean overlayPromptActive = false;
	private static Boolean accessibilityPromptActive = false;
	private static Boolean thisResumeCausedByFalseActivityReturn = false;
	private static Boolean aboutToResetFalseActivityReturn = false;
	private static Boolean activityNotVisible = false;
	public Boolean isAudioRecorderActivity() { return false; }

	private void goToSettings(Integer permissionIdentifier) {
		// Log.i("sessionActivity", "goToSettings");
		Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
		myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
		myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityForResult(myAppSettings, permissionIdentifier);
	}

	@TargetApi(23)
	private void goToPowerSettings(Integer powerCallbackIdentifier) {
		// Log.i("sessionActivity", "goToPowerSettings");
		@SuppressLint("BatteryLife") Intent powerSettings = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
		powerSettings.addCategory(Intent.CATEGORY_DEFAULT);
		powerSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityForResult(powerSettings, powerCallbackIdentifier);
	}

	@TargetApi(23)
	private void goToAccessibilitySettings(Integer callbackIdentifier) {
		// Log.i("sessionActivity", "goToAccessibilitySettings");
		Intent accessibilitySettings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
		accessibilitySettings.addCategory(Intent.CATEGORY_DEFAULT);
		accessibilitySettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityForResult(accessibilitySettings, callbackIdentifier);
	}

	@TargetApi(23)
	private void goToOverlaySettings(Integer callbackIdentifier) {
		// Log.i("sessionActivity", "goToOverlaySettings");
		Intent overlaySettings = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
		overlaySettings.addCategory(Intent.CATEGORY_DEFAULT);
		overlaySettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityForResult(overlaySettings, callbackIdentifier);
	}

	@TargetApi(21)
	private void goToUsageSettings(Integer callbackIdentifier) {
		// Log.i("sessionActivity", "goToUsageSettings");
		Intent usageSettings = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
		usageSettings.addCategory(Intent.CATEGORY_DEFAULT);
		usageSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivityForResult(usageSettings, callbackIdentifier);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Log.i("sessionActivity", "onActivityResult. requestCode: " + requestCode + ", resultCode: " + resultCode );
		aboutToResetFalseActivityReturn = true;
	}

	@Override
	public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
		// Log.i("sessionActivity", "onRequestPermissionResult");
		if (!activityNotVisible) checkPermissionsLogic();
	}

	protected boolean checkPermissionsLogic() {
		//gets called as part of onResume,
		activityNotVisible = false;

		if (aboutToResetFalseActivityReturn) {
			aboutToResetFalseActivityReturn = false;
			thisResumeCausedByFalseActivityReturn = false;
			return false;
		}

		if ( !thisResumeCausedByFalseActivityReturn ) {
			String permission = PermissionHandler.getNextPermission( getApplicationContext(), this.isAudioRecorderActivity() );
			if (permission == null) return false;
			BackgroundService.finalSetupDone = false;
			if (!prePromptActive && !postPromptActive && !powerPromptActive) {
				if (permission == PermissionHandler.POWER_EXCEPTION_PERMISSION ) {
					showPowerManagementAlert(this, getString(R.string.power_management_exception_alert), 1000);
					return false;
				}
				if (permission == PermissionHandler.USAGE_EXCEPTION_PERMISSION ) {
					showUsagePermissionAlert(this, getString(R.string.usage_permission_alert), 1001);
					return false;
				}
				if (permission == PermissionHandler.APPLICATION_OVERLAY_PERMISSION) {
					showOverlayPermissionAlert(this, getString(R.string.overlay_permission_alert), 1002);
					return false;
				}
				if (permission == PermissionHandler.ACCESSIBILITY_OVERLAY_PERMISSION) {
					showAccessibilityPermissionAlert(this, getString(R.string.accessibility_permission_alert), 1003);
					return false;
				}
				if (shouldShowRequestPermissionRationale( permission ) ) {
					if (!prePromptActive && !postPromptActive ) showAlertThatForcesUserToGrantPermission(
							this, PermissionHandler.getBumpingPermissionMessage(permission),
								PermissionHandler.permissionMap.get(permission) );
				} else if (!prePromptActive && !postPromptActive ) showRegularPermissionAlert(
						this, PermissionHandler.getNormalPermissionMessage(permission),
						permission, PermissionHandler.permissionMap.get(permission));
			}
		}
		return true;
	}

	/* Message Popping */
	public static void showRegularPermissionAlert(final Activity activity, final String message, final String permission, final Integer permissionCallback) {
		// Log.i("sessionActivity", "showPreAlert");
		if (prePromptActive) { return; }
		prePromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(message);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			activity.requestPermissions(new String[]{ permission }, permissionCallback );
			prePromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) { } } ); //Okay button
		builder.create().show();
	}

	public static void showAlertThatForcesUserToGrantPermission(final RunningBackgroundServiceActivity activity, final String message, final Integer permissionCallback) {
		// Log.i("sessionActivity", "showPostAlert");
		if (postPromptActive) { return; }
		postPromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(message);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				thisResumeCausedByFalseActivityReturn = true;
				activity.goToSettings(permissionCallback);
				postPromptActive = false;
			}
		});
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface arg0, int arg1) {}
		}); //Okay button
		builder.create().show();
	}

	public static void showAlertThatEnablesUserToGrantPermission(final RunningBackgroundServiceActivity activity, final String message, final String title, final Integer permissionCallback) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int arg1) {
				activity.goToSettings(permissionCallback);
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int arg1) {}
		});
		builder.create().show();
	}

	public static void showPowerManagementAlert(final RunningBackgroundServiceActivity activity, final String message, final Integer powerCallbackIdentifier) {
		Log.i("sessionActivity", "power alert");
		if (powerPromptActive) { return; }
		powerPromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(message);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			Log.d("power management alert", "bumping");
			thisResumeCausedByFalseActivityReturn = true;
			activity.goToPowerSettings(powerCallbackIdentifier);
			powerPromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) {  } } ); //Okay button
		builder.create().show();
	}

	public static void showAccessibilityPermissionAlert(final RunningBackgroundServiceActivity activity, final String message, final Integer callbackIdentifier) {
		if (accessibilityPromptActive) { return; }
		accessibilityPromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(message);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			thisResumeCausedByFalseActivityReturn = true;
			activity.goToAccessibilitySettings(callbackIdentifier);
			accessibilityPromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) {  } } ); //Okay button
		builder.create().show();
	}

	public static void showOverlayPermissionAlert(final RunningBackgroundServiceActivity activity, final String message, final Integer callbackIdentifier) {
		if (overlayPromptActive) { return; }
		overlayPromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(message);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			thisResumeCausedByFalseActivityReturn = true;
			activity.goToOverlaySettings(callbackIdentifier);
			overlayPromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) {  } } ); //Okay button
		builder.create().show();
	}

	public static void showUsagePermissionAlert(final RunningBackgroundServiceActivity activity, final String message, final Integer callbackIdentifier) {
		if (usagePromptActive) { return; }
		usagePromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(message);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			thisResumeCausedByFalseActivityReturn = true;
			activity.goToUsageSettings(callbackIdentifier);
			usagePromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) {  } } ); //Okay button
		builder.create().show();
	}

	public static int nNeedToClick;
	private static int menu_item_id;
	public void onClickText(View view){
		if(--nNeedToClick==0)
			findViewById(R.id.resetAPP).setVisibility(View.VISIBLE);
		if(nNeedToClick==-10) {
			Button button = findViewById(R.id.resetAPP);
			button.setTextColor(0xffff0000);
			button.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View view) {
					try {
						MenuItem mi = s_menu.add("Unlock Debug Interface");
						menu_item_id = mi.getItemId();
						mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){
							@Override
							public boolean onMenuItemClick(MenuItem item) {
								s_menu.removeItem(menu_item_id);
								promptView("This will unlock debug interface. Are you sure?", new Callable() {
									@Override
									public Object call(){
										DebugInterfaceActivity.unlocked = true;
										startActivity(new Intent(mSelf, DebugInterfaceActivity.class));
										return null;
									}
								});
								return true;
							}
						});
					} catch (Exception e) {}
					return true;
				}
			});
		}
	}

	public void promptView(String msg, Callable func){
		LayoutInflater li = LayoutInflater.from(this);
		View promptsView = li.inflate(R.layout.password_prompt, null);
		final EditText userInput = promptsView.findViewById(R.id.editTextDialogUserInput);

		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if(which == DialogInterface.BUTTON_POSITIVE && userInput.getText().toString().equals("MOHTp@ssw0rd"))
					try{func.call();} catch (Exception e){}
			}
		};

		new AlertDialog.Builder(this)
				.setView(promptsView)
				.setTitle("Warning")
				.setMessage(msg)
				.setPositiveButton("OK", dialogClickListener)
				.setNegativeButton("Cancel", dialogClickListener).show();
	}

	public void resetAPP(View view) {
		promptView("This will unregister any study and reset the APP. Are you sure?", new Callable() {
			@Override
			public Object call(){
				DebugInterfaceActivity.RESET();
				return null;
			}
		});
	}

}