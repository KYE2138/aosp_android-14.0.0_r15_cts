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
package com.android.compatibility.common.util;

import android.content.Context;
import android.provider.Settings;

/**
 * JUnit rule used to restore a {@link Settings} preference after the test is run.
 *
 * <p>It stores the current value before the test, and restores it after the test (if necessary).
 */
public class SettingsStateKeeperRule extends StateKeeperRule<String> {

    /**
     * Default constructor.
     *
     * @param context context used to retrieve the {@link Settings} provider.
     * @param key preference key.
     */
    public SettingsStateKeeperRule(Context context, String key) {
        super(new SettingsStateManager(context, key));
    }
}
