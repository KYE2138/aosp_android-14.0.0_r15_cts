/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.graphics.framerateoverride;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.support.test.uiautomator.UiDevice;
import android.sysprop.SurfaceFlingerProperties;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.graphics.framerateoverride.FrameRateOverrideTestActivity.FrameRateObserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for frame rate override and the behaviour of {@link Display#getRefreshRate()} and
 * {@link Display.Mode#getRefreshRate()} Api.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameRateOverrideTest {
    private static final String TAG = "FrameRateOverrideTest";
    // See b/170503758 for more details
    private static final long DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID = 170503758;

    // The tolerance within which we consider refresh rates are equal
    private static final float REFRESH_RATE_TOLERANCE = 0.01f;

    private int mInitialMatchContentFrameRate;
    private DisplayManager mDisplayManager;
    private UiDevice mUiDevice;
    private final Handler mHandler = new Handler(Looper.getMainLooper());


    @Rule
    public ActivityTestRule<FrameRateOverrideTestActivity> mActivityRule =
            new ActivityTestRule<>(FrameRateOverrideTestActivity.class);

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(
                        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();
        mUiDevice.executeShellCommand("wm dismiss-keyguard");

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOG_COMPAT_CHANGE,
                        Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
                        Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
                        Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                        Manifest.permission.MANAGE_GAME_MODE);

        mDisplayManager = mActivityRule.getActivity().getSystemService(DisplayManager.class);
        mInitialMatchContentFrameRate = toSwitchingType(
                mDisplayManager.getMatchContentFrameRateUserPreference());
        mDisplayManager.setRefreshRateSwitchingType(
                DisplayManager.SWITCHING_TYPE_RENDER_FRAME_RATE_ONLY);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
        boolean changeIsEnabled =
                CompatChanges.isChangeEnabled(DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID);
        Log.i(TAG, "DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID is "
                + (changeIsEnabled ? "enabled" : "disabled"));
    }

    @After
    public void tearDown() {
        mDisplayManager.setRefreshRateSwitchingType(mInitialMatchContentFrameRate);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }

    private void setMode(Display.Mode mode) {
        Log.i(TAG, "Setting display refresh rate to " + mode.getRefreshRate());
        mHandler.post(() -> {
            Window window = mActivityRule.getActivity().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredDisplayModeId = mode.getModeId();
            params.preferredRefreshRate = 0;
            params.preferredMinDisplayRefreshRate = 0;
            params.preferredMaxDisplayRefreshRate = 0;
            window.setAttributes(params);
        });
    }

    // The TV emulator is not expected to be a performant device,
    // so backpressure tests will always fail.
    private boolean isTvEmulator() {
        return SystemProperties.get("ro.build.characteristics").equals("emulator")
            && SystemProperties.get("ro.product.system.name").equals("atv_generic");
    }

    // Find refresh rates with the same resolution.
    private List<Display.Mode> getModesToTest() {
        List<Display.Mode> modesWithSameResolution = new ArrayList<>();
        if (!SurfaceFlingerProperties.enable_frame_rate_override().orElse(true)) {
            Log.i(TAG, "Frame rate override is not enabled, skipping");
            return modesWithSameResolution;
        }

        Display.Mode[] modes = mActivityRule.getActivity().getDisplay().getSupportedModes();
        Display.Mode currentMode = mActivityRule.getActivity().getDisplay().getMode();
        final long currentDisplayHeight = currentMode.getPhysicalHeight();
        final long currentDisplayWidth = currentMode.getPhysicalWidth();

        for (Display.Mode mode : modes) {
            if (mode.getPhysicalHeight() == currentDisplayHeight
                    && mode.getPhysicalWidth() == currentDisplayWidth) {
                modesWithSameResolution.add(mode);
            }
        }

        return modesWithSameResolution;
    }

    private void testGameModeFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        for (Display.Mode mode : getModesToTest()) {
            setMode(mode);
            activity.testFrameRateOverride(
                    activity.new GameModeTest(mUiDevice),
                    frameRateObserver, mode.getRefreshRate());
            Log.i(TAG, "\n");
        }
        Log.i(TAG, "\n");
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeBackpressureDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeBackpressure() throws InterruptedException, IOException {
        if (isTvEmulator()) {
            Log.i(TAG, "**** Skipping Backpressure ****");
            return;
        }

        Log.i(TAG, "**** Starting Game Mode Backpressure Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeChoreographerDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeChoreographer() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Choreographer Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeDisplayGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeDisplayGetRefreshRate() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ false));
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ true));
    }
}
