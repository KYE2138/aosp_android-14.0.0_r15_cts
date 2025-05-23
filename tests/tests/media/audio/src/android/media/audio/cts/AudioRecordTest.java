/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.AudioRecordingConfiguration;
import android.media.AudioSystem;
import android.media.AudioTimestamp;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.MicrophoneDirection;
import android.media.MicrophoneInfo;
import android.media.cts.AudioHelper;
import android.media.cts.StreamUtils;
import android.media.metrics.LogSessionId;
import android.media.metrics.MediaMetricsManager;
import android.media.metrics.RecordingSession;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class AudioRecordTest {
    private final static String TAG = "AudioRecordTest";
    private static final String REPORT_LOG_NAME = "CtsMediaAudioTestCases";
    private AudioRecord mAudioRecord;
    private AudioManager mAudioManager;
    private static final int SAMPLING_RATE_HZ = 44100;
    private boolean mIsOnMarkerReachedCalled;
    private boolean mIsOnPeriodicNotificationCalled;
    private boolean mIsHandleMessageCalled;
    private Looper mLooper;
    // For doTest
    private int mMarkerPeriodInFrames;
    private int mMarkerPosition;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            mIsHandleMessageCalled = true;
            super.handleMessage(msg);
        }
    };
    private static final int RECORD_DURATION_MS = 500;
    private static final int TEST_TIMING_TOLERANCE_MS = 70;

    @Before
    public void setUp() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        mAudioManager = InstrumentationRegistry .getInstrumentation()
                                               .getContext().getSystemService(AudioManager.class);
        /*
         * InstrumentationTestRunner.onStart() calls Looper.prepare(), which creates a looper
         * for the current thread. However, since we don't actually call loop() in the test,
         * any messages queued with that looper will never be consumed. Therefore, we must
         * create the instance in another thread, either without a looper, so the main looper is
         * used, or with an active looper.
         */
        Thread t = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mLooper = Looper.myLooper();
                synchronized(this) {
                    mAudioRecord = new AudioRecord.Builder()
                                    .setAudioFormat(new AudioFormat.Builder()
                                        .setSampleRate(SAMPLING_RATE_HZ)
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                                    .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                                    .setBufferSizeInBytes(
                                        AudioRecord.getMinBufferSize(SAMPLING_RATE_HZ,
                                              AudioFormat.CHANNEL_IN_MONO,
                                              AudioFormat.ENCODING_PCM_16BIT) * 10)
                                    .build();
                    this.notify();
                }
                Looper.loop();
            }
        };
        synchronized(t) {
            t.start(); // will block until we wait
            t.wait();
        }
        assertNotNull(mAudioRecord);
    }

    @After
    public void tearDown() throws Exception {
        if (hasMicrophone()) {
            mAudioRecord.release();
            mLooper.quit();
        }
    }

    private void reset() {
        mIsOnMarkerReachedCalled = false;
        mIsOnPeriodicNotificationCalled = false;
        mIsHandleMessageCalled = false;
    }

    @Test
    public void testAudioRecordProperties() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, mAudioRecord.getAudioFormat());
        assertEquals(MediaRecorder.AudioSource.DEFAULT, mAudioRecord.getAudioSource());
        assertEquals(1, mAudioRecord.getChannelCount());
        assertEquals(AudioFormat.CHANNEL_IN_MONO,
                mAudioRecord.getChannelConfiguration());
        assertEquals(AudioRecord.STATE_INITIALIZED, mAudioRecord.getState());
        assertEquals(SAMPLING_RATE_HZ, mAudioRecord.getSampleRate());
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_HZ,
                AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
        assertTrue(bufferSize > 0);
    }

    @Test
    public void testAudioRecordOP() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        final int SLEEP_TIME = 10;
        final int RECORD_TIME = 5000;
        assertEquals(AudioRecord.STATE_INITIALIZED, mAudioRecord.getState());

        int markerInFrames = mAudioRecord.getSampleRate() / 2;
        assertEquals(AudioRecord.SUCCESS,
                mAudioRecord.setNotificationMarkerPosition(markerInFrames));
        assertEquals(markerInFrames, mAudioRecord.getNotificationMarkerPosition());
        int periodInFrames = mAudioRecord.getSampleRate();
        assertEquals(AudioRecord.SUCCESS,
                mAudioRecord.setPositionNotificationPeriod(periodInFrames));
        assertEquals(periodInFrames, mAudioRecord.getPositionNotificationPeriod());
        OnRecordPositionUpdateListener listener = new OnRecordPositionUpdateListener() {

            public void onMarkerReached(AudioRecord recorder) {
                mIsOnMarkerReachedCalled = true;
            }

            public void onPeriodicNotification(AudioRecord recorder) {
                mIsOnPeriodicNotificationCalled = true;
            }
        };
        mAudioRecord.setRecordPositionUpdateListener(listener);

        // use byte array as buffer
        final int BUFFER_SIZE = 102400;
        byte[] byteData = new byte[BUFFER_SIZE];
        long time = System.currentTimeMillis();
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        while (System.currentTimeMillis() - time < RECORD_TIME) {
            Thread.sleep(SLEEP_TIME);
            mAudioRecord.read(byteData, 0, BUFFER_SIZE);
        }
        mAudioRecord.stop();
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());
        assertTrue(mIsOnMarkerReachedCalled);
        assertTrue(mIsOnPeriodicNotificationCalled);
        reset();

        // use short array as buffer
        short[] shortData = new short[BUFFER_SIZE];
        time = System.currentTimeMillis();
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        while (System.currentTimeMillis() - time < RECORD_TIME) {
            Thread.sleep(SLEEP_TIME);
            mAudioRecord.read(shortData, 0, BUFFER_SIZE);
        }
        mAudioRecord.stop();
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());
        assertTrue(mIsOnMarkerReachedCalled);
        assertTrue(mIsOnPeriodicNotificationCalled);
        reset();

        // use ByteBuffer as buffer
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        time = System.currentTimeMillis();
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        while (System.currentTimeMillis() - time < RECORD_TIME) {
            Thread.sleep(SLEEP_TIME);
            mAudioRecord.read(byteBuffer, BUFFER_SIZE);
        }
        mAudioRecord.stop();
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());
        assertTrue(mIsOnMarkerReachedCalled);
        assertTrue(mIsOnPeriodicNotificationCalled);
        reset();

        // use handler
        final Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                mIsHandleMessageCalled = true;
                super.handleMessage(msg);
            }
        };

        mAudioRecord.setRecordPositionUpdateListener(listener, handler);
        time = System.currentTimeMillis();
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        while (System.currentTimeMillis() - time < RECORD_TIME) {
            Thread.sleep(SLEEP_TIME);
            mAudioRecord.read(byteData, 0, BUFFER_SIZE);
        }
        mAudioRecord.stop();
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());
        assertTrue(mIsOnMarkerReachedCalled);
        assertTrue(mIsOnPeriodicNotificationCalled);
        // The handler argument is only ever used for getting the associated Looper
        assertFalse(mIsHandleMessageCalled);

        mAudioRecord.release();
        assertEquals(AudioRecord.STATE_UNINITIALIZED, mAudioRecord.getState());
    }

    @Test
    public void testAudioRecordResamplerMono8Bit() throws Exception {
        doTest("resampler_mono_8bit", true /*localRecord*/, false /*customHandler*/,
                1 /*periodsPerSecond*/, 1 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/,  false /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 88200 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_8BIT);
    }

    @Test
    public void testAudioRecordResamplerStereo8Bit() throws Exception {
        doTest("resampler_stereo_8bit", true /*localRecord*/, false /*customHandler*/,
                0 /*periodsPerSecond*/, 3 /*markerPeriodsPerSecond*/,
                true /*useByteBuffer*/,  true /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 45000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_8BIT);
    }

    @Presubmit
    @Test
    public void testAudioRecordLocalMono16BitShort() throws Exception {
        doTest("local_mono_16bit_short", true /*localRecord*/, false /*customHandler*/,
                30 /*periodsPerSecond*/, 2 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, true /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 8000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 500 /*TEST_TIME_MS*/);
    }

    @Test
    public void testAudioRecordLocalMono16Bit() throws Exception {
        doTest("local_mono_16bit", true /*localRecord*/, false /*customHandler*/,
                30 /*periodsPerSecond*/, 2 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, true /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 8000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    }

    @Test
    public void testAudioRecordStereo16Bit() throws Exception {
        doTest("stereo_16bit", false /*localRecord*/, false /*customHandler*/,
                2 /*periodsPerSecond*/, 2 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, false /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 17000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    }

    @Test
    public void testAudioRecordMonoFloat() throws Exception {
        doTest("mono_float", false /*localRecord*/, true /*customHandler*/,
                30 /*periodsPerSecond*/, 2 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, true /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 32000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT);
    }

    @Test
    public void testAudioRecordLocalNonblockingStereoFloat() throws Exception {
        doTest("local_nonblocking_stereo_float", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, false /*blocking*/,
                false /*auditRecording*/, false /*isChannelIndex*/, 48000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
    }

    // Audit modes work best with non-blocking mode
    @Test
    public void testAudioRecordAuditByteBufferResamplerStereoFloat() throws Exception {
        if (isLowRamDevice()) {
            return; // skip. FIXME: reenable when AF memory allocation is updated.
        }
        doTest("audit_byte_buffer_resampler_stereo_float",
                false /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                true /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, false /*isChannelIndex*/, 96000 /*TEST_SR*/,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
    }

    @Test
    public void testAudioRecordAuditChannelIndexMonoFloat() throws Exception {
        doTest("audit_channel_index_mono_float", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, true /*isChannelIndex*/, 47000 /*TEST_SR*/,
                (1 << 0) /* 1 channel */, AudioFormat.ENCODING_PCM_FLOAT);
    }

    // Audit buffers can run out of space with high sample rate,
    // so keep the channels and pcm encoding low
    @Test
    public void testAudioRecordAuditChannelIndex2() throws Exception {
        if (isLowRamDevice()) {
            return; // skip. FIXME: reenable when AF memory allocation is updated.
        }
        doTest("audit_channel_index_2", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, true /*isChannelIndex*/, 192000 /*TEST_SR*/,
                (1 << 0) | (1 << 2) /* 2 channels, gap in middle */,
                AudioFormat.ENCODING_PCM_8BIT);
    }

    // Audit buffers can run out of space with high numbers of channels,
    // so keep the sample rate low.
    @Test
    public void testAudioRecordAuditChannelIndex5() throws Exception {
        doTest("audit_channel_index_5", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, true /*isChannelIndex*/, 16000 /*TEST_SR*/,
                (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4)  /* 5 channels */,
                AudioFormat.ENCODING_PCM_16BIT);
    }

    // Audit buffers can run out of space with high numbers of channels,
    // so keep the sample rate low.
    // This tests the maximum reported Mixed PCM channel capability
    // for AudioRecord and AudioTrack.
    @Test
    public void testAudioRecordAuditChannelIndexMax() throws Exception {
        // We skip this test for isLowRamDevice(s).
        // Otherwise if the build reports a high PCM channel count capability,
        // we expect this CTS test to work at 16kHz.
        if (isLowRamDevice()) {
            return; // skip. FIXME: reenable when AF memory allocation is updated.
        }
        final int maxChannels = AudioSystem.OUT_CHANNEL_COUNT_MAX; // FCC_LIMIT
        doTest("audit_channel_index_max", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                false /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, true /*isChannelIndex*/, 16000 /*TEST_SR*/,
                (1 << maxChannels) - 1,
                AudioFormat.ENCODING_PCM_16BIT);
    }

    // Audit buffers can run out of space with high numbers of channels,
    // so keep the sample rate low.
    @Test
    public void testAudioRecordAuditChannelIndex3() throws Exception {
        doTest("audit_channel_index_3", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                true /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, true /*isChannelIndex*/, 16000 /*TEST_SR*/,
                (1 << 0) | (1 << 1) | (1 << 2)  /* 3 channels */,
                AudioFormat.ENCODING_PCM_24BIT_PACKED);
    }

    // Audit buffers can run out of space with high numbers of channels,
    // so keep the sample rate low.
    @Test
    public void testAudioRecordAuditChannelIndex1() throws Exception {
        doTest("audit_channel_index_1", true /*localRecord*/, true /*customHandler*/,
                2 /*periodsPerSecond*/, 0 /*markerPeriodsPerSecond*/,
                true /*useByteBuffer*/, false /*blocking*/,
                true /*auditRecording*/, true /*isChannelIndex*/, 24000 /*TEST_SR*/,
                (1 << 0)  /* 1 channels */,
                AudioFormat.ENCODING_PCM_32BIT);
    }

    // Test AudioRecord.Builder to verify the observed configuration of an AudioRecord built with
    // an empty Builder matches the documentation / expected values
    @Test
    public void testAudioRecordBuilderDefault() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        // constants for test
        final String TEST_NAME = "testAudioRecordBuilderDefault";
        // expected values below match the AudioRecord.Builder documentation
        final int expectedCapturePreset = MediaRecorder.AudioSource.DEFAULT;
        final int expectedChannel = AudioFormat.CHANNEL_IN_MONO;
        final int expectedEncoding = AudioFormat.ENCODING_PCM_16BIT;
        final int expectedState = AudioRecord.STATE_INITIALIZED;
        // use builder with default values
        final AudioRecord rec = new AudioRecord.Builder().build();
        // save results
        final int observedSource = rec.getAudioSource();
        final int observedChannel = rec.getChannelConfiguration();
        final int observedEncoding = rec.getAudioFormat();
        final int observedState = rec.getState();
        // release recorder before the test exits (either successfully or with an exception)
        rec.release();
        // compare results
        assertEquals(TEST_NAME + ": default capture preset", expectedCapturePreset, observedSource);
        assertEquals(TEST_NAME + ": default channel config", expectedChannel, observedChannel);
        assertEquals(TEST_NAME + ": default encoding", expectedEncoding, observedEncoding);
        assertEquals(TEST_NAME + ": state", expectedState, observedState);
    }

    // Test AudioRecord.Builder to verify the observed configuration of an AudioRecord built with
    // an incomplete AudioFormat matches the documentation / expected values
    @Test
    public void testAudioRecordBuilderPartialFormat() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        // constants for test
        final String TEST_NAME = "testAudioRecordBuilderPartialFormat";
        final int expectedRate = 16000;
        final int expectedState = AudioRecord.STATE_INITIALIZED;
        // expected values below match the AudioRecord.Builder documentation
        final int expectedChannel = AudioFormat.CHANNEL_IN_MONO;
        final int expectedEncoding = AudioFormat.ENCODING_PCM_16BIT;
        // use builder with a partial audio format
        final AudioRecord rec = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(expectedRate).build())
                .build();
        // save results
        final int observedRate = rec.getSampleRate();
        final int observedChannel = rec.getChannelConfiguration();
        final int observedEncoding = rec.getAudioFormat();
        final int observedState = rec.getState();
        // release recorder before the test exits (either successfully or with an exception)
        rec.release();
        // compare results
        assertEquals(TEST_NAME + ": configured rate", expectedRate, observedRate);
        assertEquals(TEST_NAME + ": default channel config", expectedChannel, observedChannel);
        assertEquals(TEST_NAME + ": default encoding", expectedEncoding, observedEncoding);
        assertEquals(TEST_NAME + ": state", expectedState, observedState);
    }

    // Test AudioRecord.Builder to verify the observed configuration of an AudioRecord matches
    // the parameters used in the builder
    @Test
    public void testAudioRecordBuilderParams() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        // constants for test
        final String TEST_NAME = "testAudioRecordBuilderParams";
        final int expectedRate = 8000;
        final int expectedChannel = AudioFormat.CHANNEL_IN_MONO;
        final int expectedChannelCount = 1;
        final int expectedEncoding = AudioFormat.ENCODING_PCM_16BIT;
        final int expectedSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
        final int expectedState = AudioRecord.STATE_INITIALIZED;
        // use builder with expected parameters
        final AudioRecord rec = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(expectedRate)
                        .setChannelMask(expectedChannel)
                        .setEncoding(expectedEncoding)
                        .build())
                .setAudioSource(expectedSource)
                .build();
        // save results
        final int observedRate = rec.getSampleRate();
        final int observedChannel = rec.getChannelConfiguration();
        final int observedChannelCount = rec.getChannelCount();
        final int observedEncoding = rec.getAudioFormat();
        final int observedSource = rec.getAudioSource();
        final int observedState = rec.getState();
        // release recorder before the test exits (either successfully or with an exception)
        rec.release();
        // compare results
        assertEquals(TEST_NAME + ": configured rate", expectedRate, observedRate);
        assertEquals(TEST_NAME + ": configured channel config", expectedChannel, observedChannel);
        assertEquals(TEST_NAME + ": configured encoding", expectedEncoding, observedEncoding);
        assertEquals(TEST_NAME + ": implicit channel count", expectedChannelCount,
                observedChannelCount);
        assertEquals(TEST_NAME + ": configured source", expectedSource, observedSource);
        assertEquals(TEST_NAME + ": state", expectedState, observedState);
    }
    // Test AudioRecord.Builder.setRequestHotwordStream, and hotword capture
    @Test
    public void testAudioRecordBuilderHotword() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        // Verify typical behavior continues to work, and clearing works
        AudioRecord regularRecord = new AudioRecord.Builder()
                                    .setRequestHotwordStream(true)
                                    .setRequestHotwordStream(false)
                                    .build();

        assertEquals(regularRecord.getState(), AudioRecord.STATE_INITIALIZED);
        assertFalse(regularRecord.isHotwordStream());
        assertFalse(regularRecord.isHotwordLookbackStream());
        regularRecord.startRecording();
        regularRecord.read(ByteBuffer.allocateDirect(4096), 4096);
        regularRecord.stop();
        regularRecord.release();

        regularRecord = new AudioRecord.Builder()
                                    .setRequestHotwordLookbackStream(true)
                                    .setRequestHotwordLookbackStream(false)
                                    .build();

        assertEquals(regularRecord.getState(), AudioRecord.STATE_INITIALIZED);
        assertFalse(regularRecord.isHotwordStream());
        assertFalse(regularRecord.isHotwordLookbackStream());
        regularRecord.startRecording();
        regularRecord.read(ByteBuffer.allocateDirect(4096), 4096);
        regularRecord.stop();
        regularRecord.release();

        // Should fail due to incompatible arguments
        assertThrows(UnsupportedOperationException.class,
               () ->  new AudioRecord.Builder()
                                    .setRequestHotwordStream(true)
                                    .setRequestHotwordLookbackStream(true)
                                    .build());

        // Should fail due to permission issues
        assertThrows(UnsupportedOperationException.class,
                   () -> new AudioRecord.Builder()
                                    .setRequestHotwordStream(true)
                                    .build());
        assertThrows(UnsupportedOperationException.class,
                   () -> new AudioRecord.Builder()
                                    .setRequestHotwordLookbackStream(true)
                                    .build());

        // Adopt permissions to access query APIs and test functionality
        InstrumentationRegistry.getInstrumentation()
                               .getUiAutomation()
                               .adoptShellPermissionIdentity(
                                Manifest.permission.CAPTURE_AUDIO_HOTWORD);


        for (final boolean lookbackOn : new boolean[] { false, true} ) {
            AudioRecord audioRecord = null;
            if (!mAudioManager.isHotwordStreamSupported(lookbackOn)) {
                // Hardware does not support capturing hotword content
                continue;
            }
            try {
                AudioRecord.Builder builder = new AudioRecord.Builder();
                if (lookbackOn) {
                    builder.setRequestHotwordLookbackStream(true);
                } else {
                    builder.setRequestHotwordStream(true);
                }
                audioRecord = builder.build();
                if (lookbackOn) {
                    assertTrue(audioRecord.isHotwordLookbackStream());
                } else {
                    assertTrue(audioRecord.isHotwordStream());
                }
                audioRecord.startRecording();
                audioRecord.read(ByteBuffer.allocateDirect(4096), 4096);
                audioRecord.stop();
            } finally {
                if (audioRecord != null) {
                    audioRecord.release();
                }
            }
        }
        InstrumentationRegistry.getInstrumentation()
                               .getUiAutomation()
                               .dropShellPermissionIdentity();
    }

    // Test AudioRecord to ensure we can build after a failure.
    @Test
    public void testAudioRecordBufferSize() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        // constants for test
        final String TEST_NAME = "testAudioRecordBufferSize";

        // use builder with parameters that should fail
        final int superBigBufferSize = 1 << 28;
        try {
            final AudioRecord record = new AudioRecord.Builder()
                .setBufferSizeInBytes(superBigBufferSize)
                .build();
            record.release();
            fail(TEST_NAME + ": should throw exception on failure");
        } catch (UnsupportedOperationException e) {
            ;
        }

        // we should be able to create again with minimum buffer size
        final int verySmallBufferSize = 2 * 3 * 4; // frame size multiples
        final AudioRecord record2 = new AudioRecord.Builder()
                .setBufferSizeInBytes(verySmallBufferSize)
                .build();

        final int observedState2 = record2.getState();
        final int observedBufferSize2 = record2.getBufferSizeInFrames();
        record2.release();

        // succeeds for minimum buffer size
        assertEquals(TEST_NAME + ": state", AudioRecord.STATE_INITIALIZED, observedState2);
        // should force the minimum size buffer which is > 0
        assertTrue(TEST_NAME + ": buffer frame count", observedBufferSize2 > 0);
    }

    @Test
    public void testTimestamp() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        final String TEST_NAME = "testTimestamp";
        AudioRecord record = null;

        try {
            final int NANOS_PER_MILLISECOND = 1000000;
            final long RECORD_TIME_MS = 2000;
            final long RECORD_TIME_NS = RECORD_TIME_MS * NANOS_PER_MILLISECOND;
            final int RECORD_ENCODING = AudioFormat.ENCODING_PCM_16BIT; // fixed at this time.
            final int RECORD_CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;
            final int RECORD_SAMPLE_RATE = 23456;  // requires resampling
            record = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RECORD_SAMPLE_RATE)
                            .setChannelMask(RECORD_CHANNEL_MASK)
                            .setEncoding(RECORD_ENCODING)
                            .build())
                    .build();

            // For our tests, we could set test duration by timed sleep or by # frames received.
            // Since we don't know *exactly* when AudioRecord actually begins recording,
            // we end the test by # frames read.
            final int numChannels =
                    AudioFormat.channelCountFromInChannelMask(RECORD_CHANNEL_MASK);
            final int bytesPerSample = AudioFormat.getBytesPerSample(RECORD_ENCODING);
            final int bytesPerFrame = numChannels * bytesPerSample;
            // careful about integer overflow in the formula below:
            final int targetFrames =
                    (int)((long)RECORD_TIME_MS * RECORD_SAMPLE_RATE / 1000);
            final int targetSamples = targetFrames * numChannels;
            final int BUFFER_FRAMES = 512;
            final int BUFFER_SAMPLES = BUFFER_FRAMES * numChannels;

            final int tries = 2;
            for (int i = 0; i < tries; ++i) {
                final long trackStartTimeNs = System.nanoTime();
                final long trackStartTimeBootNs = android.os.SystemClock.elapsedRealtimeNanos();

                record.startRecording();

                final AudioTimestamp ts = new AudioTimestamp();
                int samplesRead = 0;
                // For 16 bit data, use shorts
                final short[] shortData = new short[BUFFER_SAMPLES];
                final AudioHelper.TimestampVerifier tsVerifier =
                        new AudioHelper.TimestampVerifier(TAG, RECORD_SAMPLE_RATE,
                                0 /* startFrames */, isProAudioDevice());

                while (samplesRead < targetSamples) {
                    final int amount = samplesRead == 0 ? numChannels :
                            Math.min(BUFFER_SAMPLES, targetSamples - samplesRead);
                    final int ret = record.read(shortData, 0, amount);
                    assertEquals("read incorrect amount", amount, ret);
                    // timestamps follow a different path than data, so it is conceivable
                    // that first data arrives before the first timestamp is ready.

                    if (record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC)
                            == AudioRecord.SUCCESS) {
                        tsVerifier.add(ts);
                    }
                    samplesRead += ret;
                }
                record.stop();

                // stop is synchronous, but need not be in the future.
                final long SLEEP_AFTER_STOP_FOR_INACTIVITY_MS = 1000;
                Thread.sleep(SLEEP_AFTER_STOP_FOR_INACTIVITY_MS);

                AudioTimestamp stopTs = new AudioTimestamp();
                AudioTimestamp stopTsBoot = new AudioTimestamp();

                assertEquals(AudioRecord.SUCCESS,
                        record.getTimestamp(stopTs, AudioTimestamp.TIMEBASE_MONOTONIC));
                assertEquals(AudioRecord.SUCCESS,
                        record.getTimestamp(stopTsBoot, AudioTimestamp.TIMEBASE_BOOTTIME));

                // printTimestamp("timestamp Monotonic", ts);
                // printTimestamp("timestamp Boottime", tsBoot);
                // Log.d(TEST_NAME, "startTime Monotonic " + startTime);
                // Log.d(TEST_NAME, "startTime Boottime " + startTimeBoot);

                assertEquals(stopTs.framePosition, stopTsBoot.framePosition);
                assertTrue(stopTs.framePosition >= targetFrames);
                assertTrue(stopTs.nanoTime - trackStartTimeNs > RECORD_TIME_NS);
                assertTrue(stopTsBoot.nanoTime - trackStartTimeBootNs > RECORD_TIME_NS);

                tsVerifier.verifyAndLog(trackStartTimeNs, "test_timestamp" /* logName */);
            }
        } finally {
            if (record != null) {
                record.release();
                record = null;
            }
        }
    }

    @Test
    public void testRecordNoDataForIdleUids() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        AudioRecord recorder = null;
        String packageName = InstrumentationRegistry.getTargetContext().getPackageName();
        int currentUserId = Process.myUserHandle().getIdentifier();

        // We will record audio for 20 sec from active and idle state expecting
        // the recording from active state to have data while from idle silence.
        try {
            // Ensure no race and UID active
            makeMyUidStateActive(packageName, currentUserId);

            // Setup a recorder
            final AudioRecord candidateRecorder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setBufferSizeInBytes(1024)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(8000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build())
                    .build();

            // Unleash it :P
            candidateRecorder.startRecording();
            recorder = candidateRecorder;

            final int sampleCount = AudioHelper.frameCountFromMsec(6000,
                    candidateRecorder.getFormat()) * candidateRecorder.getFormat()
                    .getChannelCount();
            final ShortBuffer buffer = ShortBuffer.allocate(sampleCount);

            // Read five seconds of data
            readDataTimed(recorder, 5000, buffer);
            // Ensure we read non-empty bytes. Some systems only
            // emulate audio devices and do not provide any actual audio data.
            if (isAudioSilent(buffer)) {
                Log.w(TAG, "Recording does not produce audio data");
                return;
            }

            // Start clean
            buffer.clear();
            // Force idle the package
            makeMyUidStateIdle(packageName, currentUserId);
            // Read five seconds of data
            readDataTimed(recorder, 5000, buffer);
            // Ensure we read empty bytes
            assertTrue("Recording was not silenced while UID idle", isAudioSilent(buffer));

            // Start clean
            buffer.clear();
            // Reset to active
            makeMyUidStateActive(packageName, currentUserId);
            // Read five seconds of data
            readDataTimed(recorder, 5000, buffer);
            // Ensure we read non-empty bytes
            assertFalse("Recording was silenced while UID active", isAudioSilent(buffer));
        } finally {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
            resetMyUidState(packageName, currentUserId);
        }
    }

    @Test
    public void testRestrictedAudioSourcePermissions() throws Exception {
        // Make sure that the following audio sources cannot be used by apps that
        // don't have the CAPTURE_AUDIO_OUTPUT permissions:
        // - VOICE_CALL,
        // - VOICE_DOWNLINK
        // - VOICE_UPLINK
        // - REMOTE_SUBMIX
        // - ECHO_REFERENCE  - 1997
        // - RADIO_TUNER - 1998
        // - HOTWORD - 1999
        // The attempt to build an AudioRecord with those sources should throw either
        // UnsupportedOperationException or IllegalArgumentException exception.
        final int[] restrictedAudioSources = new int [] {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.REMOTE_SUBMIX,
            1997,
            1998,
            1999
        };

        for (int source : restrictedAudioSources) {
            // AudioRecord.Builder should fail when trying to use
            // one of the voice call audio sources.
            try {
                AudioRecord ar = new AudioRecord.Builder()
                 .setAudioSource(source)
                 .setAudioFormat(new AudioFormat.Builder()
                         .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                         .setSampleRate(8000)
                         .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                         .build())
                 .build();
                fail("testRestrictedAudioSourcePermissions: no exception thrown for source: "
                        + source);
            } catch (Exception e) {
                Log.i(TAG, "Exception: " + e);
                if (!UnsupportedOperationException.class.isInstance(e)
                        && !IllegalArgumentException.class.isInstance(e)) {
                    fail("testRestrictedAudioSourcePermissions: no exception thrown for source: "
                        + source + " Exception:" + e);
                }
            }
        }
    }

    @Test
    public void testMediaMetrics() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        AudioRecord record = null;
        try {
            final int RECORD_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
            final int RECORD_CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
            final int RECORD_SAMPLE_RATE = 8000;
            final AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(RECORD_SAMPLE_RATE)
                    .setChannelMask(RECORD_CHANNEL_MASK)
                    .setEncoding(RECORD_ENCODING)
                    .build();

            // Setup a recorder
            record = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .build();

            final PersistableBundle metrics = record.getMetrics();

            assertNotNull("null metrics", metrics);
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.ENCODING,
                    new String("AUDIO_FORMAT_PCM_16_BIT"));
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.SOURCE,
                    new String("AUDIO_SOURCE_MIC"));
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.SAMPLERATE,
                    new Integer(RECORD_SAMPLE_RATE));
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.CHANNELS,
                    new Integer(AudioFormat.channelCountFromInChannelMask(RECORD_CHANNEL_MASK)));

            // deprecated, value ignored.
            AudioHelper.assertMetricsKey(metrics, AudioRecord.MetricsConstants.LATENCY);

            // TestApi:
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.CHANNEL_MASK,
                    new Long(RECORD_CHANNEL_MASK));
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.FRAME_COUNT,
                    new Integer(record.getBufferSizeInFrames()));
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.DURATION_MS,
                    new Double(0.));
            AudioHelper.assertMetricsKeyEquals(metrics, AudioRecord.MetricsConstants.START_COUNT,
                    new Long(0));

            // TestApi: no particular value checking.
            AudioHelper.assertMetricsKey(metrics, AudioRecord.MetricsConstants.PORT_ID);
            AudioHelper.assertMetricsKey(metrics, AudioRecord.MetricsConstants.ATTRIBUTES);
        } finally {
            if (record != null) {
                record.release();
            }
        }
    }

    private void printMicrophoneInfo(MicrophoneInfo microphone) {
        Log.i(TAG, "deviceId:" + microphone.getDescription());
        Log.i(TAG, "portId:" + microphone.getId());
        Log.i(TAG, "type:" + microphone.getType());
        Log.i(TAG, "address:" + microphone.getAddress());
        Log.i(TAG, "deviceLocation:" + microphone.getLocation());
        Log.i(TAG, "deviceGroup:" + microphone.getGroup()
            + " index:" + microphone.getIndexInTheGroup());
        MicrophoneInfo.Coordinate3F position = microphone.getPosition();
        Log.i(TAG, "position:" + position.x + "," + position.y + "," + position.z);
        MicrophoneInfo.Coordinate3F orientation = microphone.getOrientation();
        Log.i(TAG, "orientation:" + orientation.x + "," + orientation.y + "," + orientation.z);
        Log.i(TAG, "frequencyResponse:" + microphone.getFrequencyResponse());
        Log.i(TAG, "channelMapping:" + microphone.getChannelMapping());
        Log.i(TAG, "sensitivity:" + microphone.getSensitivity());
        Log.i(TAG, "max spl:" + microphone.getMaxSpl());
        Log.i(TAG, "min spl:" + microphone.getMinSpl());
        Log.i(TAG, "directionality:" + microphone.getDirectionality());
        Log.i(TAG, "******");
    }

    @CddTest(requirement="5.4.1/C-1-4")
    @Test
    public void testGetActiveMicrophones() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        mAudioRecord.startRecording();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        List<MicrophoneInfo> activeMicrophones = mAudioRecord.getActiveMicrophones();
        assertTrue(activeMicrophones.size() > 0);
        for (MicrophoneInfo activeMicrophone : activeMicrophones) {
            printMicrophoneInfo(activeMicrophone);
        }
    }

    private Executor mExec = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    @Test
    public void testAudioRecordInfoCallback() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioRecordingConfigurationTest.MyAudioRecordingCallback callback =
                new AudioRecordingConfigurationTest.MyAudioRecordingCallback(
                        mAudioRecord.getAudioSessionId(), MediaRecorder.AudioSource.DEFAULT);
        mAudioRecord.registerAudioRecordingCallback(mExec, callback);
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());

        callback.await(TEST_TIMING_TOLERANCE_MS);
        assertTrue(callback.mCalled);
        assertTrue(callback.mConfigs.size() <= 1);
        if (callback.mConfigs.size() == 1) {
            checkRecordingConfig(callback.mConfigs.get(0));
        }

        Thread.sleep(RECORD_DURATION_MS);
        mAudioRecord.unregisterAudioRecordingCallback(callback);
    }

    @Test
    public void testGetActiveRecordingConfiguration() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());

        try {
            Thread.sleep(RECORD_DURATION_MS);
        } catch (InterruptedException e) {
        }

        AudioRecordingConfiguration config = mAudioRecord.getActiveRecordingConfiguration();
        checkRecordingConfig(config);

        mAudioRecord.release();
        // test no exception is thrown when querying immediately after release()
        // which is not a synchronous operation
        config = mAudioRecord.getActiveRecordingConfiguration();
        try {
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        } catch (InterruptedException e) {
        }
        assertNull("Recording configuration not null after release",
                mAudioRecord.getActiveRecordingConfiguration());
    }

    private static void checkRecordingConfig(AudioRecordingConfiguration config) {
        assertNotNull(config);
        AudioFormat format = config.getClientFormat();
        assertEquals(AudioFormat.CHANNEL_IN_MONO, format.getChannelMask());
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, format.getEncoding());
        assertEquals(SAMPLING_RATE_HZ, format.getSampleRate());
        assertEquals(MediaRecorder.AudioSource.MIC, config.getAudioSource());
        assertNotNull(config.getAudioDevice());
        assertNotNull(config.getClientEffects());
        assertNotNull(config.getEffects());
        // no requirement here, just testing the API
        config.isClientSilenced();
    }

    private AudioRecord createAudioRecord(
            int audioSource, int sampleRateInHz,
            int channelConfig, int audioFormat, int bufferSizeInBytes,
            boolean auditRecording, boolean isChannelIndex) {
        final AudioRecord record;
        if (auditRecording) {
            record = new AudioHelper.AudioRecordAudit(
                    audioSource, sampleRateInHz, channelConfig,
                    audioFormat, bufferSizeInBytes, isChannelIndex);
        } else if (isChannelIndex) {
            record = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setChannelIndexMask(channelConfig)
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRateInHz)
                            .build())
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build();
        } else {
            record = new AudioRecord(audioSource, sampleRateInHz, channelConfig,
                    audioFormat, bufferSizeInBytes);
        }

        // did we get the AudioRecord we expected?
        final AudioFormat format = record.getFormat();
        assertEquals(isChannelIndex ? channelConfig : AudioFormat.CHANNEL_INVALID,
                format.getChannelIndexMask());
        assertEquals(isChannelIndex ? AudioFormat.CHANNEL_INVALID : channelConfig,
                format.getChannelMask());
        assertEquals(audioFormat, format.getEncoding());
        assertEquals(sampleRateInHz, format.getSampleRate());
        final int frameSize =
                format.getChannelCount() * AudioFormat.getBytesPerSample(audioFormat);
        // our native frame count cannot be smaller than our minimum buffer size request.
        assertTrue(record.getBufferSizeInFrames() * frameSize >= bufferSizeInBytes);
        return record;
    }

    private void doTest(String reportName, boolean localRecord, boolean customHandler,
            int periodsPerSecond, int markerPeriodsPerSecond,
            boolean useByteBuffer, boolean blocking,
            final boolean auditRecording, final boolean isChannelIndex,
            final int TEST_SR, final int TEST_CONF, final int TEST_FORMAT) throws Exception {
        final int TEST_TIME_MS = auditRecording ? 10000 : 2000;
        doTest(reportName, localRecord, customHandler, periodsPerSecond, markerPeriodsPerSecond,
                useByteBuffer, blocking, auditRecording, isChannelIndex,
                TEST_SR, TEST_CONF, TEST_FORMAT, TEST_TIME_MS);
    }
    private void doTest(String reportName, boolean localRecord, boolean customHandler,
            int periodsPerSecond, int markerPeriodsPerSecond,
            boolean useByteBuffer, boolean blocking,
            final boolean auditRecording, final boolean isChannelIndex,
            final int TEST_SR, final int TEST_CONF, final int TEST_FORMAT, final int TEST_TIME_MS)
            throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        // audit recording plays back recorded audio, so use longer test timing
        final int TEST_SOURCE = MediaRecorder.AudioSource.DEFAULT;
        mIsHandleMessageCalled = false;

        // For channelIndex use one frame in bytes for buffer size.
        // This is adjusted to the minimum buffer size by native code.
        final int bufferSizeInBytes = isChannelIndex ?
                (AudioFormat.getBytesPerSample(TEST_FORMAT)
                        * AudioFormat.channelCountFromInChannelMask(TEST_CONF)) :
                AudioRecord.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        assertTrue(bufferSizeInBytes > 0);

        final AudioRecord record;
        final AudioHelper
                .MakeSomethingAsynchronouslyAndLoop<AudioRecord> makeSomething;

        if (localRecord) {
            makeSomething = null;
            record = createAudioRecord(TEST_SOURCE, TEST_SR, TEST_CONF,
                    TEST_FORMAT, bufferSizeInBytes, auditRecording, isChannelIndex);
        } else {
            makeSomething =
                    new AudioHelper.MakeSomethingAsynchronouslyAndLoop<AudioRecord>(
                            new AudioHelper.MakesSomething<AudioRecord>() {
                                @Override
                                public AudioRecord makeSomething() {
                                    return createAudioRecord(TEST_SOURCE, TEST_SR, TEST_CONF,
                                            TEST_FORMAT, bufferSizeInBytes, auditRecording,
                                            isChannelIndex);
                                }
                            }
                            );
           // create AudioRecord on different thread's looper.
           record = makeSomething.make();
        }

        // AudioRecord creation may have silently failed, check state now
        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());

        final MockOnRecordPositionUpdateListener listener;
        if (customHandler) {
            listener = new MockOnRecordPositionUpdateListener(record, mHandler);
        } else {
            listener = new MockOnRecordPositionUpdateListener(record);
        }

        final int updatePeriodInFrames = (periodsPerSecond == 0)
                ? 0 : TEST_SR / periodsPerSecond;
        // After starting, there is no guarantee when the first frame of data is read.
        long firstSampleTime = 0;

        // blank final variables: all successful paths will initialize the times.
        // this must be declared here for visibility as they are set within the try block.
        final long endTime;
        final long startTime;
        final long stopRequestTime;
        final long stopTime;
        final long coldInputStartTime;

        try {
            if (markerPeriodsPerSecond != 0) {
                mMarkerPeriodInFrames = TEST_SR / markerPeriodsPerSecond;
                mMarkerPosition = mMarkerPeriodInFrames;
                assertEquals(AudioRecord.SUCCESS,
                        record.setNotificationMarkerPosition(mMarkerPosition));
            } else {
                mMarkerPeriodInFrames = 0;
            }

            assertEquals(AudioRecord.SUCCESS,
                    record.setPositionNotificationPeriod(updatePeriodInFrames));

            // at the start, there is no timestamp.
            AudioTimestamp startTs = new AudioTimestamp();
            assertEquals(AudioRecord.ERROR_INVALID_OPERATION,
                    record.getTimestamp(startTs, AudioTimestamp.TIMEBASE_MONOTONIC));
            assertEquals("invalid getTimestamp doesn't affect nanoTime", 0, startTs.nanoTime);

            listener.start(TEST_SR);
            record.startRecording();
            assertEquals(AudioRecord.RECORDSTATE_RECORDING, record.getRecordingState());
            startTime = System.currentTimeMillis();

            // For our tests, we could set test duration by timed sleep or by # frames received.
            // Since we don't know *exactly* when AudioRecord actually begins recording,
            // we end the test by # frames read.
            final int numChannels =  AudioFormat.channelCountFromInChannelMask(TEST_CONF);
            final int bytesPerSample = AudioFormat.getBytesPerSample(TEST_FORMAT);
            final int bytesPerFrame = numChannels * bytesPerSample;
            // careful about integer overflow in the formula below:
            final int targetFrames = (int)((long)TEST_TIME_MS * TEST_SR / 1000);
            final int targetSamples = targetFrames * numChannels;
            final int BUFFER_FRAMES = 512;
            final int BUFFER_SAMPLES = BUFFER_FRAMES * numChannels;
            // TODO: verify behavior when buffer size is not a multiple of frame size.

            int samplesRead = 0;
            // abstract out the buffer type used with lambda.
            final byte[] byteData = new byte[BUFFER_SAMPLES];
            final short[] shortData = new short[BUFFER_SAMPLES];
            final float[] floatData = new float[BUFFER_SAMPLES];
            final ByteBuffer byteBuffer =
                    ByteBuffer.allocateDirect(BUFFER_SAMPLES * bytesPerSample);
            BiFunction<Integer, Boolean, Integer> reader = null;

            // depending on the options, create a lambda to read data.
            if (useByteBuffer) {
                reader = (samples, blockForData) -> {
                    final int amount = samples * bytesPerSample;    // in bytes
                    // read always places data at the start of the byte buffer with
                    // position and limit are ignored.  test this by setting
                    // position and limit to arbitrary values here.
                    final int lastPosition = 7;
                    final int lastLimit = 13;
                    byteBuffer.position(lastPosition);
                    byteBuffer.limit(lastLimit);
                    final int ret = blockForData ? record.read(byteBuffer, amount) :
                            record.read(byteBuffer, amount, AudioRecord.READ_NON_BLOCKING);
                    return ret / bytesPerSample;
                };
            } else {
                switch (TEST_FORMAT) {
                    case AudioFormat.ENCODING_PCM_8BIT:
                        reader = (samples, blockForData) -> {
                            return blockForData ? record.read(byteData, 0, samples) :
                                    record.read(byteData, 0, samples,
                                            AudioRecord.READ_NON_BLOCKING);
                        };
                        break;
                    case AudioFormat.ENCODING_PCM_16BIT:
                        reader = (samples, blockForData) -> {
                            return blockForData ? record.read(shortData, 0, samples) :
                                    record.read(shortData, 0, samples,
                                            AudioRecord.READ_NON_BLOCKING);
                        };
                        break;
                    case AudioFormat.ENCODING_PCM_FLOAT:
                        reader = (samples, blockForData) -> {
                            return record.read(floatData, 0, samples,
                                    blockForData ? AudioRecord.READ_BLOCKING
                                            : AudioRecord.READ_NON_BLOCKING);
                        };
                        break;
                }
            }

            while (samplesRead < targetSamples) {
                // the first time through, we read a single frame.
                // this sets the recording anchor position.
                final int amount = samplesRead == 0 ? numChannels :
                    Math.min(BUFFER_SAMPLES, targetSamples - samplesRead);
                final int ret = reader.apply(amount, blocking);
                if (blocking) {
                    assertEquals("blocking reads should return amount requested", amount, ret);
                } else {
                    assertTrue("non-blocking reads should return amount in range: " +
                            "0 <= " + ret + " <= " + amount,
                            0 <= ret && ret <= amount);
                }
                if (samplesRead == 0 && ret > 0) {
                    firstSampleTime = System.currentTimeMillis();
                }
                samplesRead += ret;
                if (startTs.nanoTime == 0 && ret > 0 &&
                        record.getTimestamp(startTs, AudioTimestamp.TIMEBASE_MONOTONIC)
                                == AudioRecord.SUCCESS) {
                    assertTrue("expecting valid timestamp with nonzero nanoTime",
                            startTs.nanoTime > 0);
                }
            }

            // We've read all the frames, now check the record timing.
            endTime = System.currentTimeMillis();

            coldInputStartTime = firstSampleTime - startTime;
            //Log.d(TAG, "first sample time " + coldInputStartTime
            //        + " test time " + (endTime - firstSampleTime));

            if (coldInputStartTime > 200) {
                Log.w(TAG, "cold input start time way too long "
                        + coldInputStartTime + " > 200ms");
            } else if (coldInputStartTime > 100) {
                Log.w(TAG, "cold input start time too long "
                        + coldInputStartTime + " > 100ms");
            }

            final int COLD_INPUT_START_TIME_LIMIT_MS = 5000;
            assertTrue("track must start within " + COLD_INPUT_START_TIME_LIMIT_MS + " millis",
                    coldInputStartTime < COLD_INPUT_START_TIME_LIMIT_MS);

            // Verify recording completes within 50 ms of expected test time (typical 20ms)
            final int RECORDING_TIME_TOLERANCE_MS = auditRecording ?
                (isLowLatencyDevice() ? 1000 : 2000) : (isLowLatencyDevice() ? 50 : 400);
            assertEquals("recording must complete within " + RECORDING_TIME_TOLERANCE_MS
                    + " of expected test time",
                    TEST_TIME_MS, endTime - firstSampleTime, RECORDING_TIME_TOLERANCE_MS);

            // Even though we've read all the frames we want, the events may not be sent to
            // the listeners (events are handled through a separate internal callback thread).
            // One must sleep to make sure the last event(s) come in.
            Thread.sleep(30);

            stopRequestTime = System.currentTimeMillis();
            record.stop();
            assertEquals("state should be RECORDSTATE_STOPPED after stop()",
                    AudioRecord.RECORDSTATE_STOPPED, record.getRecordingState());

            stopTime = System.currentTimeMillis();

            // stop listening - we should be done.
            // Caution M behavior and likely much earlier:
            // we assume no events can happen after stop(), but this may not
            // always be true as stop can take 100ms to complete (as it may disable
            // input recording on the hal); thus the event handler may be block with
            // valid events, issuing right after stop completes. Except for those events,
            // no other events should show up after stop.
            // This behavior may change in the future but we account for it here in testing.
            final long SLEEP_AFTER_STOP_FOR_EVENTS_MS = 30;
            Thread.sleep(SLEEP_AFTER_STOP_FOR_EVENTS_MS);
            listener.stop();

            // get stop timestamp
            AudioTimestamp stopTs = new AudioTimestamp();
            assertEquals("should successfully get timestamp after stop",
                    AudioRecord.SUCCESS,
                    record.getTimestamp(stopTs, AudioTimestamp.TIMEBASE_MONOTONIC));
            AudioTimestamp stopTsBoot = new AudioTimestamp();
            assertEquals("should successfully get boottime timestamp after stop",
                    AudioRecord.SUCCESS,
                    record.getTimestamp(stopTsBoot, AudioTimestamp.TIMEBASE_BOOTTIME));

            // printTimestamp("startTs", startTs);
            // printTimestamp("stopTs", stopTs);
            // printTimestamp("stopTsBoot", stopTsBoot);
            // Log.d(TAG, "time Monotonic " + System.nanoTime());
            // Log.d(TAG, "time Boottime " + SystemClock.elapsedRealtimeNanos());

            // stop should not reset timestamps
            assertTrue("stop timestamp position should be no less than frames read",
                    stopTs.framePosition >= targetFrames);
            assertEquals("stop timestamp position should be same "
                    + "between monotonic and boot timestamps",
                    stopTs.framePosition, stopTsBoot.framePosition);
            assertTrue("stop timestamp nanoTime must be set", stopTs.nanoTime > 0);

            // timestamps follow a different path than data, so it is conceivable
            // that first data arrives before the first timestamp is ready.
            assertTrue("no start timestamp read", startTs.nanoTime > 0);

            verifyContinuousTimestamps(startTs, stopTs, TEST_SR);

            // clean up
            if (makeSomething != null) {
                makeSomething.join();
            }

        } finally {
            listener.release();
            // we must release the record immediately as it is a system-wide
            // resource needed for other tests.
            record.release();
        }

        final int markerPeriods = markerPeriodsPerSecond * TEST_TIME_MS / 1000;
        final int updatePeriods = periodsPerSecond * TEST_TIME_MS / 1000;
        final int markerPeriodsMax =
                markerPeriodsPerSecond * (int)(stopTime - firstSampleTime) / 1000 + 1;
        final int updatePeriodsMax =
                periodsPerSecond * (int)(stopTime - firstSampleTime) / 1000 + 1;

        // collect statistics
        final ArrayList<Integer> markerList = listener.getMarkerList();
        final ArrayList<Integer> periodicList = listener.getPeriodicList();
        // verify count of markers and periodic notifications.
        // there could be an extra notification since we don't stop() immediately
        // rather wait for potential events to come in.
        //Log.d(TAG, "markerPeriods " + markerPeriods +
        //        " markerPeriodsReceived " + markerList.size());
        //Log.d(TAG, "updatePeriods " + updatePeriods +
        //        " updatePeriodsReceived " + periodicList.size());
        if (isLowLatencyDevice()) {
            assertTrue(TAG + ": markerPeriods " + markerPeriods +
                    " <= markerPeriodsReceived " + markerList.size() +
                    " <= markerPeriodsMax " + markerPeriodsMax,
                    markerPeriods <= markerList.size()
                    && markerList.size() <= markerPeriodsMax);
            assertTrue(TAG + ": updatePeriods " + updatePeriods +
                   " <= updatePeriodsReceived " + periodicList.size() +
                   " <= updatePeriodsMax " + updatePeriodsMax,
                    updatePeriods <= periodicList.size()
                    && periodicList.size() <= updatePeriodsMax);
        }

        // Since we don't have accurate positioning of the start time of the recorder,
        // and there is no record.getPosition(), we consider only differential timing
        // from the first marker or periodic event.
        final int toleranceInFrames = TEST_SR * 80 / 1000; // 80 ms
        final int testTimeInFrames = (int)((long)TEST_TIME_MS * TEST_SR / 1000);

        AudioHelper.Statistics markerStat = new AudioHelper.Statistics();
        for (int i = 1; i < markerList.size(); ++i) {
            final int expected = mMarkerPeriodInFrames * i;
            if (markerList.get(i) > testTimeInFrames) {
                break; // don't consider any notifications when we might be stopping.
            }
            final int actual = markerList.get(i) - markerList.get(0);
            //Log.d(TAG, "Marker: " + i + " expected(" + expected + ")  actual(" + actual
            //        + ")  diff(" + (actual - expected) + ")"
            //        + " tolerance " + toleranceInFrames);
            if (isLowLatencyDevice()) {
                assertEquals(expected, actual, toleranceInFrames);
            }
            markerStat.add((double)(actual - expected) * 1000 / TEST_SR);
        }

        AudioHelper.Statistics periodicStat = new AudioHelper.Statistics();
        for (int i = 1; i < periodicList.size(); ++i) {
            final int expected = updatePeriodInFrames * i;
            if (periodicList.get(i) > testTimeInFrames) {
                break; // don't consider any notifications when we might be stopping.
            }
            final int actual = periodicList.get(i) - periodicList.get(0);
            //Log.d(TAG, "Update: " + i + " expected(" + expected + ")  actual(" + actual
            //        + ")  diff(" + (actual - expected) + ")"
            //        + " tolerance " + toleranceInFrames);
            if (isLowLatencyDevice()) {
                assertEquals(expected, actual, toleranceInFrames);
            }
            periodicStat.add((double)(actual - expected) * 1000 / TEST_SR);
        }

        // report this
        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, reportName);
        log.addValue("start_recording_lag", coldInputStartTime, ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("stop_execution_time", stopTime - stopRequestTime, ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("total_record_time_expected", TEST_TIME_MS, ResultType.NEUTRAL, ResultUnit.MS);
        log.addValue("total_record_time_actual", endTime - firstSampleTime, ResultType.NEUTRAL,
                ResultUnit.MS);
        log.addValue("total_markers_expected", markerPeriods, ResultType.NEUTRAL, ResultUnit.COUNT);
        log.addValue("total_markers_actual", markerList.size(), ResultType.NEUTRAL,
                ResultUnit.COUNT);
        log.addValue("total_periods_expected", updatePeriods, ResultType.NEUTRAL, ResultUnit.COUNT);
        log.addValue("total_periods_actual", periodicList.size(), ResultType.NEUTRAL,
                ResultUnit.COUNT);
        log.addValue("average_marker_diff", markerStat.getAvg(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("maximum_marker_abs_diff", markerStat.getMaxAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("average_marker_abs_diff", markerStat.getAvgAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("average_periodic_diff", periodicStat.getAvg(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("maximum_periodic_abs_diff", periodicStat.getMaxAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("average_periodic_abs_diff", periodicStat.getAvgAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.setSummary("unified_abs_diff", (periodicStat.getAvgAbs() + markerStat.getAvgAbs()) / 2,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        log.submit(InstrumentationRegistry.getInstrumentation());
    }

    private class MockOnRecordPositionUpdateListener
                                        implements OnRecordPositionUpdateListener {
        public MockOnRecordPositionUpdateListener(AudioRecord record) {
            mAudioRecord = record;
            record.setRecordPositionUpdateListener(this);
        }

        public MockOnRecordPositionUpdateListener(AudioRecord record, Handler handler) {
            mAudioRecord = record;
            record.setRecordPositionUpdateListener(this, handler);
        }

        public synchronized void onMarkerReached(AudioRecord record) {
            if (mIsTestActive) {
                int position = getPosition();
                mOnMarkerReachedCalled.add(position);
                mMarkerPosition += mMarkerPeriodInFrames;
                assertEquals(AudioRecord.SUCCESS,
                        mAudioRecord.setNotificationMarkerPosition(mMarkerPosition));
            } else {
                // see comment on stop()
                final long delta = System.currentTimeMillis() - mStopTime;
                Log.d(TAG, "onMarkerReached called " + delta + " ms after stop");
                fail("onMarkerReached called when not active");
            }
        }

        public synchronized void onPeriodicNotification(AudioRecord record) {
            if (mIsTestActive) {
                int position = getPosition();
                mOnPeriodicNotificationCalled.add(position);
            } else {
                // see comment on stop()
                final long delta = System.currentTimeMillis() - mStopTime;
                Log.d(TAG, "onPeriodicNotification called " + delta + " ms after stop");
                fail("onPeriodicNotification called when not active");
            }
        }

        public synchronized void start(int sampleRate) {
            mIsTestActive = true;
            mSampleRate = sampleRate;
            mStartTime = System.currentTimeMillis();
        }

        public synchronized void stop() {
            // the listener should be stopped some time after AudioRecord is stopped
            // as some messages may not yet be posted.
            mIsTestActive = false;
            mStopTime = System.currentTimeMillis();
        }

        public ArrayList<Integer> getMarkerList() {
            return mOnMarkerReachedCalled;
        }

        public ArrayList<Integer> getPeriodicList() {
            return mOnPeriodicNotificationCalled;
        }

        public synchronized void release() {
            stop();
            mAudioRecord.setRecordPositionUpdateListener(null);
            mAudioRecord = null;
        }

        private int getPosition() {
            // we don't have mAudioRecord.getRecordPosition();
            // so we fake this by timing.
            long delta = System.currentTimeMillis() - mStartTime;
            return (int)(delta * mSampleRate / 1000);
        }

        private long mStartTime;
        private long mStopTime;
        private int mSampleRate;
        private boolean mIsTestActive = true;
        private AudioRecord mAudioRecord;
        private ArrayList<Integer> mOnMarkerReachedCalled = new ArrayList<Integer>();
        private ArrayList<Integer> mOnPeriodicNotificationCalled = new ArrayList<Integer>();
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    private boolean isLowRamDevice() {
        return ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
                .isLowRamDevice();
    }

    private boolean isLowLatencyDevice() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_LOW_LATENCY);
    }

    private boolean isProAudioDevice() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_PRO);
    }

    private void verifyContinuousTimestamps(
            AudioTimestamp startTs, AudioTimestamp stopTs, int sampleRate)
            throws Exception {
        final long timeDiff = stopTs.nanoTime - startTs.nanoTime;
        final long frameDiff = stopTs.framePosition - startTs.framePosition;
        final long NANOS_PER_SECOND = 1000000000;
        final long timeByFrames = frameDiff * NANOS_PER_SECOND / sampleRate;
        final double ratio = (double)timeDiff / timeByFrames;

        // Usually the ratio is accurate to one part per thousand or better.
        // Log.d(TAG, "ratio=" + ratio + ", timeDiff=" + timeDiff + ", frameDiff=" + frameDiff +
        //        ", timeByFrames=" + timeByFrames + ", sampleRate=" + sampleRate);
        assertEquals(1.0 /* expected */, ratio, isLowLatencyDevice() ? 0.01 : 0.5 /* delta */);
    }

    // remove if AudioTimestamp has a better toString().
    private void printTimestamp(String s, AudioTimestamp ats) {
        Log.d(TAG, s + ":  pos: " + ats.framePosition + "  time: " + ats.nanoTime);
    }

    private static void readDataTimed(AudioRecord recorder, long durationMillis,
            ShortBuffer out) throws IOException {
        final short[] buffer = new short[1024];
        final long startTimeMillis = SystemClock.uptimeMillis();
        final long stopTimeMillis = startTimeMillis + durationMillis;
        while (SystemClock.uptimeMillis() < stopTimeMillis) {
            final int readCount = recorder.read(buffer, 0, buffer.length);
            if (readCount <= 0) {
                return;
            }
            out.put(buffer, 0, readCount);
        }
    }

    private static boolean isAudioSilent(ShortBuffer buffer) {
        // Always need some bytes read
        assertTrue("Buffer should have some data", buffer.position() > 0);

        // It is possible that the transition from empty to non empty bytes
        // happened in the middle of the read data due to the async nature of
        // the system. Therefore, we look for the transitions from non-empty
        // to empty and from empty to non-empty values for robustness.
        int totalSilenceCount = 0;
        final int valueCount = buffer.position();
        for (int i = valueCount - 1; i >= 0; i--) {
            final short value = buffer.get(i);
            if (value == 0) {
                totalSilenceCount++;
            }
        }
        return totalSilenceCount > valueCount / 2;
    }

    private static void makeMyUidStateActive(String packageName, int userId) throws IOException {
        final String command = String.format(
                "cmd media.audio_policy set-uid-state %s active --user %d", packageName, userId);
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
    }

    private static void makeMyUidStateIdle(String packageName, int userId) throws IOException {
        final String command = String.format(
                "cmd media.audio_policy set-uid-state %s idle --user %d", packageName, userId);
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
    }

    private static void resetMyUidState(String packageName, int userId) throws IOException {
        final String command = String.format(
                "cmd media.audio_policy reset-uid-state %s --user %d", packageName, userId);
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /*
     * Microphone Direction API tests
     */
    @Test
    public void testSetPreferredMicrophoneDirection() {
        if (!hasMicrophone()) {
            return;
        }

        try {
            boolean success =
                    mAudioRecord.setPreferredMicrophoneDirection(
                            MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER);

            // Can't actually test this as HAL may not have implemented it
            // Just verify that it doesn't crash or throw an exception
            // assertTrue(success);
        } catch (Exception ex) {
            Log.e(TAG, "testSetPreferredMicrophoneDirection() exception:" + ex);
            assertTrue(false);
        }
        return;
    }

    @Test
    public void testSetPreferredMicrophoneFieldDimension() {
        if (!hasMicrophone()) {
            return;
        }

        try {
            boolean success = mAudioRecord.setPreferredMicrophoneFieldDimension(1.0f);

            // Can't actually test this as HAL may not have implemented it
            // Just verify that it doesn't crash or throw an exception
            // assertTrue(success);
        } catch (Exception ex) {
            Log.e(TAG, "testSetPreferredMicrophoneFieldDimension() exception:" + ex);
            assertTrue(false);
        }
        return;
    }

    /**
     * Test AudioRecord Builder error handling.
     *
     * @throws Exception
     */
    @Test
    public void testAudioRecordBuilderError() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        final AudioRecord[] audioRecord = new AudioRecord[1]; // pointer to AudioRecord.
        final int BIGNUM = Integer.MAX_VALUE; // large value that should be invalid.
        final int INVALID_SESSION_ID = 1024;  // can never occur (wrong type in 3 lsbs)
        final int INVALID_CHANNEL_MASK = -1;

        try {
            // NOTE:
            // AudioFormat tested in AudioFormatTest#testAudioFormatBuilderError.

            // We must be able to create the AudioRecord.
            audioRecord[0] = new AudioRecord.Builder().build();
            audioRecord[0].release();

            // Out of bounds buffer size.  A large size will fail in AudioRecord creation.
            assertThrows(UnsupportedOperationException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setBufferSizeInBytes(BIGNUM)
                        .build();
            });

            // 0 and negative buffer size throw IllegalArgumentException
            for (int bufferSize : new int[] {-BIGNUM, -1, 0}) {
                assertThrows(IllegalArgumentException.class, () -> {
                    audioRecord[0] = new AudioRecord.Builder()
                            .setBufferSizeInBytes(bufferSize)
                            .build();
                });
            }

            assertThrows(IllegalArgumentException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setAudioSource(BIGNUM)
                        .build();
            });

            assertThrows(IllegalArgumentException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setAudioSource(-2)
                        .build();
            });

            // Invalid session id that is positive.
            // (logcat error message vague)
            assertThrows(UnsupportedOperationException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setSessionId(INVALID_SESSION_ID)
                        .build();
            });

            // Specialty AudioRecord tests
            assertThrows(NullPointerException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(null)
                        .build();
            });

            assertThrows(NullPointerException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setContext(null)
                        .build();
            });

            // Bad audio encoding DRA expected unsupported.
            try {
                audioRecord[0] = new AudioRecord.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .setEncoding(AudioFormat.ENCODING_DRA)
                                .build())
                        .build();
                // Don't throw an exception, maybe it is supported somehow, but warn.
                Log.w(TAG, "ENCODING_DRA is expected to be unsupported");
                audioRecord[0].release();
                audioRecord[0] = null;
            } catch (UnsupportedOperationException e) {
                ; // OK expected
            }

            // Sample rate out of bounds.
            // System levels caught on AudioFormat.
            assertThrows(IllegalArgumentException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(BIGNUM)
                                .build())
                        .build();
            });

            // Invalid channel mask
            // This is a UOE for AudioRecord vs IAE for AudioTrack.
            assertThrows(UnsupportedOperationException.class, () -> {
                audioRecord[0] = new AudioRecord.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                                .setChannelMask(INVALID_CHANNEL_MASK)
                                .build())
                        .build();
            });
        } finally {
            // Did we successfully complete for some reason but did not
            // release?
            if (audioRecord[0] != null) {
                audioRecord[0].release();
                audioRecord[0] = null;
            }
        }
    }

    @Test
    public void testPrivacySensitiveBuilder() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        for (final boolean privacyOn : new boolean[] { false, true} ) {
            AudioRecord record = new AudioRecord.Builder()
            .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(8000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
            .setPrivacySensitive(privacyOn)
            .build();
            assertEquals(privacyOn, record.isPrivacySensitive());
            record.release();
        }
    }

    @Test
    public void testPrivacySensitiveDefaults() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        AudioRecord record = new AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(new AudioFormat.Builder()
                 .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                 .setSampleRate(8000)
                 .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                 .build())
            .build();
        assertFalse(record.isPrivacySensitive());
        record.release();

        record = new AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(new AudioFormat.Builder()
                 .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                 .setSampleRate(8000)
                 .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                 .build())
            .build();
        assertTrue(record.isPrivacySensitive());
        record.release();
    }

    @Test
    public void testSetLogSessionId() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioRecord audioRecord = null;
        try {
            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .build();
            audioRecord.setLogSessionId(LogSessionId.LOG_SESSION_ID_NONE); // should not throw.
            assertEquals(LogSessionId.LOG_SESSION_ID_NONE, audioRecord.getLogSessionId());

            final MediaMetricsManager mediaMetricsManager =
                    getContext().getSystemService(MediaMetricsManager.class);
            final RecordingSession recordingSession =
                    mediaMetricsManager.createRecordingSession();
            audioRecord.setLogSessionId(recordingSession.getSessionId());
            assertEquals(recordingSession.getSessionId(), audioRecord.getLogSessionId());

            // record some data to generate a log entry.
            short data[] = new short[audioRecord.getSampleRate() / 2];
            audioRecord.startRecording();
            audioRecord.read(data, 0 /* offsetInShorts */, data.length);
            audioRecord.stop();

            // Also can check the mediametrics dumpsys to validate logs generated.
        } finally {
            if (audioRecord != null) {
                audioRecord.release();
            }
        }
    }

    @Test
    public void testCompressedCaptureAAC() throws Exception {
        final int ENCODING = AudioFormat.ENCODING_AAC_LC;
        final String MIMETYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
        final int BUFFER_SIZE = 16000;
        if (!hasMicrophone()) {
            return;
        }
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        // TODO test multiple supporting devices if available
        AudioDeviceInfo supportingDevice = null;
        for (AudioDeviceInfo device : devices) {
            for (int encoding : device.getEncodings()) {
                if (encoding == ENCODING) {
                    supportingDevice = device;
                    break;
                }
            }
            if (supportingDevice != null) break;
        }
        if (supportingDevice == null) {
            Log.i(TAG, "Compressed audio (AAC) not supported");
            return; // Compressed Audio is not supported
        }
        Log.i(TAG, "Compressed audio (AAC) supported");
        AudioRecord audioRecord = null;
        try {
            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(ENCODING)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .build();
            audioRecord.setPreferredDevice(supportingDevice);
            class ByteBufferImpl extends StreamUtils.ByteBufferStream {
                @Override
                public ByteBuffer read() throws IOException {
                    if (mCount < 1 /* only one buffer */) {
                        ++mCount;
                        return mByteBuffer;
                    }
                    return null;
                }
                public ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
                private int mCount = 0;
            }

            ByteBufferImpl byteBufferImpl = new ByteBufferImpl();
            audioRecord.startRecording();
            audioRecord.read(byteBufferImpl.mByteBuffer, BUFFER_SIZE);
            audioRecord.stop();
            // Attempt to decode compressed data
            //sample rate/ch count not needed
            final MediaFormat format = MediaFormat.createAudioFormat(MIMETYPE, 0, 0);
            final StreamUtils.MediaCodecStream decodingStream
                = new StreamUtils.MediaCodecStream(byteBufferImpl, format, false);
            ByteBuffer decoded =  decodingStream.read();
            int totalDecoded = 0;
            while (decoded != null) {
                // TODO validate actual data
                totalDecoded += decoded.remaining();
                decoded = decodingStream.read();
            }
            Log.i(TAG, "Decoded size:" + String.valueOf(totalDecoded));
        // TODO rethrow following exceptions on verification
        } catch (UnsupportedOperationException e) {
            Log.w(TAG, "Compressed AudioRecord unable to be built");
        } catch (IllegalStateException e) {
            Log.w(TAG, "Compressed AudioRecord unable to be started");
        } finally {
            if (audioRecord != null) {
                audioRecord.release();
            }
        }
    }
}
