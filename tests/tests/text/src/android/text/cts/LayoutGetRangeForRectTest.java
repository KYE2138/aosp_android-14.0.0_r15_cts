/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.RectF;
import android.graphics.Typeface;
import android.icu.util.ULocale;
import android.text.GraphemeClusterSegmentFinder;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.WordSegmentFinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LayoutGetRangeForRectTest {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 1000;
    private static final String DEFAULT_TEXT = ""
            // Line 0 (offset 0 to 18)
            // - Word 0 (offset 0 to 4) has bounds [0, 40], center 20
            // - Word 1 (offset 5 to 11) has bounds [50, 110], center 80
            // - Word 2 (offset 12 to 17) has bounds [120, 170], center 145
            + "XXXX XXXXXX XXXXX "
            // Line 1 (offset 18 to 36)
            // - Word 3 (offset 18 to 23) has bounds [0, 50], center 25
            // - Word 4 (offset 24 to 26, RTL) has bounds [100, 110], center 105
            // - Word 5 (offset 27 to 29, RTL) has bounds [80, 90], center 85
            // - Word 6 start part (offset 30 to 32, RTL) has bounds [60, 70], center 65
            // - Word 6 end part (offset 32 to 35) has bounds [110, 140], center 125
            + "XXXXX \u05D1\u05D1 \u05D1\u05D1 \u05D1\u05D1XXX\n"
            // Line 2 (offset 36 to 38)
            // - Word 7 start part (offset 36 to 38) has bounds [0, 150], center 75
            // Line 3 (offset 38 to 40)
            // - Word 7 middle part (offset 38 to 40) has bounds [0, 150], center 75
            // Line 4 (offset 40 to 46)
            // - Word 7 end part (offset 40 to 41) has bounds [0, 100], center 50
            // - Word 8 (offset 42 to 44) has bounds [110, 130], center 120
            + "CLCLC XX \n";

    private Layout mLayout;
    private float[] mLineCenters;
    private GraphemeClusterSegmentFinder mGraphemeClusterSegmentFinder;
    private WordSegmentFinder mWordSegmentFinder;

    @Before
    public void setup() {
        // The test font includes the following characters:
        // U+0020 ( ): 10em
        // U+002E (.): 10em
        // U+0049 (I): 1em
        // U+0056 (V): 5em
        // U+0058 (X): 10em
        // U+004C (L): 50em
        // U+0043 (C): 100em
        // U+005F (_): 0em
        // U+05D0    : 1em  // HEBREW LETTER ALEF
        // U+05D1    : 5em  // HEBREW LETTER BET
        // U+FFFD (invalid surrogate will be replaced to this): 7em
        // U+10331 (\uD800\uDF31): 10em
        // Undefined : 0.5em
        TextPaint textPaint = new TextPaint();
        textPaint.setTypeface(
                Typeface.createFromAsset(
                        InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets(),
                        "fonts/StaticLayoutLineBreakingTestFont.ttf"));
        // Make 1 em equal to 1 pixel.
        textPaint.setTextSize(1.0f);

        mLayout = StaticLayout.Builder.obtain(
                DEFAULT_TEXT, 0, DEFAULT_TEXT.length(), textPaint, WIDTH).build();

        mLineCenters = new float[mLayout.getLineCount()];
        for (int i = 0; i < mLayout.getLineCount(); ++i) {
            mLineCenters[i] = (mLayout.getLineTop(i)
                    + mLayout.getLineBottom(i, /* includeLineSpacing= */ false)) / 2f;
        }

        mGraphemeClusterSegmentFinder = new GraphemeClusterSegmentFinder(DEFAULT_TEXT, textPaint);
        mWordSegmentFinder = new WordSegmentFinder(DEFAULT_TEXT, ULocale.US);
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_anyOverlap() {
        // Character 37 on line 2
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(37) + 2f,
                mLayout.getLineTop(2),
                mLayout.getPrimaryHorizontal(37) + 3f,
                mLayout.getLineTop(2) + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_ANY_OVERLAP);

        assertThat(range).asList().containsExactly(37, 38).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_anyOverlap_multiLine() {
        // Character 4 on line 0
        // Character 37 on line 2
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(4) + 1f,
                mLayout.getLineBottom(0, /* includeLineSpacing= */ false) - 1,
                mLayout.getPrimaryHorizontal(37) + 1f,
                mLayout.getLineTop(2) + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_ANY_OVERLAP);

        assertThat(range).asList().containsExactly(4, 38).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter() {
        // Character 1 on line 0 has center 15.
        // Character 2 on line 0 has center 25.
        RectF area = new RectF(14f, mLineCenters[0] - 1f, 26f, mLineCenters[0] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(1, 3).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_partialCharacterButNotCenter() {
        // Character 0 on line 0 has center 5.
        // Character 1 on line 0 has center 15.
        // Character 2 on line 0 has center 25.
        // Character 3 on line 0 has center 35.
        RectF area = new RectF(6f, mLineCenters[0] - 1f, 34f, mLineCenters[0] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area partially overlaps characters 0 and 3 but does not contain their centers.
        assertThat(range).asList().containsExactly(1, 3).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_rtl() {
        // Character 25 on line 1 has center 102.5.
        // Character 26 on line 1 has center 95.
        RectF area = new RectF(94f, mLineCenters[1] - 1f, 103f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(25, 27).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_ltrAndRtl() {
        // Character 22 on line 1 has center 45.
        // The end of the RTL run (offset 24 to 32) on line 1 is at 60.
        RectF area = new RectF(44f, mLineCenters[1] - 1f, 93f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(22, 32).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_rtlAndLtr() {
        // The start of the RTL run (offset 24 to 32) on line 1 is at 110.
        // Character 33 on line 1 has center 125.
        RectF area = new RectF(93f, mLineCenters[1] - 1f, 131f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(24, 34).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_betweenCharacters_returnsNull() {
        // Character 1 on line 0 has center 15.
        // Character 2 on line 0 has center 25.
        RectF area = new RectF(16f, mLineCenters[0] - 1f, 24f, mLineCenters[0] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_betweenLines_returnsNull() {
        // Area top is below the center of line 0.
        // Area bottom is above the center of line 1.
        RectF area = new RectF(0f, mLineCenters[0] + 1f, WIDTH, mLineCenters[1] - 1f);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area partially covers two lines but does not contain the center of any characters.
        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_multiLine() {
        // Character 9 on line 0 has center 95.
        // Character 42 on line 4 has center 115.
        RectF area = new RectF(93f, 0, 118f, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(9, 43).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_multiLine_betweenCharactersOnSomeLines() {
        // Character 6 on line 0 has center 65.
        // Character 7 on line 0 has center 75.
        // Character 30 on line 1 has center 67.5.
        // Character 36 on line 2 has center 50.
        // Character 37 on line 2 has center 125.
        // Character 38 on line 3 has center 50.
        // Character 39 on line 3 has center 125.
        // Character 40 on line 4 has center 50.
        // Character 41 on line 4 has center 105.
        RectF area = new RectF(66f, 0, 69f, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area crosses all lines but does not contain the center of any characters on lines 0, 2,
        // 3, or 4. So the only included character is character 30 on line 1.
        assertThat(range).asList().containsExactly(30, 31).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_multiLine_betweenCharactersOnAllLines_returnsNull() {
        // Character 6 on line 0 has center 65.
        // Character 7 on line 0 has center 75.
        // Character 30 on line 1 has center 67.5.
        // Character 31 on line 1 has center 62.5.
        // Character 36 on line 2 has center 50.
        // Character 37 on line 2 has center 125.
        // Character 38 on line 3 has center 50.
        // Character 39 on line 3 has center 125.
        // Character 40 on line 4 has center 50.
        // Character 41 on line 4 has center 105.
        RectF area = new RectF(66f, 0, 67f, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area crosses all lines but does not contain the center of any characters.
        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsCenter_all() {
        // Entire area, should include all text.
        RectF area = new RectF(0f, 0f, WIDTH, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(0, DEFAULT_TEXT.length()).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsAll() {
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(3) - 1,
                mLayout.getLineTop(0) - 1,
                mLayout.getPrimaryHorizontal(13) + 1,
                mLayout.getLineBottom(0, /* includeLineSpacing= */ false) + 1);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        assertThat(range).asList().containsExactly(3, 13).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsAll_charactersNotFullyCovered() {
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(3) + 1,
                mLayout.getLineTop(0) - 1,
                mLayout.getPrimaryHorizontal(13) - 1,
                mLayout.getLineBottom(0, /* includeLineSpacing= */ false) + 1);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        // The left edge of the area is 1 pixel to the right of the left bound of character 3, so it
        // does not fully cover the character.
        // The right edge of the area is 1 pixel to the left of the right bound of character 12, so
        // it does not fully cover the character.
        assertThat(range).asList().containsExactly(4, 12).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsAll_twoLines() {
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(3) - 1,
                mLayout.getLineTop(0) - 1,
                mLayout.getPrimaryHorizontal(33) + 1,
                mLayout.getLineBottom(1, /* includeLineSpacing= */ false) + 1);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        assertThat(range).asList().containsExactly(3, 33).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsAll_rtl() {
        // Characters 24 to 32 on line 1 are RTL.
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(31) - 1,
                mLayout.getLineTop(1) - 1,
                mLayout.getPrimaryHorizontal(27) + 1,
                mLayout.getLineBottom(1, /* includeLineSpacing= */ false) + 1);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        assertThat(range).asList().containsExactly(27, 31).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void character_containsAll_linesNotFullyCovered_returnsNull() {
        RectF area = new RectF(
                0f,
                mLayout.getLineTop(0) + 1,
                WIDTH,
                mLayout.getLineBottom(1, /* includeLineSpacing= */ false) - 1);
        int[] range = mLayout.getRangeForRect(
                area, mGraphemeClusterSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        // The top edge of the area is 1 pixel below the top bound of the characters on line 0, so
        // it does not fully cover any of them.
        // The bottom edge of the area is 1 pixel is 1 pixel above the bottom bound of the
        // characters on line 1, so it does not fully cover any of them.
        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_anyOverlap() {
        // Character 8 on line 0 is in word 1 (offset 5 to 11)
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(8) + 2f,
                mLayout.getLineTop(0),
                mLayout.getPrimaryHorizontal(8) + 3f,
                mLayout.getLineTop(0) + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_ANY_OVERLAP);

        // Selects all of word 1 (offset 5 to 11)
        assertThat(range).asList().containsExactly(5, 11).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_anyOverlap_wordSpansMultipleLines_middlePartInsideArea() {
        // Character 37 on line 2 is in word 7 (offset 36 to 41)
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(37) + 2f,
                mLayout.getLineTop(2),
                mLayout.getPrimaryHorizontal(37) + 3f,
                mLayout.getLineTop(2) + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_ANY_OVERLAP);

        // Selects all of word 7, not just the first part on line 2.
        assertThat(range).asList().containsExactly(36, 41).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter() {
        // Word 1 (offset 5 to 11) on line 0 has center 80.
        RectF area = new RectF(79f, mLineCenters[0] - 1f, 81f, mLineCenters[0] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(5, 11).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_partialWordButNotCenter() {
        // Word 0 (offset 0 to 4) on line 0 has center 20.
        // Word 1 (offset 5 to 11) on line 0 has center 80.
        // Word 2 (offset 12 to 17) on line 0 center 145
        RectF area = new RectF(21f, mLineCenters[0] - 1f, 144f, mLineCenters[0] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area partially overlaps words 0 and 2 but does not contain their centers, so only word 1
        // is included. Whitespace between words is not included.
        assertThat(range).asList().containsExactly(5, 11).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_rtl() {
        // Word 4 (offset 24 to 26, RTL) on line 1 center 105
        RectF area = new RectF(88f, mLineCenters[1] - 1f, 119f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(24, 26).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_ltrAndRtl() {
        // Word 3 (offset 18 to 23) on line 1 has center 25
        // The end of the RTL run (offset 24 to 32) on line 1 is at 60.
        RectF area = new RectF(24f, mLineCenters[1] - 1f, 93f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Selects all of word 6, not just the first RTL part.
        assertThat(range).asList().containsExactly(18, 35).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_rtlAndLtr() {
        // The start of the RTL run (offset 24 to 32) on line 1 is at 110.
        // End part of word 6 (offset 32 to 35) on line 1 has center 125.
        RectF area = new RectF(93f, mLineCenters[1] - 1f, 174f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(24, 35).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_betweenWords_returnsNull() {
        // Word 1 on line 0 has center 80.
        // Word 2 on line 0 has center 145.
        RectF area = new RectF(81f, mLineCenters[0] - 1f, 144f, mLineCenters[0] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_betweenLines_returnsNull() {
        // Area top is below the center of line 0.
        // Area bottom is above the center of line 1.
        RectF area = new RectF(0f, mLineCenters[0] + 1f, WIDTH, mLineCenters[1] - 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area partially covers two lines but does not contain the center of any words.
        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_multiLine() {
        // Word 1 (offset 5 to 11) on line 0 has center 80.
        // End part of word 7 (offset 40 to 41) on line 4 has center 50.
        RectF area = new RectF(42f, 0, 91f, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(5, 41).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_multiLine_betweenWordsOnSomeLines() {
        // Word 1 on line 0 has center 80.
        // Word 2 on line 0 has center 145.
        // Word 5 (offset 27 to 29) on line 1 has center 85.
        // Word 7 on line 2 has center 50.
        // Word 37 on line 2 has center 125.
        // Word 38 on line 3 has center 50.
        // Word 39 on line 3 has center 125.
        // Word 40 on line 4 has center 50.
        // Word 41 on line 4 has center 105.
        RectF area = new RectF(84f, 0, 86f, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area crosses all lines but does not contain the center of any words on lines 0, 2, 3, or
        // 4. So the only included word is word 5 on line 1.
        assertThat(range).asList().containsExactly(27, 29).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_multiLine_betweenCharactersOnAllLines_returnsNull() {
        // Word 1 on line 0 has center 80.
        // Word 2 on line 0 has center 145.
        // Word 4 on line 1 has center 105.
        // Word 5 on line 1 has center 85.
        // Word 7 on line 2 has center 50.
        // Word 37 on line 2 has center 125.
        // Word 38 on line 3 has center 50.
        // Word 39 on line 3 has center 125.
        // Word 40 on line 4 has center 50.
        // Word 41 on line 4 has center 105.
        RectF area = new RectF(86f, 0, 89f, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Area crosses all lines but does not contain the center of any words.
        assertThat(range).isNull();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_wordSpansMultipleLines_firstPartInsideArea() {
        // Word 5 (offset 27 to 29) on line 1 has center 85.
        // First part of word 7 (offset 36 to 38) on line 2 has center 75.
        RectF area = new RectF(74f, mLineCenters[1] - 1f, 86f, mLineCenters[2] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Selects all of word 7, not just the first part on line 2.
        assertThat(range).asList().containsExactly(27, 41).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_wordSpansMultipleLines_middlePartInsideArea() {
        // Middle part of word 7 (offset 38 to 40) on line 2 has center 75.
        RectF area = new RectF(74f, mLineCenters[3] - 1f, 76f, mLineCenters[3] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Selects all of word 7, not just the middle part on line 3.
        assertThat(range).asList().containsExactly(36, 41).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_wordSpansMultipleLines_endPartInsideArea() {
        // End part of word 7 (offset 40 to 41) on line 4 has center 50.
        // Word 8 (offset 42 to 44) on line 4 has center 120
        RectF area = new RectF(49f, mLineCenters[4] - 1f, 121f, mLineCenters[4] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Selects all of word 7, not just the middle part on line 3.
        assertThat(range).asList().containsExactly(36, 44).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_wordSpansMultipleRuns_firstPartInsideArea() {
        // Word 5 (offset 27 to 29) on line 1 has center 85.
        // First part of word 6 (offset 30 to 32) on line 1 has center 65.
        RectF area = new RectF(64f, mLineCenters[1] - 1f, 86f, mLineCenters[1] + 1f);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        // Selects all of word 6, not just the first RTL part.
        assertThat(range).asList().containsExactly(27, 35).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsCenter_all() {
        // Entire area, should include all text except the last two whitespace characters.
        RectF area = new RectF(0f, 0f, WIDTH, HEIGHT);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER);

        assertThat(range).asList().containsExactly(0, DEFAULT_TEXT.length() - 2).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsAll() {
        // Word 1 (offset 5 to 11) on line 0
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(5) - 1,
                mLayout.getLineTop(0) - 1,
                mLayout.getPrimaryHorizontal(11) + 1,
                mLayout.getLineBottom(0, /* includeLineSpacing= */ false) + 1);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        assertThat(range).asList().containsExactly(5, 11).inOrder();
    }

    @Test
    @ApiTest(apis = "android.text.Layout#getRangeForRect")
    public void word_containsAll_notFullyCovered_returnsNull() {
        // Word 1 (offset 5 to 11) on line 0
        RectF area = new RectF(
                mLayout.getPrimaryHorizontal(5) + 1,
                mLayout.getLineTop(0) - 1,
                mLayout.getPrimaryHorizontal(11) + 1,
                mLayout.getLineBottom(0, /* includeLineSpacing= */ false) + 1);
        int[] range = mLayout.getRangeForRect(
                area, mWordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_ALL);

        // The left edge of the area is 1 pixel to the right of the left bound of character 5, so it
        // does not fully cover the word.
        assertThat(range).isNull();
    }
}
