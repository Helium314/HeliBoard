# OpenBoard upgrade, WIP

This is an attempt to integrate changes / improvements into OpenBoard that have been sitting around for a long time due to low dev activity.
Might end up on F-Droid...

**This is mostly a development version. On updates there may be changes that reset some settings. Consider all releases as beta quality at best.**

## Features
* Allow loading Glide typing library
  * not included in the app, as there is no compatible open source library
  * can be extracted from GApps packages (_swypelibs_), or downloaded [here](https://github.com/erkserkserks/openboard/tree/master/app/src/main/jniLibs)
* Multilingual typing
* Load external dictionaries
  * get them [here](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/dictionaries), or in the [experimental](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/dictionaries_experimental) section (quality may vary)
  * additional dictionaries for emojis or scientific symbols can be used to provide suggestions ("emoji search")
  * note that for Korean layouts, suggestions only work using [this dictionary](https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f), the tools in the dictionary repository are not able to create working dictionaries
* Adjust keyboard themes (style and colors)
  * can follow the system's day/night setting
* Split keyboard
* Number row
* Number pad
* Show all available extra characters on long pressing a key

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
* Add Unicode 15 emojis, https://github.com/openboard-team/openboard/issues/25
* Better currency selection, https://github.com/Helium314/openboard/pull/21 / https://github.com/Helium314/openboard/commit/0d1106649f95ecbd7d8f6d950428547666059564
* Reduce space between keys, with option to use old values, https://github.com/Helium314/openboard/pull/8
* Fix number row not split in split keyboard view, https://github.com/Helium314/openboard/pull/27
* Fix issue with spell checker incorrectly flagging words before a period as wrong on newer Android versions, https://github.com/openboard-team/openboard/pull/679
  * maybe not properly fixed, this causes some other issues
* Fix always-dark settings on some Android versions, https://github.com/Helium314/openboard/pull/69
* Fix bug with space before word being deleted in some apps / input fields, https://github.com/Helium314/openboard/commit/ce0bf06545c4547d3fc5791cc769508db0a89e87
* Allow using auto theme on some devices with Android 9
* Add number pad
* Overhauled language settings
* Updated translations
* Open dictionary files with the app
* Add more options to the language switch key

## The rough plan/todo before "full" release
* Add/change pre-defined themes
* Internal clean up (xml files, unused resources and classes)
  * even after a lot of work here, the current state look rather messy, with many useless and duplicate entries
* work through _todo_s in code
* Make suggestion removal functionality more discoverable
* Better detection when to separate words and when not (e.g. detection of email addresses and URLs)
* Fix some bugs
  * especially the spell checker issue https://github.com/Helium314/openboard/issues/55
  * "partial" multi-character codepoint deletion with delete gesture (e.g. for emojis), https://github.com/Helium314/openboard/issues/22

Once above is done, we can think about properly releasing the app. First just in this repository, and later on F-Droid.
This would include renaming the app and the package, changing the icon and using a localization tool (most likely weblate).

## Further plan
* more customizable theming
* improved / less bad suggestions in some cases
* add emojis to user history, to be used for next word suggestions
* sliding key input for numpad and emojis (like `?123` and _shift_ sliding input)
* updated suggestion strip, maybe add tools or make the suggestions scroll

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
