/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.mediastress.cts;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Junit / Instrumentation test case for the media player api
 */
public class CodecTest {
    private static String TAG = "CodecTest";
    private static MediaPlayer mMediaPlayer;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;

    private static int WAIT_FOR_COMMAND_TO_COMPLETE = 60000;  //1 min max.
    private static boolean mInitialized = false;
    private static boolean mPrepareReset = false;
    private static Looper mLooper = null;
    private static final Object mLock = new Object();
    private static final Object mPrepareDone = new Object();
    private static final Object mVideoSizeChanged = new Object();
    private static boolean mOnPrepareSuccess = false;
    private static final long PAUSE_WAIT_TIME = 3000;
    private static final long WAIT_TIME = 2000;
    private static final int SEEK_TIME = 10000;
    private static final int PLAYBACK_SETTLE_TIME_MS = 5000;
    private static final int SETUP_SETTLE_TIME_MS = 5000;

    public static CountDownLatch mFirstFrameLatch;
    public static CountDownLatch mCompletionLatch;
    public static boolean mOnCompleteSuccess = false;
    public static boolean mPlaybackError = false;

    public static int mMediaInfoUnknownCount = 0;
    public static int mMediaInfoVideoTrackLaggingCount = 0;
    public static int mMediaInfoBadInterleavingCount = 0;
    public static int mMediaInfoNotSeekableCount = 0;
    public static int mMediaInfoMetdataUpdateCount = 0;

    public static String printCpuInfo() {
        String cm = "dumpsys cpuinfo";
        String cpuinfo = null;
        int ch;
        try {
            Process  p = Runtime.getRuntime().exec(cm);
            InputStream in = p.getInputStream();
            StringBuffer sb = new StringBuffer(512);
            while ( ( ch = in.read() ) != -1 ) {
                sb.append((char) ch);
            }
            cpuinfo = sb.toString();
        } catch (IOException e) {
            Log.v(TAG, e.toString());
        }
        return cpuinfo;
    }


    public static int getDuration(String filePath) {
        Log.v(TAG, "getDuration - " + filePath);
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            mp.prepare();
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }
        int duration = mp.getDuration();
        Log.v(TAG, "Duration " + duration);
        mp.release();
        Log.v(TAG, "release");
        return duration;
    }

    public static boolean getCurrentPosition(String filePath) {
        Log.v(TAG, "GetCurrentPosition - " + filePath);
        int currentPosition = 0;
        long t1=0;
        long t2 =0;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            Log.v(TAG, "start playback");
            mp.prepare();
            mp.start();
            t1=SystemClock.uptimeMillis();
            Thread.sleep(10000);
            mp.pause();
            Thread.sleep(PAUSE_WAIT_TIME);
            t2=SystemClock.uptimeMillis();
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }
        currentPosition = mp.getCurrentPosition();
        mp.stop();
        mp.release();
        Log.v(TAG, "mp currentPositon = " + currentPosition + " play duration = " + (t2-t1));

        if ((currentPosition < ((t2-t1) *1.2)) && (currentPosition > 0))
            return true;
        else
            return false;
    }

    public static boolean seekTo(String filePath) {
        Log.v(TAG, "seekTo " + filePath);
        int currentPosition = 0;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            mp.prepare();
            mp.start();
            mp.seekTo(SEEK_TIME);
            Thread.sleep(WAIT_TIME);
            currentPosition = mp.getCurrentPosition();
        } catch (Exception e) {
            Log.v(TAG, e.getMessage());
        }
        mp.stop();
        mp.release();
        Log.v(TAG, "CurrentPosition = " + currentPosition);
        //The currentposition should be at least greater than the 80% of seek time
        if ((currentPosition > SEEK_TIME *0.8))
            return true;
        else
            return false;
    }

    public static boolean setLooping(String filePath) {
        int currentPosition = 0;
        int duration = 0;
        long t1 =0;
        long t2 =0;
        Log.v (TAG, "SetLooping - " + filePath);
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            mp.prepare();
            duration = mp.getDuration();
            Log.v(TAG, "setLooping duration " + duration);
            mp.setLooping(true);
            mp.start();
            Thread.sleep(5000);
            mp.seekTo(duration - 5000);
            t1=SystemClock.uptimeMillis();
            Thread.sleep(20000);
            t2=SystemClock.uptimeMillis();
            Log.v(TAG, "pause");
            //Bug# 1106852 - IllegalStateException will be thrown if pause is called
            //in here
            //mp.pause();
            currentPosition = mp.getCurrentPosition();
            Log.v(TAG, "looping position " + currentPosition + "duration = " + (t2-t1));
        } catch (Exception e) {
            Log.v(TAG, "Exception : " + e.toString());
        }
        mp.stop();
        mp.release();
        //The current position should be within 20% of the sleep time
        //and should be greater than zero.
        if ((currentPosition < ((t2-t1-5000)*1.2)) && currentPosition > 0)
            return true;
        else
            return false;
    }

    public static boolean pause(String filePath) throws Exception {
        Log.v(TAG, "pause - " + filePath);
        boolean misPlaying = true;
        boolean pauseResult = false;
        long t1=0;
        long t2=0;
        MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(filePath);
        mp.prepare();
        int duration = mp.getDuration();
        mp.start();
        t1=SystemClock.uptimeMillis();
        Thread.sleep(5000);
        mp.pause();
        Thread.sleep(PAUSE_WAIT_TIME);
        t2=SystemClock.uptimeMillis();
        misPlaying = mp.isPlaying();
        int curPosition = mp.getCurrentPosition();
        Log.v(TAG, filePath + " pause currentPositon " + curPosition);
        Log.v(TAG, "isPlaying "+ misPlaying + " wait time " + (t2 - t1) );
        String cpuinfo = printCpuInfo();
        Log.v(TAG, cpuinfo);
        if ((curPosition>0) && (curPosition < ((t2-t1) * 1.3)) && (misPlaying == false))
            pauseResult = true;
        mp.stop();
        mp.release();
        return pauseResult;
    }

    public static void prepareStopRelease(String filePath) throws Exception {
        Log.v(TAG, "prepareStopRelease" + filePath);
        MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(filePath);
        mp.prepare();
        mp.stop();
        mp.release();
    }

    public static void preparePauseRelease(String filePath) throws Exception {
        Log.v(TAG, "preparePauseRelease" + filePath);
        MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(filePath);
        mp.prepare();
        mp.pause();
        mp.release();
    }

    static MediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener =
        new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                synchronized (mVideoSizeChanged) {
                    Log.v(TAG, "sizechanged notification received ...");
                    mVideoSizeChanged.notify();
                }
            }
    };

    //Register the videoSizeChanged listener
    public static int videoHeight(String filePath) throws Exception {
        Log.v(TAG, "videoHeight - " + filePath);
        int videoHeight = 0;
        synchronized (mLock) {
            initializeMessageLooper();
            try {
                mLock.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
            } catch(Exception e) {
                Log.v(TAG, "looper was interrupted.");
                return 0;
            }
        }
        try {
            mMediaPlayer.setDataSource(filePath);
            mMediaPlayer.setDisplay(MediaFrameworkTest.getSurfaceView().getHolder());
            mMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
            synchronized (mVideoSizeChanged) {
                try {
                    mMediaPlayer.prepare();
                    mMediaPlayer.start();
                    mVideoSizeChanged.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
                } catch (Exception e) {
                    Log.v(TAG, "wait was interrupted");
                }
            }
            videoHeight = mMediaPlayer.getVideoHeight();
            terminateMessageLooper();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return videoHeight;
    }

    //Register the videoSizeChanged listener
    public static int videoWidth(String filePath) throws Exception {
        Log.v(TAG, "videoWidth - " + filePath);
        int videoWidth = 0;

        synchronized (mLock) {
            initializeMessageLooper();
            try {
                mLock.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
            } catch(Exception e) {
                Log.v(TAG, "looper was interrupted.");
                return 0;
            }
        }
        try {
            mMediaPlayer.setDataSource(filePath);
            mMediaPlayer.setDisplay(MediaFrameworkTest.getSurfaceView().getHolder());
            mMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
            synchronized (mVideoSizeChanged) {
                try {
                    mMediaPlayer.prepare();
                    mMediaPlayer.start();
                    mVideoSizeChanged.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
                } catch (Exception e) {
                    Log.v(TAG, "wait was interrupted");
                }
            }
            videoWidth = mMediaPlayer.getVideoWidth();
            terminateMessageLooper();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return videoWidth;
    }

    //This also test the streaming video which may take a long
    //time to start the playback.
    public static boolean videoSeekTo(String filePath) throws Exception {
        Log.v(TAG, "videoSeekTo - " + filePath);
        int currentPosition = 0;
        int duration = 0;
        boolean videoResult = false;
        MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(filePath);
        mp.setDisplay(MediaFrameworkTest.getSurfaceView().getHolder());
        mp.prepare();
        mp.start();

        Thread.sleep(5000);
        duration = mp.getDuration();
        Log.v(TAG, "video duration " + duration);
        mp.pause();
        Thread.sleep(PAUSE_WAIT_TIME);
        mp.seekTo(duration - 20000 );
        mp.start();
        Thread.sleep(1000);
        mp.pause();
        Thread.sleep(PAUSE_WAIT_TIME);
        mp.seekTo(duration/2);
        mp.start();
        Thread.sleep(10000);
        currentPosition = mp.getCurrentPosition();
        Log.v(TAG, "video currentPosition " + currentPosition);
        mp.release();
        if (currentPosition > (duration /2 )*0.9)
            return true;
        else
            return false;

    }

    public static boolean seekToEnd(String filePath) {
        Log.v(TAG, "seekToEnd - " + filePath);
        int duration = 0;
        int currentPosition = 0;
        boolean isPlaying = false;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            Log.v(TAG, "start playback");
            mp.prepare();
            duration = mp.getDuration();
            mp.seekTo(duration - 3000);
            mp.start();
            Thread.sleep(6000);
        } catch (Exception e) {}
        isPlaying = mp.isPlaying();
        currentPosition = mp.getCurrentPosition();
        Log.v(TAG, "seekToEnd currentPosition= " + currentPosition + " isPlaying = " + isPlaying);
        mp.stop();
        mp.release();
        Log.v(TAG, "duration = " + duration);
        if (currentPosition < 0.9 * duration || isPlaying)
            return false;
        else
            return true;
    }

    public static boolean shortMediaStop(String filePath) {
        Log.v(TAG, "shortMediaStop - " + filePath);
        //This test is only for the short media file
        int duration = 0;
        int currentPosition = 0;
        boolean isPlaying = false;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            Log.v(TAG, "start playback");
            mp.prepare();
            duration = mp.getDuration();
            mp.start();
            Thread.sleep(10000);
        } catch (Exception e) {}
        isPlaying = mp.isPlaying();
        currentPosition = mp.getCurrentPosition();
        Log.v(TAG, "seekToEnd currentPosition= " + currentPosition + " isPlaying = " + isPlaying);
        mp.stop();
        mp.release();
        Log.v(TAG, "duration = " + duration);
        if (currentPosition > duration || isPlaying)
            return false;
        else
            return true;
    }

    public static boolean playToEnd(String filePath) {
        Log.v(TAG, "shortMediaStop - " + filePath);
        //This test is only for the short media file
        int duration = 200000;
        int updateDuration = 0;
        int currentPosition = 0;
        boolean isPlaying = false;
        MediaPlayer mp = new MediaPlayer();
        try {
            Thread.sleep(5000);
            mp.setDataSource(filePath);
            Log.v(TAG, "start playback");
            mp.prepare();
            //duration = mp.getDuration();
            mp.start();
            Thread.sleep(50000);
        } catch (Exception e){}
        isPlaying = mp.isPlaying();
        currentPosition = mp.getCurrentPosition();
        //updateDuration = mp.getDuration();
        Log.v(TAG, "seekToEnd currentPosition= " + currentPosition + " isPlaying = " + isPlaying);
        mp.stop();
        mp.release();
        //Log.v(TAG, "duration = " + duration);
        //Log.v(TAG, "Update duration = " + updateDuration);
        if (currentPosition > duration || isPlaying)
            return false;
        else
            return true;
    }

    public static boolean seektoBeforeStart(String filePath){
        Log.v(TAG, "seektoBeforeStart - " + filePath);
        //This test is only for the short media file
        int duration = 0;
        int currentPosition = 0;

        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            mp.prepare();
            duration = mp.getDuration();
            mp.seekTo(duration - 10000);
            mp.start();
            currentPosition=mp.getCurrentPosition();
            mp.stop();
            mp.release();
        } catch (Exception e) {}
        if (currentPosition < duration/2)
            return false;
        else
            return true;
    }

    public static boolean mediaRecorderRecord(String filePath){
        Log.v(TAG, "SoundRecording - " + filePath);
        //This test is only for the short media file
        int duration = 0;
        try {
            MediaRecorder mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile(filePath);
            mRecorder.prepare();
            mRecorder.start();
            Thread.sleep(500);
            mRecorder.stop();
            Log.v(TAG, "sound recorded");
            mRecorder.release();
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }

        //Verify the recorded file
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(filePath);
            mp.prepare();
            duration = mp.getDuration();
            Log.v(TAG,"Duration " + duration);
            mp.release();
        } catch (Exception e) {}
        //Check the record media file length is greate than zero
        if (duration > 0)
            return true;
        else
            return false;

    }

    //Test for mediaMeta Data Thumbnail
    public static boolean getThumbnail(String filePath, String goldenPath) {
        Log.v(TAG, "getThumbnail - " + filePath);

        int goldenHeight = 0;
        int goldenWidth = 0;
        int outputWidth = 0;
        int outputHeight = 0;

        //This test is only for the short media file
        try {
            BitmapFactory mBitmapFactory = new BitmapFactory();

            Bitmap outThumbnail;
            try (MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever()) {
                try {
                    mediaMetadataRetriever.setDataSource(filePath);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                outThumbnail = mediaMetadataRetriever.getFrameAtTime(-1);
            }

            //Verify the thumbnail
            Bitmap goldenBitmap = mBitmapFactory.decodeFile(goldenPath);
            outputWidth = outThumbnail.getWidth();
            outputHeight = outThumbnail.getHeight();
            goldenHeight = goldenBitmap.getHeight();
            goldenWidth = goldenBitmap.getWidth();

            //check the image dimension
            if ((outputWidth != goldenWidth) || (outputHeight != goldenHeight))
                return false;

            // Check half line of pixel
            int x = goldenHeight / 2;
            for (int j = 1; j < goldenWidth / 2; j++) {
                if (goldenBitmap.getPixel(x, j) != outThumbnail.getPixel(x, j)) {
                    Log.v(TAG, "pixel = " + goldenBitmap.getPixel(x, j));
                    return false;
                }
           }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
            return false;
        }
        return true;
    }

    //Load midi file from resources
    public static boolean resourcesPlayback(AssetFileDescriptor afd, int expectedDuration) {
        int duration = 0;
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(), afd.getLength());
            mp.prepare();
            mp.start();
            duration = mp.getDuration();
            Thread.sleep(5000);
            mp.release();
        } catch (Exception e) {
            Log.v(TAG,e.getMessage());
        }
        if (duration > expectedDuration)
            return true;
        else
            return false;
    }

    public static boolean prepareAsyncReset(String filePath) {
        //preparesAsync
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(filePath);
            mp.prepareAsync();
            mp.reset();
            mp.release();
        } catch (Exception e) {
            Log.v(TAG,e.getMessage());
            return false;
        }
        return true;
    }


    public static boolean isLooping(String filePath) {
        MediaPlayer mp = null;

        try {
            mp = new MediaPlayer();
            if (mp.isLooping()) {
                Log.v(TAG, "MediaPlayer.isLooping() returned true after ctor");
                return false;
            }
            mp.setDataSource(filePath);
            mp.prepare();

            mp.setLooping(true);
            if (!mp.isLooping()) {
                Log.v(TAG, "MediaPlayer.isLooping() returned false after setLooping(true)");
                return false;
            }

            mp.setLooping(false);
            if (mp.isLooping()) {
                Log.v(TAG, "MediaPlayer.isLooping() returned true after setLooping(false)");
                return false;
            }
        } catch (Exception e) {
            Log.v(TAG, "Exception : " + e.toString());
            return false;
        } finally {
            if (mp != null)
                mp.release();
        }

        return true;
    }

    public static boolean isLoopingAfterReset(String filePath) {
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setDataSource(filePath);
            mp.prepare();

            mp.setLooping(true);
            mp.reset();
            if (mp.isLooping()) {
                Log.v(TAG, "MediaPlayer.isLooping() returned true after reset()");
                return false;
            }
        } catch (Exception e){
            Log.v(TAG, "Exception : " + e.toString());
            return false;
        } finally {
            if (mp != null)
                mp.release();
        }

        return true;
    }

    /*
     * Initializes the message looper so that the mediaPlayer object can
     * receive the callback messages.
     */
    private static void initializeMessageLooper() {
        Log.v(TAG, "start looper");
        new Thread() {
            @Override
            public void run() {
                // Set up a looper to be used by camera.
                Looper.prepare();
                Log.v(TAG, "start loopRun");
                // Save the looper so that we can terminate this thread
                // after we are done with it.
                mLooper = Looper.myLooper();
                mMediaPlayer = new MediaPlayer();
                synchronized (mLock) {
                    mInitialized = true;
                    mLock.notify();
                }
                Looper.loop();  // Blocks forever until Looper.quit() is called.
                Log.v(TAG, "initializeMessageLooper: quit.");
            }
        }.start();
    }

    /*
     * Terminates the message looper thread.
     */
    private static void terminateMessageLooper() {
        if (mLooper != null) {
            mLooper.quit();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
    }

    static MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            synchronized (mPrepareDone) {
                if(mPrepareReset) {
                    Log.v(TAG, "call Reset");
                    mMediaPlayer.reset();
                }
                Log.v(TAG, "notify the prepare callback");
                mPrepareDone.notify();
                mOnPrepareSuccess = true;
            }
        }
    };

    public static boolean prepareAsyncCallback(String filePath, boolean reset) throws Exception {
        //Added the PrepareReset flag which allow us to switch to different
        //test case.
        if (reset) {
            mPrepareReset = true;
        }

        synchronized (mLock) {
            initializeMessageLooper();
            try {
                mLock.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
            } catch(Exception e) {
                Log.v(TAG, "looper was interrupted.");
                return false;
            }
        }
        try{
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setDataSource(filePath);
            mMediaPlayer.setDisplay(MediaFrameworkTest.getSurfaceView().getHolder());
            mMediaPlayer.prepareAsync();
            synchronized (mPrepareDone) {
                try {
                    mPrepareDone.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
                } catch (Exception e) {
                    Log.v(TAG, "wait was interrupted.");
                }
            }
            terminateMessageLooper();
        }catch (Exception e) {
            Log.v(TAG,e.getMessage());
        }
       return mOnPrepareSuccess;
    }

    static MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
                Log.v(TAG, "notify the completion callback");
                mOnCompleteSuccess = true;
                mCompletionLatch.countDown();
        }
    };

    static MediaPlayer.OnErrorListener mOnErrorListener = new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
            Log.v(TAG, "playback error");
            mPlaybackError = true;
            mp.reset();
            mOnCompleteSuccess = false;
            mCompletionLatch.countDown();
            return true;
        }
    };

    static MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOWN:
                    mMediaInfoUnknownCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                    mMediaInfoVideoTrackLaggingCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    mMediaInfoBadInterleavingCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    mMediaInfoNotSeekableCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    mMediaInfoMetdataUpdateCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    mFirstFrameLatch.countDown();
                    break;
            }
            return true;
        }
    };

    private static void setupPlaybackMarkers() {
        // we only ever worry about these firing 1 time
        mFirstFrameLatch = new CountDownLatch(1);
        mCompletionLatch = new CountDownLatch(1);

        mOnCompleteSuccess = false;
        mPlaybackError = false;

        mMediaInfoUnknownCount = 0;
        mMediaInfoVideoTrackLaggingCount = 0;
        mMediaInfoBadInterleavingCount = 0;
        mMediaInfoNotSeekableCount = 0;
        mMediaInfoMetdataUpdateCount = 0;
    }

    // null == success, !null == reason why it failed
    public static String playMediaSample(String fileName) throws Exception {
        int duration = 0;
        int curPosition = 0;
        int nextPosition = 0;

        setupPlaybackMarkers();

        initializeMessageLooper();
        synchronized (mLock) {
            try {
                mLock.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
            } catch(Exception e) {
                Log.v(TAG, "looper was interrupted.");
                return "Looper was interrupted";
            }
        }
        try {
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mOnErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            Log.v(TAG, "playMediaSample: sample file name " + fileName);
            mMediaPlayer.setDataSource(fileName);
            mMediaPlayer.setDisplay(MediaFrameworkTest.getSurfaceView().getHolder());
            mMediaPlayer.prepare();
            duration = mMediaPlayer.getDuration();
            Log.v(TAG, "duration of media " + duration);

            // start to play
            long time_started = SystemClock.uptimeMillis();
            long time_firstFrame = time_started - 1;
            long time_completed = time_started - 1;
            mMediaPlayer.start();

            boolean happyStart = mFirstFrameLatch.await(SETUP_SETTLE_TIME_MS,
                            TimeUnit.MILLISECONDS);
            time_firstFrame = SystemClock.uptimeMillis();
            if (happyStart == false) {
                String msg = "playMediaSamples playback did not start within "
                                + SETUP_SETTLE_TIME_MS + " ms";
                Log.i(TAG, msg);
                return msg;
            }

            // now that we know playback has started, calculate when it should
            // finish and wait that long that. Account for what has already
            // played (should be very close to 0 as we get here shortly after playback
            // starts)
            int startingPosition = mMediaPlayer.getCurrentPosition();
            int remainingDuration = duration - startingPosition;

            boolean happyFinish = mCompletionLatch.await(remainingDuration + PLAYBACK_SETTLE_TIME_MS,
                            TimeUnit.MILLISECONDS);
            time_completed = SystemClock.uptimeMillis();

            // really helps diagnose the class of failures we've seen.
            if (true) {
                Log.i(TAG, "duration of video sample:             " + duration + " ms");
                Log.i(TAG, "full start+playback+completionsignal: "
                                + (time_completed - time_started) + " ms");
                Log.i(TAG, "total overhead:                       "
                                + (time_completed - time_started - duration) + " ms");
                Log.i(TAG, "time until 1st frame rendered:        "
                                + (time_firstFrame - time_started) + " ms");
                Log.i(TAG, "video position when started timer:    " + startingPosition + " ms");
                long preOverhead = (time_firstFrame - time_started) - (startingPosition);
                Log.i(TAG, "start() startup overhead:             " + preOverhead + " ms");
                long postOverhead = (time_completed - time_started) - duration - preOverhead;
                Log.i(TAG, "trailing costs overhead:              " + postOverhead + " ms");
            }

            // did we succeed?
            if (happyFinish == false) {
                // the test failed

                // wait a little more, to help who is trying to figure out why it's bad.
                boolean happyExtra = mCompletionLatch.await(10000, TimeUnit.MILLISECONDS);
                long time_extension = SystemClock.uptimeMillis();

                String extraTime = "";
                if (happyExtra) {
                    extraTime = " BUT complete after an additional "
                                    + (time_extension - time_completed) + " ms";
                } else {
                    extraTime = " AND still not complete after an additional "
                                    + (time_extension - time_completed) + " ms";
                }

                // it's still a failure, even if we did finish in extra time
                Log.e(TAG, "wait timed-out without onCompletion notification" + extraTime);
                return "wait timed-out without onCompletion notification" + extraTime;
            }
        } catch (Exception e) {
            Log.v(TAG, "playMediaSample Exception:" + e.getMessage());
        } finally {
            // we need to clean up, even if we tripped an early return above
            terminateMessageLooper();
        }
        return mOnCompleteSuccess ? null : "unknown failure reason";
    }
}
