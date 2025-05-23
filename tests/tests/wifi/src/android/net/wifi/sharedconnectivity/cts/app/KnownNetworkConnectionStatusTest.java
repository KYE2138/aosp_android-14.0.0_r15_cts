/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.sharedconnectivity.cts.app;

import static android.net.wifi.WifiInfo.SECURITY_TYPE_WEP;
import static android.net.wifi.sharedconnectivity.app.KnownNetwork.NETWORK_SOURCE_NEARBY_SELF;
import static android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus.CONNECTION_STATUS_SAVED;
import static android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus.CONNECTION_STATUS_SAVE_FAILED;
import static android.net.wifi.sharedconnectivity.app.NetworkProviderInfo.DEVICE_TYPE_TABLET;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;

import java.util.Arrays;

/**
 * CTS tests for {@link KnownNetworkConnectionStatus}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@NonMainlineTest
public class KnownNetworkConnectionStatusTest {
    private static final int NETWORK_SOURCE = NETWORK_SOURCE_NEARBY_SELF;
    private static final String SSID = "TEST_SSID";
    private static final int[] SECURITY_TYPES = {SECURITY_TYPE_WEP};
    private static final NetworkProviderInfo NETWORK_PROVIDER_INFO =
            new NetworkProviderInfo.Builder("TEST_NAME", "TEST_MODEL")
                    .setDeviceType(DEVICE_TYPE_TABLET).setConnectionStrength(2)
                    .setBatteryPercentage(50).build();
    private static final String SSID_1 = "TEST_SSID1";
    private static final String BUNDLE_KEY = "INT-KEY";
    private static final int BUNDLE_VALUE = 1;

    @Test
    public void parcelOperation() {
        KnownNetworkConnectionStatus status = buildConnectionStatusBuilder().build();

        Parcel parcelW = Parcel.obtain();
        status.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        KnownNetworkConnectionStatus fromParcel =
                KnownNetworkConnectionStatus.CREATOR.createFromParcel(parcelR);

        assertThat(fromParcel).isEqualTo(status);
        assertThat(fromParcel.hashCode()).isEqualTo(status.hashCode());
    }

    @Test
    public void equalsOperation() {
        KnownNetworkConnectionStatus status1 = buildConnectionStatusBuilder().build();
        KnownNetworkConnectionStatus status2 = buildConnectionStatusBuilder().build();
        assertThat(status1).isEqualTo(status2);

        KnownNetworkConnectionStatus.Builder builder = buildConnectionStatusBuilder()
                .setStatus(CONNECTION_STATUS_SAVE_FAILED);
        assertThat(builder.build()).isNotEqualTo(status1);

        builder = buildConnectionStatusBuilder()
                .setKnownNetwork(buildKnownNetworkBuilder().setSsid(SSID_1).build());
        assertThat(builder.build()).isNotEqualTo(status1);
    }

    @Test
    public void getMethods() {
        KnownNetworkConnectionStatus status = buildConnectionStatusBuilder().build();
        assertThat(status.getStatus()).isEqualTo(CONNECTION_STATUS_SAVED);
        assertThat(status.getKnownNetwork()).isEqualTo(buildKnownNetworkBuilder().build());
        assertThat(status.getExtras().getInt(BUNDLE_KEY)).isEqualTo(BUNDLE_VALUE);
    }

    @Test
    public void hashCodeCalculation() {
        KnownNetworkConnectionStatus status1 = buildConnectionStatusBuilder().build();
        KnownNetworkConnectionStatus status2 = buildConnectionStatusBuilder().build();

        assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
    }

    @Test
    public void illegalStatusValueIsSet_shouldThrowException() {
        KnownNetworkConnectionStatus.Builder builder = buildConnectionStatusBuilder();
        builder.setStatus(1000);

        Exception e = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(e.getMessage()).contains("Illegal connection status");
    }

    private KnownNetworkConnectionStatus.Builder buildConnectionStatusBuilder() {
        return new KnownNetworkConnectionStatus.Builder()
                .setStatus(CONNECTION_STATUS_SAVED)
                .setKnownNetwork(buildKnownNetworkBuilder().build())
                .setExtras(buildBundle());
    }

    private Bundle buildBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(BUNDLE_KEY, BUNDLE_VALUE);
        return bundle;
    }

    private KnownNetwork.Builder buildKnownNetworkBuilder() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder().setNetworkSource(NETWORK_SOURCE)
                .setSsid(SSID).setNetworkProviderInfo(NETWORK_PROVIDER_INFO);
        Arrays.stream(SECURITY_TYPES).forEach(builder::addSecurityType);
        return builder;
    }

}
