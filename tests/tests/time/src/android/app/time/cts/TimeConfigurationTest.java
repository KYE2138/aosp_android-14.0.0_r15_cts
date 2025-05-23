/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.time.cts;

import static android.app.time.cts.ParcelableTestSupport.assertRoundTripParcelable;

import static com.google.common.truth.Truth.assertThat;

import android.app.time.TimeConfiguration;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TimeConfigurationTest {

    @Test
    public void testBuilder() {
        TimeConfiguration first = new TimeConfiguration.Builder()
                .setAutoDetectionEnabled(true)
                .build();

        assertThat(first.isAutoDetectionEnabled()).isTrue();

        TimeConfiguration copyFromBuilderConfiguration = new TimeConfiguration.Builder(first)
                .build();

        assertThat(first).isEqualTo(copyFromBuilderConfiguration);
    }

    @Test
    public void testParcelable() {
        TimeConfiguration.Builder builder = new TimeConfiguration.Builder();

        assertRoundTripParcelable(builder.setAutoDetectionEnabled(true).build());

        assertRoundTripParcelable(builder.setAutoDetectionEnabled(false).build());
    }

}
