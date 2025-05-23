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

package com.android.interactive.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a class implements the automation for some Interactive Step.
 *

 *
 * <p>This automation will be pulled in at runtime. If the step being automated does not exist,
 * either because the String is wrong or the Step doesn't exist - then it will be ignored.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutomationFor {

    /** Te fully qualified classname of the step.
     *
     * <p>For example
     * "com.android.interactive.steps.enterprise.settings.AccountsRemoveWorkProfileStep"
     */
    String value();
}
