/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.queryable.queries;

import android.os.Bundle;

import androidx.annotation.CheckResult;

import com.android.queryable.Queryable;

/** Query for a {@link Bundle}. */
public interface BundleQuery<E extends Queryable> extends Query<Bundle> {

    /** Queries a {@link Bundle}. */
    static BundleQueryHelper.BundleQueryBase bundle() {
        return new BundleQueryHelper.BundleQueryBase();
    }

    /** Queries a given key on the {@link Bundle}. */
    @CheckResult
    BundleKeyQuery<E> key(String key);
}
