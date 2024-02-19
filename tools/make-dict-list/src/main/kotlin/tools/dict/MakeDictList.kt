package tools.dict

import java.io.File
import java.net.URL

class MakeDictList {

    companion object {

        @JvmStatic fun main(args: Array<String>) {
            val readmeUrl = "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/README.md"
            val readmeText = URL(readmeUrl).readText()
            val fileText = doIt(readmeText)
            val targetDir = args[0]

            File(targetDir).mkdirs()
            File("$targetDir/dictionaries_in_dict_repo.csv").writeText(fileText)
        }

    }
}

/**
 *  extract dictionary list from README.md
 *  output format: <localeString>,<type>,<experimental>
 *   <experimental> is empty if dictionary is not experimental, no other check done
 *  requires README.md to have dicts in correct "# Dictionaries" or "# Experimental dictionaries" sections
 */
private fun doIt(readme: String): String {
    // output format: <localeString>,<type>,<experimental>
    // experimental is empty if dictionary is not experimental, no other check done
    var mode = MODE_NOTHING
    val outLines = mutableListOf<String>()
    readme.split("\n").forEach { line ->
        if (line.startsWith("#")) {
            mode = if (line.trim() == "# Dictionaries")
                MODE_NORMAL
            else if (line.trim() == "# Experimental dictionaries")
                MODE_EXPERIMENTAL
            else
                MODE_NOTHING
            return@forEach
        }
        if (mode == MODE_NOTHING || !line.startsWith("*")) return@forEach
        val dictName = line.substringAfter("]").substringAfter("(").substringBefore(")")
            .substringAfterLast("/").substringBefore(".dict")
        val type = dictName.substringBefore("_")
        val rawLocale = dictName.substringAfter("_")
        val locale = if ("_" !in rawLocale) rawLocale
        else {
            val split = rawLocale.split("_").toMutableList()
            if (!split[1].startsWith("#"))
                split[1] = split[1].uppercase()
            split.joinToString("_")
        }
        outLines.add("$type,$locale,${if (mode == MODE_EXPERIMENTAL) "exp" else ""}")
    }
    return outLines.joinToString("\n") + "\n"
}

private const val MODE_NOTHING = 0
private const val MODE_NORMAL = 1
private const val MODE_EXPERIMENTAL = 2
