/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import android.content.res.TypedArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public final class XmlParseUtils {
    private XmlParseUtils() {
        // This utility class is not publicly instantiable.
    }

    public static class ParseException extends XmlPullParserException {
        public ParseException(final String msg, final XmlPullParser parser) {
            super(msg + " at " + parser.getPositionDescription());
        }
    }

    public static final class IllegalStartTag extends ParseException {
        public IllegalStartTag(final XmlPullParser parser, final String tag, final String parent) {
            super("Illegal start tag " + tag + " in " + parent, parser);
        }
    }

    public static final class IllegalEndTag extends ParseException {
        public IllegalEndTag(final XmlPullParser parser, final String tag, final String parent) {
            super("Illegal end tag " + tag + " in " + parent, parser);
        }
    }

    public static final class NonEmptyTag extends ParseException{
        public NonEmptyTag(final XmlPullParser parser, final String tag) {
            super(tag + " must be empty tag", parser);
        }
    }

    public static void checkEndTag(final String tag, final XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (parser.next() == XmlPullParser.END_TAG && tag.equals(parser.getName()))
            return;
        throw new NonEmptyTag(parser, tag);
    }

    public static void checkAttributeExists(final TypedArray attr, final int attrId,
            final String attrName, final String tag, final XmlPullParser parser)
                    throws XmlPullParserException {
        if (attr.hasValue(attrId)) {
            return;
        }
        throw new ParseException(
                "No " + attrName + " attribute found in <" + tag + "/>", parser);
    }
}
