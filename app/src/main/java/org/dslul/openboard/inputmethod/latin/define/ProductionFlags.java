/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.define;

public final class ProductionFlags {
    private ProductionFlags() {
        // This class is not publicly instantiable.
    }

    public static final boolean IS_HARDWARE_KEYBOARD_SUPPORTED = true;

    /**
     * Include all suggestions from all dictionaries in
     * {@link org.dslul.openboard.inputmethod.latin.SuggestedWords#mRawSuggestions}.
     */
    public static final boolean INCLUDE_RAW_SUGGESTIONS = false;

    /**
     * When false, the metrics logging is not yet ready to be enabled.
     */
    public static final boolean IS_METRICS_LOGGING_SUPPORTED = false;

    /**
     * When {@code false}, the split keyboard is not yet ready to be enabled.
     */
    public static final boolean IS_SPLIT_KEYBOARD_SUPPORTED = true;

}
