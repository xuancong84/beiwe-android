package org.beiwe.app.storage;

import org.json.JSONException;
import org.json.JSONObject;

public class SetDeviceSettings {
	public static void writeDeviceSettings(JSONObject deviceSettings) throws JSONException {
		// Write data stream booleans
		Boolean accelerometerEnabled = deviceSettings.getBoolean("accelerometer");
		PersistentData.setAccelerometerEnabled(accelerometerEnabled);
		Boolean gpsEnabled = deviceSettings.getBoolean("gps");
		PersistentData.setGpsEnabled(gpsEnabled);
		Boolean callsEnabled = deviceSettings.getBoolean("calls");
		PersistentData.setCallsEnabled(callsEnabled);
		Boolean textsEnabled = deviceSettings.getBoolean("texts");
		PersistentData.setTextsEnabled(textsEnabled);
		Boolean wifiEnabled = deviceSettings.getBoolean("wifi");
		PersistentData.setWifiEnabled(wifiEnabled);
		Boolean bluetoothEnabled = deviceSettings.getBoolean("bluetooth");
		PersistentData.setBluetoothEnabled(bluetoothEnabled);
		Boolean powerStateEnabled = deviceSettings.getBoolean("power_state");
		PersistentData.setPowerStateEnabled(powerStateEnabled);
		
		Boolean allowUploadOverCellularData; // This key was added late, and if the server is old it may not be present
		try { allowUploadOverCellularData = deviceSettings.getBoolean("allow_upload_over_cellular_data");}
		catch (JSONException e) { allowUploadOverCellularData = false; }
		PersistentData.setAllowUploadOverCellularData(allowUploadOverCellularData);
		
		// Write timer settings
		int accelerometerOffDuration = deviceSettings.getInt("accelerometer_off_duration_seconds");
		PersistentData.setAccelerometerOffDurationSeconds(accelerometerOffDuration);
		int accelerometerOnDuration = deviceSettings.getInt("accelerometer_on_duration_seconds");
		PersistentData.setAccelerometerOnDurationSeconds(accelerometerOnDuration);
		int bluetoothOnDurationSeconds = deviceSettings.getInt("bluetooth_on_duration_seconds");
		PersistentData.setBluetoothOnDurationSeconds(bluetoothOnDurationSeconds);
		int bluetoothTotalDurationSeconds = deviceSettings.getInt("bluetooth_total_duration_seconds");
		PersistentData.setBluetoothTotalDurationSeconds(bluetoothTotalDurationSeconds);
		int bluetoothGlobalOffsetSeconds = deviceSettings.getInt("bluetooth_global_offset_seconds");
		PersistentData.setBluetoothGlobalOffsetSeconds(bluetoothGlobalOffsetSeconds);
		int checkForNewSurveysSeconds = deviceSettings.getInt("check_for_new_surveys_frequency_seconds");
		PersistentData.setCheckForNewSurveysFrequencySeconds(checkForNewSurveysSeconds);
		int createNewDataFilesFrequencySeconds = deviceSettings.getInt("create_new_data_files_frequency_seconds");
		PersistentData.setCreateNewDataFilesFrequencySeconds(createNewDataFilesFrequencySeconds);
		int gpsOffDurationSeconds = deviceSettings.getInt("gps_off_duration_seconds");
		PersistentData.setGpsOffDurationSeconds(gpsOffDurationSeconds);
		int gpsOnDurationSeconds = deviceSettings.getInt("gps_on_duration_seconds");
		PersistentData.setGpsOnDurationSeconds(gpsOnDurationSeconds);
		int secondsBeforeAutoLogout = deviceSettings.getInt("seconds_before_auto_logout");
		PersistentData.setSecondsBeforeAutoLogout(secondsBeforeAutoLogout);
		int uploadDataFilesFrequencySeconds = deviceSettings.getInt("upload_data_files_frequency_seconds");
		PersistentData.setUploadDataFilesFrequencySeconds(uploadDataFilesFrequencySeconds);
		int voiceRecordingMaxTimeLengthSeconds = deviceSettings.getInt("voice_recording_max_time_length_seconds");
		PersistentData.setVoiceRecordingMaxTimeLengthSeconds(voiceRecordingMaxTimeLengthSeconds);
		int wifiLogFrequencySeconds = deviceSettings.getInt("wifi_log_frequency_seconds");
		PersistentData.setWifiLogFrequencySeconds(wifiLogFrequencySeconds);
		
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
