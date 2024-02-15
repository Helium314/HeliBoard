A compilation of information about the layout formats usable in this app.

There are two distinct formats:
* the _simple_ format is a text file with one key per line, and two consecutive line breaks indicating a switch to the next row, [example](app/src/main/assets/layouts/qwerty.txt)
* the _json_ format taken from [FlorisBoard](https://github.com/florisboard/florisboard/blob/master/CONTRIBUTING.md#adding-the-layout), but only "normal" keys are supported (i.e. no action keys and similar), [example](app/src/main/assets/layouts/azerty.json)

## General notes
Adding too many keys or too long texts will make the keyboard look awkward or broken, and even crash the app under some specific conditions.
There are some sanity checks when adding a layout to avoid such issues, but they do not cover all possible cases.
Further there is no check whether the layout actually contains characters of the selected language.

If you use an external glide typing library, you likely will have issues if your layout contains duplicate keys, or keys with text longer than a single letter.

There are special key labels that are intended for internal use only, but can (currently) be set on custom layouts too. An example is `!icon/previous_key|!code/key_action_previous`, so it's unlikely you will stumble upon issues here when not intentionally provoking it.
One special label that might be wanted though is `$$$`, which will be replaced by the local currency. `$$$1` - `$$$4` will be replaced by currencies available on long-pressing the currency key.
If you want different key label and use text, set the label to [label]|[text], e.g. `aa|bb` will show `aa`, but pressing the key will input `bb`.

## Simple format
* One key per line
  * Key format: [label] [popup keys], all separated by space, e.g. `a 0 + *` will create a key with text `a`, and the keys `0`, `+`, and `*` on long press
* Two consecutive newlines mark beginning of a new row

## Json format
* Allows more flexibility than the simple format, e.g. changing keys depending on input type, shift state or layout direction
* You can use character layouts from FlorisBoard
* There is no need for specifying a code, it will be determined from the label automatically
  * Specify it if you want key label and code to be different
* You can add a _labelFlag_ to a key for some specific effects, see [here](app/src/main/res/values/attrs.xml) in the section _keyLabelFlags_ for names and numeric values
* More details on the formal will be provided. For now you can check other layouts, often you just need to copy lines and change the labels.

## Adding new layouts / languages
* You need a layout file in one of the formats above, and add it to [layouts](app/src/main/assets/layouts)
  * Popup keys in the layout will be in the "Layout" popup key group.
* Add a layout entry to [method.xml](app/src/main/res/xml/method.xml)
  * `KeyboardLayoutSet` in `android:imeSubtypeExtraValue` must be set to the name of your layout file (without file ending)
  * `android:subtypeId` must be set to a value that is unique in this file (please use the same length as for other layouts)
  * If you add a layout to an existing language, add a string with the layout name to use instead of `subtype_generic`. `%s` will be replaced with the language
* If you add a new language, you might want to provide a [language_key_texts](/app/src/main/assets/language_key_texts) file
  * `[popup_keys]` section contains popup keys that are similar to the letter (like `a` and `ä` or `य` and `य़`)
    * Such forms should _not_ be in the layout. They will apply to all layouts of that language, even custom ones.
    * The popup keys will be added to the "_Language_" popup key group (relevant for setting popup key order).
      * Use `%` to mark all preceding keys as "_Language (important)_" instead. Keys after `%` will still be in the "_Language_" group.
    * The `punctuation` key is typically the period key. `popup_keys` set here override the default.
  * `[labels]` may contain non-default labels for the following keys `symbol`, `alphabet`, `shift_symbol`, `shift_symbol_tablet`, `comma`, `period`, `question`
  * `[number_row]` may contain a custom number row (1-9 and 0 separated by space). You should also add the language to `numberRowLocales` in [`PreferencesSettingsFragment`](app/src/main/java/helium314/keyboard/latin/settings/PreferencesSettingsFragment.java) so the user can opt into having a localized number row.
  * `[extra_keys]` are typically keys shown in the default layout of the language. This is currently only used for latin layouts to avoid duplicating layouts for just adding few keys on the right side. The layout name need to end with `+`, but the `+` is removed when looking up the actual layout.
