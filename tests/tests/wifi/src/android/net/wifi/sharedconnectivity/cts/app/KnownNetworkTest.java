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

import static android.net.wifi.WifiInfo.SECURITY_TYPE_PSK;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_WEP;
import static android.net.wifi.sharedconnectivity.app.KnownNetwork.NETWORK_SOURCE_CLOUD_SELF;
import static android.net.wifi.sharedconnectivity.app.KnownNetwork.NETWORK_SOURCE_NEARBY_SELF;
import static android.net.wifi.sharedconnectivity.app.NetworkProviderInfo.DEVICE_TYPE_PHONE;
import static android.net.wifi.sharedconnectivity.app.NetworkProviderInfo.DEVICE_TYPE_TABLET;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.util.ArraySet;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;

import java.util.Arrays;

/**
 * CTS tests for {@link KnownNetwork}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@NonMainlineTest
public class KnownNetworkTest {

    private static final int NETWORK_SOURCE = NETWORK_SOURCE_NEARBY_SELF;
    private static final String SSID = "TEST_SSID";
    private static final int[] SECURITY_TYPES = {SECURITY_TYPE_WEP};
    private static final NetworkProviderInfo NETWORK_PROVIDER_INFO =
            new NetworkProviderInfo.Builder("TEST_NAME", "TEST_MODEL")
                    .setDeviceType(DEVICE_TYPE_TABLET).setConnectionStrength(2)
                    .setBatteryPercentage(50).build();
    private static final String BUNDLE_KEY = "INT-KEY";
    private static final int BUNDLE_VALUE = 1;

    private static final int NETWORK_SOURCE_1 = NETWORK_SOURCE_CLOUD_SELF;
    private static final String SSID_1 = "TEST_SSID1";
    private static final int[] SECURITY_TYPES_1 = {SECURITY_TYPE_PSK};
    private static final NetworkProviderInfo NETWORK_PROVIDER_INFO1 =
            new NetworkProviderInfo.Builder("TEST_NAME_1", "TEST_MODEL_1")
                    .setDeviceType(DEVICE_TYPE_PHONE).setConnectionStrength(3)
                    .setBatteryPercentage(33).build();

    @Test
    public void parcelOperation() {
        KnownNetwork network = buildKnownNetworkBuilder().build();

        Parcel parcelW = Parcel.obtain();
        network.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        KnownNetwork fromParcel = KnownNetwork.CREATOR.createFromParcel(parcelR);

        assertThat(fromParcel).isEqualTo(network);
        assertThat(fromParcel.hashCode()).isEqualTo(network.hashCode());
    }
    @Test
    public void parcelOperation_noNetworkProviderInfo() {
        KnownNetwork network = buildKnownNetworkBuilder().setNetworkProviderInfo(null)
                .setNetworkSource(NETWORK_SOURCE_CLOUD_SELF).build();

        Parcel parcelW = Parcel.obtain();
        network.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        KnownNetwork fromParcel = KnownNetwork.CREATOR.createFromParcel(parcelR);

        assertThat(fromParcel).isEqualTo(network);
        assertThat(fromParcel.hashCode()).isEqualTo(network.hashCode());
    }

    @Test
    public void equalsOperation() {
        KnownNetwork network1 = buildKnownNetworkBuilder().build();
        KnownNetwork network2 = buildKnownNetworkBuilder().build();
        assertThat(network1).isEqualTo(network2);

        KnownNetwork.Builder builder = buildKnownNetworkBuilder()
                .setNetworkSource(NETWORK_SOURCE_1);
        assertThat(builder.build()).isNotEqualTo(network1);

        builder = buildKnownNetworkBuilder().setSsid(SSID_1);
        assertThat(builder.build()).isNotEqualTo(network1);

        builder = buildKnownNetworkBuilder();
        Arrays.stream(SECURITY_TYPES_1).forEach(builder::addSecurityType);
        assertThat(builder.build()).isNotEqualTo(network1);

        builder = buildKnownNetworkBuilder().setNetworkProviderInfo(NETWORK_PROVIDER_INFO1);
        assertThat(builder.build()).isNotEqualTo(network1);
    }

    /**
     * Verifies the get methods return the expected data.
     */
    @Test
    public void testGetMethods() {
        KnownNetwork network = buildKnownNetworkBuilder().build();
        KnownNetwork network1 = buildKnownNetworkBuilder().setNetworkProviderInfo(null)
                .setNetworkSource(NETWORK_SOURCE_CLOUD_SELF).build();
        ArraySet<Integer> securityTypes = new ArraySet<>();
        Arrays.stream(SECURITY_TYPES).forEach(securityTypes::add);

        assertThat(network.getNetworkSource()).isEqualTo(NETWORK_SOURCE);
        assertThat(network.getSsid()).isEqualTo(SSID);
        assertThat(network.getSecurityTypes()).containsExactlyElementsIn(securityTypes);
        assertThat(network.getNetworkProviderInfo()).isEqualTo(NETWORK_PROVIDER_INFO);
        assertThat(network.getExtras().getInt(BUNDLE_KEY)).isEqualTo(BUNDLE_VALUE);
        assertThat(network1.getNetworkProviderInfo()).isNull();
    }

    @Test
    public void hashCodeCalculation() {
        KnownNetwork network1 = buildKnownNetworkBuilder().build();
        KnownNetwork network2 = buildKnownNetworkBuilder().build();

        assertThat(network1.hashCode()).isEqualTo(network2.hashCode());
    }

    @Test
    public void hashCodeCalculation_noNetworkProviderInfo() {
        KnownNetwork network1 = buildKnownNetworkBuilder().setNetworkProviderInfo(null)
                .setNetworkSource(NETWORK_SOURCE_CLOUD_SELF).build();
        KnownNetwork network2 = buildKnownNetworkBuilder().setNetworkProviderInfo(null)
                .setNetworkSource(NETWORK_SOURCE_CLOUD_SELF).build();

        assertThat(network1.hashCode()).isEqualTo(network2.hashCode());
    }

    @Test
    public void illegalNetworkSourceValueIsSet_shouldThrowException() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder();
        builder.setNetworkSource(1000);

        Exception e = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(e.getMessage()).contains("Illegal network source");
    }

    @Test
    public void ssidNotSet_shouldThrowException() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder();
        builder.setNetworkSource(NETWORK_SOURCE);

        Exception e = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(e.getMessage()).contains("SSID must be set");
    }

    @Test
    public void securityTypesNotSet_shouldThrowException() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder();
        builder.setNetworkSource(NETWORK_SOURCE).setSsid(SSID);

        Exception e = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(e.getMessage()).contains("SecurityTypes must be set");
    }

    @Test
    public void networkProviderInfoNotSetWhenNetworkSourceIsNearbySelf_shouldThrowException() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder();
        builder.setNetworkSource(NETWORK_SOURCE_NEARBY_SELF).setSsid(SSID);
        Arrays.stream(SECURITY_TYPES).forEach(builder::addSecurityType);

        Exception e = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(e.getMessage()).contains("Device info must be provided");
    }

    private KnownNetwork.Builder buildKnownNetworkBuilder() {
        KnownNetwork.Builder builder = new KnownNetwork.Builder().setNetworkSource(NETWORK_SOURCE)
                .setSsid(SSID).setNetworkProviderInfo(NETWORK_PROVIDER_INFO)
                .setExtras(buildBundle());
        Arrays.stream(SECURITY_TYPES).forEach(builder::addSecurityType);
        return builder;
    }

    private Bundle buildBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(BUNDLE_KEY, BUNDLE_VALUE);
        return bundle;
    }
}
