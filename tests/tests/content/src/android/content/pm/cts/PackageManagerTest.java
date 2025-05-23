/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.pm.cts;

import static android.Manifest.permission.DELETE_PACKAGES;
import static android.Manifest.permission.GET_INTENT_SENDER_INTENT;
import static android.Manifest.permission.INSTALL_TEST_ONLY_PACKAGE;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Intent.FLAG_EXCLUDE_STOPPED_PACKAGES;
import static android.content.pm.ApplicationInfo.FLAG_HAS_CODE;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.GET_ACTIVITIES;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_PROVIDERS;
import static android.content.pm.PackageManager.GET_RECEIVERS;
import static android.content.pm.PackageManager.GET_SERVICES;
import static android.content.pm.PackageManager.MATCH_APEX;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_FACTORY_ONLY;
import static android.content.pm.PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_INSTANT;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.parsePackageDump;
import static android.os.UserHandle.CURRENT;
import static android.os.UserHandle.USER_CURRENT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.cts.MockActivity;
import android.content.cts.MockContentProvider;
import android.content.cts.MockReceiver;
import android.content.cts.MockService;
import android.content.cts.R;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.IPackageManager;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ComponentEnabledSetting;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.Signature;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;
import com.android.internal.security.VerityUtils;

import com.google.common.truth.Expect;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This test is based on the declarations in AndroidManifest.xml. We create mock declarations
 * in AndroidManifest.xml just for test of PackageManager, and there are no corresponding parts
 * of these declarations in test project.
 */
@AppModeFull // TODO(Instant) Figure out which APIs should work.
@RunWith(AndroidJUnit4.class)
public class PackageManagerTest {
    private static final String TAG = "PackageManagerTest";

    private Context mContext;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;
    private static final String PACKAGE_NAME = "android.content.cts";
    private static final String STUB_PACKAGE_NAME = "com.android.cts.stub";
    private static final String APPLICATION_NAME = "android.content.cts.MockApplication";
    private static final String ACTIVITY_ACTION_NAME = "android.intent.action.PMTEST";
    private static final String MAIN_ACTION_NAME = "android.intent.action.MAIN";
    private static final String SERVICE_ACTION_NAME =
            "android.content.pm.cts.activity.PMTEST_SERVICE";
    private static final String RECEIVER_ACTION_NAME =
            "android.content.pm.cts.PackageManagerTest.PMTEST_RECEIVER";
    private static final String GRANTED_PERMISSION_NAME = "android.permission.INTERNET";
    private static final String NOT_GRANTED_PERMISSION_NAME = "android.permission.HARDWARE_TEST";
    private static final String ACTIVITY_NAME = "android.content.pm.cts.TestPmActivity";
    private static final String SERVICE_NAME = "android.content.pm.cts.TestPmService";
    private static final String RECEIVER_NAME = "android.content.pm.cts.PmTestReceiver";
    private static final String INSTRUMENT_NAME = "android.content.pm.cts.TestPmInstrumentation";
    private static final String CALL_ABROAD_PERMISSION_NAME =
            "android.content.cts.CALL_ABROAD_PERMISSION";
    private static final String PROVIDER_NAME = "android.content.cts.MockContentProvider";
    private static final String PERMISSIONGROUP_NAME = "android.permission-group.COST_MONEY";
    private static final String PERMISSION_TREE_ROOT =
            "android.content.cts.permission.TEST_DYNAMIC";
    // Number of activities/activity-alias in AndroidManifest
    private static final int NUM_OF_ACTIVITIES_IN_MANIFEST = 21;
    public static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private static final String SHIM_APEX_PACKAGE_NAME = "com.android.apex.cts.shim";

    private static final int[] PACKAGE_INFO_MATCH_FLAGS = {MATCH_UNINSTALLED_PACKAGES,
            MATCH_DISABLED_COMPONENTS, MATCH_SYSTEM_ONLY, MATCH_FACTORY_ONLY, MATCH_INSTANT,
            MATCH_APEX, MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS};

    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String EMPTY_APP_APK = SAMPLE_APK_BASE
            + "CtsContentEmptyTestApp.apk";
    private static final String LONG_PACKAGE_NAME_APK = SAMPLE_APK_BASE
            + "CtsContentLongPackageNameTestApp.apk";
    private static final String LONG_SHARED_USER_ID_APK = SAMPLE_APK_BASE
            + "CtsContentLongSharedUserIdTestApp.apk";
    private static final String MAX_PACKAGE_NAME_APK = SAMPLE_APK_BASE
            + "CtsContentMaxPackageNameTestApp.apk";
    private static final String MAX_SHARED_USER_ID_APK = SAMPLE_APK_BASE
            + "CtsContentMaxSharedUserIdTestApp.apk";
    private static final String LONG_LABEL_NAME_APK = SAMPLE_APK_BASE
            + "CtsContentLongLabelNameTestApp.apk";
    private static final String LONG_USES_PERMISSION_NAME_APK = SAMPLE_APK_BASE
            + "CtsContentLongUsesPermissionNameTestApp.apk";
    private static final String EMPTY_APP_PACKAGE_NAME = "android.content.cts.emptytestapp";
    private static final String EMPTY_APP_MAX_PACKAGE_NAME = "android.content.cts.emptytestapp27j"
            + "EBRNRG3ozwBsGr1sVIM9U0bVTI2TdyIyeRkZgW4JrJefwNIBAmCg4AzqXiCvG6JjqA0uTCWSFu2YqAVxVd"
            + "iRKAay19k5VFlSaM7QW9uhvlrLQqsTW01ofFzxNDbp2QfIFHZR6rebKzKBz6byQFM0DYQnYMwFWXjWkMPN"
            + "dqkRLykoFLyBup53G68k2n8w";
    private static final String EMPTY_APP_LONG_USES_PERMISSION_PACKAGE_NAME =
            EMPTY_APP_PACKAGE_NAME + ".longusespermission";
    private static final String SHELL_PACKAGE_NAME = "com.android.shell";
    private static final String HELLO_WORLD_PACKAGE_NAME = "com.example.helloworld";
    private static final String HELLO_WORLD_APK = SAMPLE_APK_BASE + "HelloWorld5.apk";
    private static final String HELLO_WORLD_LOTS_OF_FLAGS_APK =
            SAMPLE_APK_BASE + "HelloWorldLotsOfFlags.apk";
    private static final String MOCK_LAUNCHER_PACKAGE_NAME = "android.content.cts.mocklauncherapp";
    private static final String MOCK_LAUNCHER_APK = SAMPLE_APK_BASE
            + "CtsContentMockLauncherTestApp.apk";
    private static final String NON_EXISTENT_PACKAGE_NAME = "android.content.cts.nonexistent.pkg";
    private static final String STUB_PACKAGE_APK = SAMPLE_APK_BASE
            + "CtsSyncAccountAccessStubs.apk";
    private static final String STUB_PACKAGE_SPLIT =
            SAMPLE_APK_BASE + "CtsSyncAccountAccessStubs_mdpi-v4.apk";

    private static final int MAX_SAFE_LABEL_LENGTH = 1000;

    // For intent resolution tests
    private static final String NON_EXISTENT_ACTION_NAME = "android.intent.action.cts.NON_EXISTENT";
    private static final String INTENT_RESOLUTION_TEST_PKG_NAME =
            "android.content.cts.IntentResolutionTest";
    private static final String RESOLUTION_TEST_ACTION_NAME =
            "android.intent.action.RESOLUTION_TEST";
    private static final String SELECTOR_ACTION_NAME = "android.intent.action.SELECTORTEST";
    private static final String FILE_PROVIDER_AUTHORITY = "android.content.cts.fileprovider";

    private static final ComponentName ACTIVITY_COMPONENT = new ComponentName(
            PACKAGE_NAME, ACTIVITY_NAME);
    private static final ComponentName SERVICE_COMPONENT = new ComponentName(
            PACKAGE_NAME, SERVICE_NAME);
    private static final ComponentName STUB_ACTIVITY_COMPONENT = ComponentName.createRelative(
            STUB_PACKAGE_NAME, ".StubActivity");
    private static final ComponentName STUB_SERVICE_COMPONENT = ComponentName.createRelative(
            STUB_PACKAGE_NAME, ".StubService");
    private static final ComponentName RESET_ENABLED_SETTING_ACTIVITY_COMPONENT =
            ComponentName.createRelative(MOCK_LAUNCHER_PACKAGE_NAME, ".MockActivity");
    private static final ComponentName RESET_ENABLED_SETTING_RECEIVER_COMPONENT =
            ComponentName.createRelative(MOCK_LAUNCHER_PACKAGE_NAME, ".MockReceiver");
    private static final ComponentName RESET_ENABLED_SETTING_SERVICE_COMPONENT =
            ComponentName.createRelative(MOCK_LAUNCHER_PACKAGE_NAME, ".MockService");
    private static final ComponentName RESET_ENABLED_SETTING_PROVIDER_COMPONENT =
            ComponentName.createRelative(MOCK_LAUNCHER_PACKAGE_NAME, ".MockProvider");

    private final ServiceTestRule mServiceTestRule = new ServiceTestRule();

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    @Rule public final Expect expect = Expect.create();

    @Before
    public void setup() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mPackageManager = mContext.getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(EMPTY_APP_PACKAGE_NAME);
        uninstallPackage(EMPTY_APP_MAX_PACKAGE_NAME);
        uninstallPackage(HELLO_WORLD_PACKAGE_NAME);
        uninstallPackage(MOCK_LAUNCHER_PACKAGE_NAME);
        uninstallPackage(EMPTY_APP_LONG_USES_PERMISSION_PACKAGE_NAME);
    }

    @Test
    public void testQuery() throws NameNotFoundException {
        // Test query Intent Activity related methods

        Intent activityIntent = new Intent(ACTIVITY_ACTION_NAME);
        String cmpActivityName = "android.content.pm.cts.TestPmCompare";
        // List with different activities and the filter doesn't work,
        List<ResolveInfo> listWithDiff = mPackageManager.queryIntentActivityOptions(
                new ComponentName(PACKAGE_NAME, cmpActivityName), null, activityIntent,
                PackageManager.ResolveInfoFlags.of(0));
        checkActivityInfoName(ACTIVITY_NAME, listWithDiff);

        // List with the same activities to make filter work
        List<ResolveInfo> listInSame = mPackageManager.queryIntentActivityOptions(
                new ComponentName(PACKAGE_NAME, ACTIVITY_NAME), null, activityIntent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, listInSame.size());

        // Test queryIntentActivities
        List<ResolveInfo> intentActivities =
                mPackageManager.queryIntentActivities(activityIntent,
                        PackageManager.ResolveInfoFlags.of(0));
        assertTrue(intentActivities.size() > 0);
        checkActivityInfoName(ACTIVITY_NAME, intentActivities);

        // End of Test query Intent Activity related methods

        // Test queryInstrumentation
        String targetPackage = "android";
        List<InstrumentationInfo> instrumentations = mPackageManager.queryInstrumentation(
                targetPackage, 0);
        checkInstrumentationInfoName(INSTRUMENT_NAME, instrumentations);

        // Test queryIntentServices
        Intent serviceIntent = new Intent(SERVICE_ACTION_NAME);
        List<ResolveInfo> services = mPackageManager.queryIntentServices(serviceIntent,
                PackageManager.ResolveInfoFlags.of(0));
        checkServiceInfoName(SERVICE_NAME, services);

        // Test queryBroadcastReceivers
        Intent broadcastIntent = new Intent(RECEIVER_ACTION_NAME);
        List<ResolveInfo> broadcastReceivers =
                mPackageManager.queryBroadcastReceivers(broadcastIntent,
                        PackageManager.ResolveInfoFlags.of(0));
        checkActivityInfoName(RECEIVER_NAME, broadcastReceivers);

        // Test queryPermissionsByGroup, queryContentProviders
        String testPermissionsGroup = "android.permission-group.COST_MONEY";
        List<PermissionInfo> permissions = mPackageManager.queryPermissionsByGroup(
                testPermissionsGroup, PackageManager.GET_META_DATA);
        checkPermissionInfoName(CALL_ABROAD_PERMISSION_NAME, permissions);

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        List<ProviderInfo> providers = mPackageManager.queryContentProviders(PACKAGE_NAME,
                appInfo.uid, PackageManager.ComponentInfoFlags.of(0));
        checkProviderInfoName(PROVIDER_NAME, providers);
    }

    @Test
    public void testStoppedPackagesQuery() throws NameNotFoundException {
        installPackage(HELLO_WORLD_APK);

        final Intent intent = new Intent(ACTIVITY_ACTION_NAME);
        intent.addFlags(FLAG_EXCLUDE_STOPPED_PACKAGES);

        // Stopped after install.
        {
            final List<ResolveInfo> matches = mPackageManager.queryIntentActivities(intent,
                    PackageManager.ResolveInfoFlags.of(0));
            assertFalse(containsActivityInfoName("com.example.helloworld.MainActivity", matches));
        }

        SystemUtil.runShellCommand("am start -W "
                + "--user current "
                + "-a android.intent.action.MAIN "
                + "-c android.intent.category.LAUNCHER "
                + HELLO_WORLD_PACKAGE_NAME + "/.MainActivity");

        // Started.
        {
            final List<ResolveInfo> matches = mPackageManager.queryIntentActivities(intent,
                    PackageManager.ResolveInfoFlags.of(0));
            assertTrue(containsActivityInfoName("com.example.helloworld.MainActivity", matches));
        }

        assertEquals("", SystemUtil.runShellCommand("am force-stop " + HELLO_WORLD_PACKAGE_NAME));

        // Force stopped.
        {
            final List<ResolveInfo> matches = mPackageManager.queryIntentActivities(intent,
                    PackageManager.ResolveInfoFlags.of(0));
            assertFalse(containsActivityInfoName("com.example.helloworld.MainActivity", matches));
        }
    }

    // Disable the test due to feature revert
    private void testEnforceIntentToMatchIntentFilter() {
        Intent intent = new Intent();
        List<ResolveInfo> results;

        /* Implicit intent tests */

        intent.setPackage(INTENT_RESOLUTION_TEST_PKG_NAME);

        // Implicit intents with matching intent filter
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());

        // Implicit intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());

        /* Explicit intent tests */

        intent = new Intent();
        ComponentName comp;

        // Explicit intents with matching intent filter
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());

        // Explicit intents with non-matching intent filter on target T+
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());

        // More comprehensive intent matching tests on target T+
        intent = new Intent();
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME + "2");
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        intent.setType("*/*");
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        intent.setData(Uri.parse("http://example.com"));
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        intent.setDataAndType(Uri.parse("http://example.com"), "*/*");
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        File file = new File(mContext.getFilesDir(), "test.txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            fail(e.getMessage());
        }
        Uri uri = FileProvider.getUriForFile(mContext, FILE_PROVIDER_AUTHORITY, file);
        intent.setData(uri);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        file.delete();
        intent.addCategory(Intent.CATEGORY_APP_BROWSER);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());

        // Explicit intents with non-matching intent filter on target < T
        final String api30Pkg = INTENT_RESOLUTION_TEST_PKG_NAME + "Api30";
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(api30Pkg, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(api30Pkg, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(api30Pkg, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());

        // Explicit intents with non-matching intent filter on our own package
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(PACKAGE_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(PACKAGE_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(PACKAGE_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());

        /* Intent selector tests */

        Intent selector = new Intent();
        selector.setPackage(INTENT_RESOLUTION_TEST_PKG_NAME);
        intent = new Intent();
        intent.setSelector(selector);

        // Matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());

        // Matching intent and non-matching selector
        selector.setAction(NON_EXISTENT_ACTION_NAME);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());

        // Non-matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());

        /* Pending Intent tests */

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(GET_INTENT_SENDER_INTENT);
        var authority = INTENT_RESOLUTION_TEST_PKG_NAME + ".provider";
        Bundle b = mContext.getContentResolver().call(authority, "", null, null);
        assertNotNull(b);
        PendingIntent pi = b.getParcelable("pendingIntent", PendingIntent.class);
        assertNotNull(pi);
        intent = pi.getIntent();
        // It should be a non-matching intent, which cannot be resolved in our package
        results = mPackageManager.queryIntentActivities(intent,
            PackageManager.ResolveInfoFlags.of(0));
        assertEquals(0, results.size());
        // However, querying on behalf of the pending intent creator should work properly
        results = pi.queryIntentComponents(0);
        assertEquals(1, results.size());
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(
                new ComponentName("android", "com.android.internal.app.ResolverActivity"));
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException ignore) {

        }
    }

    @Test
    public void testRevertEnforceIntentToMatchIntentFilter() {
        Intent intent = new Intent();
        List<ResolveInfo> results;
        ComponentName comp;

        /* Component explicit intent tests */

        // Explicit intents with non-matching intent filter on target T+
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        comp = new ComponentName(INTENT_RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());

        /* Intent selector tests */

        Intent selector = new Intent();
        selector.setPackage(INTENT_RESOLUTION_TEST_PKG_NAME);
        intent = new Intent();
        intent.setSelector(selector);

        // Non-matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(1, results.size());
    }

    private boolean containsActivityInfoName(String expectedName, List<ResolveInfo> resolves) {
        Iterator<ResolveInfo> infoIterator = resolves.iterator();
        String current;
        while (infoIterator.hasNext()) {
            current = infoIterator.next().activityInfo.name;
            if (current.equals(expectedName)) {
                return true;
            }
        }
        return false;
    }
    private void checkActivityInfoName(String expectedName, List<ResolveInfo> resolves) {
        assertTrue(containsActivityInfoName(expectedName, resolves));
    }

    private void checkServiceInfoName(String expectedName, List<ResolveInfo> resolves) {
        boolean isContained = false;
        Iterator<ResolveInfo> infoIterator = resolves.iterator();
        String current;
        while (infoIterator.hasNext()) {
            current = infoIterator.next().serviceInfo.name;
            if (current.equals(expectedName)) {
                isContained = true;
                break;
            }
        }
        assertTrue(isContained);
    }

    private void checkPermissionInfoName(String expectedName, List<PermissionInfo> permissions) {
        List<String> names = new ArrayList<String>();
        for (PermissionInfo permission : permissions) {
            names.add(permission.name);
        }
        boolean isContained = names.contains(expectedName);
        assertTrue("Permission " + expectedName + " not present in " + names, isContained);
    }

    private void checkProviderInfoName(String expectedName, List<ProviderInfo> providers) {
        boolean isContained = false;
        Iterator<ProviderInfo> infoIterator = providers.iterator();
        String current;
        while (infoIterator.hasNext()) {
            current = infoIterator.next().name;
            if (current.equals(expectedName)) {
                isContained = true;
                break;
            }
        }
        assertTrue(isContained);
    }

    private void checkInstrumentationInfoName(String expectedName,
            List<InstrumentationInfo> instrumentations) {
        boolean isContained = false;
        Iterator<InstrumentationInfo> infoIterator = instrumentations.iterator();
        String current;
        while (infoIterator.hasNext()) {
            current = infoIterator.next().name;
            if (current.equals(expectedName)) {
                isContained = true;
                break;
            }
        }
        assertTrue(isContained);
    }

    @Test
    public void testGetInfo() throws NameNotFoundException {
        // Test getApplicationInfo, getText
        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        int discriptionRes = R.string.hello_android;
        String expectedDisciptionRes = "Hello, Android!";
        CharSequence appText = mPackageManager.getText(PACKAGE_NAME, discriptionRes, appInfo);
        assertEquals(expectedDisciptionRes, appText);
        ComponentName activityName = new ComponentName(PACKAGE_NAME, ACTIVITY_NAME);
        ComponentName serviceName = new ComponentName(PACKAGE_NAME, SERVICE_NAME);
        ComponentName receiverName = new ComponentName(PACKAGE_NAME, RECEIVER_NAME);
        ComponentName instrName = new ComponentName(PACKAGE_NAME, INSTRUMENT_NAME);

        // Test getPackageInfo
        PackageInfo packageInfo = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_INSTRUMENTATION));
        assertEquals(PACKAGE_NAME, packageInfo.packageName);

        // Test getApplicationInfo, getApplicationLabel
        String appLabel = "Android TestCase";
        assertEquals(appLabel, mPackageManager.getApplicationLabel(appInfo));
        assertEquals(PACKAGE_NAME, appInfo.processName);

        // Test getServiceInfo
        assertEquals(SERVICE_NAME, mPackageManager.getServiceInfo(serviceName,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA)).name);

        // Test getReceiverInfo
        assertEquals(RECEIVER_NAME, mPackageManager.getReceiverInfo(receiverName,
                PackageManager.ComponentInfoFlags.of(0)).name);

        // Test getPackageArchiveInfo
        final String apkRoute = mContext.getPackageCodePath();
        final String apkName = mContext.getPackageName();
        assertEquals(apkName, mPackageManager.getPackageArchiveInfo(apkRoute,
                PackageManager.PackageInfoFlags.of(0)).packageName);

        // Test getPackagesForUid, getNameForUid
        checkPackagesNameForUid(PACKAGE_NAME, mPackageManager.getPackagesForUid(appInfo.uid));
        assertEquals(PACKAGE_NAME, mPackageManager.getNameForUid(appInfo.uid));

        // Test getActivityInfo
        assertEquals(ACTIVITY_NAME, mPackageManager.getActivityInfo(activityName,
                PackageManager.ComponentInfoFlags.of(0)).name);

        // Test getPackageGids
        assertTrue(mPackageManager.getPackageGids(PACKAGE_NAME).length > 0);

        // Test getPermissionInfo
        assertEquals(GRANTED_PERMISSION_NAME,
                mPackageManager.getPermissionInfo(GRANTED_PERMISSION_NAME, 0).name);

        // Test getPermissionGroupInfo
        assertEquals(PERMISSIONGROUP_NAME, mPackageManager.getPermissionGroupInfo(
                PERMISSIONGROUP_NAME, 0).name);

        // Test getAllPermissionGroups
        List<PermissionGroupInfo> permissionGroups = mPackageManager.getAllPermissionGroups(0);
        checkPermissionGroupInfoName(PERMISSIONGROUP_NAME, permissionGroups);

        // Test getInstalledApplications
        assertTrue(mPackageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA)).size() > 0);

        // Test getInstalledPacakge
        assertTrue(mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0)).size() > 0);

        // Test getInstrumentationInfo
        assertEquals(INSTRUMENT_NAME, mPackageManager.getInstrumentationInfo(instrName, 0).name);

        // Test getSystemSharedLibraryNames, in javadoc, String array and null
        // are all OK as return value.
        mPackageManager.getSystemSharedLibraryNames();

        // Test getLaunchIntentForPackage, Intent of activity
        // android.content.pm.cts.TestPmCompare is set to match the condition
        // to make sure the return of this method is not null.
        assertEquals(MAIN_ACTION_NAME, mPackageManager.getLaunchIntentForPackage(PACKAGE_NAME)
                .getAction());

        // Test isSafeMode. Because the test case will not run in safe mode, so
        // the return will be false.
        assertFalse(mPackageManager.isSafeMode());

        // Test getTargetSdkVersion
        int expectedTargetSdk = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0)).targetSdkVersion;
        assertEquals(expectedTargetSdk, mPackageManager.getTargetSdkVersion(PACKAGE_NAME));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(
                        "android.content.cts.non_existent_package"));
    }

    private void checkPackagesNameForUid(String expectedName, String[] uid) {
        boolean isContained = false;
        for (int i = 0; i < uid.length; i++) {
            if (uid[i].equals(expectedName)) {
                isContained = true;
                break;
            }
        }
        assertTrue(isContained);
    }

    private void checkPermissionGroupInfoName(String expectedName,
            List<PermissionGroupInfo> permissionGroups) {
        boolean isContained = false;
        Iterator<PermissionGroupInfo> infoIterator = permissionGroups.iterator();
        String current;
        while (infoIterator.hasNext()) {
            current = infoIterator.next().name;
            if (current.equals(expectedName)) {
                isContained = true;
                break;
            }
        }
        assertTrue(isContained);
    }


    /**
     * Simple test for {@link PackageManager#getPreferredActivities(List, List, String)} that tests
     * calling it has no effect. The method is essentially a no-op because no preferred activities
     * can be added.
     *
     * @see PackageManager#addPreferredActivity(IntentFilter, int, ComponentName[], ComponentName)
     */
    @Test
    public void testGetPreferredActivities() {
        assertNoPreferredActivities();
    }

    /**
     * Helper method to test that {@link PackageManager#getPreferredActivities(List, List, String)}
     * returns empty lists.
     */
    private void assertNoPreferredActivities() {
        List<ComponentName> outActivities = new ArrayList<ComponentName>();
        List<IntentFilter> outFilters = new ArrayList<IntentFilter>();
        mPackageManager.getPreferredActivities(outFilters, outActivities, PACKAGE_NAME);
        assertEquals(0, outActivities.size());
        assertEquals(0, outFilters.size());
    }

    /**
     * Test that calling {@link PackageManager#addPreferredActivity(IntentFilter, int,
     * ComponentName[], ComponentName)} throws a {@link SecurityException}.
     * <p/>
     * The method is protected by the {@link android.permission.SET_PREFERRED_APPLICATIONS}
     * signature permission. Even though this app declares that permission, it still should not be
     * able to call this method because it is not signed with the platform certificate.
     */
    @Test
    public void testAddPreferredActivity() {
        IntentFilter intentFilter = new IntentFilter(ACTIVITY_ACTION_NAME);
        ComponentName[] componentName = {new ComponentName(PACKAGE_NAME, ACTIVITY_NAME)};
        try {
            mPackageManager.addPreferredActivity(intentFilter, IntentFilter.MATCH_CATEGORY_HOST,
                    componentName, componentName[0]);
            fail("addPreferredActivity unexpectedly succeeded");
        } catch (SecurityException e) {
            // expected
        }
        assertNoPreferredActivities();
    }

    /**
     * Test that calling {@link PackageManager#clearPackagePreferredActivities(String)} has no
     * effect.
     */
    @Test
    public void testClearPackagePreferredActivities() {
        // just ensure no unexpected exceptions are thrown, nothing else to do
        mPackageManager.clearPackagePreferredActivities(PACKAGE_NAME);
    }

    private void checkComponentName(String expectedName, List<ComponentName> componentNames) {
        boolean isContained = false;
        Iterator<ComponentName> nameIterator = componentNames.iterator();
        String current;
        while (nameIterator.hasNext()) {
            current = nameIterator.next().getClassName();
            if (current.equals(expectedName)) {
                isContained = true;
                break;
            }
        }
        assertTrue(isContained);
    }

    private void checkIntentFilterAction(String expectedName, List<IntentFilter> intentFilters) {
        boolean isContained = false;
        Iterator<IntentFilter> filterIterator = intentFilters.iterator();
        IntentFilter currentFilter;
        String currentAction;
        while (filterIterator.hasNext()) {
            currentFilter = filterIterator.next();
            for (int i = 0; i < currentFilter.countActions(); i++) {
                currentAction = currentFilter.getAction(i);
                if (currentAction.equals(expectedName)) {
                    isContained = true;
                    break;
                }
            }
        }
        assertTrue(isContained);
    }

    @Test
    public void testAccessEnabledSetting() {
        mPackageManager.setApplicationEnabledSetting(PACKAGE_NAME,
                COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getApplicationEnabledSetting(PACKAGE_NAME));

        ComponentName componentName = new ComponentName(PACKAGE_NAME, ACTIVITY_NAME);
        mPackageManager.setComponentEnabledSetting(componentName,
                COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getComponentEnabledSetting(componentName));
    }

    @Test
    public void testGetApplicationEnabledSetting_notFound() {
        try {
            mPackageManager.getApplicationEnabledSetting("this.package.does.not.exist");
            fail("Exception expected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetIcon() throws NameNotFoundException {
        assertNotNull(mPackageManager.getApplicationIcon(PACKAGE_NAME));
        assertNotNull(mPackageManager.getApplicationIcon(mPackageManager.getApplicationInfo(
                PACKAGE_NAME, 0)));
        assertNotNull(mPackageManager
                .getActivityIcon(new ComponentName(PACKAGE_NAME, ACTIVITY_NAME)));
        assertNotNull(mPackageManager.getActivityIcon(new Intent(MAIN_ACTION_NAME)));

        assertNotNull(mPackageManager.getDefaultActivityIcon());
        assertTrue(mPackageManager.isDefaultApplicationIcon(
                mPackageManager.getDefaultActivityIcon()));
        assertTrue(mPackageManager.isDefaultApplicationIcon(mPackageManager.getDefaultActivityIcon()
                .getConstantState().newDrawable()));

        assertFalse(mPackageManager.isDefaultApplicationIcon(mPackageManager.getActivityIcon(
                new ComponentName(PACKAGE_NAME, ACTIVITY_NAME))));

        // getDrawable is called by ComponentInfo.loadIcon() which called by getActivityIcon()
        // method of PackageMaganer. Here is just assurance for its functionality.
        int iconRes = R.drawable.start;
        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        assertNotNull(mPackageManager.getDrawable(PACKAGE_NAME, iconRes, appInfo));
    }

    @Test
    public void testCheckSignaturesMatch_byPackageName() {
        // Compare the signature of this package to another package installed by this test suite
        // (see AndroidTest.xml). Their signatures must match.
        assertEquals(PackageManager.SIGNATURE_MATCH, mPackageManager.checkSignatures(PACKAGE_NAME,
                "com.android.cts.stub"));
        // This package's signature should match its own signature.
        assertEquals(PackageManager.SIGNATURE_MATCH, mPackageManager.checkSignatures(PACKAGE_NAME,
                PACKAGE_NAME));
    }

    @Test
    public void testCheckSignaturesMatch_byUid() throws NameNotFoundException {
        // Compare the signature of this package to another package installed by this test suite
        // (see AndroidTest.xml). Their signatures must match.
        int uid1 = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0)).applicationInfo.uid;
        int uid2 = mPackageManager.getPackageInfo("com.android.cts.stub",
                PackageManager.PackageInfoFlags.of(0)).applicationInfo.uid;
        assertEquals(PackageManager.SIGNATURE_MATCH, mPackageManager.checkSignatures(uid1, uid2));

        // A UID's signature should match its own signature.
        assertEquals(PackageManager.SIGNATURE_MATCH, mPackageManager.checkSignatures(uid1, uid1));
    }

    @Test
    public void testCheckSignaturesNoMatch_byPackageName() {
        // This test package's signature shouldn't match the system's signature.
        assertEquals(PackageManager.SIGNATURE_NO_MATCH, mPackageManager.checkSignatures(
                PACKAGE_NAME, "android"));
    }

    @Test
    public void testCheckSignaturesNoMatch_byUid() throws NameNotFoundException {
        // This test package's signature shouldn't match the system's signature.
        int uid1 = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0)).applicationInfo.uid;
        int uid2 = mPackageManager.getPackageInfo("android",
                PackageManager.PackageInfoFlags.of(0)).applicationInfo.uid;
        assertEquals(PackageManager.SIGNATURE_NO_MATCH,
                mPackageManager.checkSignatures(uid1, uid2));
    }

    @Test
    public void testCheckSignaturesUnknownPackage() {
        assertEquals(PackageManager.SIGNATURE_UNKNOWN_PACKAGE, mPackageManager.checkSignatures(
                PACKAGE_NAME, "this.package.does.not.exist"));
    }

    @Test
    public void testCheckPermissionGranted() {
        assertEquals(PackageManager.PERMISSION_GRANTED,
                mPackageManager.checkPermission(GRANTED_PERMISSION_NAME, PACKAGE_NAME));
    }

    @Test
    public void testCheckPermissionNotGranted() {
        assertEquals(PackageManager.PERMISSION_DENIED,
                mPackageManager.checkPermission(NOT_GRANTED_PERMISSION_NAME, PACKAGE_NAME));
    }

    @Test
    public void testResolveMethods() {
        // Test resolveActivity
        Intent intent = new Intent(ACTIVITY_ACTION_NAME);
        intent.setComponent(new ComponentName(PACKAGE_NAME, ACTIVITY_NAME));
        assertEquals(ACTIVITY_NAME, mPackageManager.resolveActivity(intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY))
                .activityInfo.name);

        // Test resolveService
        intent = new Intent(SERVICE_ACTION_NAME);
        intent.setComponent(new ComponentName(PACKAGE_NAME, SERVICE_NAME));
        ResolveInfo resolveInfo = mPackageManager.resolveService(intent,
                PackageManager.ResolveInfoFlags.of(0));
        assertEquals(SERVICE_NAME, resolveInfo.serviceInfo.name);

        // Test resolveContentProvider
        String providerAuthorities = "ctstest";
        assertEquals(PROVIDER_NAME,
                mPackageManager.resolveContentProvider(providerAuthorities,
                        PackageManager.ComponentInfoFlags.of(0)).name);
    }

    @Test
    public void testGetResources() throws NameNotFoundException {
        ComponentName componentName = new ComponentName(PACKAGE_NAME, ACTIVITY_NAME);
        int resourceId = R.xml.pm_test;
        String xmlName = "android.content.cts:xml/pm_test";
        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        assertNotNull(mPackageManager.getXml(PACKAGE_NAME, resourceId, appInfo));
        assertEquals(xmlName, mPackageManager.getResourcesForActivity(componentName)
                .getResourceName(resourceId));
        assertEquals(xmlName, mPackageManager.getResourcesForApplication(appInfo).getResourceName(
                resourceId));
        assertEquals(xmlName, mPackageManager.getResourcesForApplication(PACKAGE_NAME)
                .getResourceName(resourceId));
    }

    @Test
    public void testGetResources_withConfig() throws NameNotFoundException {
        int resourceId = R.string.config_overridden_string;
        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));

        Configuration c1 = new Configuration(mContext.getResources().getConfiguration());
        c1.orientation = Configuration.ORIENTATION_PORTRAIT;
        assertEquals("default", mPackageManager.getResourcesForApplication(
                appInfo, c1).getString(resourceId));

        Configuration c2 = new Configuration(mContext.getResources().getConfiguration());
        c2.orientation = Configuration.ORIENTATION_LANDSCAPE;
        assertEquals("landscape", mPackageManager.getResourcesForApplication(
                appInfo, c2).getString(resourceId));
    }

    @Test
    public void testGetPackageArchiveInfo() {
        final String apkPath = mContext.getPackageCodePath();
        final String apkName = mContext.getPackageName();

        PackageInfo pkgInfo = mPackageManager.getPackageArchiveInfo(apkPath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        assertEquals("getPackageArchiveInfo should return the correct package name",
                apkName, pkgInfo.packageName);
        assertNotNull("SigningInfo should have been collected when GET_SIGNING_CERTIFICATES"
                        + " flag is specified", pkgInfo.signingInfo);

        pkgInfo = mPackageManager.getPackageArchiveInfo(apkPath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES));
        assertNotNull("Signatures should have been collected when GET_SIGNATURES"
                + " flag is specified", pkgInfo.signatures);

        pkgInfo = mPackageManager.getPackageArchiveInfo(apkPath,
                PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNATURES | PackageManager.GET_SIGNING_CERTIFICATES));
        assertNotNull("SigningInfo should have been collected when"
                        + " GET_SIGNATURES and GET_SIGNING_CERTIFICATES flags are both specified",
                pkgInfo.signingInfo);
        assertNotNull("Signatures should have been collected when"
                + " GET_SIGNATURES and GET_SIGNING_CERTIFICATES flags are both specified",
                pkgInfo.signatures);

    }

    private void runTestGetPackageArchiveInfoSameApplicationInfo(long flags) {
        final String apkPath = mContext.getPackageCodePath();
        PackageInfo packageInfo = mPackageManager.getPackageArchiveInfo(apkPath,
                PackageManager.PackageInfoFlags.of(flags));

        ApplicationInfo applicationInfo = null;
        if (packageInfo.activities != null) {
            for (ActivityInfo ac : packageInfo.activities) {
                if (applicationInfo == null) {
                    applicationInfo = ac.applicationInfo;
                } else {
                    assertSame(applicationInfo, ac.applicationInfo);
                }
            }
        }
        if (packageInfo.receivers != null) {
            for (ActivityInfo ac : packageInfo.receivers) {
                if (applicationInfo == null) {
                    applicationInfo = ac.applicationInfo;
                } else {
                    assertSame(applicationInfo, ac.applicationInfo);
                }
            }
        }
        if (packageInfo.services != null) {
            for (ServiceInfo si : packageInfo.services) {
                if (applicationInfo == null) {
                    applicationInfo = si.applicationInfo;
                } else {
                    assertSame(applicationInfo, si.applicationInfo);
                }
            }
        }
        if (packageInfo.providers != null) {
            for (ProviderInfo pi : packageInfo.providers) {
                if (applicationInfo == null) {
                    applicationInfo = pi.applicationInfo;
                } else {
                    assertSame(applicationInfo, pi.applicationInfo);
                }
            }
        }
    }

    @Test
    public void testGetPackageArchiveInfoSameApplicationInfo() {
        runTestGetPackageArchiveInfoSameApplicationInfo(PackageManager.GET_META_DATA);
        runTestGetPackageArchiveInfoSameApplicationInfo(
                PackageManager.GET_META_DATA | PackageManager.GET_ACTIVITIES);
        runTestGetPackageArchiveInfoSameApplicationInfo(
                PackageManager.GET_META_DATA | PackageManager.GET_RECEIVERS);
        runTestGetPackageArchiveInfoSameApplicationInfo(
                PackageManager.GET_META_DATA | PackageManager.GET_SERVICES);
        runTestGetPackageArchiveInfoSameApplicationInfo(
                PackageManager.GET_META_DATA | PackageManager.GET_PROVIDERS);
        runTestGetPackageArchiveInfoSameApplicationInfo(
                PackageManager.GET_META_DATA | PackageManager.GET_ACTIVITIES
                        | PackageManager.GET_RECEIVERS);
    }

    @Test
    public void testGetNamesForUids_null() throws Exception {
        assertNull(mPackageManager.getNamesForUids(null));
    }

    @Test
    public void testGetNamesForUids_empty() throws Exception {
        assertNull(mPackageManager.getNamesForUids(new int[0]));
    }

    @Test
    public void testGetNamesForUids_valid() throws Exception {
        final int shimId =
                mPackageManager.getApplicationInfo("com.android.cts.ctsshim",
                        PackageManager.ApplicationInfoFlags.of(0)).uid;
        final int[] uids = new int[]{
                1000,
                Integer.MAX_VALUE,
                shimId,
        };
        final String[] result;
        result = mPackageManager.getNamesForUids(uids);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals("shared:android.uid.system", result[0]);
        assertEquals(null, result[1]);
        assertEquals("shared:com.android.cts.ctsshim", result[2]);
    }

    @Test
    public void testGetPackageUid() throws NameNotFoundException {
        int userId = mContext.getUserId();
        int expectedUid = UserHandle.getUid(userId, 1000);

        assertEquals(expectedUid, mPackageManager.getPackageUid("android",
                PackageManager.PackageInfoFlags.of(0)));

        int uid = mPackageManager.getApplicationInfo("com.android.cts.ctsshim",
                PackageManager.ApplicationInfoFlags.of(0)).uid;
        assertEquals(uid, mPackageManager.getPackageUid("com.android.cts.ctsshim",
                PackageManager.PackageInfoFlags.of(0)));
    }

    @Test
    public void testGetPackageInfo() throws NameNotFoundException {
        PackageInfo pkgInfo = mPackageManager.getPackageInfo(PACKAGE_NAME, GET_META_DATA
                | GET_PERMISSIONS | GET_ACTIVITIES | GET_PROVIDERS | GET_SERVICES | GET_RECEIVERS);
        assertTestPackageInfo(pkgInfo);
    }

    @Test
    public void testPackageSettingsFlags() throws Exception {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                "pm install -t " + HELLO_WORLD_LOTS_OF_FLAGS_APK));
        final String pkgFlags = parsePackageDump(HELLO_WORLD_PACKAGE_NAME, "    pkgFlags=[");
        assertEquals(
                " DEBUGGABLE HAS_CODE ALLOW_TASK_REPARENTING ALLOW_CLEAR_USER_DATA TEST_ONLY "
                        + "VM_SAFE_MODE ALLOW_BACKUP LARGE_HEAP ]",
                pkgFlags);
        final String privatePkgFlags = parsePackageDump(HELLO_WORLD_PACKAGE_NAME,
                "    privatePkgFlags=[");
        assertEquals(
                " PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION "
                        + "ALLOW_AUDIO_PLAYBACK_CAPTURE "
                        + "PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING ]",
                privatePkgFlags);
    }

    @Test
    public void testGetPackageInfo_notFound() {
        try {
            mPackageManager.getPackageInfo("this.package.does.not.exist",
                    PackageManager.PackageInfoFlags.of(0));
            fail("Exception expected");
        } catch (NameNotFoundException expected) {
        }
    }

    @Test
    public void testGetInstalledPackages() throws Exception {
        List<PackageInfo> pkgs = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                        GET_META_DATA | GET_PERMISSIONS | GET_ACTIVITIES | GET_PROVIDERS
                                | GET_SERVICES | GET_RECEIVERS));

        PackageInfo pkgInfo = findPackageOrFail(pkgs, PACKAGE_NAME);
        assertTestPackageInfo(pkgInfo);
    }

    /**
     * Asserts that the pkgInfo object correctly describes the {@link #PACKAGE_NAME} package.
     */
    private void assertTestPackageInfo(PackageInfo pkgInfo) {
        // Check metadata
        ApplicationInfo appInfo = pkgInfo.applicationInfo;
        assertEquals(APPLICATION_NAME, appInfo.name);
        assertEquals("Android TestCase", appInfo.loadLabel(mPackageManager));
        assertEquals(PACKAGE_NAME, appInfo.packageName);
        assertTrue(appInfo.enabled);
        // The process name defaults to the package name when not set.
        assertEquals(PACKAGE_NAME, appInfo.processName);
        assertEquals(0, appInfo.flags & FLAG_SYSTEM);
        assertEquals(FLAG_INSTALLED, appInfo.flags & FLAG_INSTALLED);
        assertEquals(FLAG_HAS_CODE, appInfo.flags & FLAG_HAS_CODE);

        // Check required permissions
        List<String> requestedPermissions = Arrays.asList(pkgInfo.requestedPermissions);
        assertThat(requestedPermissions).containsAtLeast(
                "android.permission.MANAGE_ACCOUNTS",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.content.cts.permission.TEST_GRANTED");

        // Check usesPermissionFlags
        boolean requestedAccessFineLocation = false;
        boolean requestedAccessCoarseLocation = false;
        for (int i = 0; i < pkgInfo.requestedPermissions.length; i++) {
            final String name = pkgInfo.requestedPermissions[i];
            final int flags = pkgInfo.requestedPermissionsFlags[i];

            // Verify "never for location" flag
            final boolean neverForLocation = (flags
                    & PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0;
            if ("android.content.cts.permission.TEST_GRANTED".equals(name)) {
                assertTrue(name + " with flags " + flags, neverForLocation);
            } else {
                assertFalse(name + " with flags " + flags, neverForLocation);
            }

            // Verify "implicit" flag
            final boolean hasImplicitFlag =
                    (flags & PackageInfo.REQUESTED_PERMISSION_IMPLICIT) != 0;
            if ("android.permission.ACCESS_FINE_LOCATION".equals(name)) {
                assertFalse(name + " with flags " + flags, hasImplicitFlag);
                requestedAccessFineLocation = true;
            }
            if ("android.permission.ACCESS_COARSE_LOCATION".equals(name)) {
                assertTrue(name + " with flags " + flags, hasImplicitFlag);
                requestedAccessCoarseLocation = true;
            }
        }
        assertTrue("expected ACCESS_FINE_LOCATION to be requested", requestedAccessFineLocation);
        assertTrue("expected ACCESS_COARSE_LOCATION to be requested",
                requestedAccessCoarseLocation);

        // Check declared permissions
        PermissionInfo declaredPermission = (PermissionInfo) findPackageItemOrFail(
                pkgInfo.permissions, CALL_ABROAD_PERMISSION_NAME);
        assertEquals("Call abroad", declaredPermission.loadLabel(mPackageManager));
        assertEquals(PERMISSIONGROUP_NAME, declaredPermission.group);
        assertEquals(PermissionInfo.PROTECTION_NORMAL, declaredPermission.protectionLevel);

        // Check if number of activities in PackageInfo matches number of activities in manifest,
        // to make sure no synthesized activities not in the manifest are returned.
        assertEquals("Number of activities in manifest != Number of activities in PackageInfo",
                NUM_OF_ACTIVITIES_IN_MANIFEST, pkgInfo.activities.length);
        // Check activities
        ActivityInfo activity = findPackageItemOrFail(pkgInfo.activities, ACTIVITY_NAME);
        assertTrue(activity.enabled);
        assertTrue(activity.exported); // Has intent filters - export by default.
        assertEquals(PACKAGE_NAME, activity.taskAffinity);
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activity.launchMode);

        // Check services
        ServiceInfo service = findPackageItemOrFail(pkgInfo.services, SERVICE_NAME);
        assertTrue(service.enabled);
        assertTrue(service.exported); // Has intent filters - export by default.
        assertEquals(PACKAGE_NAME, service.packageName);
        assertEquals(CALL_ABROAD_PERMISSION_NAME, service.permission);

        // Check ContentProviders
        ProviderInfo provider = findPackageItemOrFail(pkgInfo.providers, PROVIDER_NAME);
        assertTrue(provider.enabled);
        assertFalse(provider.exported); // Don't export by default.
        assertEquals(PACKAGE_NAME, provider.packageName);
        assertEquals("ctstest", provider.authority);

        // Check Receivers
        ActivityInfo receiver = findPackageItemOrFail(pkgInfo.receivers, RECEIVER_NAME);
        assertTrue(receiver.enabled);
        assertTrue(receiver.exported); // Has intent filters - export by default.
        assertEquals(PACKAGE_NAME, receiver.packageName);
    }

    // Tests that other packages can be queried.
    @Test
    public void testGetInstalledPackages_OtherPackages() throws Exception {
        List<PackageInfo> pkgInfos = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0));

        // Check a normal package.
        PackageInfo pkgInfo = findPackageOrFail(pkgInfos, "com.android.cts.stub"); // A test package
        assertEquals(0, pkgInfo.applicationInfo.flags & FLAG_SYSTEM);

        // Check a system package.
        pkgInfo = findPackageOrFail(pkgInfos, "android");
        assertEquals(FLAG_SYSTEM, pkgInfo.applicationInfo.flags & FLAG_SYSTEM);
    }

    @Test
    public void testGetInstalledApplications() throws Exception {
        List<ApplicationInfo> apps = mPackageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(GET_META_DATA));

        ApplicationInfo app = findPackageItemOrFail(
                apps.toArray(new ApplicationInfo[]{}), APPLICATION_NAME);

        assertEquals(APPLICATION_NAME, app.name);
        assertEquals("Android TestCase", app.loadLabel(mPackageManager));
        assertEquals(PACKAGE_NAME, app.packageName);
        assertTrue(app.enabled);
        // The process name defaults to the package name when not set.
        assertEquals(PACKAGE_NAME, app.processName);
    }

    private PackageInfo findPackageOrFail(List<PackageInfo> pkgInfos, String pkgName) {
        for (PackageInfo pkgInfo : pkgInfos) {
            if (pkgName.equals(pkgInfo.packageName)) {
                return pkgInfo;
            }
        }
        fail("Package not found with name " + pkgName);
        return null;
    }

    private <T extends PackageItemInfo> T findPackageItemOrFail(T[] items, String name) {
        for (T item : items) {
            if (name.equals(item.name)) {
                return item;
            }
        }
        fail("Package item not found with name " + name);
        return null;
    }

    @Test
    public void testGetPackagesHoldingPermissions() {
        List<PackageInfo> pkgInfos = mPackageManager.getPackagesHoldingPermissions(
                new String[]{GRANTED_PERMISSION_NAME}, PackageManager.PackageInfoFlags.of(0));
        findPackageOrFail(pkgInfos, PACKAGE_NAME);

        pkgInfos = mPackageManager.getPackagesHoldingPermissions(
                new String[]{NOT_GRANTED_PERMISSION_NAME},
                PackageManager.PackageInfoFlags.of(0));
        for (PackageInfo pkgInfo : pkgInfos) {
            if (PACKAGE_NAME.equals(pkgInfo.packageName)) {
                fail("Must not return package " + PACKAGE_NAME);
            }
        }
    }

    @Test
    public void testGetPermissionInfo() throws NameNotFoundException {
        // Check a normal permission.
        String permissionName = "android.permission.INTERNET";
        PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(permissionName, 0);
        assertEquals(permissionName, permissionInfo.name);
        assertEquals(PermissionInfo.PROTECTION_NORMAL, permissionInfo.getProtection());

        // Check a dangerous (runtime) permission.
        permissionName = "android.permission.RECORD_AUDIO";
        permissionInfo = mPackageManager.getPermissionInfo(permissionName, 0);
        assertEquals(permissionName, permissionInfo.name);
        assertEquals(PermissionInfo.PROTECTION_DANGEROUS, permissionInfo.getProtection());
        assertNotNull(permissionInfo.group);

        // Check a signature permission.
        permissionName = "android.permission.MODIFY_PHONE_STATE";
        permissionInfo = mPackageManager.getPermissionInfo(permissionName, 0);
        assertEquals(permissionName, permissionInfo.name);
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, permissionInfo.getProtection());

        // Check a special access (appop) permission.
        permissionName = "android.permission.SYSTEM_ALERT_WINDOW";
        permissionInfo = mPackageManager.getPermissionInfo(permissionName, 0);
        assertEquals(permissionName, permissionInfo.name);
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, permissionInfo.getProtection());
        assertEquals(PermissionInfo.PROTECTION_FLAG_APPOP,
                permissionInfo.getProtectionFlags() & PermissionInfo.PROTECTION_FLAG_APPOP);
    }

    @Test
    public void testGetPermissionInfo_notFound() {
        try {
            mPackageManager.getPermissionInfo("android.permission.nonexistent.permission", 0);
            fail("Exception expected");
        } catch (NameNotFoundException expected) {
        }
    }

    @Test
    public void testGetPermissionGroupInfo() throws NameNotFoundException {
        PermissionGroupInfo groupInfo = mPackageManager.getPermissionGroupInfo(
                PERMISSIONGROUP_NAME, 0);
        assertEquals(PERMISSIONGROUP_NAME, groupInfo.name);
        assertEquals(PACKAGE_NAME, groupInfo.packageName);
        assertFalse(TextUtils.isEmpty(groupInfo.loadDescription(mPackageManager)));
    }

    @Test
    public void testGetPermissionGroupInfo_notFound() throws NameNotFoundException {
        try {
            mPackageManager.getPermissionGroupInfo("this.group.does.not.exist", 0);
            fail("Exception expected");
        } catch (NameNotFoundException expected) {
        }
    }

    @Test
    public void testAddPermission_cantAddOutsideRoot() {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.name = "some.other.permission.tree.some-permission";
        permissionInfo.nonLocalizedLabel = "Some Permission";
        permissionInfo.protectionLevel = PermissionInfo.PROTECTION_NORMAL;
        // Remove first
        try {
            mPackageManager.removePermission(permissionInfo.name);
        } catch (SecurityException se) {
        }
        try {
            mPackageManager.addPermission(permissionInfo);
            fail("Must not add permission outside the permission tree defined in the manifest.");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testAddPermission() throws NameNotFoundException {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.name = PERMISSION_TREE_ROOT + ".some-permission";
        permissionInfo.protectionLevel = PermissionInfo.PROTECTION_NORMAL;
        permissionInfo.nonLocalizedLabel = "Some Permission";
        // Remove first
        try {
            mPackageManager.removePermission(permissionInfo.name);
        } catch (SecurityException se) {
        }
        mPackageManager.addPermission(permissionInfo);
        PermissionInfo savedInfo = mPackageManager.getPermissionInfo(permissionInfo.name, 0);
        assertEquals(PACKAGE_NAME, savedInfo.packageName);
        assertEquals(PermissionInfo.PROTECTION_NORMAL, savedInfo.protectionLevel);
    }

    @Test
    public void testSetSystemAppHiddenUntilInstalled() throws Exception {
        String packageToManipulate = "com.android.cts.ctsshim";
        try {
            mPackageManager.getPackageInfo(packageToManipulate, MATCH_SYSTEM_ONLY);
        } catch (NameNotFoundException e) {
            Log.i(TAG, "Device doesn't have " + packageToManipulate + " installed, skipping");
            return;
        }

        try {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mPackageManager.setSystemAppState(packageToManipulate,
                            PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mPackageManager.setSystemAppState(packageToManipulate,
                            PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN));

            // Setting the state to SYSTEM_APP_STATE_UNINSTALLED is an async operation in
            // PackageManagerService with no way to listen for completion, so poll until the
            // app is no longer found.
            int pollingPeriodMs = 100;
            int timeoutMs = 1000;
            long startTimeMs = SystemClock.elapsedRealtime();
            boolean isAppStillVisible = true;
            while (SystemClock.elapsedRealtime() < startTimeMs + timeoutMs) {
                try {
                    mPackageManager.getPackageInfo(packageToManipulate,
                            PackageManager.PackageInfoFlags.of(MATCH_SYSTEM_ONLY));
                } catch (NameNotFoundException e) {
                    // expected, stop polling
                    isAppStillVisible = false;
                    break;
                }
                Thread.sleep(pollingPeriodMs);
            }
            if (isAppStillVisible) {
                fail(packageToManipulate + " should not be found via getPackageInfo.");
            }
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mPackageManager.setSystemAppState(packageToManipulate,
                            PackageManager.SYSTEM_APP_STATE_INSTALLED));
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mPackageManager.setSystemAppState(packageToManipulate,
                            PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE));
            try {
                mPackageManager.getPackageInfo(packageToManipulate,
                        PackageManager.PackageInfoFlags.of(MATCH_SYSTEM_ONLY));
            } catch (NameNotFoundException e) {
                fail(packageToManipulate
                        + " should be found via getPackageInfo after re-enabling.");
            }
        }
    }

    @Test
    public void testGetPackageInfo_ApexSupported_ApexPackage_MatchesApex() throws Exception {
        assumeTrue("Device doesn't support updating APEX", isUpdatingApexSupported());

        final int flags = PackageManager.MATCH_APEX
                | PackageManager.MATCH_FACTORY_ONLY
                | PackageManager.GET_SIGNING_CERTIFICATES
                | PackageManager.GET_SIGNATURES;
        PackageInfo packageInfo = mPackageManager.getPackageInfo(SHIM_APEX_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(flags));
        assertShimApexInfoIsCorrect(packageInfo);
    }

    @Test
    public void testGetPackageInfo_ApexSupported_ApexPackage_DoesNotMatchApex() {
        assumeTrue("Device doesn't support updating APEX", isUpdatingApexSupported());

        try {
            mPackageManager.getPackageInfo(SHIM_APEX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0));
            fail("NameNotFoundException expected");
        } catch (NameNotFoundException expected) {
        }
    }

    @Test
    public void testGetPackageInfo_ApexNotSupported_ApexPackage_MatchesApex() {
        assumeFalse("Device supports updating APEX", isUpdatingApexSupported());

        try {
            mPackageManager.getPackageInfo(SHIM_APEX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));
            fail("NameNotFoundException expected");
        } catch (NameNotFoundException expected) {
        }
    }

    @Test
    public void testGetPackageInfo_ApexNotSupported_ApexPackage_DoesNotMatchApex() {
        assumeFalse("Device supports updating APEX", isUpdatingApexSupported());

        try {
            mPackageManager.getPackageInfo(SHIM_APEX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0));
            fail("NameNotFoundException expected");
        } catch (NameNotFoundException expected) {
        }
    }

    @Test
    public void testGetInstalledPackages_ApexSupported_MatchesApex() {
        assumeTrue("Device doesn't support updating APEX", isUpdatingApexSupported());

        final int flags = PackageManager.MATCH_APEX
                | PackageManager.MATCH_FACTORY_ONLY
                | PackageManager.GET_SIGNING_CERTIFICATES
                | PackageManager.GET_SIGNATURES;
        List<PackageInfo> installedPackages = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags));
        List<PackageInfo> shimApex = installedPackages.stream().filter(
                packageInfo -> packageInfo.packageName.equals(SHIM_APEX_PACKAGE_NAME)).collect(
                Collectors.toList());
        assertWithMessage("More than one shim apex found").that(shimApex).hasSize(1);
        assertShimApexInfoIsCorrect(shimApex.get(0));
    }

    @Test
    public void testGetInstalledPackages_ApexSupported_DoesNotMatchApex() {
        assumeTrue("Device doesn't support updating APEX", isUpdatingApexSupported());

        List<PackageInfo> installedPackages = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0));
        List<PackageInfo> shimApex = installedPackages.stream().filter(
                packageInfo -> packageInfo.packageName.equals(SHIM_APEX_PACKAGE_NAME)).collect(
                Collectors.toList());
        assertWithMessage("Shim apex wasn't supposed to be found").that(shimApex).isEmpty();
    }

    @Test
    public void testGetInstalledPackages_ApexNotSupported_MatchesApex() {
        assumeFalse("Device supports updating APEX", isUpdatingApexSupported());

        List<PackageInfo> installedPackages = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));
        List<PackageInfo> shimApex = installedPackages.stream().filter(
                packageInfo -> packageInfo.packageName.equals(SHIM_APEX_PACKAGE_NAME)).collect(
                Collectors.toList());
        assertWithMessage("Shim apex wasn't supposed to be found").that(shimApex).isEmpty();
    }

    @Test
    public void testGetInstalledPackages_ApexNotSupported_DoesNotMatchApex() {
        assumeFalse("Device supports updating APEX", isUpdatingApexSupported());

        List<PackageInfo> installedPackages = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0));
        List<PackageInfo> shimApex = installedPackages.stream().filter(
                packageInfo -> packageInfo.packageName.equals(SHIM_APEX_PACKAGE_NAME)).collect(
                Collectors.toList());
        assertWithMessage("Shim apex wasn't supposed to be found").that(shimApex).isEmpty();
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with all components in this
     * package will only be filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_noMetaData_InPackage() throws Exception {
        final PackageInfo info = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(
                        GET_ACTIVITIES | GET_SERVICES | GET_RECEIVERS | GET_PROVIDERS));

        assertThat(info.applicationInfo.metaData).isNull();
        Arrays.stream(info.activities).forEach(i -> assertThat(i.metaData).isNull());
        Arrays.stream(info.services).forEach(i -> assertThat(i.metaData).isNull());
        Arrays.stream(info.receivers).forEach(i -> assertThat(i.metaData).isNull());
        Arrays.stream(info.providers).forEach(i -> assertThat(i.metaData).isNull());
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this application will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_noMetaData_InApplication() throws Exception {
        final ApplicationInfo ai = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        assertThat(ai.metaData).isNull();
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this activity will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_noMetaData_InActivity() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockActivity.class);
        final ActivityInfo info = mPackageManager.getActivityInfo(componentName,
                PackageManager.ComponentInfoFlags.of(0));
        assertThat(info.metaData).isNull();
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this service will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_noMetaData_InService() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockService.class);
        final ServiceInfo info = mPackageManager.getServiceInfo(componentName,
                PackageManager.ComponentInfoFlags.of(0));
        assertThat(info.metaData).isNull();
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this receiver will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_noMetaData_InBroadcastReceiver() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockReceiver.class);
        final ActivityInfo info = mPackageManager.getReceiverInfo(componentName,
                PackageManager.ComponentInfoFlags.of(0));
        assertThat(info.metaData).isNull();
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this provider will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_noMetaData_InContentProvider() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockContentProvider.class);
        final ProviderInfo info = mPackageManager.getProviderInfo(componentName,
                PackageManager.ComponentInfoFlags.of(0));
        assertThat(info.metaData).isNull();
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with all components in this
     * package will not be filled in if the {@link PackageManager#GET_META_DATA} flag is not set.
     */
    @Test
    public void testGetInfo_checkMetaData_InPackage() throws Exception {
        final PackageInfo info = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(
                        GET_META_DATA | GET_ACTIVITIES | GET_SERVICES | GET_RECEIVERS
                                | GET_PROVIDERS));

        checkMetaData(new PackageItemInfo(info.applicationInfo));
        checkMetaData(new PackageItemInfo(
                findPackageItemOrFail(info.activities, "android.content.cts.MockActivity")));
        checkMetaData(new PackageItemInfo(
                findPackageItemOrFail(info.services, "android.content.cts.MockService")));
        checkMetaData(new PackageItemInfo(
                findPackageItemOrFail(info.receivers, "android.content.cts.MockReceiver")));
        checkMetaData(new PackageItemInfo(
                findPackageItemOrFail(info.providers, "android.content.cts.MockContentProvider")));
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this application will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_checkMetaData_InApplication() throws Exception {
        final ApplicationInfo ai = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(GET_META_DATA));
        checkMetaData(new PackageItemInfo(ai));
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this activity will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_checkMetaData_InActivity() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockActivity.class);
        final ActivityInfo ai = mPackageManager.getActivityInfo(componentName,
                PackageManager.ComponentInfoFlags.of(GET_META_DATA));
        checkMetaData(new PackageItemInfo(ai));
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this service will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_checkMetaData_InService() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockService.class);
        final ServiceInfo info = mPackageManager.getServiceInfo(componentName,
                PackageManager.ComponentInfoFlags.of(GET_META_DATA));
        checkMetaData(new PackageItemInfo(info));
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this receiver will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_checkMetaData_InBroadcastReceiver() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockReceiver.class);
        final ActivityInfo info = mPackageManager.getReceiverInfo(componentName,
                PackageManager.ComponentInfoFlags.of(GET_META_DATA));
        checkMetaData(new PackageItemInfo(info));
    }

    /**
     * Test that {@link ComponentInfo#metaData} data associated with this provider will only be
     * filled in if the {@link PackageManager#GET_META_DATA} flag is set.
     */
    @Test
    public void testGetInfo_checkMetaData_InContentProvider() throws Exception {
        final ComponentName componentName = new ComponentName(mContext, MockContentProvider.class);
        final ProviderInfo info = mPackageManager.getProviderInfo(componentName,
                PackageManager.ComponentInfoFlags.of(GET_META_DATA));
        checkMetaData(new PackageItemInfo(info));
    }

    private void checkMetaData(@NonNull PackageItemInfo ci)
            throws IOException, XmlPullParserException, NameNotFoundException {
        final Bundle metaData = ci.metaData;
        final Resources res = mPackageManager.getResourcesForApplication(ci.packageName);
        assertWithMessage("No meta-data found").that(metaData).isNotNull();

        assertThat(metaData.getString("android.content.cts.string")).isEqualTo("foo");
        assertThat(metaData.getBoolean("android.content.cts.boolean")).isTrue();
        assertThat(metaData.getInt("android.content.cts.integer")).isEqualTo(100);
        assertThat(metaData.getInt("android.content.cts.color")).isEqualTo(0xff000000);
        assertThat(metaData.getFloat("android.content.cts.float")).isEqualTo(100.1f);
        assertThat(metaData.getInt("android.content.cts.reference")).isEqualTo(R.xml.metadata);

        XmlResourceParser xml = null;
        TypedArray a = null;
        try {
            xml = ci.loadXmlMetaData(mPackageManager, "android.content.cts.reference");
            assertThat(xml).isNotNull();

            int type;
            while ((type = xml.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Seek parser to start tag.
            }
            assertThat(type).isEqualTo(XmlPullParser.START_TAG);
            assertThat(xml.getName()).isEqualTo("thedata");

            assertThat(xml.getAttributeValue(null, "rawText")).isEqualTo("some raw text");
            assertThat(xml.getAttributeIntValue(null, "rawColor", 0)).isEqualTo(0xffffff00);
            assertThat(xml.getAttributeValue(null, "rawColor")).isEqualTo("#ffffff00");

            a = res.obtainAttributes(xml, new int[]{android.R.attr.text, android.R.attr.color});
            assertThat(a.getString(0)).isEqualTo("metadata text");
            assertThat(a.getColor(1, 0)).isEqualTo(0xffff0000);
            assertThat(a.getString(1)).isEqualTo("#ffff0000");
        } finally {
            if (a != null) {
                a.recycle();
            }
            if (xml != null) {
                xml.close();
            }
        }
    }

    @Test
    public void testGetApplicationInfo_ApexSupported_MatchesApex() throws Exception {
        assumeTrue("Device doesn't support updating APEX", isUpdatingApexSupported());

        ApplicationInfo ai = mPackageManager.getApplicationInfo(
                SHIM_APEX_PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_APEX));
        assertThat(ai.sourceDir).isEqualTo("/system/apex/com.android.apex.cts.shim.apex");
        assertThat(ai.publicSourceDir).isEqualTo(ai.sourceDir);
        assertThat(ai.flags & ApplicationInfo.FLAG_SYSTEM).isEqualTo(ApplicationInfo.FLAG_SYSTEM);
        assertThat(ai.flags & ApplicationInfo.FLAG_INSTALLED)
                .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
    }

    @Test
    public void testGetApplicationInfo_icon_MatchesUseRoundIcon() throws Exception {
        installPackage(HELLO_WORLD_APK);
        final boolean useRoundIcon = mContext.getResources().getBoolean(
                mContext.getResources().getIdentifier("config_useRoundIcon", "bool", "android"));
        final ApplicationInfo info = mPackageManager.getApplicationInfo(HELLO_WORLD_PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        assertThat(info.icon).isEqualTo((useRoundIcon ? info.roundIconRes : info.iconRes));
    }

    private boolean isUpdatingApexSupported() {
        return SystemProperties.getBoolean("ro.apex.updatable", false);
    }

    private static void assertShimApexInfoIsCorrect(PackageInfo packageInfo) {
        assertThat(packageInfo.packageName).isEqualTo(SHIM_APEX_PACKAGE_NAME);
        assertThat(packageInfo.getLongVersionCode()).isEqualTo(1);
        assertThat(packageInfo.isApex).isTrue();
        assertThat(packageInfo.applicationInfo.sourceDir).isEqualTo(
                "/system/apex/com.android.apex.cts.shim.apex");
        assertThat(packageInfo.applicationInfo.publicSourceDir)
                .isEqualTo(packageInfo.applicationInfo.sourceDir);
        // Verify that legacy mechanism for handling signatures is supported.
        Signature[] pastSigningCertificates =
                packageInfo.signingInfo.getSigningCertificateHistory();
        assertThat(packageInfo.signatures)
                .asList().containsExactly((Object[]) pastSigningCertificates);
    }

    /**
     * Runs a test for all combinations of a set of flags
     *
     * @param flagValues Which flags to use
     * @param test       The test
     */
    public void runTestWithFlags(int[] flagValues, Consumer<Integer> test) {
        for (int i = 0; i < (1 << flagValues.length); i++) {
            int flags = 0;
            for (int j = 0; j < flagValues.length; j++) {
                if ((i & (1 << j)) != 0) {
                    flags |= flagValues[j];
                }
            }
            try {
                test.accept(flags);
            } catch (Throwable t) {
                throw new AssertionError(
                        "Test failed for flags 0x" + String.format("%08x", flags), t);
            }
        }
    }

    /**
     * Test that the MATCH_FACTORY_ONLY flag doesn't add new package names in the result of
     * getInstalledPackages.
     */
    @Test
    public void testGetInstalledPackages_WithFactoryFlag_IsSubset() {
        runTestWithFlags(PACKAGE_INFO_MATCH_FLAGS,
                this::testGetInstalledPackages_WithFactoryFlag_IsSubset);
    }

    public void testGetInstalledPackages_WithFactoryFlag_IsSubset(int flags) {
        List<PackageInfo> packageInfos = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags));
        List<PackageInfo> packageInfos2 = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags | MATCH_FACTORY_ONLY));
        Set<String> supersetNames =
                packageInfos.stream().map(pi -> pi.packageName).collect(Collectors.toSet());

        for (PackageInfo pi : packageInfos2) {
            if (!supersetNames.contains(pi.packageName)) {
                throw new AssertionError(
                        "The subset contains packages that the superset doesn't contain.");
            }
        }
    }

    /**
     * Test that the MATCH_FACTORY_ONLY flag filters out all non-system packages in the result of
     * getInstalledPackages.
     */
    @Test
    public void testGetInstalledPackages_WithFactoryFlag_ImpliesSystem() {
        runTestWithFlags(PACKAGE_INFO_MATCH_FLAGS,
                this::testGetInstalledPackages_WithFactoryFlag_ImpliesSystem);
    }

    public void testGetInstalledPackages_WithFactoryFlag_ImpliesSystem(int flags) {
        List<PackageInfo> packageInfos =
                mPackageManager.getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(flags | MATCH_FACTORY_ONLY));
        for (PackageInfo pi : packageInfos) {
            if (!pi.applicationInfo.isSystemApp()) {
                throw new AssertionError(pi.packageName + " is not a system app.");
            }
        }
    }

    /**
     * Test that we con't have conflicting package names between APK and APEX.
     */
    @Test
    public void testGetInstalledPackages_WithApexFlag_ContainsNoDuplicates() {
        List<PackageInfo> packageInfos = mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(MATCH_APEX));
        final Set<String> apexPackageNames = packageInfos.stream()
                .filter(pi -> pi.isApex).map(pi -> pi.packageName).collect(Collectors.toSet());
        final Set<String> apkPackageNames = packageInfos.stream()
                .filter(pi -> !pi.isApex).map(pi -> pi.packageName).collect(Collectors.toSet());
        for (String packageName : apkPackageNames) {
            if (apexPackageNames.contains(packageName)) {
                expect.withMessage("Conflicting APK package " + packageName + " detected").fail();
            }
        }
    }

    /**
     * Test that the MATCH_FACTORY_ONLY flag doesn't add the same package multiple times since there
     * may be multiple versions of a system package on the device.
     */
    @Test
    public void testGetInstalledPackages_WithFactoryFlag_ContainsNoDuplicates() {
        final Set<String> packageNames = new HashSet<>();
        runTestWithFlags(PACKAGE_INFO_MATCH_FLAGS,
                flags -> testGetInstalledPackages_WithFactoryFlag_ContainsNoDuplicates(flags,
                        packageNames));
    }

    public void testGetInstalledPackages_WithFactoryFlag_ContainsNoDuplicates(int flags,
            Set<String> packageNames) {
        List<PackageInfo> packageInfos =
                mPackageManager.getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(flags | MATCH_FACTORY_ONLY));

        final Set<String> localPackageNames = new HashSet<>();
        for (PackageInfo pi : packageInfos) {
            final String packageName = pi.packageName;
            // Duplicate: already in local.
            // Dedup error messages: not in global.
            if (!localPackageNames.add(pi.packageName) && packageNames.add(packageName)) {
                expect.withMessage("Duplicate package " + packageName + " detected").fail();
            }
        }
    }

    @Test
    public void testInstallTestOnlyPackagePermission_onlyGrantedToShell() {
        List<PackageInfo> packages = mPackageManager.getPackagesHoldingPermissions(
                new String[]{INSTALL_TEST_ONLY_PACKAGE}, PackageManager.PackageInfoFlags.of(0));

        assertThat(packages).hasSize(1);
        assertThat(packages.get(0).packageName).isEqualTo(SHELL_PACKAGE_NAME);
    }

    @Test
    public void testInstall_withLongPackageName_fail() {
        assertThat(installPackage(LONG_PACKAGE_NAME_APK)).isFalse();
    }

    @Test
    public void testInstall_withLongSharedUserId_fail() {
        assertThat(installPackage(LONG_SHARED_USER_ID_APK)).isFalse();
    }

    @Test
    public void testInstall_withMaxPackageName_success() {
        assertThat(installPackage(MAX_PACKAGE_NAME_APK)).isTrue();
    }

    @Test
    public void testInstall_withMaxSharedUserId_success() {
        assertThat(installPackage(MAX_SHARED_USER_ID_APK)).isTrue();
    }

    @Test
    public void testInstall_withLongUsesPermissionName_fail() {
        String expectedErrorCode = "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";
        String expectedErrorMessage = "The name in the <uses-permission> is greater than 512";

        String installResult = installPackageWithResult(LONG_USES_PERMISSION_NAME_APK);

        assertThat(installResult.contains(expectedErrorCode)).isTrue();
        assertThat(installResult.contains(expectedErrorMessage)).isTrue();
    }

    private String installPackageWithResult(String apkPath) {
        return SystemUtil.runShellCommand("pm install -t " + apkPath);
    }

    private boolean installPackage(String apkPath) {
        return SystemUtil.runShellCommand(
                "pm install -t " + apkPath).equals("Success\n");
    }

    private boolean addSplitDontKill(String packageName, String splitPath) {
        return SystemUtil.runShellCommand(
                "pm install-streaming -p " + packageName + " --dont-kill -t " + splitPath).equals(
                "Success\n");
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    @Test
    public void testGetLaunchIntentSenderForPackage() throws Exception {
        final Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                LauncherMockActivity.class.getName(), null /* result */, false /* block */);
        mInstrumentation.addMonitor(monitor);

        try {
            final IntentSender intentSender = mPackageManager.getLaunchIntentSenderForPackage(
                    PACKAGE_NAME);
            assertThat(intentSender.getCreatorPackage()).isEqualTo(PACKAGE_NAME);
            assertThat(intentSender.getCreatorUid()).isEqualTo(mContext.getApplicationInfo().uid);

            sendIntent(intentSender);
            final Activity activity = monitor.waitForActivityWithTimeout(TIMEOUT_MS);
            assertThat(activity).isNotNull();
            activity.finish();
        } finally {
            mInstrumentation.removeMonitor(monitor);
        }
    }

    @Test(expected = IntentSender.SendIntentException.class)
    public void testGetLaunchIntentSenderForPackage_noMainActivity() throws Exception {
        assertThat(installPackage(EMPTY_APP_APK)).isTrue();
        final PackageInfo packageInfo = mPackageManager.getPackageInfo(EMPTY_APP_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0));
        assertThat(packageInfo.packageName).isEqualTo(EMPTY_APP_PACKAGE_NAME);
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(EMPTY_APP_PACKAGE_NAME);
        assertThat(mPackageManager.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0))).isEmpty();

        final IntentSender intentSender = mPackageManager.getLaunchIntentSenderForPackage(
                EMPTY_APP_PACKAGE_NAME);
        assertThat(intentSender.getCreatorPackage()).isEqualTo(PACKAGE_NAME);
        assertThat(intentSender.getCreatorUid()).isEqualTo(mContext.getApplicationInfo().uid);

        sendIntent(intentSender);
    }

    @Test(expected = IntentSender.SendIntentException.class)
    public void testGetLaunchIntentSenderForPackage_packageNotExist() throws Exception {
        try {
            mPackageManager.getPackageInfo(EMPTY_APP_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0));
            fail(EMPTY_APP_PACKAGE_NAME + " should not exist in the device");
        } catch (NameNotFoundException e) {
        }
        final IntentSender intentSender = mPackageManager.getLaunchIntentSenderForPackage(
                EMPTY_APP_PACKAGE_NAME);
        assertThat(intentSender.getCreatorPackage()).isEqualTo(PACKAGE_NAME);
        assertThat(intentSender.getCreatorUid()).isEqualTo(mContext.getApplicationInfo().uid);

        sendIntent(intentSender);
    }

    @Test
    public void testDefaultHomeActivity_doesntChange_whenInstallAnotherLauncher() throws Exception {
        final Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final String currentHomeActivity =
                mPackageManager.resolveActivity(homeIntent,
                        PackageManager.ResolveInfoFlags.of(0)).activityInfo.name;

        // Install another launcher app.
        assertThat(installPackage(MOCK_LAUNCHER_APK)).isTrue();

        // There is an async operation to re-set the default home activity in Role with no way
        // to listen for completion once a package installed, so poll until the default home
        // activity is set.
        PollingCheck.waitFor(() -> currentHomeActivity.equals(
                mPackageManager.resolveActivity(homeIntent,
                        PackageManager.ResolveInfoFlags.of(0)).activityInfo.name));
        final List<String> homeApps =
                mPackageManager.queryIntentActivities(homeIntent,
                                PackageManager.ResolveInfoFlags.of(0)).stream()
                        .map(i -> i.activityInfo.packageName).collect(Collectors.toList());
        assertThat(homeApps.contains(MOCK_LAUNCHER_PACKAGE_NAME)).isTrue();
    }

    @Test
    public void setComponentEnabledSetting_nonExistentPackage_withoutPermission() {
        final ComponentName componentName = ComponentName.createRelative(
                NON_EXISTENT_PACKAGE_NAME, "ClassName");
        assertThrows(SecurityException.class, () -> mPackageManager.setComponentEnabledSetting(
                componentName, COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));
    }

    @Test
    public void setComponentEnabledSetting_nonExistentPackage_hasPermission() {
        final ComponentName componentName = ComponentName.createRelative(
                NON_EXISTENT_PACKAGE_NAME, "ClassName");
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> mPackageManager.setComponentEnabledSetting(componentName,
                            COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void loadApplicationLabel_withLongLabelName_truncated() throws Exception {
        assertThat(installPackage(LONG_LABEL_NAME_APK)).isTrue();
        final ApplicationInfo info = mPackageManager.getApplicationInfo(
                EMPTY_APP_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
        final CharSequence resLabel = mPackageManager.getText(
                EMPTY_APP_PACKAGE_NAME, info.labelRes, info);

        assertThat(resLabel.length()).isGreaterThan(MAX_SAFE_LABEL_LENGTH);
        assertThat(info.loadLabel(mPackageManager).length()).isEqualTo(MAX_SAFE_LABEL_LENGTH);
    }

    @Test
    public void loadComponentLabel_withLongLabelName_truncated() throws Exception {
        assertThat(installPackage(LONG_LABEL_NAME_APK)).isTrue();
        final ComponentName componentName = ComponentName.createRelative(
                EMPTY_APP_PACKAGE_NAME, ".MockActivity");
        final ApplicationInfo appInfo = mPackageManager.getApplicationInfo(
                EMPTY_APP_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0));
        final ActivityInfo activityInfo = mPackageManager.getActivityInfo(
                componentName, PackageManager.ComponentInfoFlags.of(0));
        final CharSequence resLabel = mPackageManager.getText(
                EMPTY_APP_PACKAGE_NAME, activityInfo.labelRes, appInfo);

        assertThat(resLabel.length()).isGreaterThan(MAX_SAFE_LABEL_LENGTH);
        assertThat(activityInfo.loadLabel(mPackageManager).length())
                .isEqualTo(MAX_SAFE_LABEL_LENGTH);
    }

    @Test
    public void setComponentEnabledSettings_withDuplicatedComponent() {
        final List<ComponentEnabledSetting> enabledSettings = List.of(
                new ComponentEnabledSetting(
                        ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(
                        ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setComponentEnabledSettings(enabledSettings));
    }

    @Test
    public void setComponentEnabledSettings_flagDontKillAppConflict() {
        final List<ComponentEnabledSetting> enabledSettings = List.of(
                new ComponentEnabledSetting(
                        ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(
                        SERVICE_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, 0));

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setComponentEnabledSettings(enabledSettings));
    }

    @Test
    public void setComponentEnabledSettings_disableSelfAndStubApp_withoutPermission() {
        final List<ComponentEnabledSetting> enabledSettings = List.of(
                new ComponentEnabledSetting(
                        ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(
                        STUB_ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, 0));

        assertThrows(SecurityException.class,
                () -> mPackageManager.setComponentEnabledSettings(enabledSettings));
    }

    @Test
    public void setComponentEnabledSettings_disableSelf() throws Exception {
        final int activityState = mPackageManager.getComponentEnabledSetting(ACTIVITY_COMPONENT);
        final int serviceState = mPackageManager.getComponentEnabledSetting(SERVICE_COMPONENT);
        assertThat(activityState).isAnyOf(
                COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_ENABLED);
        assertThat(serviceState).isAnyOf(
                COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_ENABLED);

        try {
            final List<ComponentEnabledSetting> enabledSettings = List.of(
                    new ComponentEnabledSetting(
                            ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                    new ComponentEnabledSetting(
                            SERVICE_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
            setComponentEnabledSettingsAndWaitForBroadcasts(enabledSettings);
        } finally {
            final List<ComponentEnabledSetting> enabledSettings = List.of(
                    new ComponentEnabledSetting(ACTIVITY_COMPONENT, activityState, DONT_KILL_APP),
                    new ComponentEnabledSetting(SERVICE_COMPONENT, serviceState, DONT_KILL_APP));
            setComponentEnabledSettingsAndWaitForBroadcasts(enabledSettings);
        }
    }

    @Test
    public void setComponentEnabledSettings_disableSelfAndStubApp_killStubApp()
            throws Exception {
        final int activityState = mPackageManager.getComponentEnabledSetting(ACTIVITY_COMPONENT);
        final int stubState = mPackageManager.getComponentEnabledSetting(STUB_ACTIVITY_COMPONENT);
        assertThat(activityState).isAnyOf(
                COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_ENABLED);
        assertThat(stubState).isAnyOf(
                COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_ENABLED);

        final Intent intent = new Intent();
        intent.setComponent(STUB_SERVICE_COMPONENT);
        final AtomicBoolean killed = new AtomicBoolean();
        mServiceTestRule.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                killed.set(true);
            }
        }, Context.BIND_AUTO_CREATE);
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);

        try {
            final List<ComponentEnabledSetting> enabledSettings = List.of(
                    new ComponentEnabledSetting(
                            ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                    new ComponentEnabledSetting(
                            STUB_ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, 0));
            setComponentEnabledSettingsAndWaitForBroadcasts(enabledSettings);
            TestUtils.waitUntil("Waiting for the process " + STUB_PACKAGE_NAME
                    + " to die", () -> killed.get());
        } finally {
            final List<ComponentEnabledSetting> enabledSettings = List.of(
                    new ComponentEnabledSetting(ACTIVITY_COMPONENT, activityState, DONT_KILL_APP),
                    new ComponentEnabledSetting(STUB_ACTIVITY_COMPONENT, stubState, 0));
            setComponentEnabledSettingsAndWaitForBroadcasts(enabledSettings);
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void setComponentEnabledSettings_noStateChanged_noBroadcastReceived() {
        final int activityState = mPackageManager.getComponentEnabledSetting(ACTIVITY_COMPONENT);
        final int serviceState = mPackageManager.getComponentEnabledSetting(SERVICE_COMPONENT);
        final List<ComponentEnabledSetting> enabledSettings = List.of(
                new ComponentEnabledSetting(ACTIVITY_COMPONENT, activityState, DONT_KILL_APP),
                new ComponentEnabledSetting(SERVICE_COMPONENT, serviceState, DONT_KILL_APP));

        assertThrows(TimeoutException.class,
                () -> setComponentEnabledSettingsAndWaitForBroadcasts(enabledSettings));
    }

    @Test
    public void setComponentEnabledSetting_disableMultiplePackagesNoKill() throws Exception {
        final int activityState = mPackageManager.getComponentEnabledSetting(ACTIVITY_COMPONENT);
        final int serviceState = mPackageManager.getComponentEnabledSetting(SERVICE_COMPONENT);
        assertThat(installPackage(MOCK_LAUNCHER_APK)).isTrue();
        final List<ComponentEnabledSetting> settings = List.of(
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_ACTIVITY_COMPONENT,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_RECEIVER_COMPONENT,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_SERVICE_COMPONENT,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_PROVIDER_COMPONENT,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(
                        ACTIVITY_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP),
                new ComponentEnabledSetting(
                        SERVICE_COMPONENT, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));

        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
            setComponentEnabledSettingsAndWaitForBroadcasts(settings);
        } finally {
            final List<ComponentEnabledSetting> enabledSettings = List.of(
                    new ComponentEnabledSetting(ACTIVITY_COMPONENT, activityState, DONT_KILL_APP),
                    new ComponentEnabledSetting(SERVICE_COMPONENT, serviceState, DONT_KILL_APP));
            setComponentEnabledSettingsAndWaitForBroadcasts(enabledSettings);
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void clearApplicationUserData_resetComponentEnabledSettings() throws Exception {
        assertThat(installPackage(MOCK_LAUNCHER_APK)).isTrue();
        final List<ComponentEnabledSetting> settings = List.of(
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_ACTIVITY_COMPONENT,
                        COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */),
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_RECEIVER_COMPONENT,
                        COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */),
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_SERVICE_COMPONENT,
                        COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */),
                new ComponentEnabledSetting(RESET_ENABLED_SETTING_PROVIDER_COMPONENT,
                        COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));

        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
            // update component enabled settings
            setComponentEnabledSettingsAndWaitForBroadcasts(settings);

            clearApplicationUserData(MOCK_LAUNCHER_PACKAGE_NAME);

            assertThat(mPackageManager
                    .getComponentEnabledSetting(RESET_ENABLED_SETTING_ACTIVITY_COMPONENT))
                    .isEqualTo(COMPONENT_ENABLED_STATE_DEFAULT);
            assertThat(mPackageManager
                    .getComponentEnabledSetting(RESET_ENABLED_SETTING_RECEIVER_COMPONENT))
                    .isEqualTo(COMPONENT_ENABLED_STATE_DEFAULT);
            assertThat(mPackageManager
                    .getComponentEnabledSetting(RESET_ENABLED_SETTING_SERVICE_COMPONENT))
                    .isEqualTo(COMPONENT_ENABLED_STATE_DEFAULT);
            assertThat(mPackageManager
                    .getComponentEnabledSetting(RESET_ENABLED_SETTING_PROVIDER_COMPONENT))
                    .isEqualTo(COMPONENT_ENABLED_STATE_DEFAULT);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void setComponentEnabledSettingsAndWaitForBroadcasts(
            List<ComponentEnabledSetting> enabledSettings)
            throws InterruptedException, TimeoutException {
        final List<ComponentName> componentsToWait = enabledSettings.stream()
                .map(enabledSetting -> enabledSetting.getComponentName())
                .collect(Collectors.toList());
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        final CountDownLatch latch = new CountDownLatch(1 /* count */);
        final BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String packageName = intent.getData() != null
                        ? intent.getData().getSchemeSpecificPart() : null;
                final String[] receivedComponents = intent.getStringArrayExtra(
                        Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST);
                if (packageName == null || receivedComponents == null) {
                    return;
                }
                for (String componentString : receivedComponents) {
                    componentsToWait.remove(new ComponentName(packageName, componentString));
                }
                if (componentsToWait.isEmpty()) {
                    latch.countDown();
                }
            }
        };
        mContext.registerReceiver(br, filter, RECEIVER_EXPORTED);
        try {
            mPackageManager.setComponentEnabledSettings(enabledSettings);
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Package changed broadcasts for " + componentsToWait
                        + " not received in " + TIMEOUT_MS + "ms");
            }
            for (ComponentEnabledSetting enabledSetting : enabledSettings) {
                assertThat(mPackageManager.getComponentEnabledSetting(
                        enabledSetting.getComponentName()))
                        .isEqualTo(enabledSetting.getEnabledState());
            }
        } finally {
            mContext.unregisterReceiver(br);
        }
    }

    private void clearApplicationUserData(String packageName) {
        final StringBuilder cmd = new StringBuilder("pm clear --user ");
        cmd.append(UserHandle.myUserId()).append(" ");
        cmd.append(packageName);
        SystemUtil.runShellCommand(cmd.toString());
    }

    @Test
    public void testPrebuiltSharedLibraries_existOnDevice() {
        final List<SharedLibraryInfo> infos =
                mPackageManager.getSharedLibraries(PackageManager.PackageInfoFlags.of(0)).stream()
                        .filter(info -> info.isBuiltin() && !info.isNative())
                        .collect(Collectors.toList());
        assertThat(infos).isNotEmpty();

        final List<SharedLibraryInfo> fileNotExistInfos = infos.stream()
                .filter(info -> !(new File(info.getPath()).exists())).collect(
                        Collectors.toList());
        assertThat(fileNotExistInfos).isEmpty();
    }

    @Test
    public void testInstallUpdate_applicationIsKilled() throws Exception {
        final Intent intent = new Intent();
        intent.setComponent(STUB_SERVICE_COMPONENT);
        final AtomicBoolean killed = new AtomicBoolean();
        mServiceTestRule.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                killed.set(true);
            }
        }, Context.BIND_AUTO_CREATE);

        installPackage(STUB_PACKAGE_APK);
        // The application should be killed after updating.
        TestUtils.waitUntil("Waiting for the process " + STUB_PACKAGE_NAME + " to die",
                10 /* timeoutSecond */, () -> killed.get());
    }

    @Test
    public void testInstallUpdate_dontKill_applicationIsNotKilled() throws Exception {
        installPackage(STUB_PACKAGE_APK);

        final Intent intent = new Intent();
        intent.setComponent(STUB_SERVICE_COMPONENT);
        final AtomicBoolean killed = new AtomicBoolean();
        mServiceTestRule.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                killed.set(true);
            }
        }, Context.BIND_AUTO_CREATE);

        addSplitDontKill(STUB_PACKAGE_NAME, STUB_PACKAGE_SPLIT);
        // The application shouldn't be killed after updating with --dont-kill.
        assertThrows(AssertionFailedError.class,
                () -> TestUtils.waitUntil(
                        "Waiting for the process " + STUB_PACKAGE_NAME + " to die",
                        10 /* timeoutSecond */, () -> killed.get()));
    }

    @Test
    public void testPackageInfoFlags() {
        final long rawFlags = PackageManager.GET_ACTIVITIES | PackageManager.GET_GIDS
                | PackageManager.GET_CONFIGURATIONS;
        assertEquals(rawFlags, PackageManager.PackageInfoFlags.of(rawFlags).getValue());
    }

    @Test
    public void testApplicationInfoFlags() {
        final long rawFlags = PackageManager.GET_SHARED_LIBRARY_FILES
                | PackageManager.MATCH_UNINSTALLED_PACKAGES;
        assertEquals(rawFlags, PackageManager.ApplicationInfoFlags.of(rawFlags).getValue());
    }

    @Test
    public void testResolveInfoFlags() {
        final long rawFlags = PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                | PackageManager.MATCH_SYSTEM_ONLY;
        assertEquals(rawFlags, PackageManager.ResolveInfoFlags.of(rawFlags).getValue());
    }

    @Test
    public void testComponentInfoFlags() {
        final long rawFlags = PackageManager.GET_META_DATA;
        assertEquals(rawFlags, PackageManager.ComponentInfoFlags.of(rawFlags).getValue());
    }

    @Test
    public void testDeleteDexopt_withoutShellIdentity() throws Exception {
        assertThat(runCommand("pm delete-dexopt " + PACKAGE_NAME))
                .contains(SecurityException.class.getName());
    }

    @Test
    public void testSettingAndReserveCopyVerityProtected() throws Exception {
        File systemDir = new File(Environment.getDataDirectory(), "system");
        File settings = new File(systemDir, "packages.xml");
        File settingsReserveCopy = new File(systemDir, "packages.xml.reservecopy");

        // Primary.
        assertTrue(settings.exists());
        // Reserve copy.
        assertTrue(settingsReserveCopy.exists());
        // Temporary backup.
        assertFalse(new File(systemDir, "packages-backup.xml").exists());

        assumeTrue(VerityUtils.isFsVeritySupported());
        assertTrue(VerityUtils.hasFsverity(settings.getAbsolutePath()));
        assertTrue(VerityUtils.hasFsverity(settingsReserveCopy.getAbsolutePath()));
    }

    private static String runCommand(String cmd) throws Exception {
        final Process process = Runtime.getRuntime().exec(cmd);
        final StringBuilder output = new StringBuilder();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.lines().forEach(line -> output.append(line));
        reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        reader.lines().forEach(line -> output.append(line));
        process.waitFor();
        return output.toString();
    }

    @Test
    public void testNewAppInstalledNotificationEnabled() {
        SystemUtil.runWithShellPermissionIdentity(mInstrumentation.getUiAutomation(), () -> {
            Settings.Global.putString(mContext.getContentResolver(),
                    Settings.Global.SHOW_NEW_APP_INSTALLED_NOTIFICATION_ENABLED, "1" /* true */);
        }, WRITE_SECURE_SETTINGS);

        assertEquals(true, mPackageManager.shouldShowNewAppInstalledNotification());

    }

    @Test
    public void testCanUserUninstall_setToTrue_returnsTrue() throws RemoteException {
        SystemUtil.runWithShellPermissionIdentity(mInstrumentation.getUiAutomation(), () -> {
            IPackageManager iPm = ActivityThread.getPackageManager();
            iPm.setBlockUninstallForUser(PACKAGE_NAME, true, USER_CURRENT);
        }, DELETE_PACKAGES);

        assertEquals(true, mPackageManager.canUserUninstall(PACKAGE_NAME, CURRENT));
    }

    private void sendIntent(IntentSender intentSender) throws IntentSender.SendIntentException {
        intentSender.sendIntent(mContext, 0 /* code */, null /* intent */,
                null /* onFinished */, null /* handler */, null /* requiredPermission */,
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());
    }
}
