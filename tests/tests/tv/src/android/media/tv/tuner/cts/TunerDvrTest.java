/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.TunerVersionChecker;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrRecorder;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.dvr.OnPlaybackStatusChangedListener;
import android.media.tv.tuner.dvr.OnRecordStatusChangedListener;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterConfiguration;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.RecordSettings;
import android.media.tv.tuner.filter.Settings;
import android.media.tv.tuner.filter.TsFilterConfiguration;
import android.os.ParcelFileDescriptor;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerDvrTest {
    private static final String TAG = "MediaTunerDvrTest";
    private static final long STATUS_CHECK_INTERVAL_MS = 100L;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_TUNER);

    private Context mContext;
    private Tuner mTuner;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mTuner = new Tuner(mContext, null, 100);
    }

    @After
    public void tearDown() {
        if (mTuner != null) {
          mTuner.close();
          mTuner = null;
        }
    }

    @Test
    public void testDvrSettings() throws Exception {
        DvrSettings settings = getDvrSettings();

        assertEquals(Filter.STATUS_DATA_READY, settings.getStatusMask());
        assertEquals(200L, settings.getLowThreshold());
        assertEquals(800L, settings.getHighThreshold());
        assertEquals(188L, settings.getPacketSize());
        assertEquals(DvrSettings.DATA_FORMAT_TS, settings.getDataFormat());
    }

    @Test
    public void testDvrRecorder() throws Exception {
        DvrRecorder d = mTuner.openDvrRecorder(1000, getExecutor(), getRecordListener());
        assertNotNull(d);
        d.configure(getDvrSettings());

        File tmpFile = File.createTempFile("cts_tuner", "dvr_test");
        d.setFileDescriptor(
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE));

        Filter filter = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_RECORD, 1000, getExecutor(), getFilterCallback());
        if (filter != null) {
            Settings settings = RecordSettings
                    .builder(Filter.TYPE_TS)
                    .setTsIndexMask(RecordSettings.TS_INDEX_FIRST_PACKET)
                    .build();
            FilterConfiguration config = TsFilterConfiguration
                    .builder()
                    .setTpid(10)
                    .setSettings(settings)
                    .build();
            filter.configure(config);
            d.attachFilter(filter);
        }

        d.start();
        d.flush();
        if (filter != null) {
            filter.start();
            filter.flush();
        }
        d.write(10);
        d.write(new byte[3], 0, 3);
        d.stop();
        d.close();
        if (filter != null) {
            d.detachFilter(filter);
            filter.stop();
            filter.close();
        }

        tmpFile.delete();
    }

    @Test
    public void testDvrPlayback() throws Exception {
        DvrPlayback d = mTuner.openDvrPlayback(1000, getExecutor(), getPlaybackListener());
        assertNotNull(d);
        d.configure(getDvrSettings());

        File tmpFile = File.createTempFile("cts_tuner", "dvr_test");
        try (RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
            byte[] bytes = new byte[] {3, 5, 10, 22, 73, 33, 19};
            raf.write(bytes);
        }

        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        d.attachFilter(f);
        d.detachFilter(f);

        d.setFileDescriptor(
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE));

        d.start();
        d.flush();
        assertEquals(3, d.read(3));
        assertEquals(3, d.read(new byte[3], 0, 3));
        assertEquals(0, d.seek(0));
        assertEquals(3, d.read(3));
        assertEquals(3, d.read(new byte[3], 0, 3));
        assertEquals(5, d.seek(5));
        assertEquals(2, d.read(3));
        assertEquals(10, d.seek(10));
        assertEquals(0, d.read(3));
        d.stop();
        d.close();

        tmpFile.delete();
    }

    @Test
    public void testSetRecordBufferStatusCheckIntervalHint() throws Exception {
        DvrRecorder d = mTuner.openDvrRecorder(1000, getExecutor(), getRecordListener());
        assertNotNull(d);

        if (!TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_3_0)) {
            assertEquals(Tuner.RESULT_UNAVAILABLE,
                    d.setRecordBufferStatusCheckIntervalHint(STATUS_CHECK_INTERVAL_MS));
            return;
        }

        d.configure(getDvrSettings());

        File tmpFile = File.createTempFile("cts_tuner", "dvr_test");
        d.setFileDescriptor(
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE));

        assertEquals(Tuner.RESULT_INVALID_ARGUMENT, d.setRecordBufferStatusCheckIntervalHint(-1));
        assertEquals(Tuner.RESULT_SUCCESS,
                d.setRecordBufferStatusCheckIntervalHint(STATUS_CHECK_INTERVAL_MS));

        d.start();
        d.flush();
        d.write(10);
        d.write(new byte[3], 0, 3);
        d.stop();
        d.close();

        assertEquals(Tuner.RESULT_NOT_INITIALIZED,
                d.setRecordBufferStatusCheckIntervalHint(STATUS_CHECK_INTERVAL_MS));

        tmpFile.delete();
    }

    @Test
    public void testSetPlaybackBufferStatusCheckIntervalHint() throws Exception {
        DvrPlayback d = mTuner.openDvrPlayback(1000, getExecutor(), getPlaybackListener());
        assertNotNull(d);

        if (!TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_3_0)) {
            assertEquals(Tuner.RESULT_UNAVAILABLE,
                    d.setPlaybackBufferStatusCheckIntervalHint(STATUS_CHECK_INTERVAL_MS));
            return;
        }

        d.configure(getDvrSettings());

        File tmpFile = File.createTempFile("cts_tuner", "dvr_test");
        try (RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
            byte[] bytes = new byte[] {3, 5, 10, 22, 73, 33, 19};
            raf.write(bytes);
        }

        d.setFileDescriptor(
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE));

        assertEquals(Tuner.RESULT_INVALID_ARGUMENT,
                d.setPlaybackBufferStatusCheckIntervalHint(-1));
        assertEquals(Tuner.RESULT_SUCCESS,
                d.setPlaybackBufferStatusCheckIntervalHint(STATUS_CHECK_INTERVAL_MS));

        d.start();
        d.flush();
        assertEquals(3, d.read(3));
        assertEquals(3, d.read(new byte[3], 0, 3));
        assertEquals(0, d.seek(0));
        assertEquals(3, d.read(3));
        assertEquals(3, d.read(new byte[3], 0, 3));
        assertEquals(5, d.seek(5));
        assertEquals(2, d.read(3));
        assertEquals(10, d.seek(10));
        assertEquals(0, d.read(3));
        d.stop();
        d.close();

        assertEquals(Tuner.RESULT_NOT_INITIALIZED,
                d.setPlaybackBufferStatusCheckIntervalHint(STATUS_CHECK_INTERVAL_MS));

        tmpFile.delete();
    }

    private OnRecordStatusChangedListener getRecordListener() {
        return new OnRecordStatusChangedListener() {
            @Override
            public void onRecordStatusChanged(int status) {}
        };
    }

    private OnPlaybackStatusChangedListener getPlaybackListener() {
        return new OnPlaybackStatusChangedListener() {
            @Override
            public void onPlaybackStatusChanged(int status) {}
        };
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private DvrSettings getDvrSettings() {
        return DvrSettings
                .builder()
                .setStatusMask(Filter.STATUS_DATA_READY)
                .setLowThreshold(200L)
                .setHighThreshold(800L)
                .setPacketSize(188L)
                .setDataFormat(DvrSettings.DATA_FORMAT_TS)
                .build();
    }

    private FilterCallback getFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
    }

    private boolean hasTuner() {
        // TODO: move to a Utils class.
        return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
    }
}
