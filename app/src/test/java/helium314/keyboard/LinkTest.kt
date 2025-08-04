package helium314.keyboard

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.getKnownDictionariesForLocale
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LinkTest {
    @Test fun knownDictionaries() {
        if (BuildConfig.BUILD_TYPE == "runTests") return // don't spam requests to Codeberg on every PR update
        val context = ApplicationProvider.getApplicationContext<App>()
        val urls = mutableSetOf<String>()
        context.assets.open("dictionaries_in_dict_repo.csv").reader().readLines().forEach { line ->
            getKnownDictionariesForLocale(line.split(",")[1].constructLocale(), context).forEach {
                urls.add(it.second)
            }
        }
        // can't check everything at once, this will trigger some rate limit
        val typeToCheck = listOf("/dictionaries_experimental/", "/emoji_cldr_signal_dictionaries/", "/dictionaries/").random()
        urls.forEach {
            if (it.contains(typeToCheck))
            checkLink(it)
        }
    }

    @Test fun otherLinks() {
        listOf(Links.LICENSE, Links.LAYOUT_WIKI_URL, Links.WIKI_URL, Links.CUSTOM_LAYOUTS, Links.CUSTOM_COLORS).forEach {
            checkLink(it)
        }
    }

    private fun checkLink(link: String) {
        println("checking $link")
        val url = URL(link)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        assertEquals(200, connection.responseCode)
    }
}
