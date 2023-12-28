/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.inputmethod.keyboard.tools;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarUtils {
    private JarUtils() {
        // This utility class is not publicly instantiable.
    }

    public static JarFile getJarFile(final Class<?> mainClass) {
        final String mainClassPath = "/" + mainClass.getName().replace('.', '/') + ".class";
        final URL resUrl = mainClass.getResource(mainClassPath);
        if (!resUrl.getProtocol().equals("jar")) {
            throw new RuntimeException("Should run as jar and not as " + resUrl.getProtocol());
        }
        final String path = resUrl.getPath();
        if (!path.startsWith("file:")) {
            throw new RuntimeException("Unknown jar path: " + path);
        }
        final String jarPath = path.substring("file:".length(), path.indexOf('!'));
        try {
            return new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
        } catch (IOException e) {
        }
        return null;
    }

    public static InputStream openResource(final String name) {
        return JarUtils.class.getResourceAsStream("/" + name);
    }

    public interface JarFilter {
        public boolean accept(String dirName, String name);
    }

    public static ArrayList<String> getEntryNameListing(final JarFile jar, final JarFilter filter) {
        final ArrayList<String> result = new ArrayList<>();
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String path = entry.getName();
            final int pos = path.lastIndexOf('/');
            final String dirName = (pos >= 0) ? path.substring(0, pos) : "";
            final String name = (pos >= 0) ? path.substring(pos + 1) : path;
            if (filter.accept(dirName, name)) {
                result.add(path);
            }
        }
        return result;
    }

    public static ArrayList<String> getEntryNameListing(final JarFile jar,
            final String filterName) {
        return getEntryNameListing(jar, new JarFilter() {
            @Override
            public boolean accept(final String dirName, final String name) {
                return name.equals(filterName);
            }
        });
    }

    // The locale is taken from string resource jar entry name (values-<locale>/)
    // or {@link LocaleUtils#DEFAULT_LOCALE} for the default string resource
    // directory (values/).
    public static Locale getLocaleFromEntryName(final String jarEntryName) {
        final String dirName = jarEntryName.substring(0, jarEntryName.lastIndexOf('/'));
        final int pos = dirName.lastIndexOf('/');
        final String parentName = (pos >= 0) ? dirName.substring(pos + 1) : dirName;
        final int localePos = parentName.indexOf('-');
        if (localePos < 0) {
            // Default resource name.
            return LocaleUtils.DEFAULT_LOCALE;
        }
        final String localeStr = parentName.substring(localePos + 1);
        final int regionPos = localeStr.indexOf("-r");
        if (regionPos < 0) {
            return LocaleUtils.constructLocaleFromString(localeStr);
        }
        return LocaleUtils.constructLocaleFromString(localeStr.replace("-r", "_"));
    }

    public static void close(final Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
        }
    }
}
