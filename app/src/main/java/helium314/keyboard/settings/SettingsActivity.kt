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

// what should be done, but not in this PR
//  merge PREF_TOOLBAR_CUSTOM_KEY_CODES and PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES into one pref (don't forget settings upgrade)
//  replace the setup wizard
//   initially: just move view to settingsFragmentContainer, don't use activity (only if little work)
//   then: put it in compose, in or outside the navHost?
//  platformActivityTheme background color should align with compose background (also consider dynamic colors)
//   probably no need for appcompat any more
//  replace color settings (should at least change how colors are stored, and have a color search/filter)
//  in general: changes to anything outside the new settings (unless necessary), and changes to how screens / fragments work
//  re-organize screens, no need to keep exactly the same arrangement
//  language settings (should change more than just move to compose)
//  user dictionary settings (or maybe leave old state for a while?)
//  allow users to add custom themes instead of only having a single one (maybe also switchable in colors settings)
//  one single place for default values (to be used in composables and settings)
//   does it make sense to put this into PrefDef?
//  make auto_correct_threshold a float directly with the list pref (needs pref upgrade)
//  use context.prefs() outside settings
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
//  actually lenient json parsing is not good in a certain way: we should show an error if a json property is unknown
//  syntax highlighting for json? should show basic json errors
//  does restore prefs not delete dictionaries?
//  don't require to switch keyboard when entering settings
//  calling KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext()) while keyboard is showing shows just full screen background
//   but reload while keyboard is showing would be great (isn't it at least semi-done when changing one-handed mode?)
//   copying onMeasure from ClipboardHistoryView to KeyboardWrapperView helps
//    but the keyboard is still empty/white

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
//  initially it was 900 kB, and another 300 kB for Material3
//  textField and others add more (not sure what exactly), and now we're already at 2 MB...

// todo: with compose, app startup is slower and UI needs some "warmup" time to be snappy
//  maybe baseline profiles help?
//  https://developer.android.com/codelabs/android-baseline-profiles-improve
//  https://developer.android.com/codelabs/jetpack-compose-performance#2
//  https://developer.android.com/topic/performance/baselineprofiles/overview
class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getInstance().current == null)
            Settings.init(this)

        settingsContainer = SettingsContainer(this)

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
        // public write so compose previews can show the screens
        lateinit var settingsContainer: SettingsContainer
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged.value++
    }
}

@JvmField
var keyboardNeedsReload = false
