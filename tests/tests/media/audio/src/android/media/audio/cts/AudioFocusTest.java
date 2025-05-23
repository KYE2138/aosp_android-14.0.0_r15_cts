/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.RawRes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.audio.cts.R;
import android.media.cts.TestUtils;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@NonMainlineTest
public class AudioFocusTest extends CtsAndroidTestCase {
    private static final String TAG = "AudioFocusTest";

    private static final int TEST_TIMING_TOLERANCE_MS = 100;
    private static final long MEDIAPLAYER_PREPARE_TIMEOUT_MS = 2000;

    private static final AudioAttributes ATTR_DRIVE_DIR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final AudioAttributes ATTR_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    private static final AudioAttributes ATTR_A11Y = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final AudioAttributes ATTR_CALL = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private static final String TEST_CALL_ID = "fake call";

    public void testInvalidAudioFocusRequestDelayNoListener() throws Exception {
        AudioFocusRequest req = null;
        Exception ex = null;
        try {
            req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAcceptsDelayedFocusGain(true).build();
        } catch (Exception e) {
            // expected
            ex = e;
        }
        assertNotNull("No exception was thrown for an invalid build", ex);
        assertEquals("Wrong exception thrown", ex.getClass(), IllegalStateException.class);
        assertNull("Shouldn't be able to create delayed request without listener", req);
    }

    public void testInvalidAudioFocusRequestPauseOnDuckNoListener() throws Exception {
        AudioFocusRequest req = null;
        Exception ex = null;
        try {
            req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setWillPauseWhenDucked(true).build();
        } catch (Exception e) {
            // expected
            ex = e;
        }
        assertNotNull("No exception was thrown for an invalid build", ex);
        assertEquals("Wrong exception thrown", ex.getClass(), IllegalStateException.class);
        assertNull("Shouldn't be able to create pause-on-duck request without listener", req);
    }

    public void testAudioFocusRequestBuilderDefault() throws Exception {
        final AudioFocusRequest reqDefaults =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
        assertEquals("Focus gain differs", AudioManager.AUDIOFOCUS_GAIN,
                reqDefaults.getFocusGain());
        assertEquals("Listener differs", null, reqDefaults.getOnAudioFocusChangeListener());
        assertEquals("Handler differs", null, reqDefaults.getOnAudioFocusChangeListenerHandler());
        assertEquals("Duck behavior differs", false, reqDefaults.willPauseWhenDucked());
        assertEquals("Delayed focus differs", false, reqDefaults.acceptsDelayedFocusGain());
    }


    public void testAudioFocusRequestCopyBuilder() throws Exception {
        final FocusChangeListener focusListener = new FocusChangeListener();
        final int focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        final AudioFocusRequest reqToCopy =
                new AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(ATTR_DRIVE_DIR)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .build();

        AudioFocusRequest newReq = new AudioFocusRequest.Builder(reqToCopy).build();
        assertEquals("AudioAttributes differ", ATTR_DRIVE_DIR, newReq.getAudioAttributes());
        assertEquals("Listener differs", focusListener, newReq.getOnAudioFocusChangeListener());
        assertEquals("Focus gain differs", focusGain, newReq.getFocusGain());
        assertEquals("Duck behavior differs", true, newReq.willPauseWhenDucked());
        assertEquals("Delayed focus differs", true, newReq.acceptsDelayedFocusGain());

        newReq = new AudioFocusRequest.Builder(reqToCopy)
                .setWillPauseWhenDucked(false)
                .setFocusGain(AudioManager.AUDIOFOCUS_GAIN)
                .build();
        assertEquals("AudioAttributes differ", ATTR_DRIVE_DIR, newReq.getAudioAttributes());
        assertEquals("Listener differs", focusListener, newReq.getOnAudioFocusChangeListener());
        assertEquals("Focus gain differs", AudioManager.AUDIOFOCUS_GAIN, newReq.getFocusGain());
        assertEquals("Duck behavior differs", false, newReq.willPauseWhenDucked());
        assertEquals("Delayed focus differs", true, newReq.acceptsDelayedFocusGain());
    }

    public void testNullListenerHandlerNpe() throws Exception {
        final AudioFocusRequest.Builder afBuilder =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilder.setOnAudioFocusChangeListener(null);
            fail("no NPE when setting a null listener");
        } catch (NullPointerException e) {
        }

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());
        final AudioFocusRequest.Builder afBuilderH =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilderH.setOnAudioFocusChangeListener(null, h);
            fail("no NPE when setting a null listener with non-null Handler");
        } catch (NullPointerException e) {
        }

        final AudioFocusRequest.Builder afBuilderL =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilderL.setOnAudioFocusChangeListener(new FocusChangeListener(), null);
            fail("no NPE when setting a non-null listener with null Handler");
        } catch (NullPointerException e) {
        }
    }

    public void testAudioFocusRequestGainLoss() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, attributes, false /*no handler*/);
    }

    public void testAudioFocusRequestGainLossHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, attributes, true /*with handler*/);
    }


    public void testAudioFocusRequestGainLossTransient() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, attributes,
                false /*no handler*/);
    }

    public void testAudioFocusRequestGainLossTransientHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, attributes,
                true /*with handler*/);
    }

    public void testAudioFocusRequestGainLossTransientDuck() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG,"Test testAudioFocusRequestGainLossTransientDuck "
                    + "skipped: not required for Auto platform");
            return;
        }

        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                false /*no handler*/);
    }

    public void testAudioFocusRequestGainLossTransientDuckHandler() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG,"Test testAudioFocusRequestGainLossTransientDuckHandler "
                    + "skipped: not required for Auto platform");
            return;
        }

        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                true /*with handler*/);
    }

    public void testAudioFocusRequestForceDuckNotA11y() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG,"Test testAudioFocusRequestForceDuckNotA11y "
                    + "skipped: not required for Auto platform");
            return;
        }

        // verify a request that is "force duck"'d still causes loss of focus because it doesn't
        // come from an A11y service, and requests are from same uid
        final AudioAttributes[] attributes = {ATTR_MEDIA, ATTR_A11Y};
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                false /*no handler*/, true /* forceDucking */);
    }

    public void testAudioFocusRequestA11y() throws Exception {
        final AudioAttributes[] attributes = {ATTR_DRIVE_DIR, ATTR_A11Y};
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, attributes,
                false /*no handler*/, false /* forceDucking */);
    }

    /**
     * Test delayed focus behaviors with the sequence:
     * 1/ (simulated) call with focus lock: media gets FOCUS_LOSS_TRANSIENT
     * 2/ media requests FOCUS_GAIN + delay OK: is delayed
     * 3/ call ends: media gets FOCUS_GAIN
     * @throws Exception when failing
     */
    public void testAudioMediaFocusDelayedByCall() throws Exception {
        Log.i(TAG, "testAudioMediaFocusDelayedByCall");
        AudioManager am = new AudioManager(getContext());
        Handler handler = new Handler(Looper.getMainLooper());

        AudioFocusRequest callFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(ATTR_CALL)
                        .setLocksFocus(true)
                        .build();

        FocusChangeListener mediaListener = new FocusChangeListener();
        AudioFocusRequest mediaFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_MEDIA)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(mediaListener, handler)
                        .build();


        // for focus request/abandon test methods
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_AUDIO_STATE);
        try {
            // call requests audio focus
            int res = am.requestAudioFocusForTest(callFocusReq, TEST_CALL_ID, 1977,
                    Build.VERSION_CODES.S);
            assertEquals("call request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // media requests audio focus, verify it's delayed
            res = am.requestAudioFocus(mediaFocusReq);
            assertEquals("Focus request from media wasn't delayed",
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED, res);
            // end the call, verify media gets focus
            am.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            mediaListener.waitForFocusChange("testAudioMediaFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);

            assertEquals("Focus gain not dispatched to media after call",
                    AudioManager.AUDIOFOCUS_GAIN, mediaListener.getFocusChangeAndReset());
        } finally {
            am.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            am.abandonAudioFocusRequest(mediaFocusReq);
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Test delayed focus behaviors with the sequence:
     * 1/ media requests FOCUS_GAIN
     * 2/ (simulated) call with focus lock: media gets FOCUS_LOSS_TRANSIENT
     * 3/ drive dir requests FOCUS_GAIN + delay OK: is delayed + media gets FOCUS_LOSS
     * 4/ call ends: drive dir gets FOCUS_GAIN
     * @throws Exception when failing
     */
    public void testAudioFocusDelayedByCall() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusDelayedByCall "
                    + "skipped: not required for Auto platform");
            return;
        }
        if (hasPCFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusDelayedByCall "
                    + "skipped: not required for Desktop platform");
            return;
        }
        Log.i(TAG, "testAudioFocusDelayedByCall");
        final AudioManager am = new AudioManager(getContext());
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioFocusRequest callFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setLocksFocus(true).build();
        final FocusChangeListener mediaListener = new FocusChangeListener();
        final AudioFocusRequest mediaFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_MEDIA)
                        .setOnAudioFocusChangeListener(mediaListener, handler)
                        .build();
        final FocusChangeListener driveListener = new FocusChangeListener();
        final AudioFocusRequest driveFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_DRIVE_DIR)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(driveListener, handler)
                        .build();

        // for focus request/abandon test methods
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_AUDIO_STATE);
        try {
            // media requests audio focus
            int res = am.requestAudioFocus(mediaFocusReq);
            assertEquals("media request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // call requests audio focus
            am.requestAudioFocusForTest(callFocusReq, TEST_CALL_ID, 1977, Build.VERSION_CODES.S);
            assertEquals("call request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // verify media lost focus with LOSS_TRANSIENT
            mediaListener.waitForFocusChange("testAudioFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched to media after call start",
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mediaListener.getFocusChangeAndReset());
            // drive dir requests audio focus, verify it's delayed
            res = am.requestAudioFocus(driveFocusReq);
            assertEquals("Focus request from drive dir. wasn't delayed",
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED, res);
            // verify media lost focus with LOSS as it's being kicked out of the focus stack
            mediaListener.waitForFocusChange("testAudioFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched to media after drive dir delayed focus",
                    AudioManager.AUDIOFOCUS_LOSS, mediaListener.getFocusChangeAndReset());
            // end the call, verify drive dir gets focus
            am.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            driveListener.waitForFocusChange("testAudioFocusDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus gain not dispatched to drive dir after call",
                    AudioManager.AUDIOFOCUS_GAIN, driveListener.getFocusChangeAndReset());
        } finally {
            am.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            am.abandonAudioFocusRequest(driveFocusReq);
            am.abandonAudioFocusRequest(mediaFocusReq);
            handler.getLooper().quit();
            handlerThread.quitSafely();
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Test delayed focus behaviors with the sequence:
     * 1/ media requests FOCUS_GAIN
     * 2/ (simulated) call with focus lock: media gets FOCUS_LOSS_TRANSIENT
     * 3/ drive dir requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK + delay OK: is delayed
     * 4/ call ends: drive dir gets FOCUS_GAIN
     * 5/ drive dir ends: media gets FOCUS_GAIN (because it was still in the stack,
     *                    unlike in testAudioFocusDelayedByCall)
     * @throws Exception when failing
     */
    public void testAudioFocusTransientDelayedByCall() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusTransientDelayedByCall "
                    + "skipped: not required for Auto platform");
            return;
        }
        if (hasPCFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusTransientDelayedByCall "
                    + "skipped: not required for Desktop platform");
            return;
        }
        Log.i(TAG, "testAudioFocusDelayedByCall");
        final AudioManager am = new AudioManager(getContext());
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioFocusRequest callFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setLocksFocus(true).build();
        final FocusChangeListener mediaListener = new FocusChangeListener();
        final AudioFocusRequest mediaFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(ATTR_MEDIA)
                        .setOnAudioFocusChangeListener(mediaListener, handler)
                        .build();
        final FocusChangeListener driveListener = new FocusChangeListener();
        final AudioFocusRequest driveFocusReq =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(ATTR_DRIVE_DIR)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(driveListener, handler)
                        .build();

        // for focus request/abandon test methods
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_AUDIO_STATE);
        try {
            // media requests audio focus
            int res = am.requestAudioFocus(mediaFocusReq);
            assertEquals("media request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // call requests audio focus
            am.requestAudioFocusForTest(callFocusReq, TEST_CALL_ID, 1977, Build.VERSION_CODES.S);
            assertEquals("call request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // verify media lost focus with LOSS_TRANSIENT
            mediaListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched to media after call start",
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mediaListener.getFocusChangeAndReset());
            // drive dir requests audio focus, verify it's delayed
            res = am.requestAudioFocus(driveFocusReq);
            assertEquals("Focus request from drive dir. wasn't delayed",
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED, res);
            // end the call, verify drive dir gets focus, and media didn't get focus change
            am.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            driveListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus gain not dispatched to drive dir after call",
                    AudioManager.AUDIOFOCUS_GAIN, driveListener.getFocusChangeAndReset());
            mediaListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ false);
            assertEquals("Focus change was dispatched to media",
                    AudioManager.AUDIOFOCUS_NONE, mediaListener.getFocusChangeAndReset());
            // end the drive dir, verify media gets focus
            am.abandonAudioFocusRequest(driveFocusReq);
            mediaListener.waitForFocusChange("testAudioFocusTransientDelayedByCall",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus gain not dispatched to media after drive dir",
                    AudioManager.AUDIOFOCUS_GAIN, mediaListener.getFocusChangeAndReset());
        } finally {
            am.abandonAudioFocusForTest(callFocusReq, TEST_CALL_ID);
            am.abandonAudioFocusRequest(driveFocusReq);
            am.abandonAudioFocusRequest(mediaFocusReq);
            handler.getLooper().quit();
            handlerThread.quitSafely();
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }
    /**
     * Determine if automotive feature is available
     * @param context context to query
     * @return true if automotive feature is available
     */
    private static boolean hasAutomotiveFeature(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Determine if desktop feature is available
     * @param context context to query
     * @return true if desktop feature is available
     */
    private static boolean hasPCFeature(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC);
    }

    /**
     * Test delayed focus loss after fade out
     * @throws Exception
     */
    public void testAudioFocusRequestMediaGainLossWithPlayer() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusRequestMediaGainLossWithPlayer "
                    + "skipped: not required for Auto platform");
            return;
        }

        // for query of fade out duration and focus request/abandon test methods
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_AUDIO_STATE);

        final int NB_FOCUS_OWNERS = 2;
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        final int FOCUS_UNDER_TEST = 0;// index of focus owner to be tested
        final int FOCUS_SIMULATED = 1; // index of focus requester used to simulate a request coming
                                       //   from another client on a different UID than CTS

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioAttributes mediaAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        for (int focusIndex : new int[]{ FOCUS_UNDER_TEST, FOCUS_SIMULATED }) {
            focusListeners[focusIndex] = new FocusChangeListener();
            focusRequests[focusIndex] = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mediaAttributes)
                    .setOnAudioFocusChangeListener(focusListeners[focusIndex], handler)
                    .build();
        }
        final AudioManager am = new AudioManager(getContext());

        MediaPlayer mp = null;
        final String simFocusClientId = "fakeClientId";
        try {
            // set up the test conditions: a focus owner is playing media on a MediaPlayer
            mp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, mediaAttributes);
            int res = am.requestAudioFocus(focusRequests[FOCUS_UNDER_TEST]);
            assertEquals("real focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            mp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            final long fadeDuration = am.getFadeOutDurationOnFocusLossMillis(mediaAttributes);
            Log.i(TAG, "using fade out duration = " + fadeDuration);

            res = am.requestAudioFocusForTest(focusRequests[FOCUS_SIMULATED],
                    simFocusClientId, Integer.MAX_VALUE /*fakeClientUid*/, Build.VERSION_CODES.S);
            assertEquals("test focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);

            if (fadeDuration > 0) {
                assertEquals("Focus loss dispatched too early", AudioManager.AUDIOFOCUS_NONE,
                        focusListeners[FOCUS_UNDER_TEST].getFocusChangeAndReset());
                focusListeners[FOCUS_UNDER_TEST]
                        .waitForFocusChange(
                                "testAudioFocusRequestMediaGainLossWithPlayer fadeDuration",
                                fadeDuration, /* shouldAcquire= */ false);
            }

            focusListeners[FOCUS_UNDER_TEST].waitForFocusChange(
                    "testAudioFocusRequestMediaGainLossWithPlayer",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched", AudioManager.AUDIOFOCUS_LOSS,
                    focusListeners[FOCUS_UNDER_TEST].getFocusChangeAndReset());

        }
        finally {
            handler.getLooper().quit();
            handlerThread.quitSafely();
            if (mp != null) {
                mp.release();
            }
            am.abandonAudioFocusForTest(focusRequests[FOCUS_SIMULATED], simFocusClientId);
            am.abandonAudioFocusRequest(focusRequests[FOCUS_UNDER_TEST]);
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Test there is no delayed focus loss when focus loser is playing speech
     * @throws Exception
     */
    public void testAudioFocusRequestMediaGainLossWithSpeechPlayer() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusRequestMediaGainLossWithSpeechPlayer "
                    + "skipped: not required for Auto platform");
            return;
        }
        doTwoFocusOwnerOnePlayerFocusLoss(
                true /*playSpeech*/,
                false /*speechFocus*/,
                false /*pauseOnDuck*/);
    }

    /**
     * Test there is no delayed focus loss when focus loser had requested focus with
     * AudioAttributes with speech content type
     * @throws Exception
     */
    public void testAudioFocusRequestMediaGainLossWithSpeechFocusRequest() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusRequestMediaGainLossWithSpeechPlayer "
                    + "skipped: not required for Auto platform");
            return;
        }
        doTwoFocusOwnerOnePlayerFocusLoss(
                false /*playSpeech*/,
                true /*speechFocus*/,
                false /*pauseOnDuck*/);
    }

    /**
     * Test there is no delayed focus loss when focus loser had requested focus specifying
     * it pauses on duck
     * @throws Exception
     */
    public void testAudioFocusRequestMediaGainLossWithPauseOnDuckFocusRequest() throws Exception {
        if (hasAutomotiveFeature(getContext())) {
            Log.i(TAG, "Test testAudioFocusRequestMediaGainLossWithSpeechPlayer "
                    + "skipped: not required for Auto platform");
            return;
        }
        doTwoFocusOwnerOnePlayerFocusLoss(
                false /*playSpeech*/,
                false /*speechFocus*/,
                true /*pauseOnDuck*/);
    }

    private void doTwoFocusOwnerOnePlayerFocusLoss(boolean playSpeech, boolean speechFocus,
            boolean pauseOnDuck) throws Exception {
        // for query of fade out duration and focus request/abandon test methods
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_AUDIO_STATE);

        final int NB_FOCUS_OWNERS = 2;
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        // index of focus owner to be tested, has an active player
        final int FOCUS_UNDER_TEST = 0;
        // index of focus requester used to simulate a request coming from another client
        // on a different UID than CTS
        final int FOCUS_SIMULATED = 1;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());

        final AudioAttributes focusAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(playSpeech ? AudioAttributes.CONTENT_TYPE_SPEECH
                        : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        final AudioAttributes playerAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(speechFocus ? AudioAttributes.CONTENT_TYPE_SPEECH
                        : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        for (int focusIndex : new int[]{ FOCUS_UNDER_TEST, FOCUS_SIMULATED }) {
            focusListeners[focusIndex] = new FocusChangeListener();
            focusRequests[focusIndex] = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(focusIndex == FOCUS_UNDER_TEST ? playerAttributes
                            : focusAttributes)
                    .setWillPauseWhenDucked(pauseOnDuck)
                    .setOnAudioFocusChangeListener(focusListeners[focusIndex], handler)
                    .build();
        }
        final AudioManager am = new AudioManager(getContext());

        MediaPlayer mp = null;
        final String simFocusClientId = "fakeClientId";
        try {
            // set up the test conditions: a focus owner is playing media on a MediaPlayer
            mp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, playerAttributes);
            int res = am.requestAudioFocus(focusRequests[FOCUS_UNDER_TEST]);
            assertEquals("real focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            mp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            res = am.requestAudioFocusForTest(focusRequests[FOCUS_SIMULATED],
                    simFocusClientId, Integer.MAX_VALUE /*fakeClientUid*/, Build.VERSION_CODES.S);
            assertEquals("test focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);

            focusListeners[FOCUS_UNDER_TEST].waitForFocusChange("doTwoFocusOwnerOnePlayerFocusLoss",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched", AudioManager.AUDIOFOCUS_LOSS,
                    focusListeners[FOCUS_UNDER_TEST].getFocusChangeAndReset());

        }
        finally {
            handler.getLooper().quit();
            handlerThread.quitSafely();
            if (mp != null) {
                mp.release();
            }
            am.abandonAudioFocusForTest(focusRequests[FOCUS_SIMULATED], simFocusClientId);
            am.abandonAudioFocusRequest(focusRequests[FOCUS_UNDER_TEST]);
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }
    //-----------------------------------
    // Test utilities

    /**
     * Test focus request and abandon between two focus owners
     * @param gainType focus gain of the focus owner on top (== 2nd focus requester)
     */
    private void doTestTwoPlayersGainLoss(int gainType, AudioAttributes[] attributes,
            boolean useHandlerInListener) throws Exception {
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, gainType, attributes,
                useHandlerInListener, false /*forceDucking*/);
    }

    /**
     * Same as {@link #doTestTwoPlayersGainLoss(int, AudioAttributes[], boolean)} with forceDucking
     *   set to false.
     * @param gainTypeForFirstPlayer focus gain of the focus owner on bottom (== 1st focus request)
     * @param gainTypeForSecondPlayer focus gain of the focus owner on top (== 2nd focus request)
     * @param attributes Audio attributes for first and second player, in order.
     * @param useHandlerInListener
     * @param forceDucking value used for setForceDucking in request for focus requester at top of
     *   stack (second requester in test).
     * @throws Exception
     */
    private void doTestTwoPlayersGainLoss(int gainTypeForFirstPlayer, int gainTypeForSecondPlayer,
            AudioAttributes[] attributes, boolean useHandlerInListener,
            boolean forceDucking) throws Exception {
        final int NB_FOCUS_OWNERS = 2;
        if (NB_FOCUS_OWNERS != attributes.length) {
            throw new IllegalArgumentException("Invalid test: invalid number of attributes");
        }
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        final int[] focusGains = { gainTypeForFirstPlayer, gainTypeForSecondPlayer };
        int expectedLoss = 0;
        switch (gainTypeForSecondPlayer) {
            case AudioManager.AUDIOFOCUS_GAIN:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                break;
            default:
                fail("invalid focus gain used in test");
        }
        final AudioManager am = new AudioManager(getContext());

        final Handler h;
        if (useHandlerInListener) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        try {
            for (int i = 0 ; i < NB_FOCUS_OWNERS ; i++) {
                focusListeners[i] = new FocusChangeListener();
                final boolean forceDuck = i == NB_FOCUS_OWNERS - 1 ? forceDucking : false;
                if (h != null) {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i], h /*handler*/)
                            .setForceDucking(forceDuck)
                            .build();
                } else {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i])
                            .setForceDucking(forceDuck)
                            .build();
                }
            }

            // focus owner 0 requests focus with GAIN,
            // then focus owner 1 requests focus with gainType
            // then 1 abandons focus, then 0 abandons focus
            int res = am.requestAudioFocus(focusRequests[0]);
            assertEquals("1st focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            res = am.requestAudioFocus(focusRequests[1]);
            assertEquals("2nd focus request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusListeners[0].waitForFocusChange("doTestTwoPlayersGainLoss",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ true);
            assertEquals("Focus loss not dispatched", expectedLoss,
                    focusListeners[0].getFocusChangeAndReset());
            res = am.abandonAudioFocusRequest(focusRequests[1]);
            assertEquals("1st abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[1] = null;
            focusListeners[0].waitForFocusChange("doTestTwoPlayersGainLoss",
                    TEST_TIMING_TOLERANCE_MS,
                    gainTypeForSecondPlayer != AudioManager.AUDIOFOCUS_GAIN);
            // when focus was lost because it was requested with GAIN, focus is not given back
            if (gainTypeForSecondPlayer != AudioManager.AUDIOFOCUS_GAIN) {
                assertEquals("Focus gain not dispatched", AudioManager.AUDIOFOCUS_GAIN,
                        focusListeners[0].getFocusChangeAndReset());
            } else {
                // verify there was no focus change because focus user 0 was kicked out of stack
                assertEquals("Focus change was dispatched", AudioManager.AUDIOFOCUS_NONE,
                        focusListeners[0].getFocusChangeAndReset());
            }
            res = am.abandonAudioFocusRequest(focusRequests[0]);
            assertEquals("2nd abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[0] = null;
        }
        finally {
            for (int i = 0 ; i < NB_FOCUS_OWNERS ; i++) {
                if (focusRequests[i] != null) {
                    am.abandonAudioFocusRequest(focusRequests[i]);
                }
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    private @Nullable MediaPlayer createPreparedMediaPlayer(
            @RawRes int resID, AudioAttributes aa) throws Exception {
        final TestUtils.Monitor onPreparedCalled = new TestUtils.Monitor();

        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(aa);
        AssetFileDescriptor afd = getContext().getResources().openRawResourceFd(resID);
        try {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        mp.setOnPreparedListener(mp1 -> onPreparedCalled.signal());
        mp.prepare();
        onPreparedCalled.waitForSignal(MEDIAPLAYER_PREPARE_TIMEOUT_MS);
        assertTrue(
                "MediaPlayer wasn't prepared in under " + MEDIAPLAYER_PREPARE_TIMEOUT_MS + " ms",
                onPreparedCalled.isSignalled());
        return mp;
    }

    private static class FocusChangeListener implements OnAudioFocusChangeListener {
        private final Object mLock = new Object();
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        private int mFocusChange = AudioManager.AUDIOFOCUS_NONE;

        int getFocusChangeAndReset() {
            final int change;
            synchronized (mLock) {
                change = mFocusChange;
                mFocusChange = AudioManager.AUDIOFOCUS_NONE;
            }
            mChangeEventSignal.drainPermits();
            return change;
        }

        void waitForFocusChange(String caller, long timeoutMs, boolean shouldAcquire)
                throws Exception {
            boolean acquired = mChangeEventSignal.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            assertWithMessage(caller + " wait acquired").that(acquired).isEqualTo(shouldAcquire);
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "onAudioFocusChange:" + focusChange + " listener:" + this);
            synchronized (mLock) {
                mFocusChange = focusChange;
            }
            mChangeEventSignal.release();
        }
    }
}
