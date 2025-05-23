/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.display.cts;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;


import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayStateManager;
import com.android.compatibility.common.util.SettingsStateKeeperRule;
import com.android.compatibility.common.util.StateKeeperRule;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tests that applications can create virtual displays and present content on them.
 *
 * This CTS test is unable to test public virtual displays since special permissions
 * are required.  See also framework VirtualDisplayTest unit tests.
 */
@RunWith(AndroidJUnit4.class)
public class VirtualDisplayTest {
    private static final String TAG = "VirtualDisplayTest";

    private static final String NAME = TAG;
    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;
    private static final int DENSITY = DisplayMetrics.DENSITY_MEDIUM;
    private static final float REQUESTED_REFRESH_RATE = 30.0f;
    private static final int TIMEOUT = 40000;

    // Colors that we use as a signal to determine whether some desired content was
    // drawn.  The colors themselves doesn't matter but we choose them to have with distinct
    // values for each color channel so as to detect possible RGBA vs. BGRA buffer format issues.
    // We should only observe RGBA buffers but some graphics drivers might incorrectly
    // deliver BGRA buffers to virtual displays instead.
    private static final int BLUEISH = 0xff1122ee;
    private static final int GREENISH = 0xff33dd44;

    private Context mContext;
    private DisplayManager mDisplayManager;
    private Handler mHandler;
    private final Lock mImageReaderLock = new ReentrantLock(true /*fair*/);
    private ImageReader mImageReader;
    private Surface mSurface;
    private ImageListener mImageListener;
    private HandlerThread mCheckThread;
    private Handler mCheckHandler;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.WRITE_SECURE_SETTINGS);

    @ClassRule
    public static final SettingsStateKeeperRule mAreUserDisabledHdrFormatsAllowedSettingsKeeper =
            new SettingsStateKeeperRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    Settings.Global.ARE_USER_DISABLED_HDR_FORMATS_ALLOWED);

    @ClassRule
    public static final SettingsStateKeeperRule mUserDisabledHdrFormatsSettingsKeeper =
            new SettingsStateKeeperRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    Settings.Global.USER_DISABLED_HDR_FORMATS);

    @Rule(order = 1)
    public StateKeeperRule<DisplayStateManager.DisplayState> mDisplayManagerStateKeeper =
            new StateKeeperRule<>(new DisplayStateManager(
                    InstrumentationRegistry.getInstrumentation().getTargetContext()));

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mDisplayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mImageListener = new ImageListener();
        // thread for image checking
        mCheckThread = new HandlerThread("TestHandler");
        mCheckThread.start();
        mCheckHandler = new Handler(mCheckThread.getLooper());

        mImageReaderLock.lock();
        try {
            mImageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);
            mImageReader.setOnImageAvailableListener(mImageListener, mCheckHandler);
            mSurface = mImageReader.getSurface();
        } finally {
            mImageReaderLock.unlock();
        }
    }

    @After
    public void tearDown() throws Exception {
        mImageReaderLock.lock();
        try {
            mImageReader.close();
            mImageReader = null;
            mSurface = null;
        } finally {
            mImageReaderLock.unlock();
        }
        mCheckThread.quit();
    }

    /**
     * Ensures that an application can create a private virtual display and show
     * its own windows on it.
     */
    @Test
    @AsbSecurityTest(cveBugId = 141745510)
    public void testPrivateVirtualDisplay() throws Exception {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                WIDTH, HEIGHT, DENSITY, mSurface, 0);
        assertNotNull("virtual display must not be null", virtualDisplay);

        Display display = virtualDisplay.getDisplay();
        try {
            assertDisplayRegistered(display, Display.FLAG_PRIVATE);
            assertEquals(mSurface, virtualDisplay.getSurface());

            // Show a private presentation on the display.
            assertDisplayCanShowPresentation("private presentation window",
                    display, BLUEISH, 0);
        } finally {
            virtualDisplay.release();
        }
        assertDisplayUnregistered(display);
    }

    /**
     * Ensures that an application can create a private presentation virtual display and show
     * its own windows on it.
     */
    @Test
    @AsbSecurityTest(cveBugId = 141745510)
    public void testPrivatePresentationVirtualDisplay() throws Exception {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                WIDTH, HEIGHT, DENSITY, mSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
        assertNotNull("virtual display must not be null", virtualDisplay);

        Display display = virtualDisplay.getDisplay();
        try {
            assertDisplayRegistered(display, Display.FLAG_PRIVATE | Display.FLAG_PRESENTATION);
            assertEquals(mSurface, virtualDisplay.getSurface());

            // Show a private presentation on the display.
            assertDisplayCanShowPresentation("private presentation window",
                    display, BLUEISH, 0);
        } finally {
            virtualDisplay.release();
        }
        assertDisplayUnregistered(display);
    }

    /**
     * Ensures that an application can create a private virtual display and show
     * its own windows on it where the surface is attached or detached dynamically.
     */
    @Test
    @AsbSecurityTest(cveBugId = 141745510)
    public void testPrivateVirtualDisplayWithDynamicSurface() throws Exception {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                WIDTH, HEIGHT, DENSITY, null, 0);
        assertNotNull("virtual display must not be null", virtualDisplay);

        Display display = virtualDisplay.getDisplay();
        try {
            assertDisplayRegistered(display, Display.FLAG_PRIVATE);
            assertNull(virtualDisplay.getSurface());

            // Attach the surface.
            virtualDisplay.setSurface(mSurface);
            assertEquals(mSurface, virtualDisplay.getSurface());

            // Show a private presentation on the display.
            assertDisplayCanShowPresentation("private presentation window",
                    display, BLUEISH, 0);

            // Detach the surface.
            virtualDisplay.setSurface(null);
            assertNull(virtualDisplay.getSurface());
        } finally {
            virtualDisplay.release();
        }
        assertDisplayUnregistered(display);
    }

    /**
     * Ensures that {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS} will
     * be clear if an application creates an virtual display without the
     * flag {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_TRUSTED}.
     */
    @Test
    public void testUntrustedSysDecorVirtualDisplay() throws Exception {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                WIDTH, HEIGHT, DENSITY, mSurface,
                VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS);
        assertNotNull("virtual display must not be null", virtualDisplay);

        Display display = virtualDisplay.getDisplay();
        try {
            // Verify that the created virtual display doesn't have flags
            // FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS.
            assertDisplayRegistered(display, Display.FLAG_PRIVATE);
            assertEquals(mSurface, virtualDisplay.getSurface());

            // Show a private presentation on the display.
            assertDisplayCanShowPresentation("private presentation window",
                    display, BLUEISH, 0);
        } finally {
            virtualDisplay.release();
        }
        assertDisplayUnregistered(display);
    }

    /**
     * Ensures that throws {@link SecurityException} when an application creates a trusted virtual
     * display without holding the permission {@code ADD_TRUSTED_DISPLAY}.
     */
    @Test
    public void testTrustedVirtualDisplay() throws Exception {
        try {
            VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                    WIDTH, HEIGHT, DENSITY, mSurface, VIRTUAL_DISPLAY_FLAG_TRUSTED);
        } catch (SecurityException e) {
            // Expected.
            return;
        }
        fail("SecurityException must be thrown if a trusted virtual display is created without"
                + "holding the permission ADD_TRUSTED_DISPLAY.");
    }

    /**
     * Ensures that an application can create a private virtual display with a requested
     * refresh rate and show its own windows on it.
     */
    @Test
    public void testVirtualDisplayWithRequestedRefreshRate() throws Exception {
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(NAME, WIDTH, HEIGHT, DENSITY)
                .setSurface(mSurface)
                .setRequestedRefreshRate(REQUESTED_REFRESH_RATE)
                .build();
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(config);
        assertNotNull("virtual display must not be null", virtualDisplay);
        Display display = virtualDisplay.getDisplay();
        try {
            assertDisplayRegistered(display, Display.FLAG_PRIVATE);
            assertEquals(mSurface, virtualDisplay.getSurface());

            assertEquals(display.getRefreshRate(), REQUESTED_REFRESH_RATE, 0.1f);
        } finally {
            virtualDisplay.release();
        }
        assertDisplayUnregistered(display);
    }

    @Test
    public void testHdrApiMethods() {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                WIDTH, HEIGHT, DENSITY, mSurface, /*flags*/ 0);
        try {
            assertFalse(virtualDisplay.getDisplay().isHdr());
            assertNull(virtualDisplay.getDisplay().getHdrCapabilities());
        } finally {
            virtualDisplay.release();
        }
    }

    @Test
    public void testGetHdrCapabilitiesWithUserDisabledFormats() {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                WIDTH, HEIGHT, DENSITY, mSurface, /*flags*/ 0);
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
        int[] userDisabledHdrTypes = {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
                Display.HdrCapabilities.HDR_TYPE_HLG};
        mDisplayManager.setUserDisabledHdrTypes(userDisabledHdrTypes);

        try {
            assertFalse(virtualDisplay.getDisplay().isHdr());
            assertNull(virtualDisplay.getDisplay().getHdrCapabilities());
        } finally {
            virtualDisplay.release();
        }
    }

    private void assertDisplayRegistered(Display display, int flags) {
        assertNotNull("display object must not be null", display);
        assertTrue("display must be valid", display.isValid());
        assertTrue("display id must be unique",
                display.getDisplayId() != Display.DEFAULT_DISPLAY);
        assertEquals("display must have correct flags", flags, display.getFlags());
        assertEquals("display name must match supplied name", NAME, display.getName());
        Point size = new Point();
        display.getSize(size);
        assertEquals("display width must match supplied width", WIDTH, size.x);
        assertEquals("display height must match supplied height", HEIGHT, size.y);
        assertEquals("display rotation must be 0",
                Surface.ROTATION_0, display.getRotation());
        assertNotNull("display must be registered",
                findDisplay(mDisplayManager.getDisplays(), NAME));

        if ((flags & Display.FLAG_PRESENTATION) != 0) {
            assertNotNull("display must be registered as a presentation display",
                    findDisplay(mDisplayManager.getDisplays(
                            DisplayManager.DISPLAY_CATEGORY_PRESENTATION), NAME));
        } else {
            assertNull("display must not be registered as a presentation display",
                    findDisplay(mDisplayManager.getDisplays(
                            DisplayManager.DISPLAY_CATEGORY_PRESENTATION), NAME));
        }
    }

    private void assertDisplayUnregistered(Display display) {
        assertNull("display must no longer be registered after being removed",
                findDisplay(mDisplayManager.getDisplays(), NAME));
        assertFalse("display must no longer be valid", display.isValid());
    }

    private void assertDisplayCanShowPresentation(String message, final Display display,
            final int color, final int windowFlags) {
        // At this point, we should not have seen any blue.
        assertTrue(message + ": display should not show content before window is shown",
                mImageListener.getColor() != color);

        final TestPresentation[] presentation = new TestPresentation[1];
        try {
            // Show the presentation.
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    presentation[0] = new TestPresentation(mContext, display,
                            color, windowFlags);
                    presentation[0].show();
                }
            });

            // Wait for the blue to be seen.
            assertTrue(message + ": display should show content after window is shown",
                    mImageListener.waitForColor(color, TIMEOUT));
        } finally {
            if (presentation[0] != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        presentation[0].dismiss();
                    }
                });
            }
        }
    }

    private void runOnUiThread(Runnable runnable) {
        Runnable waiter = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    notifyAll();
                }
            }
        };
        synchronized (waiter) {
            mHandler.post(runnable);
            mHandler.post(waiter);
            try {
                waiter.wait(TIMEOUT);
            } catch (InterruptedException ex) {
            }
        }
    }

    private Display findDisplay(Display[] displays, String name) {
        for (int i = 0; i < displays.length; i++) {
            if (displays[i].getName().equals(name)) {
                return displays[i];
            }
        }
        return null;
    }

    private final class TestPresentation extends Presentation {
        private final int mColor;
        private final int mWindowFlags;

        public TestPresentation(Context context, Display display,
                int color, int windowFlags) {
            super(context, display);
            mColor = color;
            mWindowFlags = windowFlags;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setTitle(TAG);
            getWindow().addFlags(mWindowFlags);

            // Create a solid color image to use as the content of the presentation.
            ImageView view = new ImageView(getContext());
            view.setImageDrawable(new ColorDrawable(mColor));
            view.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setContentView(view);
        }
    }

    /**
     * Watches for an image with a large amount of some particular solid color to be shown.
     */
    private final class ImageListener
            implements ImageReader.OnImageAvailableListener {
        private int mColor = -1;

        public int getColor() {
            synchronized (this) {
                return mColor;
            }
        }

        public boolean waitForColor(int color, long timeoutMillis) {
            long timeoutTime = SystemClock.uptimeMillis() + timeoutMillis;
            synchronized (this) {
                while (mColor != color) {
                    long now = SystemClock.uptimeMillis();
                    if (now >= timeoutTime) {
                        return false;
                    }
                    try {
                        wait(timeoutTime - now);
                    } catch (InterruptedException ex) {
                    }
                }
                return true;
            }
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            mImageReaderLock.lock();
            try {
                if (reader != mImageReader) {
                    return;
                }

                Log.d(TAG, "New image available from virtual display.");
                // Get the latest buffer
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    try {
                        // Scan for colors.
                        int color = scanImage(image);
                        synchronized (this) {
                            if (mColor != color) {
                                mColor = color;
                                notifyAll();
                            }
                        }
                    } finally {
                        image.close();
                    }
                }
            } finally {
                mImageReaderLock.unlock();
            }
        }

        private int scanImage(Image image) {
            final Image.Plane plane = image.getPlanes()[0];
            final ByteBuffer buffer = plane.getBuffer();
            final int width = image.getWidth();
            final int height = image.getHeight();
            final int pixelStride = plane.getPixelStride();
            final int rowStride = plane.getRowStride();
            final int rowPadding = rowStride - pixelStride * width;

            Log.d(TAG, "- Scanning image: width=" + width + ", height=" + height
                    + ", pixelStride=" + pixelStride + ", rowStride=" + rowStride);

            int offset = 0;
            int blackPixels = 0;
            int bluePixels = 0;
            int greenPixels = 0;
            int otherPixels = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = 0;
                    pixel |= (buffer.get(offset) & 0xff) << 16;     // R
                    pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
                    pixel |= (buffer.get(offset + 2) & 0xff);       // B
                    pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
                    if (pixel == Color.BLACK || pixel == 0) {
                        blackPixels += 1;
                    } else if (pixel == BLUEISH) {
                        bluePixels += 1;
                    } else if (pixel == GREENISH) {
                        greenPixels += 1;
                    } else {
                        otherPixels += 1;
                        if (otherPixels < 10) {
                            Log.d(TAG, "- Found unexpected color: " + Integer.toHexString(pixel));
                        }
                    }
                    offset += pixelStride;
                }
                offset += rowPadding;
            }

            // Return a color if it represents more than one quarter of the pixels.
            // We use this threshold in case the display is being letterboxed when
            // mirroring so there might be large black bars on the sides, which is normal.
            Log.d(TAG, "- Pixels: " + blackPixels + " black, "
                    + bluePixels + " blue, "
                    + greenPixels + " green, "
                    + otherPixels + " other");
            final int threshold = width * height / 4;
            if (bluePixels > threshold) {
                Log.d(TAG, "- Reporting blue.");
                return BLUEISH;
            }
            if (greenPixels > threshold) {
                Log.d(TAG, "- Reporting green.");
                return GREENISH;
            }
            if (blackPixels > threshold) {
                Log.d(TAG, "- Reporting black.");
                return Color.BLACK;
            }
            Log.d(TAG, "- Reporting other.");
            return -1;
        }
    }
}

