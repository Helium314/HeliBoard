package helium314.keyboard.latin.dictionary

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale

// todo
//  how to add to correct directory?
//   is it enough to provide the file?
//   use user suffix?
//  parse "everything"
//   bigram
//   shortcut
//  ui for adding, with feedback
//  ui for managing
//   should show up in dictionaries screen
//   should have a full header
//  backup/restore
class UserAddedDictionary(context: Context, locale: Locale, name: String) : ExpandableBinaryDictionary(
    context,
    name,
    locale,
    name,
    File(DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)!!, name + "_" + DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
) {
    var content: List<String>? = null
        private set
    var added = 0
    var failed = 0

    fun setContents(contents: List<String>) {
        Log.i(TAG, "setting new contents for ")
        content = contents
        setNeedsToRecreate()
        reloadDictionaryIfRequired()
    }

    // todo: after around 350k words this becomes slow, and fails to add more words a bit later
    //  split at 300k words? not nice for query performance for large dicts i guess
    //  just tell the user that it's not working?
    //  ideally we'd create an actual .dict file, here we also have some unknown but larger limit
    override fun loadInitialContentsLocked() {
        added = 0
        failed = 0
        content?.forEach { line ->
            if (!line.trim().startsWith("word")) return@forEach
            val split = line.split(",").map { it.trim() }
            val success = addUnigramLocked(
                split.first { it.startsWith("word=") }.substringAfter("word="),
                split.first { it.startsWith("f=") }.substringAfter("f=").toInt(),
                null,
                0,
                split.contains("not_a_word=true"),
                split.contains("possibly_offensive=true"),
                BinaryDictionary.NOT_A_VALID_TIMESTAMP
            )
            if (success) added++ else failed++
            runGCIfRequiredLocked(true)
        }
        content = null
        Log.i(TAG, "added $added entries, could not add $failed entries")
    }

    companion object {
        private val TAG = UserAddedDictionary::class.java.simpleName

        fun tryParseHeader(file: File): DictionaryHeader? {
            val lines = file.readLines()
            if (lines.size < 2) return null
            if (!lines[1].trim().startsWith("word=", true) || !lines[1].contains("f=", true)) {
                Log.e(TAG, "tryParseHeader: second line not a dictionary line: ${lines[1]}")
                return null // not the right format (should be extended though, why not just accept word lists, or word + frequency)
            }

            return if (lines[0].trim().startsWith("dictionary", true))
                runCatching { DictionaryHeader.fromString(lines[0]) }.getOrNull()
            else DictionaryHeader.createEmptyHeader()
        }
    }
}
