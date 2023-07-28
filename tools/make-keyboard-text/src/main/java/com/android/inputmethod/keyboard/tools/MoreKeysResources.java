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

package com.android.inputmethod.keyboard.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;
import java.util.jar.JarFile;

public class MoreKeysResources {
    private static final String TEXT_RESOURCE_NAME = "donottranslate-more-keys.xml";

    private static final String JAVA_TEMPLATE = "KeyboardTextsTable.tmpl";
    private static final String MARK_NAMES = "@NAMES@";
    private static final String MARK_DEFAULT_TEXTS = "@DEFAULT_TEXTS@";
    private static final String MARK_TEXTS = "@TEXTS@";
    private static final String TEXTS_ARRAY_NAME_PREFIX = "TEXTS_";
    private static final String MARK_LOCALES_AND_TEXTS = "@LOCALES_AND_TEXTS@";
    private static final String EMPTY_STRING_VAR = "EMPTY";

    private final JarFile mJar;
    // String resources maps sorted by its language. The language is determined from the jar entry
    // name by calling {@link JarUtils#getLocaleFromEntryName(String)}.
    private final TreeMap<String, StringResourceMap> mResourcesMap = new TreeMap<>();
    // Default string resources map.
    private final StringResourceMap mDefaultResourceMap;
    // Histogram of string resource names. This is used to sort {@link #mSortedResourceNames}.
    private final HashMap<String, Integer> mNameHistogram = new HashMap<>();
    // Sorted string resource names array; Descending order of histogram count.
    // The string resource name is specified as an attribute "name" in string resource files.
    // The string resource can be accessed by specifying name "!text/<name>"
    // via {@link KeyboardTextsSet#getText(String)}.
    private final String[] mSortedResourceNames;

    public MoreKeysResources(final JarFile jar) {
        mJar = jar;
        final ArrayList<String> resourceEntryNames = JarUtils.getEntryNameListing(
                jar, TEXT_RESOURCE_NAME);
        for (final String entryName : resourceEntryNames) {
            final StringResourceMap resMap = new StringResourceMap(entryName);
            mResourcesMap.put(LocaleUtils.getLocaleCode(resMap.mLocale), resMap);
        }
        mDefaultResourceMap = mResourcesMap.get(
                LocaleUtils.getLocaleCode(LocaleUtils.DEFAULT_LOCALE));

        // Initialize name histogram and names list.
        final HashMap<String, Integer> nameHistogram = mNameHistogram;
        final ArrayList<String> resourceNamesList = new ArrayList<>();
        for (final StringResource res : mDefaultResourceMap.getResources()) {
            nameHistogram.put(res.mName, 0); // Initialize histogram value.
            resourceNamesList.add(res.mName);
        }
        // Make name histogram.
        for (final String locale : mResourcesMap.keySet()) {
            final StringResourceMap resMap = mResourcesMap.get(locale);
            if (resMap == mDefaultResourceMap) continue;
            for (final StringResource res : resMap.getResources()) {
                if (!mDefaultResourceMap.contains(res.mName)) {
                    throw new RuntimeException(res.mName + " in " + locale
                            + " doesn't have default resource");
                }
                final int histogramValue = nameHistogram.get(res.mName);
                nameHistogram.put(res.mName, histogramValue + 1);
            }
        }
        // Sort names list.
        Collections.sort(resourceNamesList, new Comparator<String>() {
            @Override
            public int compare(final String leftName, final String rightName) {
                final int leftCount = nameHistogram.get(leftName);
                final int rightCount = nameHistogram.get(rightName);
                // Descending order of histogram count.
                if (leftCount > rightCount) return -1;
                if (leftCount < rightCount) return 1;
                // TODO: Add further criteria to order the same histogram value names to be able to
                // minimize footprints of string resources arrays.
                return 0;
            }
        });
        mSortedResourceNames = resourceNamesList.toArray(new String[resourceNamesList.size()]);
    }

    public void writeToJava(final String outDir) {
        final ArrayList<String> list = JarUtils.getEntryNameListing(mJar, JAVA_TEMPLATE);
        if (list.isEmpty()) {
            throw new RuntimeException("Can't find java template " + JAVA_TEMPLATE);
        }
        if (list.size() > 1) {
            throw new RuntimeException("Found multiple java template " + JAVA_TEMPLATE);
        }
        final String template = list.get(0);
        final String javaPackage = template.substring(0, template.lastIndexOf('/'));
        PrintStream ps = null;
        LineNumberReader lnr = null;
        try {
            if (outDir == null) {
                ps = System.out;
            } else {
                final File outPackage = new File(outDir, javaPackage);
                final File outputFile = new File(outPackage,
                        JAVA_TEMPLATE.replace(".tmpl", ".java"));
                outPackage.mkdirs();
                ps = new PrintStream(outputFile, "UTF-8");
            }
            lnr = new LineNumberReader(new InputStreamReader(JarUtils.openResource(template)));
            inflateTemplate(lnr, ps);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            JarUtils.close(lnr);
            JarUtils.close(ps);
        }
    }

    private void inflateTemplate(final LineNumberReader in, final PrintStream out)
            throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains(MARK_NAMES)) {
                dumpNames(out);
            } else if (line.contains(MARK_DEFAULT_TEXTS)) {
                dumpDefaultTexts(out);
            } else if (line.contains(MARK_TEXTS)) {
                dumpTexts(out);
            } else if (line.contains(MARK_LOCALES_AND_TEXTS)) {
                dumpLocalesMap(out);
            } else {
                out.println(line);
            }
        }
    }

    private void dumpNames(final PrintStream out) {
        final int namesCount = mSortedResourceNames.length;
        for (int index = 0; index < namesCount; index++) {
            final String name = mSortedResourceNames[index];
            final int histogramValue = mNameHistogram.get(name);
            out.format("        /* %3d:%2d */ \"%s\",\n", index, histogramValue, name);
        }
    }

    private void dumpDefaultTexts(final PrintStream out) {
        final int outputArraySize = dumpTextsInternal(out, mDefaultResourceMap);
        mDefaultResourceMap.setOutputArraySize(outputArraySize);
    }

    private static String getArrayNameForLocale(final Locale locale) {
        return TEXTS_ARRAY_NAME_PREFIX + LocaleUtils.getLocaleCode(locale);
    }

    private void dumpTexts(final PrintStream out) {
        for (final StringResourceMap resMap : mResourcesMap.values()) {
            final Locale locale = resMap.mLocale;
            if (resMap == mDefaultResourceMap) continue;
            out.format("    /* Locale %s: %s */\n",
                    locale, LocaleUtils.getLocaleDisplayName(locale));
            out.format("    private static final String[] " + getArrayNameForLocale(locale)
                    + " = {\n");
            final int outputArraySize = dumpTextsInternal(out, resMap);
            resMap.setOutputArraySize(outputArraySize);
            out.format("    };\n\n");
        }
    }

    private void dumpLocalesMap(final PrintStream out) {
        for (final StringResourceMap resMap : mResourcesMap.values()) {
            final Locale locale = resMap.mLocale;
            final String localeStr = LocaleUtils.getLocaleCode(locale);
            final String localeStr2 = localeStr.equals("he")
                    ? "iw" // android still uses iw, but apparently with a newer java version iw ends up being converted to he
                    : localeStr;
            final String localeToDump = (locale == LocaleUtils.DEFAULT_LOCALE)
                    ? String.format("\"%s\"", localeStr)
                    : String.format("\"%s\"%s", localeStr2, "       ".substring(localeStr.length()));
            out.format("        %s, %-12s /* %3d/%3d %s */\n",
                    localeToDump, getArrayNameForLocale(locale) + ",",
                    resMap.getResources().size(), resMap.getOutputArraySize(),
                    LocaleUtils.getLocaleDisplayName(locale));
        }
    }

    private int dumpTextsInternal(final PrintStream out, final StringResourceMap resMap) {
        final ArrayInitializerFormatter formatter =
                new ArrayInitializerFormatter(out, 100, "        ", mSortedResourceNames);
        int outputArraySize = 0;
        boolean successiveNull = false;
        final int namesCount = mSortedResourceNames.length;
        for (int index = 0; index < namesCount; index++) {
            final String name = mSortedResourceNames[index];
            final StringResource res = resMap.get(name);
            if (res != null) {
                // TODO: Check whether the resource value is equal to the default.
                if (res.mComment != null) {
                    formatter.outCommentLines(addPrefix("        // ", res. mComment));
                }
                final String escaped = escapeNonAscii(res.mValue);
                if (escaped.length() == 0) {
                    formatter.outElement(EMPTY_STRING_VAR + ",");
                } else {
                    formatter.outElement(String.format("\"%s\",", escaped));
                }
                successiveNull = false;
                outputArraySize = formatter.getCurrentIndex();
            } else {
                formatter.outElement("null,");
                successiveNull = true;
            }
        }
        if (!successiveNull) {
            formatter.flush();
        }
        return outputArraySize;
    }

    private static String addPrefix(final String prefix, final String lines) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : lines.split("\n")) {
            sb.append(prefix + line.trim() + "\n");
        }
        return sb.toString();
    }

    private static String escapeNonAscii(final String text) {
        final StringBuilder sb = new StringBuilder();
        final int length = text.length();
        for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if (c >= ' ' && c < 0x7f) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04X", (int)c));
            }
        }
        return sb.toString();
    }
}
