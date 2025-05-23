/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.wavelib.DspBufferComplex;
import com.android.cts.verifier.audio.wavelib.DspBufferDouble;
import com.android.cts.verifier.audio.wavelib.DspBufferMath;
import com.android.cts.verifier.audio.wavelib.DspFftServer;
import com.android.cts.verifier.audio.wavelib.DspWindow;
import com.android.cts.verifier.audio.wavelib.PipeShort;
import com.android.cts.verifier.audio.wavelib.VectorAverage;

/**
 * Tests Audio Device roundtrip latency by using a loopback plug.
 */
public class AudioFrequencyLineActivity extends AudioFrequencyActivity implements Runnable,
    AudioRecord.OnRecordPositionUpdateListener {
    private static final String TAG = "AudioFrequencyLineActivity";

    static final int TEST_STARTED = 900;
    static final int TEST_ENDED = 901;
    static final int TEST_MESSAGE = 902;
    static final double MIN_ENERGY_BAND_1 = -20.0;
    static final double MIN_FRACTION_POINTS_IN_BAND = 0.3;

    OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    Button mWiredPortYes;
    Button mWiredPortNo;

    Button mLoopbackPlugReady;
    Button mTestButton;
    TextView mResultText;
    ProgressBar mProgressBar;
    //recording
    private boolean mIsRecording = false;
    private final Object mRecordingLock = new Object();
    private AudioRecord mRecorder;
    private int mMinRecordBufferSizeInSamples = 0;
    private short[] mAudioShortArray;
    private short[] mAudioShortArray2;

    private final int mBlockSizeSamples = 1024;
    private final int mSamplingRate = 48000;
    private final int mSelectedRecordSource = MediaRecorder.AudioSource.UNPROCESSED;
    private final int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private volatile Thread mRecordThread;
    private boolean mRecordThreadShutdown = false;

    PipeShort mPipe = new PipeShort(65536);
    SoundPlayerObject mSPlayer;

    private DspBufferComplex mC;
    private DspBufferDouble mData;

    private DspWindow mWindow;
    private DspFftServer mFftServer;
    private VectorAverage mFreqAverageMain = new VectorAverage();

    private VectorAverage mFreqAverage0 = new VectorAverage();
    private VectorAverage mFreqAverage1 = new VectorAverage();

    private int mCurrentTest = -1;
    int mBands = 4;
    AudioBandSpecs[] bandSpecsArray = new AudioBandSpecs[mBands];

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_frequency_line_plug_ready_btn) {
                Log.i(TAG, "audio loopback plug ready");
                //enable all the other views.
                enableLayout(R.id.audio_frequency_line_layout, true);
                setMaxLevel();
                testMaxLevel();
            } else if (id == R.id.audio_frequency_line_test_btn) {
                Log.i(TAG, "audio loopback test");
                startAudioTest();
            } else if (id == R.id.audio_wired_yes) {
                Log.i(TAG, "User confirms wired Port existence");
                mLoopbackPlugReady.setEnabled(true);
                recordHeasetPortFound(true);
                mWiredPortYes.setEnabled(false);
                mWiredPortNo.setEnabled(false);
            } else if (id == R.id.audio_wired_no) {
                Log.i(TAG, "User denies wired Port existence");
                recordHeasetPortFound(false);
                getPassButton().setEnabled(true);
                mWiredPortYes.setEnabled(false);
                mWiredPortNo.setEnabled(false);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_frequency_line_activity);

        mWiredPortYes = (Button)findViewById(R.id.audio_wired_yes);
        mWiredPortYes.setOnClickListener(mBtnClickListener);
        mWiredPortNo = (Button)findViewById(R.id.audio_wired_no);
        mWiredPortNo.setOnClickListener(mBtnClickListener);

        mLoopbackPlugReady = (Button)findViewById(R.id.audio_frequency_line_plug_ready_btn);
        mLoopbackPlugReady.setOnClickListener(mBtnClickListener);
        mLoopbackPlugReady.setEnabled(false);
        mTestButton = (Button)findViewById(R.id.audio_frequency_line_test_btn);
        mTestButton.setOnClickListener(mBtnClickListener);
        mResultText = (TextView)findViewById(R.id.audio_frequency_line_results_text);
        mProgressBar = (ProgressBar)findViewById(R.id.audio_frequency_line_progress_bar);
        showWait(false);
        enableLayout(R.id.audio_frequency_line_layout, false);         //disabled all content

        mSPlayer = new SoundPlayerObject();
        mSPlayer.setSoundWithResId(mContext, R.raw.stereo_mono_white_noise_48);
        mSPlayer.setBalance(0.5f);

        //Init FFT stuff
        mAudioShortArray2 = new short[mBlockSizeSamples*2];
        mData = new DspBufferDouble(mBlockSizeSamples);
        mC = new DspBufferComplex(mBlockSizeSamples);
        mFftServer = new DspFftServer(mBlockSizeSamples);

        int overlap = mBlockSizeSamples / 2;

        mWindow = new DspWindow(DspWindow.WINDOW_HANNING, mBlockSizeSamples, overlap);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_frequency_line_test,
                R.string.audio_frequency_line_info, -1);

        //Init bands
        bandSpecsArray[0] = new AudioBandSpecs(
                50, 500,        /* frequency start,stop */
                4.0, -50,     /* start top,bottom value */
                4.0, -4.0       /* stop top,bottom value */);

        bandSpecsArray[1] = new AudioBandSpecs(
                500,4000,       /* frequency start,stop */
                4.0, -4.0,      /* start top,bottom value */
                4.0, -4.0        /* stop top,bottom value */);

        bandSpecsArray[2] = new AudioBandSpecs(
                4000, 12000,    /* frequency start,stop */
                4.0, -4.0,      /* start top,bottom value */
                5.0, -5.0       /* stop top,bottom value */);

        bandSpecsArray[3] = new AudioBandSpecs(
                12000, 20000,   /* frequency start,stop */
                5.0, -5.0,      /* start top,bottom value */
                5.0, -30.0      /* stop top,bottom value */);
    }

    /**
     * show active progress bar
     */
    private void showWait(boolean show) {
        if (show) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    /**
     *  Start the loopback audio test
     */
    private void startAudioTest() {
        if (mTestThread != null && !mTestThread.isAlive()) {
            mTestThread = null; //kill it.
        }

        if (mTestThread == null) {
            Log.v(TAG,"Executing test Thread");
            mTestThread = new Thread(mPlayRunnable);
            getPassButton().setEnabled(false);
            if (!mSPlayer.isAlive())
                mSPlayer.start();
            mTestThread.start();
        } else {
            Log.v(TAG,"test Thread already running.");
        }
    }

    Thread mTestThread;
    Runnable mPlayRunnable = new Runnable() {
        public void run() {
            Message msg = Message.obtain();
            msg.what = TEST_STARTED;
            mMessageHandler.sendMessage(msg);

            sendMessage("Testing Left Capture");
            mCurrentTest = 0;
            mFreqAverage0.reset();
            mSPlayer.setBalance(0.0f);
            play();

            sendMessage("Testing Right Capture");
            mCurrentTest = 1;
            mFreqAverage1.reset();
            mSPlayer.setBalance(1.0f);
            play();

            mCurrentTest = -1;
            sendMessage("Testing Completed");

            Message msg2 = Message.obtain();
            msg2.what = TEST_ENDED;
            mMessageHandler.sendMessage(msg2);
        }

        private void play() {
            startRecording();
            mSPlayer.play(true);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mSPlayer.play(false);
            stopRecording();
        }

        private void sendMessage(String str) {
            Message msg = Message.obtain();
            msg.what = TEST_MESSAGE;
            msg.obj = str;
            mMessageHandler.sendMessage(msg);
        }
    };

    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case TEST_STARTED:
                showWait(true);
                getPassButton().setEnabled(false);
                break;
            case TEST_ENDED:
                showWait(false);
                computeResults();
                break;
            case TEST_MESSAGE:
                String str = (String)msg.obj;
                if (str != null) {
                    mResultText.setText(str);
                }
                break;
            default:
                Log.e(TAG, String.format("Unknown message: %d", msg.what));
            }
        }
    };

    private class Results {
        private String mLabel;
        public double[] mValuesLog;
        int[] mPointsPerBand = new int[mBands];
        double[] mAverageEnergyPerBand = new double[mBands];
        int[] mInBoundPointsPerBand = new int[mBands];
        public Results(String label) {
            mLabel = label;
        }

        //append results
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Channel %s\n", mLabel));
            sb.append("Level in Band 1 : " + (testLevel() ? "OK" :"Not Optimal") +"\n");
            for (int b = 0; b < mBands; b++) {
                double percent = 0;
                if (mPointsPerBand[b] > 0) {
                    percent = 100.0 * (double)mInBoundPointsPerBand[b] / mPointsPerBand[b];
                }
                sb.append(String.format(
                        " Band %d: Av. Level: %.1f dB InBand: %d/%d (%.1f%%) %s\n",
                        b, mAverageEnergyPerBand[b],
                        mInBoundPointsPerBand[b],
                        mPointsPerBand[b],
                        percent,
                        (testInBand(b) ? "OK" : "Not Optimal")));
            }
            return sb.toString();
        }

        public boolean testLevel() {
            if (mAverageEnergyPerBand[1] >= MIN_ENERGY_BAND_1) {
                return true;
            }
            return false;
        }

        public boolean testInBand(int b) {
            if (b >= 0 && b < mBands && mPointsPerBand[b] > 0) {
                if ((double)mInBoundPointsPerBand[b] / mPointsPerBand[b] >
                MIN_FRACTION_POINTS_IN_BAND)
                    return true;
            }
            return false;
        }

        public boolean testAll() {
            if (!testLevel()) {
                return false;
            }
            for (int b = 0; b < mBands; b++) {
                if (!testInBand(b)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * compute test results
     */
    private void computeResults() {
        Results resultsLeft = new Results("Left");
        computeResultsForVector(mFreqAverage0, resultsLeft);
        Results resultsRight = new Results("Right");
        computeResultsForVector(mFreqAverage1, resultsRight);
        if (resultsLeft.testAll() && resultsRight.testAll()) {
            String strSuccess = getResources().getString(R.string.audio_general_test_passed);
            appendResultsToScreen(strSuccess);
        } else {
            String strFailed = getResources().getString(R.string.audio_general_test_failed);
            appendResultsToScreen(strFailed + "\n");
            String strWarning = getResources().getString(R.string.audio_general_deficiency_found);
            appendResultsToScreen(strWarning);
        }
        getPassButton().setEnabled(true); //Everybody passes! (for now...)
    }

    private void computeResultsForVector(VectorAverage freqAverage,Results results) {

        int points = freqAverage.getSize();
        if (points > 0) {
            //compute vector in db
            double[] values = new double[points];
            freqAverage.getData(values, false);
            results.mValuesLog = new double[points];
            for (int i = 0; i < points; i++) {
                results.mValuesLog[i] = 20 * Math.log10(values[i]);
            }

            int currentBand = 0;
            for (int i = 0; i < points; i++) {
                double freq = (double)mSamplingRate * i / (double)mBlockSizeSamples;
                if (freq > bandSpecsArray[currentBand].mFreqStop) {
                    currentBand++;
                    if (currentBand >= mBands)
                        break;
                }

                if (freq >= bandSpecsArray[currentBand].mFreqStart) {
                    results.mAverageEnergyPerBand[currentBand] += results.mValuesLog[i];
                    results.mPointsPerBand[currentBand]++;
                }
            }

            for (int b = 0; b < mBands; b++) {
                if (results.mPointsPerBand[b] > 0) {
                    results.mAverageEnergyPerBand[b] =
                            results.mAverageEnergyPerBand[b] / results.mPointsPerBand[b];
                }
            }

            //set offset relative to band 1 level
            for (int b = 0; b < mBands; b++) {
                bandSpecsArray[b].setOffset(results.mAverageEnergyPerBand[1]);
            }

            //test points in band.
            currentBand = 0;
            for (int i = 0; i < points; i++) {
                double freq = (double)mSamplingRate * i / (double)mBlockSizeSamples;
                if (freq >  bandSpecsArray[currentBand].mFreqStop) {
                    currentBand++;
                    if (currentBand >= mBands)
                        break;
                }

                if (freq >= bandSpecsArray[currentBand].mFreqStart) {
                    double value = results.mValuesLog[i];
                    if (bandSpecsArray[currentBand].isInBounds(freq, value)) {
                        results.mInBoundPointsPerBand[currentBand]++;
                    }
                }
            }

            appendResultsToScreen(results.toString());

            //store results
            storeTestResults(results);
        } else {
            appendResultsToScreen("Failed testing channel " + results.mLabel);
        }
    }

    //append results
    private void appendResultsToScreen(String str) {
        String currentText = mResultText.getText().toString();
        mResultText.setText(currentText + "\n" + str);
    }

    /**
     * Store test results in log
     */
    private static final String SECTION_AUDIOFREQUENCYLINE =
            "audio_frequency_line";
    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_AUDIOFREQUENCYLINE);
    }

    private void storeTestResults(Results results) {
        String channelLabel = "channel_" + results.mLabel;

        CtsVerifierReportLog reportLog = getReportLog();
        for (int b = 0; b < mBands; b++) {
            String bandLabel = String.format(channelLabel + "_%d", b);
            reportLog.addValue(
                    bandLabel + "_Level",
                    results.mAverageEnergyPerBand[b],
                    ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);

            reportLog.addValue(
                    bandLabel + "_pointsinbound",
                    results.mInBoundPointsPerBand[b],
                    ResultType.HIGHER_BETTER,
                    ResultUnit.COUNT);

            reportLog.addValue(
                    bandLabel + "_pointstotal",
                    results.mPointsPerBand[b],
                    ResultType.NEUTRAL,
                    ResultUnit.COUNT);
        }

        reportLog.addValues(channelLabel + "_magnitudeSpectrumLog",
                results.mValuesLog,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        Log.v(TAG, "Results Recorded");
    }

    @Override // PassFailButtons
    public void recordTestResults() {
        getReportLog().submit();
    }

    private void recordHeasetPortFound(boolean found) {
        getReportLog().addValue(
                "User Reported Headset Port",
                found ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private void startRecording() {
        synchronized (mRecordingLock) {
            mIsRecording = true;
        }

        boolean successful = initRecord();
        if (successful) {
            startRecordingForReal();
        } else {
            Log.v(TAG, "Recorder initialization error.");
            synchronized (mRecordingLock) {
                mIsRecording = false;
            }
        }
    }

    private void startRecordingForReal() {
        // start streaming
        if (mRecordThread == null) {
            mRecordThread = new Thread(AudioFrequencyLineActivity.this);
            mRecordThread.setName("FrequencyAnalyzerThread");
            mRecordThreadShutdown = false;
        }
        if (!mRecordThread.isAlive()) {
            mRecordThread.start();
        }

        mPipe.flush();

        long startTime = SystemClock.uptimeMillis();
        mRecorder.startRecording();
        if (mRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            stopRecording();
            return;
        }
        Log.v(TAG, "Start time: " + (long) (SystemClock.uptimeMillis() - startTime) + " ms");
    }

    private void stopRecording() {
        synchronized (mRecordingLock) {
            stopRecordingForReal();
            mIsRecording = false;
        }
    }

    private void stopRecordingForReal() {

        // stop streaming
        Thread zeThread = mRecordThread;
        mRecordThread = null;
        mRecordThreadShutdown = true;
        if (zeThread != null) {
            zeThread.interrupt();
            try {
                zeThread.join();
            } catch(InterruptedException e) {
                Log.v(TAG,"Error shutting down recording thread " + e);
                //we don't really care about this error, just logging it.
            }
        }
         // release recording resources
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    private boolean initRecord() {
        int minRecordBuffSizeInBytes = AudioRecord.getMinBufferSize(mSamplingRate,
                mChannelConfig, mAudioFormat);
        Log.v(TAG,"FrequencyAnalyzer: min buff size = " + minRecordBuffSizeInBytes + " bytes");
        if (minRecordBuffSizeInBytes <= 0) {
            return false;
        }

        mMinRecordBufferSizeInSamples = minRecordBuffSizeInBytes / 2;
        // allocate the byte array to read the audio data

        mAudioShortArray = new short[mMinRecordBufferSizeInSamples];

        Log.v(TAG, "Initiating record:");
        Log.v(TAG, "      using source " + mSelectedRecordSource);
        Log.v(TAG, "      at " + mSamplingRate + "Hz");

        try {
            mRecorder = new AudioRecord(mSelectedRecordSource, mSamplingRate,
                    mChannelConfig, mAudioFormat, 2 * minRecordBuffSizeInBytes);
        } catch (IllegalArgumentException e) {
            Log.v(TAG, "Error: " + e.toString());
            return false;
        }
        if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            mRecorder.release();
            mRecorder = null;
            Log.v(TAG, "Error: mRecorder not initialized");
            return false;
        }
        mRecorder.setRecordPositionUpdateListener(this);
        mRecorder.setPositionNotificationPeriod(mBlockSizeSamples / 2);
        return true;
    }

    // ---------------------------------------------------------
    // Implementation of AudioRecord.OnPeriodicNotificationListener
    // --------------------
    public void onPeriodicNotification(AudioRecord recorder) {
        int samplesAvailable = mPipe.availableToRead();
        int samplesNeeded = mBlockSizeSamples;
        if (samplesAvailable >= samplesNeeded) {
            mPipe.read(mAudioShortArray2, 0, samplesNeeded);

            //compute stuff.
            double maxval = Math.pow(2, 15);
            int clipcount = 0;
            double cliplevel = (maxval-10) / maxval;
            double sum = 0;
            double maxabs = 0;
            int i;
            int index = 0;

            for (i = 0; i < samplesNeeded; i++) {
                double value = mAudioShortArray2[i] / maxval;
                double valueabs = Math.abs(value);

                if (valueabs > maxabs) {
                    maxabs = valueabs;
                }

                if (valueabs > cliplevel) {
                    clipcount++;
                }

                sum += value * value;
                //fft stuff
                if (index < mBlockSizeSamples) {
                    mData.mData[index] = value;
                }
                index++;
            }

            //for the current frame, compute FFT and send to the viewer.

            //apply window and pack as complex for now.
            DspBufferMath.mult(mData, mData, mWindow.mBuffer);
            DspBufferMath.set(mC, mData);
            mFftServer.fft(mC, 1);

            double[] halfMagnitude = new double[mBlockSizeSamples / 2];
            for (i = 0; i < mBlockSizeSamples / 2; i++) {
                halfMagnitude[i] = Math.sqrt(mC.mReal[i] * mC.mReal[i] + mC.mImag[i] * mC.mImag[i]);
            }

            mFreqAverageMain.setData(halfMagnitude, false); //average all of them!

            switch(mCurrentTest) {
                case 0:
                    mFreqAverage0.setData(halfMagnitude, false);
                    break;
                case 1:
                    mFreqAverage1.setData(halfMagnitude, false);
                    break;
            }
        }
    }

    public void onMarkerReached(AudioRecord track) {
    }

    // ---------------------------------------------------------
    // Implementation of Runnable for the audio recording + playback
    // --------------------
    public void run() {
        int nSamplesRead = 0;

        Thread thisThread = Thread.currentThread();
        while (mRecordThread == thisThread && !mRecordThreadShutdown) {
            // read from native recorder
            nSamplesRead = mRecorder.read(mAudioShortArray, 0, mMinRecordBufferSizeInSamples);
            if (nSamplesRead > 0) {
                mPipe.write(mAudioShortArray, 0, nSamplesRead);
            }
        }
    }
}
