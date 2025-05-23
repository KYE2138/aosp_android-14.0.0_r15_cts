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
 * limitations under the License
 */

package android.server.wm;

import static android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;
import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.UiDeviceUtils.pressSleepButton;
import static android.server.wm.app.Components.VIRTUAL_DISPLAY_ACTIVITY;
import static android.server.wm.app.Components.VirtualDisplayActivity.COMMAND_CREATE_DISPLAY;
import static android.server.wm.app.Components.VirtualDisplayActivity.COMMAND_DESTROY_DISPLAY;
import static android.server.wm.app.Components.VirtualDisplayActivity.COMMAND_RESIZE_DISPLAY;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_CAN_SHOW_WITH_INSECURE_KEYGUARD;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_COMMAND;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_COUNT;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_DENSITY_DPI;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_PRESENTATION_DISPLAY;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_PUBLIC_DISPLAY;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_RESIZE_DISPLAY;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_SHOW_SYSTEM_DECORATIONS;
import static android.server.wm.app.Components.VirtualDisplayActivity.KEY_SUPPORTS_TOUCH;
import static android.server.wm.app.Components.VirtualDisplayActivity.VIRTUAL_DISPLAY_PREFIX;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.cts.mockime.ImeEventStreamTestUtils.clearAllEvents;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.provider.Settings;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.ActivitySessionClient;
import android.server.wm.WindowManagerState.DisplayContent;
import android.server.wm.settings.SettingsSession;
import android.util.Pair;
import android.util.Size;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeEventStreamTestUtils;

import org.junit.Before;
import org.junit.ClassRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for ActivityManager display tests.
 *
 * @see DisplayTests
 * @see MultiDisplayKeyguardTests
 * @see MultiDisplayLockedKeyguardTests
 * @see AppConfigurationTests
 */
public class MultiDisplayTestBase extends ActivityManagerTestBase {

    static final int CUSTOM_DENSITY_DPI = 222;
    private static final int INVALID_DENSITY_DPI = -1;
    protected Context mTargetContext;

    @ClassRule
    public static DisableImmersiveModeConfirmationRule mDisableImmersiveModeConfirmationRule =
            new DisableImmersiveModeConfirmationRule();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTargetContext = getInstrumentation().getTargetContext();
    }

    DisplayContent getDisplayState(int displayId) {
        return getDisplayState(getDisplaysStates(), displayId);
    }

    DisplayContent getDisplayState(List<DisplayContent> displays, int displayId) {
        for (DisplayContent display : displays) {
            if (display.mId == displayId) {
                return display;
            }
        }
        return null;
    }

    /** Return the display state with width, height, dpi. Always not default display. */
    DisplayContent getDisplayState(List<DisplayContent> displays, int width, int height,
            int dpi) {
        for (DisplayContent display : displays) {
            if (display.mId == DEFAULT_DISPLAY) {
                continue;
            }
            final Configuration config = display.mFullConfiguration;
            if (config.densityDpi == dpi && config.screenWidthDp == width
                    && config.screenHeightDp == height) {
                return display;
            }
        }
        return null;
    }

    List<DisplayContent> getDisplaysStates() {
        mWmState.computeState();
        return mWmState.getDisplays();
    }

    /** Find the display that was not originally reported in oldDisplays and added in newDisplays */
    List<DisplayContent> findNewDisplayStates(List<DisplayContent> oldDisplays,
            List<DisplayContent> newDisplays) {
        final ArrayList<DisplayContent> result = new ArrayList<>();

        for (DisplayContent newDisplay : newDisplays) {
            if (oldDisplays.stream().noneMatch(d -> d.mId == newDisplay.mId)) {
                result.add(newDisplay);
            }
        }

        return result;
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected DisplayMetricsSession createManagedDisplayMetricsSession(int displayId) {
        return mObjectTracker.manage(new DisplayMetricsSession(displayId));
    }

    public static class LetterboxAspectRatioSession extends IgnoreOrientationRequestSession {
        private static final String WM_SET_LETTERBOX_STYLE_ASPECT_RATIO =
                "wm set-letterbox-style --aspectRatio ";
        private static final String WM_RESET_LETTERBOX_STYLE_ASPECT_RATIO =
                "wm reset-letterbox-style aspectRatio";

        LetterboxAspectRatioSession(float aspectRatio) {
            super(true);
            executeShellCommand(WM_SET_LETTERBOX_STYLE_ASPECT_RATIO + aspectRatio);
        }

        @Override
        public void close() {
            super.close();
            executeShellCommand(WM_RESET_LETTERBOX_STYLE_ASPECT_RATIO);
        }
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected LetterboxAspectRatioSession createManagedLetterboxAspectRatioSession(
            float aspectRatio) {
        return mObjectTracker.manage(new LetterboxAspectRatioSession(aspectRatio));
    }

    void waitForDisplayGone(Predicate<DisplayContent> displayPredicate) {
        waitForOrFail("displays to be removed", () -> {
            mWmState.computeState();
            return mWmState.getDisplays().stream().noneMatch(displayPredicate);
        });
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected VirtualDisplaySession createManagedVirtualDisplaySession() {
        return mObjectTracker.manage(new VirtualDisplaySession());
    }

    /**
     * This class should only be used when you need to test virtual display created by a
     * non-privileged app.
     * Or when you need to test on simulated display.
     *
     * If you need to test virtual display created by a privileged app, please use
     * {@link ExternalDisplaySession} instead.
     */
    public class VirtualDisplaySession implements AutoCloseable {
        private int mDensityDpi = CUSTOM_DENSITY_DPI;
        private boolean mLaunchInSplitScreen = false;
        private boolean mCanShowWithInsecureKeyguard = false;
        private boolean mPublicDisplay = false;
        private boolean mResizeDisplay = true;
        private boolean mShowSystemDecorations = false;
        private boolean mOwnContentOnly = false;
        private int mDisplayImePolicy = DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
        private boolean mPresentationDisplay = false;
        private boolean mSimulateDisplay = false;
        private boolean mSupportsTouch = false;
        private boolean mMustBeCreated = true;
        private Size mSimulationDisplaySize = new Size(1024 /* width */, 768 /* height */);

        private boolean mVirtualDisplayCreated = false;
        private OverlayDisplayDevicesSession mOverlayDisplayDeviceSession;

        VirtualDisplaySession setDensityDpi(int densityDpi) {
            mDensityDpi = densityDpi;
            return this;
        }

        VirtualDisplaySession setLaunchInSplitScreen(boolean launchInSplitScreen) {
            mLaunchInSplitScreen = launchInSplitScreen;
            return this;
        }

        VirtualDisplaySession setCanShowWithInsecureKeyguard(boolean canShowWithInsecureKeyguard) {
            mCanShowWithInsecureKeyguard = canShowWithInsecureKeyguard;
            return this;
        }

        VirtualDisplaySession setPublicDisplay(boolean publicDisplay) {
            mPublicDisplay = publicDisplay;
            return this;
        }

        VirtualDisplaySession setResizeDisplay(boolean resizeDisplay) {
            mResizeDisplay = resizeDisplay;
            return this;
        }

        VirtualDisplaySession setShowSystemDecorations(boolean showSystemDecorations) {
            mShowSystemDecorations = showSystemDecorations;
            return this;
        }

        VirtualDisplaySession setOwnContentOnly(boolean ownContentOnly) {
            mOwnContentOnly = ownContentOnly;
            return this;
        }

        VirtualDisplaySession setSupportsTouch(boolean supportsTouch) {
            mSupportsTouch = supportsTouch;
            return this;
        }


        /**
         * Sets the policy for how the display should show the ime.
         *
         * Set to one of:
         *   <ul>
         *     <li>{@link WindowManager#DISPLAY_IME_POLICY_LOCAL}
         *     <li>{@link WindowManager#DISPLAY_IME_POLICY_FALLBACK_DISPLAY}
         *     <li>{@link WindowManager#DISPLAY_IME_POLICY_HIDE}
         *   </ul>
         */
        VirtualDisplaySession setDisplayImePolicy(int displayImePolicy) {
            mDisplayImePolicy = displayImePolicy;
            return this;
        }

        VirtualDisplaySession setPresentationDisplay(boolean presentationDisplay) {
            mPresentationDisplay = presentationDisplay;
            return this;
        }

        // TODO(b/154565343) move simulate display out of VirtualDisplaySession
        public VirtualDisplaySession setSimulateDisplay(boolean simulateDisplay) {
            mSimulateDisplay = simulateDisplay;
            return this;
        }

        VirtualDisplaySession setSimulationDisplaySize(int width, int height) {
            mSimulationDisplaySize = new Size(width, height);
            return this;
        }

        @Nullable
        public DisplayContent createDisplay(boolean mustBeCreated) {
            mMustBeCreated = mustBeCreated;
            final DisplayContent display = createDisplays(1).stream().findFirst().orElse(null);
            if (mustBeCreated && display == null) {
                throw new IllegalStateException("No display is created");
            }
            return display;
        }

        @NonNull
        public DisplayContent createDisplay() {
            return Objects.requireNonNull(createDisplay(true /* mustBeCreated */));
        }

        @NonNull
        List<DisplayContent> createDisplays(int count) {
            if (mSimulateDisplay) {
                return simulateDisplays(count);
            } else {
                return createVirtualDisplays(count);
            }
        }

        void resizeDisplay() {
            if (mSimulateDisplay) {
                throw new IllegalStateException(
                        "Please use ReportedDisplayMetrics#setDisplayMetrics to resize"
                                + " simulate display");
            }
            executeShellCommand(getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY)
                    + " -f 0x20000000" + " --es " + KEY_COMMAND + " " + COMMAND_RESIZE_DISPLAY);
        }

        @Override
        public void close()  {
            if (mOverlayDisplayDeviceSession != null) {
                mOverlayDisplayDeviceSession.close();
            }
            if (mVirtualDisplayCreated) {
                destroyVirtualDisplays();
                mVirtualDisplayCreated = false;
            }
        }

        /**
         * Simulate new display.
         * <pre>
         * <code>mDensityDpi</code> provide custom density for the display.
         * </pre>
         * @return {@link DisplayContent} of newly created display.
         */
        private List<DisplayContent> simulateDisplays(int count) {
            mOverlayDisplayDeviceSession = new OverlayDisplayDevicesSession(mContext);
            mOverlayDisplayDeviceSession.createDisplays(mSimulationDisplaySize, mDensityDpi,
                    mOwnContentOnly, mShowSystemDecorations, count);
            mOverlayDisplayDeviceSession.configureDisplays(mDisplayImePolicy /* imePolicy */);
            return mOverlayDisplayDeviceSession.getCreatedDisplays();
        }

        /**
         * Create new virtual display.
         * <pre>
         * <code>mDensityDpi</code> provide custom density for the display.
         * <code>mLaunchInSplitScreen</code> start
         *     {@link android.server.wm.app.VirtualDisplayActivity} to side from
         *     {@link android.server.wm.app.LaunchingActivity} on primary display.
         * <code>mCanShowWithInsecureKeyguard</code>  allow showing content when device is
         *     showing an insecure keyguard.
         * <code>mMustBeCreated</code> should assert if the display was or wasn't created.
         * <code>mPublicDisplay</code> make display public.
         * <code>mResizeDisplay</code> should resize display when surface size changes.
         * <code>LaunchActivity</code> should launch test activity immediately after display
         *     creation.
         * </pre>
         * @param displayCount number of displays to be created.
         * @return A list of {@link DisplayContent} that represent newly created displays.
         * @throws Exception
         */
        private List<DisplayContent> createVirtualDisplays(int displayCount) {
            // Start an activity that is able to create virtual displays.
            if (mLaunchInSplitScreen) {
                getLaunchActivityBuilder()
                        .setToSide(true)
                        .setTargetActivity(VIRTUAL_DISPLAY_ACTIVITY)
                        .execute();
                final int secondaryTaskId =
                        mWmState.getTaskByActivity(VIRTUAL_DISPLAY_ACTIVITY).mTaskId;
                mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);
            } else {
                launchActivity(VIRTUAL_DISPLAY_ACTIVITY);
            }
            mWmState.computeState(
                    new WaitForValidActivityState(VIRTUAL_DISPLAY_ACTIVITY));
            mWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mWmState.assertFocusedActivity("Focus must be on virtual display host activity",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final List<DisplayContent> originalDS = getDisplaysStates();

            // Create virtual display with custom density dpi.
            final StringBuilder createVirtualDisplayCommand = new StringBuilder(
                    getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY))
                    .append(" -f 0x20000000")
                    .append(" --es " + KEY_COMMAND + " " + COMMAND_CREATE_DISPLAY);
            if (mDensityDpi != INVALID_DENSITY_DPI) {
                createVirtualDisplayCommand
                        .append(" --ei " + KEY_DENSITY_DPI + " ")
                        .append(mDensityDpi);
            }
            createVirtualDisplayCommand.append(" --ei " + KEY_COUNT + " ").append(displayCount)
                    .append(" --ez " + KEY_CAN_SHOW_WITH_INSECURE_KEYGUARD + " ")
                    .append(mCanShowWithInsecureKeyguard)
                    .append(" --ez " + KEY_PUBLIC_DISPLAY + " ").append(mPublicDisplay)
                    .append(" --ez " + KEY_RESIZE_DISPLAY + " ").append(mResizeDisplay)
                    .append(" --ez " + KEY_SHOW_SYSTEM_DECORATIONS + " ")
                    .append(mShowSystemDecorations)
                    .append(" --ez " + KEY_PRESENTATION_DISPLAY + " ").append(mPresentationDisplay)
                    .append(" --ez " + KEY_SUPPORTS_TOUCH + " ").append(mSupportsTouch);
            executeShellCommand(createVirtualDisplayCommand.toString());
            mVirtualDisplayCreated = true;

            return assertAndGetNewDisplays(mMustBeCreated ? displayCount : -1, originalDS);
        }

        /**
         * Destroy existing virtual display.
         */
        void destroyVirtualDisplays() {
            final String destroyVirtualDisplayCommand = getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY)
                    + " -f 0x20000000"
                    + " --es " + KEY_COMMAND + " " + COMMAND_DESTROY_DISPLAY;
            executeShellCommand(destroyVirtualDisplayCommand);
            waitForDisplayGone(
                    d -> d.getName() != null && d.getName().contains(VIRTUAL_DISPLAY_PREFIX));
        }
    }

    // TODO(b/112837428): Merge into VirtualDisplaySession when all usages are migrated.
    protected class VirtualDisplayLauncher extends VirtualDisplaySession {
        private final ActivitySessionClient mActivitySessionClient = createActivitySessionClient();

        ActivitySession launchActivityOnDisplay(ComponentName activityName,
                DisplayContent display) {
            return launchActivityOnDisplay(activityName, display, null /* extrasConsumer */,
                    true /* withShellPermission */, true /* waitForLaunch */);
        }

        ActivitySession launchActivityOnDisplay(ComponentName activityName,
                DisplayContent display, Consumer<Bundle> extrasConsumer,
                boolean withShellPermission, boolean waitForLaunch) {
            return launchActivity(builder -> builder
                    // VirtualDisplayActivity is in different package. If the display is not public,
                    // it requires shell permission to launch activity ({@see com.android.server.wm.
                    // ActivityStackSupervisor#isCallerAllowedToLaunchOnDisplay}).
                    .setWithShellPermission(withShellPermission)
                    .setWaitForLaunched(waitForLaunch)
                    .setIntentExtra(extrasConsumer)
                    .setTargetActivity(activityName)
                    .setDisplayId(display.mId));
        }

        ActivitySession launchActivity(Consumer<LaunchActivityBuilder> setupBuilder) {
            final LaunchActivityBuilder builder = getLaunchActivityBuilder()
                    .setUseInstrumentation();
            setupBuilder.accept(builder);
            return mActivitySessionClient.startActivity(builder);
        }

        @Override
        public void close() {
            super.close();
            mActivitySessionClient.close();
        }
    }

    /** Helper class to save, set, and restore overlay_display_devices preference. */
    private class OverlayDisplayDevicesSession extends SettingsSession<String> {
        /** See display_manager_overlay_display_name. */
        private static final String OVERLAY_DISPLAY_NAME_PREFIX = "Overlay #";

        /** See {@link OverlayDisplayAdapter#OVERLAY_DISPLAY_FLAG_OWN_CONTENT_ONLY}. */
        private static final String OVERLAY_DISPLAY_FLAG_OWN_CONTENT_ONLY = ",own_content_only";

        /**
         * See {@link OverlayDisplayAdapter#OVERLAY_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS}.
         */
        private static final String OVERLAY_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS =
                ",should_show_system_decorations";

        /** The displays which are created by this session. */
        private final List<DisplayContent> mDisplays = new ArrayList<>();
        /** The configured displays that need to be restored when this session is closed. */
        private final List<OverlayDisplayState> mDisplayStates = new ArrayList<>();
        private final WindowManager mWm;

        OverlayDisplayDevicesSession(Context context) {
            super(Settings.Global.getUriFor(Settings.Global.OVERLAY_DISPLAY_DEVICES),
                    Settings.Global::getString,
                    Settings.Global::putString);
            // Remove existing overlay display to avoid display count problem.
            removeExisting();
            mWm = context.getSystemService(WindowManager.class);
        }

        List<DisplayContent> getCreatedDisplays() {
            return new ArrayList<>(mDisplays);
        }

        @Override
        public void set(String value) {
            final List<DisplayContent> originalDisplays = getDisplaysStates();
            super.set(value);
            final int newDisplayCount = 1 + (int) value.chars().filter(ch -> ch == ';').count();
            mDisplays.addAll(assertAndGetNewDisplays(newDisplayCount, originalDisplays));
        }

        /** Creates overlay display with custom density dpi, specified size, and test flags. */
        void createDisplays(Size displaySize, int densityDpi, boolean ownContentOnly,
                boolean shouldShowSystemDecorations, int count) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < count; i++) {
                String displaySettingsEntry = displaySize + "/" + densityDpi;
                if (ownContentOnly) {
                    displaySettingsEntry += OVERLAY_DISPLAY_FLAG_OWN_CONTENT_ONLY;
                }
                if (shouldShowSystemDecorations) {
                    displaySettingsEntry += OVERLAY_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
                }
                builder.append(displaySettingsEntry);
                // Creating n displays needs (n - 1) ';'.
                if (i < count - 1) {
                    builder.append(';');
                }
            }
            set(builder.toString());
        }

        void configureDisplays(int imePolicy) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                for (DisplayContent display : mDisplays) {
                    final int oldImePolicy = mWm.getDisplayImePolicy(display.mId);
                    mDisplayStates.add(new OverlayDisplayState(display.mId, oldImePolicy));
                    if (imePolicy != oldImePolicy) {
                        mWm.setDisplayImePolicy(display.mId, imePolicy);
                        waitForOrFail("display config show-IME to be set",
                                () -> (mWm.getDisplayImePolicy(display.mId) == imePolicy));
                    }
                }
            });
        }

        private void restoreDisplayStates() {
            mDisplayStates.forEach(state -> SystemUtil.runWithShellPermissionIdentity(() -> {
                mWm.setDisplayImePolicy(state.mId, state.mImePolicy);

                // Only need to wait the last flag to be set.
                waitForOrFail("display config show-IME to be restored",
                        () -> (mWm.getDisplayImePolicy(state.mId) == state.mImePolicy));
            }));
        }

        @Override
        public void close() {
            // Need to restore display state before display is destroyed.
            restoreDisplayStates();
            super.close();
            // Waiting for restoring to the state before this session was created.
            waitForDisplayGone(display -> mDisplays.stream()
                    .anyMatch(createdDisplay -> createdDisplay.mId == display.mId));
        }

        private void removeExisting() {
            if (!mHasInitialValue || mInitialValue == null) {
                // No existing overlay displays.
                return;
            }
            delete(mUri);
            // Make sure all overlay displays are completely removed.
            waitForDisplayGone(
                    display -> display.getName().startsWith(OVERLAY_DISPLAY_NAME_PREFIX));
        }

        private class OverlayDisplayState {
            int mId;
            int mImePolicy;

            OverlayDisplayState(int displayId, int imePolicy) {
                mId = displayId;
                mImePolicy = imePolicy;
            }
        }
    }

    /** Wait for provided number of displays and report their configurations. */
    List<DisplayContent> getDisplayStateAfterChange(int expectedDisplayCount) {
        return Condition.waitForResult("the correct number of displays=" + expectedDisplayCount,
                condition -> condition
                        .setReturnLastResult(true)
                        .setResultSupplier(this::getDisplaysStates)
                        .setResultValidator(
                                displays -> areDisplaysValid(displays, expectedDisplayCount)));
    }

    private boolean areDisplaysValid(List<DisplayContent> displays, int expectedDisplayCount) {
        if (displays.size() != expectedDisplayCount) {
            return false;
        }
        for (DisplayContent display : displays) {
            if (display.mOverrideConfiguration.densityDpi == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wait for desired number of displays to be created and get their properties.
     *
     * @param newDisplayCount expected display count, -1 if display should not be created.
     * @param originalDisplays display states before creation of new display(s).
     * @return list of new displays, empty list if no new display is created.
     */
    private List<DisplayContent> assertAndGetNewDisplays(int newDisplayCount,
            List<DisplayContent> originalDisplays) {
        final int originalDisplayCount = originalDisplays.size();

        // Wait for the display(s) to be created and get configurations.
        final List<DisplayContent> ds = getDisplayStateAfterChange(
                originalDisplayCount + newDisplayCount);
        if (newDisplayCount != -1) {
            assertEquals("New virtual display(s) must be created",
                    originalDisplayCount + newDisplayCount, ds.size());
        } else {
            assertEquals("New virtual display must not be created",
                    originalDisplayCount, ds.size());
            return Collections.emptyList();
        }

        // Find the newly added display(s).
        final List<DisplayContent> newDisplays = findNewDisplayStates(originalDisplays, ds);
        assertThat("New virtual display must be created", newDisplays, hasSize(newDisplayCount));

        return newDisplays;
    }

    /** A clearer alias of {@link Pair#create(Object, Object)}. */
    protected <K, V> Pair<K, V> pair(K k, V v) {
        return new Pair<>(k, v);
    }

    protected void assertBothDisplaysHaveResumedActivities(
            Pair<Integer, ComponentName> firstPair, Pair<Integer, ComponentName> secondPair) {
        assertNotEquals("Displays must be different.  First display id: "
                        + firstPair.first, firstPair.first, secondPair.first);
        mWmState.assertResumedActivities("Both displays must have resumed activities",
                mapping -> {
                    mapping.put(firstPair.first, firstPair.second);
                    mapping.put(secondPair.first, secondPair.second);
                });
    }

    /** Checks if the device supports multi-display. */
    protected boolean supportsMultiDisplay() {
        return hasDeviceFeature(FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }

    /** Checks if the device supports live wallpaper for multi-display. */
    protected boolean supportsLiveWallpaper() {
        return hasDeviceFeature(PackageManager.FEATURE_LIVE_WALLPAPER);
    }

    /** Checks if the device supports wallpaper. */
    protected boolean supportsWallpaper() {
        return WallpaperManager.getInstance(mContext).isWallpaperSupported();
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected ExternalDisplaySession createManagedExternalDisplaySession() {
        return mObjectTracker.manage(new ExternalDisplaySession());
    }

    @SafeVarargs
    final void waitOrderedImeEventsThenAssertImeShown(ImeEventStream stream, int displayId,
            Predicate<ImeEvent>... conditions) throws Exception {
        for (Predicate<ImeEvent> condition : conditions) {
            expectEvent(stream, condition, TimeUnit.SECONDS.toMillis(5) /* eventTimeout */);
        }
        // Assert the IME is shown on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(displayId);
    }

    protected void waitAndAssertImeNoScreenSizeChanged(ImeEventStream stream) {
        notExpectEvent(stream, event -> "onConfigurationChanged".equals(event.getEventName())
                && (event.getArguments().getInt("ConfigUpdates") & CONFIG_SCREEN_SIZE) != 0,
                TimeUnit.SECONDS.toMillis(1) /* eventTimeout */);
    }

    /**
     * Clears all {@link InputMethodService#onConfigurationChanged(Configuration)} events from the
     * given {@code stream} and returns a forked {@link ImeEventStream}.
     *
     * @see ImeEventStreamTestUtils#clearAllEvents(ImeEventStream, String)
     */
    protected ImeEventStream clearOnConfigurationChangedFromStream(ImeEventStream stream) {
        return clearAllEvents(stream, "onConfigurationChanged");
    }

    /**
     * This class is used when you need to test virtual display created by a privileged app.
     *
     * If you need to test virtual display created by a non-privileged app or when you need to test
     * on simulated display, please use {@link VirtualDisplaySession} instead.
     */
    public class ExternalDisplaySession implements AutoCloseable {

        private boolean mCanShowWithInsecureKeyguard = false;
        private boolean mPublicDisplay = false;
        private boolean mShowSystemDecorations = false;

        private int mDisplayId = INVALID_DISPLAY;

        @Nullable
        private VirtualDisplayHelper mExternalDisplayHelper;

        ExternalDisplaySession setCanShowWithInsecureKeyguard(boolean canShowWithInsecureKeyguard) {
            mCanShowWithInsecureKeyguard = canShowWithInsecureKeyguard;
            return this;
        }

        ExternalDisplaySession setPublicDisplay(boolean publicDisplay) {
            mPublicDisplay = publicDisplay;
            return this;
        }

        /**
         * @deprecated untrusted virtual display won't have system decorations even it has the flag.
         * Only use this method to verify that. To test secondary display with system decorations,
         * please use simulated display.
         */
        @Deprecated
        ExternalDisplaySession setShowSystemDecorations(boolean showSystemDecorations) {
            mShowSystemDecorations = showSystemDecorations;
            return this;
        }

        /**
         * Creates a private virtual display with insecure keyguard flags set.
         */
        DisplayContent createVirtualDisplay() {
            final List<DisplayContent> originalDS = getDisplaysStates();
            final int originalDisplayCount = originalDS.size();

            mExternalDisplayHelper = new VirtualDisplayHelper();
            mExternalDisplayHelper
                    .setPublicDisplay(mPublicDisplay)
                    .setCanShowWithInsecureKeyguard(mCanShowWithInsecureKeyguard)
                    .setShowSystemDecorations(mShowSystemDecorations)
                    .createAndWaitForDisplay();

            // Wait for the virtual display to be created and get configurations.
            final List<DisplayContent> ds = getDisplayStateAfterChange(originalDisplayCount + 1);
            assertEquals("New virtual display must be created", originalDisplayCount + 1,
                    ds.size());

            // Find the newly added display.
            final DisplayContent newDisplay = findNewDisplayStates(originalDS, ds).get(0);
            mDisplayId = newDisplay.mId;
            return newDisplay;
        }

        void turnDisplayOff() {
            if (mExternalDisplayHelper == null) {
                throw new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOff();
        }

        void turnDisplayOn() {
            if (mExternalDisplayHelper == null) {
                throw new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOn();
        }

        @Override
        public void close() {
            if (mExternalDisplayHelper != null) {
                mExternalDisplayHelper.releaseDisplay();
                mExternalDisplayHelper = null;

                waitForDisplayGone(d -> d.mId == mDisplayId);
                mDisplayId = INVALID_DISPLAY;
            }
        }
    }

    public class PrimaryDisplayStateSession implements AutoCloseable {

        void turnScreenOff() {
            setPrimaryDisplayState(false);
        }

        @Override
        public void close() {
            setPrimaryDisplayState(true);
        }

        /** Turns the primary display on/off by pressing the power key */
        private void setPrimaryDisplayState(boolean wantOn) {
            if (wantOn) {
                wakeUpAndUnlock(mContext);
            } else {
                pressSleepButton();
            }
            VirtualDisplayHelper.waitForDefaultDisplayState(wantOn);
        }
    }
}
