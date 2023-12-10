/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.RunInLocale;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final ArrayList<String[]> mTextsTables = new ArrayList<>();

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
        mTextsTables.clear();
        if (Settings.getInstance().getCurrent().mShowMoreKeys > 0) {
            mTextsTables.add(KeyboardTextsTable.getTextsTable(new Locale(SubtypeLocaleUtils.NO_LANGUAGE)));
            return;
        }
        mTextsTables.add(KeyboardTextsTable.getTextsTable(locale));
        if (locale != RichInputMethodManager.getInstance().getCurrentSubtypeLocale())
            return; // emojiCategory calls this several times with "zz" locale
        for (final Locale secondaryLocale : Settings.getInstance().getCurrent().mSecondaryLocales) {
            mTextsTables.add(KeyboardTextsTable.getTextsTable(secondaryLocale));
        }
    }

    private String getTextInternal(final String name, final int localeIndex) {
        return KeyboardTextsTable.getText(name, mTextsTables.get(localeIndex));
    }

    public String getText(final String name) {
        Log.w(getClass().getSimpleName(), "still used for resolving "+name);
        return getTextInternal(name, 0); // only used for emoji and clipboard keyboards
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
    // todo: this style of merging for different locales it not good, but how to do it better?
    public String resolveTextReference(final String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return null;
        }
        if (mTextsTables.size() == 1 || !rawText.startsWith("!text/more")) {
            // no need for locale-specific stuff, as they are used for moreKeys only
            String text = resolveTextReferenceInternal(rawText, 0);
            if (text.isEmpty())
                return null;
            return text;
        }
        // get for all languages and merge if necessary
        // this is considerably slower than the simple version above, but still for all ~60 calls
        // when creation a keyboard, that's only a few ms on S4 mini -> should be acceptable
        final ArrayList<String> texts = new ArrayList<>(mTextsTables.size());
        for (int i = 0; i < mTextsTables.size(); i++) {
            final String text = resolveTextReferenceInternal(rawText, i);
            if (text.length() == 0)
                continue;
            texts.add(text);
        }
        if (texts.isEmpty())
            return null;
        if (texts.size() == 1)
            return texts.get(0);
        final LinkedHashSet<String> moreKeys = new LinkedHashSet<>();
        for (final String text : texts) {
            // no thanks linter, we don't want to create an intermediate list
            for (final String c : text.split(",")) {
                moreKeys.add(c);
            }
        }
        return String.join(",", moreKeys);
    }

    public String resolveTextReferenceInternal(final String rawText, final int localeIndex) {
        int level = 0;
        String text = rawText;
        StringBuilder sb;
        final int prefixLength = PREFIX_TEXT.length();
        do {
            level++;
            if (level >= MAX_REFERENCE_INDIRECTION) {
                throw new RuntimeException("Too many " + PREFIX_TEXT + " or " + PREFIX_RESOURCE +
                        " reference indirection: " + text);
            }

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
                    pos = expandReference(text, pos, PREFIX_TEXT, sb, localeIndex);
                } else if (text.startsWith(PREFIX_RESOURCE, pos)) {
                    if (sb == null) {
                        sb = new StringBuilder(text.substring(0, pos));
                    }
                    pos = expandReference(text, pos, PREFIX_RESOURCE, sb, localeIndex);
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
        return text;
    }

    private int expandReference(final String text, final int pos, final String prefix,
            final StringBuilder sb, final int localeIndex) {
        final int prefixLength = prefix.length();
        final int end = searchTextNameEnd(text, pos + prefixLength);
        final String name = text.substring(pos + prefixLength, end);
        if (prefix.equals(PREFIX_TEXT)) {
            sb.append(getTextInternal(name, localeIndex));
        } else { // PREFIX_RESOURCE
            final String resourcePackageName = mResourcePackageName;
            final RunInLocale<String> getTextJob = new RunInLocale<String>() {
                @Override
                protected String job(final Resources res) {
                    // this is for identifiers in strings-action-keys.xml (100% sure nothing else?)
                    final int resId = res.getIdentifier(name, "string", resourcePackageName);
                    return res.getString(resId);
                }
            };
            // no need to do it in locale, it's just labels
            sb.append(getTextJob.runInLocale(mResources, mResourceLocale));
        }
        return end - 1;
    }
}
