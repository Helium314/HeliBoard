package helium314.keyboard.latin.setup

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.roshaan.multiplebottomsheets.MultipleSheetsContainer
import com.roshaan.multiplebottomsheets.REMOVE_LOCK
import helium314.keyboard.bottomsheets.SheetOneFragment
import helium314.keyboard.bottomsheets.SheetThreeFragment
import helium314.keyboard.bottomsheets.SheetTwoFragment
import helium314.keyboard.latin.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sheetContainer = findViewById<MultipleSheetsContainer>(R.id.sheetContainer)
        sheetContainer.addFragment(0, SheetOneFragment())
        sheetContainer.addFragment(1, SheetTwoFragment())
        sheetContainer.addFragment(2, SheetThreeFragment())

        sheetContainer.lockedSheetIndex = REMOVE_LOCK

        if (savedInstanceState == null) {
            showFragment(SheetOneFragment())
        }
    }fun goToNextStep(step: Int) {
        val nextFragment: Fragment = when (step) {
            1 -> SheetTwoFragment()
            2 -> SheetThreeFragment()
            else -> SheetOneFragment()
        }

        showFragment(nextFragment)
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

}