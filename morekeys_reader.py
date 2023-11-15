#!/bin/python
import pathlib
import xml.etree.ElementTree as ET
import langcodes
import re

# (WIP) script for reading moreKeys from tools used to create KeyTextsTable while resolving "!text/" references
# plan is to create a straightforward moreKeys file per language
xml_folder = "tools/make-keyboard-text/src/main/resources/"
default_file = pathlib.Path(__file__).parent / f"{xml_folder}/values/donottranslate-more-keys.xml"
out_folder = "app/src/main/assets/language_key_texts/"


def read_keys(file):
    root = ET.parse(file).getroot()
    morekeys = dict()
    extra_keys = dict()
    for key in root.iter("resources"):
        for string in key.iter("string"):
            for tag, value in string.items():
                text = resolve_text(string.text, file)
                if "!text/" in text:
                    raise ValueError(f"can't have !text/ in {text} (from {string.text})")
                if "  " in text:
                    raise ValueError(f"can't have consecutive spaces in {text} (from {string.text})")
                if tag == "name" and value.startswith("keyspec_") and "_row" in value:
                    # put additional key labels (for nordic, spanish, swiss)
                    key = value.split("_row")[1]
                    d = extra_keys.get(key, dict())
                    d["label"] = text
                    extra_keys[key] = d
                elif tag == "name" and value.startswith("morekeys_") and "_row" in value:
                    # put additional key morekeys (for nordic, spanish, swiss)
                    key = value.split("_row")[1]
                    d = extra_keys.get(key, dict())
                    d["morekeys"] = text
                    extra_keys[key] = d
                elif tag == "name" and value.startswith("morekeys_"):
                    key_label = value.split("morekeys_")[1]
                    if len(key_label) > 1 and key_label != "punctuation":
                        print(f"ignoring long more key: {key_label}: {text}")
                        continue
                    morekeys[key_label] = text
                elif tag == "name" and (value == "single_quotes" or value == "single_angle_quotes"):
                    morekeys['\''] = text
                elif tag == "name" and (value == "double_quotes" or value == "double_angle_quotes"):
                    morekeys['\"'] = text
                # todo: labels should be in [labels] and use sth like symbols: ?123
                else:
                    print(f"ignored tag: {tag}={value}, {text}")
    keys = dict()
    keys["morekeys"] = morekeys
    keys["extra_keys"] = extra_keys
    return keys


def resolve_text(text, file):
    if text.startswith("\"") and text.endswith("\"") and len(text) > 1:
        text = text[1:-1]
    sp = re.split("(?<!\\\\),", text)

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
    raise LookupError(f"{text} not found in {file}")


def read_locale_from_folder(folder):
    if folder.startswith("values-"):
        return folder.split("values-")[1]
    return None


def get_morekeys_texts():
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
            continue  # skip non-latin scripts for now
        print(file)
        keys = read_keys(f"{file}/donottranslate-more-keys.xml")
        if not locc.startswith("defff"):
            continue
        outfile_name = locc.replace("-r", "_").lower() + ".txt"
        outfile = pathlib.Path(out_folder + outfile_name)
        outfile.parent.mkdir(exist_ok=True, parents=True)
        with open(outfile, "w") as f:
            # write section [more_keys], then [extra_keys], skip if empty
            if len(keys["morekeys"]) > 0:
                f.write("[morekeys]\n")
                for k, v in keys["morekeys"].items():
                    f.write(f"{k} {v}\n")
                if len(keys["extra_keys"]) > 0:
                    f.write("\n")
            if len(keys["extra_keys"]) > 0:
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

        # ignored:
        #  the key labels for sr-Latn: test it later, current state: sr-Cyrl has cyrillic labels, sr-Latn has default locale labels
        #  currency: better move vietnamese currency key to the normal currency key style
        # issue:


def main():
    get_morekeys_texts()

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
