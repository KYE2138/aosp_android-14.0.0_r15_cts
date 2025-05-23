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

package android.os.storage.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import static java.util.stream.Collectors.joining;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.ProxyFileDescriptorCallback;
import android.os.UserHandle;
import android.os.cts.R;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageManager.StorageVolumeCallback;
import android.os.storage.StorageVolume;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.test.ComparisonFailure;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FileUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.SyncFailedException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// The FixMethodOrder annotation is added to avoid flakiness caused by a change in test execution
// order (b/273874071).
// TODO(b/278069249): Investigate why the order matters, and possibly remove the annotation.
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class StorageManagerTest {

    private static final String TAG = StorageManager.class.getSimpleName();

    private static final long MAX_WAIT_TIME = 25*1000;
    private static final long WAIT_TIME_INCR = 5*1000;

    private static final String OBB_MOUNT_PREFIX = "/mnt/obb/";
    private static final String TEST1_NEW_CONTENTS = "1\n";

    private Context mContext;
    private StorageManager mStorageManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mStorageManager = mContext.getSystemService(StorageManager.class);
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testMountAndUnmountObbNormal() throws IOException {
        for (File target : getTargetFiles()) {
            target = new File(target, "test1_new.obb");
            Log.d(TAG, "Testing path " + target);
            doMountAndUnmountObbNormal(target);
        }
    }

    private void doMountAndUnmountObbNormal(File outFile) throws IOException {
        final String canonPath = mountObb(R.raw.test1_new, outFile, OnObbStateChangeListener.MOUNTED);

        mountObb(R.raw.test1_new, outFile, OnObbStateChangeListener.ERROR_ALREADY_MOUNTED);

        try {
            final String mountPath = checkMountedPath(canonPath);
            final File mountDir = new File(mountPath);
            final File testFile = new File(mountDir, "test1.txt");

            assertTrue("OBB mounted path should be a directory", mountDir.isDirectory());
            assertTrue("test1.txt does not exist in OBB dir", testFile.exists());
            assertFileContains(testFile, TEST1_NEW_CONTENTS);
        } finally {
            unmountObb(outFile, OnObbStateChangeListener.UNMOUNTED);
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testAttemptMountNonObb() {
        for (File target : getTargetFiles()) {
            target = new File(target, "test1_nosig.obb");
            Log.d(TAG, "Testing path " + target);
            doAttemptMountNonObb(target);
        }
    }

    private void doAttemptMountNonObb(File outFile) {
        try {
            mountObb(R.raw.test1_nosig, outFile, OnObbStateChangeListener.ERROR_INTERNAL);
            fail("mountObb should've failed with an exception");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        assertFalse("OBB should not be mounted",
                mStorageManager.isObbMounted(outFile.getPath()));

        assertNull("OBB's mounted path should be null",
                mStorageManager.getMountedObbPath(outFile.getPath()));
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testAttemptMountObbWrongPackage() {
        for (File target : getTargetFiles()) {
            target = new File(target, "test1_wrongpackage.obb");
            Log.d(TAG, "Testing path " + target);
            doAttemptMountObbWrongPackage(target);
        }
    }

    private void doAttemptMountObbWrongPackage(File outFile) {
        mountObb(R.raw.test1_wrongpackage, outFile,
                OnObbStateChangeListener.ERROR_PERMISSION_DENIED);

        assertFalse("OBB should not be mounted",
                mStorageManager.isObbMounted(outFile.getPath()));

        assertNull("OBB's mounted path should be null",
                mStorageManager.getMountedObbPath(outFile.getPath()));
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testMountAndUnmountTwoObbs() throws IOException {
        for (File target : getTargetFiles()) {
            Log.d(TAG, "Testing target " + target);
            final File test1 = new File(target, "test1.obb");
            final File test2 = new File(target, "test2.obb");
            doMountAndUnmountTwoObbs(test1, test2);
        }
    }

    private void doMountAndUnmountTwoObbs(File file1, File file2) throws IOException {
        ObbObserver oo1 = mountObbWithoutWait(R.raw.test1_new, file1);
        ObbObserver oo2 = mountObbWithoutWait(R.raw.test1_new, file2);

        Log.d(TAG, "Waiting for OBB #1 to complete mount");
        waitForObbActionCompletion(file1, oo1, OnObbStateChangeListener.MOUNTED);
        Log.d(TAG, "Waiting for OBB #2 to complete mount");
        waitForObbActionCompletion(file2, oo2, OnObbStateChangeListener.MOUNTED);

        try {
            final String mountPath1 = checkMountedPath(oo1.getPath());
            final File mountDir1 = new File(mountPath1);
            final File testFile1 = new File(mountDir1, "test1.txt");
            assertTrue("OBB mounted path should be a directory", mountDir1.isDirectory());
            assertTrue("test1.txt does not exist in OBB dir", testFile1.exists());
            assertFileContains(testFile1, TEST1_NEW_CONTENTS);

            final String mountPath2 = checkMountedPath(oo2.getPath());
            final File mountDir2 = new File(mountPath2);
            final File testFile2 = new File(mountDir2, "test1.txt");
            assertTrue("OBB mounted path should be a directory", mountDir2.isDirectory());
            assertTrue("test1.txt does not exist in OBB dir", testFile2.exists());
            assertFileContains(testFile2, TEST1_NEW_CONTENTS);
        } finally {
            unmountObb(file1, OnObbStateChangeListener.UNMOUNTED);
            unmountObb(file2, OnObbStateChangeListener.UNMOUNTED);
        }
    }

    @Test
    public void testGetPrimaryVolume() throws Exception {
        final StorageVolume volume = mStorageManager.getPrimaryStorageVolume();
        assertNotNull("Did not get primary storage", volume);

        // Tests some basic Scoped Directory Access requests.
        assertNull("Should not grant access for root directory", volume.createAccessIntent(null));
        assertNull("Should not grant access for invalid directory",
                volume.createAccessIntent("/system"));
        assertNotNull("Should grant access for valid directory " + Environment.DIRECTORY_DOCUMENTS,
                volume.createAccessIntent(Environment.DIRECTORY_DOCUMENTS));

        // Tests basic properties.
        assertNotNull("Should have description", volume.getDescription(mContext));
        assertTrue("Should be primary", volume.isPrimary());
        assertEquals("Wrong state", Environment.MEDIA_MOUNTED, volume.getState());

        // Tests properties that depend on storage type (emulated or physical)
        final String fsUuid = volume.getUuid();
        final UUID uuid = volume.getStorageUuid();
        final boolean removable = volume.isRemovable();
        final boolean emulated = volume.isEmulated();
        if (emulated) {
            assertFalse("Should not be externally managed", volume.isExternallyManaged());
            assertFalse("Should not be removable", removable);
            assertNull("Should not have fsUuid", fsUuid);
            assertEquals("Should have uuid_default", StorageManager.UUID_DEFAULT, uuid);
        } else {
            assertTrue("Should be removable", removable);
            assertNotNull("Should have fsUuid", fsUuid);
            assertNull("Should not have uuid", uuid);
        }

        // Tests path - although it's not a public API, sm.getPrimaryStorageVolume()
        // explicitly states it should match Environment#getExternalStorageDirectory
        final String path = volume.getPath();
        assertEquals("Path does not match Environment's",
                Environment.getExternalStorageDirectory().getAbsolutePath(), path);

        // Tests Parcelable contract.
        assertEquals("Wrong describeContents", 0, volume.describeContents());
        final Parcel parcel = Parcel.obtain();
        try {
            volume.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final StorageVolume clone = StorageVolume.CREATOR.createFromParcel(parcel);
            assertStorageVolumesEquals(volume, clone);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetStorageVolumes() throws Exception {
        final List<StorageVolume> volumes = mStorageManager.getStorageVolumes();
        assertFalse("No volume return", volumes.isEmpty());
        StorageVolume primary = null;
        for (StorageVolume volume : volumes) {
            if (volume.isPrimary()) {
                assertNull("There can be only one primary volume: " + volumes, primary);
                primary = volume;
            }
        }
        assertNotNull("No primary volume on  " + volumes, primary);
        assertStorageVolumesEquals(primary, mStorageManager.getPrimaryStorageVolume());
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetRecentStorageVolumes() throws Exception {
        // At a minimum recent volumes should include current volumes
        final Set<String> currentNames = mStorageManager.getStorageVolumes().stream()
                .map((v) -> v.getMediaStoreVolumeName()).collect(Collectors.toSet());
        final Set<String> recentNames = mStorageManager.getRecentStorageVolumes().stream()
                .map((v) -> v.getMediaStoreVolumeName()).collect(Collectors.toSet());
        assertTrue(recentNames.containsAll(currentNames));
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetStorageVolume() throws Exception {
        assertNull("Should not get volume for null path",
                mStorageManager.getStorageVolume((File) null));
        assertNull("Should not get volume for invalid path",
                mStorageManager.getStorageVolume(new File("/system")));
        assertNull("Should not get volume for storage directory",
                mStorageManager.getStorageVolume(Environment.getStorageDirectory()));

        final File root = Environment.getExternalStorageDirectory();
        Log.d("StorageManagerTest", "root: " + root);
        final StorageVolume primary = mStorageManager.getPrimaryStorageVolume();
        final StorageVolume rootVolume = mStorageManager.getStorageVolume(root);
        assertNotNull("No volume for root (" + root + ")", rootVolume);
        assertStorageVolumesEquals(primary, rootVolume);

        final File child = new File(root, "child");
        StorageVolume childVolume = mStorageManager.getStorageVolume(child);
        assertNotNull("No volume for child (" + child + ")", childVolume);
        Log.d("StorageManagerTest", "child: " + childVolume.getPath());
        assertStorageVolumesEquals(primary, childVolume);
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetStorageVolumeUSB() throws Exception {
        assumeTrue(StorageManagerHelper.isAdoptableStorageSupported(mContext));

        String volumeName = StorageManagerHelper.createUSBVirtualDisk();
        Log.d(TAG, "testGetStorageVolumeUSB#volumeName: " + volumeName);
        List<StorageVolume> storageVolumes = mStorageManager.getStorageVolumes();
        Optional<StorageVolume> usbStorageVolume =
                storageVolumes.stream().filter(sv->
                sv != null && sv.getPath() != null && sv.getPath().contains(volumeName)
            ).findFirst();
        assertTrue("The USB storage volume mounted on the main user is not present in "
                + storageVolumes.stream().map(StorageVolume::getPath)
                .collect(joining("\n")), usbStorageVolume.isPresent());
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetStorageVolumeSDCard() throws Exception {
        assumeTrue(StorageManagerHelper.isAdoptableStorageSupported(mContext));

        String volumeName = StorageManagerHelper.createSDCardVirtualDisk();
        Log.d(TAG, "testGetStorageVolumeSDCard#volumeName: " + volumeName);
        List<StorageVolume> storageVolumes = mStorageManager.getStorageVolumes();
        Optional<StorageVolume> sdCardStorageVolume =
                storageVolumes.stream().filter(sv->
                sv != null && sv.getPath() != null && sv.getPath().contains(volumeName)
            ).findFirst();
        assertTrue("The SdCard storage volume mounted on the main user is not present in "
                        + storageVolumes.stream().map(StorageVolume::getPath)
                        .collect(joining("\n")), sdCardStorageVolume.isPresent());
    }

    private void assertNoUuid(File file) {
        try {
            final UUID uuid = mStorageManager.getUuidForPath(file);
            fail("Unexpected UUID " + uuid + " for " + file);
        } catch (IOException expected) {
        }
    }

    @Test
    public void testGetUuidForPath() throws Exception {
        assertEquals(StorageManager.UUID_DEFAULT,
                mStorageManager.getUuidForPath(Environment.getDataDirectory()));
        assertEquals(StorageManager.UUID_DEFAULT,
                mStorageManager.getUuidForPath(mContext.getDataDir()));

        assertNoUuid(new File("/"));
        assertNoUuid(new File("/proc/"));
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetExternalUuidForPath() throws Exception {
        final UUID extUuid = mStorageManager
                .getUuidForPath(Environment.getExternalStorageDirectory());
        if (Environment.isExternalStorageEmulated()) {
            assertEquals(StorageManager.UUID_DEFAULT, extUuid);
        }

        assertEquals(extUuid, mStorageManager.getUuidForPath(mContext.getExternalCacheDir()));
        assertEquals(extUuid, mStorageManager.getUuidForPath(new File("/sdcard/")));
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testCallback() throws Exception {
        final CountDownLatch mounted = new CountDownLatch(1);
        final CountDownLatch unmounted = new CountDownLatch(1);
        final StorageVolumeCallback callback = new StorageVolumeCallback() {
            @Override
            public void onStateChanged(StorageVolume volume) {
                switch (volume.getState()) {
                    case Environment.MEDIA_MOUNTED: mounted.countDown(); break;
                    case Environment.MEDIA_UNMOUNTED: unmounted.countDown(); break;
                }
            }
        };

        // Unmount storage data and obb dirs before test so test process won't be killed.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "sm unmount-app-data-dirs " + mContext.getPackageName() + " "
                        + Process.myPid() + " " + UserHandle.myUserId());

        // Unmount primary storage, verify we can see it take effect
        mStorageManager.registerStorageVolumeCallback(mContext.getMainExecutor(), callback);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("sm unmount emulated;" + UserHandle.myUserId());
        assertTrue(unmounted.await(30, TimeUnit.SECONDS));

        // Now unregister and verify we don't hear future events
        mStorageManager.unregisterStorageVolumeCallback(callback);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("sm mount emulated;" + UserHandle.myUserId());
        assertFalse(mounted.await(15, TimeUnit.SECONDS));
    }

    private static class TestProxyFileDescriptorCallback extends ProxyFileDescriptorCallback {
        final byte[] bytes;
        int fsyncCount;
        int releaseCount;
        ErrnoException onGetSizeError = null;
        ErrnoException onReadError = null;
        ErrnoException onWriteError = null;
        ErrnoException onFsyncError = null;

        TestProxyFileDescriptorCallback(int size, String seed) {
            final byte[] seedBytes = seed.getBytes();
            bytes = new byte[size];
            for (int i = 0; i < size; i++) {
                bytes[i] = seedBytes[i % seedBytes.length];
            }
        }

        @Override
        public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
            if (onWriteError != null) {
                throw onWriteError;
            }
            for (int i = 0; i < size; i++) {
                bytes[(int)(i + offset)] = data[i];
            }
            return size;
        }

        @Override
        public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            if (onReadError != null) {
                throw onReadError;
            }
            final int len = (int)(Math.min(size, bytes.length - offset));
            for (int i = 0; i < len; i++) {
                data[i] = bytes[(int)(i + offset)];
            }
            return len;
        }

        @Override
        public long onGetSize() throws ErrnoException {
            if (onGetSizeError != null) {
                throw onGetSizeError;
            }
            return bytes.length;
        }

        @Override
        public void onFsync() throws ErrnoException {
            if (onFsyncError != null) {
                throw onFsyncError;
            }
            fsyncCount++;
        }

        @Override
        public void onRelease() {
            releaseCount++;
        }
    }

    @Test
    public void testOpenProxyFileDescriptor() throws Exception {
        final TestProxyFileDescriptorCallback appleCallback =
                new TestProxyFileDescriptorCallback(1024 * 1024, "Apple");
        final TestProxyFileDescriptorCallback orangeCallback =
                new TestProxyFileDescriptorCallback(1024 * 128, "Orange");
        final TestProxyFileDescriptorCallback cherryCallback =
                new TestProxyFileDescriptorCallback(1024 * 1024, "Cherry");
        try (final ParcelFileDescriptor appleFd = mStorageManager.openProxyFileDescriptor(
                     ParcelFileDescriptor.MODE_READ_ONLY, appleCallback, mHandler);
             final ParcelFileDescriptor orangeFd = mStorageManager.openProxyFileDescriptor(
                     ParcelFileDescriptor.MODE_WRITE_ONLY, orangeCallback, mHandler);
             final ParcelFileDescriptor cherryFd = mStorageManager.openProxyFileDescriptor(
                     ParcelFileDescriptor.MODE_READ_WRITE, cherryCallback, mHandler)) {
            // Stat
            assertEquals(appleCallback.onGetSize(), appleFd.getStatSize());
            assertEquals(orangeCallback.onGetSize(), orangeFd.getStatSize());
            assertEquals(cherryCallback.onGetSize(), cherryFd.getStatSize());

            final byte[] bytes = new byte[100];

            // Read
            for (int i = 0; i < 2; i++) {
                Os.read(appleFd.getFileDescriptor(), bytes, 0, 100);
                for (int j = 0; j < 100; j++) {
                    assertEquals(appleCallback.bytes[i * 100 + j], bytes[j]);
                }
            }
            try {
                Os.read(orangeFd.getFileDescriptor(), bytes, 0, 100);
                fail();
            } catch (ErrnoException exp) {
                assertEquals(OsConstants.EBADF, exp.errno);
            }
            for (int i = 0; i < 2; i++) {
                Os.read(cherryFd.getFileDescriptor(), bytes, 0, 100);
                for (int j = 0; j < 100; j++) {
                    assertEquals(cherryCallback.bytes[i * 100 + j], bytes[j]);
                }
            }

            // Pread
            Os.pread(appleFd.getFileDescriptor(), bytes, 0, 100, 500);
            for (int j = 0; j < 100; j++) {
                assertEquals(appleCallback.bytes[500 + j], bytes[j]);
            }
            try {
                Os.pread(orangeFd.getFileDescriptor(), bytes, 0, 100, 500);
                fail();
            } catch (ErrnoException exp) {
                assertEquals(OsConstants.EBADF, exp.errno);
            }
            Os.pread(cherryFd.getFileDescriptor(), bytes, 0, 100, 500);
            for (int j = 0; j < 100; j++) {
                assertEquals(cherryCallback.bytes[500 + j], bytes[j]);
            }

            // Write
            final byte[] writeSeed = "Strawberry".getBytes();
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = writeSeed[i % writeSeed.length];
            }
            try {
                Os.write(appleFd.getFileDescriptor(), bytes, 0, 100);
                fail();
            } catch (ErrnoException exp) {
                assertEquals(OsConstants.EBADF, exp.errno);
            }
            for (int i = 0; i < 2; i++) {
                Os.write(orangeFd.getFileDescriptor(), bytes, 0, 100);
                for (int j = 0; j < 100; j++) {
                    assertEquals(bytes[j], orangeCallback.bytes[i * 100 + j]);
                }
            }
            Os.lseek(cherryFd.getFileDescriptor(), 0, OsConstants.SEEK_SET);
            for (int i = 0; i < 2; i++) {
                Os.write(cherryFd.getFileDescriptor(), bytes, 0, 100);
                for (int j = 0; j < 100; j++) {
                    assertEquals(bytes[j], cherryCallback.bytes[i * 100 + j]);
                }
            }

            // Pwrite
            try {
                Os.pwrite(appleFd.getFileDescriptor(), bytes, 0, 100, 500);
                fail();
            } catch (ErrnoException exp) {
                assertEquals(OsConstants.EBADF, exp.errno);
            }
            Os.pwrite(orangeFd.getFileDescriptor(), bytes, 0, 100, 500);
            for (int j = 0; j < 100; j++) {
                assertEquals(bytes[j], orangeCallback.bytes[500 + j]);
            }
            Os.pwrite(cherryFd.getFileDescriptor(), bytes, 0, 100, 500);
            for (int j = 0; j < 100; j++) {
                assertEquals(bytes[j], cherryCallback.bytes[500 + j]);
            }

            // Flush
            assertEquals(0, appleCallback.fsyncCount);
            assertEquals(0, orangeCallback.fsyncCount);
            assertEquals(0, cherryCallback.fsyncCount);
            appleFd.getFileDescriptor().sync();
            orangeFd.getFileDescriptor().sync();
            cherryFd.getFileDescriptor().sync();
            assertEquals(1, appleCallback.fsyncCount);
            assertEquals(1, orangeCallback.fsyncCount);
            assertEquals(1, cherryCallback.fsyncCount);

            // Before release
            assertEquals(0, appleCallback.releaseCount);
            assertEquals(0, orangeCallback.releaseCount);
            assertEquals(0, cherryCallback.releaseCount);
        }

        // Release
        int retry = 3;
        while (true) {
            try {
                assertEquals(1, appleCallback.releaseCount);
                assertEquals(1, orangeCallback.releaseCount);
                assertEquals(1, cherryCallback.releaseCount);
                break;
            } catch (AssertionError error) {
                if (retry-- > 0) {
                   Thread.sleep(500);
                   continue;
                } else {
                    throw error;
                }
            }
        }
    }

    @Test
    public void testOpenProxyFileDescriptor_error() throws Exception {
        final TestProxyFileDescriptorCallback callback =
                new TestProxyFileDescriptorCallback(1024 * 1024, "Error");
        final byte[] bytes = new byte[128];
        callback.onGetSizeError = new ErrnoException("onGetSize", OsConstants.ENOENT);
        try {
            try (final ParcelFileDescriptor fd = mStorageManager.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_WRITE, callback, mHandler)) {}
            fail();
        } catch (IOException exp) {}
        callback.onGetSizeError = null;

        try (final ParcelFileDescriptor fd = mStorageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_WRITE, callback, mHandler)) {
            callback.onReadError = new ErrnoException("onRead", OsConstants.EIO);
            try {
                Os.read(fd.getFileDescriptor(), bytes, 0, bytes.length);
                fail();
            } catch (ErrnoException error) {}

            callback.onReadError = new ErrnoException("onRead", OsConstants.ENOSYS);
            try {
                Os.read(fd.getFileDescriptor(), bytes, 0, bytes.length);
                fail();
            } catch (ErrnoException error) {}

            callback.onReadError = null;
            Os.read(fd.getFileDescriptor(), bytes, 0, bytes.length);

            callback.onWriteError = new ErrnoException("onWrite", OsConstants.EIO);
            try {
                Os.write(fd.getFileDescriptor(), bytes, 0, bytes.length);
                fail();
            } catch (ErrnoException error) {}

            callback.onWriteError = new ErrnoException("onWrite", OsConstants.ENOSYS);
            try {
                Os.write(fd.getFileDescriptor(), bytes, 0, bytes.length);
                fail();
            } catch (ErrnoException error) {}

            callback.onWriteError = null;
            Os.write(fd.getFileDescriptor(), bytes, 0, bytes.length);

            callback.onFsyncError = new ErrnoException("onFsync", OsConstants.EIO);
            try {
                fd.getFileDescriptor().sync();
                fail();
            } catch (SyncFailedException error) {}

            callback.onFsyncError = new ErrnoException("onFsync", OsConstants.ENOSYS);
            try {
                fd.getFileDescriptor().sync();
                fail();
            } catch (SyncFailedException error) {}

            callback.onFsyncError = null;
            fd.getFileDescriptor().sync();
        }
    }

    @Test
    public void testOpenProxyFileDescriptor_async() throws Exception {
        final CountDownLatch blockReadLatch = new CountDownLatch(1);
        final CountDownLatch readBlockedLatch = new CountDownLatch(1);
        final CountDownLatch releaseLatch = new CountDownLatch(2);
        final TestProxyFileDescriptorCallback blockCallback =
                new TestProxyFileDescriptorCallback(1024 * 1024, "Block") {
            @Override
            public int onRead(long offset, int size, byte[] data) throws ErrnoException {
                try {
                    readBlockedLatch.countDown();
                    blockReadLatch.await();
                } catch (InterruptedException e) {
                    fail(e.getMessage());
                }
                return super.onRead(offset, size, data);
            }

            @Override
            public void onRelease() {
                releaseLatch.countDown();
            }
        };
        final TestProxyFileDescriptorCallback asyncCallback =
                new TestProxyFileDescriptorCallback(1024 * 1024, "Async") {
            @Override
            public void onRelease() {
                releaseLatch.countDown();
            }
        };
        final SynchronousQueue<Handler> handlerChannel = new SynchronousQueue<>();
        final Thread looperThread = new Thread(() -> {
            Looper.prepare();
            try {
                handlerChannel.put(new Handler());
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            Looper.loop();
        });
        looperThread.start();

        final Handler handler = handlerChannel.take();

        try (final ParcelFileDescriptor blockFd =
                    mStorageManager.openProxyFileDescriptor(
                            ParcelFileDescriptor.MODE_READ_ONLY, blockCallback, mHandler);
             final ParcelFileDescriptor asyncFd =
                    mStorageManager.openProxyFileDescriptor(
                            ParcelFileDescriptor.MODE_READ_ONLY, asyncCallback, handler)) {
            final Thread readingThread = new Thread(() -> {
                final byte[] bytes = new byte[128];
                try {

                    Os.read(blockFd.getFileDescriptor(), bytes, 0, 128);
                } catch (ErrnoException | InterruptedIOException e) {
                    fail(e.getMessage());
                }
            });
            readingThread.start();

            readBlockedLatch.countDown();
            assertEquals(Thread.State.RUNNABLE, readingThread.getState());

            final byte[] bytes = new byte[128];
            Log.d("StorageManagerTest", "start read async");
            assertEquals(128, Os.read(asyncFd.getFileDescriptor(), bytes, 0, 128));
            Log.d("StorageManagerTest", "stop read async");

            blockReadLatch.countDown();
            readingThread.join();
        }

        releaseLatch.await();
        handler.getLooper().quit();
        looperThread.join();
    }

    @Test
    public void testOpenProxyFileDescriptor_largeFile() throws Exception {
        final ProxyFileDescriptorCallback callback = new ProxyFileDescriptorCallback() {
            @Override
            public int onRead(long offset, int size, byte[] data) throws ErrnoException {
                for (int i = 0; i < size; i++) {
                    data[i] = 'L';
                }
                return size;
            }

            @Override
            public long onGetSize() throws ErrnoException {
                return 8L * 1024L * 1024L * 1024L;  // 8GB
            }

            @Override
            public void onRelease() {}
        };
        final byte[] bytes = new byte[128];
        try (final ParcelFileDescriptor fd = mStorageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY, callback, mHandler)) {
            assertEquals(8L * 1024L * 1024L * 1024L, fd.getStatSize());

            final int readBytes = Os.pread(
                    fd.getFileDescriptor(), bytes, 0, bytes.length, fd.getStatSize() - 64L);
            assertEquals(64, readBytes);
            for (int i = 0; i < 64; i++) {
                assertEquals('L', bytes[i]);
            }
        }
    }

    @Test
    public void testOpenProxyFileDescriptor_largeRead() throws Exception {
        final int SIZE = 1024 * 1024;
        final TestProxyFileDescriptorCallback callback =
                new TestProxyFileDescriptorCallback(SIZE, "abcdefghijklmnopqrstuvwxyz");
        final byte[] bytes = new byte[SIZE];
        try (final ParcelFileDescriptor fd = mStorageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY, callback, mHandler)) {
            final int readBytes = Os.read(
                    fd.getFileDescriptor(), bytes, 0, bytes.length);
            assertEquals(bytes.length, readBytes);
            for (int i = 0; i < bytes.length; i++) {
                assertEquals(callback.bytes[i], bytes[i]);
            }
        }
    }

    @Test
    public void testOpenProxyFileDescriptor_largeWrite() throws Exception {
        final int SIZE = 1024 * 1024;
        final TestProxyFileDescriptorCallback callback =
                new TestProxyFileDescriptorCallback(SIZE, "abcdefghijklmnopqrstuvwxyz");
        final byte[] bytes = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte)(i % 123);
        }
        try (final ParcelFileDescriptor fd = mStorageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_WRITE_ONLY, callback, mHandler)) {
            final int writtenBytes = Os.write(
                    fd.getFileDescriptor(), bytes, 0, bytes.length);
            assertEquals(bytes.length, writtenBytes);
            for (int i = 0; i < bytes.length; i++) {
                assertEquals(bytes[i], callback.bytes[i]);
            }
        }
    }

    @Test
    public void testIsAllocationSupported() throws Exception {
        FileDescriptor good = Os.open(
            File.createTempFile("StorageManagerTest", "").getAbsolutePath(),
            OsConstants.O_RDONLY, 0);
        FileDescriptor bad = Os.open("/proc/self/cmdline", OsConstants.O_RDONLY, 0);
        try {
            assertTrue(mStorageManager.isAllocationSupported(good));
            assertFalse(mStorageManager.isAllocationSupported(bad));
        } finally {
            try {
                Os.close(good);
            } catch (ErrnoException ignored) {}

            try {
                Os.close(bad);
            } catch (ErrnoException ignored) {}
        }
    }

    @Test
    public void testFatUuidHandling() throws Exception {
        assertEquals(UUID.fromString("fafafafa-fafa-5afa-8afa-fafa01234567"),
                StorageManager.convert("0123-4567"));
        assertEquals(UUID.fromString("fafafafa-fafa-5afa-8afa-fafadeadbeef"),
                StorageManager.convert("DEAD-BEEF"));
        assertEquals(UUID.fromString("fafafafa-fafa-5afa-8afa-fafadeadbeef"),
                StorageManager.convert("dead-BEEF"));

        try {
            StorageManager.convert("DEADBEEF");
            fail();
        } catch (IllegalArgumentException expected) {}

        try {
            StorageManager.convert("DEAD-BEEF0");
            fail();
        } catch (IllegalArgumentException expected) {}

        assertEquals("0123-4567",
                StorageManager.convert(UUID.fromString("fafafafa-fafa-5afa-8afa-fafa01234567")));
        assertEquals("DEAD-BEEF",
                StorageManager.convert(UUID.fromString("fafafafa-fafa-5afa-8afa-fafadeadbeef")));
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_EXTERNAL_STORAGE permission")
    public void testGetManageSpaceActivityIntent() throws Exception {
        String packageName = "android.os.cts";
        int REQUEST_CODE = 1;
        PendingIntent piActual = null;

        // Test should only pass with MANAGE_EXTERNAL_STORAGE permission
        assertThat(Environment.isExternalStorageManager()).isTrue();

        // Invalid packageName should throw an IllegalArgumentException
        String invalidPackageName = "this.is.invalid";
        assertThrows(
                IllegalArgumentException.class,
                () -> mStorageManager.getManageSpaceActivityIntent(invalidPackageName,
                        REQUEST_CODE));

        piActual = mStorageManager.getManageSpaceActivityIntent(packageName,
                REQUEST_CODE);
        assertThat(piActual.isActivity()).isTrue();

        // Nothing to assert, but call send to make sure it does not throw an exception
        piActual.send();

        // Drop MANAGE_EXTERNAL_STORAGE permission
        InstrumentationRegistry.getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    private void assertStorageVolumesEquals(StorageVolume volume, StorageVolume clone)
            throws Exception {
        // Asserts equals() method.
        assertEquals("StorageVolume.equals() mismatch", volume, clone);
        // Make sure all fields match.
        for (Field field : StorageVolume.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            final Object originalValue = field.get(volume);
            final Object clonedValue = field.get(clone);
            assertEquals("Mismatch for field " + field.getName(), originalValue, clonedValue);
        }
    }

    private static void assertStartsWith(String message, String prefix, String actual) {
        if (!actual.startsWith(prefix)) {
            throw new ComparisonFailure(message, prefix, actual);
        }
    }

    private static void assertFileContains(File file, String contents) throws IOException {
        byte[] actual = readFully(new FileInputStream(file));
        byte[] expected = contents.getBytes("UTF-8");
        assertEquals("unexpected size", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("unexpected value at offset " + i, expected[i], actual[i]);
        }
    }

    private static class ObbObserver extends OnObbStateChangeListener {
        private String path;

        public int state = -1;
        boolean done = false;

        @Override
        public void onObbStateChange(String path, int state) {
            Log.d(TAG, "Received message.  path=" + path + ", state=" + state);
            synchronized (this) {
                this.path = path;
                this.state = state;
                done = true;
                notifyAll();
            }
        }

        public String getPath() {
            assertTrue("Expected ObbObserver to have received a state change.", done);
            return path;
        }

        public int getState() {
            assertTrue("Expected ObbObserver to have received a state change.", done);
            return state;
        }

        public boolean isDone() {
            return done;
        }

        public boolean waitForCompletion() {
            long waitTime = 0;
            synchronized (this) {
                while (!isDone() && waitTime < MAX_WAIT_TIME) {
                    try {
                        wait(WAIT_TIME_INCR);
                        waitTime += WAIT_TIME_INCR;
                    } catch (InterruptedException e) {
                        Log.i(TAG, "Interrupted during sleep", e);
                    }
                }
            }

            return isDone();
        }
    }

    private List<File> getTargetFiles() {
        final List<File> targets = new ArrayList<File>();
        for (File dir : mContext.getObbDirs()) {
            assertNotNull("Valid media must be inserted during CTS", dir);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getStorageState(dir));
            targets.add(dir);
        }
        return targets;
    }

    private void copyRawToFile(int rawResId, File outFile) {
        Resources res = mContext.getResources();
        InputStream is = null;
        try {
            is = res.openRawResource(rawResId);
        } catch (NotFoundException e) {
            fail("Failed to load resource with id: " + rawResId);
        }
        assertTrue(FileUtils.copyToFile(is, outFile));
        exposeFile(outFile);
    }

    private File exposeFile(File file) {
        file.setReadable(true, false);
        file.setReadable(true, true);

        File dir = file.getParentFile();
        do {
            dir.setExecutable(true, false);
            dir.setExecutable(true, true);
            dir = dir.getParentFile();
        } while (dir != null);

        return file;
    }

    private String mountObb(final int resource, final File file, int expectedState) {
        copyRawToFile(resource, file);

        final ObbObserver observer = new ObbObserver();
        assertTrue("mountObb call on " + file.getPath() + " should succeed",
                mStorageManager.mountObb(file.getPath(), null, observer));

        assertTrue("Mount should have completed",
                observer.waitForCompletion());

        if (expectedState == OnObbStateChangeListener.MOUNTED) {
            assertTrue("OBB should be mounted", mStorageManager.isObbMounted(observer.getPath()));
        }

        assertEquals(expectedState, observer.getState());

        return observer.getPath();
    }

    private ObbObserver mountObbWithoutWait(final int resource, final File file) {
        copyRawToFile(resource, file);

        final ObbObserver observer = new ObbObserver();
        assertTrue("mountObb call on " + file.getPath() + " should succeed",
                mStorageManager.mountObb(file.getPath(), null, observer));

        return observer;
    }

    private void waitForObbActionCompletion(final File file, final ObbObserver observer,
            int expectedState) {
        assertTrue("Mount should have completed", observer.waitForCompletion());

        assertTrue("OBB should be mounted", mStorageManager.isObbMounted(observer.getPath()));

        assertEquals(expectedState, observer.getState());
    }

    private String checkMountedPath(final String path) {
        final String mountPath = mStorageManager.getMountedObbPath(path);
        assertStartsWith("Path should be in " + OBB_MOUNT_PREFIX,
                OBB_MOUNT_PREFIX,
                mountPath);
        return mountPath;
    }

    private void unmountObb(final File file, int expectedState) {
        final ObbObserver observer = new ObbObserver();

        assertTrue("unmountObb call on test1_new.obb should succeed",
                mStorageManager.unmountObb(file.getPath(), false, observer));

        assertTrue("Unmount should have completed",
                observer.waitForCompletion());

        assertEquals(expectedState, observer.getState());

        if (expectedState == OnObbStateChangeListener.UNMOUNTED) {
            assertFalse("OBB should not be mounted", mStorageManager.isObbMounted(file.getPath()));
        }
    }

    @Test
    public void testComputeStorageCacheBytes() throws Exception {
        File mockFile = mock(File.class);

        final int[] storageThresholdPercentHigh = new int[1];
        SystemUtil.runWithShellPermissionIdentity(() -> {
            storageThresholdPercentHigh[0] = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                StorageManager.STORAGE_THRESHOLD_PERCENT_HIGH_KEY, 0);
        });
        assertTrue("storageThresholdPercentHigh [" + storageThresholdPercentHigh[0]
                + "] expected to be greater than equal to 0", storageThresholdPercentHigh[0] >= 0);
        assertTrue("storageThresholdPercentHigh [" + storageThresholdPercentHigh[0]
                + "] expected to be lesser than equal to 100",
                storageThresholdPercentHigh[0] <= 100);

        when(mockFile.getUsableSpace()).thenReturn(10000L);
        when(mockFile.getTotalSpace()).thenReturn(15000L);
        final long[] resultHigh = new long[1];
        SystemUtil.runWithShellPermissionIdentity(() -> {
            resultHigh[0] = mStorageManager.computeStorageCacheBytes(mockFile);
        });
        assertTrue("" + resultHigh[0] + " expected to be greater than equal to 0",
                resultHigh[0] >= 0L);
        assertTrue("" + resultHigh[0] + " expected to be less than equal to total space",
                resultHigh[0] <= mockFile.getTotalSpace());

        when(mockFile.getUsableSpace()).thenReturn(10000L);
        when(mockFile.getTotalSpace()).thenReturn(250000L);
        final long[] resultLow = new long[1];
        SystemUtil.runWithShellPermissionIdentity(() -> {
            resultLow[0] = mStorageManager.computeStorageCacheBytes(mockFile);
        });
        assertTrue("" + resultLow[0] + " expected to be greater than equal to 0",
                resultLow[0] >= 0L);
        assertTrue("" + resultLow[0] + " expected to be less than equal to total space",
                resultLow[0] <= mockFile.getTotalSpace());

        when(mockFile.getUsableSpace()).thenReturn(10000L);
        when(mockFile.getTotalSpace()).thenReturn(100000L);
        final long[] resultModerate = new long[1];
        SystemUtil.runWithShellPermissionIdentity(() -> {
            resultModerate[0] = mStorageManager.computeStorageCacheBytes(mockFile);
        });
        assertTrue("" + resultModerate[0] + " expected to be greater than equal to 0",
                resultModerate[0] >= 0L);
        assertTrue("" + resultModerate[0] + " expected to be less than equal to total space",
                resultModerate[0] <= mockFile.getTotalSpace());
    }

    public static byte[] readFully(InputStream in) throws IOException {
        // Shamelessly borrowed from libcore.io.Streams
        try {
            return readFullyNoClose(in);
        } finally {
            in.close();
        }
    }

    public static byte[] readFullyNoClose(InputStream in) throws IOException {
        // Shamelessly borrowed from libcore.io.Streams
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
