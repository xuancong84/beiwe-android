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
			PersistentData.setTimeInSeconds(feature, deviceSettings.optInt(feature,0));

		// Other settings
		PersistentData.setAboutPageText(deviceSettings.getString("about_page_text"));
		PersistentData.setCallClinicianButtonText(deviceSettings.getString("call_clinician_button_text"));
		PersistentData.setConsentFormText(deviceSettings.getString("consent_form_text"));
		PersistentData.setSurveySubmitSuccessToastText(deviceSettings.getString("survey_submit_success_toast_text"));
		PersistentData.setEnabled(SKIP_CONSENT, deviceSettings.optBoolean(SKIP_CONSENT, false));
		PersistentData.setInteger(PHONE_NUMBER_LENGTH, deviceSettings.optInt(PHONE_NUMBER_LENGTH, 8));
		PersistentData.setString(PCP_PHONE_NUMBER, deviceSettings.optString(PCP_PHONE_NUMBER, ""));
		PersistentData.setEnabled(USE_GPS_FUZZING, deviceSettings.optBoolean(USE_GPS_FUZZING,false ));
		PersistentData.setEnabled(USE_ANONYMIZED_HASHING, deviceSettings.optBoolean(USE_ANONYMIZED_HASHING,true));
	}
}
