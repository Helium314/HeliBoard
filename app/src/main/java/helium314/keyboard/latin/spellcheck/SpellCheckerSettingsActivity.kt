// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.spellcheck

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import helium314.keyboard.settings.SettingsActivity

class SpellCheckerSettingsActivity : Activity() {
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
