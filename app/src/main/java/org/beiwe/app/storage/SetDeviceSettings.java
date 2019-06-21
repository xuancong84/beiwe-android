package org.beiwe.app.storage;

import org.json.JSONException;
import org.json.JSONObject;

public class SetDeviceSettings {
	public static void writeDeviceSettings(JSONObject deviceSettings) throws JSONException {
		// Write data stream booleans
		for(String feature : PersistentData.feature_list) {
			boolean value;
			try {
				value = deviceSettings.getBoolean(feature);
			}catch (Exception e){
				value = false;
			}
			PersistentData.setEnabled(feature, value);
		}
		
		// Write timer settings
		for(String feature : PersistentData.time_param_list) {
			int value;
			try {
				value = deviceSettings.getInt(feature);
			}catch (Exception e){
				value = 0;
			}
			PersistentData.setTimeInSeconds(feature, value);
		}


		// Write text strings
		String aboutPageText = deviceSettings.getString("about_page_text");
		PersistentData.setAboutPageText(aboutPageText);
		String callClinicianButtonText = deviceSettings.getString("call_clinician_button_text");
		PersistentData.setCallClinicianButtonText(callClinicianButtonText);
		String consentFormText = deviceSettings.getString("consent_form_text");
		PersistentData.setConsentFormText(consentFormText);
		String surveySubmitSuccessToastText = deviceSettings.getString("survey_submit_success_toast_text");
		PersistentData.setSurveySubmitSuccessToastText(surveySubmitSuccessToastText);

		// Anonymized hashing
		boolean useAnonymizedHashing; // This key was added late, and if the server is old it may not be present
		try { useAnonymizedHashing = deviceSettings.getBoolean("use_anonymized_hashing"); }
		catch (JSONException e) { useAnonymizedHashing = false; }
		PersistentData.setUseAnonymizedHashing(useAnonymizedHashing);

		// Use GPS Fuzzing
		boolean useGpsFuzzing; // This key was added late, and if the server is old it may not be present
		try { useGpsFuzzing = deviceSettings.getBoolean("use_gps_fuzzing"); }
		catch (JSONException e) { useGpsFuzzing = false; }
		PersistentData.setUseGpsFuzzing(useGpsFuzzing);
	}
}
