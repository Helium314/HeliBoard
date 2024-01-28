# OpenBoard upgrade, WIP
IMPORTANT: The package will be renamed soon. To avoid merge conflicts, please do not submit any PRs until the renaming is done.

## Plan for actual release
The plans for major changes are completed, and most features appear to be stable enough for a proper release.
So what comes next:
* Work on issues with the [when ready](https://github.com/Helium314/openboard/labels/when%20ready) label
* Rename app, package and this repository
* New icon
* Use a translation tool (probably weblate)
* Release on F-Droid
* Maybe add a version that does not allow providing a glide typing library, for people concerned about security

## Features
* Allow loading Glide typing library
  * not included in the app, as there is no compatible open source library
  * can be extracted from GApps packages (_swypelibs_), or downloaded [here](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs)
* Multilingual typing
* Load external dictionaries
  * get them [here](https://codeberg.org/Helium314/aosp-dictionaries#dictionaries), or in the [experimental](https://codeberg.org/Helium314/aosp-dictionaries#experimental-dictionaries) section (quality may vary)
  * add them in language settings (click on the language, then on `+` next to _dictionary_), or open the file in a file explorer
  * additional dictionaries for emojis or scientific symbols can be used to provide suggestions ("emoji search")
  * note that for Korean layouts, suggestions only work using [this dictionary](https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f), the tools in the dictionary repository are not able to create working dictionaries
* Adjust keyboard themes (style and colors)
  * can follow the system's day/night setting on Android 10+ (and on some versions of Android 9)
* Split keyboard (if the screen is large enough)
* Number row
* Number pad
* Show all available extra characters on long pressing a key
* Backup your learned word / history data
* Add custom keyboard [layouts](layouts.md)
* Adjustable bottom padding

## Hidden functionality
Features that may go unnoticed, and further potentially useful information
* Long-pressing the Clipboard Key (the optional one in the suggestion strip) pastes system clipboard contents.
* Long-pressing keys in the suggestion strip toolbar pins them to the suggestion strip.
* Long-press the Comma-key to access Clipboard View, Emoji View, One-handed Mode, Settings, or Switch Language:
  * Emoji View and Language Switch will disappear if you have the corresponding key enabled;
  * For some layouts it\'s not the Comma-key, but the key at the same position (e.g. it\'s `q` for Dvorak layout).
* When incognito mode is enabled, no words will be learned, and no emojis will be added to recents.
* Sliding key input: Swipe from shift to another key to type a single uppercase key:
  * This also works for the `?123` key to type a single symbol from the symbols keyboard, and for related keys.
* Long-press a suggestion in the suggestion strip to show more suggestions, and a delete button to remove this suggestion.
* Swipe up from a suggestion to open more suggestions, and release on the suggestion to select it.
* Long-press an entry in the clipboard history to pin it (keep it in clipboard until you unpin).
* Swipe left in clipboard view to remove an entry (except when it's pinned)
* You can add dictionaries by opening them in a file explorer:
  * This only works with _content-uris_ and not with _file-uris_, meaning that it may not work with some file explorers.
* _When using debug mode / debug APK_
  * Long-press a suggestion in the suggestion strip twice to show the source dictionary.
  * When using debug APK, you can find Debug Settings within the Advanced Preferences, though the usefulness is limited except for dumping dictionaries into the log.
  * In the event of an application crash, you will be prompted whether you want the crash logs when you open the Settings.
  * When using multilingual typing, space bar will show an confidence value used for determining the currenly used language.
  * Suggestions will have some tiny numbers on top showing some internal score and source dictionary (can be disabled)
* For users doing manual backups with root access: Starting at Android 7, the shared preferences file is not in the default location, because the app is using [device protected storage](https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()). This is necessary so the settings can be read before the device is unlocked, e.g. at boot. The file is located in `/data/user_de/0/package_id/shared_prefs/`, though this may depend on the device and Android version.

# Contribution ❤

## Issue reporting

Whether you encountered a bug, or want to see a new feature in OpenBoard, you can contribute to the project by opening a new issue [here](https://github.com/openboard-team/openboard/issues). Your help is always welcome!

Before opening a new issue, be sure to check the following:
 - **Does the issue already exist ?** Make sure a similar issue has not been reported by browsing [existing issues](https://github.com/Helium314/openboard/issues). Please search open and closed issues.
 - **Is the issue still relevant ?** Make sure your issue is not already fixed in the latest version of OpenBoard.
 - **Did you use the issue template ?** It is important to make life of our kind contributors easier by avoiding  issues that miss key informations to their resolution.

## Translation
Currently there is no simple way of translating the app, but it's coming soon...

## Dictionary creation
There will not be any further dictionaries bundled in this app. However, you can add dictionaries to the [dictionaries repository](https://codeberg.org/Helium314/aosp-dictionaries).
To create or update a dictionary for your language, you can use [this tool](https://github.com/remi0s/aosp-dictionary-tools). You will need a wordlist, as described [here](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/wordlists/sample.combined) and in the repository readme.

## Code contribution
IMPORTANT: The package will be renamed soon. To avoid merge conflicts, please do not submit any PRs until the renaming is done.

### Getting started

OpenBoard project is based on Gradle and Android Gradle Plugin. To get started, you'll just need to install [Android Studio](https://developer.android.com/studio), and import project 'from Version Control / Git / Github' by providing this git repository [URL](https://github.com/openboard-team/openboard) (or git SSH [URL](git@github.com:openboard-team/openboard.git)).

Once everything got setted up correctly, you're ready to go !

### Guidelines

OpenBoard is a complex application, when contributing, you must take a step back and make sure your contribution :
- **Uses already in-place mechanism and take advantage of them**. In other terms, does not reinvent the wheel or uses shortcuts that could alter the consistency of the existing code.
- **Has the lowest footprint possible**. OpenBoard code has been written by android experts (AOSP/Google engineers). It has been tested and runned on millions of devices. Thus, **existing code will always be safer than new code**. The less we alter existing code, the more OpenBoard will stay stable. Especially in the input logic scope.
- **Does not bring any non-free code or proprietary binary blobs**. This also applies to code/binaries with unknown licenses. Make sure you do not introduce any closed-source library from Google.
- **Complies with the user privacy principle OpenBoard follows**. 

In addition to previous elements, OpenBoard must stick to [F-Droid inclusion guidelines](https://f-droid.org/docs/Inclusion_Policy/).

### Adding layouts

See [layouts.md](layouts.md) for how to add new layouts to the app.

### Update emojis

See make-emoji-keys tool [README](tools/make-emoji-keys/README.md).

# License

OpenBoard (and this yet unnamed fork) project is licensed under GNU General Public License v3.0.

 > Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE-GPL-3) file.

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided too.

# Credits
- Icon by [Marco TLS](https://www.marcotls.eu)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
- Our [contributors](https://github.com/Helium314/openboard/graphs/contributors)
