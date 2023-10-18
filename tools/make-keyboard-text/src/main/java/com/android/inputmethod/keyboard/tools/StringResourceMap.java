/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.keyboard.tools;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class StringResourceMap {
    // Locale of this string resource map.
    public final Locale mLocale;
    // String resource list.
    private final List<StringResource> mResources;
    // Name to string resource map.
    private final Map<String, StringResource> mResourcesMap;

    // The length of String[] that is created from this {@link StringResourceMap}. The length is
    // calculated in {@link MoreKeysResources#dumpTexts(OutputStream)} and recorded by
    // {@link #setOutputArraySize(int)}. The recorded length is used as a part of comment by
    // {@link MoreKeysResources#dumpLocaleMap(OutputStream)} via {@link #getOutputArraySize()}.
    private int mOutputArraySize;

    public StringResourceMap(final String jarEntryName) {
        mLocale = JarUtils.getLocaleFromEntryName(jarEntryName);
        final StringResourceHandler handler = new StringResourceHandler();
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final InputStream stream = JarUtils.openResource(jarEntryName);
        try {
            final SAXParser parser = factory.newSAXParser();
            // In order to get comment tag.
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            parser.parse(stream, handler);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (SAXParseException e) {
            throw new RuntimeException(e.getMessage() + " at line " + e.getLineNumber()
                    + ", column " + e.getColumnNumber(), e);
        } catch (SAXException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            JarUtils.close(stream);
        }

        mResources = Collections.unmodifiableList(handler.mResources);
        final HashMap<String, StringResource> map = new HashMap<>();
        for (final StringResource res : mResources) {
            map.put(res.mName, res);
        }
        mResourcesMap = map;
    }

    public List<StringResource> getResources() {
        return mResources;
    }

    public boolean contains(final String name) {
        return mResourcesMap.containsKey(name);
    }

    public StringResource get(final String name) {
        return mResourcesMap.get(name);
    }

    public void setOutputArraySize(final int arraySize) {
        mOutputArraySize = arraySize;
    }

    public int getOutputArraySize() {
        return mOutputArraySize;
    }

    static class StringResourceHandler extends DefaultHandler2 {
        private static final String TAG_RESOURCES = "resources";
        private static final String TAG_STRING = "string";
        private static final String ATTR_NAME = "name";

        final ArrayList<StringResource> mResources = new ArrayList<>();

        private String mName;
        private final StringBuilder mValue = new StringBuilder();
        private final StringBuilder mComment = new StringBuilder();

        private void init() {
            mName = null;
            mComment.setLength(0);
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            mComment.append(ch, start, length);
            if (ch[start + length - 1] != '\n') {
                mComment.append('\n');
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attr) {
            if (TAG_RESOURCES.equals(localName)) {
                init();
            } else if (TAG_STRING.equals(localName)) {
                mName = attr.getValue(ATTR_NAME);
                mValue.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            mValue.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (TAG_STRING.equals(localName)) {
                if (mName == null)
                    throw new SAXException(TAG_STRING + " doesn't have name");
                final String comment = mComment.length() > 0 ? mComment.toString() : null;
                String value = mValue.toString();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    // Trim surroundings double quote.
                    value = value.substring(1, value.length() - 1);
                }
                mResources.add(new StringResource(mName, value, comment));
                init();
            }
        }
    }
}
