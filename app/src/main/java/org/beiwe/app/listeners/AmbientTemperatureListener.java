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
import org.spongycastle.util.Pack;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AmbientTemperatureListener implements SensorEventListener{
    public static final String name = "temperature";
    public static final String header = "timestamp,accuracy,value(s)";

    private Sensor sensor;
    private Context appContext;
    private Boolean exists = null;
    public int enabled = -1;
    private String accuracy;

    public class SensorInfo {
        public String sensor_name;
        public String sensor_feature_name;
        public int sensor_type;
        SensorInfo (String name, String fea_name, int type) {sensor_name=name; sensor_feature_name=fea_name; sensor_type=type;}
    }

    public final SensorInfo [] sensor_infos = {
        new SensorInfo(Sensor.STRING_TYPE_AMBIENT_TEMPERATURE, PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE, Sensor.TYPE_AMBIENT_TEMPERATURE),
        new SensorInfo(Sensor.STRING_TYPE_RELATIVE_HUMIDITY, PackageManager.FEATURE_SENSOR_RELATIVE_HUMIDITY, Sensor.TYPE_RELATIVE_HUMIDITY),
        new SensorInfo(Sensor.STRING_TYPE_PRESSURE, PackageManager.FEATURE_SENSOR_BAROMETER, Sensor.TYPE_PRESSURE),
        new SensorInfo(Sensor.STRING_TYPE_PROXIMITY, PackageManager.FEATURE_SENSOR_PROXIMITY, Sensor.TYPE_PROXIMITY),
        new SensorInfo(Sensor.STRING_TYPE_HEART_RATE, PackageManager.FEATURE_SENSOR_HEART_RATE, Sensor.TYPE_HEART_RATE),
        new SensorInfo(Sensor.STRING_TYPE_HEART_BEAT, PackageManager.FEATURE_SENSOR_HEART_RATE_ECG, Sensor.TYPE_HEART_BEAT),
        new SensorInfo(Sensor.STRING_TYPE_STEP_DETECTOR, PackageManager.FEATURE_SENSOR_STEP_DETECTOR, Sensor.TYPE_STEP_DETECTOR),
        new SensorInfo(Sensor.STRING_TYPE_STEP_COUNTER, PackageManager.FEATURE_SENSOR_STEP_COUNTER, Sensor.TYPE_STEP_COUNTER),
    };

    public static int sensor_i = 0;
    public SensorInfo si;

    /**Listens for ambient temperature sensor updates.  NOT activated on instantiation.
     * Use the turn_on() function to log any ambient temperature sensor updates to the
     * ambient temperature sensor log.
     * @param applicationContext a Context from an activity or service. */
    public AmbientTemperatureListener(Context applicationContext){
        this.appContext = applicationContext;
        this.accuracy = "";
        si = sensor_infos[0];
    }

    public synchronized void turn_on() {
        if (enabled==sensor_i) return;

        // check package existence
        exists = BackgroundService.packageManager.hasSystemFeature(si.sensor_feature_name);
        if (!exists) {
            TextFileManager.getDebugLogFile().writeEncrypted("System Feature " + si.sensor_feature_name + " does not exist!");
        }
        sensor = BackgroundService.sensorManager.getDefaultSensor(si.sensor_type);
        if (sensor == null) {
            TextFileManager.getDebugLogFile().writeEncrypted("Sensor " + si.sensor_name + " does not exist!");
            return;
        }
        if (enabled!=-1 && enabled!=sensor_i) turn_off();
        if (enabled==sensor_i) return;
        if (!BackgroundService.sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)) {
            TextFileManager.getDebugLogFile().writeEncrypted("Failed to start Sensor " + si.sensor_name + ", sensorManager.registerListener failed!");
            enabled = -1;
        }
        enabled = sensor_i;
    }

    public synchronized void turn_off(){
        BackgroundService.sensorManager.unregisterListener(this);
        enabled = -1;
    }

    public synchronized void increment(){ increment(1); }
    public synchronized void increment(int inc){
        sensor_i = (sensor_i+inc+sensor_infos.length)%sensor_infos.length;
        si = sensor_infos[sensor_i];
    }

    /** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
     * (only triggered by the system.) */
    @Override
    public synchronized void onAccuracyChanged(Sensor arg0, int arg1) {	accuracy = "" + arg1; }

    /** On receipt of a sensor change, record it.  Include accuracy. (only ever triggered by the system.) */
    @Override
    public synchronized void onSensorChanged(SensorEvent arg0) {
        Long javaTimeCode = arg0.timestamp;
        String data = javaTimeCode.toString() + TextFileManager.DELIMITER + accuracy;
        for(float value : arg0.values)
            data += TextFileManager.DELIMITER + value;
        TextFileManager.getAmbientTemperatureFile().writeEncrypted(data);
        turn_off();
    }
}
