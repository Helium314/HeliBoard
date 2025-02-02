// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isGone
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.flow.MutableStateFlow

// todo (roughly in order)
//  make all prefs actually work
//   appearance
//    holo white selectable for non-holo style if we had selected holo theme before
//     the list just stays the old one after changing the setting
//     also, even if selected it should not be used...
//    click on bg image does nothing when already set (but works after reload)
//    split spacer scale setting does not reload?
//    narrow key gaps setting is not changing properly?
//    custom font loading not implemented
//    have large bg image, and first-time load the keyboard on new search field -> bg image expands full size
//   advanced
//   preferences
//  try moving the recomposition of pref change somewhere else, so it's not duplicated everywhere
//  make the pref lists more compact (compare with old settings)
//  try making text size similar to old state (also in dialogs)
//  check whether dialogs have the same colors, i think currently it's a bit inconsistent
//   see all the properties for each alertDialog -> any way to set it in a single place?
//  title too huge for bg image and text on spacebar dialogs, also maybe somewhere else -> where to set in one place?
//  check dark and light theme (don't have dynamic)
//  rename both settingsActivities
//  work on todos in other files
//  use better / more structured and clear names and arrangement of files
//   the prefDef and AllPrefs, also be clear about pref <-> key <-> prefKey (all used, probably should be latter)
//   there is a lot more ambiguous naming...
//  animations when stuff (dis)appears
//   LaunchedEffect, AnimatedVisibility
//  performance
//   find a nice way of testing (probably add logs for measuring time and recompositions)
//   consider that stuff in composables can get called quite often on any changes
//    -> use remember for things that are slow, but be careful about things that can change (e.g. values derived from prefs)
//   improve performance when loading screens with many settings (lazyColumn?)
//    first check whether it's really necessary (test advanced or correction screen normal and with lazyColumn)
//    screens could have a lazy column of preferences and category separators, and the list has an if-setting-then-null for hiding
//    lazyColumn also has a "key", this should be used and be the pref name (or maybe title because that's also for category separators)
//  nice arrows (in top bar, and as next-screen indicator)
//  PRs adding prefs -> need to do before continuing
//   1319 (soon)
//   1263 (no response for 3 weeks)
//  merge main to implement all the new settings
//  consider IME insets when searching
//  dialogs should be rememberSaveable to survive display orientation change and stuff?
//  try making old fragment back stuff work better, and try the different themes (with and without top bar)
//  any way to get rid of the "old" background on starting settings? probably comes from app theme, can we avoid it?
//  consider using simple list picker dialog (but the "full" one is probably better for language settings stuff)
//  spdx headers everywhere

// what should be done, but not in this PR
//  in general: changes to anything outside the new settings (unless necessary), and changes to how screens / fragments work
//  re-organize screens, no need to keep exactly the same arrangement
//  language settings (should change more than just move to compose)
//  user dictionary settings (or maybe leave old state for a while?)
//  color settings (should at least change how colors are stored, and have a color search/filter)
//  allow users to add custom themes instead of only having a single one (maybe also switchable in colors settings)
//  one single place for default values (to be used in composables and settings)
//  make auto_correct_threshold a float directly with the list pref (needs pref upgrade)
//  using context.prefs() outside settings
//  merge PREF_TOOLBAR_CUSTOM_KEY_CODES and PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES into one pref (don't forget settings upgrade)
//  adjust debug settings
//   have them in main screen?
//   allow users to find the individual settings in search even if debug settings are not enabled?

//  consider disabled settings & search
//   don't show -> users confused
//   show as disabled -> users confused
//   show (but change will not do anything because another setting needs to be enabled first)
//   -> last is probably best, but people will probably open issues no matter what

// maybe do after the PR
//  bottom dummy text field (though we have the search now anyway, and thus maybe don't need it)
//  search only in current pref screen, except when in main?
//  try getting rid of appcompat stuff (activity, dialogs, ...)
//  rearrange settings screens? now it should be very simple to do (definitely separate PR)

// preliminary results:
// looks ok (ugly M3 switches)
// performance
//  time until app and screens are shown is clearly worse than previously (2-4x)
//  gets much better when opening same screen again
//  material3 is ~25% faster than material2
//  debug is MUCH slower than release
//   much of this is before the app actually starts (before App.onCreate), maybe loading the many compose classes slows down startup
//  -> should be fine on reasonably recent phones (imo even still acceptable on S4 mini)
// apk size increase
//  ca 900 kb with base + material2
//  another 300 kb with navHost (and activity-compose, but not needed)
//  another 300 kb when switching material2 to material3
//  ca 150 kb reduction when removing androidx.preference
//  -> too much, but still ok if we can get nicer preference stuff
//  meh, and using a TextField adds another 300 kb... huge chunks for sth that seems so small

class SettingsActivity2 : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getInstance().current == null)
            Settings.init(this)

        allPrefs = AllPrefs(this)

        // todo: when removing old settings completely, remove settings_activity.xml and supportFragmentManager stuff
//        val cv = ComposeView(context = this)
//        setContentView(cv)
        setContentView(R.layout.settings_activity)
        supportFragmentManager.addOnBackStackChangedListener {
            updateContainerVisibility()
        }
//        cv.setContent { // todo: when removing old settings
        findViewById<ComposeView>(R.id.navHost).setContent {
            Theme {
                Surface {
                    SettingsNavHost(
                        onClickBack = {
//                            this.finish() // todo: when removing old settings
                            if (supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null)
                                this.finish()
                            else supportFragmentManager.popBackStack()
                        }
                    )
                }
            }
        }
    }

    private fun updateContainerVisibility() { // todo: remove when removing old settings
        findViewById<RelativeLayout>(R.id.settingsFragmentContainer).isGone = supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    companion object {
        // public write so compose previews can show the pref screens
        lateinit var allPrefs: AllPrefs
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged.value++
    }
}

@JvmField
var keyboardNeedsReload = false
