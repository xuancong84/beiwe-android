package org.beiwe.app.storage;

import org.json.JSONException;
import org.json.JSONObject;
import static org.beiwe.app.storage.PersistentData.*;

public class SetDeviceSettings {
	public static void writeDeviceSettings(JSONObject deviceSettings) throws JSONException {
		// Write data stream booleans
		for(String feature : PersistentData.feature_list)
			PersistentData.setEnabled(feature, deviceSettings.optBoolean(feature,false));

		// Write timer settings
		for(String feature : PersistentData.time_param_list)
			PersistentData.setLong(feature, deviceSettings.optLong(feature,0));

		// Other settings
		setString(ABOUT_PAGE_TEXT_KEY, deviceSettings.getString(ABOUT_PAGE_TEXT_KEY));
		setString(CALL_CLINICIAN_BUTTON_TEXT_KEY, deviceSettings.getString(CALL_CLINICIAN_BUTTON_TEXT_KEY));
		setString(CONSENT_FORM_TEXT_KEY, deviceSettings.getString(CONSENT_FORM_TEXT_KEY));
		setString(SURVEY_SUBMIT_SUCCESS_TOAST_TEXT_KEY, deviceSettings.getString(SURVEY_SUBMIT_SUCCESS_TOAST_TEXT_KEY));
		setString(PCP_PHONE_NUMBER, deviceSettings.optString(PCP_PHONE_NUMBER, ""));
		setInteger(PHONE_NUMBER_LENGTH, deviceSettings.optInt(PHONE_NUMBER_LENGTH, 8));
		setEnabled(USE_GPS_FUZZING, deviceSettings.optBoolean(USE_GPS_FUZZING,false ));
		setEnabled(USE_ANONYMIZED_HASHING, deviceSettings.optBoolean(USE_ANONYMIZED_HASHING,true));

		TextFileManager.write_buffer_size = deviceSettings.optInt(WRITE_BUFFER_SIZE, 0);
		setInteger(WRITE_BUFFER_SIZE, TextFileManager.write_buffer_size);
	}
}
