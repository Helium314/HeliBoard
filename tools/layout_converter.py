import pathlib
import re


def simple(files):
    mk = []
    kss = []
    for file in files:
        with open(file) as f:
            ks = []
            waiting_for_morekeys = None
            for line in f:
                if "moreKeys" in line and waiting_for_morekeys is not None:
                    r = re.findall(r"&#x\w{4};", line)
                    for x in r:
                        replacement = x.replace(";", "").replace("&#x", "\\u").encode().decode('unicode-escape')
                        line = line.replace(x, replacement)
                    line = line.split("\"")[1]
                    mk.append(waiting_for_morekeys + " " + line.replace(",", " "))
                    waiting_for_morekeys = None
                if "keySpec" in line:
                    r = re.findall(r"&#x\w{4};", line)
                    if len(r) != 1:
                        print("something wrong")
                    c = r[0].replace(";", "").replace("&#x", "\\u").encode().decode('unicode-escape')
                    print(c)
                    ks.append(c)
                    waiting_for_morekeys = c
            print("")
            kss.append(ks)
    print("[morekeys]\n")
    print("\n".join(mk))


def shift(files):
    mk = []
    kss = []
    kss_shift = []
    for index, file in enumerate(files):
        with open(file) as f:
            shift = False
            ks = []
            ks_shift = []
            waiting_for_morekeys = None
            for line in f:
                if "alphabetManualShifted|alphabetShiftLocked|alphabetShiftLockShifted" in line:
                    shift = True
                if "<default>" in line:
                    shift = False
                if "keySpec" in line:
                    l = line.split("\"")[1]
                    r = re.findall(r"&#x\w{4};", line)
                    for x in r:
                        replacement = x.replace(";", "").replace("&#x", "\\u").encode().decode('unicode-escape')
                        l = l.replace(x, replacement)
                    r = re.findall(r"&#\w{4};", line)
                    for x in r:
                        replacement = int(x.replace(";", "").replace("&#", ""))
                        l = l.replace(x, chr(replacement))
                    c = l
                    if shift:
                        ks_shift.append(c)
                    else:
                        ks.append(c)
                    waiting_for_morekeys = c
                if "moreKeys" in line and waiting_for_morekeys is not None:
                    l = line.split("\"")[1]
                    r = re.findall(r"&#x\w{4};", line)
                    for x in r:
                        replacement = x.replace(";", "").replace("&#x", "\\u").encode().decode('unicode-escape')
                        l = l.replace(x, replacement)
                    r = re.findall(r"&#\w{4};", l)
                    for x in r:
                        replacement = int(x.replace(";", "").replace("&#", ""))
                        l = l.replace(x, chr(replacement))
                    mk.append(waiting_for_morekeys + " " + l.replace(",", " "))
                    waiting_for_morekeys = None
                if "/>" in line:
                    waiting_for_morekeys = None
            kss.append(ks)
            kss_shift.append(ks_shift)
            if len(ks) != len(ks_shift):
                print(f"dammit, {len(ks)}, {len(ks_shift)}")
    print("[")
    for i, ks in enumerate(kss):
        print("  [")
        for j, k in enumerate(ks):
            print("    { \"$\": \"shift_state_selector\",")
            print("      \"manualOrLocked\": { \"label\": \"" + kss_shift[i][j] + "\" },")
            print("      \"default\": { \"label\": \"" + k + "\" }")
            print("    },")
        print("  ],")
    print("]\n")
    print("[morekeys]\n")
    print("\n".join(mk))

# select layout, run and copy the text for layout and moreKeys (for locale_key_texts)
# note that additionalMoreKeys are ignored, these are often symbols (will change to be determined automatically at some point), and numbers (already determined automatically)
# json layouts have additional commas in the end of arrays, should be removed manually
d = pathlib.Path("../app/src/main/res/xml/")
files = []
for f in d.iterdir():
    if f.name.startswith("rowkeys") and "unijoy" in f.name and ("left" in f.name or "right" in f.name):
        files.append(f)

# shift for layouts where all keys change when shifted (alphabetManualShifted|alphabetShiftLocked|alphabetShiftLockShifted)
# ignores extra attributes like labelFlags
shift(files)

# simple for layouts that have no case switch
#simple(files)
