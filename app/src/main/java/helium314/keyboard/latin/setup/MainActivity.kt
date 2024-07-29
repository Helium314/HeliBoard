package helium314.keyboard.latin.setup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
    }

}