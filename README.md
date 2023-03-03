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
