/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import org.dslul.openboard.inputmethod.latin.ClipboardHistoryEntry;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JsonUtils {
    private static final String TAG = JsonUtils.class.getSimpleName();

    private static final String INTEGER_CLASS_NAME = Integer.class.getSimpleName();
    private static final String STRING_CLASS_NAME = String.class.getSimpleName();
    private static final String CLIPBOARD_HISTORY_ENTRY_ID_KEY = "id";
    private static final String CLIPBOARD_HISTORY_ENTRY_CONTENT_KEY = "content";

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

    public static List<ClipboardHistoryEntry> jsonBytesToHistoryEntryList(final byte[] bytes) {
        final ArrayList<ClipboardHistoryEntry> list = new ArrayList<>();
        final JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                long id = 0;
                String content = EMPTY_STRING;
                while (reader.hasNext()) {
                    final String name = reader.nextName();
                    if (name.equals(CLIPBOARD_HISTORY_ENTRY_ID_KEY)) {
                        id = reader.nextLong();
                    } else if (name.equals(CLIPBOARD_HISTORY_ENTRY_CONTENT_KEY)) {
                        content = reader.nextString();
                    } else {
                        Log.w(TAG, "Invalid name: " + name);
                        reader.skipValue();
                    }
                }
                if (id > 0 && !TextUtils.isEmpty(content)) {
                    list.add(new ClipboardHistoryEntry(id, content, true));
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

    public static String historyEntryListToJsonStr(final Collection<ClipboardHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY_STRING;
        }
        final StringWriter sw = new StringWriter();
        final JsonWriter writer = new JsonWriter(sw);
        try {
            writer.beginArray();
            for (final ClipboardHistoryEntry e : entries) {
                writer.beginObject();
                writer.name(CLIPBOARD_HISTORY_ENTRY_ID_KEY).value(e.getTimeStamp());
                writer.name(CLIPBOARD_HISTORY_ENTRY_CONTENT_KEY).value(e.getContent().toString());
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
