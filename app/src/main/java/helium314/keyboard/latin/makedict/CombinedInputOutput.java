/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.makedict;

import helium314.keyboard.latin.makedict.FormatSpec.DictionaryOptions;
import helium314.keyboard.latin.makedict.FusionDictionary.PtNodeArray;

import helium314.keyboard.latin.utils.CombinedFormatUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * Reads and writes combined format for a FusionDictionary.
 * <p>
 * All functions in this class are static.
 */
public class CombinedInputOutput {
    private static final String WHITELIST_TAG = "whitelist";
    private static final String OPTIONS_TAG = "options";
    private static final String COMMENT_LINE_STARTER = "#";
    private static final int HISTORICAL_INFO_ELEMENT_COUNT = 3;

    /**
     * Basic test to find out whether the file is in the combined format or not.
     * <p>
     * Concretely this only tests the header line.
     *
     * @param filename The name of the file to test.
     * @return true if the file is in the combined format, false otherwise
     */
    public static boolean isCombinedDictionary(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String firstLine = reader.readLine();
            while (firstLine.startsWith(COMMENT_LINE_STARTER)) {
                firstLine = reader.readLine();
            }
            return firstLine.matches(
                    "^" + CombinedFormatUtils.DICTIONARY_TAG + "=[^:]+(:[^=]+=[^:]+)*");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Reads a dictionary from a combined format file.
     * <p>
     * This is the public method that will read a combined file and return the corresponding memory
     * representation.
     *
     * @param reader the buffered reader to read the data from.
     * @return the in-memory representation of the dictionary.
     */
    public static FusionDictionary readDictionaryCombined(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        while (headerLine.startsWith(COMMENT_LINE_STARTER)) {
            headerLine = reader.readLine();
        }
        String[] header = headerLine.split(",");
        HashMap<String, String> attributes = new HashMap<>();
        for (String item : header) {
            String[] keyValue = item.split("=");
            if (2 != keyValue.length) {
                throw new RuntimeException("Wrong header format : " + headerLine);
            }
            attributes.put(keyValue[0], keyValue[1]);
        }

        attributes.remove(OPTIONS_TAG);
        FusionDictionary dict =
                new FusionDictionary(new PtNodeArray(), new DictionaryOptions(attributes));

        String line;
        String word = null;
        ProbabilityInfo probabilityInfo = new ProbabilityInfo(0);
        boolean isNotAWord = false;
        boolean isPossiblyOffensive = false;
        ArrayList<WeightedString> bigrams = new ArrayList<>();
        ArrayList<WeightedString> shortcuts = new ArrayList<>();
        while (null != (line = reader.readLine())) {
            if (line.startsWith(COMMENT_LINE_STARTER)) continue;
            String[] args = line.trim().split(",");
            if (args[0].matches(CombinedFormatUtils.WORD_TAG + "=.*")) {
                if (null != word) {
                    dict.add(word, probabilityInfo, shortcuts.isEmpty() ? null : shortcuts,
                            isNotAWord, isPossiblyOffensive);
                    for (WeightedString s : bigrams) {
                        dict.setBigram(word, s.mWord, s.mProbabilityInfo);
                    }
                }
                if (!shortcuts.isEmpty()) shortcuts = new ArrayList<>();
                if (!bigrams.isEmpty()) bigrams = new ArrayList<>();
                isNotAWord = false;
                isPossiblyOffensive = false;
                for (String param : args) {
                    String[] params = param.split("=", 2);
                    if (2 != params.length) throw new RuntimeException("Wrong format : " + line);
                    switch (params[0]) {
                        case CombinedFormatUtils.WORD_TAG:
                            word = params[1];
                            break;
                        case CombinedFormatUtils.PROBABILITY_TAG:
                            probabilityInfo = new ProbabilityInfo(Integer.parseInt(params[1]),
                                    probabilityInfo.mTimestamp, probabilityInfo.mLevel,
                                    probabilityInfo.mCount);
                            break;
                        case CombinedFormatUtils.HISTORICAL_INFO_TAG:
                            String[] historicalInfoParams = params[1].split(
                                    CombinedFormatUtils.HISTORICAL_INFO_SEPARATOR);
                            if (historicalInfoParams.length != HISTORICAL_INFO_ELEMENT_COUNT) {
                                throw new RuntimeException("Wrong format (historical info) : "
                                        + line);
                            }
                            probabilityInfo = new ProbabilityInfo(probabilityInfo.mProbability,
                                    Integer.parseInt(historicalInfoParams[0]),
                                    Integer.parseInt(historicalInfoParams[1]),
                                    Integer.parseInt(historicalInfoParams[2]));
                            break;
                        case CombinedFormatUtils.NOT_A_WORD_TAG:
                            isNotAWord = CombinedFormatUtils.isLiteralTrue(params[1]);
                            break;
                        case CombinedFormatUtils.POSSIBLY_OFFENSIVE_TAG:
                            isPossiblyOffensive = CombinedFormatUtils.isLiteralTrue(params[1]);
                            break;
                    }
                }
            } else if (args[0].matches(CombinedFormatUtils.SHORTCUT_TAG + "=.*")) {
                String shortcut = null;
                int shortcutFreq = 0;
                for (String param : args) {
                    String[] params = param.split("=", 2);
                    if (2 != params.length) throw new RuntimeException("Wrong format : " + line);
                    if (CombinedFormatUtils.SHORTCUT_TAG.equals(params[0])) {
                        shortcut = params[1];
                    } else if (CombinedFormatUtils.PROBABILITY_TAG.equals(params[0])) {
                        shortcutFreq = WHITELIST_TAG.equals(params[1])
                                ? FormatSpec.SHORTCUT_WHITELIST_FREQUENCY
                                : Integer.parseInt(params[1]);
                    }
                }
                if (null != shortcut) {
                    shortcuts.add(new WeightedString(shortcut, shortcutFreq));
                } else {
                    throw new RuntimeException("Wrong format : " + line);
                }
            } else if (args[0].matches(CombinedFormatUtils.BIGRAM_TAG + "=.*")) {
                String secondWordOfBigram = null;
                ProbabilityInfo bigramProbabilityInfo = new ProbabilityInfo(0);
                for (String param : args) {
                    String[] params = param.split("=", 2);
                    if (2 != params.length) throw new RuntimeException("Wrong format : " + line);
                    if (CombinedFormatUtils.BIGRAM_TAG.equals(params[0])) {
                        secondWordOfBigram = params[1];
                    } else if (CombinedFormatUtils.PROBABILITY_TAG.equals(params[0])) {
                        bigramProbabilityInfo = new ProbabilityInfo(Integer.parseInt(params[1]),
                                bigramProbabilityInfo.mTimestamp, bigramProbabilityInfo.mLevel,
                                bigramProbabilityInfo.mCount);
                    }  else if (CombinedFormatUtils.HISTORICAL_INFO_TAG.equals(params[0])) {
                        String[] historicalInfoParams =
                                params[1].split(CombinedFormatUtils.HISTORICAL_INFO_SEPARATOR);
                        if (historicalInfoParams.length != HISTORICAL_INFO_ELEMENT_COUNT) {
                            throw new RuntimeException("Wrong format (historical info) : " + line);
                        }
                        bigramProbabilityInfo = new ProbabilityInfo(
                                bigramProbabilityInfo.mProbability,
                                Integer.parseInt(historicalInfoParams[0]),
                                Integer.parseInt(historicalInfoParams[1]),
                                Integer.parseInt(historicalInfoParams[2]));
                    }
                }
                if (null != secondWordOfBigram) {
                    bigrams.add(new WeightedString(secondWordOfBigram, bigramProbabilityInfo));
                } else {
                    throw new RuntimeException("Wrong format : " + line);
                }
            }
        }
        if (null != word) {
            dict.add(word, probabilityInfo, shortcuts.isEmpty() ? null : shortcuts, isNotAWord,
                    isPossiblyOffensive);
            for (WeightedString s : bigrams) {
                dict.setBigram(word, s.mWord, s.mProbabilityInfo);
            }
        }

        return dict;
    }

    /**
     * Writes a dictionary to a combined file.
     *
     * @param destination a destination writer.
     * @param dict the dictionary to write.
     */
    public static void writeDictionaryCombined(BufferedWriter destination, FusionDictionary dict) throws IOException {
        TreeSet<WordProperty> wordPropertiesInDict = new TreeSet<>();
        for (WordProperty wordProperty : dict) {
            // This for ordering by frequency, then by asciibetic order
            wordPropertiesInDict.add(wordProperty);
        }
        destination.write(CombinedFormatUtils.formatAttributeMap(dict.mOptions.mAttributes));
        for (WordProperty wordProperty : wordPropertiesInDict) {
            destination.write(CombinedFormatUtils.formatWordProperty(wordProperty));
        }
    }
}
