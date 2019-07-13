package org.beiwe.app.listeners;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class AccelerometerListener implements SensorEventListener{
	public static final String name = "accel";
	public static final String header = "timestamp,accuracy,x,y,z";
	
	private Sensor accelSensor;
	private Boolean exists = null;
	private Boolean enabled = null;
	private String accuracy;
	
	public Boolean check_status(){ 
		if (exists) return enabled;
		return false; }
	
	/**Listens for accelerometer updates.  NOT activated on instantiation.
	 * Use the turn_on() function to log any accelerometer updates to the 
	 * accelerometer log.
	 * @param applicationContext a Context from an activity or service. */
	public AccelerometerListener(Context applicationContext){
		this.accuracy = "";
		this.exists = BackgroundService.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
		
		if (this.exists) {
			enabled = false;
			accelSensor = BackgroundService.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			if (accelSensor == null) {
				Log.e("Accelerometer Problems", "accelSensor does not exist? (2)");
				TextFileManager.getDebugLogFile().writeEncrypted("accelSensor does not exist? (2)");
				exists = false;
			}
		}
	}

	public synchronized void turn_on() {
		if ( !this.exists ) return;
		if ( !BackgroundService.sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL) ) {
			Log.e("Accelerometer", "Accelerometer is broken");
			TextFileManager.getDebugLogFile().writeEncrypted("Trying to start Accelerometer session, device cannot find accelerometer."); }
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
//		Log.e("Accelerometer", "accelerometer update");
		Long javaTimeCode = System.currentTimeMillis();
		float[] values = arg0.values;
		String data = javaTimeCode.toString() + TextFileManager.DELIMITER
				+ accuracy + TextFileManager.DELIMITER
				+ values[0] + TextFileManager.DELIMITER
				+ values[1] + TextFileManager.DELIMITER + values[2];
		TextFileManager.getAccelFile().writeEncrypted(data);
	}
}