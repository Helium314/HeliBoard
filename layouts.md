# Layouts

(WIP) information about the layout format

## simple
One key per line, two consecutive newlines mark a row end.
Key format: [label] [moreKeys], all separated by space, e.g. `a 0 + *` will create a key with text a, and the keys `0`, `+`, and `*` on long press. Some characters currently require escape using `\` (todo: add the list, or better add them in code instead of requiring it in the layouts).
Special symbols: `%` (only for language-dependent moreKeys, not user defined, also better use sth like `%%%`) acts as placeholder for normal moreKeys. `$$$` will be replaced by currency (or default to `$`).
Language-dependent moreKeys should never contain "special" moreKeys, i.e. those starting with `!` (exception for `punctuation`)

## json
Character layouts from FlorisBoard, but missing code or label will be determined automatically. And not everything supported...
