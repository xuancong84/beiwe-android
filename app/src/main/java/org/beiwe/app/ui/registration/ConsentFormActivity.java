package org.beiwe.app.ui.registration;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.networking.SurveyDownloader;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.LoadingActivity;
import org.beiwe.app.storage.PersistentData;
import static org.beiwe.app.storage.PersistentData.*;

public class ConsentFormActivity extends RunningBackgroundServiceActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(PersistentData.getEnabled(SKIP_CONSENT)){
			consentButton(null );
			return;
		}

		setContentView(R.layout.activity_consent_form);
		
		TextView consentFormBody = (TextView) findViewById(R.id.consent_form_body);
		consentFormBody.setText(PersistentData.getConsentFormText());
	}
	
	/** On the press of the do not consent button, we pop up an alert, allowing the user
	 * to press "Cancel" if they did not mean to press the do not consent. */
	public void doNotConsentButton(View view) {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(ConsentFormActivity.this);
		alertBuilder.setTitle("Do Not Consent");
		alertBuilder.setMessage(getString(R.string.doNotConsentAlert));
		alertBuilder.setPositiveButton("I Understand", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
				System.exit(0);
			}
		});
		alertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) { return; }} );
		alertBuilder.create().show();
	}
	
	public void consentButton(View view) {
		PersistentData.setRegistered(true);
		PersistentData.loginOrRefreshLogin();

		// Download the survey questions and schedule the surveys
		SurveyDownloader.downloadSurveys(getApplicationContext());

		
		// Create new data files, these will now have a patientID prepended to those files
		TextFileManager.initialize(getApplicationContext());
		TextFileManager.makeNewFilesForEverything();
		
		//This is important.  we need to start timers...
		BackgroundService.localHandle.doSetup();
		
		// Start the Main Screen Activity, destroy this activity
		startActivity(new Intent(getApplicationContext(), LoadingActivity.class) );
		finish();
	}
}