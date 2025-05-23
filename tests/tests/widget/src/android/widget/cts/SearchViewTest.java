/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Resources;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.SearchView;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link SearchView}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SearchViewTest {
    private Activity mActivity;
    private SearchView mSearchView;
    private Instrumentation mInstrumentation;
    private CtsKeyEventUtil mCtsKeyEventUtil;

    @Rule
    public ActivityTestRule<SearchViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(SearchViewCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mCtsKeyEventUtil = new CtsKeyEventUtil(mInstrumentation.getTargetContext());
        mActivity = mActivityRule.getActivity();
        mSearchView = (SearchView) mActivity.findViewById(R.id.search_view);

        PollingCheck.waitFor(() -> mActivity.hasWindowFocus());
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new SearchView(mActivity);

        new SearchView(mActivity, null);

        new SearchView(mActivity, null, android.R.attr.searchViewStyle);

        new SearchView(mActivity, null, 0, android.R.style.Widget_Material_SearchView);

        new SearchView(mActivity, null, 0, android.R.style.Widget_Material_Light_SearchView);
    }

    @UiThreadTest
    @Test
    public void testAttributesFromXml() {
        SearchView searchViewWithAttributes =
                (SearchView) mActivity.findViewById(R.id.search_view_with_defaults);
        assertEquals(mActivity.getString(R.string.search_query_hint),
                searchViewWithAttributes.getQueryHint());
        assertFalse(searchViewWithAttributes.isIconfiedByDefault());
        assertEquals(EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS | EditorInfo.TYPE_CLASS_TEXT,
                searchViewWithAttributes.getInputType());
        assertEquals(EditorInfo.IME_ACTION_DONE, searchViewWithAttributes.getImeOptions());
        assertEquals(mActivity.getResources().getDimensionPixelSize(R.dimen.search_view_max_width),
                searchViewWithAttributes.getMaxWidth());
    }

    @UiThreadTest
    @Test
    public void testAccessIconified() {
        mSearchView.setIconified(true);
        assertTrue(mSearchView.isIconified());

        mSearchView.setIconified(false);
        assertFalse(mSearchView.isIconified());
    }

    @UiThreadTest
    @Test
    public void testAccessIconifiedByDefault() {
        mSearchView.setIconifiedByDefault(true);
        assertTrue(mSearchView.isIconfiedByDefault());

        mSearchView.setIconifiedByDefault(false);
        assertFalse(mSearchView.isIconfiedByDefault());
    }

    @Test
    public void testDenyIconifyingNonInconifiableView() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSearchView.setIconifiedByDefault(false);
            mSearchView.setIconified(false);
        });

        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mSearchView,
                () -> mSearchView.setIconified(true), true);

        // Since our search view is marked with iconifiedByDefault=false, call to setIconified
        // with true us going to be ignored, as detailed in the class-level documentation of
        // SearchView.
        assertFalse(mSearchView.isIconified());
    }

    @Test
    public void testDenyIconifyingInconifiableView() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSearchView.setIconifiedByDefault(true);
            mSearchView.setIconified(false);
        });

        final SearchView.OnCloseListener mockDenyCloseListener =
                mock(SearchView.OnCloseListener.class);
        when(mockDenyCloseListener.onClose()).thenReturn(Boolean.TRUE);
        mSearchView.setOnCloseListener(mockDenyCloseListener);

        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mSearchView,
                () -> mSearchView.setIconified(true), true);

        // Our mock listener is configured to return true from its onClose, thereby preventing
        // the iconify request to be completed. Check that the listener was called and that the
        // search view is not iconified.
        verify(mockDenyCloseListener, times(1)).onClose();
        assertFalse(mSearchView.isIconified());
    }

    @Test
    public void testAllowIconifyingInconifiableView() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSearchView.setIconifiedByDefault(true);
            mSearchView.setIconified(false);
        });

        final SearchView.OnCloseListener mockAllowCloseListener =
                mock(SearchView.OnCloseListener.class);
        when(mockAllowCloseListener.onClose()).thenReturn(Boolean.FALSE);
        mSearchView.setOnCloseListener(mockAllowCloseListener);

        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mSearchView,
                () -> mSearchView.setIconified(true), true);

        // Our mock listener is configured to return false from its onClose, thereby allowing
        // the iconify request to be completed. Check that the listener was called and that the
        // search view is not iconified.
        verify(mockAllowCloseListener, times(1)).onClose();
        assertTrue(mSearchView.isIconified());
    }

    @Test
    public void testAccessMaxWidth() throws Throwable {
        final Resources res = mActivity.getResources();
        final int maxWidth1 = res.getDimensionPixelSize(R.dimen.search_view_max_width);
        final int maxWidth2 = res.getDimensionPixelSize(R.dimen.search_view_max_width2);

        // Set search view to not be iconified before running max-width tests
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mSearchView,
                () -> {
                    mSearchView.setIconified(false);
                    mSearchView.setMaxWidth(maxWidth1);
                }, true);
        assertEquals(maxWidth1, mSearchView.getMaxWidth());
        assertTrue(mSearchView.getWidth() <= maxWidth1);

        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mSearchView,
                () -> mSearchView.setMaxWidth(maxWidth2), true);
        assertEquals(maxWidth2, mSearchView.getMaxWidth());
        assertTrue(mSearchView.getWidth() <= maxWidth2);
    }

    @UiThreadTest
    @Test
    public void testAccessQuery() {
        mSearchView.setIconified(false);

        final SearchView.OnQueryTextListener mockQueryTextListener =
                mock(SearchView.OnQueryTextListener.class);
        when(mockQueryTextListener.onQueryTextSubmit(anyString())).thenReturn(Boolean.TRUE);
        mSearchView.setOnQueryTextListener(mockQueryTextListener);

        mSearchView.setQuery("alpha", false);
        assertTrue(TextUtils.equals("alpha", mSearchView.getQuery()));
        // Since we passed false as the second parameter to setQuery, our query text listener
        // should have been invoked only with text change
        verify(mockQueryTextListener, times(1)).onQueryTextChange("alpha");
        verify(mockQueryTextListener, never()).onQueryTextSubmit(anyString());

        mSearchView.setQuery("beta", true);
        assertTrue(TextUtils.equals("beta", mSearchView.getQuery()));
        // Since we passed true as the second parameter to setQuery, our query text listener
        // should have been invoked on both callbacks
        verify(mockQueryTextListener, times(1)).onQueryTextChange("beta");
        verify(mockQueryTextListener, times(1)).onQueryTextSubmit("beta");

        mSearchView.setQuery("gamma", true);
        assertTrue(TextUtils.equals("gamma", mSearchView.getQuery()));
        // Since we passed true as the second parameter to setQuery, our query text listener
        // should have been invoked on both callbacks
        verify(mockQueryTextListener, times(1)).onQueryTextChange("gamma");
        verify(mockQueryTextListener, times(1)).onQueryTextSubmit("gamma");

        verifyNoMoreInteractions(mockQueryTextListener);
    }

    @UiThreadTest
    @Test
    public void testAccessQueryHint() {
        mSearchView.setQueryHint("hint 1");
        assertTrue(TextUtils.equals("hint 1", mSearchView.getQueryHint()));

        mSearchView.setQueryHint("hint 2");
        assertTrue(TextUtils.equals("hint 2", mSearchView.getQueryHint()));
    }

    @UiThreadTest
    @Test
    public void testAccessInputType() {
        mSearchView.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        assertEquals(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, mSearchView.getInputType());

        mSearchView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, mSearchView.getInputType());

        mSearchView.setInputType(InputType.TYPE_CLASS_PHONE);
        assertEquals(InputType.TYPE_CLASS_PHONE, mSearchView.getInputType());
    }

    @UiThreadTest
    @Test
    public void testAccessImeOptions() {
        mSearchView.setImeOptions(EditorInfo.IME_ACTION_GO);
        assertEquals(EditorInfo.IME_ACTION_GO, mSearchView.getImeOptions());

        mSearchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        assertEquals(EditorInfo.IME_ACTION_DONE, mSearchView.getImeOptions());

        mSearchView.setImeOptions(EditorInfo.IME_NULL);
        assertEquals(EditorInfo.IME_NULL, mSearchView.getImeOptions());
    }

    @Test
    public void testEnterKey() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSearchView.setIconifiedByDefault(false);
            mSearchView.setIconified(false);
        });

        final SearchView.OnQueryTextListener mockQueryTextListener =
                mock(SearchView.OnQueryTextListener.class);
        when(mockQueryTextListener.onQueryTextSubmit(anyString())).thenReturn(Boolean.TRUE);
        mSearchView.setOnQueryTextListener(mockQueryTextListener);

        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mSearchView, () -> {
            mSearchView.setQuery("alpha", false);
            mSearchView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        mCtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mSearchView, KeyEvent.KEYCODE_ENTER);

        verify(mockQueryTextListener, times(1)).onQueryTextChange("alpha");
        verify(mockQueryTextListener, atLeastOnce()).onQueryTextSubmit("alpha");

        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mSearchView, () -> {
            mSearchView.setQuery("beta", false);
            mSearchView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        mCtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mSearchView,
                KeyEvent.KEYCODE_NUMPAD_ENTER);

        verify(mockQueryTextListener, times(1)).onQueryTextChange("beta");
        verify(mockQueryTextListener, atLeastOnce()).onQueryTextSubmit("beta");

        verifyNoMoreInteractions(mockQueryTextListener);
    }

}
