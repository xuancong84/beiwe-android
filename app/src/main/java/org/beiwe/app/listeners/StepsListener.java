package org.beiwe.app.listeners;

/*************************************************************************
 *
 * MOH Office of Healthcare Transformation (MOHT) CONFIDENTIAL
 *
 *  Copyright 2018-2019
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of MOH Office of Healthcare Transformation.
 * The intellectual and technical concepts contained
 * herein are proprietary to MOH Office of Healthcare Transformation
 * and may be covered by Singapore, U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from MOH Office of Healthcare Transformation.
 */

import org.beiwe.app.BackgroundService;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import static java.lang.String.format;

public class StepsListener implements SensorEventListener {
	public static final String name = "steps";
	public static final String header = "timestamp,sensor_timestamp,sensor,steps";

	private Sensor stepsCounterSensor, stepsDetectorSensor;
	private boolean exists = false;
	private boolean enabled = false;
	private String accuracy;

	public Boolean check_status() {
		if (exists) return enabled;
		return false;
	}

	/**
	 * Listens for Magnetometer updates.  NOT activated on instantiation.
	 * Use the turn_on() function to log any Magnetometer updates to the Magnetometer log.
	 *
	 * @param applicationContext a Context from an activity or service.
	 */
	public StepsListener(Context applicationContext) {
		accuracy = "unknown";
//		exists = BackgroundService.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);
//		exists = BackgroundService.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR);
		stepsCounterSensor = BackgroundService.sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		stepsDetectorSensor = BackgroundService.sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
		exists = (stepsCounterSensor != null || stepsDetectorSensor != null);
		if (stepsCounterSensor == null) {
			Log.e("StepsListener", "StepsCounterSensor does not exist? (2)");
			TextFileManager.getDebugLogFile().writeEncrypted("StepsCounterSensor does not exist? (2)");
		}
		if (stepsDetectorSensor == null) {
			Log.e("StepsListener", "StepsDetectorSensor does not exist? (2)");
			TextFileManager.getDebugLogFile().writeEncrypted("StepsDetectorSensor does not exist? (2)");
		}
	}

	public synchronized void turn_on() {
		if ( !exists || enabled ) return;
		if ( !BackgroundService.sensorManager.registerListener(this, stepsCounterSensor, SensorManager.SENSOR_DELAY_NORMAL) ) {
			Log.e("StepsListener", "StepsCounterSensor is broken");
			TextFileManager.getDebugLogFile().writeEncrypted("Trying to start StepsCounterSensor session, device failed to register listener."); }
		if ( !BackgroundService.sensorManager.registerListener(this, stepsDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST) ) {
			Log.e("StepsListener", "StepsDetectorSensor is broken");
			TextFileManager.getDebugLogFile().writeEncrypted("Trying to start StepsDetectorSensor session, device failed to register listener."); }
		enabled = true;
	}

	public synchronized void turn_off(){
		if(stepsCounterSensor!=null) BackgroundService.sensorManager.unregisterListener(this, stepsCounterSensor);
		if(stepsDetectorSensor!=null) BackgroundService.sensorManager.unregisterListener(this, stepsDetectorSensor);
		enabled = false;
	}

	/** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
	 * (only triggered by the system.) */
	@Override
	public synchronized void onAccuracyChanged(Sensor arg0, int arg1) {	accuracy = "" + arg1; }

	/** On receipt of a sensor change, record it.  Include accuracy.
	 * (only ever triggered by the system.) */
	@Override
	public synchronized void onSensorChanged(SensorEvent arg0) {
		float[] values = arg0.values;
		// Why put both timestamps?? Some vendors such as Huawei put sequence number (1,2,3,...) as timestamp in arg0.timestamp
		String data = System.currentTimeMillis() + TextFileManager.DELIMITER + arg0.timestamp + TextFileManager.DELIMITER
				+ (arg0.sensor==stepsDetectorSensor?"detector":(arg0.sensor==stepsCounterSensor?"counter":"null"));
		for(int x=0; x<values.length; ++x)
			data += TextFileManager.DELIMITER + values[x];
		TextFileManager.getStepsFile().writeEncrypted(data);
	}
}

