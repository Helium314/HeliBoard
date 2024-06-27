# HeliBoard
HeliBoard is a privacy-conscious and customizable open-source keyboard, based on AOSP / OpenBoard.
Does not use internet permission, and thus is 100% offline.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/helium314.keyboard/)
[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/Helium314/HeliBoard/releases/latest)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/helium314.keyboard)

## Table of Contents

- [Features](#features)
   * [FAQ / Common Issues](#faq--common-issues)
   * [Hidden Functionality](#hidden-functionality)
- [Contributing](#contributing-)
   * [Reporting Issues](#reporting-issues)
   * [Translations](#translations)
   * [Dictionary Creation](#dictionary-creation)
   * [Code Contribution](CONTRIBUTING.md)
- [To-do](#to-do)
- [License](#license)
- [Credits](#credits)

# Features
<ul>
  <li>Add dictionaries for suggestions and spell check</li>
  <ul>
    <li>build your own, or get them  <a href="https://codeberg.org/Helium314/aosp-dictionaries#dictionaries">here</a>, or in the <a href="https://codeberg.org/Helium314/aosp-dictionaries#experimental-dictionaries">experimental</a> section (quality may vary)</li>
    <li>additional dictionaries for emojis or scientific symbols can be used to provide suggestions (similar to "emoji search")</li>
    <li>note that for Korean layouts, suggestions only work using <a href="https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f">this dictionary</a>, the tools in the dictionary repository are not able to create working dictionaries</li>
  </ul>
  <li>Customize keyboard themes (style, colors and background image)</li>
  <ul>
    <li>can follow the system's day/night setting on Android 10+ (and on some versions of Android 9)</li>
    <li>can follow dynamic colors for Android 12+</li>
  </ul>
  <li>Customize keyboard <a href="https://github.com/Helium314/HeliBoard/blob/main/layouts.md">layouts</a> (only available when disabling <i>use system languages</i>)</li>
  <li>Customize special layouts, like symbols, number,  or functional key layout</li>
  <li>Multilingual typing</li>
  <li>Glide typing (<i>only with closed source library</i> ☹️)</li>
  <ul>
    <li>library not included in the app, as there is no compatible open source library available</li>
    <li>can be extracted from GApps packages ("<i>swypelibs</i>"), or downloaded <a href="https://github.com/erkserkserks/openboard/tree/46fdf2b550035ca69299ce312fa158e7ade36967/app/src/main/jniLibs">here</a> (click on the file and then "raw" or the tiny download button)</li>
  </ul>
  <li>Clipboard history</li>
  <li>One-handed mode</li>
  <li>Split keyboard (only available if the screen is large enough)</li>
  <li>Number pad</li>
  <li>Backup and restore your settings and learned word / history data</li>
</ul>

## FAQ / Common Issues
* __Add a dictionary__: First download the dictionary file, e.g. from [here](https://codeberg.org/Helium314/aosp-dictionaries#dictionaries). Then go to language settings, click on the language, then on `+` next to _dictionary_ the _add_ and select the file. Alternatively you can open a `.dict` file in a file explorer with HeliBoard and then select the language. Note that the latter method does not work with all file explorers.
* __Emoji search__: You can get addon dictionaries for emoji suggestions in the [dictionaries repo](https://codeberg.org/Helium314/aosp-dictionaries). An actual search function does not exist yet.
* __Cannot switch choose layout__: This is only possible when _use system languages_ is disabled. You can select the layout when tapping on the language.
  * __How to customize layout__: Go to layout selection and use the `+` button, then you can add a custom layout, either from a file or you can copy and edit an existing layout.
* __No suggestions for some language__: Check [dictionaries repo](https://codeberg.org/Helium314/aosp-dictionaries) whether a dictionary is available. If there is one, download it and add it in the language settings for this language.
* __No suggestions in some app / text field__: This app respects the [no suggestions flag](https://developer.android.com/reference/android/text/InputType#TYPE_TEXT_FLAG_NO_SUGGESTIONS) set by some input fields, i.e. the developer does not want you to see suggestions here. Best do in issue report for that app if you think this behavior is wrong. Alternatively you can enable the _always show suggestions_ setting that overrides the _no suggestions_ flag.
* __Multilingual typing__ (type in multiple languages without switching manually): Enable in _Languages & Layouts_, select the main language and tap the `+` button next to _multilingual typing_ to add a language. Note that the selection is limited to languages with the same script as the main language, and to languages that have a dictionary (see above for how to add).
* __How to enable glide typing__: There is no glide typing built into this app, but you can load compatible libraries: Go to advanced settings -> _load gesture typing library_ and point to a file (setting not available in _nouserlib_ version). You can extract the file from GApps packages ("_swypelibs_"), or download one [here](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs). Make sure to use the correct version (app will tell you in the dialog to load the library).
  * __Glide typing is not working after loading a library__: Possibly the download was corrupted, or you downloaded the wrong file. If you get a "_unknown file_" confirmation popup, it is likely you are not using the correct file (or you might be using a different version of the library). In rare cases, there might be crashes when the file is not in internal storage, or some [Samsung-specific problems](https://stackoverflow.com/a/75286899). 
* __German layout with / without umlauts__: _German (Germany)_ layout has umlauts, _German_ layout doesn't
* __Spell checker is not checking all languages in multilingual typing__: Make sure you actually enabled HeliBoard spell checker. Usually it can be found in System Settings -> System -> Languages -> Advanced -> Spell Checker, but this may depend on Android version.
* __Words added to Gboard dictionary are not suggested__: Gboard uses its own dictionary instead of the system's personal dictionary. See [here](https://github.com/Helium314/HeliBoard/issues/500#issuecomment-2032292161) for how to export the words.
* __What is the _nouserlib_ version?__: The normal version (_release_) allows the user to provide a library for glide typing, while the _nouserlib_ version does not. Running code that isn't supplied with the app is _dynamic code loading_, which is a security risk. Android Studio warns about this:
  > Dynamically loading code from locations other than the application's library directory or the Android platform's built-in library directories is dangerous, as there is an increased risk that the code could have been tampered with. Applications should use loadLibrary when possible, which provides increased assurance that libraries are loaded from one of these safer locations. Application developers should use the features of their development environment to place application native libraries into the lib directory of their compiled APKs.

  The app checks the SHA256 checksum of the library and warns the user if it doesn't match with known library versions. A mismatch indicates the library was modified, but may also occur if the user intentionally provides a different library than expected (e.g. a self-built variant).
  Note that if the the app is installed as a system app, both versions have access to the system glide typing library (if it is installed).
* __App crashing when using as system app__: This happens if you do not install the app, but just copy the APK. Then the app's own library is not extracted from the APK, and not accessible to the app. You will need tp either install the app over itself, or provide a library.

## Hidden Functionality
Features that may go unnoticed, and further potentially useful information
* Long-pressing toolbar keys results in additional functionality: clipboard -> paste, move left/right -> word left/right, move up/down -> page up/down, word left/right -> line start/end, page up/down -> page start/end, copy -> copy all, select word -> select all, undo <-> redo
* Long-press the Comma-key to access Clipboard View, Emoji View, One-handed Mode, Settings, or Switch Language:
  * Emoji View and Language Switch will disappear if you have the corresponding key enabled;
  * For some layouts it\'s not the Comma-key, but the key at the same position (e.g. it\'s `q` for Dvorak layout).
* When incognito mode is enabled, no words will be learned, and no emojis will be added to recents.
* Sliding key input: Swipe from shift or symbol key to another key. This will enter a single uppercase key or symbol and return to the previous keyboard.
* Hold shift or symbol key, press one or more keys, and then release shift or symbol key to return to the previous keyboard.
* Long-press a suggestion in the suggestion strip to show more suggestions, and a delete button to remove this suggestion.
* Swipe up from a suggestion to open more suggestions, and release on the suggestion to select it.
* Long-press an entry in the clipboard history to pin it (keep it in clipboard until you unpin).
* Swipe left in clipboard view to remove an entry (except when it's pinned)
* Select text and press shift to switch between uppercase, lowercase and capitalize words
* You can add dictionaries by opening the file
  * This only works with _content-uris_ and not with _file-uris_, meaning that it may not work with some file explorers.
* Not really a feature, but you can restart the keyboard by going to the settings and swiping it away from recents
* _Debug mode / debug APK_
  * Long-press a suggestion in the suggestion strip twice to show the source dictionary.
  * When using debug APK, you can find _Debug Settings_ within the _Advanced Preferences_, though the usefulness is limited except for dumping dictionaries into the log.
    * For a release APK, you need to tap the version in _About_ several times, then you can find debug settings in _Advanced Preferences_.
    * When enabling _Show suggestion infos_, suggestions will have some tiny numbers on top showing some internal score and source dictionary.
  * In the event of an application crash, you will be prompted whether you want the crash logs when you open the Settings.
  * When using multilingual typing, space bar will show an confidence value used for determining the currently used language.
* For users doing manual backups with root access: Starting at Android 7, some files and the main shared preferences file are not in the default location, because the app is using [device protected storage](https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()). This is necessary so the settings and layout files can be read before the device is unlocked, e.g. at boot. The files are usually located in `/data/user_de/0/<package_id>/`, though the location may depend on the device and Android version.

# Contributing ❤

## Reporting Issues

Whether you encountered a bug, or want to see a new feature in HeliBoard, you can contribute to the project by opening a new issue [here](https://github.com/Helium314/HeliBoard/issues). Your help is always welcome!

Before opening a new issue, be sure to check the following:
 - **Does the issue already exist?** Make sure a similar issue has not been reported by browsing [existing issues](https://github.com/Helium314/HeliBoard/issues?q=). Please search open and closed issues.
 - **Is the issue still relevant?** Make sure your issue is not already fixed in the latest version of HeliBoard.
 - **Is it a single topic?** If you want to suggest multiple things, open multiple issues.
 - **Did you use the issue template?** It is important to make life of our kind contributors easier by avoiding issues that miss key information to their resolution.
Note that issues that that ignore part of the issue template will likely get treated with very low priority, as often they are needlessly hard to read or understand (e.g. huge screenshots, not providing a proper description, or addressing multiple topics).

If you're interested, you can read the following useful text about effective bug reporting (a bit longer read): https://www.chiark.greenend.org.uk/~sgtatham/bugs.html

## Translations
Translations can be added using [Weblate](https://translate.codeberg.org/projects/heliboard/). You will need an account to update translations and add languages. Add the language you want to translate to in Languages -> Manage translated languages in the top menu bar.
Updating translations in a PR will not be accepted, as it may cause conflicts with Weblate translations.

## Dictionary Creation
There will not be any further dictionaries bundled in this app. However, you can add dictionaries to the [dictionaries repository](https://codeberg.org/Helium314/aosp-dictionaries).
To create or update a dictionary for your language, you can use [this tool](https://github.com/remi0s/aosp-dictionary-tools). You will need a wordlist, as described [here](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/wordlists/sample.combined) and in the repository readme.

## Code Contribution
See [Contribution Guidelines](CONTRIBUTING.md)

# To-do
__Planned features and improvements:__
* Improve support for modifier keys (_alt_, _ctrl_, _meta_ and _fn_), some ideas:
  * keep modifier keys on with long press
  * keep modifier keys on until the next key press
  * use sliding input
* Less complicated addition of new keyboard languages (e.g. #519)
* Additional and customizable key swipe functionality
  * Some functionality will not be possible when using glide typing
* Ability to enter all emojis independent of Android version (optional, #297)
* Add and enable emoji dictionaries by default (if available for language)
* Clearer / more intuitive arrangement of settings
  * Maybe hide some less used settings by default (similar to color customization)
* Customizable currency keys
* Ability to export/import (share) custom colors
* Make use of the `.com` key in URL fields (currently only available for tablets)
  * With language-dependent TLDs
* Internal cleanup (a lot of over-complicated and convoluted code)
* [Bug fixes](https://github.com/Helium314/HeliBoard/issues?q=is%3Aissue+is%3Aopen+label%3Abug)

__What will _not_ be added:__
* Material 3 (not worth adding 1.5 MB to app size)
* Dictionaries for more languages (you can still download them)
* Anything that requires additional permissions, unless there is a very good reason

# License

HeliBoard (as a fork of OpenBoard) is licensed under GNU General Public License v3.0.

 > Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE-GPL-3) file.

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided.

The icon is licensed under [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). A [license file](LICENSE-CC-BY-SA-4.0) is also included.

# Credits
- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
- Our [contributors](https://github.com/Helium314/HeliBoard/graphs/contributors)
