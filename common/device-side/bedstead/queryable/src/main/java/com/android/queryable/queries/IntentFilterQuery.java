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

import android.content.IntentFilter;

import com.android.queryable.Queryable;

/**
 * Query for an {@link IntentFilter}.
 *
 * @param <E> Type of query.
 */
public interface IntentFilterQuery<E extends Queryable> extends Query<IntentFilter> {

    /** Queries a {@link IntentFilter}*/
    static IntentFilterQueryHelper.IntentFilterQueryBase intentFilter() {
        return new IntentFilterQueryHelper.IntentFilterQueryBase();
    }

    /** Query for the actions on a intent filter.*/
    SetQuery<E, String> actions();

    /** Query for the categories of an intent filter.*/
    SetQuery<E, String> categories();
}
