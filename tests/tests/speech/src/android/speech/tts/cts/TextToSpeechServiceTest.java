/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.speech.tts.cts;

import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.ServiceManager;
import android.speech.tts.ITextToSpeechManager;
import android.speech.tts.ITextToSpeechSession;
import android.speech.tts.ITextToSpeechSessionCallback;
import android.speech.tts.TextToSpeech;
import android.test.AndroidTestCase;

import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link android.speech.tts.TextToSpeechService} using StubTextToSpeechService.
 */
public class TextToSpeechServiceTest extends AndroidTestCase {
    private static final String UTTERANCE = "text to speech cts test";
    private static final String SAMPLE_FILE_NAME = "mytts.wav";
    private static final String EARCON_UTTERANCE = "testEarcon";
    private static final String SPEECH_UTTERANCE = "testSpeech";

    private TextToSpeechWrapper mTts;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        StubTextToSpeechService.sSynthesizeTextWait = null;
        mTts = TextToSpeechWrapper.createTextToSpeechMockWrapper(getContext());
        assertNotNull(mTts);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mTts.shutdown();
    }

    private TextToSpeech getTts() {
        return mTts.getTts();
    }

    public void testNullEngine() throws Exception {
        IBinder binder = ServiceManager.getService(Context.TEXT_TO_SPEECH_MANAGER_SERVICE);
        ITextToSpeechManager manager =
                Objects.requireNonNull(ITextToSpeechManager.Stub.asInterface(binder));
        // could use a spy instead but we run into "cannot proxy inaccessible class" issues
        final SettableFuture<String> errorMsg = SettableFuture.create();
        final SettableFuture<Void> connected = SettableFuture.create();
        final SettableFuture<Void> disconnected = SettableFuture.create();
        ITextToSpeechSessionCallback callback =
                new ITextToSpeechSessionCallback.Stub() {
                    @Override
                    public void onConnected(ITextToSpeechSession session, IBinder serviceBinder) {
                        connected.set(null);
                    }

                    @Override
                    public void onDisconnected() {
                        disconnected.set(null);
                    }

                    @Override
                    public void onError(String errorInfo) {
                        errorMsg.set(errorInfo);
                    }
                };
        manager.createSession(null, callback);
        assertFalse(connected.isDone());
        assertFalse(disconnected.isDone());
        assertEquals("Engine cannot be null", errorMsg.get(1, TimeUnit.SECONDS));
    }

    public void testSynthesizeToFile() throws Exception {
        File sampleFile = new File(getContext().getExternalFilesDir(null), SAMPLE_FILE_NAME);
        try {
            assertFalse(sampleFile.exists());

            int result =
                    getTts().synthesizeToFile(
                            UTTERANCE, createParams("mocktofile"), sampleFile.getPath());
            verifySynthesisFile(result, mTts, sampleFile);
        } finally {
            sampleFile.delete();
        }
        verifySynthesisResults(mTts);
    }

    public void testSynthesizeToFileWithFileObject() throws Exception {
        File sampleFile = new File(getContext().getExternalFilesDir(null), SAMPLE_FILE_NAME);
        try {
            assertFalse(sampleFile.exists());

            Bundle params = createParamsBundle("mocktofile");

            int result =
                    getTts().synthesizeToFile(
                            UTTERANCE,
                            params,
                            sampleFile,
                            params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID));
            verifySynthesisFile(result, mTts, sampleFile);
        } finally {
            sampleFile.delete();
        }
        verifySynthesisResults(mTts);
    }

    public void testSynthesizeToFileWithFileDescriptor() throws Exception {
        File sampleFile = new File(getContext().getExternalFilesDir(null), SAMPLE_FILE_NAME);
        try {
            assertFalse(sampleFile.exists());

            ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(sampleFile,
                    ParcelFileDescriptor.MODE_WRITE_ONLY
                            | ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE);

            Bundle params = createParamsBundle("mocktofile");

            int result =
                    getTts().synthesizeToFile(
                            UTTERANCE, params, fileDescriptor,
                            params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID));
            verifySynthesisFile(result, mTts, sampleFile);
        } finally {
            sampleFile.delete();
        }
        verifySynthesisResults(mTts);
    }

    public void testMaxSpeechInputLength() {
        File sampleFile = new File(getContext().getExternalFilesDir(null), SAMPLE_FILE_NAME);
        try {
            assertFalse(sampleFile.exists());
            TextToSpeech tts = getTts();

            int maxLength = tts.getMaxSpeechInputLength();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maxLength; i++) {
                sb.append("c");
            }
            String valid = sb.toString();
            sb.append("c");
            String invalid = sb.toString();

            assertEquals(maxLength, valid.length());
            assertTrue(invalid.length() > maxLength);
            assertEquals(TextToSpeech.ERROR,
                    tts.synthesizeToFile(invalid, createParams("mockToFile"),
                            sampleFile.getPath()));
            assertEquals(TextToSpeech.SUCCESS,
                    tts.synthesizeToFile(valid, createParams("mockToFile"), sampleFile.getPath()));
        } finally {
            sampleFile.delete();
        }
    }

    public void testSpeak() throws Exception {
        int result = getTts().speak(UTTERANCE, TextToSpeech.QUEUE_FLUSH, createParams("mockspeak"));
        assertEquals("speak() failed", TextToSpeech.SUCCESS, result);
        assertTrue("speak() completion timeout", waitForUtterance("mockspeak"));
        mTts.verify("mockspeak");

        final Map<String, Integer> chunksReceived = mTts.chunksReceived();
        assertEquals(Integer.valueOf(10), chunksReceived.get("mockspeak"));
    }

    public void testSpeakStop() throws Exception {
        final ConditionVariable synthesizeTextWait = new ConditionVariable();
        StubTextToSpeechService.sSynthesizeTextWait = synthesizeTextWait;

        getTts().stop();
        final int iterations = 20;
        for (int i = 0; i < iterations; i++) {
            int result = getTts().speak(UTTERANCE, TextToSpeech.QUEUE_ADD, null,
                    "stop_" + Integer.toString(i));
            assertEquals("speak() failed", TextToSpeech.SUCCESS, result);
        }
        getTts().stop();

        // Wake up the Stubs #onSynthesizeSpeech (one that will be stopped in-progress)
        synthesizeTextWait.open();

        for (int i = 0; i < iterations; i++) {
            assertTrue("speak() stop callback timeout", mTts.waitForStop(
                    "stop_" + Integer.toString(i)));
        }
    }

    public void testSpeakStopBehindOtherAudioPlayback() throws Exception {
        final ConditionVariable synthesizeTextWait = new ConditionVariable();
        final ConditionVariable synthesizeTextStartEvent = new ConditionVariable();
        StubTextToSpeechService.sSynthesizeTextWait = synthesizeTextWait;
        StubTextToSpeechService.sSynthesizeTextStartEvent = synthesizeTextStartEvent;

        // Make the audio playback queue busy by putting a 30s of silence.
        getTts().stop();
        getTts().playSilentUtterance(30000, TextToSpeech.QUEUE_ADD, "silence");

        // speak(), wait it to starting in the service, and stop().
        int result = getTts().speak(UTTERANCE, TextToSpeech.QUEUE_ADD, null, "stop");
        assertEquals("speak() failed", TextToSpeech.SUCCESS, result);
        assertTrue("synthesis not started", synthesizeTextStartEvent.block(10000));
        getTts().stop();

        // Wake up the Stubs #onSynthesizeSpeech (one that will be stopped in-progress)
        synthesizeTextWait.open();

        assertTrue("speak() stop callback timeout", mTts.waitForStop("stop"));
    }

    public void testMediaPlayerFails() throws Exception {
        File sampleFile = new File(getContext().getExternalFilesDir(null), "notsound.wav");
        try {
            assertFalse(TextToSpeechWrapper.isSoundFile(sampleFile.getPath()));
            FileOutputStream out = new FileOutputStream(sampleFile);
            out.write(new byte[]{0x01, 0x02});
            out.close();
            assertFalse(TextToSpeechWrapper.isSoundFile(sampleFile.getPath()));
        } finally {
            sampleFile.delete();
        }
    }

    public void testAddPlayEarcon() throws Exception {
        File sampleFile = new File(getContext().getExternalFilesDir(null), SAMPLE_FILE_NAME);
        try {
            generateSampleAudio(sampleFile);

            Uri sampleUri = Uri.fromFile(sampleFile);
            assertEquals(getTts().addEarcon(EARCON_UTTERANCE, sampleFile), TextToSpeech.SUCCESS);

            int result = getTts().playEarcon(EARCON_UTTERANCE,
                    TextToSpeech.QUEUE_FLUSH, createParamsBundle(EARCON_UTTERANCE),
                    EARCON_UTTERANCE);

            verifyAddPlay(result, mTts, EARCON_UTTERANCE);
        } finally {
            sampleFile.delete();
        }
    }

    public void testAddPlaySpeech() throws Exception {
        File sampleFile = new File(getContext().getExternalFilesDir(null), SAMPLE_FILE_NAME);
        try {
            generateSampleAudio(sampleFile);

            Uri sampleUri = Uri.fromFile(sampleFile);
            assertEquals(getTts().addSpeech(SPEECH_UTTERANCE, sampleFile), TextToSpeech.SUCCESS);

            int result = getTts().speak(SPEECH_UTTERANCE,
                    TextToSpeech.QUEUE_FLUSH, createParamsBundle(SPEECH_UTTERANCE),
                    SPEECH_UTTERANCE);

            verifyAddPlay(result, mTts, SPEECH_UTTERANCE);
        } finally {
            sampleFile.delete();
        }
    }

    public void testSetLanguage() {
        TextToSpeech tts = getTts();

        assertEquals(tts.setLanguage(null), TextToSpeech.LANG_NOT_SUPPORTED);
        assertEquals(tts.setLanguage(new Locale("en", "US")), TextToSpeech.LANG_COUNTRY_AVAILABLE);
        assertEquals(tts.setLanguage(new Locale("en")), TextToSpeech.LANG_AVAILABLE);
        assertEquals(tts.setLanguage(new Locale("es", "US")), TextToSpeech.LANG_NOT_SUPPORTED);
    }

    public void testAddAudioAttributes() {
        TextToSpeech tts = getTts();
        AudioAttributes attr =
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();

        assertEquals(tts.setAudioAttributes(null), TextToSpeech.ERROR);
        assertEquals(tts.setAudioAttributes(attr), TextToSpeech.SUCCESS);
    }

    private void generateSampleAudio(File sampleFile) throws Exception {
        assertFalse(sampleFile.exists());

        ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(sampleFile,
                ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE);

        Bundle params = createParamsBundle("mocktofile");

        int result =
                getTts().synthesizeToFile(
                        UTTERANCE, params, fileDescriptor,
                        params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID));

        verifySynthesisFile(result, mTts, sampleFile);
    }

    private void verifyAddPlay(int result, TextToSpeechWrapper mTts, String utterance)
            throws Exception {
        assertEquals(TextToSpeech.SUCCESS, result);
        assertTrue(mTts.waitForComplete(utterance));
    }

    private HashMap<String, String> createParams(String utteranceId) {
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        return params;
    }

    private Bundle createParamsBundle(String utteranceId) {
        Bundle bundle = new Bundle();
        bundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        return bundle;
    }

    private void verifySynthesisFile(int result, TextToSpeechWrapper mTts, File file)
            throws InterruptedException {

        assertEquals("synthesizeToFile() failed", TextToSpeech.SUCCESS, result);

        assertTrue("synthesizeToFile() completion timeout", mTts.waitForComplete("mocktofile"));
        assertTrue("synthesizeToFile() didn't produce a file", file.exists());
        assertTrue("synthesizeToFile() produced a non-sound file",
                TextToSpeechWrapper.isSoundFile(file.getPath()));
    }

    private void verifySynthesisResults(TextToSpeechWrapper mTts) {
        mTts.verify("mocktofile");

        final Map<String, Integer> chunksReceived = mTts.chunksReceived();
        final Map<String, List<Integer>> timePointsStart = mTts.timePointsStart();
        final Map<String, List<Integer>> timePointsEnd = mTts.timePointsEnd();
        final Map<String, List<Integer>> timePointsFrame = mTts.timePointsFrame();
        assertEquals(Integer.valueOf(10), chunksReceived.get("mocktofile"));
        // In the mock we set the start, end and frame to exactly these values for the time points.
        for (int i = 0; i < 10; i++) {
            assertEquals(Integer.valueOf(i * 5), timePointsStart.get("mocktofile").get(i));
            assertEquals(Integer.valueOf(i * 5 + 5), timePointsEnd.get("mocktofile").get(i));
            assertEquals(Integer.valueOf(i * 10), timePointsFrame.get("mocktofile").get(i));
        }
    }

    private boolean waitForUtterance(String utteranceId) throws InterruptedException {
        return mTts.waitForComplete(utteranceId);
    }

}
