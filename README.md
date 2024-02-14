# HeliBoard
HeliBoard is a privacy-conscious and customizable open-source keyboard, based on AOSP / OpenBoard.

## Table of Contents

- [Features](#features)
   * [FAQ / Common Issues](#faq--common-issues)
   * [Hidden Functionality](#hidden-functionality)
- [Contributing](#contributing-)
   * [Reporting Issues](#reporting-issues)
   * [Translation](#translation)
   * [Dictionary Creation](#dictionary-creation)
   * [Code Contribution](#code-contribution)
- [License](#license)
- [Credits](#credits)

# Features
* Add dictionaries for suggestions and spell check
  * build your own, or get them [here](https://codeberg.org/Helium314/aosp-dictionaries#dictionaries), or in the [experimental](https://codeberg.org/Helium314/aosp-dictionaries#experimental-dictionaries) section (quality may vary)
  * add them in language settings (click on the language, then on `+` next to _dictionary_), or open the file in a file explorer
  * additional dictionaries for emojis or scientific symbols can be used to provide suggestions ("emoji search")
  * note that for Korean layouts, suggestions only work using [this dictionary](https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f), the tools in the dictionary repository are not able to create working dictionaries
* Customize keyboard themes (style, colors and background image)
  * can follow the system's day/night setting on Android 10+ (and on some versions of Android 9)
  * can follow dynamic colors for Android 12+
* Customize keyboard [layouts](layouts.md)
* Multilingual typing
* Glide typing (_only with closed source library_)
  * library not included in the app, as there is no compatible open source library available
  * can be extracted from GApps packages ("_swypelibs_"), or downloaded [here](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs)
  * if you are concerned about security implications of loading user-provides libraries, please use the _nouserlib_ build variant, which removes this option. If HeliBoard is installed as system app, and _swypelibs_ are available for the system, they will be used.
* Clipboard history
* One-handed mode
* Split keyboard (if the screen is large enough)
* Number pad
* Backup and restore your learned word / history data

## FAQ / Common Issues
* __Emoji search__: You can get addon dictionaries for emoji suggestions in the [dictionaries repo](https://codeberg.org/Helium314/aosp-dictionaries). An actual search funtion does not exist.
* __No suggestions for some language__: Check [dictionaries repo](https://codeberg.org/Helium314/aosp-dictionaries) whether a dictionary is available. If there is one, download it and add it in the language settings for this language.
* __No suggestions in some app / text field__: This app respects the [no suggestions flag](https://developer.android.com/reference/android/text/InputType#TYPE_TEXT_FLAG_NO_SUGGESTIONS) set by some input fields, i.e. the developer does not want you to see suggestions here. Best do in issue report for that app if you think this behavior is wrong. Alternatively you can enable the _always show suggestions_ setting that overrides the _no suggestions_ flag.
* __How to enable glide typing__: There is no glide typing built into this app, but you can load compatible libraries: Go to advanced settings -> _load gesture typing library_ and point to a file. You can extract the file from GApps packages ("_swypelibs_"), or download one [here](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs). Make sure to use the correct version (app will tell you in the dialog to load the library).
* (_to be expanded_...) <!-- auto-correct? incognito always on? can't load library? -->

## Hidden Functionality
Features that may go unnoticed, and further potentially useful information
* Long-pressing the Clipboard Key (the optional one in the suggestion strip) pastes system clipboard contents.
* Long-pressing keys in the suggestion strip toolbar pins them to the suggestion strip.
* Long-press the Comma-key to access Clipboard View, Emoji View, One-handed Mode, Settings, or Switch Language:
  * Emoji View and Language Switch will disappear if you have the corresponding key enabled;
  * For some layouts it\'s not the Comma-key, but the key at the same position (e.g. it\'s `q` for Dvorak layout).
* When incognito mode is enabled, no words will be learned, and no emojis will be added to recents.
* Sliding key input: Swipe from shift to another key to type a single uppercase key
  * This also works for the `?123` key to type a single symbol from the symbols keyboard, and for related keys.
* Long-press the `?123` from main view to directly open numpad.
* Long-press a suggestion in the suggestion strip to show more suggestions, and a delete button to remove this suggestion.
* Swipe up from a suggestion to open more suggestions, and release on the suggestion to select it.
* Long-press an entry in the clipboard history to pin it (keep it in clipboard until you unpin).
* Swipe left in clipboard view to remove an entry (except when it's pinned)
* You can add dictionaries by opening the file
  * This only works with _content-uris_ and not with _file-uris_, meaning that it may not work with some file explorers.
* _When using debug mode / debug APK_
  * Long-press a suggestion in the suggestion strip twice to show the source dictionary.
  * When using debug APK, you can find Debug Settings within the Advanced Preferences, though the usefulness is limited except for dumping dictionaries into the log.
  * In the event of an application crash, you will be prompted whether you want the crash logs when you open the Settings.
  * When using multilingual typing, space bar will show an confidence value used for determining the currenly used language.
  * Suggestions will have some tiny numbers on top showing some internal score and source dictionary (can be disabled)
* For users doing manual backups with root access: Starting at Android 7, some files and the main shared preferences file are not in the default location, because the app is using [device protected storage](https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()). This is necessary so the settings and layout files can be read before the device is unlocked, e.g. at boot. The files are usually located in `/data/user_de/0/<package_id>/`, though the location may depend on the device and Android version.

# Contributing ❤

## Reporting Issues

Whether you encountered a bug, or want to see a new feature in HeliBoard, you can contribute to the project by opening a new issue [here](https://github.com/Helium314/HeliBoard/issues). Your help is always welcome!

Before opening a new issue, be sure to check the following:
 - **Does the issue already exist?** Make sure a similar issue has not been reported by browsing [existing issues](https://github.com/Helium314/HeliBoard/issues). Please search open and closed issues.
 - **Is the issue still relevant?** Make sure your issue is not already fixed in the latest version of HeliBoard.
 - **Did you use the issue template?** It is important to make life of our kind contributors easier by avoiding issues that miss key information to their resolution.
Note that issues that that ignore part of the issue template will likely get treated with very low priority, as often they are needlessly hard to read or understand (e.g. huge screenshots, or addressing multiple topics).

## Translation
Translations can be added using [Weblate](https://translate.codeberg.org/projects/heliboard/). You will need an account to update translations and add languages. Add the language you want to translate to in Languages -> Manage translated languages in the top menu bar.

## Dictionary Creation
There will not be any further dictionaries bundled in this app. However, you can add dictionaries to the [dictionaries repository](https://codeberg.org/Helium314/aosp-dictionaries).
To create or update a dictionary for your language, you can use [this tool](https://github.com/remi0s/aosp-dictionary-tools). You will need a wordlist, as described [here](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/wordlists/sample.combined) and in the repository readme.

## Code Contribution

### Getting Started

HeliBoard project is based on Gradle and Android Gradle Plugin. To get started, you can install [Android Studio](https://developer.android.com/studio), and import project 'from Version Control / Git / Github' by providing this git repository [URL](https://github.com/Helium314/HeliBoard) (or git SSH [URL](git@github.com:Helium314/openboard.git)).
Of course you can also use any other compatible IDE, or work with text editor and command line.

Once everything is up correctly, you're ready to go!

### Guidelines

HeliBoard is a complex application, when contributing, you must take a step back and make sure your contribution:
- **Is actually wanted**. Best check related open issues before you start working on a PR. Issues with "PR" and "contributor needed" labels are accepted, but still it would be good if you announced that you are working on it.
If there is no issue related to your intended contribution, it's a good idea to open a new one to avoid disappointment of the contribution not being accepted. For small changes or fixing obvious bugs this step is not necessary.
- **Is only about a single thing**. Mixing unrelated contributions into a single PR is hard to review and can get messy.
- **Has a proper description**. What your contribution does is usually less obvious to reviewers than for yourself. A good description helps a lot for understanding what is going on, and for separating wanted from unintended changes in behavior.
- **Uses already in-place mechanism and take advantage of them**. In other terms, does not reinvent the wheel or uses shortcuts that could alter the consistency of the existing code.
- **Has a low footprint**. Some parts of the code are executed very frequently, and the keyboard should stay responsive even on older devices.
- **Does not bring any non-free code or proprietary binary blobs**. This also applies to code/binaries with unknown licenses. Make sure you do not introduce any closed-source library from Google.
If your contribution contains code that is not your own, provide a link to the source.
- **Complies with the user privacy principle HeliBoard follows**. 

In addition to previous elements, HeliBoard must stick to [F-Droid inclusion guidelines](https://f-droid.org/docs/Inclusion_Policy/).

### Adding Layouts

See [layouts.md](layouts.md) for how to add new layouts to the app.

### Update Emojis

See make-emoji-keys tool [README](tools/make-emoji-keys/README.md).

# License

HeliBoard (as a fork of OpenBoard) is licensed under GNU General Public License v3.0.

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
- Our [contributors](https://github.com/Helium314/HeliBoard/graphs/contributors)
