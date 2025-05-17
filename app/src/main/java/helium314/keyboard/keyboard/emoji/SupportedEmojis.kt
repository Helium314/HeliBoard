package helium314.keyboard.keyboard.emoji

import android.content.Context
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

object SupportedEmojis {
    private val unsupportedEmojis = hashSetOf<String>()

    fun load(context: Context) {
        val maxSdk = context.prefs().getInt(Settings.PREF_EMOJI_MAX_SDK, Defaults.PREF_EMOJI_MAX_SDK)
        Log.i("test", "max $maxSdk")
        unsupportedEmojis.clear()
        context.assets.open("emoji/minApi.txt").reader().readLines().forEach {
            val s = it.split(" ")
            val minApi = s.first().toInt()
            if (minApi > maxSdk)
                unsupportedEmojis.addAll(s.drop(1))
        }
        Log.i("test", "have ${unsupportedEmojis.size}, longest emoji: ${unsupportedEmojis.maxOfOrNull { it.length }}")
    }

    fun isSupported(emoji: String) = emoji !in unsupportedEmojis
}
