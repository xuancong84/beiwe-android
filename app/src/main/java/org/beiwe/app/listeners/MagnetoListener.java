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

public class MagnetoListener implements SensorEventListener{
	public static final String name = "magnetometer";
	public static final String header = "timestamp,accuracy,x,y,z";

	private Sensor magnetoSensor;
	private Boolean exists = null;
	private Boolean enabled = null;
	private String accuracy;

	public Boolean check_status(){
		if (exists) return enabled;
		return false; }

	/**Listens for Magnetometer updates.  NOT activated on instantiation.
	 * Use the turn_on() function to log any Magnetometer updates to the Magnetometer log.
	 * @param applicationContext a Context from an activity or service. */
	public MagnetoListener(Context applicationContext){
		this.accuracy = "unknown";
		this.exists = BackgroundService.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS);

		if (this.exists) {
			enabled = false;
			this.magnetoSensor = BackgroundService.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			if (this.magnetoSensor == null ) {
				Log.e("Magnetometer Problems", "magnetoSensor does not exist? (2)" );
				TextFileManager.getDebugLogFile().writeEncrypted("magnetoSensor does not exist? (2)");
				exists = false;	}
		} }

	public synchronized void turn_on() {
		if ( !this.exists ) return;
		if ( !BackgroundService.sensorManager.registerListener(this, magnetoSensor, SensorManager.SENSOR_DELAY_NORMAL) ) {
			Log.e("Magnetometer", "Magnetometer is broken");
			TextFileManager.getDebugLogFile().writeEncrypted("Trying to start Magnetometer session, device cannot find Magnetometer."); }
		enabled = true;	}

	public synchronized void turn_off(){
		BackgroundService.sensorManager.unregisterListener(this);
		enabled = false; }

	/** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
	 * (only triggered by the system.) */
	@Override
	public synchronized void onAccuracyChanged(Sensor arg0, int arg1) {	accuracy = "" + arg1; }

	/** On receipt of a sensor change, record it.  Include accuracy.
	 * (only ever triggered by the system.) */
	@Override
	public synchronized void onSensorChanged(SensorEvent arg0) {
		Long javaTimeCode = System.currentTimeMillis();
		float[] values = arg0.values;
		String data = javaTimeCode.toString() + TextFileManager.DELIMITER
				+ accuracy + TextFileManager.DELIMITER
				+ values[0] + TextFileManager.DELIMITER
				+ values[1] + TextFileManager.DELIMITER + values[2];
		TextFileManager.getMagnetoFile().writeEncrypted(data);
	}
}
