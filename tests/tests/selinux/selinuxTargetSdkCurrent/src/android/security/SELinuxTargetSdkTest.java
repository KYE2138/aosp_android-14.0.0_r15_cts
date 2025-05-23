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
 * limitations under the License.
 */

package android.security;

import android.os.Build;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import java.io.IOException;

/**
 * Verify the selinux domain for apps running with current targetSdkVersion
 */
public class SELinuxTargetSdkTest extends SELinuxTargetSdkTestBase {
    /**
     * Verify that net.dns properties may not be read
     */
    public void testNoDns() throws IOException {
        noDns();
    }

    public void testNoNetlinkRouteGetlink() throws IOException {
        noNetlinkRouteGetlink();
    }

    public void testNoNetlinkRouteBind() throws IOException {
        noNetlinkRouteBind();
    }

    public void testNoNetlinkRouteGetneigh() throws IOException {
        checkNetlinkRouteGetneigh(false);
    }

    public void testNoHardwareAddress() throws Exception {
        checkNetworkInterfaceHardwareAddress_returnsNull();
    }

    public void testCanNotExecuteFromHomeDir() throws Exception {
        assertFalse(canExecuteFromHomeDir());
    }

    /**
     * Verify that selinux context is the expected domain based on
     * targetSdkVersion = current
     */
    public void testAppDomainContext() throws IOException {
        String context = "u:r:untrusted_app:s0:c[0-9]+,c[0-9]+,c[0-9]+,c[0-9]+";
        String msg = "Untrusted apps with targetSdkVersion 32 and above " +
            "must run in the untrusted_app selinux domain and use the levelFrom=all " +
            "selector in SELinux seapp_contexts which adds four category types " +
            "to the app's selinux context. This test is targeting API level " +
            getContext().getApplicationInfo().targetSdkVersion + ".\n" +
            "Example expected value: u:r:untrusted_app:s0:c89,c256,c512,c768\n" +
            "Actual value: ";
        appDomainContext(context, msg);
    }

    /**
     * Verify that selinux context is the expected type based on
     * targetSdkVersion = current
     */
    public void testAppDataContext() throws Exception {
        String context = "u:object_r:app_data_file:s0:c[0-9]+,c[0-9]+,c[0-9]+,c[0-9]+";
        String msg = "Untrusted apps with targetSdkVersion 29 and above " +
            "must use the app_data_file selinux context and use the levelFrom=all " +
            "selector in SELinux seapp_contexts which adds four category types " +
            "to the app_data_file context.\n" +
            "Example expected value: u:object_r:app_data_file:s0:c89,c256,c512,c768\n" +
            "Actual value: ";
        appDataContext(context, msg);
    }

    public void testDex2oat() throws Exception {
        /*
         * Apps with a vendor image older than Q may access the dex2oat executable through
         * selinux policy on the vendor partition because the permission was granted in public
         * policy for appdomain.
         */
        if (PropertyUtil.isVendorApiLevelNewerThan(28)) {
            checkDex2oatAccess(false);
        }
    }

    /**
     * Verify that hidden ro props are not accessible.
     */
    @CddTest(requirements = { "9.7/C-1-4" })
    public void testNoHiddenSystemProperties() throws Exception {
        if (PropertyUtil.isVendorApiLevelAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            noHiddenSystemProperties();
        }
    }
}
