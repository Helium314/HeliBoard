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
import kotlinx.coroutines.flow.MutableStateFlow

// todo
//  more pref screens
//   debug
//   appearance
//   colors
//   personal dictionary
//   languages (maybe separately)
//  consider IME insets when searching
//  improve performance when loading screens with many settings (lazyColumn?)
//   screens could have a lazy column of preferences and category separators, and the list has an if-setting-then-null for hiding
//   lazyColumn also has the key, this should be used! and must be unique
//  consider that stuff in composables can get called quite often on any changes -> use remember for things that are slow (maybe add test logging)
//  dialogs should be rememberSaveable to survive display orientation change and stuff?
//  default buttons for toolbar key(s) customizer and toolbar reorder dialog

// later
//  one single place for default values (in composables and settings)
//  nice arrows (in top bar, and as next-screen indicator)
//  animations when stuff (dis)appears
//   LaunchedEffect, AnimatedVisibility
//  remove PrefScreen if not used
//  rename some classes
//  split the preferences in allPrefs.createDefs into multiple files, this will get horribly long
//   maybe have sub-lists in the pref screens using the settings?
//  spdx headers everywhere (except DragDropColumn, which is from stackoverflow without explicit license)
//  changes to anything but the compose settings package should not be in the initial PR
//   commit them separately if possible
//   though some might be necessary
//  toolbar key enabled state can be wrong
//   go to correction settings, open search, toggle autocorrect toolbar key, and then toggle setting
//   -> now toolbar key always has the wrong state
//  color settings needs a color search
//  more convenient access to prefs
//  merge PREF_TOOLBAR_CUSTOM_KEY_CODES and PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES
//   should be single pref containing both
//   needs settings upgrade of course...
//  consider disabled settings & search
//   don't show -> users confused
//   show as disabled -> users confused
//   show (but change will not do anything because another setting needs to be enabled first)
//    -> users confused, but probably better than the 2 above
//  adjust layout a little, there is too much empty space and titles are too large (dialogs!)
//  check dialogs have the same colors
//  list preference -> we can make auto_correct_threshold a float directly

// maybe later
//  weird problem with app sometimes closing on back, but that's related to "old" settings (don't care if all are removed)
//  bottom text field (though we have the search now anyway)
//  remove navHost? but probably too useful to have...
//  lazyColumn for prefs (or just in category?)
//   should improve loading time for screens with many settings
//   but needs a bit of work for probably not so much benefit
//  adjust the debug settings thing, so that users can always find them in search but nowhere else? unless debug mode
//  search only in current pref screen, except when in main?
//  try getting rid of appcompat stuff (activity, dialogs, ...)
//  re-organize screens, no need to keep exactly the same arrangement
//  use simple list picker
//  exclude all debug settings from search results if they are not enabled

// preliminary results:
// looks ok (ugly M3 switches)
// performance
//  time until app and screens are shown is clearly worse than previously (2-4x)
//  gets much better when opening same screen again
//  material3 is ~25% faster than material2
//  debug is MUCH slower than release
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

//        val cv = ComposeView(context = this)
        allPrefs = AllPrefs(this)
//        setContentView(cv) // todo: later, but for showing both old and new style settings, the layout is better
        setContentView(R.layout.settings_activity)
        supportFragmentManager.addOnBackStackChangedListener {
            updateContainerVisibility()
        }
//        cv.setContent { // also later...
        findViewById<ComposeView>(R.id.navHost).setContent {
            Theme {
                Surface {
                    SettingsNavHost(
                        onClickBack = {
                            if (supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null) // todo: remove after migration is complete
                                this.finish()
                            else supportFragmentManager.popBackStack() // todo: remove after migration is complete
                        }
                    )
                }
            }
        }
    }

    private fun updateContainerVisibility() { // todo: remove after migration is complete
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
