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
 * limitations under the License
 */

package android.voicerecognition.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.server.wm.WindowManagerStateHelper;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SettingsStateChangerRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class RecognitionServiceMicIndicatorTest {

    private static final String TAG = "RecognitionServiceMicIndicatorTest";
    // same as Settings.Secure.VOICE_RECOGNITION_SERVICE
    private static final String VOICE_RECOGNITION_SERVICE = "voice_recognition_service";
    private static final String INDICATORS_FLAG = "camera_mic_icons_enabled";
    // Same as PrivacyItemController DEFAULT_MIC_CAMERA
    private static final boolean DEFAULT_MIC_CAMERA = true;
    // Th notification privacy indicator
    private static final String PRIVACY_CHIP_PACKAGE_NAME = "com.android.systemui";
    private static final String PRIVACY_CHIP_ID = "privacy_chip";
    private static final String CAR_MIC_PRIVACY_CHIP_ID = "mic_privacy_chip";
    private static final String PRIVACY_DIALOG_PACKAGE_NAME = "com.android.systemui";
    private static final String PRIVACY_DIALOG_CONTENT_ID = "text";
    private static final String CAR_PRIVACY_DIALOG_CONTENT_ID = "qc_title";
    private static final String CAR_PRIVACY_DIALOG_APP_LABEL_CONTENT_ID = "qc_title";
    private static final String TV_MIC_INDICATOR_WINDOW_TITLE = "MicrophoneCaptureIndicator";
    private static final String SC_PRIVACY_DIALOG_PACKAGE_NAME = "com.android.permissioncontroller";
    private static final String SC_PRIVACY_DIALOG_CONTENT_ID = "indicator_label";
    // The cts app label
    private static final String APP_LABEL = "CtsVoiceRecognitionTestCases";
    // A simple test voice recognition service implementation
    private static final String CTS_VOICE_RECOGNITION_SERVICE =
            "android.recognitionservice.service/android.recognitionservice.service"
                    + ".CtsVoiceRecognitionService";
    private static final long LONG_TIMEOUT_FOR_CAR = 30000L;
    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String mOriginalVoiceRecognizer = Settings.Secure.getString(
            mContext.getContentResolver(), VOICE_RECOGNITION_SERVICE);
    private UiDevice mUiDevice;
    private SpeechRecognitionActivity mActivity;
    private String mCameraLabel;
    private String mOriginalIndicatorsState;
    private boolean mSafetyCenterEnabled;

    @Rule
    public ActivityTestRule<SpeechRecognitionActivity> mActivityTestRule =
            new ActivityTestRule<>(SpeechRecognitionActivity.class);

    @Rule
    public final SettingsStateChangerRule mVoiceRecognitionServiceSetterRule =
            new SettingsStateChangerRule(mContext, VOICE_RECOGNITION_SERVICE,
                    mOriginalVoiceRecognizer);

    @Before
    public void setup() {
        prepareDevice();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivity = mActivityTestRule.getActivity();
        mActivity.initDefault(false, null);

        final PackageManager pm = mContext.getPackageManager();
        try {
            mCameraLabel = pm.getPermissionGroupInfo(Manifest.permission_group.CAMERA, 0).loadLabel(
                    pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
        }
        runWithShellPermissionIdentity(() -> {
            mOriginalIndicatorsState =
                    DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG);
            Log.v(TAG, "setup(): mOriginalIndicatorsState=" + mOriginalIndicatorsState);
        });

        // TODO(http://b/259941077): Remove once privacy indicators are implemented.
        assumeFalse("Privacy indicators not supported", isWatch());

        try {
            mSafetyCenterEnabled = callWithShellPermissionIdentity(
                () -> mContext.getSystemService(SafetyCenterManager.class).isSafetyCenterEnabled());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        setIndicatorsEnabledState(Boolean.toString(true));
        // Wait for any privacy indicator to disappear to avoid the test becoming flaky.
        waitForNoIndicator(chipId());
    }

    @After
    public void teardown() {
        // press back to close the dialog
        mUiDevice.pressHome();
        // Restore original value.
        setIndicatorsEnabledState(mOriginalIndicatorsState);
        waitForNoIndicator(chipId());
    }

    private void prepareDevice() {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }

    private void setCurrentRecognizer(String recognizer) {
        runWithShellPermissionIdentity(
                () -> Settings.Secure.putString(mContext.getContentResolver(),
                        VOICE_RECOGNITION_SERVICE, recognizer));
        mUiDevice.waitForIdle();
    }

    private void setIndicatorsEnabledState(String enabled) {
        runWithShellPermissionIdentity(
                () -> DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG,
                        enabled, false));
        mUiDevice.waitForIdle();
    }

    private boolean hasPreInstalledRecognizer(String packageName) {
        Log.v(TAG, "hasPreInstalledRecognizer package=" + packageName);
        try {
            final PackageManager pm = mContext.getPackageManager();
            final ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static String getComponentPackageNameFromString(String from) {
        ComponentName componentName = from != null ? ComponentName.unflattenFromString(from) : null;
        return componentName != null ? componentName.getPackageName() : "";
    }

    @Test
    public void testNonTrustedRecognitionServiceCanBlameCallingApp() throws Throwable {
        // We treat trusted if the current voice recognizer is also a preinstalled app. This is a
        // untrusted case.
        setCurrentRecognizer(CTS_VOICE_RECOGNITION_SERVICE);

        // verify that the untrusted app cannot blame the calling app mic access
        testVoiceRecognitionServiceBlameCallingApp(/* trustVoiceService */ false);
    }

    @Test
    public void testTrustedRecognitionServiceCanBlameCallingApp() throws Throwable {
        // We treat trusted if the current voice recognizer is also a preinstalled app. This is a
        // trusted case.
        boolean hasPreInstalledRecognizer = hasPreInstalledRecognizer(
                getComponentPackageNameFromString(mOriginalVoiceRecognizer));
        assumeTrue("No preinstalled recognizer.", hasPreInstalledRecognizer);
        // TODO(b/279146568): remove the next line after test is fixed for auto
        assumeFalse(isCar());

        // verify that the trusted app can blame the calling app mic access
        testVoiceRecognitionServiceBlameCallingApp(/* trustVoiceService */ true);
    }

    private void testVoiceRecognitionServiceBlameCallingApp(boolean trustVoiceService)
            throws Throwable {
        // Start SpeechRecognition
        mActivity.startListeningDefault();

        if (isTv()) {
            assertTvIndicatorsShown(trustVoiceService);
        } else {
            assertPrivacyChipAndIndicatorsPresent(trustVoiceService);
        }
    }

    private void assertTvIndicatorsShown(boolean trustVoiceService) {
        Log.v(TAG, "assertTvIndicatorsShown");
        final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
        wmState.waitFor(
                state -> {
                    if (trustVoiceService) {
                        return state.containsWindow(TV_MIC_INDICATOR_WINDOW_TITLE)
                                && state.isWindowVisible(TV_MIC_INDICATOR_WINDOW_TITLE);
                    } else {
                        return !state.containsWindow(TV_MIC_INDICATOR_WINDOW_TITLE);
                    }
                },
                "Waiting for the mic indicator window to come up");
    }

    private void assertPrivacyChipAndIndicatorsPresent(boolean trustVoiceService) throws Exception {
        // Open notification and verify the privacy indicator is shown
        mUiDevice.openQuickSettings();

        String chipId = chipId();
        final UiObject2 privacyChip =
                SystemUtil.getEventually(() -> {
                    final UiObject2 foundChip =
                            mUiDevice.findObject(By.res(PRIVACY_CHIP_PACKAGE_NAME, chipId));
                    assertWithMessage("Can not find mic indicator").that(foundChip).isNotNull();
                    return foundChip;
                });

        // Make sure dialog is shown
        String dialogPackageName =
                mSafetyCenterEnabled ? SC_PRIVACY_DIALOG_PACKAGE_NAME : PRIVACY_DIALOG_PACKAGE_NAME;
        String contentId;
        if (isCar()) {
            contentId = CAR_PRIVACY_DIALOG_CONTENT_ID;
        } else if (mSafetyCenterEnabled) {
            contentId = SC_PRIVACY_DIALOG_CONTENT_ID;
        } else {
            contentId = PRIVACY_DIALOG_CONTENT_ID;
        }

        // Click the privacy indicator and verify the calling app name display status in the dialog.
        privacyChip.click();
        List<UiObject2> recognitionCallingAppLabels =
                SystemUtil.getEventually(() -> {
                    List<UiObject2> labels = mUiDevice.findObjects(
                            By.res(dialogPackageName, contentId));
                    assertWithMessage("No permission dialog shown after clicking privacy chip.")
                            .that(labels).isNotEmpty();
                    return labels;
                });

        // get dialog content
        String dialogDescription;
        if (isCar()) {
            dialogDescription =
                    recognitionCallingAppLabels.get(0)
                            .findObjects(By.res(dialogPackageName,
                                    CAR_PRIVACY_DIALOG_APP_LABEL_CONTENT_ID))
                            .stream()
                            .map(UiObject2::getText)
                            .collect(Collectors.joining("\n"));
        } else {
            dialogDescription =
                    recognitionCallingAppLabels
                            .stream()
                            .map(UiObject2::getText)
                            .collect(Collectors.joining("\n"));
        }
        Log.i(TAG, "Retrieved dialog description " + dialogDescription);

        if (trustVoiceService) {
            // Check trust recognizer can blame calling apmic permission
            assertWithMessage(
                    "Trusted voice recognition service can blame the calling app name " + APP_LABEL
                            + ", but does not find it.")
                    .that(dialogDescription)
                    .contains(APP_LABEL);

            // Check trust recognizer cannot blame non-mic permission
            assertWithMessage("Trusted voice recognition service cannot blame non-mic permission")
                    .that(dialogDescription)
                    .doesNotContain(mCameraLabel);
        } else {
            assertWithMessage(
                    "Untrusted voice recognition service cannot blame the calling app name "
                            + APP_LABEL)
                    .that(dialogDescription)
                    .doesNotContain(APP_LABEL);
        }

        if (isCar()) {
            // In cars the privacy chip will continue showing while the recognizer still has a
            // session in progress
            mActivity.destroyRecognizerDefault();
            privacyChip.click();
            waitForNoIndicatorForCar(chipId);
        } else {
            // Wait for the privacy indicator to disappear to avoid the test becoming flaky.
            waitForNoIndicator(chipId);
       }
    }

    @NonNull
    private String chipId() {
        return isCar() ? CAR_MIC_PRIVACY_CHIP_ID : PRIVACY_CHIP_ID;
    }

    private void waitForNoIndicator(String chipId) {
        SystemUtil.eventually(() -> {
            final UiObject2 foundChip =
                  mUiDevice.findObject(By.res(PRIVACY_CHIP_PACKAGE_NAME, chipId));
            assertWithMessage("Chip still visible.").that(foundChip).isNull();
        });
    }

    private void waitForNoIndicatorForCar(String chipId) {
        SystemUtil.eventually(() -> {
            final UiObject2 foundChip =
                    mUiDevice.findObject(By.res(PRIVACY_CHIP_PACKAGE_NAME, chipId));
            assertWithMessage("Chip still visible.").that(foundChip).isNull();
        }, LONG_TIMEOUT_FOR_CAR);
    }
    

    private boolean isTv() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private boolean isCar() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private boolean isWatch() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
