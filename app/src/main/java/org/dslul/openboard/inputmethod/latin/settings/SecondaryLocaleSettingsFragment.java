package org.dslul.openboard.inputmethod.latin.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.view.inputmethod.InputMethodSubtype;

import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants;
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter;
import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.DialogUtils;
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SecondaryLocaleSettingsFragment extends SubScreenFragment {
    private RichInputMethodManager mRichImm;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RichInputMethodManager.init(getActivity());
        mRichImm = RichInputMethodManager.getInstance();
        addPreferencesFromResource(R.xml.additional_subtype_settings);
        resetKeyboardLocales();
    }

    private void resetKeyboardLocales() {
        mRichImm.refreshSubtypeCaches();
        getPreferenceScreen().removeAll();
        final Context context = getActivity();
        List<InputMethodSubtype> subtypes = mRichImm.getMyEnabledInputMethodSubtypeList(true);

        for (InputMethodSubtype subtype : subtypes) {
            final List<Locale> secondaryLocales = Settings.getSecondaryLocales(getSharedPreferences(), subtype.getLocale());
            final Locale secondaryLocale = secondaryLocales.size() > 0 ? secondaryLocales.get(0) : null;
            final Preference pref = new Preference(context);
            pref.setTitle(subtype.getDisplayName(context, BuildConfig.APPLICATION_ID, context.getApplicationInfo()));
            if (secondaryLocale != null)
                pref.setSummary(secondaryLocale.getDisplayLanguage(getResources().getConfiguration().locale));

            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showSecondaryLocaleDialog(subtype.getLocale().toLowerCase(Locale.ENGLISH), subtype.isAsciiCapable());
                    return true;
                }
            });
            getPreferenceScreen().addPreference(pref);
        }

    }

    private void showSecondaryLocaleDialog(String mainLocale, boolean asciiCapable) {
        final List<String> locales = new ArrayList<>(getAvailableDictionaryLocales(mainLocale, asciiCapable));
        Collections.sort(locales);

        // we don't want to offer mainLocale as a choice, same goes for the language (e.g. en for en_GB)
        locales.remove(mainLocale);
        if (mainLocale.contains("_")) {
            final String mainLanguage = LocaleUtils.constructLocaleFromString(mainLocale).getLanguage();
            locales.remove(mainLanguage);
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(
                DialogUtils.getPlatformDialogThemeContext(getActivity()))
                .setTitle(R.string.language_selection_title)
                .setPositiveButton(R.string.cancel, null);

        if (locales.isEmpty()) {
            builder.setMessage(R.string.no_secondary_locales)
                    .show();
            return;
        }

        // insert "no secondary language" option on top
        locales.add(0, getResources().getString(R.string.secondary_locale_none));

        final Locale displayLocale = getResources().getConfiguration().locale;
        final CharSequence[] titles = locales.toArray(new CharSequence[0]);
        for (int i = 1; i < titles.length ; i++) {
            final Locale loc = LocaleUtils.constructLocaleFromString(titles[i].toString());
            titles[i] = loc.getDisplayName(displayLocale);
        }

        final List<Locale> secondaryLocales = Settings.getSecondaryLocales(getSharedPreferences(), mainLocale);
        final Locale currentSecondaryLocale = secondaryLocales.size() > 0 ? secondaryLocales.get(0) : null;
        int checkedItem;
        if (currentSecondaryLocale == null)
            checkedItem = 0;
        else
            checkedItem = locales.indexOf(currentSecondaryLocale.toString());

        builder.setSingleChoiceItems(titles, checkedItem, (dialogInterface, i) -> {
            String locale = locales.get(i);
            if (i == 0)
                locale = "";
            final Set<String> encodedLocales = new HashSet<>();
            boolean updated = false;
            for (String encodedLocale : getSharedPreferences().getStringSet(Settings.PREF_SECONDARY_LOCALES, new HashSet<>())) {
                String[] locs = encodedLocale.split("§");
                if (locs.length == 2 && locs[0].equals(mainLocale)) {
                    if (!locale.isEmpty())
                        encodedLocales.add(mainLocale + "§" + locale);
                    updated = true;
                } else {
                    encodedLocales.add(encodedLocale);
                }
            }
            if (!updated)
                encodedLocales.add(mainLocale + "§" + locale);
            getSharedPreferences().edit().putStringSet(Settings.PREF_SECONDARY_LOCALES, encodedLocales).apply();
            final Intent newDictBroadcast = new Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION);
            getActivity().sendBroadcast(newDictBroadcast);
            resetKeyboardLocales();
            dialogInterface.dismiss();
        });

        builder.show();
    }

    // get locales with same script as main locale, but different language
    private Set<String> getAvailableDictionaryLocales(String mainLocale, boolean asciiCapable) {
        final Locale mainL = LocaleUtils.constructLocaleFromString(mainLocale);
        final Set<String> locales = new HashSet<>();
        final int mainScript;
        if (asciiCapable)
            mainScript = ScriptUtils.SCRIPT_LATIN;
        else
            mainScript = ScriptUtils.getScriptFromSpellCheckerLocale(mainL);
        // ScriptUtils.getScriptFromSpellCheckerLocale may return latin when it should not
        //  e.g. for persian or chinese
        // workaround: don't allow secondary locales for these locales
        if (!asciiCapable && mainScript == ScriptUtils.SCRIPT_LATIN)
            return locales;

        // get cached dictionaries: extracted or user-added dictionaries
        final File[] cachedDirectoryList = DictionaryInfoUtils.getCachedDirectoryList(getActivity());
        if (cachedDirectoryList != null) {
            for (File directory : cachedDirectoryList) {
                if (!directory.isDirectory()) continue;
                final String dirLocale =
                        DictionaryInfoUtils.getWordListIdFromFileName(directory.getName());
                if (dirLocale.equals(mainLocale)) continue;
                final Locale locale = LocaleUtils.constructLocaleFromString(dirLocale);
                if (locale.getLanguage().equals(mainL.getLanguage())) continue;
                int localeScript = ScriptUtils.getScriptFromSpellCheckerLocale(locale);
                if (localeScript != mainScript) continue;
                locales.add(locale.toString());
            }
        }
        // get assets dictionaries
        final String[] assetsDictionaryList = BinaryDictionaryGetter.getAssetsDictionaryList(getActivity());
        if (assetsDictionaryList != null) {
            for (String dictionary : assetsDictionaryList) {
                final String dictLocale =
                        BinaryDictionaryGetter.extractLocaleFromAssetsDictionaryFile(dictionary);
                if (dictLocale == null) continue;
                if (dictLocale.equals(mainLocale)) continue;
                final Locale locale = LocaleUtils.constructLocaleFromString(dictLocale);
                if (locale.getLanguage().equals(mainL.getLanguage())) continue;
                int localeScript = ScriptUtils.getScriptFromSpellCheckerLocale(locale);
                if (localeScript != mainScript) continue;
                locales.add(locale.toString());
            }
        }
        return locales;
    }

}
