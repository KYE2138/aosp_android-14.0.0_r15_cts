/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ToggleButton;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Activity for testing that Bluetooth can be disabled and enabled properly. The activity shows
 * a button that toggles Bluetooth by disabling it via {@link BluetoothAdapter#disable()} and
 * enabling it via the Intent action {@link BluetoothAdapter#ACTION_REQUEST_ENABLE}.
 */
public class BluetoothToggleActivity extends PassFailButtons.Activity {

    private static final String TAG = BluetoothToggleActivity.class.getName();

    private static final int START_ENABLE_BLUETOOTH_REQUEST = 1;
    private static final int START_DISABLE_BLUETOOTH_REQUEST = 2;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothBroadcastReceiver mReceiver;

    private ToggleButton mToggleButton;

    private int mNumDisabledTimes = 0;

    private int mNumEnabledTimes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bt_toggle);
        setPassFailButtonClickListeners();

        mReceiver = new BluetoothBroadcastReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter, RECEIVER_EXPORTED);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        getPassButton().setEnabled(false);

        mToggleButton = (ToggleButton) findViewById(R.id.bt_toggle_button);
        mToggleButton.setChecked(mBluetoothAdapter.isEnabled());
        mToggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mToggleButton.isChecked()) {
                    enableBluetooth();
                } else {
                    disableBluetooth();
                }
            }
        });
    }

    private void enableBluetooth() {
        mToggleButton.setEnabled(false);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, START_ENABLE_BLUETOOTH_REQUEST);
    }

    private void disableBluetooth() {
        mToggleButton.setEnabled(false);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISABLE);
        startActivityForResult(intent, START_DISABLE_BLUETOOTH_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case START_ENABLE_BLUETOOTH_REQUEST:
                boolean enabledBluetooth = RESULT_OK == resultCode;
                mToggleButton.setChecked(enabledBluetooth);
                mToggleButton.setEnabled(true);
                break;
            case START_DISABLE_BLUETOOTH_REQUEST:
                boolean disabledBluetooth = RESULT_OK == resultCode;
                mToggleButton.setChecked(!disabledBluetooth);
                mToggleButton.setEnabled(true);
                break;
        }
    }


    class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
            int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            Log.i(TAG, "Previous state: " + previousState + " New state: " + newState);

            if (BluetoothAdapter.STATE_OFF == newState
                    && (BluetoothAdapter.STATE_ON == previousState
                            || BluetoothAdapter.STATE_TURNING_OFF == previousState)) {
                mNumDisabledTimes++;
            }

            if (BluetoothAdapter.STATE_ON == newState
                    && (BluetoothAdapter.STATE_OFF == previousState
                            || BluetoothAdapter.STATE_TURNING_ON == previousState)) {
                mNumEnabledTimes++;
            }

            mToggleButton.setChecked(mBluetoothAdapter.isEnabled());
            getPassButton().setEnabled(mNumDisabledTimes > 0 &&  mNumEnabledTimes > 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
