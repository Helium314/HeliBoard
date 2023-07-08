#!/bin/python
import math
import os
import time
import regex
import copy
from spylls.hunspell import Dictionary

# issues:
# for english got 'i' as word (shouldn't it be 'I' only? or is 'i' the imaginary number?)
# potentially_offensive is not set, where to get info? parse android dicts?
# maybe ignore compound words like 'long-term'? will android actually suggest them?

# maybe useful
# https://wortschatz.uni-leipzig.de/en/download/
#  really useful source of sentences / fragments, but should be checked against dictionaries as they are taken from news / web
# https://en.wiktionary.org/wiki/Wiktionary:Frequency_lists
#  word frequency lists linked, in some cases there are also sentence lists
# https://github.com/wooorm/dictionaries
#  hunspell dicts, are they the same as the one included in phunspell?

# memory usage depends on word lists and language, expect 0.5 - 2 GB
# for some reason, Italian requires 4 GB for unmunch


# from https://github.com/zverok/spylls/blob/master/examples/unmunch.py
def unmunch_word(word, aff):
    result = set()

    if aff.FORBIDDENWORD and aff.FORBIDDENWORD in word.flags:
        return result

    if not (aff.NEEDAFFIX and aff.NEEDAFFIX in word.flags):
        result.add(word.stem)

    suffixes = [
        suffix
        for flag in word.flags
        for suffix in aff.SFX.get(flag, [])
        if suffix.cond_regexp.search(word.stem)
    ]
    prefixes = [
        prefix
        for flag in word.flags
        for prefix in aff.PFX.get(flag, [])
        if prefix.cond_regexp.search(word.stem)
    ]

    for suffix in suffixes:
        root = word.stem[0:-len(suffix.strip)] if suffix.strip else word.stem
        suffixed = root + suffix.add
        if not (aff.NEEDAFFIX and aff.NEEDAFFIX in suffix.flags):
            result.add(suffixed)

        secondary_suffixes = [
            suffix2
            for flag in suffix.flags
            for suffix2 in aff.SFX.get(flag, [])
            if suffix2.cond_regexp.search(suffixed)
        ]
        for suffix2 in secondary_suffixes:
            root = suffixed[0:-len(suffix2.strip)] if suffix2.strip else suffixed
            result.add(root + suffix2.add)

    for prefix in prefixes:
        root = word.stem[len(prefix.strip):]
        prefixed = prefix.add + root
        if not (aff.NEEDAFFIX and aff.NEEDAFFIX in prefix.flags):
            result.add(prefixed)

        if prefix.crossproduct:
            additional_suffixes = [
                suffix
                for flag in prefix.flags
                for suffix in aff.SFX.get(flag, [])
                if suffix.crossproduct and not suffix in suffixes and suffix.cond_regexp.search(prefixed)
            ]
            for suffix in suffixes + additional_suffixes:
                root = prefixed[0:-len(suffix.strip)] if suffix.strip else prefixed
                suffixed = root + suffix.add
                result.add(suffixed)

                secondary_suffixes = [
                    suffix2
                    for flag in suffix.flags
                    for suffix2 in aff.SFX.get(flag, [])
                    if suffix2.crossproduct and suffix2.cond_regexp.search(suffixed)
                ]
                for suffix2 in secondary_suffixes:
                    root = suffixed[0:-len(suffix2.strip)] if suffix2.strip else suffixed
                    result.add(root + suffix2.add)

    return result


def unmunch_dictionary(dictionary: Dictionary) -> set[str]:
    result = set()
    for word in dictionary.dic.words:
        result.update(unmunch_word(word, dictionary.aff))
    return result


class wordlist:
    def __init__(self,
                 # spylls dictionary
                 dictionary: Dictionary | None = None,
                 # words that should be ignored, typically (international) names we don't want in a language word list
                 neutral_words: set[str] | None = None):
        self.dictionary = dictionary
        self.dict_words: set[str] = set()
        if neutral_words is None:
            self.neutral_words = set()
        else:
            self.neutral_words = neutral_words
    # number of identified words
    count = 0

    # number of words used for frequency
    count_valid = 0

    # words to ignore, as they should be in some additional dictionary (mostly names)
    # these are not counted as valid or invalid, and not used for next-word data
    neutral_word_count = 0

    # words detected as invalid, these are mostly names and capitalized words (possibly also part of names)
    invalid_words: set[str] = set()
    not_words: set[str] = set()

    # unclear words with more than one match group in above regex
    # check and decide in the end what to do
    weird_things: set[str] = set()

    # for each word, contains a dict with:
    #  count: int (always)
    #  next: dict[str, int] (not always)
    #   how often the word is followed by some others (next_word, count)
    #  nosuggest: bool (usually only if True, as determined by hunspell dict)
    word_infos: dict = {}

    # regex for that kicks out things that are definitely not words
    # next word will be treated as ngram start
    # allow latin letters, and ' and - (but not at start/end)
    possible_word_regex = r"(?!['-])([\p{L}\d'-]+)(?<!['-])"  # \p{L} requires regex, not re

    # adds words that are valid according to dictionary
    # this is useful for adding many word form that are valid but not used frequently
    def add_unmunched_dictionary(self, unmunched_cache: str | None = None):
        unmunched: set[str] = set()
        if unmunched_cache is not None and os.path.isfile(unmunched_cache):
            try:
                with open(unmunched_cache) as f:
                    for w in f:
                        unmunched.add(w.strip())
            except:
                print(f"error reading {unmunched_cache}")
        if len(unmunched) == 0:
            s = unmunch_dictionary(self.dictionary)
            # unmunch may create word fragments
            #  remove words that are not valid according to dictionary
            #  or that start or end with -
            # unfortunately this can be really slow depending on language, seen from a few seconds up to hours (cs)
            #  but with the cache it's ok
            for word in s:
                if not word.startswith("-") and not word.endswith("-") and not word.isdigit():
                    # don't care about whether word is already in word_infos, we only want hunspell words
                    if self.dictionary.lookuper(word, capitalization=False, allow_nosuggest=False):
                        unmunched.add(word)
                    elif self.dictionary.lookuper(word, capitalization=False, allow_nosuggest=True):
                        unmunched.add(f"nosuggest:{word}")
            if unmunched_cache is not None:
                try:
                    with open(unmunched_cache, 'w') as f:
                        f.writelines([str(i) + '\n' for i in unmunched])
                except:
                    print(f"could not write to {unmunched_cache}")

        count = 0
        for word in unmunched:
            if word not in self.word_infos:
                if word.startswith("nosuggest:"):
                    word = word[10:]
                    self.add_word(word, True)
                else:
                    self.add_word(word)
                count += 1
        print(count, "words added using add_unmunched_dictionary")

    # tries adding a line, which is a sentence or sentence fragment
    # if next-word information is not wanted, use add_word instead
    # currently ngram ends after every non-word character
    #  like 2, ", ,, ., -
    #  any cases where this shouldn't happen?
    def add_line(self, line: str,
                 # True: all words will be added, except if they start with an uppercase letter and
                 #  previous_word is None (careful, this can easily add spelling mistakes)
                 # False: if no dictionary, no words will be added
                 #  if dictionary, words will still be added if they are found in a case-sensitive lookup
                 add_unknown_words: bool = False) -> None:
        previous_word: str | None = None
        for word in line.split():
            if word in self.word_infos:
                # shortcut: we already know the word, avoid doing the regex check and dict lookup if possible
                # only increase count and add next word info
                self.add_word(word)
                if previous_word is not None:
                    previous_info = self.word_infos[previous_word]
                    previous_next = previous_info.get("next", {})
                    previous_next[word] = previous_next.get(word, 0) + 1
                    previous_info["next"] = previous_next
                previous_word = word
                continue
            if len(word) >= 48:
                # android dicttool ignores those, so let's skip them already here
                previous_word = None
                continue
            if word.isspace():
                # don't treat spaces as countable word (assuming a line does not contain a line break)
                continue
            if word.isnumeric():
                # don't put numbers info not_words
                previous_word = None
                continue
            if not regex.search(r"\p{L}", word):
                # no letters, no word (but ngram ends here)
                self.not_words.add(word)
                previous_word = None
                continue
            # hunspell dict has ', not ’, but we want to understand both
            word = word.replace('’', '\'')
            # must find something, because r"\p{L}" non-matches are already removed
            re_find = regex.findall(self.possible_word_regex, word)
            self.count += 1
            if len(re_find) > 1:
                # just write down and end sentence for now
                self.weird_things.add(word)
                previous_word = None
            # treat re_find[0] as the actual word, but need to investigate weird_things to maybe improve possible_word_regex
            full_word = word
            word = re_find[0]
            # if the match is not at the start, treat as ngram start
            if not full_word.startswith(word):
                previous_word = None

            if word in self.neutral_words:
                self.neutral_word_count += 1
                previous_word = None
                continue

            if word in self.invalid_words:
                previous_word = None
                continue

            if word not in self.word_infos:
                if add_unknown_words:
                    if previous_word is None and word[0].isupper():
                        continue
                else:
                    try:
                        valid, word = self.dict_check(word, previous_word is None)
                    except IndexError:
                        # happens for "İsmail" when using German dictionary, also for other words starting with "İ"
                        previous_word = None
                        continue
                    if not valid:
                        if previous_word is not None:
                            self.invalid_words.add(word)
                        previous_word = None
                        continue

            self.count_valid += 1
            self.add_word(word, add_to_count=False)

            if previous_word is not None:
                previous_info = self.word_infos[previous_word]
                previous_next = previous_info.get("next", {})
                previous_next[word] = previous_next.get(word, 0) + 1
                previous_info["next"] = previous_next
            # set new previous word, or None if ngram end is suspected (this could be optimized, but no priority)
            if full_word.endswith(word):
                previous_word = word
            else:
                previous_word = None

    # returns whether word is valid according to the dictionary, and, for the case it was capitalized, the valid form
    def dict_check(self, word: str, try_decapitalize: bool) -> tuple[bool, str]:
        if try_decapitalize and word[0].isupper():
            decapitalized = word[0].lower() + word[1:]
            if decapitalized in self.word_infos:
                return True, decapitalized
            # todo: lookup can be slow, optimize order with capitalization and nosuggest
            if not self.dictionary.lookuper(word, capitalization=True, allow_nosuggest=True):
                return False, word
            # word may be valid, check capitalization and nosuggest
            if self.dictionary.lookuper(word, capitalization=False, allow_nosuggest=False):
                return True, word
            if self.dictionary.lookuper(decapitalized, capitalization=False, allow_nosuggest=False):
                return True, decapitalized
            if self.dictionary.lookuper(word, capitalization=False, allow_nosuggest=True):
                self.word_infos[word] = {"nosuggest": True}
                return True, word
            if self.dictionary.lookuper(decapitalized, capitalization=False, allow_nosuggest=True):
                self.word_infos[decapitalized] = {"nosuggest": True}
                return True, decapitalized
            return False, word
        # we always want correct capitalization
        # maybe invert order for better performance, similar to above
        if not self.dictionary.lookuper(word, capitalization=False, allow_nosuggest=True):
            return False, word
        if self.dictionary.lookuper(word, capitalization=False, allow_nosuggest=False):
            return True, word
        self.word_infos[word] = {"nosuggest": True}
        return True, word

    def add_word(self, word: str, nosuggest: bool = False, add_to_count: bool = True):
        word_info = self.word_infos.get(word, {})
        word_info["count"] = word_info.get("count", 0) + 1
        if nosuggest:
            word_info["nosuggest"] = True
        self.word_infos[word] = word_info
        if add_to_count:
            self.count += 1
            self.count_valid += 1

    def add_sentence_file(self, filename: str, add_unknown_words: bool = False):
        with open(filename) as f:
            for line in f:
                self.add_line(line, add_unknown_words)

    def add_word_file(self, filename: str):
        with open(filename) as f:
            for line in f:
                for word in line.split():
                    self.add_word(word)

    # dicts need all the input, but only type and locale are relevant
    # type can be any ASCII string, but typically main is used
    #  note that only one dict can be loaded for each type
    #  using main also overrides any built-in dictionary in my version of OpenBoard
    # locale should be in compatible format (e.g. en, en_US, fr, fr_CA,...)
    def create_android_word_list(self, file_path, dict_type: str, locale: str, description: str, version: int):
        with open(file_path, 'w') as f:
            t = int(time.time())
            # e.g. dictionary=main:en_us,locale=en_US,description=English (US),date=1414726260,version=54
            header = f"dictionary={dict_type}:{locale.lower()},locale={locale},description={description},date={t},version={version}"
            if locale.startswith("de"):
                header += ",REQUIRES_GERMAN_UMLAUT_PROCESSING=1"
                # any special things for other languages?
                # russian dict has MULTIPLE_WORDS_DEMOTION_RATE=50 -> what's this?
            f.write(header + "\n")
            # deep copy to avoid modifying self.word_infos
            word_infos = copy.deepcopy(self.word_infos)
            # todo: check android dicts and maybe some documentation about frequencies
            add_frequencies(word_infos, 1, 250)
            filter_bigrams(word_infos, 3, 2)

            for word, infos in sorted(word_infos.items(), key=lambda item: -item[1]["count"]):
                frequency = infos["frequency"]
                if infos.get("nosuggest", False):
                    # todo: frequency of nosuggest words?
                    #  in AOSP dicts there are possibly_offensive words with freq > 0, but profanity has frequency 0
                    #  dictionaryFacilitator will add freq == 0 to history only as invalid words
                    #   -> what happens here? try and compare "hardcore" (f=112) and "Cid" (f=0)
                    #  hunspell nosuggest english is insults/slurs, which are f=0 in AOSP dictionaries
                    #   other possibly_offensive words found in AOSP dictionaries are not flagged at all
                    #   -> maybe find a way to extract this information from existing dictonaries?
                    #   but hunspell nosuggest german is also weird/rare word forms
                    f.write(f" word={word},f={frequency},possibly_offensive=true\n")
                else:
                    f.write(f" word={word},f={frequency}\n")
                if "next" in infos:
                    for next_word, freq in infos["next"].items():
                        f.write(f"  bigram={next_word},f={freq}\n")


# adds a "frequency" entry to each entry of word_infos
#  frequency is the log of input frequencies, and scaled between min_frequency and max_frequency
def add_frequencies(word_infos: dict[str, int], min_frequency: int, max_frequency: int):
    assert max_frequency > min_frequency
    max_count = 0
    min_count = 2147483647  # simply start with a very large number (int32 max)
    # first get max and min count
    for _, infos in word_infos.items():
        count = infos["count"]
        if count < min_count:
            min_count = count
        if count > max_count:
            max_count = count
    min_f = math.log(min_count)
    fdiff = max(math.log(max_count) - min_f, 1)
    for word, infos in word_infos.items():
        f = math.log(infos["count"])
        infos["frequency"] = int((f - min_f) * (max_frequency - min_frequency) / fdiff + min_frequency)


# modifies word_infos:
#  fewer entries per word (limiting to max_bigrams and requiring min_count occurences)
#  frequency replaced by order, starting at 1 for the most used, like it seems to be in the AOSP en(_US) dictionary
def filter_bigrams(word_infos: dict, max_bigrams, min_count):
    for word, infos in word_infos.items():
        if "next" not in infos:
            continue
        bigram = infos["next"]
        new_bigram = dict()
        bigram_count = 1
        for next_word, next_count in sorted(bigram.items(), key=lambda item: -item[1]):
            if bigram_count > max_bigrams or next_count < min_count:
                break
            new_bigram[next_word] = bigram_count
            bigram_count += 1
        infos["next"] = new_bigram


# highest frequency first
def sort_dict_by_count(d: dict[str, int]):
    return sorted(d.items(), key=lambda item: -item[1])


# use existing dictionary for spell check
# use sentence list to build word list
def example_1():
    d = Dictionary.from_files("/home/user/.local/lib/python3.10/site-packages/phunspell/data/dictionary/en/en_US")
    w = wordlist(dictionary=d)
    w.add_sentence_file("/home/user/eng_news_2020_100K-sentences.txt",
                        add_unknown_words=False)  # will only add words that pass the spell check
    w.create_android_word_list("/home/user/en_US_wordlist.compiled", "main", "en_US", "english", 1)


# use existing dictionary for spell check
# use words from unmunched (affix-expanded) dictionary
#  creates a much larger wordlist in some languages
# use sentence list to build word list
#  this is mostly for frequencies and next words, but may also add new words in some languages, e.g. German compund words
def example_2():
    d = Dictionary.from_files("/home/user/.local/lib/python3.10/site-packages/phunspell/data/dictionary/en/en_US")
    w = wordlist(dictionary=d)
    # unmunched cache not necessary for english, but helps for e.g. german or czech
    w.add_unmunched_dictionary(unmunched_cache="/home/user/en_unmunched.txt")  # adds all words with frequency 1
    w.add_sentence_file("/home/user/eng_news_2020_100K-sentences.txt", add_unknown_words=False)
    w.create_android_word_list("/home/user/en_US_wordlist.compiled", "main", "en_US", "english", 1)


# don't use a dictionary, only a word list
# this will produce low-quality suggestions, as word count is the same for all words
#  but if the word list contains duplicates, it will affect word count
def example_3():
    w = wordlist()
    w.add_word_file("/home/user/some_word_list.txt")
    w.create_android_word_list("/home/user/en_US_wordlist.compiled", "main", "en_US", "english", 1)


# don't use a dictionary, but provide a word list
# use a sentence file for word count and next word suggestions
def example_4():
    w = wordlist()
    w.add_word_file("/home/user/some_word_list.txt")
    w.add_sentence_file("/home/user/eng_news_2020_100K-sentences.txt", add_unknown_words=False)
    w.create_android_word_list("/home/user/en_US_wordlist.compiled", "main", "en_US", "english", 1)


# don't use a dictionary, but a list of sentences
# android word list may contain spelling errors depending on source of the sentences
def example_5():
    w = wordlist()
    w.add_sentence_file("/home/user/eng_news_2020_100K/eng_news_2020_100K-sentences.txt",
                        add_unknown_words=True)  # add all words to the word list, except some obvious non-words
    w.create_android_word_list("/home/user/en_US_wordlist.compiled", "main", "en_US", "english", 1)
