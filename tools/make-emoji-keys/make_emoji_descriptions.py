#!/bin/python
import http.client
import io
import json
import re
import time
import os
import glob
from xml.etree.ElementTree import ElementTree

emojiList = ''
path = '../../app/src/main/assets/emoji'
for filename in glob.glob(os.path.join(path, '*.txt')):
   with open(os.path.join(os.getcwd(), filename), encoding="utf-8") as emojiFile:
       emojiList += re.sub(r"\s*\d*\w*", "", ''.join(emojiFile.readlines()))

connection = http.client.HTTPSConnection("raw.githubusercontent.com", timeout=30)

def get_descriptions(url, key):
    try:
        connection.request("GET", url)
        response = connection.getresponse()
        if response.status >= 400:
            return {}

        data = json.loads(response.read().decode())
        ann = data[key]
        if "annotations" not in ann:
            return {}

        descriptions = {}
        for emoji, emodata in ann["annotations"].items():
            if "tts" not in emodata:
                continue

            if emoji not in emojiList:
                continue

            descriptions[emoji] = emodata["tts"][0]

        return descriptions
    finally:
        connection.close()

methods = ElementTree(file='../../app/src/main/res/xml/method.xml')
localeList = [subtype.get('{http://schemas.android.com/apk/res/android}languageTag') for subtype in
            methods.findall('./subtype')]
locales = set(localeList)
for locale in localeList:
    locales.add(locale.split('-')[0])

for locale in locales:
    descriptions = (get_descriptions(f"/unicode-org/cldr-json/refs/heads/main/cldr-json/cldr-annotations-full"
                                   f"/annotations/{locale}/annotations.json", "annotations")
                    | get_descriptions(f"/unicode-org/cldr-json/refs/heads/main/cldr-json/cldr-annotations-derived-full"
                      f"/annotationsDerived/{locale}/annotations.json", "annotationsDerived"))
    if not descriptions:
        continue

    # now write
    with io.open(path + f"/descriptions/{locale}.txt", 'w', encoding="utf-8") as f:
        now = int(time.time())
        f.write(f"description=Emoji descriptions,locale={locale},date={now}\n")
        for emoji, description in descriptions.items():
            f.write(f"{emoji}={description}\n")
