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
package android.autofillservice.cts.dropdown;

import static android.autofillservice.cts.activities.SimpleSaveActivity.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.assertTextIsSanitized;

import android.autofillservice.cts.activities.DialogLauncherActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.view.View;

import androidx.test.uiautomator.UiObject2;

import org.junit.Test;

public class DialogLauncherActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<DialogLauncherActivity> {

    private DialogLauncherActivity mActivity;

    @Override
    protected AutofillActivityTestRule<DialogLauncherActivity> getActivityRule() {
        return new AutofillActivityTestRule<DialogLauncherActivity>(DialogLauncherActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    public void testAutofill_noDatasets() throws Exception {
        autofillNoDatasetsTest(false);
    }

    @Test
    public void testAutofill_noDatasets_afterResizing() throws Exception {
        autofillNoDatasetsTest(true);
    }

    private void autofillNoDatasetsTest(boolean resize) throws Exception {
        enableService();
        mActivity.launchDialog(mUiBot);

        if (resize) {
            mActivity.maximizeDialog();
        }

        // Set expectations.
        sReplier.addResponse(CannedFillResponse.NO_RESPONSE);

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Asserts results.
        try {
            mUiBot.assertNoDatasetsEver();
            // Make sure nodes were properly generated.
            assertTextIsSanitized(fillRequest.structure, ID_USERNAME);
            assertTextIsSanitized(fillRequest.structure, ID_PASSWORD);
        } catch (AssertionError e) {
            Helper.dumpStructure("D'OH!", fillRequest.structure);
            throw e;
        }
    }

    @Test
    public void testAutofill_oneDataset() throws Exception {
        autofillOneDatasetTest(false);
    }

    @Test
    public void testAutofill_oneDataset_afterResizing() throws Exception {
        autofillOneDatasetTest(true);
    }

    private void autofillOneDatasetTest(boolean resize) throws Exception {
        enableService();
        mActivity.launchDialog(mUiBot);

        if (resize) {
            mActivity.maximizeDialog();
        }

        // Set expectations.
        mActivity.expectAutofill("dude", "sweet");
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        final UiObject2 picker = mUiBot.assertDatasets("The Dude");
        if (!Helper.isAutofillWindowFullScreen(mActivity)) {
            mActivity.assertInDialogBounds(picker.getVisibleBounds());
        }

        // Asserts results.
        mUiBot.selectDataset("The Dude");
        mActivity.assertAutofilled();
    }
}
