/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.backgroundactivity.appa;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

/**
 * A background activity that will be launched, for testing if app is able to start background
 * activity.
 */
public class BackgroundActivity extends Activity {

    public static final String TAG = "BackgroundActivity";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Log.i(TAG, "onCreate(" + Process.myUserHandle() + ")");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume(" + Process.myUserHandle() + ")");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause(" + Process.myUserHandle() + ")");
    }
}
