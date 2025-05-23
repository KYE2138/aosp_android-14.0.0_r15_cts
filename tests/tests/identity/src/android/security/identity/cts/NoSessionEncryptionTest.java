/*
 * Copyright 2022 The Android Open Source Project
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

package android.security.identity.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import android.hardware.biometrics.CryptoObject;
import android.security.identity.IdentityCredential;
import android.security.identity.IdentityCredentialStore;
import android.security.identity.PresentationSession;
import android.security.identity.CredentialDataRequest;
import android.security.identity.CredentialDataResult;
import com.android.security.identity.internal.Util;
import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

import javax.crypto.SecretKey;

public class NoSessionEncryptionTest {
    private static final String TAG = "NoSessionEncryptionTest";

    @Test
    public void noSessionEncryption() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());
        assumeTrue(TestUtil.getFeatureVersion() >= 202301);

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        store.deleteCredentialByName("credential1");
        assertNull(store.deleteCredentialByName("credential1"));
        ProvisioningTest.createCredential(store, "credential1");

        IdentityCredential credential = store.getCredentialByName("credential1",
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        credential.setAvailableAuthenticationKeys(5, 3);
        Collection<X509Certificate> certificates = credential.getAuthKeysNeedingCertification();
        for (X509Certificate certificate : certificates) {
            credential.storeStaticAuthenticationData(certificate, new byte[]{42, 43, 44});
        }
        PublicKey expectedAuthKey = certificates.iterator().next().getPublicKey();

        PresentationSession session = store.createPresentationSession(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        // Calculate sessionTranscript, make something that resembles what you'd use for
        // an over-the-Internet presentation not using mdoc session encryption.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(
            new CborBuilder()
              .addArray()
                .add(SimpleValue.NULL)   // DeviceEngagementBytes isn't used.
                .add(SimpleValue.NULL)   // EReaderKeyBytes isn't used.
                .addArray()              // Proprietary handover structure follows.
                  .add("TestHandover")
                  .add(new ByteString(new byte[] {1, 2, 3, 4}))
                  .add(new ByteString(new byte[] {10, 11, 12, 13, 14}))
                  .add(new UnicodeString("something"))
                .end()
              .end()
            .build());
        byte[] sessionTranscript = baos.toByteArray();
        session.setSessionTranscript(sessionTranscript);

        // Now request data from the credential...
        //
        Map<String, Collection<String>> dsEntriesToRequest = new LinkedHashMap<>();
        dsEntriesToRequest.put("org.iso.18013-5.2019",
                Arrays.asList("First name",
                        "Last name",
                        "Home address",
                        "Birth date",
                        "Cryptanalyst",
                        "Portrait image",
                        "Height"));
        CredentialDataResult rd = session.getCredentialData(
            "credential1",
            new CredentialDataRequest.Builder()
            .setDeviceSignedEntriesToRequest(dsEntriesToRequest)
            .setAllowUsingExhaustedKeys(true)
            .setIncrementUseCount(true)
            .setAllowUsingExpiredKeys(false)
            .build());
        byte[] resultCbor = rd.getDeviceNameSpaces();
        try {
            String pretty = Util.cborPrettyPrint(Util.canonicalizeCbor(resultCbor));
            assertEquals("{\n"
                         + "  'org.iso.18013-5.2019' : {\n"
                         + "    'Height' : 180,\n"
                         + "    'Last name' : 'Turing',\n"
                         + "    'Birth date' : '19120623',\n"
                         + "    'First name' : 'Alan',\n"
                         + "    'Cryptanalyst' : true,\n"
                         + "    'Home address' : 'Maida Vale, London, England',\n"
                         + "    'Portrait image' : [0x01, 0x02]\n"
                         + "  }\n"
                         + "}",
                         pretty);
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // Since we're not using session encryption, a MAC cannot be produced
        assertNull(rd.getDeviceMac());

        // However, a signature can be produced. Check this.
        byte[] deviceAuthenticationCbor = Util.buildDeviceAuthenticationCbor(
            "org.iso.18013-5.2019.mdl",
            sessionTranscript,
            resultCbor);
        byte[] deviceAuthenticationBytes =
            Util.prependSemanticTagForEncodedCbor(deviceAuthenticationCbor);
        byte[] signature = rd.getDeviceSignature();
        assertNotNull(signature);
        assertEquals(0, Util.coseSign1GetData(signature).length);
        assertTrue(Util.coseSign1CheckSignature(signature,
                                                deviceAuthenticationBytes, // detached content
                                                expectedAuthKey));
    }
}
