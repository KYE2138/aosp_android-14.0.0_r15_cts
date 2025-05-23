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

package com.android.cts.verifier.bluetooth;

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ListView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BleConnectionPriorityClientBaseActivity extends PassFailButtons.Activity {

    public static final int DISABLE_ADAPTER = 0;

    private TestAdapter mTestAdapter;
    private boolean mPassed = false;
    private Dialog mDialog;

    private static final int BLE_CONNECTION_UPDATE = 0;
    public static final String TAG = BleConnectionPriorityClientBaseActivity.class.getSimpleName();

    private static final int ALL_PASSED = 0x1;

    private boolean mSecure;

    private Handler mHandler;
    private int mCurrentTest = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_connection_priority_client_test);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.ble_connection_priority_client_name,
                R.string.ble_connection_priority_client_info,
                -1);
        getPassButton().setEnabled(false);

        mHandler = new Handler();

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = (ListView) findViewById(R.id.ble_client_connection_tests);
        listView.setAdapter(mTestAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleConnectionPriorityClientService.ACTION_BLUETOOTH_DISABLED);
        filter.addAction(BleConnectionPriorityClientService.ACTION_CONNECTION_SERVICES_DISCOVERED);
        filter.addAction(BleConnectionPriorityClientService.ACTION_CONNECTION_PRIORITY_FINISH);
        filter.addAction(BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_SECURE);
        filter.addAction(BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_INSECURE);
        filter.addAction(BleConnectionPriorityClientService.ACTION_FINISH_DISCONNECT);
        registerReceiver(mBroadcast, filter, RECEIVER_EXPORTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcast);
        closeDialog();
    }

    protected void setSecure(boolean secure) {
        mSecure = secure;
    }

    public boolean isSecure() {
        return mSecure;
    }

    private synchronized void closeDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    private synchronized void showProgressDialog() {
        closeDialog();

        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setTitle(R.string.ble_test_running);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(getString(R.string.ble_test_running_message));
        dialog.setCanceledOnTouchOutside(false);
        mDialog = dialog;
        mDialog.show();
    }

    private void showErrorDialog(int titleId, int messageId, boolean finish) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this).setTitle(titleId).setMessage(messageId);
        if (finish) {
            builder.setOnCancelListener(
                    new Dialog.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }
                    });
        }
        builder.create().show();
    }

    private List<Integer> setupTestList() {
        ArrayList<Integer> testList = new ArrayList<Integer>();
        testList.add(R.string.ble_connection_priority_client_description);
        return testList;
    }

    private void executeNextTest(long delay) {
        mHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        executeNextTestImpl();
                    }
                },
                delay);
    }

    private void executeNextTestImpl() {
        switch (mCurrentTest) {
            case -1:
            {
                mCurrentTest = BLE_CONNECTION_UPDATE;
                Intent intent = new Intent(this, BleConnectionPriorityClientService.class);
                intent.setAction(
                        BleConnectionPriorityClientService.ACTION_CONNECTION_PRIORITY_START);
                startService(intent);
                String msg = getString(R.string.ble_client_connection_priority);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                break;
            }
            case BLE_CONNECTION_UPDATE:
            {
                // all test done
                closeDialog();
                if (mPassed == true) {
                    Intent intent = new Intent(this, BleConnectionPriorityClientService.class);
                    intent.setAction(BleConnectionPriorityClientService.ACTION_DISCONNECT);
                    startService(intent);
                }
                break;
            }
            default:
                // something went wrong
                closeDialog();
                break;
        }
    }

    public boolean shouldRebootBluetoothAfterTest() {
        return false;
    }

    private BroadcastReceiver mBroadcast =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    switch (action) {
                        case BleConnectionPriorityClientService.ACTION_BLUETOOTH_DISABLED:
                            new AlertDialog.Builder(context)
                                    .setTitle(R.string.ble_bluetooth_disable_title)
                                    .setMessage(R.string.ble_bluetooth_disable_message)
                                    .setOnCancelListener(
                                            new Dialog.OnCancelListener() {
                                                @Override
                                                public void onCancel(DialogInterface dialog) {
                                                    finish();
                                                }
                                            })
                                    .create()
                                    .show();
                            break;
                        case BleConnectionPriorityClientService
                                .ACTION_CONNECTION_SERVICES_DISCOVERED:
                            showProgressDialog();
                            executeNextTest(3000);
                            break;
                        case BleConnectionPriorityClientService.ACTION_CONNECTION_PRIORITY_FINISH:
                            mTestAdapter.setTestPass(BLE_CONNECTION_UPDATE);
                            mPassed = true;
                            executeNextTest(1000);
                            break;
                        case BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_SECURE:
                            showErrorDialog(
                                    R.string.ble_bluetooth_mismatch_title,
                                    R.string.ble_bluetooth_mismatch_secure_message,
                                    true);
                            break;
                        case BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_INSECURE:
                            showErrorDialog(
                                    R.string.ble_bluetooth_mismatch_title,
                                    R.string.ble_bluetooth_mismatch_insecure_message,
                                    true);
                            break;
                        case BleConnectionPriorityClientService.ACTION_FINISH_DISCONNECT:
                            if (shouldRebootBluetoothAfterTest()) {
                                mBtPowerSwitcher.executeSwitching();
                            } else {
                                getPassButton().setEnabled(true);
                            }
                            break;
                    }
                    mTestAdapter.notifyDataSetChanged();
                }
            };

    private final BluetoothPowerSwitcher mBtPowerSwitcher = new BluetoothPowerSwitcher();

    private class BluetoothPowerSwitcher extends BroadcastReceiver {

        private boolean mIsSwitching = false;

        private class BluetoothHandler extends Handler {
            BluetoothHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case BleConnectionPriorityClientBaseActivity.DISABLE_ADAPTER:
                        mIsSwitching = false;
                        getPassButton().setEnabled(true);
                        closeDialog();
                        break;
                }
            }
        }

        public void executeSwitching() {
            mHandler = new BluetoothHandler(Looper.getMainLooper());
            if (!mIsSwitching) {
                mIsSwitching = true;
                Message msg =
                        mHandler.obtainMessage(
                                BleConnectionPriorityClientBaseActivity.DISABLE_ADAPTER);
                mHandler.sendMessageDelayed(msg, 5000);
                showProgressDialog();
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {}
    }
}
