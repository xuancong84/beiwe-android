package org.beiwe.app.ui.registration;

import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.ui.utils.AlertsManager;

import static org.beiwe.app.storage.PersistentData.*;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class PhoneNumberEntryActivity extends RunningBackgroundServiceActivity {
	private EditText primaryCarePhone;
	private EditText passwordResetPhone;
	private int phoneNumberLength;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		phoneNumberLength = PersistentData.getInteger( PHONE_NUMBER_LENGTH,8 );

		if(!PersistentData.getString(PCP_PHONE_NUMBER).isEmpty()){
			startActivity(new Intent(getApplicationContext(), ConsentFormActivity.class));
			finish();
			return;
		}

		setContentView(R.layout.activity_phone_number_entry);
		primaryCarePhone = (EditText) findViewById(R.id.primaryCareNumber);
		passwordResetPhone = (EditText) findViewById(R.id.passwordResetNumber);

//		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard( getApplicationContext() );
//		textFieldKeyboard.makeKeyboardBehave(primaryCarePhone);
//		textFieldKeyboard.makeKeyboardBehave(passwordResetPhone);
	}
	
	public void checkAndPromptConsent(View view) {
		String primary = primaryCarePhone.getText().toString().replaceAll("[^0-9+]", "");
		String reset = passwordResetPhone.getText().toString().replaceAll("[^0-9+]", "");
		
		if (primary == null || primary.length() == 0 || reset == null || reset.length() == 0 ){
			AlertsManager.showAlert( getString(R.string.enter_phone_numbers), this );
			return;
		}
		if (primary.length() < phoneNumberLength || reset.length() < phoneNumberLength){
			AlertsManager.showAlert( String.format( getString(R.string.phone_number_length_error), phoneNumberLength), this );
			return;
		}
		
		PersistentData.setString(PCP_PHONE_NUMBER, primary);
		PersistentData.setString(PASSWORD_RESET_NUMBER_KEY, reset);
		startActivity(new Intent(getApplicationContext(), ConsentFormActivity.class));
		finish();
	}
}
