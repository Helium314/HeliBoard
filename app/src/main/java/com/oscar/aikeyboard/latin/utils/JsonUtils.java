/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.utils;

import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JsonUtils {
    private static final String TAG = JsonUtils.class.getSimpleName();

    private static final String INTEGER_CLASS_NAME = Integer.class.getSimpleName();
    private static final String STRING_CLASS_NAME = String.class.getSimpleName();

    private static final String EMPTY_STRING = "";

    public static List<Object> jsonStrToList(final String s) {
        final ArrayList<Object> list = new ArrayList<>();
        final JsonReader reader = new JsonReader(new StringReader(s));
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                while (reader.hasNext()) {
                    final String name = reader.nextName();
                    if (name.equals(INTEGER_CLASS_NAME)) {
                        list.add(reader.nextInt());
                    } else if (name.equals(STRING_CLASS_NAME)) {
                        list.add(reader.nextString());
                    } else {
                        Log.w(TAG, "Invalid name: " + name);
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
            reader.endArray();
            return list;
        } catch (final IOException e) {
        } finally {
            close(reader);
        }
        return Collections.emptyList();
    }

    public static String listToJsonStr(final List<Object> list) {
        if (list == null || list.isEmpty()) {
            return EMPTY_STRING;
        }
        final StringWriter sw = new StringWriter();
        final JsonWriter writer = new JsonWriter(sw);
        try {
            writer.beginArray();
            for (final Object o : list) {
                writer.beginObject();
                if (o instanceof Integer) {
                    writer.name(INTEGER_CLASS_NAME).value((Integer)o);
                } else if (o instanceof String) {
                    writer.name(STRING_CLASS_NAME).value((String)o);
                }
                writer.endObject();
            }
            writer.endArray();
            return sw.toString();
        } catch (final IOException e) {
        } finally {
            close(writer);
        }
        return EMPTY_STRING;
    }

    private static void close(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException e) {
            // Ignore
        }
    }
}
