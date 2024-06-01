#!/bin/python

import sys
import os
import re


file_ending_filter = "-words.txt"
word_lists_dir = "../../wordlists/"


def find_word_lists(language: str) -> list[str]:
    # return a list of files
    files = list()
    if not os.path.isdir(word_lists_dir + language):
        return files
    for (dirpath, dirnames, filenames) in os.walk(word_lists_dir + language):
        for n in filenames:
            if n.endswith(file_ending_filter):
                files.append(dirpath + "/" + n)
    return files


def check_diacritics(language: str, diacritics: list[str], all_diacritics: set[str]):
    word_lists = find_word_lists(language)
    if len(word_lists) == 0:
        return
    for dia in diacritics:
        all_diacritics.remove(dia)
    foreign_dia = "".join(all_diacritics)
    dia_regex = fr"[{foreign_dia}]"
    print("checking", language, "with", diacritics)
    foreigns = list()
    dia_count = dict()
    for dia in diacritics:
        dia_count[dia] = 0
    for word_list in word_lists:
        with open(word_list) as f:
            # check whether file contains any diacritics that are not in the list
            for line in f:
                if re.search(dia_regex, line):
                    foreigns.append(line.rstrip())
                else:
                    # search for language diacritics and add a count
                    for dia in diacritics:
                        if dia in line:
                            try:
                                # assuming the format from https://www.wortschatz.uni-leipzig.de/en/download
                                count = int(line.split("\t")[2])
                            except:
                                count = 1
                            dia_count[dia] = dia_count[dia] + count
    dia_results = f"language: {language}\n"
    dia_results = dia_results + f"diacritics: {diacritics}\n"
    dia_results = dia_results + f"language diacritics counts: {dia_count}\n"
    dia_results = dia_results + "foreign diacritics:\n"
    dia_results = dia_results + "\n".join(foreigns)
    with open(f"diacritics_report_{language}.txt", 'w') as f:
        f.write(dia_results)


def make_all_diacritics(dia_lists: list[list[str]]) -> set[str]:
    all_dia = set()
    for dia_list in dia_lists:
        for dia in dia_list:
            all_dia.add(dia)
    return all_dia


def read_diacritics() -> dict[str, list[str]]:
    d = dict()
    language = ""
    with open("diacritics.txt") as f:
        for line in f:
            if language == "":
                language = line.strip()
            else:
                d[language] = list(map(str.strip, line.split(",")))
                language = ""
    return d


def main():
    diacritics = read_diacritics()
    all_diacritics = make_all_diacritics(list(diacritics.values()))
    for key in diacritics:
        check_diacritics(key, diacritics[key], all_diacritics.copy())


if __name__ == "__main__":
    main()
