package com.android.cts.verifier.sensors;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorNotSupportedException;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.BatchArrivalVerification;
import android.hardware.cts.helpers.sensorverification.TimestampClockSourceVerification;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeviceSuspendTestActivity
            extends SensorCtsVerifierTestActivity {
        public DeviceSuspendTestActivity() {
            super(DeviceSuspendTestActivity.class);
        }

        private PowerManager.WakeLock mDeviceSuspendLock;
        private PendingIntent mPendingIntent;
        private AlarmManager mAlarmManager;
        private static String ACTION_ALARM = "DeviceSuspendTestActivity.ACTION_ALARM";
        private static String TAG = "DeviceSuspendSensorTest";
        private SensorManager mSensorManager;
        private KeyguardManager mKeyguardManager;

        @Override
        protected void activitySetUp() throws InterruptedException {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            LocalBroadcastManager.getInstance(this).registerReceiver(myBroadCastReceiver,
                                            new IntentFilter(ACTION_ALARM));

            Intent intent = new Intent(this, AlarmReceiver.class);
            mPendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE_UNAUDITED);

            mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

            mKeyguardManager = getSystemService(KeyguardManager.class);

            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mDeviceSuspendLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                                "DeviceSuspendTestActivity");

            // Launch a foreground service to ensure that the test remains in the foreground and is
            // able to be woken-up when sensor data is delivered.
            startForegroundService(new Intent(this, DeviceSuspendTestService.class));

            mDeviceSuspendLock.acquire();
            SensorTestLogger logger = getTestLogger();
            logger.logInstructions(R.string.snsr_device_suspend_test_instr);
            waitForUserToBegin();
        }

        @Override
        protected void activityCleanUp() {
            mKeyguardManager.requestDismissKeyguard(this, null);
            try {
                playSound();
            } catch(InterruptedException e) {
              // Ignore.
            }
            LocalBroadcastManager.getInstance(this).unregisterReceiver(myBroadCastReceiver);
            if (mDeviceSuspendLock != null && mDeviceSuspendLock.isHeld()) {
                mDeviceSuspendLock.release();
            }

            stopService(new Intent(this, DeviceSuspendTestService.class));
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (mDeviceSuspendLock != null && mDeviceSuspendLock.isHeld()) {
                mDeviceSuspendLock.release();
            }
        }

        public static class AlarmReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Intent alarm_intent = new Intent(context, DeviceSuspendTestActivity.class);
                alarm_intent.setAction(DeviceSuspendTestActivity.ACTION_ALARM);
                LocalBroadcastManager.getInstance(context).sendBroadcastSync(alarm_intent);
            }
        }

        public BroadcastReceiver myBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!mDeviceSuspendLock.isHeld()) {
                    mDeviceSuspendLock.acquire();
                }
            }
        };

        public static class DeviceSuspendTestService extends Service {
            private static final String NOTIFICATION_CHANNEL_ID =
                    "com.android.cts.verifier.sensors.DeviceSuspendTestActivity.Notification";
            private static final String NOTIFICATION_CHANNEL_NAME = "Device Suspend Test";

            @Override
            public IBinder onBind(Intent intent) {
                return null;
            }

            @Override
            public int onStartCommand(Intent intent, int flags, int startId) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
                NotificationManager notificationManager =
                        getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
                Notification notification =
                        new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(getString(R.string.snsr_device_suspend_service_active))
                        .setContentText(getString(
                                R.string.snsr_device_suspend_service_notification))
                        .setSmallIcon(R.drawable.icon)
                        .setAutoCancel(true)
                        .build();
                startForeground(1, notification);

                return START_NOT_STICKY;
            }
        }

        public String testAPWakeUpWhenReportLatencyExpiresAccel() throws Throwable {
            Sensor wakeUpSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
            if (wakeUpSensor == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER, true);
            }
            return runAPWakeUpWhenReportLatencyExpires(wakeUpSensor);
        }

        public String testAPWakeUpWhenReportLatencyExpiresGyro() throws Throwable {
            Sensor wakeUpSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true);
            if (wakeUpSensor == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_GYROSCOPE, true);
            }
            return runAPWakeUpWhenReportLatencyExpires(wakeUpSensor);
        }

        public String testAPWakeUpWhenReportLatencyExpiresMag() throws Throwable {
            Sensor wakeUpSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD,true);
            if (wakeUpSensor == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_MAGNETIC_FIELD, true);
            }
            return runAPWakeUpWhenReportLatencyExpires(wakeUpSensor);
        }

        public String testAPWakeUpWhenFIFOFullAccel() throws Throwable {
            Sensor wakeUpSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
            if (wakeUpSensor == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER, true);
            }
            return runAPWakeUpWhenFIFOFull(wakeUpSensor);
        }

        public String testAPWakeUpWhenFIFOFullGyro() throws Throwable {
            Sensor wakeUpSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true);
            if (wakeUpSensor == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_GYROSCOPE, true);
            }
            return runAPWakeUpWhenFIFOFull(wakeUpSensor);
        }

        public String testAPWakeUpWhenFIFOFullMag() throws Throwable {
            Sensor wakeUpSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD,true);
            if (wakeUpSensor == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_MAGNETIC_FIELD, true);
            }
            return runAPWakeUpWhenFIFOFull(wakeUpSensor);
        }

        public String testAccelBatchingInAPSuspendLargeReportLatency() throws Throwable {
            Sensor accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER, false);
            }
            return runAPWakeUpByAlarmNonWakeSensor(accel, (int)TimeUnit.SECONDS.toMicros(1000));
        }

        public String testAccelBatchingInAPSuspendZeroReportLatency() throws Throwable {
            Sensor accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
           if (accel == null) {
                throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER, false);
            }
            return runAPWakeUpByAlarmNonWakeSensor(accel, 0);
        }

        /**
         * Verify that the device is able to suspend
         */
        public void verifyDeviceCanSuspend() throws Throwable {
            // Make sure clocks are different (i.e. kernel has suspended at least once)
            // so that we can determine if sensors are using correct clocksource timestamp
            final int MAX_SLEEP_ATTEMPTS = 10;
            final int SLEEP_DURATION_MS = 2000;
            int sleep_attempts = 0;
            boolean device_needs_sleep = true;
            boolean wakelock_was_held = false;

            final long ALARM_WAKE_UP_DELAY_MS = TimeUnit.SECONDS.toMillis(20);
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                    SystemClock.elapsedRealtime() + ALARM_WAKE_UP_DELAY_MS,
                                    mPendingIntent);

            if (mDeviceSuspendLock != null && mDeviceSuspendLock.isHeld()) {
                wakelock_was_held = true;
                mDeviceSuspendLock.release();
            }

            do {
                try {
                    verifyClockDelta();
                    device_needs_sleep = false;
                } catch(Throwable e) {
                    // Delta between clocks too small, must sleep longer
                    if (sleep_attempts++ > MAX_SLEEP_ATTEMPTS) {
                        mAlarmManager.cancel(mPendingIntent);
                        if (wakelock_was_held) {
                            mDeviceSuspendLock.acquire();
                        }
                        throw e;
                    }
                    Thread.sleep(SLEEP_DURATION_MS);
                }
            } while (device_needs_sleep);

            if (wakelock_was_held) {
                mDeviceSuspendLock.acquire();
            }
            mAlarmManager.cancel(mPendingIntent);
        }

        /**
         * Verify that each continuous sensor is using the correct
         * clock source (CLOCK_BOOTTIME) for timestamps.
         */
        public String testTimestampClockSource() throws Throwable {
            String string = null;
            boolean error_occurred = false;
            List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
            if (sensorList == null) {
                throw new SensorTestStateNotSupportedException(
                    "Sensors are not available in the system.");
            }

            boolean needToVerifySuspend = true;

            for (Sensor sensor : sensorList) {
                if (sensor.getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS) {
                    Log.i(TAG, "testTimestampClockSource skipping non-continuous sensor: '" + sensor.getName());
                    continue;
                }
                if (sensor.getType() >= Sensor.TYPE_DEVICE_PRIVATE_BASE) {
                    Log.i(TAG, "testTimestampClockSource skipping vendor specific sensor: '" + sensor.getName());
                    continue;
                }

                if (needToVerifySuspend) {
                    verifyDeviceCanSuspend();
                    needToVerifySuspend = false;
                }

                try {
                    string = runVerifySensorTimestampClockbase(sensor, false);
                    if (string != null) {
                        return string;
                    }
                } catch(Throwable e) {
                    Log.e(TAG, e.getMessage());
                    error_occurred = true;
                }
            }
            if (error_occurred) {
                throw new Error("Sensors must use CLOCK_BOOTTIME as clock source for timestamping events");
            }
            return null;
        }

        public String runAPWakeUpWhenReportLatencyExpires(Sensor sensor) throws Throwable {

            verifyBatchingSupport(sensor);

            int fifoMaxEventCount = sensor.getFifoMaxEventCount();
            int samplingPeriodUs = sensor.getMaxDelay();
            if (samplingPeriodUs == 0) {
                // If maxDelay is not defined, set the value for 5 Hz.
                samplingPeriodUs = 200000;
            }

            long fifoBasedReportLatencyUs = maxBatchingPeriod(sensor, samplingPeriodUs);
            verifyBatchingPeriod(fifoBasedReportLatencyUs);

            final long MAX_REPORT_LATENCY_US = TimeUnit.SECONDS.toMicros(15); // 15 seconds
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    this,
                    sensor,
                    false,
                    samplingPeriodUs,
                    (int) MAX_REPORT_LATENCY_US,
                    true /*isDeviceSuspendTest*/);

            TestSensorOperation op = TestSensorOperation.createOperation(environment,
                                                                          mDeviceSuspendLock,
                                                                          false);
            final long ALARM_WAKE_UP_DELAY_MS =
                    TimeUnit.MICROSECONDS.toMillis(MAX_REPORT_LATENCY_US) +
                    TimeUnit.SECONDS.toMillis(10);

            op.addVerification(BatchArrivalVerification.getDefault(environment));
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                    SystemClock.elapsedRealtime() + ALARM_WAKE_UP_DELAY_MS,
                                    mPendingIntent);
            try {
                Log.i(TAG, "Running .. " + getCurrentTestNode().getName() + " " + sensor.getName());
                op.execute(getCurrentTestNode());
            } finally {
                mAlarmManager.cancel(mPendingIntent);
            }
            return null;
        }

        public String runAPWakeUpWhenFIFOFull(Sensor sensor) throws Throwable {
            verifyBatchingSupport(sensor);

            // Try to fill the FIFO at the fastest rate and check if the time is enough to run
            // the manual test.
            int samplingPeriodUs = sensor.getMinDelay();

            long fifoBasedReportLatencyUs = maxBatchingPeriod(sensor, samplingPeriodUs);

            final long MIN_LATENCY_US = TimeUnit.SECONDS.toMicros(20);
            // Ensure that FIFO based report latency is at least 20 seconds, we need at least 10
            // seconds of time to allow the device to be in suspend state.
            if (fifoBasedReportLatencyUs < MIN_LATENCY_US) {
                int fifoMaxEventCount = sensor.getFifoMaxEventCount();
                samplingPeriodUs = (int) MIN_LATENCY_US/fifoMaxEventCount;
                fifoBasedReportLatencyUs = MIN_LATENCY_US;
            }

            final int MAX_REPORT_LATENCY_US = Integer.MAX_VALUE;
            final long ALARM_WAKE_UP_DELAY_MS =
                    TimeUnit.MICROSECONDS.toMillis(fifoBasedReportLatencyUs) +
                    TimeUnit.SECONDS.toMillis(10);

            TestSensorEnvironment environment = new TestSensorEnvironment(
                    this,
                    sensor,
                    false,
                    (int) samplingPeriodUs,
                    (int) MAX_REPORT_LATENCY_US,
                    true /*isDeviceSuspendTest*/);

            TestSensorOperation op = TestSensorOperation.createOperation(environment,
                                                                        mDeviceSuspendLock,
                                                                        true);
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                    SystemClock.elapsedRealtime() + ALARM_WAKE_UP_DELAY_MS,
                                    mPendingIntent);
            op.addDefaultVerifications();
            try {
                Log.i(TAG, "Running .. " + getCurrentTestNode().getName() + " " + sensor.getName());
                op.execute(getCurrentTestNode());
            } finally {
                mAlarmManager.cancel(mPendingIntent);
            }
            return null;
        }

        /**
         * Verify the CLOCK_MONOTONIC and CLOCK_BOOTTIME clock sources are different
         * by at least 2 seconds.  Since delta between these two clock sources represents
         * time kernel has spent in suspend, device needs to have gone into suspend for
         * for at least 2 seconds since device was initially booted.
         */
        private void verifyClockDelta() throws Throwable {
            final int MIN_DELTA_BETWEEN_CLOCKS_MS = 2000;
            long uptimeMs = SystemClock.uptimeMillis();
            long realtimeMs = SystemClock.elapsedRealtime();
            long deltaMs = (realtimeMs - uptimeMs);
            if (deltaMs < MIN_DELTA_BETWEEN_CLOCKS_MS) {
                throw new Error("Delta between clock sources too small ("
                                  + deltaMs + "mS), device must sleep more than "
                                  + MIN_DELTA_BETWEEN_CLOCKS_MS/1000 + " seconds");
            }
            Log.i(TAG, "Delta between CLOCK_MONOTONIC and CLOCK_BOOTTIME is " + deltaMs + " mS");
        }


        /**
         * Verify sensor is using the correct clock source (CLOCK_BOOTTIME) for timestamps.
         * To tell the clock sources apart, the kernel must have suspended at least once.
         *
         * @param sensor - sensor to verify
         * @param verify_clock_delta
         *          true to verify that clock sources differ before running test
         *          false to skip verification of sufficient delta between clock sources
         */
        public String runVerifySensorTimestampClockbase(Sensor sensor, boolean verify_clock_delta)
            throws Throwable {
            Log.i(TAG, "Running .. " + getCurrentTestNode().getName() + " " + sensor.getName());
            if (verify_clock_delta) {
                verifyClockDelta();
            }
            /* Enable a sensor, grab a sample, and then verify timestamp is > realtimeNs
             * to assure the correct clock source is being used for the sensor timestamp.
             */
            final int MIN_TIMESTAMP_BASE_SAMPLES = 1;
            int samplingPeriodUs = sensor.getMinDelay();
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    this,
                    sensor,
                    false,
                    (int) samplingPeriodUs,
                    0,
                    false /*isDeviceSuspendTest*/);
            TestSensorOperation op = TestSensorOperation.createOperation(environment, MIN_TIMESTAMP_BASE_SAMPLES);
            op.addVerification(TimestampClockSourceVerification.getDefault(environment));
            try {
                op.execute(getCurrentTestNode());
            } finally {
            }
            return null;
        }


        public String runAPWakeUpByAlarmNonWakeSensor(Sensor sensor, int maxReportLatencyUs)
                throws Throwable {
            verifyBatchingSupport(sensor);

            int samplingPeriodUs = sensor.getMinDelay();

            TestSensorEnvironment environment = new TestSensorEnvironment(
                    this,
                    sensor,
                    false,
                    (int) samplingPeriodUs,
                    maxReportLatencyUs,
                    true /*isDeviceSuspendTest*/);

            final long ALARM_WAKE_UP_DELAY_MS = 20000;
            TestSensorOperation op = TestSensorOperation.createOperation(environment,
                                                                         mDeviceSuspendLock,
                                                                         true);
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                    SystemClock.elapsedRealtime() + ALARM_WAKE_UP_DELAY_MS,
                                    mPendingIntent);
            try {
                Log.i(TAG, "Running .. " + getCurrentTestNode().getName() + " " + sensor.getName());
                op.execute(getCurrentTestNode());
            } finally {
                mAlarmManager.cancel(mPendingIntent);
            }
            return null;
        }

        private void verifyBatchingSupport(Sensor sensor)
                throws SensorTestStateNotSupportedException {
            int fifoMaxEventCount = sensor.getFifoMaxEventCount();
            if (fifoMaxEventCount == 0) {
                throw new SensorTestStateNotSupportedException("Batching not supported.");
            }
        }

        private void verifyBatchingPeriod(long periodUs)
                throws SensorTestStateNotSupportedException {
            // Ensure that FIFO based report latency is at least 20 seconds, we need at least 10
            // seconds of time to allow the device to be in suspend state.
            if (periodUs < TimeUnit.SECONDS.toMicros(20)) {
                throw new SensorTestStateNotSupportedException("FIFO too small to test reliably");
            }
        }

        private long maxBatchingPeriod (Sensor sensor, long samplePeriod) {
            long fifoMaxEventCount = sensor.getFifoMaxEventCount();
            return fifoMaxEventCount * samplePeriod;
        }

}
