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

package android.view.inputmethod.cts.util;

import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

public final class InputConnectionTestUtils {

    private static final String U1F427 = "\uD83D\uDC27";

    /**
     * A utility function to generate test string for input method APIs.  There are several
     * pre-defined meta characters that are useful for unit tests.
     *
     * <p>Pre-defined meta characters:</p>
     * <dl>
     *     <dl>{@code [}</dl><dd>The text selection starts from here.</dd>
     *     <dl>{@code ]}</dl><dd>The text selection ends at here.</dd>
     *     <dl>{@code <}</dl><dd>Represents a high surrogate character.</dd>
     *     <dl>{@code >}</dl><dd>Represents a low surrogate character.</dd>
     * </ul>
     *
     * <p>Examples: {@code "012[3<>67]89"} will be converted to {@ode "0123HL6789"}, where
     * {@code "H"} and {@code "L"} indicate certain high and low surrogate characters, respectively,
     * with selecting {@code "3HL67"}.</p>
     *
     * @param formatString
     * @return A {@link CharSequence} object with text selection specified by the meta characters.
     */
    public static CharSequence formatString(final String formatString) {
        final SpannableStringBuilder builder = new SpannableStringBuilder();
        int selectionStart = -1;
        int selectionEnd = -1;
        for (int i = 0; i < formatString.length(); ++i) {
            final Character c = formatString.charAt(i);
            switch (c) {
                case '[':
                    selectionStart = builder.length();
                    break;
                case ']':
                    selectionEnd = builder.length();
                    break;
                case '<':
                    builder.append(U1F427.charAt(0));  // High surrogate
                    break;
                case '>':
                    builder.append(U1F427.charAt(1));  // Low surrogate
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }
        if (selectionStart < 0) {
            throw new UnsupportedOperationException("Selection marker '[' must be specified.");
        }
        if (selectionEnd < 0) {
            throw new UnsupportedOperationException("Selection marker ']' must be specified.");
        }
        Selection.setSelection(builder, selectionStart, selectionEnd);
        return builder;
    }

    /**
     * A utility method to create an instance of {@link BaseInputConnection}.
     *
     * @return {@link BaseInputConnection} instantiated in the full editor mode with {@code
     *     editable}.
     */
    public static BaseInputConnection createBaseInputConnection() {
        final View view = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
        return new BaseInputConnection(view, true);
    }

    /**
     * A utility method to create an instance of {@link BaseInputConnection} from {@link Editable}.
     *
     * @param editable the initial text.
     * @return {@link BaseInputConnection} instantiated in the full editor mode with {@code
     *     editable}.
     */
    public static BaseInputConnection createBaseInputConnection(@NonNull Editable editable) {
        final View view = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
        return new BaseInputConnection(view, true) {
            @Override
            public Editable getEditable() {
                return editable;
            }
        };
    }

    /**
     * A utility method to create an instance of {@link BaseInputConnection} in the full editor mode
     * with an initial text and selection range.
     *
     * @param source the initial text.
     * @return {@link BaseInputConnection} instantiated in the full editor mode with {@code source}
     *     and selection range from {@code selectionStart} to {@code selectionEnd}
     */
    public static BaseInputConnection createBaseInputConnectionWithSelection(CharSequence source) {
        final int selectionStart = Selection.getSelectionStart(source);
        final int selectionEnd = Selection.getSelectionEnd(source);
        final Editable editable = Editable.Factory.getInstance().newEditable(source);
        Selection.setSelection(editable, selectionStart, selectionEnd);
        return createBaseInputConnection(editable);
    }
}
