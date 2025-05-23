/*
 * Copyright 2013 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS;
import static android.hardware.camera2.cts.CameraTestUtils.SESSION_READY_TIMEOUT_MS;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleImageReaderListener;
import static android.hardware.camera2.cts.CameraTestUtils.dumpFile;
import static android.hardware.camera2.cts.CameraTestUtils.getValueNotNull;

import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.cts.CameraTestUtils.ImageDropperListener;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.rs.BitmapUtils;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.ex.camera2.blocking.BlockingSessionCallback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>Basic test for ImageReader APIs. It uses CameraDevice as producer, camera
 * sends the data to the surface provided by imageReader. Below image formats
 * are tested:</p>
 *
 * <p>YUV_420_888: flexible YUV420, it is mandatory format for camera. </p>
 * <p>JPEG: used for JPEG still capture, also mandatory format. </p>
 * <p>Some invalid access test. </p>
 * <p>TODO: Add more format tests? </p>
 */
@RunWith(Parameterized.class)
public class ImageReaderTest extends Camera2AndroidTestCase {
    private static final String TAG = "ImageReaderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // Number of frame (for streaming requests) to be verified.
    private static final int NUM_FRAME_VERIFIED = 2;
    // Number of frame (for streaming requests) to be verified with log processing time.
    private static final int NUM_LONG_PROCESS_TIME_FRAME_VERIFIED = 10;
    // The time to hold each image for to simulate long processing time.
    private static final int LONG_PROCESS_TIME_MS = 300;
    // Max number of images can be accessed simultaneously from ImageReader.
    private static final int MAX_NUM_IMAGES = 5;
    // Max difference allowed between YUV and JPEG patches. This tolerance is intentionally very
    // generous to avoid false positives due to punch/saturation operations vendors apply to the
    // JPEG outputs.
    private static final double IMAGE_DIFFERENCE_TOLERANCE = 40;
    // Legacy level devices needs even larger tolerance because jpeg and yuv are not captured
    // from the same frame in legacy mode.
    private static final double IMAGE_DIFFERENCE_TOLERANCE_LEGACY = 60;

    private SimpleImageListener mListener;

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testFlexibleYuv() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.YUV_420_888, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDepth16() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.DEPTH16, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDepthPointCloud() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.DEPTH_POINT_CLOUD, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDynamicDepth() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.DEPTH_JPEG, /*repeating*/true);
                params.mCheckSession = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testY8() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.Y8, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testJpeg() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing jpeg capture for Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG, /*repeating*/false);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testRaw() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing raw capture for camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.RAW_SENSOR, /*repeating*/false);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testRawPrivate() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing raw capture for camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.RAW_PRIVATE, /*repeating*/false);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testP010() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing YUV P010 capture for Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isCapabilitySupported(CameraCharacteristics.
                            REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
                    continue;
                }
                Set<Long> availableProfiles =
                    mStaticInfo.getAvailableDynamicRangeProfilesChecked();
                assertFalse("Absent dynamic range profiles", availableProfiles.isEmpty());
                assertTrue("HLG10 not present in the available dynamic range profiles",
                        availableProfiles.contains(DynamicRangeProfiles.HLG10));

                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.YCBCR_P010, /*repeating*/false);
                params.mDynamicRangeProfile = DynamicRangeProfiles.HLG10;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDisplayP3Yuv() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(
                                ImageFormat.YUV_420_888);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing Display P3 Yuv capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.YUV_420_888, /*repeating*/false);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDisplayP3YuvRepeating() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(
                                ImageFormat.YUV_420_888);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing repeating Display P3 Yuv capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.YUV_420_888, /*repeating*/true);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDisplayP3Heic() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(ImageFormat.HEIC);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing Display P3 HEIC capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.HEIC, /*repeating*/false);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDisplayP3HeicRepeating() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(ImageFormat.HEIC);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing repeating Display P3 HEIC capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.HEIC, /*repeating*/true);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDisplayP3Jpeg() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(ImageFormat.JPEG);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing Display P3 JPEG capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG, /*repeating*/false);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testDisplayP3JpegRepeating() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(ImageFormat.JPEG);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing repeating Display P3 JPEG capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG, /*repeating*/true);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testSRGBJpeg() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(ImageFormat.JPEG);

                if (!availableColorSpaces.contains(ColorSpace.Named.SRGB)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing sRGB JPEG capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG, /*repeating*/false);
                params.mColorSpace = ColorSpace.Named.SRGB;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testSRGBJpegRepeating() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                            .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(ImageFormat.JPEG);

                if (!availableColorSpaces.contains(ColorSpace.Named.SRGB)) {
                    continue;
                }

                openDevice(id);
                Log.v(TAG, "Testing repeating sRGB JPEG capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG, /*repeating*/true);
                params.mColorSpace = ColorSpace.Named.SRGB;
                params.mUseColorSpace = true;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testJpegR() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isJpegRSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support Jpeg/R, skipping");
                    continue;
                }
                Log.v(TAG, "Testing Jpeg/R capture for Camera " + id);

                assertTrue(mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                        .REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT));

                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG_R, /*repeating*/false);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testJpegRDisplayP3() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                if (!mAllStaticInfo.get(id).isJpegRSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support Jpeg/R, skipping");
                    continue;
                }

                if (!mAllStaticInfo.get(id).isCapabilitySupported(CameraCharacteristics
                        .REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
                    continue;
                }
                Set<ColorSpace.Named> availableColorSpaces =
                        mAllStaticInfo.get(id).getAvailableColorSpacesChecked(
                                ImageFormat.JPEG_R);

                if (!availableColorSpaces.contains(ColorSpace.Named.DISPLAY_P3)) {
                    continue;
                }
                openDevice(id);
                Log.v(TAG, "Testing Display P3 Jpeg/R capture for Camera " + id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG_R, /*repeating*/false);
                params.mColorSpace = ColorSpace.Named.DISPLAY_P3;
                params.mUseColorSpace = true;
                params.mDynamicRangeProfile = DynamicRangeProfiles.HLG10;
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testHeic() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing heic capture for Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.HEIC, /*repeating*/false);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testRepeatingJpeg() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing repeating jpeg capture for Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.JPEG, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testRepeatingRaw() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing repeating raw capture for camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.RAW_SENSOR, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testRepeatingRawPrivate() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing repeating raw capture for camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.RAW_PRIVATE, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testRepeatingHeic() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing repeating heic capture for Camera " + id);
                openDevice(id);
                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.HEIC, /*repeating*/true);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testFlexibleYuvWithTimestampBase() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.YUV_420_888, /*repeating*/true);
                params.mValidateImageData = false;
                int[] timeBases = {OutputConfiguration.TIMESTAMP_BASE_SENSOR,
                        OutputConfiguration.TIMESTAMP_BASE_MONOTONIC,
                        OutputConfiguration.TIMESTAMP_BASE_REALTIME,
                        OutputConfiguration.TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED};
                for (int timeBase : timeBases) {
                    params.mTimestampBase = timeBase;
                    bufferFormatTestByCamera(params);
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testLongProcessingRepeatingRaw() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing long processing on repeating raw for camera " + id);

                if (!mAllStaticInfo.get(id).isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    continue;
                }
                openDevice(id);

                bufferFormatLongProcessingTimeTestByCamera(ImageFormat.RAW_SENSOR);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testLongProcessingRepeatingFlexibleYuv() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing long processing on repeating YUV for camera " + id);

                if (!mAllStaticInfo.get(id).isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    continue;
                }

                openDevice(id);
                bufferFormatLongProcessingTimeTestByCamera(ImageFormat.YUV_420_888);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test invalid access of image after an image is closed, further access
     * of the image will get an IllegalStateException. The basic assumption of
     * this test is that the ImageReader always gives direct byte buffer, which is always true
     * for camera case. For if the produced image byte buffer is not direct byte buffer, there
     * is no guarantee to get an ISE for this invalid access case.
     */
    @Test
    public void testInvalidAccessTest() throws Exception {
        // Test byte buffer access after an image is released, it should throw ISE.
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing invalid image access for Camera " + id);
                openDevice(id);
                invalidAccessTestAfterClose();
            } finally {
                closeDevice(id);
                closeDefaultImageReader();
            }
        }
    }

    /**
     * Test two image stream (YUV420_888 and JPEG) capture by using ImageReader.
     *
     * <p>Both stream formats are mandatory for Camera2 API</p>
     */
    @Test
    public void testYuvAndJpeg() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "YUV and JPEG testing for camera " + id);
                if (!mAllStaticInfo.get(id).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                openDevice(id);
                bufferFormatWithYuvTestByCamera(ImageFormat.JPEG);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test two image stream (YUV420_888 and JPEG) capture by using ImageReader with the ImageReader
     * factory method that has usage flag argument.
     *
     * <p>Both stream formats are mandatory for Camera2 API</p>
     */
    @Test
    public void testYuvAndJpegWithUsageFlag() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "YUV and JPEG testing for camera " + id);
                if (!mAllStaticInfo.get(id).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                openDevice(id);
                bufferFormatWithYuvTestByCamera(ImageFormat.JPEG, true);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testImageReaderBuilderSetHardwareBufferFormatAndDataSpace() throws Exception {
        long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        try (
            ImageReader reader = new ImageReader
                .Builder(20, 45)
                .setMaxImages(2)
                .setDefaultHardwareBufferFormat(HardwareBuffer.RGBA_8888)
                .setDefaultDataSpace(DataSpace.DATASPACE_BT709)
                .setUsage(usage)
                .build();
            ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
            Image outputImage = writer.dequeueInputImage()
        ) {
            assertEquals(2, reader.getMaxImages());
            assertEquals(usage, reader.getUsage());
            assertEquals(HardwareBuffer.RGBA_8888, reader.getHardwareBufferFormat());

            assertEquals(20, outputImage.getWidth());
            assertEquals(45, outputImage.getHeight());
            assertEquals(HardwareBuffer.RGBA_8888, outputImage.getFormat());
        }
    }

    @Test
    public void testImageReaderBuilderWithBLOBAndHEIF() throws Exception {
        long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        try (
            ImageReader reader = new ImageReader
                .Builder(20, 45)
                .setMaxImages(2)
                .setDefaultHardwareBufferFormat(HardwareBuffer.BLOB)
                .setDefaultDataSpace(DataSpace.DATASPACE_HEIF)
                .setUsage(usage)
                .build();
            ImageWriter writer = new ImageWriter.Builder(reader.getSurface()).build();
        ) {
            assertEquals(2, reader.getMaxImages());
            assertEquals(usage, reader.getUsage());
            assertEquals(HardwareBuffer.BLOB, reader.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_HEIF, reader.getDataSpace());
            // writer should have same dataspace/hardwarebuffer format as reader.
            assertEquals(HardwareBuffer.BLOB, writer.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_HEIF, writer.getDataSpace());
            // HEIC is the combination of HardwareBuffer.BLOB and Dataspace.DATASPACE_HEIF
            assertEquals(ImageFormat.HEIC, writer.getFormat());
        }
    }

    @Test
    public void testImageReaderBuilderWithBLOBAndJpegR() throws Exception {
        long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        try (
                ImageReader reader = new ImageReader
                        .Builder(20, 45)
                        .setMaxImages(2)
                        .setDefaultHardwareBufferFormat(HardwareBuffer.BLOB)
                        .setDefaultDataSpace(DataSpace.DATASPACE_JPEG_R)
                        .setUsage(usage)
                        .build();
                ImageWriter writer = new ImageWriter.Builder(reader.getSurface()).build();
        ) {
            assertEquals(2, reader.getMaxImages());
            assertEquals(usage, reader.getUsage());
            assertEquals(HardwareBuffer.BLOB, reader.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_JPEG_R, reader.getDataSpace());
            // writer should have same dataspace/hardwarebuffer format as reader.
            assertEquals(HardwareBuffer.BLOB, writer.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_JPEG_R, writer.getDataSpace());
            // Jpeg/R is the combination of HardwareBuffer.BLOB and Dataspace.DATASPACE_JPEG_R
            assertEquals(ImageFormat.JPEG_R, writer.getFormat());
        }
    }

    @Test
    public void testImageReaderBuilderWithBLOBAndJFIF() throws Exception {
        long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        try (
            ImageReader reader = new ImageReader
                .Builder(20, 45)
                .setMaxImages(2)
                .setDefaultHardwareBufferFormat(HardwareBuffer.BLOB)
                .setDefaultDataSpace(DataSpace.DATASPACE_JFIF)
                .setUsage(usage)
                .build();
            ImageWriter writer = new ImageWriter.Builder(reader.getSurface()).build();
        ) {
            assertEquals(2, reader.getMaxImages());
            assertEquals(usage, reader.getUsage());
            assertEquals(HardwareBuffer.BLOB, reader.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_JFIF, reader.getDataSpace());
            // writer should have same dataspace/hardwarebuffer format as reader.
            assertEquals(HardwareBuffer.BLOB, writer.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_JFIF, writer.getDataSpace());
            // JPEG is the combination of HardwareBuffer.BLOB and Dataspace.DATASPACE_JFIF
            assertEquals(ImageFormat.JPEG, writer.getFormat());
        }
    }

    @Test
    public void testImageReaderBuilderImageFormatOverride() throws Exception {
        try (
            ImageReader reader = new ImageReader
                .Builder(20, 45)
                .setImageFormat(ImageFormat.HEIC)
                .setDefaultHardwareBufferFormat(HardwareBuffer.RGB_888)
                .setDefaultDataSpace(DataSpace.DATASPACE_BT709)
                .build();
            ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
            Image outputImage = writer.dequeueInputImage()
        ) {
            assertEquals(1, reader.getMaxImages());
            assertEquals(HardwareBuffer.USAGE_CPU_READ_OFTEN, reader.getUsage());
            assertEquals(HardwareBuffer.RGB_888, reader.getHardwareBufferFormat());
            assertEquals(DataSpace.DATASPACE_BT709, reader.getDataSpace());

            assertEquals(20, outputImage.getWidth());
            assertEquals(45, outputImage.getHeight());
            assertEquals(HardwareBuffer.RGB_888, outputImage.getFormat());
        }
    }

    @Test
    public void testImageReaderBuilderSetImageFormat() throws Exception {
        try (
            ImageReader reader = new ImageReader
                .Builder(20, 45)
                .setMaxImages(2)
                .setImageFormat(ImageFormat.YUV_420_888)
                .build();
            ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
            Image outputImage = writer.dequeueInputImage()
        ) {
            assertEquals(2, reader.getMaxImages());
            assertEquals(ImageFormat.YUV_420_888, reader.getImageFormat());
            assertEquals(HardwareBuffer.USAGE_CPU_READ_OFTEN, reader.getUsage());
            // ImageFormat.YUV_420_888 hal dataspace is DATASPACE_JFIF
            assertEquals(DataSpace.DATASPACE_JFIF, reader.getDataSpace());

            // writer should retrieve all info from reader's surface
            assertEquals(DataSpace.DATASPACE_JFIF, writer.getDataSpace());
            assertEquals(HardwareBuffer.YCBCR_420_888, writer.getHardwareBufferFormat());

            assertEquals(20, outputImage.getWidth());
            assertEquals(45, outputImage.getHeight());
            assertEquals(ImageFormat.YUV_420_888, outputImage.getFormat());
        }
    }

    /**
     * Test two image stream (YUV420_888 and RAW_SENSOR) capture by using ImageReader.
     *
     */
    @Test
    public void testImageReaderYuvAndRaw() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "YUV and RAW testing for camera " + id);
                if (!mAllStaticInfo.get(id).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                openDevice(id);
                bufferFormatWithYuvTestByCamera(ImageFormat.RAW_SENSOR);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * If the camera device advertises the SECURE_IAMGE_DATA capability, test
     * ImageFormat.PRIVATE + PROTECTED usage capture by using ImageReader with the
     * ImageReader factory method that has usage flag argument, and uses a custom usage flag.
     */
    @Test
    public void testImageReaderPrivateWithProtectedUsageFlag() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Private format and protected usage testing for camera " + id);
                List<String> testCameraIds = new ArrayList<>();

                if (mAllStaticInfo.get(id).isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA)) {
                    // Test the camera id without using physical camera
                    testCameraIds.add(null);
                }

                if (mAllStaticInfo.get(id).isLogicalMultiCamera()) {
                    Set<String> physicalIdsSet =
                        mAllStaticInfo.get(id).getCharacteristics().getPhysicalCameraIds();
                    for (String physicalId : physicalIdsSet) {
                        if (mAllStaticInfo.get(physicalId).isCapabilitySupported(
                                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA)) {
                            testCameraIds.add(physicalId);
                        }
                    }
                }

                if (testCameraIds.isEmpty()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support secure image data capability, skipping");
                    continue;
                }
                openDevice(id);


                BufferFormatTestParam params = new BufferFormatTestParam(
                        ImageFormat.PRIVATE, /*repeating*/true);
                params.mSetUsageFlag = true;
                params.mUsageFlag = HardwareBuffer.USAGE_PROTECTED_CONTENT;
                params.mRepeating = true;
                params.mCheckSession = true;
                params.mValidateImageData = false;
                for (String testCameraId : testCameraIds) {
                    params.mPhysicalId = testCameraId;
                    bufferFormatTestByCamera(params);
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test two image stream (YUV420_888 and RAW_SENSOR) capture by using ImageReader with the
     * ImageReader factory method that has usage flag argument.
     *
     */
    @Test
    public void testImageReaderYuvAndRawWithUsageFlag() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "YUV and RAW testing for camera " + id);
                if (!mAllStaticInfo.get(id).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                openDevice(id);
                bufferFormatWithYuvTestByCamera(ImageFormat.RAW_SENSOR, true);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Check that the center patches for YUV and JPEG outputs for the same frame match for each YUV
     * resolution and format supported.
     */
    @Test
    public void testAllOutputYUVResolutions() throws Exception {
        Integer[] sessionStates = {BlockingSessionCallback.SESSION_READY,
                BlockingSessionCallback.SESSION_CONFIGURE_FAILED};
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing all YUV image resolutions for camera " + id);

                if (!mAllStaticInfo.get(id).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                openDevice(id);
                // Skip warmup on FULL mode devices.
                int warmupCaptureNumber = (mStaticInfo.isHardwareLevelLegacy()) ?
                        MAX_NUM_IMAGES - 1 : 0;

                // NV21 isn't supported by ImageReader.
                final int[] YUVFormats = new int[] {ImageFormat.YUV_420_888, ImageFormat.YV12};

                CameraCharacteristics.Key<StreamConfigurationMap> key =
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
                StreamConfigurationMap config = mStaticInfo.getValueFromKeyNonNull(key);
                int[] supportedFormats = config.getOutputFormats();
                List<Integer> supportedYUVFormats = new ArrayList<>();
                for (int format : YUVFormats) {
                    if (CameraTestUtils.contains(supportedFormats, format)) {
                        supportedYUVFormats.add(format);
                    }
                }

                Size[] jpegSizes = mStaticInfo.getAvailableSizesForFormatChecked(ImageFormat.JPEG,
                        StaticMetadata.StreamDirection.Output);
                assertFalse("JPEG output not supported for camera " + id +
                        ", at least one JPEG output is required.", jpegSizes.length == 0);

                Size maxJpegSize = CameraTestUtils.getMaxSize(jpegSizes);
                Size maxPreviewSize = mOrderedPreviewSizes.get(0);
                Size QCIF = new Size(176, 144);
                Size FULL_HD = new Size(1920, 1080);
                for (int format : supportedYUVFormats) {
                    Size[] targetCaptureSizes =
                            mStaticInfo.getAvailableSizesForFormatChecked(format,
                            StaticMetadata.StreamDirection.Output);

                    for (Size captureSz : targetCaptureSizes) {
                        if (VERBOSE) {
                            Log.v(TAG, "Testing yuv size " + captureSz + " and jpeg size "
                                    + maxJpegSize + " for camera " + mCamera.getId());
                        }

                        ImageReader jpegReader = null;
                        ImageReader yuvReader = null;
                        try {
                            // Create YUV image reader
                            SimpleImageReaderListener yuvListener = new SimpleImageReaderListener();
                            yuvReader = createImageReader(captureSz, format, MAX_NUM_IMAGES,
                                    yuvListener);
                            Surface yuvSurface = yuvReader.getSurface();

                            // Create JPEG image reader
                            SimpleImageReaderListener jpegListener =
                                    new SimpleImageReaderListener();
                            jpegReader = createImageReader(maxJpegSize,
                                    ImageFormat.JPEG, MAX_NUM_IMAGES, jpegListener);
                            Surface jpegSurface = jpegReader.getSurface();

                            // Setup session
                            List<Surface> outputSurfaces = new ArrayList<Surface>();
                            outputSurfaces.add(yuvSurface);
                            outputSurfaces.add(jpegSurface);
                            createSession(outputSurfaces);

                            int state = mCameraSessionListener.getStateWaiter().waitForAnyOfStates(
                                        Arrays.asList(sessionStates),
                                        CameraTestUtils.SESSION_CONFIGURE_TIMEOUT_MS);

                            if (state == BlockingSessionCallback.SESSION_CONFIGURE_FAILED) {
                                if (captureSz.getWidth() > maxPreviewSize.getWidth() ||
                                        captureSz.getHeight() > maxPreviewSize.getHeight()) {
                                    Log.v(TAG, "Skip testing {yuv:" + captureSz
                                            + " ,jpeg:" + maxJpegSize + "} for camera "
                                            + mCamera.getId() +
                                            " because full size jpeg + yuv larger than "
                                            + "max preview size (" + maxPreviewSize
                                            + ") is not supported");
                                    continue;
                                } else if (captureSz.equals(QCIF) &&
                                        ((maxJpegSize.getWidth() > FULL_HD.getWidth()) ||
                                         (maxJpegSize.getHeight() > FULL_HD.getHeight()))) {
                                    Log.v(TAG, "Skip testing {yuv:" + captureSz
                                            + " ,jpeg:" + maxJpegSize + "} for camera "
                                            + mCamera.getId() +
                                            " because QCIF + >Full_HD size is not supported");
                                    continue;
                                } else {
                                    fail("Camera " + mCamera.getId() +
                                            ":session configuration failed for {jpeg: " +
                                            maxJpegSize + ", yuv: " + captureSz + "}");
                                }
                            }

                            // Warm up camera preview (mainly to give legacy devices time to do 3A).
                            CaptureRequest.Builder warmupRequest =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            warmupRequest.addTarget(yuvSurface);
                            assertNotNull("Fail to get CaptureRequest.Builder", warmupRequest);
                            SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

                            for (int i = 0; i < warmupCaptureNumber; i++) {
                                startCapture(warmupRequest.build(), /*repeating*/false,
                                        resultListener, mHandler);
                            }
                            for (int i = 0; i < warmupCaptureNumber; i++) {
                                resultListener.getCaptureResult(CAPTURE_WAIT_TIMEOUT_MS);
                                Image image = yuvListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                                image.close();
                            }

                            // Capture image.
                            CaptureRequest.Builder mainRequest =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            for (Surface s : outputSurfaces) {
                                mainRequest.addTarget(s);
                            }

                            startCapture(mainRequest.build(), /*repeating*/false, resultListener,
                                    mHandler);

                            // Verify capture result and images
                            resultListener.getCaptureResult(CAPTURE_WAIT_TIMEOUT_MS);

                            Image yuvImage = yuvListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                            Image jpegImage = jpegListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);

                            //Validate captured images.
                            CameraTestUtils.validateImage(yuvImage, captureSz.getWidth(),
                                    captureSz.getHeight(), format, /*filePath*/null);
                            CameraTestUtils.validateImage(jpegImage, maxJpegSize.getWidth(),
                                    maxJpegSize.getHeight(), ImageFormat.JPEG, /*filePath*/null);

                            // Compare the image centers.
                            RectF jpegDimens = new RectF(0, 0, jpegImage.getWidth(),
                                    jpegImage.getHeight());
                            RectF yuvDimens = new RectF(0, 0, yuvImage.getWidth(),
                                    yuvImage.getHeight());

                            // Find scale difference between YUV and JPEG output
                            Matrix m = new Matrix();
                            m.setRectToRect(yuvDimens, jpegDimens, Matrix.ScaleToFit.START);
                            RectF scaledYuv = new RectF();
                            m.mapRect(scaledYuv, yuvDimens);
                            float scale = scaledYuv.width() / yuvDimens.width();

                            final int PATCH_DIMEN = 40; // pixels in YUV

                            // Find matching square patch of pixels in YUV and JPEG output
                            RectF tempPatch = new RectF(0, 0, PATCH_DIMEN, PATCH_DIMEN);
                            tempPatch.offset(yuvDimens.centerX() - tempPatch.centerX(),
                                    yuvDimens.centerY() - tempPatch.centerY());
                            Rect yuvPatch = new Rect();
                            tempPatch.roundOut(yuvPatch);

                            tempPatch.set(0, 0, PATCH_DIMEN * scale, PATCH_DIMEN * scale);
                            tempPatch.offset(jpegDimens.centerX() - tempPatch.centerX(),
                                    jpegDimens.centerY() - tempPatch.centerY());
                            Rect jpegPatch = new Rect();
                            tempPatch.roundOut(jpegPatch);

                            // Decode center patches
                            int[] yuvColors = convertPixelYuvToRgba(yuvPatch.width(),
                                    yuvPatch.height(), yuvPatch.left, yuvPatch.top, yuvImage);
                            Bitmap yuvBmap = Bitmap.createBitmap(yuvColors, yuvPatch.width(),
                                    yuvPatch.height(), Bitmap.Config.ARGB_8888);

                            byte[] compressedJpegData = CameraTestUtils.getDataFromImage(jpegImage);
                            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(
                                    compressedJpegData, /*offset*/0, compressedJpegData.length,
                                    /*isShareable*/true);
                            BitmapFactory.Options opt = new BitmapFactory.Options();
                            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            Bitmap fullSizeJpegBmap = decoder.decodeRegion(jpegPatch, opt);
                            Bitmap jpegBmap = Bitmap.createScaledBitmap(fullSizeJpegBmap,
                                    yuvPatch.width(), yuvPatch.height(), /*filter*/true);

                            // Compare two patches using average of per-pixel differences
                            double difference = BitmapUtils.calcDifferenceMetric(yuvBmap, jpegBmap);
                            double tolerance = IMAGE_DIFFERENCE_TOLERANCE;
                            if (mStaticInfo.isHardwareLevelLegacy()) {
                                tolerance = IMAGE_DIFFERENCE_TOLERANCE_LEGACY;
                            }
                            Log.i(TAG, "Difference for resolution " + captureSz + " is: " +
                                    difference);
                            if (difference > tolerance) {
                                // Dump files if running in verbose mode
                                if (DEBUG) {
                                    String jpegFileName = mDebugFileNameBase + "/" + captureSz +
                                            "_jpeg.jpg";
                                    dumpFile(jpegFileName, jpegBmap);
                                    String fullSizeJpegFileName = mDebugFileNameBase + "/" +
                                            captureSz + "_full_jpeg.jpg";
                                    dumpFile(fullSizeJpegFileName, compressedJpegData);
                                    String yuvFileName = mDebugFileNameBase + "/" + captureSz +
                                            "_yuv.jpg";
                                    dumpFile(yuvFileName, yuvBmap);
                                    String fullSizeYuvFileName = mDebugFileNameBase + "/" +
                                            captureSz + "_full_yuv.jpg";
                                    int[] fullYUVColors = convertPixelYuvToRgba(yuvImage.getWidth(),
                                            yuvImage.getHeight(), 0, 0, yuvImage);
                                    Bitmap fullYUVBmap = Bitmap.createBitmap(fullYUVColors,
                                            yuvImage.getWidth(), yuvImage.getHeight(),
                                            Bitmap.Config.ARGB_8888);
                                    dumpFile(fullSizeYuvFileName, fullYUVBmap);
                                }
                                fail("Camera " + mCamera.getId() + ": YUV image at capture size "
                                        + captureSz + " and JPEG image at capture size "
                                        + maxJpegSize + " for the same frame are not similar,"
                                        + " center patches have difference metric of "
                                        + difference + ", tolerance is " + tolerance);
                            }

                            // Stop capture, delete the streams.
                            stopCapture(/*fast*/false);
                            yuvImage.close();
                            jpegImage.close();
                            yuvListener.drain();
                            jpegListener.drain();
                        } finally {
                            closeImageReader(jpegReader);
                            jpegReader = null;
                            closeImageReader(yuvReader);
                            yuvReader = null;
                        }
                    }
                }

            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test that images captured after discarding free buffers are valid.
     */
    @Test
    public void testDiscardFreeBuffers() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.v(TAG, "Testing discardFreeBuffers for Camera " + id);
                openDevice(id);
                discardFreeBuffersTestByCamera();
            } finally {
                closeDevice(id);
            }
        }
    }

    /** Tests that usage bits are preserved */
    @Test
    public void testUsageRespected() throws Exception {
        final long REQUESTED_USAGE_BITS =
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE;
        ImageReader reader = ImageReader.newInstance(1, 1, PixelFormat.RGBA_8888, 1,
                REQUESTED_USAGE_BITS);
        Surface surface = reader.getSurface();
        Canvas canvas = surface.lockHardwareCanvas();
        canvas.drawColor(Color.RED);
        surface.unlockCanvasAndPost(canvas);
        Image image = null;
        for (int i = 0; i < 100; i++) {
            image = reader.acquireNextImage();
            if (image != null) break;
            Thread.sleep(10);
        }
        assertNotNull(image);
        HardwareBuffer buffer = image.getHardwareBuffer();
        assertNotNull(buffer);
        // Mask off the upper vendor bits
        int myBits = (int) (buffer.getUsage() & 0xFFFFFFF);
        assertWithMessage("Usage bits %s did not contain requested usage bits %s", myBits,
                REQUESTED_USAGE_BITS).that(myBits & REQUESTED_USAGE_BITS)
                        .isEqualTo(REQUESTED_USAGE_BITS);
    }

    private void testLandscapeToPortraitOverride(boolean overrideToPortrait) throws Exception {
        if (!SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            Log.i(TAG, "Landscape to portrait override not supported, skipping test");
            return;
        }

        for (String id : mCameraIdsUnderTest) {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(
                    id, /*overrideToPortrait*/false);
            int[] modes = c.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
            boolean supportsRotateAndCrop = false;
            for (int mode : modes) {
                if (mode == CameraMetadata.SCALER_ROTATE_AND_CROP_90
                        || mode == CameraMetadata.SCALER_ROTATE_AND_CROP_270) {
                    supportsRotateAndCrop = true;
                    break;
                }
            }

            if (!supportsRotateAndCrop) {
                Log.i(TAG, "Skipping non-rotate-and-crop cameraId " + id);
                continue;
            }

            int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (sensorOrientation != 0 && sensorOrientation != 180) {
                Log.i(TAG, "Skipping portrait orientation sensor cameraId " + id);
                continue;
            }

            Log.i(TAG, "Testing overrideToPortrait " + overrideToPortrait
                    + " for Camera " + id);

            if (overrideToPortrait) {
                c = mCameraManager.getCameraCharacteristics(id, overrideToPortrait);
                sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                assertTrue("SENSOR_ORIENTATION should imply portrait sensor.",
                        sensorOrientation == 90 || sensorOrientation == 270);
            }

            BufferFormatTestParam params = new BufferFormatTestParam(
                    ImageFormat.JPEG, /*repeating*/false);
            params.mValidateImageData = true;

            try {
                openDevice(id, overrideToPortrait);
                bufferFormatTestByCamera(params);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testLandscapeToPortraitOverrideEnabled() throws Exception {
        testLandscapeToPortraitOverride(true);
    }

    @Test
    public void testLandscapeToPortraitOverrideDisabled() throws Exception {
        testLandscapeToPortraitOverride(false);
    }

    /**
     * Convert a rectangular patch in a YUV image to an ARGB color array.
     *
     * @param w width of the patch.
     * @param h height of the patch.
     * @param wOffset offset of the left side of the patch.
     * @param hOffset offset of the top of the patch.
     * @param yuvImage a YUV image to select a patch from.
     * @return the image patch converted to RGB as an ARGB color array.
     */
    private static int[] convertPixelYuvToRgba(int w, int h, int wOffset, int hOffset,
                                               Image yuvImage) {
        final int CHANNELS = 3; // yuv
        final float COLOR_RANGE = 255f;

        assertTrue("Invalid argument to convertPixelYuvToRgba",
                w > 0 && h > 0 && wOffset >= 0 && hOffset >= 0);
        assertNotNull(yuvImage);

        int imageFormat = yuvImage.getFormat();
        assertTrue("YUV image must have YUV-type format",
                imageFormat == ImageFormat.YUV_420_888 || imageFormat == ImageFormat.YV12 ||
                        imageFormat == ImageFormat.NV21);

        int height = yuvImage.getHeight();
        int width = yuvImage.getWidth();

        Rect imageBounds = new Rect(/*left*/0, /*top*/0, /*right*/width, /*bottom*/height);
        Rect crop = new Rect(/*left*/wOffset, /*top*/hOffset, /*right*/wOffset + w,
                /*bottom*/hOffset + h);
        assertTrue("Output rectangle" + crop + " must lie within image bounds " + imageBounds,
                imageBounds.contains(crop));
        Image.Plane[] planes = yuvImage.getPlanes();

        Image.Plane yPlane = planes[0];
        Image.Plane cbPlane = planes[1];
        Image.Plane crPlane = planes[2];

        ByteBuffer yBuf = yPlane.getBuffer();
        int yPixStride = yPlane.getPixelStride();
        int yRowStride = yPlane.getRowStride();
        ByteBuffer cbBuf = cbPlane.getBuffer();
        int cbPixStride = cbPlane.getPixelStride();
        int cbRowStride = cbPlane.getRowStride();
        ByteBuffer crBuf = crPlane.getBuffer();
        int crPixStride = crPlane.getPixelStride();
        int crRowStride = crPlane.getRowStride();

        int[] output = new int[w * h];

        // TODO: Optimize this with renderscript intrinsics
        byte[] yRow = new byte[yPixStride * (w - 1) + 1];
        byte[] cbRow = new byte[cbPixStride * (w / 2 - 1) + 1];
        byte[] crRow = new byte[crPixStride * (w / 2 - 1) + 1];
        yBuf.mark();
        cbBuf.mark();
        crBuf.mark();
        int initialYPos = yBuf.position();
        int initialCbPos = cbBuf.position();
        int initialCrPos = crBuf.position();
        int outputPos = 0;
        for (int i = hOffset; i < hOffset + h; i++) {
            yBuf.position(initialYPos + i * yRowStride + wOffset * yPixStride);
            yBuf.get(yRow);
            if ((i & 1) == (hOffset & 1)) {
                cbBuf.position(initialCbPos + (i / 2) * cbRowStride + wOffset * cbPixStride / 2);
                cbBuf.get(cbRow);
                crBuf.position(initialCrPos + (i / 2) * crRowStride + wOffset * crPixStride / 2);
                crBuf.get(crRow);
            }
            for (int j = 0, yPix = 0, crPix = 0, cbPix = 0; j < w; j++, yPix += yPixStride) {
                float y = yRow[yPix] & 0xFF;
                float cb = cbRow[cbPix] & 0xFF;
                float cr = crRow[crPix] & 0xFF;

                // convert YUV -> RGB (from JFIF's "Conversion to and from RGB" section)
                int r = (int) Math.max(0.0f, Math.min(COLOR_RANGE, y + 1.402f * (cr - 128)));
                int g = (int) Math.max(0.0f,
                        Math.min(COLOR_RANGE, y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128)));
                int b = (int) Math.max(0.0f, Math.min(COLOR_RANGE, y + 1.772f * (cb - 128)));

                // Convert to ARGB pixel color (use opaque alpha)
                output[outputPos++] = Color.rgb(r, g, b);

                if ((j & 1) == 1) {
                    crPix += crPixStride;
                    cbPix += cbPixStride;
                }
            }
        }
        yBuf.rewind();
        cbBuf.rewind();
        crBuf.rewind();

        return output;
    }

    /**
     * Test capture a given format stream with yuv stream simultaneously.
     *
     * <p>Use fixed yuv size, varies targeted format capture size. Single capture is tested.</p>
     *
     * @param format The capture format to be tested along with yuv format.
     */
    private void bufferFormatWithYuvTestByCamera(int format) throws Exception {
        bufferFormatWithYuvTestByCamera(format, false);
    }

    /**
     * Test capture a given format stream with yuv stream simultaneously.
     *
     * <p>Use fixed yuv size, varies targeted format capture size. Single capture is tested.</p>
     *
     * @param format The capture format to be tested along with yuv format.
     * @param setUsageFlag The ImageReader factory method to be used (with or without specifying
     *                     usage flag)
     */
    private void bufferFormatWithYuvTestByCamera(int format, boolean setUsageFlag)
            throws Exception {
        if (format != ImageFormat.JPEG && format != ImageFormat.RAW_SENSOR
                && format != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

        final int NUM_SINGLE_CAPTURE_TESTED = MAX_NUM_IMAGES - 1;
        Size maxYuvSz = mOrderedPreviewSizes.get(0);
        Size[] targetCaptureSizes = mStaticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);

        for (Size captureSz : targetCaptureSizes) {
            if (VERBOSE) {
                Log.v(TAG, "Testing yuv size " + maxYuvSz.toString() + " and capture size "
                        + captureSz.toString() + " for camera " + mCamera.getId());
            }

            ImageReader captureReader = null;
            ImageReader yuvReader = null;
            try {
                // Create YUV image reader
                SimpleImageReaderListener yuvListener  = new SimpleImageReaderListener();
                if (setUsageFlag) {
                    yuvReader = createImageReader(maxYuvSz, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                            HardwareBuffer.USAGE_CPU_READ_OFTEN, yuvListener);
                } else {
                    yuvReader = createImageReader(maxYuvSz, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                            yuvListener);
                }

                Surface yuvSurface = yuvReader.getSurface();

                // Create capture image reader
                SimpleImageReaderListener captureListener = new SimpleImageReaderListener();
                if (setUsageFlag) {
                    captureReader = createImageReader(captureSz, format, MAX_NUM_IMAGES,
                            HardwareBuffer.USAGE_CPU_READ_OFTEN, captureListener);
                } else {
                    captureReader = createImageReader(captureSz, format, MAX_NUM_IMAGES,
                            captureListener);
                }
                Surface captureSurface = captureReader.getSurface();

                // Capture images.
                List<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(yuvSurface);
                outputSurfaces.add(captureSurface);
                CaptureRequest.Builder request = prepareCaptureRequestForSurfaces(outputSurfaces,
                        CameraDevice.TEMPLATE_PREVIEW);
                SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

                for (int i = 0; i < NUM_SINGLE_CAPTURE_TESTED; i++) {
                    startCapture(request.build(), /*repeating*/false, resultListener, mHandler);
                }

                // Verify capture result and images
                for (int i = 0; i < NUM_SINGLE_CAPTURE_TESTED; i++) {
                    resultListener.getCaptureResult(CAPTURE_WAIT_TIMEOUT_MS);
                    if (VERBOSE) {
                        Log.v(TAG, " Got the capture result back for " + i + "th capture");
                    }

                    Image yuvImage = yuvListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                    if (VERBOSE) {
                        Log.v(TAG, " Got the yuv image back for " + i + "th capture");
                    }

                    Image captureImage = captureListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                    if (VERBOSE) {
                        Log.v(TAG, " Got the capture image back for " + i + "th capture");
                    }

                    //Validate captured images.
                    CameraTestUtils.validateImage(yuvImage, maxYuvSz.getWidth(),
                            maxYuvSz.getHeight(), ImageFormat.YUV_420_888, /*filePath*/null);
                    CameraTestUtils.validateImage(captureImage, captureSz.getWidth(),
                            captureSz.getHeight(), format, /*filePath*/null);
                    yuvImage.close();
                    captureImage.close();
                }

                // Stop capture, delete the streams.
                stopCapture(/*fast*/false);
            } finally {
                closeImageReader(captureReader);
                captureReader = null;
                closeImageReader(yuvReader);
                yuvReader = null;
            }
        }
    }

    private void invalidAccessTestAfterClose() throws Exception {
        final int FORMAT = mStaticInfo.isColorOutputSupported() ?
            ImageFormat.YUV_420_888 : ImageFormat.DEPTH16;

        Size[] availableSizes = mStaticInfo.getAvailableSizesForFormatChecked(FORMAT,
                StaticMetadata.StreamDirection.Output);
        Image img = null;
        // Create ImageReader.
        mListener = new SimpleImageListener();
        createDefaultImageReader(availableSizes[0], FORMAT, MAX_NUM_IMAGES, mListener);

        // Start capture.
        CaptureRequest request = prepareCaptureRequest();
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        startCapture(request, /* repeating */false, listener, mHandler);

        mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
        img = mReader.acquireNextImage();
        Plane firstPlane = img.getPlanes()[0];
        ByteBuffer buffer = firstPlane.getBuffer();
        img.close();

        imageInvalidAccessTestAfterClose(img, firstPlane, buffer);
    }

    /**
     * Test that images captured after discarding free buffers are valid.
     */
    private void discardFreeBuffersTestByCamera() throws Exception {
        final int FORMAT = mStaticInfo.isColorOutputSupported() ?
            ImageFormat.YUV_420_888 : ImageFormat.DEPTH16;

        final Size SIZE = mStaticInfo.getAvailableSizesForFormatChecked(FORMAT,
                StaticMetadata.StreamDirection.Output)[0];
        // Create ImageReader.
        mListener = new SimpleImageListener();
        createDefaultImageReader(SIZE, FORMAT, MAX_NUM_IMAGES, mListener);

        // Start capture.
        final boolean REPEATING = true;
        final boolean SINGLE = false;
        CaptureRequest request = prepareCaptureRequest();
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        startCapture(request, REPEATING, listener, mHandler);

        // Validate images and capture results.
        validateImage(SIZE, FORMAT, NUM_FRAME_VERIFIED, REPEATING, /*colorSpace*/ null);
        validateCaptureResult(FORMAT, SIZE, listener, NUM_FRAME_VERIFIED);

        // Discard free buffers.
        mReader.discardFreeBuffers();

        // Validate images and capture resulst again.
        validateImage(SIZE, FORMAT, NUM_FRAME_VERIFIED, REPEATING, /*colorSpace*/ null);
        validateCaptureResult(FORMAT, SIZE, listener, NUM_FRAME_VERIFIED);

        // Stop repeating request in preparation for discardFreeBuffers
        mCameraSession.stopRepeating();
        mCameraSessionListener.getStateWaiter().waitForState(
                BlockingSessionCallback.SESSION_READY, SESSION_READY_TIMEOUT_MS);

        // Drain the reader queue and discard free buffers from the reader.
        Image img = mReader.acquireLatestImage();
        if (img != null) {
            img.close();
        }
        mReader.discardFreeBuffers();

        // Do a single capture for camera device to reallocate buffers
        mListener.reset();
        startCapture(request, SINGLE, listener, mHandler);
        validateImage(SIZE, FORMAT, /*captureCount*/ 1, SINGLE, /*colorSpace*/ null);
    }

    private class BufferFormatTestParam {
        public int mFormat;
        public boolean mRepeating;
        public boolean mSetUsageFlag = false;
        public long mUsageFlag = HardwareBuffer.USAGE_CPU_READ_OFTEN;
        public boolean mCheckSession = false;
        public boolean mValidateImageData = true;
        public String mPhysicalId = null;
        public long mDynamicRangeProfile = DynamicRangeProfiles.STANDARD;
        public ColorSpace.Named mColorSpace;
        public boolean mUseColorSpace = false;
        public int mTimestampBase = OutputConfiguration.TIMESTAMP_BASE_DEFAULT;

        BufferFormatTestParam(int format, boolean repeating) {
            mFormat = format;
            mRepeating = repeating;
        }
    };

    private void bufferFormatTestByCamera(BufferFormatTestParam params)
            throws Exception {
        int format = params.mFormat;
        boolean setUsageFlag = params.mSetUsageFlag;
        long usageFlag = params.mUsageFlag;
        boolean repeating = params.mRepeating;
        boolean validateImageData = params.mValidateImageData;
        int timestampBase = params.mTimestampBase;

        String physicalId = params.mPhysicalId;
        StaticMetadata staticInfo;
        if (physicalId == null) {
            staticInfo = mStaticInfo;
        } else {
            staticInfo = mAllStaticInfo.get(physicalId);
        }

        Size[] availableSizes = staticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);

        boolean secureTest = setUsageFlag &&
                ((usageFlag & HardwareBuffer.USAGE_PROTECTED_CONTENT) != 0);
        Size secureDataSize = null;
        if (secureTest) {
            secureDataSize = staticInfo.getCharacteristics().get(
                    CameraCharacteristics.SCALER_DEFAULT_SECURE_IMAGE_SIZE);
        }

        boolean validateTimestampBase = (timestampBase
                != OutputConfiguration.TIMESTAMP_BASE_DEFAULT);
        Integer deviceTimestampSource = staticInfo.getCharacteristics().get(
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        // for each resolution, test imageReader:
        for (Size sz : availableSizes) {
            try {
                // For secure mode test only test default secure data size if HAL advertises one.
                if (secureDataSize != null && !secureDataSize.equals(sz)) {
                    continue;
                }

                if (VERBOSE) {
                    Log.v(TAG, "Testing size " + sz.toString() + " format " + format
                            + " for camera " + mCamera.getId());
                }

                // Create ImageReader.
                mListener  = new SimpleImageListener();
                if (setUsageFlag) {
                    createDefaultImageReader(sz, format, MAX_NUM_IMAGES, usageFlag, mListener);
                } else {
                    createDefaultImageReader(sz, format, MAX_NUM_IMAGES, mListener);
                }

                // Don't queue up images if we won't validate them
                if (!validateImageData && !validateTimestampBase) {
                    ImageDropperListener imageDropperListener = new ImageDropperListener();
                    mReader.setOnImageAvailableListener(imageDropperListener, mHandler);
                }

                if (params.mCheckSession) {
                    checkImageReaderSessionConfiguration(
                            "Camera capture session validation for format: " + format + "failed",
                            physicalId);
                }

                ArrayList<OutputConfiguration> outputConfigs = new ArrayList<>();
                OutputConfiguration config = new OutputConfiguration(mReader.getSurface());
                assertTrue("Default timestamp source must be DEFAULT",
                        config.getTimestampBase() == OutputConfiguration.TIMESTAMP_BASE_DEFAULT);
                assertTrue("Default mirroring mode must be AUTO",
                        config.getMirrorMode() == OutputConfiguration.MIRROR_MODE_AUTO);
                if (physicalId != null) {
                    config.setPhysicalCameraId(physicalId);
                }
                config.setDynamicRangeProfile(params.mDynamicRangeProfile);
                config.setTimestampBase(params.mTimestampBase);
                outputConfigs.add(config);

                CaptureRequest request;
                if (params.mUseColorSpace) {
                    request = prepareCaptureRequestForColorSpace(
                        outputConfigs, CameraDevice.TEMPLATE_PREVIEW, params.mColorSpace)
                        .build();
                } else {
                    request = prepareCaptureRequestForConfigs(
                        outputConfigs, CameraDevice.TEMPLATE_PREVIEW).build();
                }

                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                startCapture(request, repeating, listener, mHandler);

                int numFrameVerified = repeating ? NUM_FRAME_VERIFIED : 1;

                if (validateTimestampBase) {
                    validateTimestamps(deviceTimestampSource, timestampBase, numFrameVerified,
                            listener, repeating);
                }

                if (validateImageData) {
                    // Validate images.
                    ColorSpace colorSpace = null;
                    if (params.mUseColorSpace) {
                        colorSpace = ColorSpace.get(params.mColorSpace);
                    }
                    validateImage(sz, format, numFrameVerified, repeating, colorSpace);
                }

                // Validate capture result.
                validateCaptureResult(format, sz, listener, numFrameVerified);

                // stop capture.
                stopCapture(/*fast*/false);
            } finally {
                closeDefaultImageReader();
            }

            // Only test one size for non-default timestamp base.
            if (timestampBase != OutputConfiguration.TIMESTAMP_BASE_DEFAULT) break;
        }
    }

    private void bufferFormatLongProcessingTimeTestByCamera(int format)
            throws Exception {

        final int TEST_SENSITIVITY_VALUE = mStaticInfo.getSensitivityClampToRange(204);
        final long TEST_EXPOSURE_TIME_NS = mStaticInfo.getExposureClampToRange(28000000);
        final long EXPOSURE_TIME_ERROR_MARGIN_NS = 100000;

        Size[] availableSizes = mStaticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);

        // for each resolution, test imageReader:
        for (Size sz : availableSizes) {
            Log.v(TAG, "testing size " + sz.toString());
            try {
                if (VERBOSE) {
                    Log.v(TAG, "Testing long processing time: size " + sz.toString() + " format " +
                            format + " for camera " + mCamera.getId());
                }

                // Create ImageReader.
                mListener  = new SimpleImageListener();
                createDefaultImageReader(sz, format, MAX_NUM_IMAGES, mListener);

                // Setting manual controls
                List<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(mReader.getSurface());
                CaptureRequest.Builder requestBuilder = prepareCaptureRequestForSurfaces(
                        outputSurfaces, CameraDevice.TEMPLATE_STILL_CAPTURE);

                requestBuilder.set(
                        CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_OFF);
                requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, TEST_SENSITIVITY_VALUE);
                requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, TEST_EXPOSURE_TIME_NS);

                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                startCapture(requestBuilder.build(), /*repeating*/true, listener, mHandler);

                for (int i = 0; i < NUM_LONG_PROCESS_TIME_FRAME_VERIFIED; i++) {
                    mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);

                    // Verify image.
                    Image img = mReader.acquireNextImage();
                    assertNotNull("Unable to acquire next image", img);
                    CameraTestUtils.validateImage(img, sz.getWidth(), sz.getHeight(), format,
                            mDebugFileNameBase);

                    // Verify the exposure time and iso match the requested values.
                    CaptureResult result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);

                    long exposureTimeDiff = TEST_EXPOSURE_TIME_NS -
                            getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
                    int sensitivityDiff = TEST_SENSITIVITY_VALUE -
                            getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);

                    mCollector.expectTrue(
                            String.format("Long processing frame %d format %d size %s " +
                                    "exposure time was %d expecting %d.", i, format, sz.toString(),
                                    getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME),
                                    TEST_EXPOSURE_TIME_NS),
                            exposureTimeDiff < EXPOSURE_TIME_ERROR_MARGIN_NS &&
                            exposureTimeDiff >= 0);

                    mCollector.expectTrue(
                            String.format("Long processing frame %d format %d size %s " +
                                    "sensitivity was %d expecting %d.", i, format, sz.toString(),
                                    getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY),
                                    TEST_SENSITIVITY_VALUE),
                            sensitivityDiff >= 0);


                    // Sleep to Simulate long porcessing before closing the image.
                    Thread.sleep(LONG_PROCESS_TIME_MS);
                    img.close();
                }
                // Stop capture.
                // Drain the reader queue in case the full queue blocks
                // HAL from delivering new results
                ImageDropperListener imageDropperListener = new ImageDropperListener();
                mReader.setOnImageAvailableListener(imageDropperListener, mHandler);
                Image img = mReader.acquireLatestImage();
                if (img != null) {
                    img.close();
                }
                stopCapture(/*fast*/false);
            } finally {
                closeDefaultImageReader();
            }
        }
    }

    /**
     * Validate capture results.
     *
     * @param format The format of this capture.
     * @param size The capture size.
     * @param listener The capture listener to get capture result callbacks.
     */
    private void validateCaptureResult(int format, Size size, SimpleCaptureCallback listener,
            int numFrameVerified) {
        for (int i = 0; i < numFrameVerified; i++) {
            CaptureResult result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);

            // TODO: Update this to use availableResultKeys once shim supports this.
            if (mStaticInfo.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)) {
                StaticMetadata staticInfo = mStaticInfo;
                boolean supportActivePhysicalIdConsistency =
                        PropertyUtil.getFirstApiLevel() >= Build.VERSION_CODES.S;
                if (mStaticInfo.isLogicalMultiCamera() && supportActivePhysicalIdConsistency
                        && mStaticInfo.isActivePhysicalCameraIdSupported()) {
                    String activePhysicalId =
                            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                    staticInfo = mAllStaticInfo.get(activePhysicalId);
                }

                Long exposureTime = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
                Integer sensitivity = getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);
                mCollector.expectInRange(
                        String.format(
                                "Capture for format %d, size %s exposure time is invalid.",
                                format, size.toString()),
                        exposureTime,
                        staticInfo.getExposureMinimumOrDefault(),
                        staticInfo.getExposureMaximumOrDefault()
                );
                mCollector.expectInRange(
                        String.format("Capture for format %d, size %s sensitivity is invalid.",
                                format, size.toString()),
                        sensitivity,
                        staticInfo.getSensitivityMinimumOrDefault(),
                        staticInfo.getSensitivityMaximumOrDefault()
                );
            }
            // TODO: add more key validations.
        }
    }

    private final class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        private final ConditionVariable imageAvailable = new ConditionVariable();
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (mReader != reader) {
                return;
            }

            if (VERBOSE) Log.v(TAG, "new image available");
            imageAvailable.open();
        }

        public void waitForAnyImageAvailable(long timeout) {
            if (imageAvailable.block(timeout)) {
                imageAvailable.close();
            } else {
                fail("wait for image available timed out after " + timeout + "ms");
            }
        }

        public void closePendingImages() {
            Image image = mReader.acquireLatestImage();
            if (image != null) {
                image.close();
            }
        }

        public void reset() {
            imageAvailable.close();
        }
    }

    private void validateImage(Size sz, int format, int captureCount,  boolean repeating,
            ColorSpace colorSpace) throws Exception {
        // TODO: Add more format here, and wrap each one as a function.
        Image img;
        final int MAX_RETRY_COUNT = 20;
        int numImageVerified = 0;
        int reTryCount = 0;
        while (numImageVerified < captureCount) {
            assertNotNull("Image listener is null", mListener);
            if (VERBOSE) Log.v(TAG, "Waiting for an Image");
            mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
            if (repeating) {
                /**
                 * Acquire the latest image in case the validation is slower than
                 * the image producing rate.
                 */
                img = mReader.acquireLatestImage();
                /**
                 * Sometimes if multiple onImageAvailable callbacks being queued,
                 * acquireLatestImage will clear all buffer before corresponding callback is
                 * executed. Wait for a new frame in that case.
                 */
                if (img == null && reTryCount < MAX_RETRY_COUNT) {
                    reTryCount++;
                    continue;
                }
            } else {
                img = mReader.acquireNextImage();
            }
            assertNotNull("Unable to acquire the latest image", img);
            if (VERBOSE) Log.v(TAG, "Got the latest image");
            CameraTestUtils.validateImage(img, sz.getWidth(), sz.getHeight(), format,
                    mDebugFileNameBase, colorSpace);
            HardwareBuffer hwb = img.getHardwareBuffer();
            assertNotNull("Unable to retrieve the Image's HardwareBuffer", hwb);
            if (format == ImageFormat.DEPTH_JPEG) {
                byte [] dynamicDepthBuffer = CameraTestUtils.getDataFromImage(img);
                assertTrue("Dynamic depth validation failed!",
                        validateDynamicDepthNative(dynamicDepthBuffer));
            }
            if (VERBOSE) Log.v(TAG, "finish validation of image " + numImageVerified);
            img.close();
            numImageVerified++;
            reTryCount = 0;
        }

        // Return all pending images to the ImageReader as the validateImage may
        // take a while to return and there could be many images pending.
        mListener.closePendingImages();
    }

    private void validateTimestamps(Integer deviceTimestampSource, int timestampBase,
            int captureCount, SimpleCaptureCallback listener, boolean repeating) throws Exception {
        Image img;
        final int MAX_RETRY_COUNT = 20;
        int numImageVerified = 0;
        int retryCount = 0;
        List<Long> imageTimestamps = new ArrayList<Long>();
        assertNotNull("Image listener is null", mListener);
        while (numImageVerified < captureCount) {
            if (VERBOSE) Log.v(TAG, "Waiting for an Image");
            mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
            if (repeating) {
                img = mReader.acquireLatestImage();
                if (img == null && retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    continue;
                }
            } else {
                img = mReader.acquireNextImage();
            }
            assertNotNull("Unable to acquire the latest image", img);
            if (VERBOSE) {
                Log.v(TAG, "Got the latest image with timestamp " + img.getTimestamp());
            }
            imageTimestamps.add(img.getTimestamp());
            img.close();
            numImageVerified++;
            retryCount = 0;
        }

        List<Long> captureStartTimestamps = listener.getCaptureStartTimestamps(captureCount);
        if (VERBOSE) {
            Log.v(TAG, "deviceTimestampSource: " + deviceTimestampSource
                    + ", timestampBase: " + timestampBase + ", captureStartTimestamps: "
                    + captureStartTimestamps + ", imageTimestamps: " + imageTimestamps);
        }
        if (timestampBase == OutputConfiguration.TIMESTAMP_BASE_SENSOR
                || (timestampBase == OutputConfiguration.TIMESTAMP_BASE_MONOTONIC
                && deviceTimestampSource == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN)
                || (timestampBase == OutputConfiguration.TIMESTAMP_BASE_REALTIME
                && deviceTimestampSource == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME)) {
            // Makes sure image timestamps match capture started timestamp
            for (Long timestamp : imageTimestamps) {
                mCollector.expectTrue("Image timestamp " + timestamp
                        + " should match one of onCaptureStarted timestamps "
                        + captureStartTimestamps,
                        captureStartTimestamps.contains(timestamp));
            }
        } else if (timestampBase == OutputConfiguration.TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED) {
            // Make sure that timestamp base is MONOTONIC. Do not strictly check against
            // choreographer callback because there are cases camera framework doesn't use
            // choreographer timestamp (when consumer is slower than camera for example).
            final int TIMESTAMP_THRESHOLD_MILLIS = 3000; // 3 seconds
            long monotonicTime = SystemClock.uptimeMillis();
            for (Long timestamp : imageTimestamps) {
                long timestampMs = TimeUnit.NANOSECONDS.toMillis(timestamp);
                mCollector.expectTrue("Image timestamp " + timestampMs + " ms should be in the "
                        + "same timebase as SystemClock.updateMillis " + monotonicTime
                        + " ms when timestamp base is set to CHOREOGRAPHER synced",
                        Math.abs(timestampMs - monotonicTime) < TIMESTAMP_THRESHOLD_MILLIS);
            }
        }

        // Return all pending images to the ImageReader as the validateImage may
        // take a while to return and there could be many images pending.
        mListener.closePendingImages();
    }

    /** Load dynamic depth validation jni on initialization */
    static {
        System.loadLibrary("ctscamera2_jni");
    }
    /**
     * Use the dynamic depth SDK to validate a dynamic depth file stored in the buffer.
     *
     * Returns false if the dynamic depth has validation errors. Validation warnings/errors
     * will be printed to logcat.
     */
    private static native boolean validateDynamicDepthNative(byte[] dynamicDepthBuffer);
}
