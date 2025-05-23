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

package android.telephony.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "android.telephony.cts.SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        Log.i(TAG, "onReceive intent action " + intent.getAction());
        switch (intent.getAction()) {
            case Telephony.Sms.Intents.SMS_DELIVER_ACTION: {
                // Send broadcast for SmsManagerTest cases
                context.sendBroadcast(new Intent(SmsManagerTest.SMS_DELIVER_DEFAULT_APP_ACTION));
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages != null && messages[0] != null) {
                    AsyncSmsMessageListener.getInstance().offerSmsMessage(
                            messages[0].getMessageBody());
                }
                break;
            }
            case SmsReceiverHelper.MESSAGE_SENT_ACTION: {
                intent.putExtra(SmsReceiverHelper.EXTRA_RESULT_CODE, getResultCode());
                AsyncSmsMessageListener.getInstance().offerMessageSentIntent(intent);
                break;
            }
            case SmsReceiverHelper.MESSAGE_DELIVERED_ACTION: {
                intent.putExtra(SmsReceiverHelper.EXTRA_RESULT_CODE, getResultCode());
                AsyncSmsMessageListener.getInstance().offerMessageDeliveredIntent(intent);
                break;
            }
        }
    }
}
