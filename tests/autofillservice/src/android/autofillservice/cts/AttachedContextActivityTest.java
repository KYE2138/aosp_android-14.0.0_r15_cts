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

package android.autofillservice.cts;

import android.autofillservice.cts.activities.AttachedContextActivity;
import android.autofillservice.cts.activities.AttachedContextActivity.FillExpectation;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.platform.test.annotations.FlakyTest;

import org.junit.Test;

/**
 * Makes sure activity with attached context can be autofilled.
 */
public class AttachedContextActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<AttachedContextActivity> {

    private AttachedContextActivity mActivity;

    @Override
    protected AutofillActivityTestRule<AttachedContextActivity> getActivityRule() {
        return new AutofillActivityTestRule<AttachedContextActivity>(
                AttachedContextActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @FlakyTest(bugId = 277287435)
    @Test
    public void testAutofill() throws Exception {
        // Prepare
        enableService();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(AttachedContextActivity.ID_INPUT, "attack!")
                        .setPresentation(createPresentation("fill me"))
                        .build())
                .build());
        final FillExpectation fillExpectation = mActivity.expectAutoFill("attack!");

        // Trigger autofill
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();

        // Select dataset
        mUiBot.selectDataset("fill me");

        // Assert results
        fillExpectation.assertAutoFilled();
    }
}
