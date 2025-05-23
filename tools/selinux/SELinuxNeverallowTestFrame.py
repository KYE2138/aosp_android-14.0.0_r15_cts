#!/usr/bin/env python

src_header = """/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.security.cts;

import android.platform.test.annotations.RestrictedBuildTest;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Neverallow Rules SELinux tests.
 */
public class SELinuxNeverallowRulesTest extends DeviceTestCase implements IBuildReceiver, IDeviceTest {
    private File sepolicyAnalyze;
    private File devicePolicyFile;
    private File deviceSystemPolicyFile;

    private IBuildInfo mBuild;
    private int mVendorSepolicyVersion = -1;
    private int mSystemSepolicyVersion = -1;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mBuild = build;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        super.setDevice(device);
        mDevice = device;
    }
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        sepolicyAnalyze = android.security.cts.SELinuxHostTest.copyResourceToTempFile("/sepolicy-analyze");
        sepolicyAnalyze.setExecutable(true);

        devicePolicyFile = android.security.cts.SELinuxHostTest.getDevicePolicyFile(mDevice);

        if (isSepolicySplit()) {
            deviceSystemPolicyFile =
                    android.security.cts.SELinuxHostTest.getDeviceSystemPolicyFile(mDevice);

            // Caching this variable to save time.
            if (mVendorSepolicyVersion == -1) {
                mVendorSepolicyVersion =
                        android.security.cts.SELinuxHostTest.getVendorSepolicyVersion(mBuild, mDevice);
            }
            if (mSystemSepolicyVersion == -1) {
                mSystemSepolicyVersion =
                        android.security.cts.SELinuxHostTest.getSystemSepolicyVersion(mBuild);
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        sepolicyAnalyze.delete();
    }

    private boolean isFullTrebleDevice() throws Exception {
        return android.security.cts.SELinuxHostTest.isFullTrebleDevice(mDevice);
    }

    private boolean isDeviceLaunchingWithR() throws Exception {
        return PropertyUtil.getFirstApiLevel(mDevice) > 29;
    }

    private boolean isDeviceLaunchingWithS() throws Exception {
        return PropertyUtil.getFirstApiLevel(mDevice) > 30;
    }

    private boolean isCompatiblePropertyEnforcedDevice() throws Exception {
        return android.security.cts.SELinuxHostTest.isCompatiblePropertyEnforcedDevice(mDevice);
    }

    private boolean isSepolicySplit() throws Exception {
        return android.security.cts.SELinuxHostTest.isSepolicySplit(mDevice);
    }
"""
src_body = ""
src_footer = """}
"""

src_method = """
    @RestrictedBuildTest
    public void testNeverallowRules() throws Exception {
        String neverallowRule = "$NEVERALLOW_RULE_HERE$";
        boolean fullTrebleOnly = $TREBLE_ONLY_BOOL_HERE$;
        boolean launchingWithROnly = $LAUNCHING_WITH_R_ONLY_BOOL_HERE$;
        boolean launchingWithSOnly = $LAUNCHING_WITH_S_ONLY_BOOL_HERE$;
        boolean compatiblePropertyOnly = $COMPATIBLE_PROPERTY_ONLY_BOOL_HERE$;

        if ((fullTrebleOnly) && (!isFullTrebleDevice())) {
            // This test applies only to Treble devices but this device isn't one
            return;
        }
        if ((launchingWithROnly) && (!isDeviceLaunchingWithR())) {
            // This test applies only to devices launching with R or later but this device isn't one
            return;
        }
        if ((launchingWithSOnly) && (!isDeviceLaunchingWithS())) {
            // This test applies only to devices launching with S or later but this device isn't one
            return;
        }
        if ((compatiblePropertyOnly) && (!isCompatiblePropertyEnforcedDevice())) {
            // This test applies only to devices on which compatible property is enforced but this
            // device isn't one
            return;
        }

        // If sepolicy is split and vendor sepolicy version is behind platform's,
        // only test against platform policy.
        File policyFile =
                (isSepolicySplit() && mVendorSepolicyVersion < mSystemSepolicyVersion) ?
                deviceSystemPolicyFile :
                devicePolicyFile;

        /* run sepolicy-analyze neverallow check on policy file using given neverallow rules */
        ProcessBuilder pb = new ProcessBuilder(sepolicyAnalyze.getAbsolutePath(),
                policyFile.getAbsolutePath(), "neverallow", "-w", "-n",
                neverallowRule);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader result = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder errorString = new StringBuilder();
        while ((line = result.readLine()) != null) {
            errorString.append(line);
            errorString.append("\\n");
        }
        p.waitFor();
        assertTrue("The following errors were encountered when validating the SELinux"
                   + "neverallow rule:\\n" + neverallowRule + "\\n" + errorString,
                   errorString.length() == 0);
    }
"""
