/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.appsecurity.cts;

import com.android.tradefed.util.RunUtil;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.platform.test.annotations.Presubmit;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for visibility of packages installed in one user, in a different user.
 */
@Presubmit
@RunWith(DeviceJUnit4ClassRunner.class)
public class PackageVisibilityTest extends BaseAppSecurityTest {

    private static final String TINY_APK = "CtsPkgInstallTinyApp.apk";
    private static final String TINY_PKG = "android.appsecurity.cts.tinyapp";

    private static final String TEST_APK = "CtsPkgAccessApp.apk";
    private static final String TEST_PKG = "com.android.cts.packageaccessapp";

    private static final boolean MATCH_UNINSTALLED = true;
    private static final boolean MATCH_NORMAL = false;

    private int[] mUsers;
    private String mOldVerifierValue;

    @Before
    public void setUpPackage() throws Exception {

        mUsers = Utils.prepareMultipleUsers(getDevice());
        mOldVerifierValue =
                getDevice().executeShellCommand("settings get global package_verifier_enable");
        getDevice().executeShellCommand("settings put global package_verifier_enable 0");
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().uninstallPackage(TINY_PKG);
        installTestAppForUser(TEST_APK, mPrimaryUserId);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().uninstallPackage(TINY_PKG);
        getDevice().executeShellCommand("settings put global package_verifier_enable "
                + mOldVerifierValue);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testUninstalledPackageVisibility_full() throws Exception {
        testUninstalledPackageVisibility(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testUninstalledPackageVisibility_instant() throws Exception {
        testUninstalledPackageVisibility(true);
    }
    private void testUninstalledPackageVisibility(boolean instant) throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }

        int userId = mUsers[1];
        assertTrue(userId > 0);
        getDevice().startUser(userId);
        installTestAppForUser(TEST_APK, userId);
        installTestAppForUser(TEST_APK, mPrimaryUserId);

        installTestAppForUser(TINY_APK, mPrimaryUserId);

        // It is visible for the installed user, using shell commands
        assertTrue(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_NORMAL));
        assertTrue(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_UNINSTALLED));

        // Try the same from an app
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_inUser", mPrimaryUserId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_inUserUninstalled", mPrimaryUserId);

        // It is not visible for the other user using shell commands
        assertFalse(isAppVisibleForUser(TINY_PKG, userId, MATCH_NORMAL));
        assertFalse(isAppVisibleForUser(TINY_PKG, userId, MATCH_UNINSTALLED));

        // Try the same from an app
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUser", userId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUserUninstalled", userId);

        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_getPackagesCantSeeTiny", userId);

        getDevice().uninstallPackage(TINY_PKG);

        // Install for the new user
        installTestAppForUser(TINY_APK, userId);

        // It is visible for the installed user
        assertTrue(isAppVisibleForUser(TINY_PKG, userId, MATCH_NORMAL));
        assertTrue(isAppVisibleForUser(TINY_PKG, userId, MATCH_UNINSTALLED));

        // It is not visible for the other user
        assertFalse(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_NORMAL));
        assertFalse(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_UNINSTALLED));

        // Uninstall with keep data and reboot
        uninstallWithKeepDataForUser(TINY_PKG, userId);
        getDevice().rebootUntilOnline();
        waitForBootCompleted();
        getDevice().startUser(userId);

        // It is visible for the installed user, but only if match uninstalled
        assertFalse(isAppVisibleForUser(TINY_PKG, userId, MATCH_NORMAL));
        assertTrue(isAppVisibleForUser(TINY_PKG, userId, MATCH_UNINSTALLED));

        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUser", userId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_getPackagesCanSeeTiny", userId);

        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUserUninstalled",
                mPrimaryUserId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_getPackagesCantSeeTiny", mPrimaryUserId);

        getDevice().uninstallPackage(TINY_PKG);
        getDevice().uninstallPackage(TEST_PKG);
    }

    private void uninstallWithKeepDataForUser(String packageName, int userId) throws Exception {
        final String command = "pm uninstall -k --user " + userId + " " + packageName;
        getDevice().executeShellCommand(command);
    }

    private void waitForBootCompleted() throws Exception {
        for (int i = 0; i < 45; i++) {
            if (isBootCompleted()) {
                return;
            }
            RunUtil.getDefault().sleep(1000);
        }
        throw new AssertionError("System failed to become ready!");
    }

    private boolean isBootCompleted() throws Exception {
        return "1".equals(getDevice().executeShellCommand("getprop sys.boot_completed").trim());
    }
}
