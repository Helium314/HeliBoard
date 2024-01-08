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

## Simple format
* One key per line
  * Key format: [label] [popup keys], all separated by space, e.g. `a 0 + *` will create a key with text `a`, and the keys `0`, `+`, and `*` on long press
* Two consecutive newlines mark beginning of a new row

## Json format
* You can use character layouts from FlorisBoard
* There is no need for specifying a code, it will be determined from the label automatically
  * Specify it if you want key label and code to be different
* You can add a _labelFlag_ to a key for some specific effects, see [here](app/src/main/res/values/attrs.xml) in the section _keyLabelFlags_ for names and numeric values
