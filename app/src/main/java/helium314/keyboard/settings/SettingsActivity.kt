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
        // having it in a companion object is not ideal as it will stay in memory even after settings are closed
        // but it's small enough to not care
        lateinit var settingsContainer: SettingsContainer
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged.value++
    }
}

@JvmField
var keyboardNeedsReload = false
