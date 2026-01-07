import re

# very simple script to convert new emojis from emojipedia to the format used in android-emoji-support.txt
# use:
#  go to e.g. https://emojipedia.org/emoji-17.0
#  copy the new emojis to new_emojis.txt
#  run the script
#  paste output into a new section of android-emoji-support.txt
with open("new_emojis.txt") as f:
    p = r'[A-Z]'
    for line in f:
        line = line.strip()
        emo = re.split(p, line, 0)[0]
        text = line.replace(emo, "").strip()
        emo_text = str(emo.encode("unicode_escape")).split("'")[1].upper().replace("U", "U+").replace("0001", "1").split("\\\\")[1:]
        print(" ".join(emo_text) + " # " + text.lower())
