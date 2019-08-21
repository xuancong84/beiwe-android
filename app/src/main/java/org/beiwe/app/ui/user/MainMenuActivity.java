package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**The main menu activity of the app. Currently displays 4 buttons - Audio Recording, Graph, Call Clinician, and Sign out.
 * @author Dor Samet */
public class MainMenuActivity extends SessionActivity {
	public static MainMenuActivity mSelf = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mSelf = this;
		setContentView(R.layout.activity_main_menu);

		((TextView)findViewById(R.id.main_last_upload)).setText(PersistentData.getMainUploadInfo());
		((Button)findViewById(R.id.main_menu_call_clinician)).setText(PersistentData.getCallClinicianButtonText());
	}
	
	//############################## Buttons ####################################
//	public void graphResults (View v) { startActivity( new Intent(getApplicationContext(), GraphActivity.class) ); }
}
