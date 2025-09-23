# make-emoji-keys

This module takes care of generating emoji data bundled with SociaKeyboard.
Basically data is generated in three steps:
1. Unicode emoji table provides code points and grouping plus ordering.
2. Local file lists every new emojis supported for every android version since 4.4.
3. Emoji sequences are merged with their base version and formatted into android resource file.

### Generate emoji-categories.xml

A gradle task called 'makeEmoji' builds and runs this tool to generate android resource file which. Path to SociaKeyboard's res directory is automatically set so the file is ready to be bundled at build.

### Update to latest emoji version

* Get new emoji data from Unicode official repository located here: https://unicode.org/Public/emoji.
* Create a new directory in [/src/main/resources/emoji/ucd](/tools/make-emoji-keys/src/main/resources/emoji/ucd) and name it as a decimal number corresponding to Unicode's version.
* Update [android-emoji-support.txt](/tools/make-emoji-keys/src/main/resources/emoji/android-emoji-support.txt) with new emojis supported in latest Android versions.
* Run :
  ```sh
  ./gradlew tools:make-emoji-keys:makeEmoji
  ```
