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
 * limitations under the License.
 */

package android.telecom.cts;

import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Process;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.cts.MockMissedCallNotificationReceiver.IntentListener;
import android.util.Log;

public class MissedCallTest extends BaseTelecomTestWithMockServices {

    private RoleManager mRoleManager;
    TestUtils.InvokeCounter mShowMissedCallNotificationIntentCounter =
            new TestUtils.InvokeCounter("ShowMissedCallNotificationIntent");

    private static final String CMD_DEVICE_IDLE_TEMP_EXEMPTIONS = "cmd deviceidle tempwhitelist";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) return;
        mContext = getInstrumentation().getContext();
        mRoleManager = mContext.getSystemService(RoleManager.class);

        MockMissedCallNotificationReceiver.setIntentListener(new IntentListener() {
            @Override
            public void onIntentReceived(Intent intent) {
                Log.i(TestUtils.TAG, intent.toString());
                if (TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION
                        .equals(intent.getAction())) {
                    mShowMissedCallNotificationIntentCounter.invoke();
                }
            }
        });
    }

    @Override
    public void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            MockMissedCallNotificationReceiver.setIntentListener(null);
        }
        super.tearDown();
    }

    public void testMissedCall_NotifyDialer() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();

        final Call call = inCallService.getLastCall();

        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        connection.setDisconnected(new DisconnectCause(DisconnectCause.MISSED));
        connection.destroy();
        mShowMissedCallNotificationIntentCounter.waitForCount(1);
        if (mRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            assertTrue("After missing a call, if the default dialer is handling the missed call "
                            + "notification, then it must be in the temporary power exemption "
                            + "list.",
                    isOnTemporaryPowerExemption());
        }
    }

    private boolean isOnTemporaryPowerExemption() throws Exception {
        String exemptions = TestUtils.executeShellCommand(
                getInstrumentation(), CMD_DEVICE_IDLE_TEMP_EXEMPTIONS);
        // Just check that this process's UID is in the result.
        return exemptions.contains(String.valueOf(Process.myUid()));
    }
}
