package org.beiwe.app.listeners;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AmbientLightListener implements SensorEventListener{
	public static final String name = "light";
	public static final String header = "timestamp,accuracy,value";

	private Sensor sensor;
	private Context appContext;
	private Boolean exists = null;
	private Boolean enabled = null;
	private String accuracy;

	/**Listens for ambient light sensor updates.  NOT activated on instantiation.
	 * Use the turn_on() function to log any ambient light sensor updates to the
	 * ambient light sensor log.
	 * @param applicationContext a Context from an activity or service. */
	public AmbientLightListener(Context applicationContext){
		this.appContext = applicationContext;
		this.accuracy = "";
		this.exists = BackgroundService.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT);

		if (this.exists) {
			enabled = false;
			this.sensor = BackgroundService.sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			if (this.sensor == null ) {
				TextFileManager.getDebugLogFile().writeEncrypted("sensor does not exist? (2)");
				exists = false;	}
		} }

	public synchronized void turn_on() {
		if ( !this.exists ) return;
		if ( !BackgroundService.sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) )
			TextFileManager.getDebugLogFile().writeEncrypted("Trying to start AmbientLightSensor session, device cannot find AmbientLightSensor.");
		enabled = true;	}

	public synchronized void turn_off(){
		BackgroundService.sensorManager.unregisterListener(this);
		enabled = false; }

	/** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
	 * (only triggered by the system.) */
	@Override
	public synchronized void onAccuracyChanged(Sensor arg0, int arg1) {	accuracy = "" + arg1; }

	/** On receipt of a sensor change, record it.  Include accuracy. (only ever triggered by the system.) */
	@Override
	public synchronized void onSensorChanged(SensorEvent arg0) {
		Long javaTimeCode = System.currentTimeMillis();
		float value = arg0.values[0];
		String data = javaTimeCode.toString() + TextFileManager.DELIMITER + accuracy + TextFileManager.DELIMITER + value;
		TextFileManager.getAmbientLightFile().writeEncrypted(data);
		turn_off();
	}
}
