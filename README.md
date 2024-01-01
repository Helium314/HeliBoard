# OpenBoard upgrade, WIP

This is an attempt to integrate changes / improvements into OpenBoard that have been sitting around for a long time due to low dev activity.
Might end up on F-Droid...

**This is mostly a development version. On updates there may be changes that reset some settings. Consider all releases as beta quality at best and always have another keyboard installed in case of really bad bugs.**

## Features
* Allow loading Glide typing library
  * not included in the app, as there is no compatible open source library
  * can be extracted from GApps packages (_swypelibs_), or downloaded [here](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs)
* Multilingual typing
* Load external dictionaries
  * get them [here]( https://codeberg.org/Helium314/aosp-dictionaries#dictionaries), or in the [experimental](https://codeberg.org/Helium314/aosp-dictionaries#experimental-dictionaries) section (quality may vary)
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
* Add custom keyboard layouts
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

## Important differences and changes to OpenBoard
* Debug version can be installed along OpenBoard
* Allow users to add and replace built-in dictionaries
  * modified / improved from https://github.com/openboard-team/openboard/pull/569 and https://github.com/openboard-team/openboard/pull/578
  * some AOSP dictionaries are available [here](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/dictionaries)
    * experimental dictionaries with next-word suggestions created from sentence lists [are also available](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/dictionaries_experimental), but they may contain unwanted words, and may be missing other features
  * dictionary files starting with "main_" replace the built-in dictionary for the language, all other names work as add-on dictionaries
  * emoji dictionaries can be used to get emoji suggestions
* Fixes / improvements regarding suggestions
  * Remove suggestions by long pressing on suggestion strip while the more suggestions popup is open (suggestions get re-added if they are entered again)
  * Allow using contacts for suggestions
  * several small adjustments and fixes
* Reduce amount of unwanted automatic space insertions, https://github.com/openboard-team/openboard/pull/576
* Add multi-lingual typing, slightly modified from https://github.com/openboard-team/openboard/pull/586, https://github.com/openboard-team/openboard/pull/593
* Allow loading an external library to enable gesture typing, https://github.com/openboard-team/openboard/issues/3
  * based on [wordmage's work](https://github.com/openboard-team/openboard/tree/57d33791d7674e3fe0600eddb72f6b4317b5df00)
  * tested with [Google libraries](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs) and [others](https://github.com/openboard-team/openboard/issues/3#issuecomment-1200456262) (when building with the [rename](https://github.com/openboard-team/openboard/tree/57d33791d7674e3fe0600eddb72f6b4317b5df00))
* Theming: allow adjusting keyboard colors, https://github.com/openboard-team/openboard/issues/124
  * Optionally make the navigation bar follow current theme, https://github.com/Helium314/openboard/issues/4
  * Allow defining day/night themes
* Remove suggestions by long pressing on suggestion strip while the more suggestions popup is open, https://github.com/openboard-team/openboard/issues/106
  * suggestions get re-added if they are entered again
* Optionally add typed words to system personal dictionary
* Improve issues with emoji deletion (still happens with delete gesture), https://github.com/Helium314/openboard/issues/22
* Add Unicode 15 emojis, https://github.com/Helium314/openboard/pull/25
* Better currency selection, https://github.com/Helium314/openboard/pull/21 / https://github.com/Helium314/openboard/commit/0d1106649f95ecbd7d8f6d950428547666059564
* Reduce space between keys, with option to use old values, https://github.com/Helium314/openboard/pull/8
* Fix number row not split in split keyboard view, https://github.com/Helium314/openboard/pull/27
* Fix issue with spell checker incorrectly flagging words before a period as wrong on newer Android versions, https://github.com/openboard-team/openboard/pull/679
  * maybe not properly fixed, this causes some other issues, https://github.com/Helium314/openboard/issues/55
* Fix always-dark settings on some Android versions, https://github.com/Helium314/openboard/pull/69
* Fix bug with space before word being deleted in some apps / input fields, https://github.com/Helium314/openboard/commit/ce0bf06545c4547d3fc5791cc769508db0a89e87
* Allow using auto theme on some devices with Android 9
* Add number pad
* Overhauled language settings
* Updated translations
* Open dictionary files with the app
* Add more options to the language switch key
* New keyboard parser (no need to use `tools:make-keyboard-text:makeText` any more)
  * Can use simple text files or JSON files as used by [FlorisBoard](https://github.com/florisboard/florisboard/tree/master/app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts)

## The rough plan/todo before "full" release
* Finish keyboard parsing upgrades
  * Users should be allowed to add their own layouts
  * Determine symbol popup keys from symbols layout instead of hardcoding them to the layout (still allow layout to override or add popup keys)
  * Allow users more control over popup keys (which hint label to show, which popup keys (language, layout, symbols) to show first or at all)
* Overhaul the view system
  * Have a fixed height common to all views (keyboard, emoji, clipboard)
  * Should allow for more flexible one-handed mode (though the actual one-handed mode changes may be implemented later)
  * Should allow for background images that don't resize or get cut off when switching between views
* Internal clean up (xml files, unused resources, some todos in code)
* Solve some [issues](https://github.com/Helium314/openboard/milestone/1) requiring a lot of work

Once above is done, we can think about properly releasing the app:
* Work on issues with the [when ready](https://github.com/Helium314/openboard/labels/when%20ready) label
* Rename app, package and this repository
* New icon
* Use a translation tool (probably weblate)
* Release on F-Droid
* Maybe add a version that does not allow providing a glide typing library, for people concerned about security

## Further ideas
* More customizable theming
* Improved / less bad suggestions in some specific situations
* Sliding key input for toolbar, numpad and emojis (like `?123` and _shift_ sliding input)
* More tunable behavior, e.g for delete and spacebar swipe, for toolbar, for spell checker,...
* Adjust arrangement of settings, maybe hide settings irrelevant for most users behind some "more settings mode"
* Migrate to internally use language tags (problematic due to lack of support on older Android versions)
* More customizable toolbar
* Support providing background images (for keyboard, and possibly also for keys)
* and general [bug](https://github.com/Helium314/openboard/issues?q=is%3Aopen+is%3Aissue+label%3Abug) fixing

-----
# readme for original version of OpenBoard below
-----
<h1 align="center"><b>OpenBoard</b></h1>
<h4 align="center">100% FOSS keyboard, based on AOSP.</h4>
<p align="center"><img src='fastlane/metadata/android/en-US/images/icon.png' height='128'></p>
<p align="center">
<a href="https://github.com/openboard-team/openboard/actions/workflows/android-build.yml"><img src="https://img.shields.io/github/workflow/status/openboard-team/openboard/Build" alt="GitHub Workflow Status"></a>
<a href="https://hosted.weblate.org/engage/openboard/"><img src="https://hosted.weblate.org/widgets/openboard/-/openboard/svg-badge.svg" alt="Translation status"></a>
<a href="https://matrix.to/#/#openboard:matrix.org?via=matrix.org"><img src="https://img.shields.io/matrix/openboard:matrix.org" alt="Matrix"></a></p>
<p align="center">
<a href="https://github.com/openboard-team/openboard/releases"><img src="https://img.shields.io/github/v/release/openboard-team/openboard" alt="GitHub release (latest by date)"></a>
<a href="https://f-droid.org/packages/org.dslul.openboard.inputmethod.latin"><img alt="F-Droid Version" src="https://img.shields.io/f-droid/v/org.dslul.openboard.inputmethod.latin?color=green&amp;logo=f-droid"></a>
<a href="https://play.google.com/store/apps/details?id=org.dslul.openboard.inputmethod.latin"><img alt="Google Play Version" src="https://img.shields.io/endpoint?logo=google-play&amp;url=https%3A%2F%2Fplayshields.herokuapp.com%2Fplay%3Fi%3Dorg.dslul.openboard.inputmethod.latin%26l%3Dgoogle-play%26m%3D%24version"></a>
<a href="https://github.com/openboard-team/openboard/releases"><img src="https://img.shields.io/github/release-date/openboard-team/openboard" alt="GitHub Release Date"></a>
<a href="https://github.com/openboard-team/openboard/commits/master"><img src="https://img.shields.io/github/commits-since/openboard-team/openboard/latest" alt="GitHub commits since latest release (by date)"></a></p>
<p align="center">
<a href='https://f-droid.org/packages/org.dslul.openboard.inputmethod.latin'><img src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' alt='Get it on F-Droid' height='60'></a>
<a href='https://play.google.com/store/apps/details?id=org.dslul.openboard.inputmethod.latin&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height='60'/></a></p>  

# Table of content

- [Community](#community)
- [Contribution ❤](#contribution-)
   * [Issue reporting](#issue-reporting)
   * [Translation](#translation)
   * [Dictionary creation](#dictionary-creation)
   * [Code contribution](#code-contribution)
      + [Getting started](#getting-started)
      + [Guidelines](#guidelines)
      + [Current TODO list](#current-todo-list)
      + [Project's side tools](#tooling)
- [License](#license)
- [Credits](#credits)

# Community
Join our [matrix] channel [here](https://matrix.to/#/#openboard:matrix.org?via=matrix.org).

<img src="images/matrix_qr.png" alt="Matrix QR Code" height="128">

# Contribution ❤

## Issue reporting

Whether you encountered a bug, or want to see a new feature in OpenBoard, you can contribute to the project by opening a new issue [here](https://github.com/openboard-team/openboard/issues). Your help is always welcomed !

Before opening a new issue, be sure to check the following :
 - **Does the issue already exist ?** Make sure a similar issue has not been reported by browsing [existing issues](https://github.com/openboard-team/openboard/issues).
 - **Is the issue still relevant ?** Make sure your issue is not already fixed in the latest version of OpenBoard.
 - **Did you use the issue template ?** It is important to make life of our kind contributors easier by avoiding  issues that miss key informations to their resolution.

*Please avoid opening issues to ask for a release date, for PR reviews/merges, for more activity on the project, or worth for more contributors. If you have any interrogations on these topics, read [this comment](https://github.com/openboard-team/openboard/issues/619#issuecomment-1179534276) from issue [#619](https://github.com/openboard-team/openboard/issues/619).*

## Translation
You can help in translating OpenBoard in your language through our [Weblate project](https://hosted.weblate.org/engage/openboard/).

[![Translation status](https://hosted.weblate.org/widgets/openboard/-/openboard/287x66-grey.png)](https://hosted.weblate.org/engage/openboard/)

## Dictionary creation
To create or update a dictionary for your language, you can use [this tool](https://github.com/remi0s/aosp-dictionary-tools). You will need a wordlist, as described [here](dictionaries/sample.combined). The output .dict file must be put in [res/raw](app/src/main/res/raw), and its wordlist in [dictionaries](/dictionaries).

For your dictionary to be merged into OpenBoard, **you must provide the wordlist you used**, as well as its license if any.

## Code contribution

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

### Current TODO list
In no particular order, here is the non-exhaustive list of known wanted features :
- [x] ~~Updated emoji support~~
- [ ] MaterialYou ([M3](https://m3.material.io/)) support
- [x] ~~One-handed mode feature~~
- [ ] Android [autofill](https://developer.android.com/guide/topics/text/ime-autofill) support
- [x] ~~Clipboard history feature~~
- [ ] Text navigation/selection panel
- [ ] Multi-locale typing
- [ ] Emoji search
- [ ] Emoji variant saving
- [ ] Glide typing

### Tooling

#### Edit keyboards content
Keyboards content is often a complex concatenation of data from global to specific locales. For example, additional keys of a given key, also known as 'more keys' in code, are determined by concatenating infos from : common additional keys for a layout (eg. numbers), global locale (eg. common symbols) and specific locale (eg. accents or specific letters).

To edit these infos, you'll need to generate the [KeyboardTextsTable.java](app/src/main/java/org/dslul/openboard/inputmethod/keyboard/internal/KeyboardTextsTable.java) file. 
To do so :
1. Make your modifications in [tools/make-keyboard-text/src/main/resources](tools/make-keyboard-text/src/main/resources)/values-YOUR LOCALE.
2. Generate the new version of [KeyboardTextsTable.java](app/src/main/java/org/dslul/openboard/inputmethod/keyboard/internal/KeyboardTextsTable.java) by running Gradle task 'makeText' :
    ```sh
    ./gradlew tools:make-keyboard-text:makeText
    ```
   
#### Update emojis

See make-emoji-keys tool [README](tools/make-emoji-keys/README.md).

# License

OpenBoard project is licensed under GNU General Public License v3.0.

 > Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE) file.

# Credits
- Icon by [Marco TLS](https://www.marcotls.eu)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- Our [contributors](https://github.com/openboard-team/openboard/graphs/contributors)
