// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.spellcheck

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import helium314.keyboard.settings.SettingsActivity

// the Settings in SettingsContainer expect to be in a SettingsActivity, so we use a simple way of getting there
class SpellCheckerSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent()
        intent.setClass(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("spellchecker", true)
        startActivity(intent)
        if (!isFinishing) {
            finish()
        }
    }
}
