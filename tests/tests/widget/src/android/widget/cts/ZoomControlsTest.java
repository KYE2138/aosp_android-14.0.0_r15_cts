/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.ZoomControls;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ZoomControls}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ZoomControlsTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new ZoomControls(mContext);

        new ZoomControls(mContext, null);
    }

    @UiThreadTest
    @Test
    public void testSetOnZoomInClickListener() {
        ZoomControls zoomControls = new ZoomControls(mContext);

        // normal parameters
        final View.OnClickListener clickListener = (View view) -> {};
        zoomControls.setOnZoomInClickListener(clickListener);

        // exceptional parameters
        zoomControls.setOnZoomInClickListener(null);
    }

    @UiThreadTest
    @Test
    public void testSetOnZoomOutClickListener() {
        ZoomControls zoomControls = new ZoomControls(mContext);

        // normal parameters
        final View.OnClickListener clickListener = (View view) -> {};
        zoomControls.setOnZoomOutClickListener(clickListener);

        // exceptional parameters
        zoomControls.setOnZoomOutClickListener(null);
    }

    @UiThreadTest
    @Test
    public void testSetZoomSpeed() {
        ZoomControls zoomControls = new ZoomControls(mContext);

        zoomControls.setZoomSpeed(500);

        // TODO: how to check?
    }

    @UiThreadTest
    @Test
    public void testShowAndHide() {
        final ZoomControls zoomControls = new ZoomControls(mContext);
        assertEquals(View.VISIBLE, zoomControls.getVisibility());

        zoomControls.hide();
        assertEquals(View.GONE, zoomControls.getVisibility());

        zoomControls.show();
        assertEquals(View.VISIBLE, zoomControls.getVisibility());
    }

    @UiThreadTest
    @Test
    public void testSetIsZoomInEnabled() {
        ZoomControls zoomControls = new ZoomControls(mContext);
        zoomControls.setIsZoomInEnabled(false);
        zoomControls.setIsZoomInEnabled(true);
    }

    @UiThreadTest
    @Test
    public void testSetIsZoomOutEnabled() {
        ZoomControls zoomControls = new ZoomControls(mContext);
        zoomControls.setIsZoomOutEnabled(false);
        zoomControls.setIsZoomOutEnabled(true);
    }

    @Test
    public void testHasFocus() {
        // ZoomControl must not be in touch mode, otherwise zoom buttons won't get focus
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);

        ZoomControls zoomControls = new ZoomControls(mContext);
        try (ActivityScenario<ZoomButtonCtsActivity> scenario =
                     ActivityScenario.launch(ZoomButtonCtsActivity.class)) {

            // Attach ZoomControl to an activity in order to set zoom control not in touch mode
            scenario.onActivity(activity -> {
                activity.setContentView(zoomControls);
                assertFalse(zoomControls.hasFocus());

                assertTrue(zoomControls.requestFocus());
                assertTrue(zoomControls.hasFocus());
            });
        }
    }
}
