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

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.utils.RunInLocale;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;

// TODO: Make this an immutable class.
public final class KeyboardTextsSet {
    public static final String PREFIX_TEXT = "!text/";
    private static final String PREFIX_RESOURCE = "!string/";
    public static final String SWITCH_TO_ALPHA_KEY_LABEL = "keylabel_to_alpha";

    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final int MAX_REFERENCE_INDIRECTION = 10;

    private Resources mResources;
    private Locale mResourceLocale;
    private String mResourcePackageName;
    private String[] mTextsTable;

    public void setLocale(final Locale locale, final Context context) {
        final Resources res = context.getResources();
        // Null means the current system locale.
        final String resourcePackageName = res.getResourcePackageName(
                context.getApplicationInfo().labelRes);
        setLocale(locale, res, resourcePackageName);
    }

    @UsedForTesting
    public void setLocale(final Locale locale, final Resources res,
            final String resourcePackageName) {
        mResources = res;
        // Null means the current system locale.
        mResourceLocale = SubtypeLocaleUtils.NO_LANGUAGE.equals(locale.toString()) ? null : locale;
        mResourcePackageName = resourcePackageName;
        mTextsTable = KeyboardTextsTable.getTextsTable(locale);
    }

    public String getText(final String name) {
        return KeyboardTextsTable.getText(name, mTextsTable);
    }

    private static int searchTextNameEnd(final String text, final int start) {
        final int size = text.length();
        for (int pos = start; pos < size; pos++) {
            final char c = text.charAt(pos);
            // Label name should be consisted of [a-zA-Z_0-9].
            if ((c >= 'a' && c <= 'z') || c == '_' || (c >= '0' && c <= '9')) {
                continue;
            }
            return pos;
        }
        return size;
    }

    // TODO: Resolve text reference when creating {@link KeyboardTextsTable} class.
    public String resolveTextReference(final String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return null;
        }
        int level = 0;
        String text = rawText;
        StringBuilder sb;
        do {
            level++;
            if (level >= MAX_REFERENCE_INDIRECTION) {
                throw new RuntimeException("Too many " + PREFIX_TEXT + " or " + PREFIX_RESOURCE +
                        " reference indirection: " + text);
            }

            final int prefixLength = PREFIX_TEXT.length();
            final int size = text.length();
            if (size < prefixLength) {
                break;
            }

            sb = null;
            for (int pos = 0; pos < size; pos++) {
                final char c = text.charAt(pos);
                if (text.startsWith(PREFIX_TEXT, pos)) {
                    if (sb == null) {
                        sb = new StringBuilder(text.substring(0, pos));
                    }
                    pos = expandReference(text, pos, PREFIX_TEXT, sb);
                } else if (text.startsWith(PREFIX_RESOURCE, pos)) {
                    if (sb == null) {
                        sb = new StringBuilder(text.substring(0, pos));
                    }
                    pos = expandReference(text, pos, PREFIX_RESOURCE, sb);
                } else if (c == BACKSLASH) {
                    if (sb != null) {
                        // Append both escape character and escaped character.
                        sb.append(text.substring(pos, Math.min(pos + 2, size)));
                    }
                    pos++;
                } else if (sb != null) {
                    sb.append(c);
                }
            }

            if (sb != null) {
                text = sb.toString();
            }
        } while (sb != null);
        return TextUtils.isEmpty(text) ? null : text;
    }

    private int expandReference(final String text, final int pos, final String prefix,
            final StringBuilder sb) {
        final int prefixLength = prefix.length();
        final int end = searchTextNameEnd(text, pos + prefixLength);
        final String name = text.substring(pos + prefixLength, end);
        if (prefix.equals(PREFIX_TEXT)) {
            sb.append(getText(name));
        } else { // PREFIX_RESOURCE
            final String resourcePackageName = mResourcePackageName;
            final RunInLocale<String> getTextJob = new RunInLocale<String>() {
                @Override
                protected String job(final Resources res) {
                    // this is for identifiers in strings-action-keys.xml (100% only?)
                    final int resId = res.getIdentifier(name, "string", resourcePackageName);
                    return res.getString(resId);
                }
            };
            sb.append(getTextJob.runInLocale(mResources, mResourceLocale));
        }
        return end - 1;
    }
}
