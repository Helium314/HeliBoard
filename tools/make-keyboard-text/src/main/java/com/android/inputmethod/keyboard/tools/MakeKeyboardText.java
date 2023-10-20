/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.inputmethod.keyboard.tools;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.jar.JarFile;

public class MakeKeyboardText {
    static class Options {
        private static final String OPTION_JAVA = "-java";

        public final String mJava;

        public static void usage(String message) {
            if (message != null) {
                System.err.println(message);
            }
            System.err.println("usage: make-keyboard-text " + OPTION_JAVA + " <java_output_dir>");
            System.exit(1);
        }

        public Options(final String[] argsArray) {
            final LinkedList<String> args = new LinkedList<>(Arrays.asList(argsArray));
            String arg = null;
            String java = null;
            try {
                while (!args.isEmpty()) {
                    arg = args.removeFirst();
                    if (arg.equals(OPTION_JAVA)) {
                        java = args.removeFirst();
                    } else {
                        usage("Unknown option: " + arg);
                    }
                }
            } catch (NoSuchElementException e) {
                usage("Option " + arg + " needs argument");
            }

            mJava = java;
        }
    }

    public static void main(final String[] args) {
        final Options options = new Options(args);
        final JarFile jar = JarUtils.getJarFile(MakeKeyboardText.class);
        final MoreKeysResources resources = new MoreKeysResources(jar);
        resources.writeToJava(options.mJava);
    }
}
