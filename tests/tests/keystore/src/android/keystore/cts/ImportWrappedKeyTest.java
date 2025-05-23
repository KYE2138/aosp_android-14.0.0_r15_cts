/*
 * Copyright 2017 The Android Open Source Project
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

package android.keystore.cts;

import static android.security.keymaster.KeymasterDefs.KM_ALGORITHM_3DES;
import static android.security.keymaster.KeymasterDefs.KM_ALGORITHM_AES;
import static android.security.keymaster.KeymasterDefs.KM_KEY_FORMAT_RAW;
import static android.security.keymaster.KeymasterDefs.KM_MODE_CBC;
import static android.security.keymaster.KeymasterDefs.KM_MODE_ECB;
import static android.security.keymaster.KeymasterDefs.KM_PAD_NONE;
import static android.security.keymaster.KeymasterDefs.KM_PAD_PKCS7;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_DECRYPT;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_ENCRYPT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.SecureKeyImportUnavailableException;
import android.security.keystore.StrongBoxUnavailableException;
import android.security.keystore.WrappedKeyEntry;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

@RunWith(AndroidJUnit4.class)
public class ImportWrappedKeyTest {
    private static final String TAG = "ImportWrappedKeyTest";

    private static final String ALIAS = "my key";
    private static final String WRAPPING_KEY_ALIAS = "my_favorite_wrapping_key";

    private static final int WRAPPED_FORMAT_VERSION = 0;
    private static final int GCM_TAG_SIZE = 128;

    SecureRandom random = new SecureRandom();

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testKeyStore_ImportWrappedKey() throws Exception {
        random.setSeed(0);

        byte[] keyMaterial = new byte[32];
        random.nextBytes(keyMaterial);
        byte[] mask = new byte[32]; // Zero mask

        KeyPair kp;
        try {
            kp = genKeyPair(WRAPPING_KEY_ALIAS, false);
        } catch (SecureKeyImportUnavailableException e) {
            return;
        }

        try {
            importWrappedKey(wrapKey(
                    kp.getPublic(),
                    keyMaterial,
                    mask,
                    makeAuthList(keyMaterial.length * 8, KM_ALGORITHM_AES)));
        } catch (SecureKeyImportUnavailableException e) {
            return;
        }

        // Use Key
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

        Key key = keyStore.getKey(ALIAS, null);

        Cipher c = Cipher.getInstance("AES/ECB/PKCS7Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = c.doFinal("hello, world".getBytes());

        c = Cipher.getInstance("AES/ECB/PKCS7Padding");
        c.init(Cipher.DECRYPT_MODE, key);

        assertEquals(new String(c.doFinal(encrypted)), "hello, world");
    }

    @Test
    public void testKeyStore_ImportIncorrectWrappedKey() throws Exception {
        testKeyStore_ImportIncorrectWrappedKey(false);
    }

    @Test
    public void testKeyStore_ImportIncorrectWrappedKey_StrongBox() throws Exception {
        testKeyStore_ImportIncorrectWrappedKey(true);
    }

    private void testKeyStore_ImportIncorrectWrappedKey(boolean isStrongBox) throws Exception {
        if (isStrongBox) {
            TestUtils.assumeStrongBox();
        }
        random.setSeed(0);

        byte[] keyMaterial = new byte[32];
        random.nextBytes(keyMaterial);
        byte[] mask = new byte[32]; // Zero mask

        KeyPair kp;
        try {
            kp = genKeyPair(WRAPPING_KEY_ALIAS, isStrongBox);
        } catch (SecureKeyImportUnavailableException e) {
            return;
        }

        KeyStoreException exception = null;
        try {
            importWrappedKey(wrapKey(
                    kp.getPublic(),
                    keyMaterial,
                    mask,
                    makeAuthList(keyMaterial.length * 8, KM_ALGORITHM_AES),
                    false /* incorrect wrapping required*/));
        } catch (SecureKeyImportUnavailableException e) {
            return;
        } catch (KeyStoreException e) {
            exception = e;
        }
        assertWithMessage("Did not hit a failure but expected one").that(exception).isNotNull();
        assertThat(exception.getCause()).isInstanceOf(android.security.KeyStoreException.class);
        android.security.KeyStoreException ksException =
                (android.security.KeyStoreException) exception.getCause();
        assertFalse("Importing incorrectly wrapped key should not cause transient failure in"
                    + " Key{Mint/Master}. That means performing same operation will fail always.",
                        ksException.isTransientFailure());
    }

    @Test
    public void testKeyStore_ImportWrappedKeyWrappingKeyMissing() throws Exception {
        final String EXPECTED_FAILURE = "Failed to import wrapped key. Keystore error code: 7";
        KeyStoreException exception = null;

        try {
            byte [] fakeWrappedKey = new byte[1];
            importWrappedKey(fakeWrappedKey, WRAPPING_KEY_ALIAS + "_Missing");
        } catch (KeyStoreException e) {
            exception = e;

        }

        assertWithMessage("Did not hit a failure but expected one").that(exception).isNotNull();

        assertThat(exception.getMessage()).isEqualTo(EXPECTED_FAILURE);
        assertThat(exception.getCause()).isInstanceOf(android.security.KeyStoreException.class);
        android.security.KeyStoreException ksException =
                (android.security.KeyStoreException) exception.getCause();
        assertThat(ksException.getNumericErrorCode()).isEqualTo(
                android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_3DES() throws Exception {
      if (!TestUtils.supports3DES()) {
          return;
        }
        KeyGenerator kg = KeyGenerator.getInstance("DESEDE");
        kg.init(168);
        byte[] keyMaterial = kg.generateKey().getEncoded();
        random.nextBytes(keyMaterial);
        byte[] mask = new byte[24]; // Zero mask

        KeyPair kp;
        try {
            kp = genKeyPair(WRAPPING_KEY_ALIAS, false);
        } catch (SecureKeyImportUnavailableException e) {
            return;
        }

        try {
            importWrappedKey(wrapKey(
                    kp.getPublic(),
                    keyMaterial,
                    mask,
                    makeAuthList(168, KM_ALGORITHM_3DES)));
        } catch (SecureKeyImportUnavailableException e) {
            return;
        }

        // Use Key
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

        Key key = keyStore.getKey(ALIAS, null);

        Cipher c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        IvParameterSpec paramSpec = new IvParameterSpec(c.getIV());
        byte[] encrypted = c.doFinal("hello, world".getBytes());

        c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
        c.init(Cipher.DECRYPT_MODE, key, paramSpec);

        assertEquals(new String(c.doFinal(encrypted)), "hello, world");
    }

    @Test
    public void testKeyStore_ImportWrappedKey_3DES_StrongBox() throws Exception {
      if (!TestUtils.supports3DES()) {
          return;
      }

      if (TestUtils.hasStrongBox(getContext())) {
            KeyGenerator kg = KeyGenerator.getInstance("DESEDE");
            kg.init(168);
            byte[] keyMaterial = kg.generateKey().getEncoded();
            random.nextBytes(keyMaterial);
            byte[] mask = new byte[24]; // Zero mask

            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, true).getPublic(),
                    keyMaterial,
                    mask,
                    makeAuthList(168, KM_ALGORITHM_3DES)));

            // Use Key
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null, null);

            assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

            Key key = keyStore.getKey(ALIAS, null);

            Cipher c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
            c.init(Cipher.ENCRYPT_MODE, key);
            IvParameterSpec paramSpec = new IvParameterSpec(c.getIV());
            byte[] encrypted = c.doFinal("hello, world".getBytes());

            c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
            c.init(Cipher.DECRYPT_MODE, key, paramSpec);

            assertEquals(new String(c.doFinal(encrypted)), "hello, world");
        } else {
            try {
                genKeyPair(WRAPPING_KEY_ALIAS, true);
                fail();
            } catch (StrongBoxUnavailableException | SecureKeyImportUnavailableException e) {
            }
        }
    }

    @Test
    public void testKeyStore_ImportWrappedKey_AES_StrongBox() throws Exception {
        if (TestUtils.hasStrongBox(getContext())) {
            random.setSeed(0);

            byte[] keyMaterial = new byte[32];
            random.nextBytes(keyMaterial);
            byte[] mask = new byte[32]; // Zero mask

            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, true).getPublic(),
                    keyMaterial,
                    mask,
                    makeAuthList(keyMaterial.length * 8, KM_ALGORITHM_AES)));

            // Use Key
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null, null);

            assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

            Key key = keyStore.getKey(ALIAS, null);

            Cipher c = Cipher.getInstance("AES/CBC/PKCS7Padding");
            c.init(Cipher.ENCRYPT_MODE, key);
            IvParameterSpec paramSpec = new IvParameterSpec(c.getIV());

            byte[] encrypted = c.doFinal("hello, world".getBytes());

            c = Cipher.getInstance("AES/CBC/PKCS7Padding");
            c.init(Cipher.DECRYPT_MODE, key, paramSpec);

            assertEquals(new String(c.doFinal(encrypted)), "hello, world");
        } else {
            try {
              random.setSeed(0);

              byte[] keyMaterial = new byte[32];
              random.nextBytes(keyMaterial);
              byte[] mask = new byte[32]; // Zero mask

              importWrappedKey(wrapKey(
                  genKeyPair(WRAPPING_KEY_ALIAS, true).getPublic(),
                  keyMaterial,
                  mask,
                  makeAuthList(keyMaterial.length * 8, KM_ALGORITHM_AES)));
                fail();
            } catch (StrongBoxUnavailableException | SecureKeyImportUnavailableException e) {
            }
        }
    }

    public void importWrappedKey(byte[] wrappedKey, String wrappingKeyAlias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        AlgorithmParameterSpec spec = new KeyGenParameterSpec.Builder(wrappingKeyAlias,
                KeyProperties.PURPOSE_WRAP_KEY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build();
        Entry wrappedKeyEntry = new WrappedKeyEntry(wrappedKey, wrappingKeyAlias,
                  "RSA/ECB/OAEPPadding", spec);
        keyStore.setEntry(ALIAS, wrappedKeyEntry, null);
    }

    public void importWrappedKey(byte[] wrappedKey) throws Exception {
        importWrappedKey(wrappedKey, WRAPPING_KEY_ALIAS);
    }

    public byte[] wrapKey(PublicKey publicKey, byte[] keyMaterial, byte[] mask,
                          DERSequence authorizationList) throws Exception {
        return wrapKey(publicKey, keyMaterial, mask, authorizationList, true);
    }

    public byte[] wrapKey(PublicKey publicKey, byte[] keyMaterial, byte[] mask,
            DERSequence authorizationList, boolean correctWrappingRequired)
            throws Exception {
        // Build description
        DEREncodableVector descriptionItems = new DEREncodableVector();
        descriptionItems.add(new DERInteger(KM_KEY_FORMAT_RAW));
        descriptionItems.add(authorizationList);
        DERSequence wrappedKeyDescription = new DERSequence(descriptionItems);

        // Generate 12 byte initialization vector
        byte[] iv = new byte[12];
        random.nextBytes(iv);

        // Generate 256 bit AES key. This is the ephemeral key used to encrypt the secure key.
        byte[] aesKeyBytes = new byte[32];
        random.nextBytes(aesKeyBytes);

        // Encrypt ephemeral keys
        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
        Cipher pkCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        if (correctWrappingRequired) {
            pkCipher.init(Cipher.ENCRYPT_MODE, publicKey, spec);
        } else {
            // Use incorrect OAEPParameters while initializing cipher. By default, main digest and
            // MGF1 digest are SHA-1 here.
            pkCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        }
        byte[] encryptedEphemeralKeys = pkCipher.doFinal(aesKeyBytes);

        // Encrypt secure key
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(aesKeyBytes, "AES");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);
        byte[] aad = wrappedKeyDescription.getEncoded();

        cipher.updateAAD(aad);
        byte[] encryptedSecureKey = cipher.doFinal(keyMaterial);
        // Get GCM tag. Java puts the tag at the end of the ciphertext data :(
        int len = encryptedSecureKey.length;
        int tagSize = (GCM_TAG_SIZE / 8);
        byte[] tag = Arrays.copyOfRange(encryptedSecureKey, len - tagSize, len);

        // Remove GCM tag from end of output
        encryptedSecureKey = Arrays.copyOfRange(encryptedSecureKey, 0, len - tagSize);

        // Build ASN.1 DER encoded sequence WrappedKeyWrapper
        DEREncodableVector items = new DEREncodableVector();
        items.add(new DERInteger(WRAPPED_FORMAT_VERSION));
        items.add(new DEROctetString(encryptedEphemeralKeys));
        items.add(new DEROctetString(iv));
        items.add(wrappedKeyDescription);
        items.add(new DEROctetString(encryptedSecureKey));
        items.add(new DEROctetString(tag));
        return new DERSequence(items).getEncoded(ASN1Encoding.DER);
    }

    /**
     * xor of two byte[] for masking or unmasking transit keys
     */
    private byte[] xor(byte[] key, byte[] mask) {
        byte[] out = new byte[key.length];

        for (int i = 0; i < key.length; i++) {
            out[i] = (byte) (key[i] ^ mask[i]);
        }
        return out;
    }

    private DERSequence makeAuthList(int size,
            int algorithm_) {
        // Make an AuthorizationList to describe the secure key
        // https://developer.android.com/training/articles/security-key-attestation.html#verifying
        DEREncodableVector allPurposes = new DEREncodableVector();
        allPurposes.add(new DERInteger(KM_PURPOSE_ENCRYPT));
        allPurposes.add(new DERInteger(KM_PURPOSE_DECRYPT));
        DERSet purposeSet = new DERSet(allPurposes);
        DERTaggedObject purpose = new DERTaggedObject(true, 1, purposeSet);
        DERTaggedObject algorithm = new DERTaggedObject(true, 2, new DERInteger(algorithm_));
        DERTaggedObject keySize =
                new DERTaggedObject(true, 3, new DERInteger(size));

        DEREncodableVector allBlockModes = new DEREncodableVector();
        allBlockModes.add(new DERInteger(KM_MODE_ECB));
        allBlockModes.add(new DERInteger(KM_MODE_CBC));
        DERSet blockModeSet = new DERSet(allBlockModes);
        DERTaggedObject blockMode = new DERTaggedObject(true, 4, blockModeSet);

        DEREncodableVector allPaddings = new DEREncodableVector();
        allPaddings.add(new DERInteger(KM_PAD_PKCS7));
        allPaddings.add(new DERInteger(KM_PAD_NONE));
        DERSet paddingSet = new DERSet(allPaddings);
        DERTaggedObject padding = new DERTaggedObject(true, 6, paddingSet);

        DERTaggedObject noAuthRequired = new DERTaggedObject(true, 503, DERNull.INSTANCE);

        // Build sequence
        DEREncodableVector allItems = new DEREncodableVector();
        allItems.add(purpose);
        allItems.add(algorithm);
        allItems.add(keySize);
        allItems.add(blockMode);
        allItems.add(padding);
        allItems.add(noAuthRequired);
        return new DERSequence(allItems);
    }

    private KeyPair genKeyPair(String alias, boolean isStrongBoxBacked) throws Exception {
        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        kpg.initialize(
                new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_WRAP_KEY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                        .setIsStrongBoxBacked(isStrongBoxBacked)
                        .build());
        return kpg.generateKeyPair();
    }
}
