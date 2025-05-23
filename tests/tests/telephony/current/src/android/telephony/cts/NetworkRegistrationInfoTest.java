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
 * limitations under the License.
 */
package android.telephony.cts;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityLte;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;

import androidx.test.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;

public class NetworkRegistrationInfoTest {

    private static final String RETURN_REGISTRATION_STATE_EMERGENCY_STRING =
            "RETURN_REGISTRATION_STATE_EMERGENCY";

    @Test
    public void testDescribeContents() {
        NetworkRegistrationInfo networkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .build();
        assertEquals(0, networkRegistrationInfo.describeContents());
    }

    @Test
    public void testEquals() {
        NetworkRegistrationInfo nri1 = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .setEmergencyOnly(false)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .build();

        NetworkRegistrationInfo nri2 = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .setEmergencyOnly(false)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .build();

        NetworkRegistrationInfo nri3 = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setEmergencyOnly(false)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .build();

        assertEquals(nri1.hashCode(), nri2.hashCode());
        assertEquals(nri1, nri2);

        assertNotSame(nri1.hashCode(), nri3.hashCode());
        assertNotSame(nri1, nri3);

        assertNotSame(nri2.hashCode(), nri3.hashCode());
        assertNotSame(nri2, nri3);
    }

    @Test
    public void testGetAccessNetworkTechnology() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_EHRPD)
                .build();
        assertEquals(TelephonyManager.NETWORK_TYPE_EHRPD, nri.getAccessNetworkTechnology());
    }

    @Test
    public void testGetAvailableServices() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAvailableServices(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA,
                        NetworkRegistrationInfo.SERVICE_TYPE_VIDEO))
                .build();
        assertEquals(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA,
                NetworkRegistrationInfo.SERVICE_TYPE_VIDEO), nri.getAvailableServices());
    }

    @Test
    public void testGetEmergencyServices() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAvailableServices(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY,
                        NetworkRegistrationInfo.SERVICE_TYPE_VOICE))
                .build();
        assertEquals(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY,
                NetworkRegistrationInfo.SERVICE_TYPE_VOICE), nri.getAvailableServices());
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#isSearching()} does not throw any
     * exception.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testNetworkRegistrationInfoIsSearching() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(
                    NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING)
                .build();
        assertTrue(nri.isSearching());
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#isNetworkSearching()} does not throw any
     * exception.
     */
    @Test
    public void testNetworkRegistrationInfoIsNetworkSearching() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING)
                .build();
        assertTrue(nri.isNetworkSearching());
    }

    @Test
    public void testGetDomain() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build();
        assertEquals(NetworkRegistrationInfo.DOMAIN_CS, nri.getDomain());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRegistrationState() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        assertEquals(NetworkRegistrationInfo.REGISTRATION_STATE_HOME, nri.getRegistrationState());
    }

    @Test
    public void testGetNetworkRegistrationState() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        nri.setRoamingType(NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        assertEquals(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING,
                nri.getNetworkRegistrationState());
    }

    @Test
    public void testIsNetworkRoaming() {
        NetworkRegistrationInfo nriNetworkRoaming = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        nriNetworkRoaming.setRoamingType(NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        assertTrue(nriNetworkRoaming.isNetworkRoaming());

        NetworkRegistrationInfo nriNetworkHome = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        nriNetworkHome.setRoamingType(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        assertFalse(nriNetworkHome.isNetworkRoaming());
    }

    @Test
    public void testGetTransportType() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .build();
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, nri.getTransportType());
    }

    @Test
    public void testGetRegisteredPlmn() {
        final String plmn = "12345";
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegisteredPlmn(plmn)
                .build();
        assertEquals(plmn, nri.getRegisteredPlmn());
    }

    @Test
    public void testGetRejectCause() {
        final int fakeRejectCause = 123;
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRejectCause(fakeRejectCause)
                .build();
        assertEquals(fakeRejectCause, nri.getRejectCause());
    }

    @Test
    public void testIsEmergencyEnabled() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setEmergencyOnly(true)
                .build();
        assertTrue(nri.isEmergencyEnabled());
    }

    @Test
    public void testGetCellIdentity() {
        final CellIdentity ci = new CellIdentityLte(120 /* MCC */, 260 /* MNC */, 12345 /* CI */,
                503 /* PCI */, 54321 /* TAC */);
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setCellIdentity(ci)
                .build();
        assertEquals(ci, nri.getCellIdentity());
    }

    /**
     * Test {@link NetworkRegistrationInfo#isRegistered()} to support backward compatibility.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testIsRegistered() {
        final int[] registeredStates = new int[] {NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING};
        for (int state : registeredStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertTrue(nri.isRegistered());
        }

        final int[] unregisteredStates = new int[] {
            NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_DENIED,
                NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN,
                NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY};
        for (int state : unregisteredStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertFalse(nri.isRegistered());
        }
    }

    @Test
    public void testIsNetworkRegistered() {
        final int[] registeredStates = new int[] {NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING};
        for (int state : registeredStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertTrue(nri.isNetworkRegistered());
        }

        final int[] unregisteredStates = new int[] {
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_DENIED,
                NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN};
        for (int state : unregisteredStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertFalse(nri.isNetworkRegistered());
        }
    }

    /**
     * Test {@link NetworkRegistrationInfo#isSearching()} to support backward compatibility.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testIsSearching() {
        final int[] isSearchingStates = new int[] {
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING};
        for (int state : isSearchingStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertTrue(nri.isSearching());
        }

        final int[] isNotSearchingStates = new int[] {
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.REGISTRATION_STATE_DENIED,
                NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN};
        for (int state : isNotSearchingStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertFalse(nri.isSearching());
        }
    }

    @Test
    public void testIsNetworkSearching() {
        final int[] isSearchingStates = new int[] {
            NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING};
        for (int state : isSearchingStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertTrue(nri.isNetworkSearching());
        }

        final int[] isNotSearchingStates = new int[] {
            NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.REGISTRATION_STATE_DENIED,
                NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN,
                NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY};
        for (int state : isNotSearchingStates) {
            NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                    .setRegistrationState(state)
                    .build();
            assertFalse(nri.isNetworkSearching());
        }
    }

    @Test
    public void testParcel() {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setAvailableServices(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                .setCellIdentity(new CellIdentityLte(120 /* MCC */, 260 /* MNC */, 12345 /* CI */,
                            503 /* PCI */, 54321 /* TAC */))
                .setRegisteredPlmn("12345")
                .build();

        Parcel p = Parcel.obtain();
        nri.writeToParcel(p, 0);
        p.setDataPosition(0);

        NetworkRegistrationInfo newNrs = NetworkRegistrationInfo.CREATOR.createFromParcel(p);
        assertEquals(nri, newNrs);
    }

    @Ignore("the compatibility framework does not currently support changing compatibility flags"
            + " on user builds for device side CTS tests. Ignore this test until support is added")
    @Test
    public void testReturnRegistrationStateEmergencyAndChangesCompatDisabled() throws Exception {
        // disable compact change
        TelephonyUtils.disableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE, RETURN_REGISTRATION_STATE_EMERGENCY_STRING);

        // LTE
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();

        assertEquals(NetworkRegistrationInfo.REGISTRATION_STATE_DENIED, nri.getRegistrationState());

        // NR
        nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .build();

        assertEquals(NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                nri.getRegistrationState());

        // reset compat change
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE, RETURN_REGISTRATION_STATE_EMERGENCY_STRING);
    }

    @Test
    public void testReturnRegistrationStateEmergency() throws Exception {
        // LTE
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();

        assertEquals(NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY,
                nri.getRegistrationState());

        // NR
        nri = new NetworkRegistrationInfo.Builder()
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .build();

        assertEquals(NetworkRegistrationInfo.REGISTRATION_STATE_EMERGENCY,
                nri.getRegistrationState());
    }
}
