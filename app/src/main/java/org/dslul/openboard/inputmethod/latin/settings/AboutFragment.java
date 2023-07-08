/*
 * Copyright (C) 2014 The Android Open Source Project
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

 package org.dslul.openboard.inputmethod.latin.settings;

 import android.os.Bundle;
 import android.preference.Preference;
 
 import org.dslul.openboard.inputmethod.latin.BuildConfig;
 import org.dslul.openboard.inputmethod.latin.R;
 
 /**
  * "About" sub screen.
  */
 public final class AboutFragment extends SubScreenFragment {
     @Override
     public void onCreate(final Bundle icicle) {
         super.onCreate(icicle);
         addPreferencesFromResource(R.xml.prefs_screen_about);
         Preference versionPreference = findPreference("pref_key_version");
         versionPreference.setSummary(BuildConfig.VERSION_NAME);
     }
 }