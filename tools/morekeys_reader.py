#!/bin/python
import pathlib
import xml.etree.ElementTree as ET
import langcodes
import re

# (WIP) script for reading moreKeys from tools used to create KeyTextsTable while resolving "!text/" references
# plan is to create a straightforward moreKeys file per language
xml_folder = "make-keyboard-text/src/main/resources/"
default_file = pathlib.Path(__file__).parent / f"{xml_folder}/values/donottranslate-more-keys.xml"
out_folder = pathlib.Path(__file__).parent / "../app/src/main/assets/language_key_texts/"


def append_to_morekeys(morekeys, text, label):
    if label in morekeys:
        morekeys[label] = morekeys[label] + " " + text
    else:
        morekeys[label] = text


def prepend_to_morekeys(morekeys, text, label):
    if label in morekeys:
        morekeys[label] = text + " " + morekeys[label]
    else:
        morekeys[label] = text


def read_keys(file):
    root = ET.parse(file).getroot()
    morekeys = dict()
    extra_keys = dict()
    labels = dict()
    number_row = dict()
    for key in root.iter("resources"):
        for string in key.iter("string"):
            for tag, value in string.items():
                if tag != "name":
                    print("ignoring tag " + tag)
                    continue
                if string.text is None:
                    text = ""
                else:
                    text = resolve_text(string.text, file)
                if "!text/" in text:
                    raise ValueError(f"can't have !text/ in {text} (from {string.text})")
                if "  " in text and "fixedColumnOrder" not in text:  # issues with arabic punctuation (more) keys
                    raise ValueError(f"can't have consecutive spaces in {text} (from {string.text})")
                if value.startswith("keyspec_") and "_row" in value and "slavic" not in value:
                    # put additional key labels (for nordic, spanish, swiss, german, but not for slavic, because here the keys are not extra)
                    key = value.split("_row")[1]
                    d = extra_keys.get(key, dict())
                    d["label"] = text
                    extra_keys[key] = d
                elif value.startswith("morekeys_") and "_row" in value and "slavic" not in value:
                    # put additional key morekeys (for nordic, spanish, swiss, german, but not for slavic, because here the keys are not extra)
                    key = value.split("_row")[1]
                    d = extra_keys.get(key, dict())
                    d["morekeys"] = text
                    extra_keys[key] = d
                elif value.startswith("morekeys_"):
                    key_label = value.split("morekeys_")[1]
                    if key_label == "period":
                        key_label = "punctuation"  # used in the same place
                    if len(key_label) > 1 and key_label != "punctuation":
                        if key_label.startswith("cyrillic_"):
                            label = key_label.split("cyrillic_")[1]
                            if label == "u":
                                key_label = "у"
                            elif label == "ka":
                                key_label = "к"
                            elif label == "ie":
                                key_label = "е"
                            elif label == "en":
                                key_label = "н"
                            elif label == "ghe":
                                key_label = "г"
                            elif label == "o":
                                key_label = "о"
                            elif label == "soft_sign":
                                key_label = "ь"
                            elif label == "a":
                                key_label = "а"
                            elif label == "i":
                                key_label = "и"
                            else:
                                print(f"ignoring cyrillic long more key: {key_label}: {text}")
                                continue
                        else:
                            if key_label not in ["bullet", "star", "left_parenthesis", "right_parenthesis", "less_than", "greater_than", "symbols_semicolon", "symbols_percent"]:
                                # only print for keys that are not already handled
                                print(f"ignoring long more key: {key_label}: {text}")
                            continue
                    morekeys[key_label] = text
                elif value == "single_quotes":
                    prepend_to_morekeys(morekeys, text, '\'')
                elif value == "single_angle_quotes":
                    append_to_morekeys(morekeys, text, '\'')
                elif value == "double_quotes":
                    prepend_to_morekeys(morekeys, text, '\"')
                elif value == "double_angle_quotes":
                    append_to_morekeys(morekeys, text, '\"')
                elif value == "keylabel_to_alpha":
                    labels["alphabet"] = text
                elif value == "keylabel_to_symbol":
                    labels["symbol"] = text
                elif value == "keyspec_comma":
                    labels["comma"] = text
                elif value == "keyspec_period":
                    labels["period"] = text
                elif value.startswith("keyspec_symbols_") and len(value.split("keyspec_symbols_")[1]) == 1:  # checking whether it's an int would be better, but bah
                    number_row[value.split("keyspec_symbols_")[1]] = text
                elif "values-ur" in file and value.startswith("additional_morekeys_symbols_"):
                    number_row[value.split("additional_morekeys_symbols_")[1]] = text
                    # for some reason ur has the arabic numbers in moreKeys
                elif value in ["keyspec_currency", "symbols_semicolon", "symbols_percent"] or value.startswith("additional_morekeys_symbols_") or "_left_" in value or "_right_" in value or "_greater" in value or "_less_" in value:
                    pass  # ignore keys handled somewhere else (currency key not yet fully replaced)
                else:
                    print(f"ignored tag: {tag}={value}, {text}")
    keys = dict()
    keys["morekeys"] = morekeys
    keys["extra_keys"] = extra_keys
    keys["labels"] = labels
    keys["number_row"] = number_row
    return keys


def resolve_text(text, file):
    if text.startswith("\"") and text.endswith("\"") and len(text) > 1:
        text = text[1:-1]
    sp = re.split("(?<!\\\\),", text)  # split on comma, but not on escaped comma "\,"

    if len(sp) > 1:  # resolve each entry separately
        result = []
        for t in sp:
            resolved = resolve_text(t, file)
            if text.startswith("\\"):  # remove backslash at start, this seems to be happening somewhere in android parsing too
                result.append(resolved[1:])
            else:
                result.append(resolved)
        return " ".join(result)  # join with space, because that doesn't cause issues with comma in moreKeys
    if "!text/" not in text:
        if text.startswith("\\"):  # see above
            return text[1:]
        return text
    root = ET.parse(file).getroot()
    sp = text.split("!text/")
    required = sp[1]
    for key in root.findall(".//string"):
        for tag, value in key.items():
            if tag == "name" and value == required:
                return resolve_text(key.text, file)
    # fall back to searching in no-language values
    root = ET.parse(default_file).getroot()
    for key in root.findall(".//string"):
        for tag, value in key.items():
            if tag == "name" and value == required:
                return resolve_text(key.text, file)
    raise LookupError(text + " not found in " + str(file))


def read_locale_from_folder(folder):
    if folder.startswith("values-"):
        return folder.split("values-")[1]
    return None


def write_keys(outfile, keys, locc=""):
    with open(outfile, "w") as f:
        # write section [more_keys], then [extra_keys], skip if empty
        has_extra_keys = len(keys["extra_keys"]) > 0
        has_labels = len(keys["labels"]) > 0
        has_number_row = len(keys["number_row"]) > 0
        if len(keys["morekeys"]) > 0:
            f.write("[morekeys]\n")
            for k, v in keys["morekeys"].items():
                f.write(f"{k} {v}\n")
            if has_labels or has_number_row or has_extra_keys:
                f.write("\n")
        if has_extra_keys and locc != "eo":  # eo has the extra key moved into the layout
            f.write("[extra_keys]\n")
            # clarify somewhere that extra keys only apply to default layout (where to get?)
            for k, v in sorted(keys["extra_keys"].items()):
                row = k.split("_")[0]
                morekeys = v.get("morekeys", "")
                label = v["label"]
                if len(morekeys) == 0:
                    f.write(f"{row}: {label}\n")
                else:
                    f.write(f"{row}: {label} {morekeys}\n")
            if has_labels or has_number_row:
                f.write("\n")
        if has_labels:
            f.write("[labels]\n")
            for k, v in keys["labels"].items():
                f.write(f"{k}: {v}\n")
            if has_number_row:
                f.write("\n")
        if has_number_row:
            if len(keys["number_row"]) != 10:
                raise ValueError("number row must have 10 keys")
            f.write("[number_row]\n")
            zero = keys["number_row"]["0"]
            for k, v in sorted(keys["number_row"].items()):
                if k == "0":
                    continue
                f.write(f"{v} ")
            f.write(f"{zero}\n")


def get_morekeys_texts(write=False):
    val = []
    for file in (pathlib.Path(__file__).parent / xml_folder).iterdir():
        locc = read_locale_from_folder(file.name)
        if locc is None:
            continue
        loc = langcodes.Language.get(locc)

        script = loc.assume_script().script
        # some scripts are not detected, fill in the current state of OpenBoard
        if locc == "sr" or locc == "ky" or locc == "mn":
            script = "Cyrl"
        if locc == "sr-rZZ" or locc == "uz" or locc == "zz" or locc == "az" or locc == "tl":
            script = "Latn"
        if script is None:
            raise ValueError("undefined script")
        if script != "Latn":
            continue  # skip non-latin scripts
        print(file)
        keys = read_keys(f"{file}/donottranslate-more-keys.xml")
        val.append(keys)
        if not write:
            continue
        outfile_name = locc.replace("-r", "_").lower() + ".txt"
#        outfile = pathlib.Path(out_folder + outfile_name)
        outfile = out_folder / outfile_name
        outfile.parent.mkdir(exist_ok=True, parents=True)
        write_keys(outfile, keys, locc)
    return val


# write lists of all moreKeys from different languages
def write_combined_lists(keys):
    infos_by_letters = dict()
    for key in keys:
        for k, v in key["morekeys"].items():
            infos = infos_by_letters.get(k, dict())
            for l in v.split(" "):
                if l == "%":
                    continue
                infos[l] = infos.get(l, 0) + 1
            infos_by_letters[k] = infos
    with open(out_folder / "all_more_keys.txt", 'w') as f:
        f.write("[morekeys]\n")
        for letter, info in infos_by_letters.items():
            sorted_info = dict(sorted(info.items(), key=lambda item: item[1], reverse=True))
            f.write(letter + " " + " ".join(sorted_info.keys()) + "\n")
    with open(out_folder / "more_more_keys.txt", 'w') as f:
        f.write("[morekeys]\n")
        for letter, info in infos_by_letters.items():
            morekeys = []
            for morekey, count in sorted(info.items(), key=lambda item: item[1], reverse=True):
                if count > 1:
                    morekeys.append(morekey)
            if len(morekeys) > 0:
                f.write(letter + " " + " ".join(morekeys) + "\n")


def main():
#    k = read_keys(default_file)
#    write_keys(pathlib.Path(__file__).parent / f"defaultkeys.txt", k)
    keys = get_morekeys_texts(False)
#    write_combined_lists(keys)


# need to check strings:
# latin, but only in symbol layout
#  single_quotes, double_quotes (both used in morekeys of single/double quotes in symbol keyboard)
#  single_angle_quotes, double_angle_quotes (same place as above -> merge into the same base ' or ")
#  -> just treat them like morekeys_' and morekeys_"
#  ... resolving those is really horrible, check different things and maybe include all if not too much?
# latin, but for layout and not for moreKeys
#  keyspec_nordic_row (+swiss and spanish) -> normal keys, what do? really specify a layout? or allow modifying?
#  keyspec_q + w, y, x (eo only -> hmm, have a separate layout?)
# not latin, but cyrillic (and maybe other non-latin)
#  keyspec_east_slavic_row
#  keylabel_to_alpha
#  label_go_key and other keys (hi-rZZ and sr-rZZ -> why here? they should be in app strings, right?)
# not in latin (so far)
#  keyspec_symbols
#  additional_morekeys_symbols
#  keyspec_currency
#  keylabel_to_symbol
#  keyspec_comma
#  keyhintlabel_period -> that's with the shifted key hint maybe
#  keyhintlabel_tablet_period
#  keyspec_period
#  keyspec_tablet_period
#  keyspec_symbols_question
#  keyspec_symbols_semicolon
#  keyspec_symbols_percent
#  keyspec_tablet_comma
#  keyhintlabel_tablet_comma
#  keyspec_left_parenthesis + right
#  keyspec_left_square_bracket + right
#  keyspec_left_curly_bracket + right
#  keyspec_less_than + greater
#  keyspec_less_than_equal + greater
#  keyspec_left_double_angle_quote + right
#  keyspec_left_single_angle_quote + right


if __name__ == "__main__":
    main()
