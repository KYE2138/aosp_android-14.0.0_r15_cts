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

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
//@AppModeFull // TODO(Instant) Should clip board data be visible?
public class ClipboardManagerTest {
    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private ClipboardManager mClipboardManager;
    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Skipping Test: Wear-Os does not support ClipboardService", hasAutoFillFeature());
        mClipboardManager = mContext.getSystemService(ClipboardManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();

        // Clear any dialogs and launch an activity as focus is needed to access clipboard.
        mUiDevice.pressHome();
        mUiDevice.pressBack();
        launchActivity(MockActivity.class);
    }

    @After
    public void cleanUp() {
        if (mClipboardManager != null) {
            mClipboardManager.clearPrimaryClip();
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testSetGetText() {
        ClipboardManager clipboardManager = mClipboardManager;
        clipboardManager.setText("Test Text 1");
        assertEquals("Test Text 1", clipboardManager.getText());

        clipboardManager.setText("Test Text 2");
        assertEquals("Test Text 2", clipboardManager.getText());
    }

    @Test
    public void testHasPrimaryClip() {
        ClipboardManager clipboardManager = mClipboardManager;
        if (clipboardManager.hasPrimaryClip()) {
            assertNotNull(clipboardManager.getPrimaryClip());
            assertNotNull(clipboardManager.getPrimaryClipDescription());
        } else {
            assertNull(clipboardManager.getPrimaryClip());
            assertNull(clipboardManager.getPrimaryClipDescription());
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText("Label", "Text"));
        assertTrue(clipboardManager.hasPrimaryClip());
    }

    @Test
    public void testSetPrimaryClip_plainText() {
        ClipData textData = ClipData.newPlainText("TextLabel", "Text");
        assertSetPrimaryClip(textData, "TextLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},
                new ExpectedClipItem("Text", null, null));
    }

    @Test
    public void testSetPrimaryClip_intent() {
        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        ClipData intentData = ClipData.newIntent("IntentLabel", intent);
        assertSetPrimaryClip(intentData, "IntentLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_INTENT},
                new ExpectedClipItem(null, intent, null));
    }

    @Test
    public void testSetPrimaryClip_rawUri() {
        Uri uri = Uri.parse("http://www.google.com");
        ClipData uriData = ClipData.newRawUri("UriLabel", uri);
        assertSetPrimaryClip(uriData, "UriLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_URILIST},
                new ExpectedClipItem(null, null, uri));
    }

    @Test
    public void testSetPrimaryClip_contentUri() {
        Uri contentUri = Uri.parse("content://cts/test/for/clipboardmanager");
        ClipData contentUriData = ClipData.newUri(mContext.getContentResolver(),
                "ContentUriLabel", contentUri);
        assertSetPrimaryClip(contentUriData, "ContentUriLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_URILIST},
                new ExpectedClipItem(null, null, contentUri));
    }

    @Test
    public void testSetPrimaryClip_complexItem() {
        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        Uri uri = Uri.parse("http://www.google.com");
        ClipData multiData = new ClipData(new ClipDescription("ComplexItemLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                        ClipDescription.MIMETYPE_TEXT_URILIST}),
                new Item("Text", intent, uri));
        assertSetPrimaryClip(multiData, "ComplexItemLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                        ClipDescription.MIMETYPE_TEXT_URILIST},
                new ExpectedClipItem("Text", intent, uri));
    }

    @Test
    public void testSetPrimaryClip_multipleItems() {
        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        Uri uri = Uri.parse("http://www.google.com");
        ClipData textData = ClipData.newPlainText("TextLabel", "Text");
        textData.addItem(new Item("More Text"));
        textData.addItem(new Item(intent));
        textData.addItem(new Item(uri));
        assertSetPrimaryClip(textData, "TextLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},
                new ExpectedClipItem("Text", null, null),
                new ExpectedClipItem("More Text", null, null),
                new ExpectedClipItem(null, intent, null),
                new ExpectedClipItem(null, null, uri));
    }

    @Test
    public void testSetPrimaryClip_multipleMimeTypes() {
        ContentResolver contentResolver = mContext.getContentResolver();

        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        Uri uri = Uri.parse("http://www.google.com");
        Uri contentUri1 = Uri.parse("content://ctstest/testtable1");
        Uri contentUri2 = Uri.parse("content://ctstest/testtable2");
        Uri contentUri3 = Uri.parse("content://ctstest/testtable1/0");
        Uri contentUri4 = Uri.parse("content://ctstest/testtable1/1");
        Uri contentUri5 = Uri.parse("content://ctstest/testtable2/0");
        Uri contentUri6 = Uri.parse("content://ctstest/testtable2/1");
        Uri contentUri7 = Uri.parse("content://ctstest/testtable2/2");
        Uri contentUri8 = Uri.parse("content://ctstest/testtable2/3");

        ClipData clipData = ClipData.newPlainText("TextLabel", "Text");
        clipData.addItem(contentResolver, new Item("More Text"));
        clipData.addItem(contentResolver, new Item(intent));
        clipData.addItem(contentResolver, new Item(uri));
        clipData.addItem(contentResolver, new Item(contentUri1));
        clipData.addItem(contentResolver, new Item(contentUri2));
        clipData.addItem(contentResolver, new Item(contentUri3));
        clipData.addItem(contentResolver, new Item(contentUri4));
        clipData.addItem(contentResolver, new Item(contentUri5));
        clipData.addItem(contentResolver, new Item(contentUri6));
        clipData.addItem(contentResolver, new Item(contentUri7));
        clipData.addItem(contentResolver, new Item(contentUri8));

        assertClipData(clipData, "TextLabel",
                new String[] {
                        ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                        ClipDescription.MIMETYPE_TEXT_URILIST,
                        "vnd.android.cursor.dir/com.android.content.testtable1",
                        "vnd.android.cursor.dir/com.android.content.testtable2",
                        "vnd.android.cursor.item/com.android.content.testtable1",
                        "vnd.android.cursor.item/com.android.content.testtable2",
                        "image/jpeg",
                        "audio/mpeg",
                        "video/mpeg"
                },
                new ExpectedClipItem("Text", null, null),
                new ExpectedClipItem("More Text", null, null),
                new ExpectedClipItem(null, intent, null),
                new ExpectedClipItem(null, null, uri),
                new ExpectedClipItem(null, null, contentUri1),
                new ExpectedClipItem(null, null, contentUri2),
                new ExpectedClipItem(null, null, contentUri3),
                new ExpectedClipItem(null, null, contentUri4),
                new ExpectedClipItem(null, null, contentUri5),
                new ExpectedClipItem(null, null, contentUri6),
                new ExpectedClipItem(null, null, contentUri7),
                new ExpectedClipItem(null, null, contentUri8));
    }

    @Test
    public void testPrimaryClipChangedListener() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        mClipboardManager.addPrimaryClipChangedListener(new OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                latch.countDown();
            }
        });

        final ClipData clipData = ClipData.newPlainText("TextLabel", "Text");
        mClipboardManager.setPrimaryClip(clipData);

        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void testClearPrimaryClip() {
        final ClipData clipData = ClipData.newPlainText("TextLabel", "Text");
        mClipboardManager.setPrimaryClip(clipData);
        assertTrue(mClipboardManager.hasPrimaryClip());
        assertTrue(mClipboardManager.hasText());
        assertNotNull(mClipboardManager.getPrimaryClip());
        assertNotNull(mClipboardManager.getPrimaryClipDescription());

        mClipboardManager.clearPrimaryClip();
        assertFalse(mClipboardManager.hasPrimaryClip());
        assertFalse(mClipboardManager.hasText());
        assertNull(mClipboardManager.getPrimaryClip());
        assertNull(mClipboardManager.getPrimaryClipDescription());
    }

    @Test
    public void testPrimaryClipNotAvailableWithoutFocus() throws Exception {
        ClipData textData = ClipData.newPlainText("TextLabel", "Text1");
        assertSetPrimaryClip(textData, "TextLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},
                new ExpectedClipItem("Text1", null, null));

        // Press the home button to unfocus the app.
        mUiDevice.pressHome();
        mUiDevice.wait(Until.gone(By.pkg(MockActivity.class.getPackageName())), 5000);

        // We should see an empty clipboard now.
        assertFalse(mClipboardManager.hasPrimaryClip());
        assertFalse(mClipboardManager.hasText());
        assertNull(mClipboardManager.getPrimaryClip());
        assertNull(mClipboardManager.getPrimaryClipDescription());

        // We should be able to set the clipboard but not see the contents.
        mClipboardManager.setPrimaryClip(ClipData.newPlainText("TextLabel", "Text2"));
        assertFalse(mClipboardManager.hasPrimaryClip());
        assertFalse(mClipboardManager.hasText());
        assertNull(mClipboardManager.getPrimaryClip());
        assertNull(mClipboardManager.getPrimaryClipDescription());

        // Launch an activity to get back in focus.
        launchActivity(MockActivity.class);

        // Verify clipboard access is restored.
        assertNotNull(mClipboardManager.getPrimaryClip());
        assertNotNull(mClipboardManager.getPrimaryClipDescription());

        // Verify we were unable to change the clipboard while out of focus.
        assertClipData(mClipboardManager.getPrimaryClip(),
                "TextLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},
                new ExpectedClipItem("Text2", null, null));
    }

    @Test
    public void testReadInBackgroundRequiresPermission() throws Exception {
        ClipData clip = ClipData.newPlainText("TextLabel", "Text1");
        mClipboardManager.setPrimaryClip(clip);

        // Press the home button to unfocus the app.
        mUiDevice.pressHome();
        mUiDevice.wait(Until.gone(By.pkg(MockActivity.class.getPackageName())), 5000);

        // Without the READ_CLIPBOARD_IN_BACKGROUND permission, we should see an empty clipboard.
        assertThat(mClipboardManager.hasPrimaryClip()).isFalse();
        assertThat(mClipboardManager.hasText()).isFalse();
        assertThat(mClipboardManager.getPrimaryClip()).isNull();
        assertThat(mClipboardManager.getPrimaryClipDescription()).isNull();

        // Having the READ_CLIPBOARD_IN_BACKGROUND permission should allow us to read the clipboard
        // even when we are not in the foreground. We use the shell identity to simulate holding
        // this permission; in practice, only privileged system apps can hold this permission (e.g.
        // an app that has the SYSTEM_TEXT_INTELLIGENCE role).
        ClipData actual = SystemUtil.callWithShellPermissionIdentity(
                () -> mClipboardManager.getPrimaryClip(),
                android.Manifest.permission.READ_CLIPBOARD_IN_BACKGROUND);
        assertThat(actual).isNotNull();
        assertThat(actual.getItemAt(0).getText()).isEqualTo("Text1");
    }

    @Test
    public void testClipSourceRecordedWhenClipSet() {
        ClipData clipData = ClipData.newPlainText("TextLabel", "Text1");
        mClipboardManager.setPrimaryClip(clipData);

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_CLIP_SOURCE);
        assertThat(
                mClipboardManager.getPrimaryClipSource()).isEqualTo("android.content.cts");
    }

    @Test
    public void testSetPrimaryClipAsPackage() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_CLIP_SOURCE);

        ClipData clipData = ClipData.newPlainText("TextLabel", "Text1");
        mClipboardManager.setPrimaryClipAsPackage(clipData, "test.package");

        assertThat(
                mClipboardManager.getPrimaryClipSource()).isEqualTo("test.package");
    }

    private void launchActivity(Class<? extends Activity> clazz) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mContext.getPackageName(), clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.pkg(clazz.getPackageName())), 15000);
    }

    private class ExpectedClipItem {
        CharSequence mText;
        Intent mIntent;
        Uri mUri;

        ExpectedClipItem(CharSequence text, Intent intent, Uri uri) {
            mText = text;
            mIntent = intent;
            mUri = uri;
        }
    }

    private void assertSetPrimaryClip(ClipData clipData,
            String expectedLabel,
            String[] expectedMimeTypes,
            ExpectedClipItem... expectedClipItems) {
        ClipboardManager clipboardManager = mClipboardManager;

        clipboardManager.setPrimaryClip(clipData);
        assertTrue(clipboardManager.hasPrimaryClip());

        if (expectedClipItems != null
                && expectedClipItems.length > 0
                && expectedClipItems[0].mText != null) {
            assertTrue(clipboardManager.hasText());
        } else {
            assertFalse(clipboardManager.hasText());
        }

        assertNotNull(clipboardManager.getPrimaryClip());
        assertNotNull(clipboardManager.getPrimaryClipDescription());

        assertClipData(clipboardManager.getPrimaryClip(),
                expectedLabel, expectedMimeTypes, expectedClipItems);

        assertClipDescription(clipboardManager.getPrimaryClipDescription(),
                expectedLabel, expectedMimeTypes);
    }

    private static void assertClipData(ClipData actualData, String expectedLabel,
            String[] expectedMimeTypes, ExpectedClipItem... expectedClipItems) {
        if (expectedClipItems != null) {
            assertEquals(expectedClipItems.length, actualData.getItemCount());
            for (int i = 0; i < expectedClipItems.length; i++) {
                assertClipItem(expectedClipItems[i], actualData.getItemAt(i));
            }
        } else {
            throw new IllegalArgumentException("Should have at least one expectedClipItem...");
        }

        assertClipDescription(actualData.getDescription(), expectedLabel, expectedMimeTypes);
    }

    private static void assertClipDescription(ClipDescription description, String expectedLabel,
            String... mimeTypes) {
        assertEquals(expectedLabel, description.getLabel());
        assertEquals(mimeTypes.length, description.getMimeTypeCount());
        int mimeTypeCount = description.getMimeTypeCount();
        for (int i = 0; i < mimeTypeCount; i++) {
            assertEquals(mimeTypes[i], description.getMimeType(i));
        }
    }

    private static void assertClipItem(ExpectedClipItem expectedItem, Item item) {
        assertEquals(expectedItem.mText, item.getText());
        if (expectedItem.mIntent != null) {
            assertNotNull(item.getIntent());
        } else {
            assertNull(item.getIntent());
        }
        if (expectedItem.mUri != null) {
            assertEquals(expectedItem.mUri.toString(), item.getUri().toString());
        } else {
            assertNull(item.getUri());
        }
    }

    private boolean hasAutoFillFeature() {
        return mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOFILL);
    }
}
